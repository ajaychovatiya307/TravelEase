package com.travelease.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.common.event.AuditEntityType;
import com.travelease.notification.NotificationServiceApplication;
import com.travelease.notification.client.FinanceRecipientClient;
import com.travelease.notification.entity.DeliveryStatus;
import com.travelease.notification.entity.NotificationEvent;
import com.travelease.notification.entity.NotificationType;
import com.travelease.notification.repository.NotificationEventRepository;


/** #17 notification pagination: boundaries, size cap, per-user isolation and unread count. */
@SpringBootTest(classes = NotificationServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications-paging-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "server.servlet.context-path="
})
@DisplayName("Notification pagination IT (#17)")
class NotificationPaginationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationEventRepository repository;

    @MockBean
    private FinanceRecipientClient financeRecipientClient;

    private Long firstOfUser7;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        for (int i = 0; i < 25; i++) {
            NotificationEvent saved = repository.save(notification(7L, i, base.plusSeconds(i)));
            if (i == 0) {
                firstOfUser7 = saved.getId();
            }
        }
        repository.save(notification(9L, 100, base));
        repository.save(notification(9L, 101, base.plusSeconds(1)));
    }

    private static NotificationEvent notification(long recipient, long entityId, Instant createdAt) {
        NotificationEvent event = new NotificationEvent();
        event.setEntityType(AuditEntityType.TRAVEL_REQUEST);
        event.setEntityId(entityId);
        event.setRecipientId(recipient);
        event.setMessage("Update " + entityId);
        event.setNotificationType(NotificationType.APPROVAL);
        event.setDeliveryStatus(DeliveryStatus.SENT);
        event.setRetryCount(0);
        event.setCreatedAt(createdAt);
        return event;
    }

    /** A real HS256 user token signed with the test secret (the service's actual decoder verifies it). */
    private static RequestPostProcessor user(long id) {
        String token;
        try {
            com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(
                    new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                    new com.nimbusds.jwt.JWTClaimsSet.Builder().subject(String.valueOf(id))
                            .claim("roles", List.of("EMPLOYEE"))
                            .expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000L)).build());
            jwt.sign(new com.nimbusds.jose.crypto.MACSigner(
                    "test-only-jwt-secret-never-deploy-0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            token = jwt.serialize();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        String bearer = "Bearer " + token;
        return request -> {
            request.addHeader("Authorization", bearer);
            return request;
        };
    }

    @Test
    @DisplayName("pages are newest-first with correct totals and a short last page")
    void pageBoundaries() throws Exception {
        mockMvc.perform(get("/api/notifications").param("page", "0").param("size", "10").with(user(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[0].entityId").value(24))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get("/api/notifications").param("page", "2").param("size", "10").with(user(7)))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[4].entityId").value(0));
        mockMvc.perform(get("/api/notifications").param("page", "9").param("size", "10").with(user(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("oversized and invalid page parameters are clamped (max 100 per page)")
    void sizeCap() throws Exception {
        mockMvc.perform(get("/api/notifications").param("size", "5000").with(user(7)))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(25));
        mockMvc.perform(get("/api/notifications").param("page", "-3").param("size", "0").with(user(7)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    @DisplayName("users only ever see and update their own notifications")
    void isolation() throws Exception {
        mockMvc.perform(get("/api/notifications").with(user(9)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(post("/api/notifications/" + firstOfUser7 + "/read").with(user(9)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the unread count is independent of paging and drops when one is read")
    void unreadCount() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count").with(user(7)))
                .andExpect(jsonPath("$.unreadCount").value(25));
        mockMvc.perform(post("/api/notifications/" + firstOfUser7 + "/read").with(user(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/api/notifications/unread-count").with(user(7)))
                .andExpect(jsonPath("$.unreadCount").value(24));
    }
}
