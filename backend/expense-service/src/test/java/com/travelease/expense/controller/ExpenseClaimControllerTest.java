package com.travelease.expense.controller;

import java.math.BigDecimal;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.expense.config.SecurityConfig;
import com.travelease.expense.dto.ExpenseClaimCreateRequest;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseClaimUpdateRequest;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.exception.GlobalExceptionHandler;
import com.travelease.expense.service.ExpenseClaimService;
import com.travelease.expense.service.ExpenseDocumentService;
import com.travelease.expense.service.ExpenseReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the public expense-claim endpoints (tasks.md M-03;
 * api-contract.md §4.2/§8; spec.md FR-EXP-01–06; AC-06, AC-07, AC-08, AC-13):
 *
 * <ul>
 *   <li>201 Created with the created claim on a valid submission (AC-06)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field errors on missing/invalid
 *       structural fields (Bean Validation tier) (AC-06)</li>
 *   <li>422 {@code POLICY_VIOLATION} when the service tier rejects the claim —
 *       VAL-09 (travel request must be APPROVED), VAL-08 (expenseDate outside
 *       the travel date range, field error) (AC-07)</li>
 *   <li>422 {@code POLICY_VIOLATION} on editing a non-PENDING claim (VAL-13,
 *       AC-08) - note: tasks.md M-03 mentions "409" here, but the handler maps
 *       {@link PolicyViolationException} to 422 POLICY_VIOLATION; the 409
 *       {@code DUPLICATE_RESOURCE} mapping is reserved for hard duplicates</li>
 *   <li>422 {@code POLICY_VIOLATION} with a {@code documentIds} field error on
 *       editing an above-threshold claim without attached documents (VAL-10
 *       moved from create to edit; create-then-upload flow) (AC-08)</li>
 *   <li>200 with the claim body / 404 {@code RESOURCE_NOT_FOUND} on GET /{id}
 *       and 200 with a page of claims on the list endpoint (AC-08)</li>
 *   <li>401 without a bearer token, rejected by the security filter before the
 *       service is reached (AC-13)</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5): the module has no top-level
 * {@code @SpringBootApplication} class yet, so the slice is bootstrapped from
 * the shared {@link ExpenseTestApplication} in this package (a single top-level
 * config for the whole package, see its Javadoc). The app's
 * {@link SecurityConfig} is imported so the real JWT resource-server filter
 * chain and {@code @EnableMethodSecurity} (i.e. the {@code @PreAuthorize}
 * gating on the controller) are in effect exactly as in production; its
 * {@code DevJwtDecoders} fallback provides an offline HMAC {@code JwtDecoder},
 * so no auto-configuration exclusions are needed. Authenticated requests use
 * spring-security-test's {@code jwt()} post-processor; the {@code ROLE_EMPLOYEE}
 * authority is set explicitly because the mock token bypasses the
 * {@code JwtRoleConverters} claim-to-authority conversion that the real filter
 * chain would apply. Paths address the servlet mapping directly
 * ({@code /api/expenses}), not the {@code /api} context path of the running
 * server. Only {@code @MockBean}-style mocks (Boot 3.2.5; {@code @MockitoBean}
 * exists only from 3.4) of the controller's three constructor collaborators —
 * {@link ExpenseClaimService}, {@link ExpenseDocumentService} and
 * {@link ExpenseReviewService}; only what each test needs is stubbed.</p>
 */
