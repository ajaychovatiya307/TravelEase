package com.travelease.admin.security;

import java.util.Optional;

import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.repository.UserAccountRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects admin-service requests made with a token that predates the account's latest credential or
 * access change (password change/reset, deactivation, role change), or that belongs to a deleted or
 * inactive account (#14). Tokens without the {@code cv} claim are treated as version 0.
 *
 * <p>Scope: admin-service only. The other services verify tokens statelessly, so there a revoked
 * token remains usable until it expires (at most the token lifetime) — see README "Security notes".</p>
 */
public class CredentialsVersionValidator implements OAuth2TokenValidator<Jwt> {

    public static final String CLAIM = "cv";

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token",
            "This session is no longer valid. Please sign in again.", null);

    private final ObjectProvider<UserAccountRepository> repositoryProvider;

    public CredentialsVersionValidator(ObjectProvider<UserAccountRepository> repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        UserAccountRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            // web-layer test slices without persistence
            return OAuth2TokenValidatorResult.success();
        }
        Long userId;
        try {
            userId = Long.valueOf(token.getSubject());
        } catch (NumberFormatException ex) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        Optional<UserAccount> account = repository.findByIdAndDeletedFalse(userId);
        Number claim = token.getClaim(CLAIM);
        int tokenVersion = claim == null ? 0 : claim.intValue();
        boolean valid = account.isPresent() && account.get().isActive()
                && account.get().getCredentialsVersion() == tokenVersion;
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }
}
