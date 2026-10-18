package com.travelease.admin.controller;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.admin.config.SecurityConfig;
import com.travelease.admin.dto.ExpenseCategoryRequest;
import com.travelease.admin.dto.ExpenseCategoryResponse;
import com.travelease.admin.exception.GlobalExceptionHandler;
import com.travelease.admin.service.EmployeeManagerMappingAdminService;
import com.travelease.admin.service.EmployeeManagerRoutingService;
import com.travelease.admin.service.ExpenseCategoryService;
import com.travelease.admin.service.PolicyConfigService;
import com.travelease.admin.service.UserAdminService;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
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
 * MockMvc API tests for the Admin expense category endpoints (tasks.md M-05;
 * api-contract.md §4.3/§8; spec.md FR-ADM-02, FR-EXP-04):
 *
 * <ul>
 *   <li>201 Created with the persisted category on a valid create
 *       (FR-ADM-02, FR-EXP-04) — the compact-constructor default
 *       {@code active = true} is applied when the field is omitted
 *       (api-contract.md §5.15)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field errors on missing structural
 *       fields (Bean Validation tier) (api-contract.md §8)</li>
 *   <li>409 {@code DUPLICATE_RESOURCE} when the service tier reports a
 *       duplicate category code (FR-EXP-04, service-layer business rule)</li>
 *   <li>200 with the in-place updated category on PUT /{id}, and 409
 *       {@code DUPLICATE_RESOURCE} when the code is already owned by another
 *       category (data-model.md §5.5)</li>
 *   <li>200 with the category body / 404 {@code RESOURCE_NOT_FOUND} on
 *       GET /{id} (api-contract.md §4.3)</li>
 *   <li>403 {@code FORBIDDEN_ACTION} for a non-Admin identity on the write
 *       endpoint — {@code @PreAuthorize("hasRole('ADMIN')")} denial, translated
 *       by the dedicated {@code AccessDeniedException} handler (NFR-10,
 *       api-contract.md §8)</li>
 *   <li>401 without a bearer token, rejected by the security filter before the
 *       service is reached (api-contract.md §2)</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5): the module has no top-level
 * {@code @SpringBootApplication} class yet, so the slice is bootstrapped from
 * the shared {@link AdminTestApplication} in this package (a single top-level
 * config for the whole package, see its Javadoc). The app's
 * {@link SecurityConfig} is imported so the real JWT resource-server filter
 * chain and {@code @EnableMethodSecurity} (i.e. the {@code @PreAuthorize}
 * gating on the controller) are in effect exactly as in production; its
 * {@code DevJwtDecoders} fallback provides an offline HMAC {@code JwtDecoder},
 * so no auto-configuration exclusions are needed. Authenticated requests use
 * spring-security-test's {@code jwt()} post-processor; the {@code ROLE_ADMIN}
 * / {@code ROLE_EMPLOYEE} authorities are set explicitly because the mock
 * token bypasses the {@code JwtRoleConverters} claim-to-authority conversion
 * that the real filter chain would apply. Paths address the servlet mapping
 * directly ({@code /api/admin/expense-categories}), not the {@code /api}
 * context path of the running server. The controller's one constructor
 * collaborator — {@link ExpenseCategoryService} — plus the sibling controllers'
 * collaborators ({@link UserAdminService},
 * {@link EmployeeManagerMappingAdminService}, {@link PolicyConfigService}, and
 * {@link EmployeeManagerRoutingService}) are {@code @MockBean}-mocked (Boot
 * 3.2.5; {@code @MockitoBean} exists only from 3.4) so their beans exist under
 * the shared component-scanning config; only what each test needs is stubbed.
 * </p>
 */