@WebMvcTest(controllers = ExpenseClaimController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("ExpenseClaimController API (tasks.md M-03; api-contract.md §4.2/§8; spec.md FR-EXP-01–06; AC-06, AC-07, AC-08, AC-13)")
class ExpenseClaimControllerTest {

    private static final String EMPLOYEE_ID = "1";
    private static final String TRAVEL_REQUEST_ID = "10";
    private static final String CLAIM_ID = "100";
    private static final String EDITED_CLAIM_ID = "42";
    private static final String UNKNOWN_CLAIM_ID = "404";

    private static final String AMOUNT = "1500.50";
    private static final String CURRENCY = "INR";
    private static final String CATEGORY = "TRANSPORT";
    private static final String EXPENSE_DATE_ISO = "2027-03-16";

    private static final String APPROVED_REQUEST_REQUIRED_MESSAGE =
            "Travel request must be in APPROVED status to submit an expense claim";
    private static final String DATE_RANGE_MESSAGE =
            "Expense date must fall within travel request date range: 2027-03-15 to 2027-03-19";
    private static final String DOCUMENT_REQUIRED_MESSAGE =
            "A supporting document is required when the amount exceeds the category threshold (VAL-10)";
    private static final String NOT_EDITABLE_MESSAGE = "Expense claim can only be edited when status is PENDING";

    private static final String VALID_CREATE_BODY = """
            {
                "travelRequestId": "%s",
                "amount": %s,
                "currency": "%s",
                "category": "%s",
                "expenseDate": "%s",
                "documentIds": []
            }
            """.formatted(TRAVEL_REQUEST_ID, AMOUNT, CURRENCY, CATEGORY, EXPENSE_DATE_ISO);

    /** travelRequestId omitted (required) and amount negative - structural tier. */
    private static final String INVALID_CREATE_BODY = """
            {
                "amount": -100,
                "currency": "%s",
                "category": "%s",
                "expenseDate": "%s",
                "documentIds": []
            }
            """.formatted(CURRENCY, CATEGORY, EXPENSE_DATE_ISO);

    private static final String VALID_UPDATE_BODY = """
            {
                "travelRequestId": "%s",
                "amount": 1750.00,
                "currency": "%s",
                "category": "%s",
                "expenseDate": "%s",
                "documentIds": []
            }
            """.formatted(TRAVEL_REQUEST_ID, CURRENCY, CATEGORY, EXPENSE_DATE_ISO);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseClaimService expenseClaimService;

    @MockBean
    private ExpenseDocumentService expenseDocumentService;

    @MockBean
    private ExpenseReviewService expenseReviewService;

    /**
     * Bearer-style JWT for the authenticated employee: subject "1" (the caller's
     * logical UserAccount ID per api-contract.md §2) plus the {@code roles} claim
     * and the matching {@code ROLE_EMPLOYEE} authority (see class Javadoc).
     */
    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(EMPLOYEE_ID).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private RequestPostProcessor financeJwt() {
        return jwt()
                .jwt(builder -> builder.subject("2").claim("roles", List.of("FINANCE")))
                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE"));
    }

    private ExpenseClaimResponse createdResponse() {
        return new ExpenseClaimResponse(
                CLAIM_ID,
                EMPLOYEE_ID,
                TRAVEL_REQUEST_ID,
                new BigDecimal(AMOUNT),
                CURRENCY,
                CATEGORY,
                LocalDate.parse(EXPENSE_DATE_ISO),
                ExpenseClaimStatus.PENDING,
                false,
                null,
                null,
                List.of(),
                Instant.parse("2027-03-16T09:00:00Z"),
                Instant.parse("2027-03-16T09:00:01Z"));
    }

    @Nested
    @DisplayName("POST /api/expenses (submit)")
    class Submission {

        @Test
        @DisplayName("valid submission returns 201 with the created claim and the JWT subject as employeeId (AC-06)")
        void validSubmissionReturns201WithCreatedClaim() throws Exception {
            when(expenseClaimService.submitExpenseClaim(eq(TRAVEL_REQUEST_ID), eq(EMPLOYEE_ID),
                    any(ExpenseClaimCreateRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/expenses")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CLAIM_ID))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.travelRequestId").value(TRAVEL_REQUEST_ID))
                    // Numeric compare (isEqualByComparingTo is AssertJ, not Hamcrest; Spring
                    // compares JSON numbers by value, and 1500.5 is exactly representable).
                    .andExpect(jsonPath("$.amount").value(Double.parseDouble(AMOUNT)))
                    .andExpect(jsonPath("$.currency").value(CURRENCY))
                    .andExpect(jsonPath("$.category").value(CATEGORY))
                    .andExpect(jsonPath("$.expenseDate").value(EXPENSE_DATE_ISO))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            // The caller id must come from the JWT subject, not from the body.
            ArgumentCaptor<ExpenseClaimCreateRequest> captor =
                    ArgumentCaptor.forClass(ExpenseClaimCreateRequest.class);
            verify(expenseClaimService).submitExpenseClaim(eq(TRAVEL_REQUEST_ID), eq(EMPLOYEE_ID), captor.capture());
            ExpenseClaimCreateRequest sent = captor.getValue();
            assertThat(sent.getTravelRequestId()).isEqualTo(TRAVEL_REQUEST_ID);
            assertThat(sent.getAmount()).isEqualByComparingTo(new BigDecimal(AMOUNT));
            assertThat(sent.getCurrency()).isEqualTo(CURRENCY);
            assertThat(sent.getCategory()).isEqualTo(CATEGORY);
            assertThat(sent.getExpenseDate()).isEqualTo(LocalDate.parse(EXPENSE_DATE_ISO));
            assertThat(sent.getDocumentIds()).isEmpty();
        }

        @Test
        @DisplayName("missing travelRequestId / negative amount return 400 VALIDATION_ERROR with field errors, service untouched (AC-06)")
        void missingTravelRequestIdAndNegativeAmountReturn400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/expenses")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'travelRequestId')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(expenseClaimService);
        }

        @Test
        @DisplayName("claim against a non-APPROVED travel request (VAL-09) returns 422 POLICY_VIOLATION (AC-07)")
        void submitAgainstNonApprovedRequestReturns422PolicyViolation() throws Exception {
            when(expenseClaimService.submitExpenseClaim(eq(TRAVEL_REQUEST_ID), eq(EMPLOYEE_ID),
                    any(ExpenseClaimCreateRequest.class)))
                    .thenThrow(new PolicyViolationException(APPROVED_REQUEST_REQUIRED_MESSAGE));

            mockMvc.perform(post("/api/expenses")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(APPROVED_REQUEST_REQUIRED_MESSAGE));
        }

        @Test
        @DisplayName("expenseDate outside the travel date range (VAL-08) returns 422 POLICY_VIOLATION for field expenseDate (AC-07)")
        void expenseDateOutsideRangeReturns422WithExpenseDateFieldError() throws Exception {
            when(expenseClaimService.submitExpenseClaim(eq(TRAVEL_REQUEST_ID), eq(EMPLOYEE_ID),
                    any(ExpenseClaimCreateRequest.class)))
                    .thenThrow(new PolicyViolationException("expenseDate", DATE_RANGE_MESSAGE));

            mockMvc.perform(post("/api/expenses")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(DATE_RANGE_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("expenseDate"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(DATE_RANGE_MESSAGE));
        }

        @Test
        @DisplayName("amount above the category threshold without a document returns 201 with the created claim — VAL-10 is enforced on the edit path only (create-then-upload flow) (AC-06)")
        void submissionAboveThresholdWithoutDocumentsReturns201WithCreatedClaim() throws Exception {
            when(expenseClaimService.submitExpenseClaim(eq(TRAVEL_REQUEST_ID), eq(EMPLOYEE_ID),
                    any(ExpenseClaimCreateRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/expenses")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CLAIM_ID))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.travelRequestId").value(TRAVEL_REQUEST_ID))
                    // Numeric compare (isEqualByComparingTo is AssertJ, not Hamcrest; Spring
                    // compares JSON numbers by value, and 1500.5 is exactly representable).
                    .andExpect(jsonPath("$.amount").value(Double.parseDouble(AMOUNT)))
                    .andExpect(jsonPath("$.currency").value(CURRENCY))
                    .andExpect(jsonPath("$.category").value(CATEGORY))
                    .andExpect(jsonPath("$.expenseDate").value(EXPENSE_DATE_ISO))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            // The create path never consults the receipt threshold: an empty
            // documentIds passes straight through (create-then-upload flow).
            ArgumentCaptor<ExpenseClaimCreateRequest> captor =
                    ArgumentCaptor.forClass(ExpenseClaimCreateRequest.class);
            verify(expenseClaimService).submitExpenseClaim(eq(TRAVEL_REQUEST_ID), eq(EMPLOYEE_ID), captor.capture());
            assertThat(captor.getValue().getDocumentIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("PUT /api/expenses/{id} (edit)")
    class Editing {

        @Test
        @DisplayName("editing a non-PENDING claim (VAL-13) returns 422 POLICY_VIOLATION (AC-08)")
        void editingNonPendingClaimReturns422PolicyViolation() throws Exception {
            // DEVIATION from tasks.md M-03, which mentions "409 on editing a
            // non-PENDING claim": the VAL-13 rule raises a PolicyViolationException
            // and GlobalExceptionHandler maps PolicyViolationException to 422
            // POLICY_VIOLATION per api-contract.md §8 (409 is DUPLICATE_RESOURCE
            // only). We assert the status code and error code the handler actually
            // produces rather than inventing a 409 it never returns.
            when(expenseClaimService.editExpenseClaim(eq(EDITED_CLAIM_ID), eq(Long.valueOf(EMPLOYEE_ID)), any(ExpenseClaimUpdateRequest.class)))
                    .thenThrow(new PolicyViolationException(NOT_EDITABLE_MESSAGE));

            mockMvc.perform(put("/api/expenses/" + EDITED_CLAIM_ID)
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(NOT_EDITABLE_MESSAGE));
        }

        @Test
        @DisplayName("editing an above-threshold claim without attached documents (VAL-10) returns 422 POLICY_VIOLATION for field documentIds (AC-08)")
        void editAboveThresholdWithoutDocumentsReturns422WithDocumentIdsFieldError() throws Exception {
            when(expenseClaimService.editExpenseClaim(eq(EDITED_CLAIM_ID), eq(Long.valueOf(EMPLOYEE_ID)), any(ExpenseClaimUpdateRequest.class)))
                    .thenThrow(new PolicyViolationException("documentIds", DOCUMENT_REQUIRED_MESSAGE));

            mockMvc.perform(put("/api/expenses/" + EDITED_CLAIM_ID)
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(DOCUMENT_REQUIRED_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("documentIds"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(DOCUMENT_REQUIRED_MESSAGE));
        }
    }

    @Nested
    @DisplayName("GET /api/expenses/{id}")
    class Fetching {

        @Test
        @DisplayName("existing claim returns 200 with the claim body")
        void existingClaimReturns200() throws Exception {
            when(expenseClaimService.getExpenseClaim(eq(CLAIM_ID), anyLong(), anySet()))
                    .thenReturn(createdResponse());

            mockMvc.perform(get("/api/expenses/" + CLAIM_ID).with(employeeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CLAIM_ID))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.travelRequestId").value(TRAVEL_REQUEST_ID))
                    // Numeric compare (see the note in the Submission test above).
                    .andExpect(jsonPath("$.amount").value(Double.parseDouble(AMOUNT)))
                    .andExpect(jsonPath("$.currency").value(CURRENCY))
                    .andExpect(jsonPath("$.category").value(CATEGORY))
                    .andExpect(jsonPath("$.expenseDate").value(EXPENSE_DATE_ISO))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("unknown id returns 404 RESOURCE_NOT_FOUND")
        void unknownIdReturns404() throws Exception {
            when(expenseClaimService.getExpenseClaim(eq(UNKNOWN_CLAIM_ID), anyLong(), anySet()))
                    .thenThrow(new ResourceNotFoundException("ExpenseClaim", UNKNOWN_CLAIM_ID));

            mockMvc.perform(get("/api/expenses/" + UNKNOWN_CLAIM_ID).with(employeeJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "ExpenseClaim with id " + UNKNOWN_CLAIM_ID + " not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/expenses (list)")
    class Listing {

        @Test
        @DisplayName("employee list is scoped to the authenticated employee")
        void employeeListIsScopedToAuthenticatedEmployee() throws Exception {
            when(expenseClaimService.listExpenseClaims(eq(EMPLOYEE_ID), eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(createdResponse())));

            mockMvc.perform(get("/api/expenses").with(employeeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(CLAIM_ID));

            verify(expenseClaimService).listExpenseClaims(eq(EMPLOYEE_ID), eq(null), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("finance can list claims across employees")
        void financeListIncludesAllEmployees() throws Exception {
            when(expenseClaimService.listExpenseClaims(eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(createdResponse())));

            mockMvc.perform(get("/api/expenses").with(financeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(CLAIM_ID));

            verify(expenseClaimService).listExpenseClaims(eq(null), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("finance list binds and forwards status and duplicate filters")
        void financeListForwardsFilters() throws Exception {
            when(expenseClaimService.listExpenseClaims(
                    eq(ExpenseClaimStatus.PENDING), eq(true), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(createdResponse())));

            mockMvc.perform(get("/api/expenses")
                            .param("status", "PENDING")
                            .param("duplicateFlag", "true")
                            .with(financeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(expenseClaimService).listExpenseClaims(
                    eq(ExpenseClaimStatus.PENDING), eq(true), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Security")
    class TokenlessAccess {

        @Test
        @DisplayName("POST without a bearer token is rejected with 401 by the security filter, service never called")
        void postWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(expenseClaimService);
        }
    }
}