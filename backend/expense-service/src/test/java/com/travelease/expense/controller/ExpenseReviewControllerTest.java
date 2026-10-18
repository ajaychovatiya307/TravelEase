package com.travelease.expense.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.dto.DocumentSummary;
import com.travelease.common.entity.DecisionType;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ValidationException;
import com.travelease.expense.config.SecurityConfig;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseReimburseRequest;
import com.travelease.expense.dto.ExpenseReviewRequest;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.exception.GlobalExceptionHandler;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.service.ExpenseClaimService;
import com.travelease.expense.service.ExpenseDocumentService;
import com.travelease.expense.service.ExpenseReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the Finance review/reimburse and document-upload
 * expense endpoints (tasks.md M-04; api-contract.md §4.2/§8; spec.md
 * FR-FIN-01, FR-FIN-02, FR-FIN-03, FR-DOC-01; AC-09, AC-10, AC-11):
 *
 * <ul>
 *   <li>403 {@code FORBIDDEN_ACTION} from {@code @PreAuthorize} when an
 *       EMPLOYEE calls the Finance-only review or reimburse endpoints; the
 *       services are never reached (FR-FIN-01, AC-10)</li>
 *   <li>200 OK with the APPROVED claim on a valid approve decision
 *       (FR-FIN-01, AC-10)</li>
 *   <li>200 OK with the REJECTED claim and the stored rejection reason on a
 *       valid reject decision carrying a reason (FR-FIN-02, AC-10)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field {@code reason} when a
 *       {@code REJECTED} decision carries no reason (VAL-12, AC-10)</li>
 *   <li>422 {@code POLICY_VIOLATION} when the service tier rejects a review
 *       of a non-PENDING claim or a reimbursement of a non-APPROVED claim
 *       (FR-FIN-03, AC-10)</li>
 *   <li>200 OK with the REIMBURSED claim and the {@code reimbursedAt}
 *       timestamp on a valid reimbursement (FR-FIN-03, AC-10)</li>
 *   <li>200 OK with the {@code DocumentSummary} on a valid multipart upload
 *       (FR-DOC-01, AC-11); 400 {@code VALIDATION_ERROR} with field
 *       {@code fileType} when the document service rejects an unsupported
 *       type (VAL-11, AC-11)</li>
 *   <li>the {@code duplicateFlag} is exposed on claim bodies so Finance can
 *       spot potential duplicates in the review flow (FR-EXP-07, AC-09)</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5; mirrors the proven M-03 slice in
 * {@link ExpenseClaimControllerTest}): the review, reimburse, and document
 * endpoints live on {@link ExpenseClaimController}, so the slice targets that
 * controller. The slice is bootstrapped from the shared
 * {@link ExpenseTestApplication} in this package (a single top-level config
 * for the whole package, see its Javadoc). The app's {@link SecurityConfig}
 * is imported so the real JWT resource-server filter chain and
 * {@code @EnableMethodSecurity} (i.e. the {@code @PreAuthorize} gating on the
 * controller) are in effect exactly as in production; its
 * {@code DevJwtDecoders} fallback provides an offline HMAC {@code JwtDecoder},
 * so no auto-configuration exclusions are needed. Authenticated requests use
 * spring-security-test's {@code jwt()} post-processor; the {@code ROLE_*}
 * authority is set explicitly because the mock token bypasses the
 * {@code JwtRoleConverters} claim-to-authority conversion that the real
 * filter chain would apply. Paths address the servlet mapping directly
 * ({@code /api/expenses}), not the {@code /api} context path of the running
 * server. Only {@code @MockBean}-style mocks (Boot 3.2.5;
 * {@code @MockitoBean} exists only from 3.4) of the controller's three
 * constructor collaborators — {@link ExpenseClaimService},
 * {@link ExpenseDocumentService} and {@link ExpenseReviewService} — plus a
 * spare {@link ExpenseClaimRepository} mock; only what each test needs is
 * stubbed. The document-upload endpoint delegates to
 * {@link ExpenseDocumentService}, which loads the claim and enforces
 * owner-only upload (NFR-10), so those tests stub the id-based service
 * overload.</p>
 */
