package com.travelease.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.travelease.admin.config.SecurityConfig;
import com.travelease.admin.dto.LoginRequest;
import com.travelease.admin.dto.LoginResponse;
import com.travelease.admin.exception.GlobalExceptionHandler;
import com.travelease.admin.service.EmployeeManagerMappingAdminService;
import com.travelease.admin.service.EmployeeManagerRoutingService;
import com.travelease.admin.service.ExpenseCategoryService;
import com.travelease.admin.service.PolicyConfigService;
import com.travelease.admin.service.AuthenticationService;
import com.travelease.admin.service.TravelRequestPolicyService;
import com.travelease.admin.service.UserAdminService;

@WebMvcTest(controllers = AuthenticationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private EmployeeManagerMappingAdminService employeeManagerMappingAdminService;

    @MockBean
    private EmployeeManagerRoutingService employeeManagerRoutingService;

    @MockBean
    private ExpenseCategoryService expenseCategoryService;

    @MockBean
    private PolicyConfigService policyConfigService;

    @MockBean
    private TravelRequestPolicyService travelRequestPolicyService;

    @Test
    void allowsUnauthenticatedLoginAndReturnsIssuedToken() throws Exception {
        when(authenticationService.login(new LoginRequest("user@travelease.example", "password")))
                .thenReturn(Optional.of(new LoginResponse("signed.jwt.token")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"user@travelease.example","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"token":"signed.jwt.token"}
                        """));

        verify(authenticationService).login(new LoginRequest("user@travelease.example", "password"));
    }

    @Test
    void returns401ForInvalidCredentials() throws Exception {
        when(authenticationService.login(new LoginRequest("user@travelease.example", "wrong")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"user@travelease.example","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns400WhenCredentialsAreMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---- #14: brute-force protection, forced password change, password endpoints ----

    @org.springframework.beans.factory.annotation.Autowired
    private com.travelease.admin.service.PasswordManagementService passwordManagementService;

    @Test
    void locksOutAfterRepeatedFailuresWithA429EvenForUnknownUsers() throws Exception {
        LoginRequest guess = new LoginRequest("lockout-target@travelease.example", "guess");
        when(authenticationService.login(guess)).thenReturn(Optional.empty());
        String body = """
                {"username":"lockout-target@travelease.example","password":"guess"}
                """;
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
        // the locked attempt never reached the credential check
        verify(authenticationService, org.mockito.Mockito.times(5)).login(guess);
    }

    @Test
    void correctPasswordWithPendingResetAnswers403PasswordChangeRequired() throws Exception {
        when(authenticationService.login(new LoginRequest("reset-user@travelease.example", "Temp-pass-123")))
                .thenThrow(new com.travelease.admin.security.PasswordChangeRequiredException());

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content("""
                        {"username":"reset-user@travelease.example","password":"Temp-pass-123"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void changePasswordRequiresAuthenticationAndUsesTheTokenSubject() throws Exception {
        String body = """
                {"currentPassword":"Current-pass-2026","newPassword":"Brand-new-pass-2027"}
                """;
        mockMvc.perform(post("/api/auth/change-password").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/change-password")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .jwt().jwt(jwt -> jwt.subject("7")))
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
        verify(passwordManagementService).changePassword(7L, "Current-pass-2026", "Brand-new-pass-2027");
    }

    @Test
    void completingAForcedChangeIsPublicButValidated() throws Exception {
        mockMvc.perform(post("/api/auth/complete-password-change").contentType("application/json").content("""
                        {"username":"reset-user@travelease.example","temporaryPassword":"Temp-pass-123","newPassword":"Brand-new-pass-2027"}
                        """))
                .andExpect(status().isNoContent());
        verify(passwordManagementService).completeRequiredChange("reset-user@travelease.example", "Temp-pass-123",
                "Brand-new-pass-2027");

        mockMvc.perform(post("/api/auth/complete-password-change").contentType("application/json").content("""
                        {"username":"reset-user@travelease.example","temporaryPassword":"","newPassword":""}
                        """))
                .andExpect(status().isBadRequest());
    }
}
