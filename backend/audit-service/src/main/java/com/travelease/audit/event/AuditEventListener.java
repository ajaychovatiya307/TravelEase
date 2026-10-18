package com.travelease.audit.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.travelease.audit.service.AuditIngestionService;
import com.travelease.common.event.DomainEventEnvelope;

/**
 * Event listener for domain events published by Travel Request and Expense services (tasks.md H-10).
 * 
 * <p>Listens for {@link DomainEventEnvelope} events and delegates to {@link AuditIngestionService}
 * to persist immutable audit records. This listener is configured to run after successful
 * transaction commit ({@code TransactionPhase.AFTER_COMMIT}), ensuring audit records are
 * only persisted when the originating transaction has succeeded.</p>
 *
 * <p>Per Constitution §9, side effects (including audit logging) are asynchronous and
 * non-blocking — if this listener fails, it does not roll back the originating transaction.</p>
 */
@Component
public class AuditEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditIngestionService auditIngestionService;

    public AuditEventListener(AuditIngestionService auditIngestionService) {
        this.auditIngestionService = auditIngestionService;
    }

    /**
     * Listens for domain events and ingests them as audit records.
     *
     * <p>This method is invoked after the publishing transaction commits ({@code AFTER_COMMIT}),
     * ensuring that audit records are only created when the source transaction succeeds.
     * If audit ingestion fails, a log warning is recorded but the failure does not propagate
     * back to the originating workflow (non-blocking side effect per Constitution §9).</p>
     *
     * @param event the domain event envelope published by a service
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEventEnvelope event) {
        LOGGER.debug("Audit event listener received event: entityType={}, entityId={}, action={}",
                event.entityType(), event.entityId(), event.action());

        try {
            auditIngestionService.ingestEvent(event);
            LOGGER.info("Successfully ingested audit event: entityType={}, entityId={}, action={}",
                    event.entityType(), event.entityId(), event.action());
        } catch (Exception e) {
            // Log error but do not propagate exception (Constitution §9 - non-blocking side effects)
            LOGGER.warn("Failed to ingest audit event: entityType={}, entityId={}, action={}. Error: {}",
                    event.entityType(), event.entityId(), event.action(), e.getMessage(), e);
            // In production, consider alerting/monitoring on repeated failures
        }
    }
}
