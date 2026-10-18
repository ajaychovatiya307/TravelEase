package com.travelease.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtSecretPolicyTest {

    /**
     * The signing secret that used to be the hard-coded fallback in every service and in
     * generate-token.js. It is public (git history), so it is present here only to prove it is refused.
     */
    private static final String PREVIOUSLY_PUBLISHED_SECRET = "dev-only-shared-secret-change-me-before-production-0000";

    @Test
    @DisplayName("missing or blank secret is rejected with guidance, not a raw placeholder error")
    void missingSecretRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireUsableSecret(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVELEASE_JWT_SECRET");
        assertThatThrownBy(() -> JwtSecretPolicy.requireUsableSecret("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("secrets shorter than 256 bits are rejected")
    void shortSecretRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireUsableSecret("too-short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("the previously published fallback secret is rejected in every environment, without echoing it")
    void publishedSecretRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.requireUsableSecret(PREVIOUSLY_PUBLISHED_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(PREVIOUSLY_PUBLISHED_SECRET));
    }

    @Test
    @DisplayName("a long random secret is accepted")
    void strongSecretAccepted() {
        String secret = "k3vQ9x2LmP8rT5wZ1yB4nC7dF0gH6jK2sV9uE3aR";
        assertThat(JwtSecretPolicy.requireUsableSecret(secret)).isEqualTo(secret);
    }
}
