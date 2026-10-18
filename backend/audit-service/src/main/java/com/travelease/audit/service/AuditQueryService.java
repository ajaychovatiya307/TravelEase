package com.travelease.audit.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.travelease.audit.dto.AuditRecordResponse;
import com.travelease.common.event.AuditEntityType;

/**
 * Service interface for audit record queries (tasks.md H-10; spec.md FR-AUD-01, AC-14).
 *
 * <p>Provides read-only queries to retrieve audit records (immutable, append-only history).
 * All methods are marked with {@code @Transactional(readOnly = true)} to optimize database
 * resource allocation and indicate immutability.</p>
 *
 * <p>Every finder returns paginated results (Constitution §5, NFR-07) to support efficient
 * audit trail retrieval for large datasets (e.g., audit trails spanning months).</p>
 */
public interface AuditQueryService {

    /** Lists all immutable audit records for the unfiltered viewer, paginated. */
    Page<AuditRecordResponse> getAllAudits(Pageable pageable);

    /**
     * Retrieves the audit trail for a single entity (e.g., all actions affecting one travel request).
     *
     * <p>Used by Controllers to populate audit tabs on entity detail pages (api-contract.md §4.4).</p>
     *
     * @param entityType the type of entity being audited (TRAVEL_REQUEST or EXPENSE_CLAIM)
     * @param entityId   the identifier of the entity in its owning service
     * @param pageable   pagination and sort parameters (recommended: sort by occurredAt DESC for newest first)
     * @return a page of audit records for that entity, sorted and paginated as requested
     * @throws IllegalArgumentException if pageable is null (caller's responsibility to supply it)
     */
    Page<AuditRecordResponse> getAuditTrail(AuditEntityType entityType, Long entityId, Pageable pageable);

    /**
     * Retrieves all actions performed by one user (e.g., all approvals by a Finance manager).
     *
     * <p>Used to generate "actions by actor" reports.</p>
     *
     * @param actorId  the identifier of the acting user (UserAccount.id)
     * @param pageable pagination and sort parameters
     * @return a page of audit records created by that actor
     * @throws IllegalArgumentException if pageable is null
     */
    Page<AuditRecordResponse> getAuditsByActor(Long actorId, Pageable pageable);

    /**
     * Retrieves audit records within a specific time range (e.g., audit trail for a given month).
     *
     * <p>Used for compliance/reporting queries (api-contract.md §4.4 and §7).</p>
     *
     * @param from     inclusive start time of the range
     * @param to       inclusive end time of the range
     * @param pageable pagination and sort parameters
     * @return a page of audit records whose occurredAt falls within [from, to]
     * @throws IllegalArgumentException if pageable is null or if from > to
     */
    Page<AuditRecordResponse> getAuditsByDateRange(Instant from, Instant to, Pageable pageable);

    /**
     * Role-scoped variants (#13): only records whose entity type is in {@code visibleTypes}. Used for
     * callers who may see workflow records but not administrative ones (user accounts, policies).
     */
    Page<AuditRecordResponse> getAllAudits(Pageable pageable, java.util.Collection<AuditEntityType> visibleTypes);

    Page<AuditRecordResponse> getAuditsByActor(Long actorId, Pageable pageable,
            java.util.Collection<AuditEntityType> visibleTypes);

    Page<AuditRecordResponse> getAuditsByDateRange(Instant from, Instant to, Pageable pageable,
            java.util.Collection<AuditEntityType> visibleTypes);
}
