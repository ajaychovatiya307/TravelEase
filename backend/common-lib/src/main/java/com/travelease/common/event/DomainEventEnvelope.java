package com.travelease.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared domain-event envelope published after a state-changing transaction commits
 * (plan.md §6.4/§9, Constitution §9). Consumed asynchronously by the Audit and
 * Notification services (FR-AUD-01, FR-NOT-01). Shape only.
 *
 * entityType and action are carried as strings so the envelope stays decoupled from the
 * AuditEntityType / AuditActionType enums.
 *
 * <p>Delivery fields (all optional on the wire, so older payloads still deserialize):</p>
 * <ul>
 *   <li>{@code eventId} — unique id assigned by the producer's outbox; consumers use it to make
 *       re-delivery idempotent (no duplicate audit rows or notifications).</li>
 *   <li>{@code actorId} — the authenticated user who caused the change, captured by the producer
 *       (delivery itself is authenticated as a service, not as that user).</li>
 *   <li>{@code metadata} — small, non-sensitive change details for the audit log (field names,
 *       role names, ids). Never passwords, tokens or secrets.</li>
 * </ul>
 */
public record DomainEventEnvelope(
        String entityType,
        String entityId,
        String action,
        Instant timestamp,
        List<Long> recipientIds,
        String eventId,
        Long actorId,
        Map<String, String> metadata) {

    /**
     * Normalizes absent collections. JSON deserialization goes through this canonical constructor,
     * so missing or null {@code recipientIds}/{@code metadata} mean "none".
     */
    public DomainEventEnvelope {
        if (recipientIds == null) {
            recipientIds = List.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /** Envelope with recipients, before outbox enrichment (no event id / actor yet). */
    public DomainEventEnvelope(String entityType, String entityId, String action, Instant timestamp,
            List<Long> recipientIds) {
        this(entityType, entityId, action, timestamp, recipientIds, null, null, Map.of());
    }

    /**
     * Backwards-compatible constructor for consumers that do not need recipient
     * context, such as audit-service.
     */
    public DomainEventEnvelope(String entityType, String entityId, String action, Instant timestamp) {
        this(entityType, entityId, action, timestamp, List.of());
    }

    /** Copy with delivery identity and actor filled in (used by the outbox writer). */
    public DomainEventEnvelope withDelivery(String newEventId, Long newActorId) {
        return new DomainEventEnvelope(entityType, entityId, action, timestamp, recipientIds, newEventId, newActorId,
                metadata);
    }

    /** Copy with audit metadata attached. */
    public DomainEventEnvelope withMetadata(Map<String, String> newMetadata) {
        return new DomainEventEnvelope(entityType, entityId, action, timestamp, recipientIds, eventId, actorId,
                newMetadata);
    }
}
