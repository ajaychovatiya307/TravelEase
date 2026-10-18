package com.travelease.admin.controller;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.admin.config.SecurityConfig;
import com.travelease.admin.dto.EmployeeManagerMappingRequest;
import com.travelease.admin.dto.EmployeeManagerMappingResponse;
import com.travelease.admin.exception.GlobalExceptionHandler;
import com.travelease.admin.service.EmployeeManagerMappingAdminService;
import com.travelease.admin.service.EmployeeManagerRoutingService;
import com.travelease.admin.service.ExpenseCategoryService;
import com.travelease.admin.service.PolicyConfigService;
import com.travelease.admin.service.UserAdminService;
import com.travelease.common.exception.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the Admin Employee→Manager mapping endpoints (tasks.md
 * M-05; api-contract.md §4.3/§8; spec.md FR-ADM-01):
 *
 * <ul>
 *   <li>201 Created with the persisted mapping on a valid create/update — the
 *       endpoint is an upsert keyed on {@code employeeId} (api-contract.md
 *       §4.3, FR-ADM-01, Q5)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field errors on missing structural
 *       fields (Bean Validation tier) (api-contract.md §8)</li>
 *   <li>{@code managerId: null} is accepted for top-of-hierarchy managers,
 *       whose approvals fall back to Finance (clarification Q3, FR-TR-05;
 *       api-contract.md §5.13)</li>
 *   <li>404 {@code RESOURCE_NOT_FOUND} when the employee user does not exist
 *       (service-layer business rule, FR-ADM-01)</li>
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
 * directly ({@code /api/admin/employee-manager-mappings}), not the {@code /api}
 * context path of the running server. The controller's one constructor
 * collaborator — {@link EmployeeManagerMappingAdminService} — plus the sibling
 * controllers' collaborators ({@link UserAdminService},
 * {@link PolicyConfigService}, {@link ExpenseCategoryService}, and
 * {@link EmployeeManagerRoutingService}) are {@code @MockBean}-mocked (Boot
 * 3.2.5; {@code @MockitoBean} exists only from 3.4) so their beans exist under
 * the shared component-scanning config; only what each test needs is
 * stubbed.</p>
 */
@WebMvcTest(controllers = EmployeeManagerMappingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("EmployeeManagerMappingController API (tasks.md M-05; api-contract.md §4.3/§8; spec.md FR-ADM-01)")
class EmployeeManagerMappingControllerTest {

    private static final String ADMIN_SUBJECT = "5";
    private static final String EMPLOYEE_SUBJECT = "1";

    private static final String MAPPING_ID = "10";
    private static final String EMPLOYEE_ID = "2";
    private static final String MANAGER_ID = "3";
    private static final String EFFECTIVE_FROM = "2026-07-01";

    private static final String VALID_CREATE_BODY = """
            {
                "employeeId": %s,
                "managerId": %s,
                "effectiveFrom": "%s"
            }
            """.formatted(EMPLOYEE_ID, MANAGER_ID, EFFECTIVE_FROM);

    /** Top of hierarchy: no manager (clarification Q3) - must be accepted. */
    private static final String VALID_TOP_LEVEL_BODY = """
            {
                "employeeId": %s,
                "managerId": null,
                "effectiveFrom": "%s"
            }
            """.formatted(EMPLOYEE_ID, EFFECTIVE_FROM);

    /** employeeId and effectiveFrom missing (required) - structural tier. */
    private static final String INVALID_CREATE_BODY = """
            {
                "managerId": %s
            }
            """.formatted(MANAGER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeManagerMappingAdminService mappingAdminService;

    // Sibling-controller collaborators, mocked so their beans exist under the
    // shared component-scanning config.
    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private PolicyConfigService policyConfigService;

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

    private EmployeeManagerMappingResponse createdResponse() {
        return new EmployeeManagerMappingResponse(
                Long.parseLong(MAPPING_ID),
                Long.parseLong(EMPLOYEE_ID),
                Long.parseLong(MANAGER_ID),
                LocalDate.parse(EFFECTIVE_FROM));
    }

    @Nested
    @DisplayName("POST /api/admin/employee-manager-mappings (create/update)")
    class Creating {

        @Test
        @DisplayName("valid create/update returns 201 with the persisted mapping")
        void validCreateReturns201WithMapping() throws Exception {
            when(mappingAdminService.createOrUpdateMapping(any(EmployeeManagerMappingRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/admin/employee-manager-mappings")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(MAPPING_ID))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.managerId").value(MANAGER_ID))
                    .andExpect(jsonPath("$.effectiveFrom").value(EFFECTIVE_FROM));

            ArgumentCaptor<EmployeeManagerMappingRequest> captor =
                    ArgumentCaptor.forClass(EmployeeManagerMappingRequest.class);
            verify(mappingAdminService).createOrUpdateMapping(captor.capture());
            EmployeeManagerMappingRequest sent = captor.getValue();
            assertThat(sent.employeeId()).isEqualTo(Long.parseLong(EMPLOYEE_ID));
            assertThat(sent.managerId()).isEqualTo(Long.parseLong(MANAGER_ID));
            assertThat(sent.effectiveFrom()).isEqualTo(LocalDate.parse(EFFECTIVE_FROM));
        }

        @Test
        @DisplayName("missing employeeId and effectiveFrom return 400 VALIDATION_ERROR with field errors, service untouched")
        void invalidBodyReturns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/admin/employee-manager-mappings")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'employeeId')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'effectiveFrom')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(mappingAdminService);
        }

        @Test
        @DisplayName("null managerId (top of hierarchy, Q3) is accepted and round-trips as null")
        void nullManagerIdIsAccepted() throws Exception {
            when(mappingAdminService.createOrUpdateMapping(any(EmployeeManagerMappingRequest.class)))
                    .thenReturn(new EmployeeManagerMappingResponse(
                            11L,
                            Long.parseLong(EMPLOYEE_ID),
                            null,
                            LocalDate.parse(EFFECTIVE_FROM)));

            mockMvc.perform(post("/api/admin/employee-manager-mappings")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_TOP_LEVEL_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(11))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.managerId").value(nullValue()))
                    .andExpect(jsonPath("$.effectiveFrom").value(EFFECTIVE_FROM));

            ArgumentCaptor<EmployeeManagerMappingRequest> captor =
                    ArgumentCaptor.forClass(EmployeeManagerMappingRequest.class);
            verify(mappingAdminService).createOrUpdateMapping(captor.capture());
            assertThat(captor.getValue().managerId()).isNull();
        }

        @Test
        @DisplayName("unknown employee user returns 404 RESOURCE_NOT_FOUND")
        void unknownEmployeeReturns404() throws Exception {
            when(mappingAdminService.createOrUpdateMapping(any(EmployeeManagerMappingRequest.class)))
                    .thenThrow(new ResourceNotFoundException("UserAccount", EMPLOYEE_ID));

            mockMvc.perform(post("/api/admin/employee-manager-mappings")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "UserAccount with id " + EMPLOYEE_ID + " not found"));
        }
    }

    @Nested
    @DisplayName("Security")
    class RbacEnforcement {

        @Test
        @DisplayName("POST without ROLE_ADMIN is denied with 403 FORBIDDEN_ACTION, service never called")
        void employeeCreateReturns403() throws Exception {
            mockMvc.perform(post("/api/admin/employee-manager-mappings")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(mappingAdminService);
        }

        @Test
        @DisplayName("POST without a bearer token is rejected with 401 by the security filter, service never called")
        void createWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/admin/employee-manager-mappings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(mappingAdminService);
        }
    }
}