package com.travelease.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Signs tokens exactly as admin-service's login does (NimbusJwtEncoder, HS256, {@code roles} claim)
 * and verifies them with the decoder every resource service uses ({@link DevJwtDecoders}).
 */
class JwtInteroperabilityTest {

    private static final String SHARED_SECRET = "interop-test-secret-0123456789-abcdefghij";
    private static final String OTHER_SECRET = "a-different-secret-0123456789-abcdefghijkl";

    private static String sign(String secret, String subject, List<String> roles) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    @DisplayName("a token issued with the shared secret verifies in another service and maps roles")
    void issuedTokenVerifiesAcrossServices() {
        String token = sign(SHARED_SECRET, "42", List.of("EMPLOYEE", "MANAGER"));

        Jwt jwt = DevJwtDecoders.hmacDecoder(SHARED_SECRET).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(JwtRoleConverters.rolesConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void wrongKeyRejected() {
        String forged = sign(OTHER_SECRET, "1", List.of("ADMIN"));
        JwtDecoder decoder = DevJwtDecoders.hmacDecoder(SHARED_SECRET);

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token whose payload was altered after signing is rejected")
    void tamperedPayloadRejected() {
        String token = sign(SHARED_SECRET, "7", List.of("EMPLOYEE"));
        String[] parts = token.split("\\.");
        String escalated = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("\"EMPLOYEE\"", "\"ADMIN\"")
                        .getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + escalated + "." + parts[2];

        assertThatThrownBy(() -> DevJwtDecoders.hmacDecoder(SHARED_SECRET).decode(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an unsigned (alg=none) token is rejected")
    void unsignedTokenRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"roles\":[\"ADMIN\"]}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> DevJwtDecoders.hmacDecoder(SHARED_SECRET).decode(header + "." + payload + "."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a decoder cannot be built from a missing or weak secret")
    void decoderRefusesUnsafeSecret() {
        assertThatThrownBy(() -> DevJwtDecoders.hmacDecoder("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DevJwtDecoders.hmacDecoder("short")).isInstanceOf(IllegalStateException.class);
    }
}
