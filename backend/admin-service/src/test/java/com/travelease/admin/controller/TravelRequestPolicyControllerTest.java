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
import com.travelease.admin.dto.TravelRequestPolicyRequest;
import com.travelease.admin.dto.TravelRequestPolicyResponse;
import com.travelease.admin.exception.GlobalExceptionHandler;
import com.travelease.admin.service.EmployeeManagerMappingAdminService;
import com.travelease.admin.service.EmployeeManagerRoutingService;
import com.travelease.admin.service.ExpenseCategoryService;
import com.travelease.admin.service.PolicyConfigService;
import com.travelease.admin.service.TravelRequestPolicyService;
import com.travelease.admin.service.UserAdminService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the Admin travel-request policy endpoints (spec.md
 * VAL-04, FR-TR-02, FR-ADM-02; api-contract.md §4.3/§8):
 *
 * <ul>
 *   <li>200 with the active policy body on
 *       {@code GET /api/admin/travel-request-policy} for an ADMIN caller — the
 *       safe MVP default ({@code documentsMandatory = false}) is resolved by the
 *       service tier, not the controller (clarification Q7)</li>
 *   <li>200 on the same GET for a FINANCE caller —
 *       {@code @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")}</li>
 *   <li>200 on {@code PUT /api/admin/travel-request-policy} with a valid body —
 *       the validated {@code documentsMandatory} flag is delegated unchanged to
 *       the service (FR-ADM-02 singleton upsert)</li>
 *   <li>400 {@code VALIDATION_ERROR} with a {@code documentsMandatory} field
 *       error when the body omits/nulls the required flag (Bean Validation
 *       tier) (api-contract.md §8)</li>
 *   <li>403 {@code FORBIDDEN_ACTION} for a non-Admin identity on the PUT —
 *       {@code @PreAuthorize("hasRole('ADMIN')")} denial, translated by the
 *       dedicated {@code AccessDeniedException} handler (NFR-10,
 *       api-contract.md §8)</li>
 *   <li>401 without a bearer token, rejected by the security filter before the
 *       service is reached (api-contract.md §2)</li>
 *   <li>200 with the {@code documentsMandatory} snapshot on
 *       {@code GET /internal/travel-request-policy} for a non-admin (EMPLOYEE)
 *       caller — the internal endpoint accepts any authenticated role and the
 *       service is consulted (VAL-04, FR-TR-02)</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5): the slice is bootstrapped from the shared
 * {@link AdminTestApplication} in this package (a single top-level config for
 * the whole package, see its Javadoc). The app's {@link SecurityConfig} is
 * imported so the real JWT resource-server filter chain and
 * {@code @EnableMethodSecurity} (i.e. the {@code @PreAuthorize} gating on the
 * controller) are in effect exactly as in production; its
 * {@code DevJwtDecoders} fallback provides an offline HMAC {@code JwtDecoder},
 * so no auto-configuration exclusions are needed. Both the admin
 * {@link TravelRequestPolicyController} and the service-to-service
 * {@link InternalPolicyController} are registered in the same slice since both
 * delegate to the single {@link TravelRequestPolicyService} collaborator,
 * which is {@code @MockBean}-mocked (Boot 3.2.5; {@code @MockitoBean} exists
 * only from 3.4). Authenticated requests use spring-security-test's
 * {@code jwt()} post-processor; the {@code ROLE_ADMIN} / {@code ROLE_FINANCE} /
 * {@code ROLE_EMPLOYEE} authorities are set explicitly because the mock token
 * bypasses the {@code JwtRoleConverters} claim-to-authority conversion that
 * the real filter chain would apply. Paths address the servlet mapping
 * directly, not the {@code /api} context path of the running server.</p>
 */
