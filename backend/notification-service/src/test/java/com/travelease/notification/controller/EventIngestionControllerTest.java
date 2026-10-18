package com.travelease.notification.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.notification.config.SecurityConfig;
import com.travelease.notification.service.NotificationDispatchService;
import com.travelease.notification.service.NotificationQueryService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the HTTP domain-event ingestion endpoint (tasks.md N-02 fix;
 * api-contract.md §2/§8; spec.md FR-NOT-01, FR-NOT-02; Constitution §9):
 *
 * <ul>
 *   <li>202 Accepted on a valid envelope — the event is delegated once, unchanged, to
 *       the dispatch tier (FR-NOT-01, FR-NOT-02, AC-15)</li>
 *   <li>400 {@code VALIDATION_ERROR} with the shared structured error body when the
 *       dispatch tier rejects the envelope with {@link IllegalArgumentException}
 *       (unknown entityType/action, non-numeric entityId) (api-contract.md §8)</li>
 *   <li>401 without a bearer token, rejected by the security filter before the service
 *       is reached (api-contract.md §2, NFR-10)</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5): the slice is bootstrapped from the shared
 * {@link NotificationTestApplication} in this package (a single top-level config for
 * the whole package, see its Javadoc). The app's {@link SecurityConfig} is imported so
 * the real JWT resource-server filter chain is in effect exactly as in production; its
 * {@code DevJwtDecoders} fallback provides an offline HMAC {@code JwtDecoder}. This
 * module has no {@code @RestControllerAdvice} (unlike the sibling services), so the
 * 400 body is asserted as produced directly by the controller. The module's pom has no
 * {@code spring-security-test} dependency, so requests carry a real HS256 JWT signed
 * with the dev-only shared secret (claims {@code sub} + {@code roles} array) — the
 * token is decoded and the {@code roles} claim is converted to authorities by the
 * actual filter chain. Paths address the servlet mapping directly ({@code /api/events})
 * — the context path is empty. The controller's constructor collaborator,
 * {@link NotificationDispatchService}, is {@code @MockBean}-mocked (Boot 3.2.5;
 * {@code @MockitoBean} exists only from 3.4).
 * </p>
 */
