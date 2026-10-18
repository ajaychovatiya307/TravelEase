package com.travelease.expense.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Record-level access control for expense claims (NFR-10), exercised through the real
 * Controller -> Service -> Repository stack on H2 with real method security. Only the external
 * Travel Request / Admin policy clients and document storage are mocked.
 */
@SpringBootTest(classes = ExpenseIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.datasource.url=jdbc:h2:mem:expense-authz-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Expense claim authorization IT (NFR-10)")
class ExpenseClaimAuthorizationIT {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_EMPLOYEE_ID = 2L;
    /** A Finance user who also files expense claims (holds EMPLOYEE + FINANCE). */
    private static final long FINANCE_ID = 4L;
    private static final long ADMIN_ID = 5L;

    private static final long TRAVEL_REQUEST_ID = 100L;
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2027, 1, 12);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @MockBean
    private TravelRequestClient travelRequestClient;

    @MockBean
    private PolicyConfigClient policyConfigClient;

    @MockBean
    private ExpenseDocumentService expenseDocumentService;

    @BeforeEach
    void stubExternalServices() {
        when(travelRequestClient.getTravelRequestDateRange(anyString()))
                .thenReturn(new TravelRequestClient.TravelRequestDateRange(
                        LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15)));
        when(policyConfigClient.getActiveReceiptThreshold(anyString())).thenReturn(new BigDecimal("100000.00"));
    }

    private static RequestPostProcessor user(long id, String... roles) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(id)).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    private long claimOwnedBy(long employeeId, ExpenseClaimStatus status) {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setEmployeeId(employeeId);
        claim.setTravelRequestId(TRAVEL_REQUEST_ID);
        claim.setAmount(new BigDecimal("1500.00"));
        claim.setCurrency("INR");
        claim.setCategory("TRANSPORT");
        claim.setExpenseDate(EXPENSE_DATE);
        claim.setStatus(status);
        claim.setDuplicateFlag(false);
        claim.setDeleted(false);
        claim.setCreatedAt(Instant.now());
        claim.setCreatedBy("it");
        claim.setUpdatedAt(Instant.now());
        claim.setUpdatedBy("it");
        return expenseClaimRepository.saveAndFlush(claim).getId();
    }

    private ExpenseClaim reload(long id) {
        return expenseClaimRepository.findById(id).orElseThrow();
    }

    private static String editBody(String amount) {
        return """
                {"travelRequestId":"%d","amount":%s,"currency":"INR","category":"TRANSPORT",
                 "expenseDate":"%s","documentIds":[]}
                """.formatted(TRAVEL_REQUEST_ID, amount, EXPENSE_DATE);
    }

    @Nested
    @DisplayName("GET /api/expenses/{id}")
    class Read {

        @Test
        @DisplayName("an employee can retrieve their own claim")
        void ownerCanRead() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(get("/api/expenses/" + id).with(user(OWNER_ID, "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(String.valueOf(id)));
        }

        @Test
        @DisplayName("an employee gets 403 for another employee's claim (no ID enumeration)")
        void otherEmployeeForbidden() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(get("/api/expenses/" + id).with(user(OTHER_EMPLOYEE_ID, "EMPLOYEE")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.amount").doesNotExist());
        }

        @Test
        @DisplayName("FINANCE and ADMIN may read any claim")
        void financeAndAdminCanRead() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(get("/api/expenses/" + id).with(user(FINANCE_ID, "FINANCE")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/expenses/" + id).with(user(ADMIN_ID, "ADMIN")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a MANAGER-only caller is refused by method security")
        void managerOnlyForbidden() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(get("/api/expenses/" + id).with(user(3L, "MANAGER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated read gets 401")
        void unauthenticated() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(get("/api/expenses/" + id)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/expenses/{id}")
    class Edit {

        @Test
        @DisplayName("an employee can edit their own PENDING claim")
        void ownerCanEdit() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(put("/api/expenses/" + id)
                            .with(user(OWNER_ID, "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody("1750.00")))
                    .andExpect(status().isOk());

            assertThat(reload(id).getAmount()).isEqualByComparingTo("1750.00");
        }

        @Test
        @DisplayName("an employee gets 403 editing another employee's claim, and nothing changes")
        void otherEmployeeCannotEdit() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(put("/api/expenses/" + id)
                            .with(user(OTHER_EMPLOYEE_ID, "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody("99999.00")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"));

            assertThat(reload(id).getAmount()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("editing a non-editable (APPROVED) claim stays prohibited for its owner")
        void nonEditableStatusStillProhibited() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.APPROVED);

            mockMvc.perform(put("/api/expenses/" + id)
                            .with(user(OWNER_ID, "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody("1750.00")))
                    .andExpect(status().is4xxClientError());

            assertThat(reload(id).getAmount()).isEqualByComparingTo("1500.00");
            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.APPROVED);
        }

        @Test
        @DisplayName("FINANCE cannot edit claims (method security)")
        void financeCannotEdit() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(put("/api/expenses/" + id)
                            .with(user(FINANCE_ID, "FINANCE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody("1750.00")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated edit gets 401")
        void unauthenticated() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(put("/api/expenses/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody("1750.00")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Finance review and reimbursement")
    class ReviewAndReimburse {

        private static final String APPROVE = "{\"decision\":\"APPROVED\"}";

        @Test
        @DisplayName("a Finance user can review and reimburse someone else's claim")
        void financeCanReviewOthers() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(post("/api/expenses/" + id + "/review")
                            .with(user(FINANCE_ID, "EMPLOYEE", "FINANCE"))
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/expenses/" + id + "/reimburse")
                            .with(user(FINANCE_ID, "EMPLOYEE", "FINANCE"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());

            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.REIMBURSED);
        }

        @Test
        @DisplayName("a Finance user who owns the claim cannot review it")
        void financeCannotReviewOwnClaim() throws Exception {
            long id = claimOwnedBy(FINANCE_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(post("/api/expenses/" + id + "/review")
                            .with(user(FINANCE_ID, "EMPLOYEE", "FINANCE"))
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"));

            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
        }

        @Test
        @DisplayName("a Finance user who owns an approved claim cannot mark it reimbursed")
        void financeCannotReimburseOwnClaim() throws Exception {
            long id = claimOwnedBy(FINANCE_ID, ExpenseClaimStatus.APPROVED);

            mockMvc.perform(post("/api/expenses/" + id + "/reimburse")
                            .with(user(FINANCE_ID, "EMPLOYEE", "FINANCE"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());

            assertThat(reload(id).getStatus()).isEqualTo(ExpenseClaimStatus.APPROVED);
        }

        @Test
        @DisplayName("non-Finance users cannot review or reimburse")
        void nonFinanceForbidden() throws Exception {
            long pending = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);
            long approved = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.APPROVED);

            mockMvc.perform(post("/api/expenses/" + pending + "/review")
                            .with(user(OTHER_EMPLOYEE_ID, "EMPLOYEE", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/expenses/" + approved + "/reimburse")
                            .with(user(ADMIN_ID, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());

            assertThat(reload(pending).getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
            assertThat(reload(approved).getStatus()).isEqualTo(ExpenseClaimStatus.APPROVED);
        }

        @Test
        @DisplayName("unauthenticated review gets 401")
        void unauthenticated() throws Exception {
            long id = claimOwnedBy(OWNER_ID, ExpenseClaimStatus.PENDING);

            mockMvc.perform(post("/api/expenses/" + id + "/review")
                            .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                    .andExpect(status().isUnauthorized());
        }
    }
}