@WebMvcTest(controllers = ExpenseCategoryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("ExpenseCategoryController API (tasks.md M-05; api-contract.md §4.3/§8; spec.md FR-ADM-02, FR-EXP-04)")
class ExpenseCategoryControllerTest {

    private static final String ADMIN_SUBJECT = "5";
    private static final String EMPLOYEE_SUBJECT = "1";

    private static final String CREATED_CATEGORY_ID = "3";
    private static final String UNKNOWN_CATEGORY_ID = "404";

    private static final String CODE = "TRANSPORT";
    private static final String LABEL = "Transport";
    private static final String UPDATED_LABEL = "Transport & Travel";

    /** Active omitted on purpose - the compact constructor must default it to true. */
    private static final String VALID_CREATE_BODY = """
            {
                "code": "TRANSPORT",
                "label": "Transport"
            }
            """;

    /** Active omitted on purpose - the compact constructor must default it to true. */
    private static final String VALID_UPDATE_BODY = """
            {
                "code": "TRANSPORT",
                "label": "Transport & Travel"
            }
            """;

    /** code and label both missing (both required) - structural tier. */
    private static final String INVALID_CREATE_BODY = "{}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseCategoryService expenseCategoryService;

    // Sibling-controller collaborators, mocked so their beans exist under the
    // shared component-scanning config.
    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private EmployeeManagerMappingAdminService employeeManagerMappingAdminService;

    @MockBean
    private PolicyConfigService policyConfigService;

    @MockBean
    private EmployeeManagerRoutingService employeeManagerRoutingService;

    /**
     * Bearer-style JWT for an admin caller: subject "5" (the caller's logical
     * UserAccount ID per api-contract.md §2) plus the {@code roles} claim and
     * the matching {@code ROLE_ADMIN} authority (see class Javadoc).
     */
    private RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject(ADMIN_SUBJECT).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /**
     * Bearer-style JWT for a non-admin employee (subject "1") — used for the
     * RBAC denial cases.
     */
    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(EMPLOYEE_SUBJECT).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private ExpenseCategoryResponse createdResponse() {
        return new ExpenseCategoryResponse(
                Long.parseLong(CREATED_CATEGORY_ID),
                CODE,
                LABEL,
                Boolean.TRUE);
    }

    private ExpenseCategoryResponse updatedResponse() {
        return new ExpenseCategoryResponse(
                Long.parseLong(CREATED_CATEGORY_ID),
                CODE,
                UPDATED_LABEL,
                Boolean.TRUE);
    }

    @Nested
    @DisplayName("POST /api/admin/expense-categories (create)")
    class Creating {

        @Test
        @DisplayName("valid create returns 201 with the persisted category and the active default applied")
        void validCreateReturns201WithCategory() throws Exception {
            when(expenseCategoryService.createExpenseCategory(any(ExpenseCategoryRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/admin/expense-categories")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CREATED_CATEGORY_ID))
                    .andExpect(jsonPath("$.code").value(CODE))
                    .andExpect(jsonPath("$.label").value(LABEL))
                    .andExpect(jsonPath("$.active").value(true));

            // The record must arrive with the compact-constructor default:
            // active was omitted in the body, so the service sees true.
            ArgumentCaptor<ExpenseCategoryRequest> captor = ArgumentCaptor.forClass(ExpenseCategoryRequest.class);
            verify(expenseCategoryService).createExpenseCategory(captor.capture());
            ExpenseCategoryRequest sent = captor.getValue();
            assertThat(sent.code()).isEqualTo(CODE);
            assertThat(sent.label()).isEqualTo(LABEL);
            assertThat(sent.active()).isTrue();
        }

        @Test
        @DisplayName("missing code and label return 400 VALIDATION_ERROR with field errors, service untouched")
        void invalidBodyReturns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/admin/expense-categories")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'code')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'label')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(expenseCategoryService);
        }

        @Test
        @DisplayName("duplicate category code reported by the service tier returns 409 DUPLICATE_RESOURCE")
        void duplicateCodeReturns409() throws Exception {
            when(expenseCategoryService.createExpenseCategory(any(ExpenseCategoryRequest.class)))
                    .thenThrow(new DuplicateResourceException(
                            "An expense category with code " + CODE + " already exists"));

            mockMvc.perform(post("/api/admin/expense-categories")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                    .andExpect(jsonPath("$.message").value(
                            "An expense category with code " + CODE + " already exists"));
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/expense-categories/{id} (update)")
    class Updating {

        @Test
        @DisplayName("update returns 200 with the in-place updated category and the active default applied")
        void updateReturns200WithUpdatedCategory() throws Exception {
            when(expenseCategoryService.updateExpenseCategory(eq(Long.parseLong(CREATED_CATEGORY_ID)),
                    any(ExpenseCategoryRequest.class)))
                    .thenReturn(updatedResponse());

            mockMvc.perform(put("/api/admin/expense-categories/" + CREATED_CATEGORY_ID)
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CREATED_CATEGORY_ID))
                    .andExpect(jsonPath("$.code").value(CODE))
                    .andExpect(jsonPath("$.label").value(UPDATED_LABEL))
                    .andExpect(jsonPath("$.active").value(true));

            // The record must arrive with the compact-constructor default:
            // active was omitted in the body, so the service sees true.
            ArgumentCaptor<ExpenseCategoryRequest> captor = ArgumentCaptor.forClass(ExpenseCategoryRequest.class);
            verify(expenseCategoryService)
                    .updateExpenseCategory(eq(Long.parseLong(CREATED_CATEGORY_ID)), captor.capture());
            ExpenseCategoryRequest sent = captor.getValue();
            assertThat(sent.code()).isEqualTo(CODE);
            assertThat(sent.label()).isEqualTo(UPDATED_LABEL);
            assertThat(sent.active()).isTrue();
        }

        @Test
        @DisplayName("code already owned by another category (AndIdNot) reported by the service tier returns 409 DUPLICATE_RESOURCE")
        void duplicateCodeOnUpdateReturns409() throws Exception {
            when(expenseCategoryService.updateExpenseCategory(eq(Long.parseLong(CREATED_CATEGORY_ID)),
                    any(ExpenseCategoryRequest.class)))
                    .thenThrow(new DuplicateResourceException(
                            "An expense category with code " + CODE + " already exists"));

            mockMvc.perform(put("/api/admin/expense-categories/" + CREATED_CATEGORY_ID)
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                    .andExpect(jsonPath("$.message").value(
                            "An expense category with code " + CODE + " already exists"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/expense-categories/{id}")
    class Fetching {

        @Test
        @DisplayName("existing category returns 200 with the category body")
        void existingCategoryReturns200() throws Exception {
            when(expenseCategoryService.getExpenseCategory(Long.parseLong(CREATED_CATEGORY_ID)))
                    .thenReturn(createdResponse());

            mockMvc.perform(get("/api/admin/expense-categories/" + CREATED_CATEGORY_ID).with(adminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CREATED_CATEGORY_ID))
                    .andExpect(jsonPath("$.code").value(CODE))
                    .andExpect(jsonPath("$.label").value(LABEL))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("unknown id returns 404 RESOURCE_NOT_FOUND")
        void unknownIdReturns404() throws Exception {
            when(expenseCategoryService.getExpenseCategory(Long.parseLong(UNKNOWN_CATEGORY_ID)))
                    .thenThrow(new ResourceNotFoundException("ExpenseCategory", UNKNOWN_CATEGORY_ID));

            mockMvc.perform(get("/api/admin/expense-categories/" + UNKNOWN_CATEGORY_ID).with(adminJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "ExpenseCategory with id " + UNKNOWN_CATEGORY_ID + " not found"));
        }
    }

    @Nested
    @DisplayName("Security")
    class RbacEnforcement {

        @Test
        @DisplayName("POST without ROLE_ADMIN is denied with 403 FORBIDDEN_ACTION, service never called")
        void employeeCreateReturns403() throws Exception {
            mockMvc.perform(post("/api/admin/expense-categories")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(expenseCategoryService);
        }

        @Test
        @DisplayName("POST without a bearer token is rejected with 401 by the security filter, service never called")
        void createWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/admin/expense-categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(expenseCategoryService);
        }
    }
}