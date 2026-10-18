package com.travelease.common.security.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import com.travelease.common.security.JwtSecretPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Issues and verifies service-to-service credentials (audit/notification event ingestion and the
 * admin-service {@code /internal/**} lookups).
 *
 * <p>A credential is a short-lived HS256 token signed with {@code security.internal.secret}
 * (TRAVELEASE_INTERNAL_SECRET) — a different secret from the user-token secret — carrying
 * {@code aud=travelease-internal} and {@code typ=service}. User JWTs never carry these claims and are
 * signed with another key, so an end user's token can never be used as proof of an internal caller.</p>
 */
public final class InternalServiceTokens {

    /** Request header carrying the internal credential (kept separate from {@code Authorization}). */
    public static final String HEADER = "X-Internal-Service-Token";
    static final String AUDIENCE = "travelease-internal";
    static final String TYPE_CLAIM = "typ";
    static final String TYPE_SERVICE = "service";

    private static final Duration LIFETIME = Duration.ofMinutes(2);
    private static final Duration REUSE_WINDOW = Duration.ofSeconds(60);

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String serviceName;
    private final Clock clock;

    private volatile String cachedToken;
    private volatile Instant cachedUntil = Instant.EPOCH;

    public InternalServiceTokens(String secret, String serviceName) {
        this(secret, serviceName, Clock.systemUTC());
    }

    InternalServiceTokens(String secret, String serviceName, Clock clock) {
        JwtSecretPolicy.requireUsableSecret(secret);
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.serviceName = serviceName == null || serviceName.isBlank() ? "unknown-service" : serviceName;
        this.clock = clock;
    }

    /** Returns a valid credential for outgoing internal calls (re-used for up to a minute). */
    public String currentToken() {
        Instant now = clock.instant();
        if (cachedToken == null || now.isAfter(cachedUntil)) {
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject(serviceName)
                    .audience(List.of(AUDIENCE))
                    .issuedAt(now)
                    .expiresAt(now.plus(LIFETIME))
                    .claim(TYPE_CLAIM, TYPE_SERVICE)
                    .build();
            cachedToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
            cachedUntil = now.plus(REUSE_WINDOW);
        }
        return cachedToken;
    }

    /**
     * @return the calling service's name when the credential is genuine, unexpired and an internal
     *         service token; empty otherwise (never throws for bad input)
     */
    public Optional<String> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            boolean internalAudience = jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE);
            boolean serviceType = TYPE_SERVICE.equals(jwt.getClaimAsString(TYPE_CLAIM));
            return internalAudience && serviceType ? Optional.ofNullable(jwt.getSubject()) : Optional.empty();
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }
}
