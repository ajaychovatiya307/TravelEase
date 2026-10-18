package com.travelease.admin.controller;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.travelease.admin.dto.CompletePasswordChangeRequest;
import com.travelease.admin.dto.LoginRequest;
import com.travelease.admin.dto.LoginResponse;
import com.travelease.admin.dto.PasswordChangeRequest;
import com.travelease.admin.security.LoginAttemptLimiter;
import com.travelease.admin.security.PasswordChangeRequiredException;
import com.travelease.admin.service.AuthenticationService;
import com.travelease.admin.service.PasswordManagementService;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.exception.ValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (api-contract.md §2) plus the password lifecycle (#14).
 *
 * <p>Login keeps its contract: 200 with a token, 401 with a generic message for unknown users, wrong
 * passwords and inactive accounts alike. Repeated failures lock the username/client (429). When an
 * administrator reset is pending, the correct password yields 403 {@code PASSWORD_CHANGE_REQUIRED}
 * (only after the password was verified, so it reveals nothing to a guesser).</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final AuthenticationService authenticationService;
    private final PasswordManagementService passwordManagementService;
    private final LoginAttemptLimiter limiter;

    public AuthenticationController(AuthenticationService authenticationService,
            PasswordManagementService passwordManagementService, LoginAttemptLimiter limiter) {
        this.authenticationService = authenticationService;
        this.passwordManagementService = passwordManagementService;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String client = clientAddress(http);
        limiter.checkAllowed(request.username(), client);
        Optional<LoginResponse> response;
        try {
            response = authenticationService.login(request);
        } catch (PasswordChangeRequiredException ex) {
            limiter.recordSuccess(request.username());
            throw ex;
        }
        if (response.isEmpty()) {
            limiter.recordFailure(request.username(), client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(java.time.Instant.now(),
                    HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", INVALID_CREDENTIALS, null));
        }
        limiter.recordSuccess(request.username());
        return ResponseEntity.ok(response.get());
    }

    /** Authenticated self-service change; invalidates earlier tokens (sign in again afterwards). */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request) {
        passwordManagementService.changePassword(Long.valueOf(jwt.getSubject()), request.currentPassword(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Replaces an administrator-issued temporary password; brute-force limited like login. */
    @PostMapping("/complete-password-change")
    public ResponseEntity<Void> completePasswordChange(@Valid @RequestBody CompletePasswordChangeRequest request,
            HttpServletRequest http) {
        String client = clientAddress(http);
        limiter.checkAllowed(request.username(), client);
        try {
            passwordManagementService.completeRequiredChange(request.username(), request.temporaryPassword(),
                    request.newPassword());
        } catch (ValidationException ex) {
            if (ex.getFieldErrors().stream().anyMatch(error -> "credentials".equals(error.field()))) {
                limiter.recordFailure(request.username(), client);
            }
            throw ex;
        }
        limiter.recordSuccess(request.username());
        return ResponseEntity.noContent().build();
    }

    /**
     * Client address for rate limiting. Behind the gateway the remote address is the gateway, so the
     * first X-Forwarded-For hop is used when present (a spoofed header can only dodge the per-client
     * limit; the per-username limit still applies).
     */
    static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
