package com.travelease.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;

import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.repository.UserAccountRepository;

class CredentialsVersionValidatorTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);

    @SuppressWarnings("unchecked")
    private final CredentialsVersionValidator validator = new CredentialsVersionValidator(provider());

    @SuppressWarnings("unchecked")
    private ObjectProvider<UserAccountRepository> provider() {
        ObjectProvider<UserAccountRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        return provider;
    }

    private static Jwt token(Object version) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").subject("7");
        if (version != null) {
            builder.claim(CredentialsVersionValidator.CLAIM, version);
        }
        return builder.build();
    }

    private UserAccount account(int bumps, boolean active) {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setActive(active);
        for (int i = 0; i < bumps; i++) {
            account.bumpCredentialsVersion();
        }
        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    @DisplayName("a token carrying the current credentials version is accepted")
    void currentVersionAccepted() {
        account(2, true);
        assertThat(validator.validate(token(2)).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("a token issued before a password change/reset or role change is rejected")
    void olderVersionRejected() {
        account(3, true);
        assertThat(validator.validate(token(2)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("tokens of deactivated or deleted accounts are rejected")
    void inactiveOrMissingRejected() {
        account(0, false);
        assertThat(validator.validate(token(0)).hasErrors()).isTrue();

        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        assertThat(validator.validate(token(0)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("legacy tokens without the claim count as version 0")
    void legacyTokenWithoutClaim() {
        account(0, true);
        assertThat(validator.validate(token(null)).hasErrors()).isFalse();
        account(1, true);
        assertThat(validator.validate(token(null)).hasErrors()).isTrue();
    }
}
