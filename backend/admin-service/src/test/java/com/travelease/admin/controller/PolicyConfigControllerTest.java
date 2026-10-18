package com.travelease.admin.controller;

import java.math.BigDecimal;
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
import com.travelease.admin.dto.PolicyConfigRequest;
import com.travelease.admin.dto.PolicyConfigResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the Admin expense policy configuration endpoints
 * (tasks.md M-05; api-contract.md §4.3/§8; spec.md FR-ADM-02, AC-08):
 *
 * <ul>
 *   <li>201 Created with the persisted policy config on a valid create
 *       (FR-ADM-02, Q7) — the compact-constructor default
 *       {@code active = true} is applied when the field is omitted
 *       (api-contract.md §5.14)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field errors on missing/invalid
 *       structural fields (Bean Validation tier) (api-contract.md §8)</li>
 *   <li>409 {@code DUPLICATE_RESOURCE} when the service tier reports an
 *       active policy for the same category (FR-ADM-02, service-layer
 *       business rule)</li>
 *   <li>404 {@code RESOURCE_NOT_FOUND} when the referenced expense category
 *       does not exist (service-layer business rule, FR-ADM-02)</li>
 *   <li>200 with the config body / 404 {@code RESOURCE_NOT_FOUND} on
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
 * directly ({@code /api/admin/policy-configs}), not the {@code /api} context
 * path of the running server. The controller's one constructor collaborator —
 * {@link PolicyConfigService} — plus the sibling controllers' collaborators
 * ({@link UserAdminService}, {@link EmployeeManagerMappingAdminService},
 * {@link ExpenseCategoryService}, and {@link EmployeeManagerRoutingService})
 * are {@code @MockBean}-mocked (Boot 3.2.5; {@code @MockitoBean} exists only
 * from 3.4) so their beans exist under the shared component-scanning config;
 * only what each test needs is stubbed.</p>
 */
