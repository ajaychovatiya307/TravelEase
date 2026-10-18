package com.travelease.expense.integration;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.expense.client.PolicyConfigClient;
import com.travelease.expense.client.TravelRequestClient;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.service.ExpenseDocumentService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end-within-service integration tests for the Expense Claim workflow
 * (tasks.md M-07; Constitution §10; spec.md FR-EXP-01, FR-FIN-01–03; AC-09, AC-10):
 *
 * <ul>
 *   <li>submit → review (approve) → reimburse: a PENDING claim transitions through
 *       APPROVED to REIMBURSED, with each transition durably persisted (full
 *       Controller→Service→Repository round-trip against the in-memory H2
 *       database)</li>
 *   <li>submit → review (reject): a PENDING claim is rejected with its reason
 *       persisted (FR-FIN-02, VAL-12)</li>
 * </ul>
 *
 * <p>Only the cross-service HTTP boundaries (Constitution §2: no cross-service DB
 * access) are mocked: {@link TravelRequestClient} (Travel Request Service trip
 * lookup/ownership check) and {@link PolicyConfigClient} (Admin Service receipt
 * threshold lookup); claim creation no longer consults the receipt threshold —
 * VAL-10 is enforced on the edit path only (create-then-upload flow) — so no
 * threshold stub is needed, and {@link PolicyConfigClient} and
 * {@link ExpenseDocumentService} remain mocked cross-service boundaries.
 * Everything else - controller, service, mapper,
 * validators, repositories, and the real JPA/H2 persistence layer - runs for real,
 * per tasks.md M-07's "Controller-to-Repository flow" requirement.</p>
 */
@SpringBootTest(classes = ExpenseIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        // Force an in-memory H2 database (Constitution §10) regardless of the module's
        // main application.yml datasource (e.g. Postgres, whose driver is not on the test classpath).
        "spring.datasource.url=jdbc:h2:mem:expense-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Expense Claim workflow IT (tasks.md M-07; FR-EXP-01, FR-FIN-01–03; AC-09, AC-10)")
class ExpenseClaimWorkflowIT {

    private static final String EMPLOYEE_ID = "5";
    private static final String TRAVEL_REQUEST_ID = "100";
    private static final String CATEGORY = "TRAVEL";
    private static final LocalDate TRIP_START = LocalDate.of(2027, 1, 10);
    private static final LocalDate TRIP_END = LocalDate.of(2027, 1, 15);
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2027, 1, 12);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TravelRequestClient travelRequestClient;

    @MockBean
    private PolicyConfigClient policyConfigClient;

    @MockBean
    private ExpenseDocumentService expenseDocumentService;

    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(EMPLOYEE_ID).claim("roles", java.util.List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private RequestPostProcessor financeJwt() {
        return jwt()
                .jwt(builder -> builder.subject("9").claim("roles", java.util.List.of("FINANCE")))
                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE"));
    }

    private String createBody(String amount) {
        // NFR-05 — only the single base currency (INR) is accepted; employees
        // pre-convert foreign amounts before entry (Q18).
        return """
                {
                    "travelRequestId": "%s",
                    "amount": %s,
                    "currency": "INR",
                    "category": "%s",
                    "expenseDate": "%s",
                    "documentIds": []
                }
                """.formatted(TRAVEL_REQUEST_ID, amount, CATEGORY, EXPENSE_DATE);
    }

    private long submitExpenseClaim(String amount) throws Exception {
        when(travelRequestClient.getTravelRequestDateRange(TRAVEL_REQUEST_ID))
                .thenReturn(new TravelRequestClient.TravelRequestDateRange(TRIP_START, TRIP_END));

        String response = mockMvc.perform(post("/api/expenses")
                        .with(employeeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(objectMapper.readTree(response).get("id").asText());

        // Drafts reach Finance only through an explicit submission
        mockMvc.perform(post("/api/expenses/" + id + "/submit").with(employeeJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        return id;
    }

    @Nested
    @DisplayName("Finance status filtering")
    class FinanceStatusFiltering {

        @Test
        @Transactional
        @DisplayName("returns only claims matching each lifecycle status")
        void returnsOnlyClaimsWithRequestedStatus() throws Exception {
            for (ExpenseClaimStatus claimStatus : ExpenseClaimStatus.values()) {
                ExpenseClaim claim = new ExpenseClaim();
                claim.setEmployeeId(100L + claimStatus.ordinal());
                claim.setTravelRequestId(200L + claimStatus.ordinal());
                claim.setAmount(new java.math.BigDecimal("100.00"));
                claim.setCurrency("INR");
                claim.setCategory(CATEGORY);
                claim.setExpenseDate(EXPENSE_DATE);
                claim.setStatus(claimStatus);
                claim.setCreatedAt(Instant.now());
                claim.setUpdatedAt(Instant.now());
                claim.setCreatedBy("integration-test");
                claim.setUpdatedBy("integration-test");
                expenseClaimRepository.save(claim);
            }
            expenseClaimRepository.flush();

            for (ExpenseClaimStatus requestedStatus : ExpenseClaimStatus.values()) {
                String response = mockMvc.perform(get("/api/expenses")
                                .param("status", requestedStatus.name())
                                .with(financeJwt()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();

                var claims = objectMapper.readTree(response).path("content");
                if (requestedStatus == ExpenseClaimStatus.DRAFT) {
                    // Drafts are private to their owner and never appear in Finance's list
                    assertThat(claims).isEmpty();
                    continue;
                }
                assertThat(claims).isNotEmpty();
                for (var claim : claims) {
                    assertThat(claim.path("status").asText())
                            .as("all claims returned for filter %s must have that status", requestedStatus)
                            .isEqualTo(requestedStatus.name());
                }
            }
        }
    }

    @Nested
    @DisplayName("submit -> review (approve) -> reimburse")
    class SubmitReviewReimburse {

        @Test
        @Transactional
        @DisplayName("a submitted claim is approved then reimbursed, durably persisted as REIMBURSED (AC-09, AC-10)")
        void submitApproveReimburseEndsReimbursed() throws Exception {
            long claimId = submitExpenseClaim("250.00");

            mockMvc.perform(post("/api/expenses/" + claimId + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision": "APPROVED"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            mockMvc.perform(post("/api/expenses/" + claimId + "/reimburse")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REIMBURSED"))
                    .andExpect(jsonPath("$.reimbursedAt").exists());

            ExpenseClaim persisted = expenseClaimRepository.findByIdAndDeletedFalse(claimId).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(ExpenseClaimStatus.REIMBURSED);
            assertThat(persisted.getReimbursedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("submit -> review (reject)")
    class SubmitReject {

        @Test
        @Transactional
        @DisplayName("a submitted claim is rejected with the reason persisted (FR-FIN-02, VAL-12)")
        void submitThenRejectPersistsReason() throws Exception {
            long claimId = submitExpenseClaim("75.50");

            mockMvc.perform(post("/api/expenses/" + claimId + "/review")
                            .with(financeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision": "REJECTED", "reason": "Missing itemized receipt"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("Missing itemized receipt"));

            ExpenseClaim persisted = expenseClaimRepository.findByIdAndDeletedFalse(claimId).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(ExpenseClaimStatus.REJECTED);
            assertThat(persisted.getRejectionReason()).isEqualTo("Missing itemized receipt");
        }
    }
}
