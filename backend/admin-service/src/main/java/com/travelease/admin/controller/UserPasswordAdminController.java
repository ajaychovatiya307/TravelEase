package com.travelease.admin.controller;

import com.travelease.admin.dto.PasswordResetResponse;
import com.travelease.admin.service.PasswordManagementService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator password reset (#14). Issues a random temporary password shown once to the
 * administrator; the user must replace it before signing in, and all earlier tokens stop working
 * in admin-service. The action is audited (PASSWORD_RESET) without the password value.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserPasswordAdminController {

    private final PasswordManagementService passwordManagementService;

    public UserPasswordAdminController(PasswordManagementService passwordManagementService) {
        this.passwordManagementService = passwordManagementService;
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PasswordResetResponse> resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        PasswordResetResponse response = passwordManagementService.resetPassword(Long.valueOf(jwt.getSubject()), id);
        // the temporary password must never be cached by browsers or proxies
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
