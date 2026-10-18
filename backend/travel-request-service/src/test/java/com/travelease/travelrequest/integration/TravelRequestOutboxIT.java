package com.travelease.travelrequest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.outbox.OutboxDispatcher;
import com.travelease.common.outbox.OutboxEvent;
import com.travelease.common.outbox.OutboxEventRepository;
import com.travelease.common.outbox.OutboxStatus;
import com.travelease.common.security.internal.InternalServiceClientInterceptor;
import com.travelease.common.security.internal.InternalServiceTokens;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.service.TravelRequestService;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingServiceClient;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyClient;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * #12 transactional outbox, end to end: real service + H2 + real HTTP to a stub audit/notification
 * endpoint that verifies the internal service credential. Not @Transactional: every step commits.
 */
@SpringBootTest(classes = TravelRequestIntegrationTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:travelrequest-outbox-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "travelease.outbox.base-backoff=0s",
        "travelease.outbox.max-attempts=3"
})
@DisplayName("Transactional outbox delivery IT (#12)")
class TravelRequestOutboxIT {

    private static final String INTERNAL_SECRET = "test-only-internal-secret-never-deploy-0123456789";

    /** Stub destination: records requests, answers with the next queued status (default 202). */
    private static final HttpServer STUB;
    private static final ConcurrentLinkedQueue<Integer> NEXT_STATUSES = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<String> RECEIVED_BODIES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger REJECTED_CREDENTIALS = new AtomicInteger();

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        InternalServiceTokens verifier = new InternalServiceTokens(INTERNAL_SECRET, "stub");
        STUB.createContext("/", exchange -> {
            String credential = exchange.getRequestHeaders().getFirst(InternalServiceTokens.HEADER);
            int status;
            if (verifier.verify(credential).isEmpty()) {
                REJECTED_CREDENTIALS.incrementAndGet();
                status = 401;
            } else {
                RECEIVED_BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                        + "|" + exchange.getRequestURI().getPath());
                Integer queued = NEXT_STATUSES.poll();
                status = queued == null ? 202 : queued;
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        STUB.start();
    }

    @DynamicPropertySource
    static void destinations(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        registry.add("travelease.outbox.destinations.audit", () -> base + "/audit/api/events");
        registry.add("travelease.outbox.destinations.notification", () -> base + "/notification/api/events");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Autowired
    private TravelRequestService travelRequestService;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeManagerRoutingServiceClient routingClient;

    @MockBean
    private TravelRequestPolicyClient policyClient;

    @BeforeEach
    void reset() {
        NEXT_STATUSES.clear();
        RECEIVED_BODIES.clear();
        REJECTED_CREDENTIALS.set(0);
        outbox.deleteAll();
        when(routingClient.resolveApprover(anyLong())).thenReturn(2L);
        when(policyClient.documentsMandatory()).thenReturn(false);
        // the employee who submits (actor captured into the event)
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("1").claim("roles", List.of("EMPLOYEE")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private long submitTravelRequest() {
        return Long.parseLong(travelRequestService.submitTravelRequest(1L, new TravelRequestCreateRequest(
                "Pune", LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), "Customer workshop", List.of()))
                .id());
    }

    @Test
    @DisplayName("the business change and one PENDING outbox row per destination commit together, with event id and actor")
    void eventStoredAtomicallyWithChange() throws Exception {
        long id = submitTravelRequest();

        List<OutboxEvent> rows = outbox.findAll();
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING));
        assertThat(rows).extracting(OutboxEvent::getDestination).containsExactlyInAnyOrder("audit", "notification");
        assertThat(rows).extracting(OutboxEvent::getEventId).containsOnly(rows.get(0).getEventId());

        JsonNode payload = objectMapper.readTree(rows.get(0).getPayload());
        assertThat(payload.path("entityType").asText()).isEqualTo("TRAVEL_REQUEST");
        assertThat(payload.path("entityId").asText()).isEqualTo(String.valueOf(id));
        assertThat(payload.path("action").asText()).isEqualTo("CREATED");
        assertThat(payload.path("recipientIds").get(0).asLong()).isEqualTo(2L);
        assertThat(payload.path("actorId").asLong()).isEqualTo(1L);
        assertThat(payload.path("eventId").asText()).isEqualTo(rows.get(0).getEventId());
    }

    @Test
    @DisplayName("if the business transaction rolls back, its event is never stored (no phantom audit entries)")
    void rolledBackChangeLeavesNoEvent() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publisher.publishEvent(new DomainEventEnvelope("TRAVEL_REQUEST", "99", "UPDATED", java.time.Instant.now()));
            throw new IllegalStateException("business rule failed after publishing");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outbox.findAll()).isEmpty();
    }

    @Test
    @DisplayName("publishing outside a transaction fails loudly instead of silently losing the event")
    void publishingWithoutTransactionIsRejected() {
        assertThatThrownBy(() -> publisher.publishEvent(
                new DomainEventEnvelope("TRAVEL_REQUEST", "98", "UPDATED", java.time.Instant.now())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("delivery posts the original payload with a valid internal credential and marks both rows DELIVERED")
    void deliversWithInternalCredential() {
        submitTravelRequest();

        assertThat(dispatcher.dispatchDue()).isEqualTo(2);

        assertThat(outbox.findAll()).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
            assertThat(row.getDeliveredAt()).isNotNull();
        });
        assertThat(REJECTED_CREDENTIALS.get()).isZero();
        assertThat(RECEIVED_BODIES).hasSize(2)
                .anySatisfy(body -> assertThat(body).contains("\"action\":\"CREATED\"").endsWith("/audit/api/events"))
                .anySatisfy(body -> assertThat(body).endsWith("/notification/api/events"));
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    @DisplayName("a downstream outage is retried and the event is delivered once the service recovers")
    void retriesAfterOutage() {
        submitTravelRequest();
        NEXT_STATUSES.add(503);
        NEXT_STATUSES.add(503);

        dispatcher.dispatchDue();
        assertThat(outbox.findAll()).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(row.getAttempts()).isEqualTo(1);
            assertThat(row.getLastError()).isEqualTo("HTTP 503");
        });

        dispatcher.dispatchDue();
        assertThat(outbox.findAll()).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxStatus.DELIVERED));
    }

    @Test
    @DisplayName("pending events survive a restart: a brand-new dispatcher delivers what an unreachable run left behind")
    void pendingEventsSurviveRestart() {
        submitTravelRequest();
        RestTemplate unreachable = new RestTemplate(new SimpleClientHttpRequestFactory());
        OutboxDispatcher.OutboxSettings settings = new OutboxDispatcher.OutboxSettings(true, 50, 5, Duration.ZERO,
                Duration.ofMinutes(1));
        new OutboxDispatcher(outbox, unreachable, new TransactionTemplate(transactionManager),
                Map.of("audit", "http://127.0.0.1:1/down", "notification", "http://127.0.0.1:1/down"), settings,
                Clock.systemUTC()).dispatchDue();
        assertThat(outbox.findAll()).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING));

        // "restart": a fresh dispatcher instance reading the same persisted rows
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        restTemplate.getInterceptors().add(new InternalServiceClientInterceptor(
                new InternalServiceTokens(INTERNAL_SECRET, "travel-request-service")));
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        int delivered = new OutboxDispatcher(outbox, restTemplate, new TransactionTemplate(transactionManager),
                Map.of("audit", base + "/audit/api/events", "notification", base + "/notification/api/events"),
                settings, Clock.systemUTC()).dispatchDue();

        assertThat(delivered).isEqualTo(2);
        assertThat(outbox.findAll()).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxStatus.DELIVERED));
    }

    @Test
    @DisplayName("a permanent 4xx rejection is not retried; retries are bounded by max-attempts")
    void permanentRejectionAndBoundedRetries() {
        submitTravelRequest();
        // audit rejects as invalid (400); notification keeps failing (500)
        NEXT_STATUSES.add(400);
        NEXT_STATUSES.add(500);
        NEXT_STATUSES.add(500);
        NEXT_STATUSES.add(500);

        dispatcher.dispatchDue();
        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        List<OutboxEvent> rows = outbox.findAll();
        OutboxEvent first = rows.stream().filter(r -> r.getLastError() != null && r.getLastError().contains("400"))
                .findFirst().orElseThrow();
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(first.getAttempts()).isEqualTo(1);
        OutboxEvent second = rows.stream().filter(r -> r != first).findFirst().orElseThrow();
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(second.getAttempts()).isEqualTo(3);
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    @DisplayName("the stub really enforces the credential: an unauthenticated delivery is refused and retried")
    void unauthenticatedDeliveryIsRefused() {
        submitTravelRequest();
        RestTemplate noCredential = new RestTemplate(new SimpleClientHttpRequestFactory());
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        new OutboxDispatcher(outbox, noCredential, new TransactionTemplate(transactionManager),
                Map.of("audit", base + "/audit/api/events", "notification", base + "/notification/api/events"),
                new OutboxDispatcher.OutboxSettings(true, 50, 5, Duration.ZERO, Duration.ofMinutes(1)),
                Clock.systemUTC()).dispatchDue();

        assertThat(REJECTED_CREDENTIALS.get()).isEqualTo(2);
        assertThat(RECEIVED_BODIES).isEmpty();
        // 401 is a client error: treated as permanent rejection
        assertThat(outbox.findAll()).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED));
    }
}