@WebMvcTest(controllers = EventIngestionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("EventIngestionController API (tasks.md N-02; spec.md FR-NOT-01, FR-NOT-02)")
class EventIngestionControllerTest {

    /** Test-only HMAC secret; must match security.jwt.secret in src/test/resources/application.properties. */
    private static final String DEV_JWT_SECRET = "test-only-jwt-secret-never-deploy-0123456789abcdef";

    private static final String ENTITY_TYPE = "TRAVEL_REQUEST";
    private static final String ENTITY_ID = "2";
    private static final String ACTION = "APPROVED";
    private static final String TIMESTAMP = "2026-10-08T15:00:00Z";

    private static final String VALID_ENVELOPE_BODY = """
            {
                "entityType": "TRAVEL_REQUEST",
                "entityId": "2",
                "action": "APPROVED",
                "timestamp": "2026-10-08T15:00:00Z",
                "recipientIds": []
            }
            """;

    /** Unknown entityType — the dispatch tier rejects it with IllegalArgumentException. */
    private static final String INVALID_ENVELOPE_BODY = """
            {
                "entityType": "FLIGHT",
                "entityId": "2",
                "action": "APPROVED",
                "timestamp": "2026-10-08T15:00:00Z"
            }
            """;

    /** Same message shape the real dispatch tier produces for an unknown entityType. */
    private static final String INVALID_EVENT_MESSAGE =
            "Invalid event format: No enum constant com.travelease.common.event.AuditEntityType.FLIGHT";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationDispatchService notificationDispatchService;

    @MockBean
    private NotificationQueryService notificationQueryService;

    @Test
    @DisplayName("valid envelope returns 202 and is delegated once, unchanged, to the dispatch tier")
    void validEnvelopeReturns202AndDelegatesToDispatchService() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header(com.travelease.common.security.internal.InternalServiceTokens.HEADER, internalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ENVELOPE_BODY))
                .andExpect(status().isAccepted());

        verify(notificationDispatchService).dispatchNotification(
                new DomainEventEnvelope(ENTITY_TYPE, ENTITY_ID, ACTION, Instant.parse(TIMESTAMP)));
    }

    @Test
    @DisplayName("malformed envelope rejected by the dispatch tier returns 400 VALIDATION_ERROR")
    void invalidEnvelopeReturns400WithStructuredError() throws Exception {
        doThrow(new IllegalArgumentException(INVALID_EVENT_MESSAGE))
                .when(notificationDispatchService).dispatchNotification(any(DomainEventEnvelope.class));

        mockMvc.perform(post("/api/events")
                        .header(com.travelease.common.security.internal.InternalServiceTokens.HEADER, internalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_ENVELOPE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(INVALID_EVENT_MESSAGE));
    }

    @Test
    @DisplayName("request without a bearer token returns 401 before the service is reached")
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ENVELOPE_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationDispatchService);
    }

    /**
     * Signs a real HS256 JWT with the dev-only shared secret (claims {@code sub} +
     * {@code roles} array) so the imported resource-server {@link SecurityConfig}'s
     * actual decoder and {@code JwtRoleConverters} claim-to-authority conversion run
     * exactly as in production.
     */
    private static String devJwt(String subject, List<String> roles) {
        return signWith(DEV_JWT_SECRET, subject, roles);
    }

    private static String signWith(String secret, String subject, List<String> roles) {
        try {
            MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .claim("roles", roles)
                    .expirationTime(new Date(System.currentTimeMillis() + 60_000L))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign dev JWT", ex);
        }
    }

    /** Test-only internal secret; must match security.internal.secret in src/test/resources/application.properties. */
    private static final String INTERNAL_SECRET = "test-only-internal-secret-never-deploy-0123456789";

    /** A genuine service credential, as the producers' outbox sends it. */
    private static String internalToken() {
        return new com.travelease.common.security.internal.InternalServiceTokens(
                INTERNAL_SECRET, "travel-request-service").currentToken();
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("internal-only ingestion (#12): user tokens are never enough")
    class InternalOnly {

        @Test
        @DisplayName("an end user's JWT (even FINANCE/ADMIN) is rejected with 401 and nothing is ingested")
        void userJwtRejected() throws Exception {
            for (String role : List.of("EMPLOYEE", "FINANCE", "ADMIN")) {
                mockMvc.perform(post("/api/events")
                                .header("Authorization", "Bearer " + devJwt("5", List.of(role)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ENVELOPE_BODY))
                        .andExpect(status().isUnauthorized());
            }
            org.mockito.Mockito.verifyNoInteractions(notificationDispatchService);
        }

        @Test
        @DisplayName("an internal token signed with the wrong key is rejected")
        void forgedInternalTokenRejected() throws Exception {
            String forged = new com.travelease.common.security.internal.InternalServiceTokens(
                    "attacker-chosen-secret-0123456789-abcdefgh", "travel-request-service").currentToken();
            mockMvc.perform(post("/api/events")
                            .header(com.travelease.common.security.internal.InternalServiceTokens.HEADER, forged)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_ENVELOPE_BODY))
                    .andExpect(status().isUnauthorized());
            org.mockito.Mockito.verifyNoInteractions(notificationDispatchService);
        }

        @Test
        @DisplayName("a user-style token in the internal header (no service claims) is rejected even with the internal key")
        void userStyleTokenWithInternalKeyRejected() throws Exception {
            mockMvc.perform(post("/api/events")
                            .header(com.travelease.common.security.internal.InternalServiceTokens.HEADER,
                                    signWith(INTERNAL_SECRET, "5", List.of("ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_ENVELOPE_BODY))
                    .andExpect(status().isUnauthorized());
            org.mockito.Mockito.verifyNoInteractions(notificationDispatchService);
        }
    }
}