@WebMvcTest(controllers = PolicyConfigController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("PolicyConfigController API (tasks.md M-05; api-contract.md §4.3/§8; spec.md FR-ADM-02, AC-08)")
class PolicyConfigControllerTest {

    private static final String ADMIN_SUBJECT = "5";
    private static final String EMPLOYEE_SUBJECT = "1";

    private static final String CREATED_CONFIG_ID = "9";
    private static final String UNKNOWN_CONFIG_ID = "404";

    private static final String CATEGORY_CODE = "TRANSPORT";
    private static final String CURRENCY = "INR";
    private static final BigDecimal THRESHOLD = new BigDecimal("1000.00");

    /** Active omitted on purpose - the compact constructor must default it to true. */
    private static final String VALID_CREATE_BODY = """
            {
                "categoryCode": "TRANSPORT",
                "receiptThresholdAmount": 1000.00,
                "currency": "INR"
            }
            """;

    /** categoryCode missing (required) and receiptThresholdAmount negative - structural tier. */
    private static final String INVALID_CREATE_BODY = """
            {
                "receiptThresholdAmount": -50,
                "currency": "INR"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PolicyConfigService policyConfigService;

    // Sibling-controller collaborators, mocked so their beans exist under the
    // shared component-scanning config.
    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private EmployeeManagerMappingAdminService employeeManagerMappingAdminService;

    @MockBean
    private ExpenseCategoryService expenseCategoryService;

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

    private PolicyConfigResponse createdResponse() {
        return new PolicyConfigResponse(
                Long.parseLong(CREATED_CONFIG_ID),
                CATEGORY_CODE,
                THRESHOLD,
                CURRENCY,
                Boolean.TRUE);
    }

    @Nested
    @DisplayName("POST /api/admin/policy-configs (create)")
    class Creating {

        @Test
        @DisplayName("valid create returns 201 with the persisted policy config and the active default applied")
        void validCreateReturns201WithConfig() throws Exception {
            when(policyConfigService.createPolicyConfig(any(PolicyConfigRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/admin/policy-configs")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CREATED_CONFIG_ID))
                    .andExpect(jsonPath("$.categoryCode").value(CATEGORY_CODE))
                    .andExpect(jsonPath("$.receiptThresholdAmount").value(1000.0))
                    .andExpect(jsonPath("$.currency").value(CURRENCY))
                    .andExpect(jsonPath("$.active").value(true));

            // The record must arrive with the compact-constructor default:
            // active was omitted in the body, so the service sees true.
            ArgumentCaptor<PolicyConfigRequest> captor = ArgumentCaptor.forClass(PolicyConfigRequest.class);
            verify(policyConfigService).createPolicyConfig(captor.capture());
            PolicyConfigRequest sent = captor.getValue();
            assertThat(sent.categoryCode()).isEqualTo(CATEGORY_CODE);
            assertThat(sent.receiptThresholdAmount()).isEqualByComparingTo(THRESHOLD);
            assertThat(sent.currency()).isEqualTo(CURRENCY);
            assertThat(sent.active()).isTrue();
        }

        @Test
        @DisplayName("missing categoryCode and negative threshold return 400 VALIDATION_ERROR with field errors, service untouched")
        void invalidBodyReturns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/admin/policy-configs")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'categoryCode')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'receiptThresholdAmount')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(policyConfigService);
        }

        @Test
        @DisplayName("duplicate active policy for the category reported by the service tier returns 409 DUPLICATE_RESOURCE")
        void duplicateActivePolicyReturns409() throws Exception {
            when(policyConfigService.createPolicyConfig(any(PolicyConfigRequest.class)))
                    .thenThrow(new DuplicateResourceException(
                            "An active policy configuration for category " + CATEGORY_CODE + " already exists"));

            mockMvc.perform(post("/api/admin/policy-configs")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                    .andExpect(jsonPath("$.message").value(
                            "An active policy configuration for category " + CATEGORY_CODE + " already exists"));
        }

        @Test
        @DisplayName("unknown category code returns 404 RESOURCE_NOT_FOUND")
        void unknownCategoryOnCreateReturns404() throws Exception {
            when(policyConfigService.createPolicyConfig(any(PolicyConfigRequest.class)))
                    .thenThrow(new ResourceNotFoundException("ExpenseCategory", CATEGORY_CODE));

            mockMvc.perform(post("/api/admin/policy-configs")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "ExpenseCategory with id " + CATEGORY_CODE + " not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/policy-configs/{id}")
    class Fetching {

        @Test
        @DisplayName("existing policy config returns 200 with the config body")
        void existingConfigReturns200() throws Exception {
            when(policyConfigService.getPolicyConfig(Long.parseLong(CREATED_CONFIG_ID)))
                    .thenReturn(createdResponse());

            mockMvc.perform(get("/api/admin/policy-configs/" + CREATED_CONFIG_ID).with(adminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CREATED_CONFIG_ID))
                    .andExpect(jsonPath("$.categoryCode").value(CATEGORY_CODE))
                    .andExpect(jsonPath("$.receiptThresholdAmount").value(1000.0))
                    .andExpect(jsonPath("$.currency").value(CURRENCY))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("unknown id returns 404 RESOURCE_NOT_FOUND")
        void unknownIdReturns404() throws Exception {
            when(policyConfigService.getPolicyConfig(Long.parseLong(UNKNOWN_CONFIG_ID)))
                    .thenThrow(new ResourceNotFoundException("PolicyConfig", UNKNOWN_CONFIG_ID));

            mockMvc.perform(get("/api/admin/policy-configs/" + UNKNOWN_CONFIG_ID).with(adminJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "PolicyConfig with id " + UNKNOWN_CONFIG_ID + " not found"));
        }
    }

    @Nested
    @DisplayName("Security")
    class RbacEnforcement {

        @Test
        @DisplayName("POST without ROLE_ADMIN is denied with 403 FORBIDDEN_ACTION, service never called")
        void employeeCreateReturns403() throws Exception {
            mockMvc.perform(post("/api/admin/policy-configs")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(policyConfigService);
        }

        @Test
        @DisplayName("POST without a bearer token is rejected with 401 by the security filter, service never called")
        void createWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/admin/policy-configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(policyConfigService);
        }
    }
}