@WebMvcTest(controllers = {TravelRequestPolicyController.class, InternalPolicyController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("TravelRequestPolicyController API (spec.md VAL-04, FR-TR-02, FR-ADM-02)")
class TravelRequestPolicyControllerTest {

    private static final String ADMIN_SUBJECT = "5";
    private static final String FINANCE_SUBJECT = "4";
    private static final String EMPLOYEE_SUBJECT = "1";

    private static final String ADMIN_PATH = "/api/admin/travel-request-policy";
    private static final String INTERNAL_PATH = "/internal/travel-request-policy";

    private static final String POLICY_ID = "7";

    private static final String VALID_UPSERT_BODY = """
            {
                "documentsMandatory": true
            }
            """;

    /** documentsMandatory explicitly null - violates the @NotNull rule (Bean Validation tier). */
    private static final String INVALID_UPSERT_BODY = """
            {
                "documentsMandatory": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TravelRequestPolicyService travelRequestPolicyService;

    // Sibling-controller collaborators, mocked so their beans exist under the
    // shared component-scanning config.
    @MockBean
    private PolicyConfigService policyConfigService;

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

    /** Bearer-style JWT for a finance caller: subject "4" plus {@code ROLE_FINANCE}. */
    private RequestPostProcessor financeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(FINANCE_SUBJECT).claim("roles", List.of("FINANCE")))
                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE"));
    }

    /**
     * Bearer-style JWT for a non-admin employee (subject "1") — used for the
     * RBAC denial cases and the any-authenticated-role internal endpoint.
     */
    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(EMPLOYEE_SUBJECT).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private TravelRequestPolicyResponse policyResponse() {
        return new TravelRequestPolicyResponse(Long.parseLong(POLICY_ID), true, true);
    }

    @Nested
    @DisplayName("GET /api/admin/travel-request-policy")
    class Fetching {

        @Test
        @DisplayName("returns 200 with the active policy body as ADMIN")
        void getAsAdminReturns200() throws Exception {
            when(travelRequestPolicyService.getActivePolicy()).thenReturn(policyResponse());

            mockMvc.perform(get(ADMIN_PATH).with(adminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(POLICY_ID))
                    .andExpect(jsonPath("$.documentsMandatory").value(true))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("returns 200 with the active policy body as FINANCE (hasAnyRole('ADMIN', 'FINANCE'))")
        void getAsFinanceReturns200() throws Exception {
            when(travelRequestPolicyService.getActivePolicy()).thenReturn(policyResponse());

            mockMvc.perform(get(ADMIN_PATH).with(financeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(POLICY_ID))
                    .andExpect(jsonPath("$.documentsMandatory").value(true))
                    .andExpect(jsonPath("$.active").value(true));

            verify(travelRequestPolicyService).getActivePolicy();
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/travel-request-policy (upsert)")
    class Updating {

        @Test
        @DisplayName("valid upsert returns 200 and delegates the documentsMandatory flag to the service")
        void validUpsertReturns200AndDelegates() throws Exception {
            when(travelRequestPolicyService.upsertPolicy(any(TravelRequestPolicyRequest.class)))
                    .thenReturn(policyResponse());

            mockMvc.perform(put(ADMIN_PATH)
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPSERT_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(POLICY_ID))
                    .andExpect(jsonPath("$.documentsMandatory").value(true))
                    .andExpect(jsonPath("$.active").value(true));

            ArgumentCaptor<TravelRequestPolicyRequest> captor =
                    ArgumentCaptor.forClass(TravelRequestPolicyRequest.class);
            verify(travelRequestPolicyService).upsertPolicy(captor.capture());
            assertThat(captor.getValue().documentsMandatory()).isTrue();
        }

        @Test
        @DisplayName("null documentsMandatory returns 400 VALIDATION_ERROR with a documentsMandatory field error, service untouched")
        void nullFlagReturns400WithFieldError() throws Exception {
            mockMvc.perform(put(ADMIN_PATH)
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_UPSERT_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'documentsMandatory')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(travelRequestPolicyService);
        }
    }

    @Nested
    @DisplayName("Security")
    class RbacEnforcement {

        @Test
        @DisplayName("PUT without ROLE_ADMIN is denied with 403 FORBIDDEN_ACTION, service never called")
        void employeePutReturns403() throws Exception {
            mockMvc.perform(put(ADMIN_PATH)
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPSERT_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(travelRequestPolicyService);
        }

        @Test
        @DisplayName("PUT without a bearer token is rejected with 401 by the security filter, service never called")
        void putWithoutTokenReturns401() throws Exception {
            mockMvc.perform(put(ADMIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPSERT_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(travelRequestPolicyService);
        }
    }

    @Nested
    @DisplayName("GET /internal/travel-request-policy (service-to-service)")
    class InternalMandateLookup {

        @Test
        @DisplayName("returns 200 with the documentsMandatory snapshot for a trusted internal service caller")
        void internalServiceLookupReturns200WithSnapshot() throws Exception {
            when(travelRequestPolicyService.getActivePolicy()).thenReturn(policyResponse());

            mockMvc.perform(get(INTERNAL_PATH).header(
                            com.travelease.common.security.internal.InternalServiceTokens.HEADER,
                            new com.travelease.common.security.internal.InternalServiceTokens(
                                    "test-only-internal-secret-never-deploy-0123456789", "travel-request-service")
                                    .currentToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentsMandatory").value(true));

            verify(travelRequestPolicyService).getActivePolicy();
        }

        @Test
        @DisplayName("an end user is not enough for /internal/** and the service is not consulted (#12)")
        void userJwtRejected() throws Exception {
            // jwt() places an authenticated end user directly in the security context: authenticated,
            // but not an internal service -> 403. (A real Bearer header is not even read on this chain;
            // that path answers 401 — see the audit/notification ingestion tests.)
            mockMvc.perform(get(INTERNAL_PATH).with(employeeJwt())).andExpect(status().isForbidden());
            mockMvc.perform(get(INTERNAL_PATH)).andExpect(status().isUnauthorized());

            org.mockito.Mockito.verifyNoInteractions(travelRequestPolicyService);
        }
    }
}