@WebMvcTest(controllers = ExpenseClaimController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("ExpenseClaimController review/reimburse/document-upload API (tasks.md M-04; api-contract.md §4.2/§8; spec.md FR-FIN-01, FR-FIN-02, FR-FIN-03, FR-DOC-01; AC-09, AC-10, AC-11)")
class ExpenseReviewControllerTest {

    private static final String FINANCE_ID = "4";
    private static final String EMPLOYEE_ID = "1";
    private static final String TRAVEL_REQUEST_ID = "10";
    private static final String CLAIM_ID = "100";

    private static final String AMOUNT = "1500.50";
    private static final String CURRENCY = "INR";
    private static final String CATEGORY = "TRANSPORT";
    private static final String EXPENSE_DATE_ISO = "2027-03-16";
    private static final String REIMBURSED_AT_ISO = "2027-03-17T10:15:00Z";
    private static final String REJECTION_REASON = "Not reimbursable";

    /** Exact message raised when reviewing a claim that is not PENDING. */
    private static final String NON_PENDING_REVIEW_MESSAGE =
            "Expense claim can only be reviewed when in PENDING status; current status: REJECTED";

    /** Exact message raised when reimbursing a claim that is not APPROVED. */
    private static final String NON_APPROVED_REIMBURSE_MESSAGE =
            "Expense claim can only be marked reimbursed when in APPROVED status; current status: PENDING";

    /** Exact VAL-12 message raised by {@code ExpenseReviewServiceImpl.reviewExpenseClaim}. */
    private static final String VAL12_REASON_REQUIRED_MESSAGE =
            "Rejection reason is required when rejecting an expense claim";

    /** Exact VAL-11 message raised by the shared document constraint validator for an unknown type. */
    private static final String INVALID_FILE_TYPE_MESSAGE =
            "Invalid file type: TXT. Only PDF, JPG, PNG are supported.";

    private static final String APPROVE_REVIEW_BODY = "{\"decision\": \"APPROVED\"}";
    private static final String REJECT_REVIEW_BODY =
            "{\"decision\": \"REJECTED\", \"reason\": \"" + REJECTION_REASON + "\"}";
    private static final String REJECT_NO_REASON_BODY = "{\"decision\": \"REJECTED\"}";
    private static final String REIMBURSE_BODY = "{\"reimbursedAt\": \"" + REIMBURSED_AT_ISO + "\"}";

    private static final String DOCUMENT_ID = "200";
    private static final String UPLOAD_FILE_NAME = "receipt.pdf";
    private static final String UPLOAD_FILE_TYPE = "PDF";
    private static final String STORAGE_REFERENCE = "expense-claims/100/receipt.pdf";
    private static final String INVALID_FILE_NAME = "notes.txt";
    private static final String INVALID_FILE_TYPE = "TXT";
    private static final byte[] UPLOAD_CONTENT = "expense-receipt".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseClaimService expenseClaimService;

    @MockBean
    private ExpenseDocumentService expenseDocumentService;

    @MockBean
    private ExpenseReviewService expenseReviewService;

    @MockBean
    private ExpenseClaimRepository expenseClaimRepository;

    /**
     * Bearer-style JWT for the reviewing Finance user: subject "4" plus the
     * {@code roles} claim and the matching {@code ROLE_FINANCE} authority, so
     * {@code @PreAuthorize("hasRole('FINANCE')")} is satisfied (see class
     * Javadoc).
     */
    private RequestPostProcessor financeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(FINANCE_ID).claim("roles", List.of("FINANCE")))
                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE"));
    }

    /**
     * Bearer-style JWT for the authenticated employee: subject "1" (the
     * caller's logical UserAccount ID per api-contract.md §2) plus the
     * {@code roles} claim and the matching {@code ROLE_EMPLOYEE} authority
     * (see class Javadoc).
     */
    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(EMPLOYEE_ID).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private ExpenseClaimResponse claimResponse(ExpenseClaimStatus status, String rejectionReason,
            Instant reimbursedAt, boolean duplicateFlag) {
        return new ExpenseClaimResponse(
                CLAIM_ID,
                EMPLOYEE_ID,
                TRAVEL_REQUEST_ID,
                new BigDecimal(AMOUNT),
                CURRENCY,
                CATEGORY,
                LocalDate.parse(EXPENSE_DATE_ISO),
                status,
                duplicateFlag,
                rejectionReason,
                reimbursedAt,
                List.of(),
                Instant.parse("2027-03-16T09:00:00Z"),
                Instant.parse("2027-03-16T09:00:01Z"));
    }

    @Nested
    @DisplayName("POST /api/expenses/{id}/review (approve/reject)")
    class Review {

        @Test
        @DisplayName("FINANCE approval returns 200 with the APPROVED claim (FR-FIN-01, AC-10)")
        void approveReturns200WithApprovedClaim() throws Exception {
            when(expenseReviewService.reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReviewRequest.class)))
                    .thenReturn(claimResponse(ExpenseClaimStatus.APPROVED, null, null, false));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_REVIEW_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CLAIM_ID))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.travelRequestId").value(TRAVEL_REQUEST_ID))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.rejectionReason").isEmpty())
                    .andExpect(jsonPath("$.reimbursedAt").isEmpty());

            ArgumentCaptor<ExpenseReviewRequest> captor = ArgumentCaptor.forClass(ExpenseReviewRequest.class);
            verify(expenseReviewService).reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), captor.capture());
            assertThat(captor.getValue().getDecision()).isEqualTo(DecisionType.APPROVED);
            assertThat(captor.getValue().getReason()).isNull();
        }

        @Test
        @DisplayName("FINANCE rejection with reason returns 200 with the REJECTED claim and the stored reason (FR-FIN-02, AC-10)")
        void rejectWithReasonReturns200WithRejectedClaim() throws Exception {
            when(expenseReviewService.reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReviewRequest.class)))
                    .thenReturn(claimResponse(ExpenseClaimStatus.REJECTED, REJECTION_REASON, null, false));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REJECT_REVIEW_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CLAIM_ID))
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value(REJECTION_REASON));

            ArgumentCaptor<ExpenseReviewRequest> captor = ArgumentCaptor.forClass(ExpenseReviewRequest.class);
            verify(expenseReviewService).reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), captor.capture());
            assertThat(captor.getValue().getDecision()).isEqualTo(DecisionType.REJECTED);
            assertThat(captor.getValue().getReason()).isEqualTo(REJECTION_REASON);
        }

        @Test
        @DisplayName("REJECTED decision without reason (VAL-12) returns 400 VALIDATION_ERROR for field reason, service untouched (AC-10)")
        void rejectWithoutReasonReturns400WithReasonFieldError() throws Exception {
            // Document the service-tier contract: if a REJECTED decision
            // without a reason ever reaches the service,
            // ExpenseReviewServiceImpl raises this exact VAL-12 exception,
            // which maps to the same 400 VALIDATION_ERROR / field=reason
            // response shape asserted below.
            when(expenseReviewService.reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReviewRequest.class)))
                    .thenThrow(new ValidationException("reason", VAL12_REASON_REQUIRED_MESSAGE));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REJECT_NO_REASON_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            // Unlike the travel-request DTO (M-02), ExpenseReviewRequest
            // carries the J-02 class-level @RequiredWhenRejected constraint,
            // so the Bean Validation tier enforces VAL-12 on field
            // "reason" before the service is reached; hence no service
            // interaction.
            verifyNoInteractions(expenseReviewService);
        }

        @Test
        @DisplayName("reviewing a non-PENDING claim returns 422 POLICY_VIOLATION (FR-FIN-02, AC-10)")
        void reviewingNonPendingClaimReturns422PolicyViolation() throws Exception {
            when(expenseReviewService.reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReviewRequest.class)))
                    .thenThrow(new PolicyViolationException(NON_PENDING_REVIEW_MESSAGE));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_REVIEW_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(NON_PENDING_REVIEW_MESSAGE));
        }

        @Test
        @DisplayName("potential duplicate flag is visible on the review response (FR-EXP-07, AC-09)")
        void duplicateFlagVisibleOnReviewResponse() throws Exception {
            when(expenseReviewService.reviewExpenseClaim(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReviewRequest.class)))
                    .thenReturn(claimResponse(ExpenseClaimStatus.APPROVED, null, null, true));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_REVIEW_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.duplicateFlag").value(true));
        }

        @Test
        @DisplayName("EMPLOYEE calling the review endpoint is rejected with 403 FORBIDDEN_ACTION by @PreAuthorize, services never called (FR-FIN-01, AC-10)")
        void employeeRoleReturns403() throws Exception {
            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/review")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_REVIEW_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(expenseReviewService, expenseDocumentService,
                    expenseClaimService, expenseClaimRepository);
        }
    }

    @Nested
    @DisplayName("POST /api/expenses/{id}/reimburse")
    class Reimburse {

        @Test
        @DisplayName("FINANCE reimbursement returns 200 with the REIMBURSED claim and the reimbursedAt timestamp (FR-FIN-03, AC-10)")
        void reimburseReturns200WithReimbursedClaim() throws Exception {
            when(expenseReviewService.markReimbursed(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReimburseRequest.class)))
                    .thenReturn(claimResponse(ExpenseClaimStatus.REIMBURSED, null,
                            Instant.parse(REIMBURSED_AT_ISO), false));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/reimburse")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REIMBURSE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CLAIM_ID))
                    .andExpect(jsonPath("$.status").value("REIMBURSED"))
                    .andExpect(jsonPath("$.reimbursedAt").value(REIMBURSED_AT_ISO));

            // The optional client-supplied timestamp must be passed through to the service.
            ArgumentCaptor<ExpenseReimburseRequest> captor = ArgumentCaptor.forClass(ExpenseReimburseRequest.class);
            verify(expenseReviewService).markReimbursed(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), captor.capture());
            assertThat(captor.getValue().getReimbursedAt()).isEqualTo(Instant.parse(REIMBURSED_AT_ISO));
        }

        @Test
        @DisplayName("reimbursing a non-APPROVED claim returns 422 POLICY_VIOLATION (FR-FIN-03, AC-10)")
        void reimbursingNonApprovedClaimReturns422PolicyViolation() throws Exception {
            when(expenseReviewService.markReimbursed(eq(CLAIM_ID), eq(Long.valueOf(FINANCE_ID)), any(ExpenseReimburseRequest.class)))
                    .thenThrow(new PolicyViolationException(NON_APPROVED_REIMBURSE_MESSAGE));

            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/reimburse")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REIMBURSE_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(NON_APPROVED_REIMBURSE_MESSAGE));
        }

        @Test
        @DisplayName("EMPLOYEE calling the reimburse endpoint is rejected with 403 FORBIDDEN_ACTION by @PreAuthorize, services never called (FR-FIN-03, AC-10)")
        void employeeRoleReturns403() throws Exception {
            mockMvc.perform(post("/api/expenses/" + CLAIM_ID + "/reimburse")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REIMBURSE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(expenseReviewService, expenseDocumentService,
                    expenseClaimService, expenseClaimRepository);
        }
    }

    @Nested
    @DisplayName("POST /api/expenses/{id}/documents (multipart upload)")
    class DocumentUpload {

        @Test
        @DisplayName("valid PDF upload returns 200 with the document summary and the part bytes (FR-DOC-01, AC-11)")
        void validUploadReturns200WithSummary() throws Exception {
            // DEVIATION from tasks.md M-04, which lists "201" here: the
            // expense document endpoint returns ResponseEntity.ok(...) (its
            // Javadoc says "200 OK with the document summary"), unlike the
            // travel-request upload endpoint which returns 201. We assert the
            // status code the controller actually produces.
            DocumentSummary summary = new DocumentSummary(
                    DOCUMENT_ID, UPLOAD_FILE_NAME, DocumentFileType.PDF,
                    UPLOAD_CONTENT.length, STORAGE_REFERENCE, UploadStatus.CONFIRMED);
            when(expenseDocumentService.uploadDocument(eq(Long.valueOf(CLAIM_ID)), eq(Long.valueOf(EMPLOYEE_ID)),
                    eq(UPLOAD_FILE_NAME), eq(UPLOAD_FILE_TYPE), any(byte[].class)))
                    .thenReturn(summary);

            mockMvc.perform(multipart("/api/expenses/" + CLAIM_ID + "/documents")
                            .file(new MockMultipartFile("file", UPLOAD_FILE_NAME, "application/pdf", UPLOAD_CONTENT))
                            .param("fileName", UPLOAD_FILE_NAME)
                            .param("fileType", UPLOAD_FILE_TYPE)
                            .with(employeeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(DOCUMENT_ID))
                    .andExpect(jsonPath("$.fileName").value(UPLOAD_FILE_NAME))
                    .andExpect(jsonPath("$.fileType").value("PDF"))
                    .andExpect(jsonPath("$.fileSizeBytes").value(UPLOAD_CONTENT.length))
                    .andExpect(jsonPath("$.storageReference").value(STORAGE_REFERENCE))
                    .andExpect(jsonPath("$.uploadStatus").value("CONFIRMED"));

            // The controller must hand the service the claim id, the
            // authenticated employee id, and the part's bytes.
            ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(expenseDocumentService).uploadDocument(eq(Long.valueOf(CLAIM_ID)), eq(Long.valueOf(EMPLOYEE_ID)),
                    eq(UPLOAD_FILE_NAME), eq(UPLOAD_FILE_TYPE), contentCaptor.capture());
            assertThat(contentCaptor.getValue()).isEqualTo(UPLOAD_CONTENT);
        }

        @Test
        @DisplayName("uploading to a claim the caller does not own returns 403 FORBIDDEN_ACTION (NFR-10)")
        void nonOwnerUploadReturns403ForbiddenAction() throws Exception {
            when(expenseDocumentService.uploadDocument(eq(Long.valueOf(CLAIM_ID)), eq(Long.valueOf(EMPLOYEE_ID)),
                    eq(UPLOAD_FILE_NAME), eq(UPLOAD_FILE_TYPE), any(byte[].class)))
                    .thenThrow(new ForbiddenActionException("upload documents", "the claim owner"));

            mockMvc.perform(multipart("/api/expenses/" + CLAIM_ID + "/documents")
                            .file(new MockMultipartFile("file", UPLOAD_FILE_NAME, "application/pdf", UPLOAD_CONTENT))
                            .param("fileName", UPLOAD_FILE_NAME)
                            .param("fileType", UPLOAD_FILE_TYPE)
                            .with(employeeJwt()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value("Only the claim owner can upload documents"));
        }

        @Test
        @DisplayName("unsupported file type (TXT) returns 400 VALIDATION_ERROR for field fileType (VAL-11, AC-11)")
        void unsupportedTypeReturns400WithFileTypeFieldError() throws Exception {
            when(expenseDocumentService.uploadDocument(eq(Long.valueOf(CLAIM_ID)), eq(Long.valueOf(EMPLOYEE_ID)),
                    eq(INVALID_FILE_NAME), eq(INVALID_FILE_TYPE), any(byte[].class)))
                    .thenThrow(new ValidationException("fileType", INVALID_FILE_TYPE_MESSAGE));

            byte[] textContent = "plain text receipt".getBytes(StandardCharsets.UTF_8);
            mockMvc.perform(multipart("/api/expenses/" + CLAIM_ID + "/documents")
                            .file(new MockMultipartFile("file", INVALID_FILE_NAME, "text/plain", textContent))
                            .param("fileName", INVALID_FILE_NAME)
                            .param("fileType", INVALID_FILE_TYPE)
                            .with(employeeJwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(INVALID_FILE_TYPE_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("fileType"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(INVALID_FILE_TYPE_MESSAGE));
        }
    }
}