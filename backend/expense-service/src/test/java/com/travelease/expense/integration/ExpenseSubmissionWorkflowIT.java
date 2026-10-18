package com.travelease.expense.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.expense.client.PolicyConfigClient;
import com.travelease.expense.client.TravelRequestClient;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.entity.ExpenseDocument;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Draft -> upload -> submit workflow (#5), rejected-claim correction and resubmission (#9) and claim
 * document download (#6), through the real Controller -> Service -> Repository stack on H2 with real
 * method security, the real document service and on-disk storage in a temp directory. Only the
 * Travel Request / Admin policy HTTP clients are stubbed (external services).
 */
@SpringBootTest(classes = ExpenseIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.datasource.url=jdbc:h2:mem:expense-submission-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Expense submission, resubmission and document download IT (#5, #6, #9)")
class ExpenseSubmissionWorkflowIT {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_EMPLOYEE_ID = 2L;
    private static final long FINANCE_ID = 4L;
    private static final long ADMIN_ID = 5L;
    private static final String TRAVEL_REQUEST_ID = "100";
    private static final BigDecimal RECEIPT_THRESHOLD = new BigDecimal("1000.00");
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2027, 1, 12);
    private static final byte[] PDF_BYTES = "%PDF-1.4 test receipt".getBytes(StandardCharsets.UTF_8);

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("travelease.storage.base-dir", () -> storageRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseClaimRepository claimRepository;

    @Autowired
    private ExpenseDocumentRepository documentRepository;

    @Autowired
    private ApplicationEvents events;

    @MockBean
    private TravelRequestClient travelRequestClient;

    @MockBean
    private PolicyConfigClient policyConfigClient;

    @BeforeEach
    void stubExternalServices() {
        when(travelRequestClient.getTravelRequestDateRange(anyString()))
                .thenReturn(new TravelRequestClient.TravelRequestDateRange(
                        LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15)));
        when(policyConfigClient.getActiveReceiptThreshold(anyString())).thenReturn(RECEIPT_THRESHOLD);
    }

    private static RequestPostProcessor user(long id, String... roles) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(id)).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    private static RequestPostProcessor owner() {
        return user(OWNER_ID, "EMPLOYEE");
    }

    private static RequestPostProcessor finance() {
        return user(FINANCE_ID, "FINANCE");
    }

    private static String claimBody(String amount) {
        return """
                {"travelRequestId":"%s","amount":%s,"currency":"INR","category":"LODGING",
                 "expenseDate":"%s","documentIds":[]}
                """.formatted(TRAVEL_REQUEST_ID, amount, EXPENSE_DATE);
    }

    private long createDraft(String amount) throws Exception {
        String response = mockMvc.perform(post("/api/expenses").with(owner())
                        .contentType(MediaType.APPLICATION_JSON).content(claimBody(amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions submit(long id, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post("/api/expenses/" + id + "/submit").with(caller));
    }

    /** Uploads through the real endpoint the way the web app does: no explicit fileType field. */
    private ResultActions upload(long id, RequestPostProcessor caller, String fileName, String contentType, byte[] bytes)
            throws Exception {
        return mockMvc.perform(multipart("/api/expenses/" + id + "/documents")
                .file(new MockMultipartFile("file", fileName, contentType, bytes))
                .param("fileName", fileName)
                .with(caller));
    }

    private long uploadReceipt(long id) throws Exception {
        String response = upload(id, owner(), "hotel-receipt.pdf", "application/pdf", PDF_BYTES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileType").value("PDF"))
                .andExpect(jsonPath("$.uploadStatus").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(objectMapper.readTree(response).get("id").asText());
    }

    private ExpenseClaim reload(long id) {
        return claimRepository.findById(id).orElseThrow();
    }

    private List<String> publishedActions(long claimId) {
        return events.stream(DomainEventEnvelope.class)
                .filter(e -> e.entityId().equals(String.valueOf(claimId)))
                .map(DomainEventEnvelope::action)
                .toList();
    }

    @Nested
    @DisplayName("#5 draft -> submit")
    class DraftAndSubmit {

        @Test
        @DisplayName("a new claim is a DRAFT: hidden from Finance and not reviewable until submitted")
        void newClaimIsPrivateDraft() throws Exception {
            long id = createDraft("500.00");

            JsonNode financeList = objectMapper.readTree(mockMvc.perform(get("/api/expenses").with(finance()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(financeList.path("content").findValuesAsText("id")).doesNotContain(String.valueOf(id));

            mockMvc.perform(post("/api/expenses/" + id + "/review").with(finance())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isUnprocessableEntity());
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
            assertThat(publishedActions(id)).containsExactly("CREATED");
        }

        @Test
        @DisplayName("a below-threshold draft submits to PENDING and emits SUBMITTED")
        void belowThresholdSubmits() throws Exception {
            long id = createDraft("500.00");

            submit(id, owner()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
            assertThat(publishedActions(id)).containsExactly("CREATED", "SUBMITTED");
        }

        @Test
        @DisplayName("an above-threshold draft without a receipt cannot reach Finance (422 on documentIds)")
        void missingReceiptBlocksSubmission() throws Exception {
            long id = createDraft("4000.00");

            submit(id, owner())
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"));

            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
            assertThat(publishedActions(id)).doesNotContain("SUBMITTED");
        }

        @Test
        @DisplayName("upload (type derived from the file, as the web app sends it) then submit succeeds")
        void uploadThenSubmit() throws Exception {
            long id = createDraft("4000.00");
            uploadReceipt(id);

            submit(id, owner()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
        }

        @Test
        @DisplayName("a FAILED upload record does not count as a receipt")
        void failedUploadDoesNotSatisfyReceiptRule() throws Exception {
            long id = createDraft("4000.00");
            ExpenseDocument failed = new ExpenseDocument();
            failed.setExpenseClaim(reload(id));
            failed.setFileName("lost.pdf");
            failed.setFileType(DocumentFileType.PDF);
            failed.setFileSizeBytes(10L);
            failed.setStorageReference("never-stored");
            failed.setUploadStatus(UploadStatus.FAILED);
            failed.setDeleted(false);
            stamp(failed);
            documentRepository.saveAndFlush(failed);

            submit(id, owner()).andExpect(status().isUnprocessableEntity());
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
        }

        @Test
        @DisplayName("an unsupported file is rejected with a clear 400 instead of a silent failure")
        void unsupportedUploadRejected() throws Exception {
            long id = createDraft("500.00");

            upload(id, owner(), "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
            assertThat(documentRepository.findByExpenseClaimIdAndDeletedFalse(id)).isEmpty();
        }

        @Test
        @DisplayName("submitting twice is refused (no duplicate submission)")
        void duplicateSubmissionRefused() throws Exception {
            long id = createDraft("500.00");
            submit(id, owner()).andExpect(status().isOk());

            submit(id, owner()).andExpect(status().isUnprocessableEntity());
            assertThat(publishedActions(id).stream().filter("SUBMITTED"::equals)).hasSize(1);
        }

        @Test
        @DisplayName("only the owner can submit; Finance and other employees get 403")
        void onlyOwnerSubmits() throws Exception {
            long id = createDraft("500.00");

            submit(id, user(OTHER_EMPLOYEE_ID, "EMPLOYEE")).andExpect(status().isForbidden());
            submit(id, finance()).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/expenses/" + id + "/submit")).andExpect(status().isUnauthorized());

            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
        }

        @Test
        @DisplayName("a draft whose trip was cancelled meanwhile cannot be submitted")
        void cancelledTripBlocksSubmission() throws Exception {
            long id = createDraft("500.00");
            doThrow(new PolicyViolationException("Expense claims cannot be filed against a cancelled travel request"))
                    .when(travelRequestClient).validateTravelRequestApprovedAndOwned(eq(TRAVEL_REQUEST_ID), anyString());

            submit(id, owner()).andExpect(status().isUnprocessableEntity());
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.DRAFT);
        }

        @Test
        @DisplayName("documents cannot be attached once a claim is APPROVED")
        void noUploadsAfterApproval() throws Exception {
            long id = createDraft("500.00");
            submit(id, owner()).andExpect(status().isOk());
            mockMvc.perform(post("/api/expenses/" + id + "/review").with(finance())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isOk());

            upload(id, owner(), "late.pdf", "application/pdf", PDF_BYTES).andExpect(status().isUnprocessableEntity());
            submit(id, owner()).andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("#9 rejected claim correction and resubmission")
    class Resubmission {

        private long rejectedClaim(String amount) throws Exception {
            long id = createDraft(amount);
            if (new BigDecimal(amount).compareTo(RECEIPT_THRESHOLD) > 0) {
                uploadReceipt(id);
            }
            submit(id, owner()).andExpect(status().isOk());
            mockMvc.perform(post("/api/expenses/" + id + "/review").with(finance())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"REJECTED\",\"reason\":\"Amount does not match the invoice\"}"))
                    .andExpect(status().isOk());
            return id;
        }

        @Test
        @DisplayName("the owner edits a REJECTED claim and resubmits it through Finance review")
        void editAndResubmit() throws Exception {
            long id = rejectedClaim("800.00");

            mockMvc.perform(put("/api/expenses/" + id).with(owner())
                            .contentType(MediaType.APPLICATION_JSON).content(claimBody("750.00")))
                    .andExpect(status().isOk());
            ExpenseClaim edited = reload(id);
            assertThat(edited.getStatus()).isEqualTo(ExpenseClaimStatus.REJECTED);
            assertThat(edited.getAmount()).isEqualByComparingTo("750.00");

            submit(id, owner()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

            ExpenseClaim resubmitted = reload(id);
            assertThat(resubmitted.getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
            // the previous decision stays visible to Finance until they decide again
            assertThat(resubmitted.getRejectionReason()).isEqualTo("Amount does not match the invoice");
            assertThat(publishedActions(id)).endsWith("REJECTED", "UPDATED", "RESUBMITTED");
            assertThat(claimRepository.findAll().stream().filter(c -> c.getTravelRequestId() == 100L)).hasSize(1);

            mockMvc.perform(post("/api/expenses/" + id + "/review").with(finance())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
            assertThat(reload(id).getRejectionReason()).isNull();
        }

        @Test
        @DisplayName("resubmission re-validates the receipt rule after a correction raises the amount")
        void resubmissionRevalidatesReceipt() throws Exception {
            long id = rejectedClaim("800.00");
            mockMvc.perform(put("/api/expenses/" + id).with(owner())
                            .contentType(MediaType.APPLICATION_JSON).content(claimBody("5000.00")))
                    .andExpect(status().isOk());

            submit(id, owner()).andExpect(status().isUnprocessableEntity());
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.REJECTED);

            uploadReceipt(id);
            submit(id, owner()).andExpect(status().isOk());
        }

        @Test
        @DisplayName("another employee cannot edit or resubmit someone else's rejected claim")
        void othersCannotEditOrResubmit() throws Exception {
            long id = rejectedClaim("800.00");

            mockMvc.perform(put("/api/expenses/" + id).with(user(OTHER_EMPLOYEE_ID, "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(claimBody("1.00")))
                    .andExpect(status().isForbidden());
            submit(id, user(OTHER_EMPLOYEE_ID, "EMPLOYEE")).andExpect(status().isForbidden());

            assertThat(reload(id).getAmount()).isEqualByComparingTo("800.00");
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.REJECTED);
        }

        @Test
        @DisplayName("final states cannot be edited or resubmitted")
        void finalStatesLocked() throws Exception {
            long id = createDraft("500.00");
            submit(id, owner()).andExpect(status().isOk());
            mockMvc.perform(post("/api/expenses/" + id + "/review").with(finance())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/expenses/" + id + "/reimburse").with(finance())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/expenses/" + id).with(owner())
                            .contentType(MediaType.APPLICATION_JSON).content(claimBody("1.00")))
                    .andExpect(status().isUnprocessableEntity());
            submit(id, owner()).andExpect(status().isUnprocessableEntity());
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.REIMBURSED);
        }
    }

    @Nested
    @DisplayName("#6 claim document download")
    class Download {

        @Test
        @DisplayName("the owner, Finance and Admin download the stored bytes as a safe attachment")
        void authorizedDownloads() throws Exception {
            long id = createDraft("4000.00");
            long documentId = uploadReceipt(id);
            String url = "/api/expenses/" + id + "/documents/" + documentId;

            for (RequestPostProcessor caller : List.of(owner(), finance(), user(ADMIN_ID, "ADMIN"))) {
                byte[] body = mockMvc.perform(get(url).with(caller))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "application/pdf"))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andExpect(header().string("Content-Disposition",
                                org.hamcrest.Matchers.startsWith("attachment;")))
                        .andReturn().getResponse().getContentAsByteArray();
                assertThat(body).isEqualTo(PDF_BYTES);
            }
        }

        @Test
        @DisplayName("another employee cannot download the owner's receipt (403) and anonymous callers get 401")
        void otherEmployeeForbidden() throws Exception {
            long id = createDraft("4000.00");
            long documentId = uploadReceipt(id);

            mockMvc.perform(get("/api/expenses/" + id + "/documents/" + documentId)
                            .with(user(OTHER_EMPLOYEE_ID, "EMPLOYEE")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/expenses/" + id + "/documents/" + documentId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a document id from another claim is not served through this claim's URL (404)")
        void crossClaimDocumentNotServed() throws Exception {
            long mine = createDraft("500.00");
            long other = createDraft("4000.00");
            long otherDocument = uploadReceipt(other);

            mockMvc.perform(get("/api/expenses/" + mine + "/documents/" + otherDocument).with(owner()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a document whose stored file is gone returns 404, not 500/503")
        void missingFileIs404() throws Exception {
            long id = createDraft("4000.00");
            long documentId = uploadReceipt(id);
            try (Stream<Path> files = Files.list(storageRoot)) {
                for (Path file : files.toList()) {
                    Files.delete(file);
                }
            }

            mockMvc.perform(get("/api/expenses/" + id + "/documents/" + documentId).with(owner()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/expenses/" + id + "/documents/999999").with(owner()))
                    .andExpect(status().isNotFound());
        }
    }

    private static void stamp(com.travelease.common.audit.Auditable entity) {
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy("it");
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy("it");
    }
}
