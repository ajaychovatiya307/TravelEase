package com.travelease.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.travelease.admin.config.SecurityConfig;
import com.travelease.admin.dto.PasswordResetResponse;
import com.travelease.admin.exception.GlobalExceptionHandler;
import com.travelease.admin.service.PasswordManagementService;

/** #14: administrator password reset endpoint (real method security; service mocked). */
@WebMvcTest(controllers = UserPasswordAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class UserPasswordAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordManagementService passwordManagementService;

    // AdminTestApplication registers every controller; their collaborators are mocked as in the other slices
    @MockBean
    private com.travelease.admin.service.AuthenticationService authenticationService;
    @MockBean
    private com.travelease.admin.service.UserAdminService userAdminService;
    @MockBean
    private com.travelease.admin.service.EmployeeManagerMappingAdminService employeeManagerMappingAdminService;
    @MockBean
    private com.travelease.admin.service.EmployeeManagerRoutingService employeeManagerRoutingService;
    @MockBean
    private com.travelease.admin.service.ExpenseCategoryService expenseCategoryService;
    @MockBean
    private com.travelease.admin.service.PolicyConfigService policyConfigService;
    @MockBean
    private com.travelease.admin.service.TravelRequestPolicyService travelRequestPolicyService;

    @Test
    void adminResetReturnsTheTemporaryPasswordOnceAndUncached() throws Exception {
        when(passwordManagementService.resetPassword(5L, 7L)).thenReturn(new PasswordResetResponse("Tmp4xYz9Qw2Er8Ty"));

        mockMvc.perform(post("/api/admin/users/7/reset-password")
                        .with(jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").value("Tmp4xYz9Qw2Er8Ty"));
        verify(passwordManagementService).resetPassword(5L, 7L);
    }

    @Test
    void nonAdminsCannotResetPasswords() throws Exception {
        org.mockito.Mockito.clearInvocations(passwordManagementService);
        mockMvc.perform(post("/api/admin/users/7/reset-password")
                        .with(jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/7/reset-password")).andExpect(status().isUnauthorized());
        verifyNoInteractions(passwordManagementService);
    }
}
