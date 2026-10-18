package com.travelease.audit.dto;

import java.time.Instant;

import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;

/**
 * Read-only audit query response (api-contract.md §5.16; tasks.md E-06).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage): exposes audit data as a
 * read shape decoupled from the {@code AuditRecord} entity. No Bean Validation - this is
 * a response/read shape, never a write payload.</p>
 */
public record AuditRecordResponse(
        /** Identifier of the audit record (api-contract.md §5.16; tasks.md E-06). */
        Long id,

        /** Logical entity type of the audited subject (api-contract.md §5.16; tasks.md E-06). */
        AuditEntityType entityType,

        /** Identifier of the audited entity in its owning service (api-contract.md §5.16; tasks.md E-06). */
        Long entityId,

        /** Logical reference to the acting user (UserAccount.id) (api-contract.md §5.16; tasks.md E-06). */
        Long actorId,

        /** Action classification (api-contract.md §5.16; tasks.md E-06). */
        AuditActionType action,

        /** Status before the action; deliberately nullable - a CREATED action has no before-status (api-contract.md §5.16; tasks.md E-06). */
        String beforeStatus,

        /** Status after the action (api-contract.md §5.16; tasks.md E-06). */
        String afterStatus,

        /** When the action occurred (api-contract.md §5.16; tasks.md E-06). */
        Instant occurredAt,

        /** Human-readable change summary for administrative records (e.g. "role ADMIN granted"); may be null. */
        String details
) { }