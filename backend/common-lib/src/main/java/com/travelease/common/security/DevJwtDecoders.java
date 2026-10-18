package com.travelease.common.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Local/dev-only HMAC {@link JwtDecoder} factory, used as a fallback when no external
 * identity-provider (issuer-uri/jwk-set-uri) is configured yet (api-contract.md §2: token
 * issuance/IdP is deferred to infrastructure design). Each service overrides this via
 * standard Spring Boot resource-server properties once an IdP is available.
 */
public final class DevJwtDecoders {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private DevJwtDecoders() {
        // factory holder - not instantiable
    }

    /**
     * Builds an HS256 decoder for the configured shared secret.
     *
     * @throws IllegalStateException when the secret fails {@link JwtSecretPolicy}
     */
    public static JwtDecoder hmacDecoder(String secret) {
        JwtSecretPolicy.requireUsableSecret(secret);
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
