package com.travelease.common.outbox;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelease.common.event.DomainEventEnvelope;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures every {@link DomainEventEnvelope} a service publishes and stores it in the outbox, one row
 * per destination, inside the publisher's transaction ({@code MANDATORY}: publishing outside a
 * transaction is a programming error and fails loudly instead of silently losing the event).
 */
public class OutboxEventWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Map<String, String> destinations;
    private final Clock clock;

    public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper,
            Map<String, String> destinations, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.destinations = destinations;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onDomainEvent(DomainEventEnvelope event) {
        DomainEventEnvelope enriched = event.withDelivery(
                event.eventId() != null ? event.eventId() : UUID.randomUUID().toString(),
                event.actorId() != null ? event.actorId() : currentActorId());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(enriched);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Domain event could not be serialized", ex);
        }
        for (String destination : destinations.keySet()) {
            repository.save(OutboxEvent.pending(enriched.eventId(), destination, payload, clock.instant()));
        }
    }

    /** The authenticated user (JWT subject) that caused the change, when there is one. */
    static Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            try {
                return Long.valueOf(jwt.getToken().getSubject());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
