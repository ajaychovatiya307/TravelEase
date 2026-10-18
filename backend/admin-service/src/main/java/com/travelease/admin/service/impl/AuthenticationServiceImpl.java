package com.travelease.admin.service.impl;

import com.travelease.admin.security.CredentialsVersionValidator;
import com.travelease.admin.security.PasswordChangeRequiredException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.admin.dto.LoginRequest;
import com.travelease.admin.dto.LoginResponse;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.service.AuthenticationService;

/** Database-backed authentication for user accounts owned by admin-service. */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private static final String JWT_TYPE = "JWT";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthenticationServiceImpl(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginResponse> login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Optional.empty();
        }

        Optional<UserAccount> account = userAccountRepository
                .findByEmailIgnoreCaseAndDeletedFalse(request.username().trim());
        if (account.isEmpty()) {
            return Optional.empty();
        }

        UserAccount user = account.get();
        if (!user.isActive() || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return Optional.empty();
        }

        List<String> roles = new ArrayList<>();
        for (UserRole role : user.getRoles()) {
            if (!role.isDeleted() && role.getRole() != null && !roles.contains(role.getRole().name())) {
                roles.add(role.getRole().name());
            }
        }
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        // correct password, but an administrator reset is pending: no token until it is replaced (#14)
        if (user.isMustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }

        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(TOKEN_LIFETIME_SECONDS, ChronoUnit.SECONDS))
                .claim("roles", roles)
                // credentials version: tokens issued before a password change/reset or a role/active
                // change are rejected by admin-service (CredentialsVersionValidator, #14)
                .claim(CredentialsVersionValidator.CLAIM, user.getCredentialsVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type(JWT_TYPE).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Optional.of(new LoginResponse(token));
    }
}
