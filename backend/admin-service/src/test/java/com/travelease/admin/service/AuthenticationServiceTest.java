package com.travelease.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.travelease.admin.dto.LoginRequest;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.service.impl.AuthenticationServiceImpl;
import com.travelease.common.security.DevJwtDecoders;
import com.travelease.common.security.RoleType;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String SECRET = "test-only-jwt-secret-never-deploy-0123456789abcdef";
    private static final String PASSWORD = "Unit-test-pass-2026";

    @Mock
    private UserAccountRepository userAccountRepository;

    private PasswordEncoder passwordEncoder;
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authenticationService = new AuthenticationServiceImpl(
                userAccountRepository,
                passwordEncoder,
                new NimbusJwtEncoder(new ImmutableSecret<>(
                        new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))));
    }

    @Test
    void issuesCompatibleSignedTokenForValidCredentials() {
        UserAccount account = account(5L, "sam.admin@travelease.example", true, RoleType.ADMIN, RoleType.FINANCE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        when(userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse("SAM.ADMIN@TRAVELEASE.EXAMPLE"))
                .thenReturn(Optional.of(account));

        String token = authenticationService.login(
                new LoginRequest("SAM.ADMIN@TRAVELEASE.EXAMPLE", PASSWORD)).orElseThrow().token();
        JwtDecoder decoder = DevJwtDecoders.hmacDecoder(SECRET);
        Jwt claims = decoder.decode(token);

        assertThat(claims.getSubject()).isEqualTo("5");
        assertThat(claims.getClaimAsStringList("roles")).containsExactly("ADMIN", "FINANCE");
        Instant issuedAt = Objects.requireNonNull(claims.getIssuedAt());
        assertThat(claims.getExpiresAt()).isAfter(Instant.now());
        assertThat(claims.getExpiresAt()).isEqualTo(issuedAt.plusSeconds(3600));
        verify(userAccountRepository).findByEmailIgnoreCaseAndDeletedFalse("SAM.ADMIN@TRAVELEASE.EXAMPLE");
    }

    @Test
    void returnsEmptyForUnknownUsername() {
        when(userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse("missing@travelease.example"))
                .thenReturn(Optional.empty());

        assertThat(authenticationService.login(new LoginRequest("missing@travelease.example", PASSWORD))).isEmpty();
    }

    @Test
    void returnsEmptyForWrongPassword() {
        UserAccount account = account(1L, "asha.rao@travelease.example", true, RoleType.EMPLOYEE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        when(userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse("asha.rao@travelease.example"))
                .thenReturn(Optional.of(account));

        assertThat(authenticationService.login(
                new LoginRequest("asha.rao@travelease.example", "wrong-password"))).isEmpty();
    }

    @Test
    void returnsEmptyForInactiveAccount() {
        UserAccount account = account(1L, "asha.rao@travelease.example", false, RoleType.EMPLOYEE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        when(userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse("asha.rao@travelease.example"))
                .thenReturn(Optional.of(account));

        assertThat(authenticationService.login(
                new LoginRequest("asha.rao@travelease.example", PASSWORD))).isEmpty();
    }

    private static UserAccount account(Long id, String email, boolean active, RoleType... roles) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setEmail(email);
        account.setActive(active);
        for (RoleType roleType : roles) {
            UserRole role = new UserRole();
            role.setRole(roleType);
            account.addRole(role);
        }
        return account;
    }

    @org.junit.jupiter.api.Test
    void tokenCarriesTheCredentialsVersionClaim() {
        UserAccount account = account(5L, "sam.admin@travelease.example", true, RoleType.ADMIN);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.bumpCredentialsVersion();
        account.bumpCredentialsVersion();
        when(userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse("sam.admin@travelease.example"))
                .thenReturn(Optional.of(account));

        String token = authenticationService.login(new LoginRequest("sam.admin@travelease.example", PASSWORD)).orElseThrow().token();

        assertThat(DevJwtDecoders.hmacDecoder(SECRET).decode(token).<Number>getClaim("cv").intValue()).isEqualTo(2);
    }

    @org.junit.jupiter.api.Test
    void pendingResetBlocksTokenOnlyAfterTheCorrectPassword() {
        UserAccount account = account(5L, "reset.user@travelease.example", true, RoleType.EMPLOYEE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setMustChangePassword(true);
        when(userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse("reset.user@travelease.example"))
                .thenReturn(Optional.of(account));

        // a wrong password learns nothing about the pending reset
        assertThat(authenticationService.login(new LoginRequest("reset.user@travelease.example", "wrong"))).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        authenticationService.login(new LoginRequest("reset.user@travelease.example", PASSWORD)))
                .isInstanceOf(com.travelease.admin.security.PasswordChangeRequiredException.class);
    }
}
