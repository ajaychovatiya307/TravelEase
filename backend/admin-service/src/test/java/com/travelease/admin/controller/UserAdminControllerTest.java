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
import com.travelease.admin.dto.UserAccountCreateRequest;
import com.travelease.admin.dto.UserAccountResponse;
import com.travelease.admin.exception.GlobalExceptionHandler;
import com.travelease.admin.service.EmployeeManagerMappingAdminService;
import com.travelease.admin.service.EmployeeManagerRoutingService;
import com.travelease.admin.service.ExpenseCategoryService;
import com.travelease.admin.service.PolicyConfigService;
import com.travelease.admin.service.UserAdminService;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.security.RoleType;

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
 * MockMvc API tests for the Admin user account endpoints (tasks.md M-05;
 * api-contract.md §4.3/§8; spec.md FR-ADM-01):
 *
 * <ul>
 *   <li>201 Created with the persisted account on a valid create (FR-ADM-01,
 *       AC-01) — the compact-constructor default {@code active = true} is
 *       applied when the field is omitted (api-contract.md §5.12)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field errors on missing/invalid
 *       structural fields (Bean Validation tier) (api-contract.md §8)</li>
 *   <li>409 {@code DUPLICATE_RESOURCE} when the service tier reports a
 *       duplicate email (FR-ADM-01, service-layer business rule)</li>
 *   <li>200 with the account body / 404 {@code RESOURCE_NOT_FOUND} on
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
 * directly ({@code /api/admin/users}), not the {@code /api} context path of
 * the running server. The controller's one constructor collaborator —
 * {@link UserAdminService} — plus the sibling controllers' collaborators
 * ({@link EmployeeManagerMappingAdminService}, {@link PolicyConfigService},
 * {@link ExpenseCategoryService}, and {@link EmployeeManagerRoutingService})
 * are {@code @MockBean}-mocked (Boot 3.2.5; {@code @MockitoBean} exists only
 * from 3.4) so their beans exist under the shared component-scanning config;
 * only what each test needs is stubbed.</p>
 */
@WebMvcTest(controllers = UserAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("UserAdminController API (tasks.md M-05; api-contract.md §4.3/§8; spec.md FR-ADM-01)")
class UserAdminControllerTest {

    private static final String ADMIN_SUBJECT = "5";
    private static final String EMPLOYEE_SUBJECT = "1";

    private static final String CREATED_USER_ID = "7";
    private static final String UNKNOWN_USER_ID = "404";

    private static final String FULL_NAME = "Ajay Kumar";
    private static final String EMAIL = "ajay.kumar@travelease.example";

    /** Active omitted on purpose - the compact constructor must default it to true. */
    private static final String VALID_CREATE_BODY = """
            {
                "fullName": "%s",
                "email": "%s",
                "roles": ["EMPLOYEE", "MANAGER"],
                "password": "InitialPassword123"
            }
            """.formatted(FULL_NAME, EMAIL);

    /** fullName missing (required) and email malformed - structural tier. */
    private static final String INVALID_CREATE_BODY = """
            {
                "email": "not-an-email",
                "roles": ["EMPLOYEE"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAdminService userAdminService;

    // Sibling-controller collaborators, mocked so their beans exist under the
    // shared component-scanning config.
    @MockBean
    private EmployeeManagerMappingAdminService employeeManagerMappingAdminService;

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

    private UserAccountResponse createdResponse() {
        return new UserAccountResponse(
                Long.parseLong(CREATED_USER_ID),
                FULL_NAME,
                EMAIL,
                List.of(RoleType.EMPLOYEE, RoleType.MANAGER),
                Boolean.TRUE);
    }

    @Nested
    @DisplayName("POST /api/admin/users (create)")
    class Creating {

        @Test
        @DisplayName("valid create returns 201 with the persisted account and the active default applied")
        void validCreateReturns201WithCreatedAccount() throws Exception {
            when(userAdminService.createUser(any(UserAccountCreateRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/admin/users")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CREATED_USER_ID))
                    .andExpect(jsonPath("$.fullName").value(FULL_NAME))
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    // Indexed access instead of $.roles .value(List): whole-array
                    // value() matching mis-evaluates as null in this harness.
                    .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
                    .andExpect(jsonPath("$.roles[1]").value("MANAGER"))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            // The record must arrive with the compact-constructor default:
            // active was omitted in the body, so the service sees true.
            ArgumentCaptor<UserAccountCreateRequest> captor =
                    ArgumentCaptor.forClass(UserAccountCreateRequest.class);
            verify(userAdminService).createUser(captor.capture());
            UserAccountCreateRequest sent = captor.getValue();
            assertThat(sent.fullName()).isEqualTo(FULL_NAME);
            assertThat(sent.email()).isEqualTo(EMAIL);
            assertThat(sent.roles()).containsExactly(RoleType.EMPLOYEE, RoleType.MANAGER);
            assertThat(sent.active()).isTrue();
            assertThat(sent.password()).isEqualTo("InitialPassword123");
        }

        @Test
        @DisplayName("missing required fields return 400 VALIDATION_ERROR with field errors, service untouched")
        void invalidBodyReturns400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'fullName')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(userAdminService);
        }

        @Test
        @DisplayName("duplicate email reported by the service tier returns 409 DUPLICATE_RESOURCE")
        void duplicateEmailReturns409() throws Exception {
            when(userAdminService.createUser(any(UserAccountCreateRequest.class)))
                    .thenThrow(new DuplicateResourceException("UserAccount", EMAIL));

            mockMvc.perform(post("/api/admin/users")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                    .andExpect(jsonPath("$.message").value(
                            "UserAccount with id " + EMAIL + " already exists"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/users/{id}")
    class Fetching {

        @Test
        @DisplayName("existing account returns 200 with the account body")
        void existingAccountReturns200() throws Exception {
            when(userAdminService.getUser(Long.parseLong(CREATED_USER_ID)))
                    .thenReturn(createdResponse());

            mockMvc.perform(get("/api/admin/users/" + CREATED_USER_ID).with(adminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CREATED_USER_ID))
                    .andExpect(jsonPath("$.fullName").value(FULL_NAME))
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    // Indexed access instead of $.roles .value(List): whole-array
                    // value() matching mis-evaluates as null in this harness.
                    .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
                    .andExpect(jsonPath("$.roles[1]").value("MANAGER"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("unknown id returns 404 RESOURCE_NOT_FOUND")
        void unknownIdReturns404() throws Exception {
            when(userAdminService.getUser(Long.parseLong(UNKNOWN_USER_ID)))
                    .thenThrow(new ResourceNotFoundException("UserAccount", UNKNOWN_USER_ID));

            mockMvc.perform(get("/api/admin/users/" + UNKNOWN_USER_ID).with(adminJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "UserAccount with id " + UNKNOWN_USER_ID + " not found"));
        }
    }

    @Nested
    @DisplayName("Security")
    class RbacEnforcement {

        @Test
        @DisplayName("POST without ROLE_ADMIN is denied with 403 FORBIDDEN_ACTION, service never called")
        void employeeCreateReturns403() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to perform this action"));

            verifyNoInteractions(userAdminService);
        }

        @Test
        @DisplayName("POST without a bearer token is rejected with 401 by the security filter, service never called")
        void createWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userAdminService);
        }
    }
}