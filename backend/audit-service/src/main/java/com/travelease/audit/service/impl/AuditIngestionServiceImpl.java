package com.travelease.audit.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.audit.entity.AuditRecord;
import com.travelease.audit.repository.AuditRecordRepository;
import com.travelease.audit.service.AuditIngestionService;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.event.DomainEventEnvelope;

/**
 * Audit event ingestion service implementation (tasks.md H-10).
 * 
 * <p>Consumes domain events published by Travel Request and Expense services after
 * state-changing transactions commit, and persists immutable audit records (append-only).
 * This service transforms generic DomainEventEnvelope objects into structured
 * AuditRecord entities.</p>
 */
@Service
public class AuditIngestionServiceImpl implements AuditIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditIngestionServiceImpl.class);
    private static final String DRAFT_STATUS = "DRAFT";
    private static final String PENDING_STATUS = "PENDING";
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String REIMBURSED_STATUS = "REIMBURSED";
    private static final String CANCELLED_STATUS = "CANCELLED";

    private final AuditRecordRepository auditRecordRepository;

    public AuditIngestionServiceImpl(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    @Override
    @Transactional
    public void ingestEvent(DomainEventEnvelope event) {
        LOGGER.debug("Ingesting audit event: entityType={}, entityId={}, action={}, timestamp={}",
                event.entityType(), event.entityId(), event.action(), event.timestamp());

        // Idempotent re-delivery: the producer's outbox delivers at least once
        if (event.eventId() != null && auditRecordRepository.existsByEventId(event.eventId())) {
            LOGGER.info("Duplicate audit event {} ignored", event.eventId());
            return;
        }
        try {
            // Parse entity type
            AuditEntityType entityType = AuditEntityType.valueOf(event.entityType());

            // Parse action type
            AuditActionType actionType = AuditActionType.valueOf(event.action());

            // Parse entity ID
            Long entityId = Long.valueOf(event.entityId());

            // The producer records who acted; delivery itself is authenticated as a service.
            // Fall back to the security context for legacy callers without actorId.
            Long actorId = event.actorId() != null ? event.actorId() : resolveActorId();

            // Determine before/after status based on action type
            String beforeStatus = null;
            String afterStatus = resolveAfterStatus(actionType);

            // Handle state transitions that have a before-status
            if (actionType == AuditActionType.APPROVED || actionType == AuditActionType.REJECTED
                    || actionType == AuditActionType.RESUBMITTED) {
                // For these actions, the before-status is typically PENDING
                beforeStatus = PENDING_STATUS;
            } else if (actionType == AuditActionType.SUBMITTED) {
                // Submission: a DRAFT expense claim enters Finance review
                beforeStatus = DRAFT_STATUS;
            } else if (actionType == AuditActionType.REIMBURSED) {
                // Reimbursement: before-status is APPROVED
                beforeStatus = APPROVED_STATUS;
            }
            // CREATED and UPDATED typically have no before-status
            if (!entityType.isWorkflowRecord()) {
                // administrative records (users, roles, policies, mappings) have no lifecycle status
                beforeStatus = null;
                afterStatus = null;
            } else if (actionType == AuditActionType.REASSIGNED) {
                beforeStatus = PENDING_STATUS;
            }

            // Create and persist audit record
            AuditRecord auditRecord = new AuditRecord();
            auditRecord.setEntityType(entityType);
            auditRecord.setEntityId(entityId);
            auditRecord.setActorId(actorId);
            auditRecord.setAction(actionType);
            auditRecord.setBeforeStatus(beforeStatus);
            auditRecord.setAfterStatus(afterStatus);
            auditRecord.setOccurredAt(event.timestamp());
            auditRecord.setEventId(event.eventId());
            auditRecord.setDetails(formatDetails(event.metadata()));

            AuditRecord persistedRecord = auditRecordRepository.save(auditRecord);
            LOGGER.info("Audit record persisted: id={}, entityType={}, entityId={}, action={}",
                    persistedRecord.getId(), entityType, entityId, actionType);

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid event format for entityType=" + event.entityType()
                            + ", action=" + event.action() + ": " + e.getMessage(),
                    e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to ingest audit event for entityId=" + event.entityId(), e);
        }
    }

    /**
     * Resolves the actor ID from Spring Security context.
     * If no authenticated principal is found, defaults to a system actor ID (0L).
     *
     * @return the actor ID, or 0L if no authentication context is available
     */
    /** "key=value; key=value" in key order, capped to the column size; producers never include secrets. */
    static String formatDetails(java.util.Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String joined = new java.util.TreeMap<>(metadata).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("; "));
        return joined.length() <= 2000 ? joined : joined.substring(0, 2000);
    }

    private Long resolveActorId() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null) {
            Authentication auth = context.getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                if (auth instanceof JwtAuthenticationToken jwtAuthentication) {
                    String subject = jwtAuthentication.getToken().getSubject();
                    try {
                        return Long.valueOf(subject);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Authenticated JWT subject is not a valid Long ID: {}", subject);
                    }
                }
                String actorName = auth.getName();
                try {
                    return Long.valueOf(actorName);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Authenticated principal name is not a valid Long ID: {}", actorName);
                }
            }
        }
        // Default system actor ID when no authentication context (e.g., system-triggered events)
        LOGGER.debug("No authenticated principal found; using system actor ID 0");
        return 0L;
    }

    /**
     * Determines the after-status based on the action type and entity type.
     * For example, APPROVED action results in afterStatus = "APPROVED".
     *
     * @param entityType the type of entity being audited
     * @param actionType the action/event type
     * @return the after-status string for the audit record
     */
    private String resolveAfterStatus(AuditActionType actionType) {
        return switch (actionType) {
            case CREATED -> DRAFT_STATUS;  // Newly created claims/requests start in DRAFT
            case UPDATED -> PENDING_STATUS;  // After update, typically moves to PENDING (if not already approved)
            case APPROVED -> APPROVED_STATUS;
            case REJECTED -> REJECTED_STATUS;
            case RESUBMITTED -> PENDING_STATUS;  // Resubmitted requests go back to PENDING
            case REIMBURSED -> REIMBURSED_STATUS;
            case SUBMITTED -> PENDING_STATUS;  // Draft expense claim sent to Finance review
            case CANCELLED -> CANCELLED_STATUS;  // Travel request withdrawn by its owner
            case REASSIGNED -> PENDING_STATUS;  // Still awaiting a decision, by a different approver
            // administrative actions: no workflow status (statuses cleared for admin records)
            case DEACTIVATED, ACTIVATED, ROLE_GRANTED, ROLE_REVOKED, PASSWORD_RESET, PASSWORD_CHANGED -> null;
        };
    }
}
