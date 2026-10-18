package com.travelease.notification.controller;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.travelease.notification.NotificationServiceApplication;
import com.travelease.notification.client.FinanceRecipientClient;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NotificationServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "server.servlet.context-path="
})
class NotificationFlowIntegrationTest {

    private static final String DEV_JWT_SECRET =
            "test-only-jwt-secret-never-deploy-0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceRecipientClient financeRecipientClient;

    @Test
    void eventPersistsUnreadNotificationAndRecipientCanMarkItRead() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/events")
                        // delivered by a producer's outbox with the internal service credential
                        .header(com.travelease.common.security.internal.InternalServiceTokens.HEADER,
                                new com.travelease.common.security.internal.InternalServiceTokens(
                                        "test-only-internal-secret-never-deploy-0123456789", "travel-request-service")
                                        .currentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityType": "TRAVEL_REQUEST",
                                  "entityId": "25",
                                  "action": "APPROVED",
                                  "timestamp": "2026-10-10T10:30:00Z",
                                  "recipientIds": [7]
                                }
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer("8", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer("7", "EMPLOYEE", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].entityType").value("TRAVEL_REQUEST"))
                .andExpect(jsonPath("$.content[0].entityId").value(25))
                .andExpect(jsonPath("$.content[0].recipientId").value(7))
                .andExpect(jsonPath("$.content[0].message").value("Your travel request has been approved."))
                .andExpect(jsonPath("$.content[0].read").value(false));

        mockMvc.perform(post("/api/notifications/1/read")
                        .header("Authorization", bearer("8", "EMPLOYEE")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/notifications/1/read")
                        .header("Authorization", bearer("7", "EMPLOYEE", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(post("/api/notifications/1/read")
                        .header("Authorization", bearer("7", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer("7", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].read").value(true));
    }

    @Test
    void redeliveredEventCreatesExactlyOneNotificationPerRecipient() throws Exception {
        String internalToken = new com.travelease.common.security.internal.InternalServiceTokens(
                "test-only-internal-secret-never-deploy-0123456789", "expense-service").currentToken();
        String body = """
                {
                  "entityType": "EXPENSE_CLAIM",
                  "entityId": "41",
                  "action": "REJECTED",
                  "timestamp": "2026-10-11T10:30:00Z",
                  "recipientIds": [9],
                  "eventId": "5b7e8f1c-0d6a-4d1f-9a55-3c2e1f0b9a01",
                  "actorId": 4
                }
                """;
        // the outbox may deliver the same event more than once (at-least-once)
        for (int delivery = 0; delivery < 3; delivery++) {
            mockMvc.perform(post("/api/events")
                            .header(com.travelease.common.security.internal.InternalServiceTokens.HEADER, internalToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer("9", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].entityId").value(41));
    }

    private static String bearer(String subject, String... roles) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    new JWTClaimsSet.Builder()
                            .subject(subject)
                            .claim("roles", List.of(roles))
                            .expirationTime(new Date(System.currentTimeMillis() + 60_000L))
                            .build());
            jwt.sign(new MACSigner(DEV_JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
            return "Bearer " + jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign test JWT", exception);
        }
    }
}
