package com.travelease.audit.service;

import com.travelease.common.event.DomainEventEnvelope;

/**
 * Service interface for audit event ingestion (tasks.md H-10; spec.md FR-AUD-01).
 *
 * <p>Consumes domain events published by Travel Request and Expense services after
 * state-changing transactions commit, and persists immutable audit records (append-only).
 * This layer provides the business logic for transforming events into audit records.</p>
 *
 * <p>Designed to be invoked asynchronously by {@link com.travelease.audit.event.AuditEventListener}
 * to ensure audit ingestion never blocks the originating workflow (Constitution §9).</p>
 */
public interface AuditIngestionService {

    /**
     * Ingests a domain event and persists a corresponding immutable audit record (FR-AUD-01, AC-14).
     *
     * <p>Converts the DomainEventEnvelope (generic shape published by any service) into an
     * AuditRecord with:
     * <ul>
     *   <li>entityType and entityId extracted from the event</li>
     *   <li>action converted from event string to AuditActionType enum</li>
     *   <li>status fields resolved contextually (e.g., for APPROVED action on EXPENSE_CLAIM, afterStatus = "APPROVED")</li>
     *   <li>actorId resolved from Spring Security context (the current authenticated user)</li>
     *   <li>occurredAt set from event timestamp</li>
     * </ul>
     *
     * <p>Once persisted, the record is immutable and never modified. This method is
     * idempotent within transactional scope and designed to be called by async listeners.</p>
     *
     * @param event the domain event published after a service transaction commits
     * @throws IllegalArgumentException if event structure is invalid (e.g., unknown entityType or action)
     */
    void ingestEvent(DomainEventEnvelope event);
}
