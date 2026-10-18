package com.travelease.audit.repository;

import java.time.Instant;

import com.travelease.audit.entity.AuditRecord;
import com.travelease.common.event.AuditEntityType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Append-only Spring Data repository for audit records (tasks.md D-09;
 * data-model.md §6.1 and §11; spec.md FR-AUD-01, AC-14).
 *
 * <p>Deliberately extends only the bare {@link Repository} marker interface - not
 * {@code JpaRepository} or {@code CrudRepository} - so that no delete, delete-all
 * or bulk-update operation is ever exposed: an {@code AuditRecord} is written once
 * and never modified or removed (data-model.md §11 immutability guidance).
 * Only {@code save} and read-side finders are declared.</p>
 *
 * <p>Every finder is paginated (Constitution §5) to back the paginated
 * {@code GET /api/audit} query surface (api-contract.md §4.4 and §7).</p>
 */
@org.springframework.stereotype.Repository
public interface AuditRecordRepository extends Repository<AuditRecord, Long> {

    /**
     * Appends one immutable audit record (FR-AUD-01, AC-14).
     *
     * @param entity the record to persist (never subsequently modified)
     * @return the persisted record with its generated id
     */
    <S extends AuditRecord> S save(S entity);

    /** Idempotency probe for re-delivered events (outbox at-least-once delivery). */
    boolean existsByEventId(String eventId);

    /** All immutable audit records, paginated for the default viewer query. */
    Page<AuditRecord> findAll(Pageable pageable);

    /**
     * The audit trail of one entity, paginated (FR-AUD-01, AC-14).
     *
     * @param entityType type of the audited entity (TRAVEL_REQUEST / EXPENSE_CLAIM)
     * @param entityId   the audited entity's identifier
     * @param pageable   pagination and sorting (e.g. occurredAt,desc)
     * @return a page of audit records for that entity
     */
    Page<AuditRecord> findByEntityTypeAndEntityId(AuditEntityType entityType, Long entityId, Pageable pageable);

    /**
     * Audit records produced by one actor, paginated (FR-AUD-01).
     *
     * @param actorId  the acting user's identifier
     * @param pageable pagination and sorting
     * @return a page of audit records produced by that actor
     */
    Page<AuditRecord> findByActorId(Long actorId, Pageable pageable);

    /**
     * Audit records whose {@code occurredAt} falls inside a date range, paginated
     * (FR-AUD-01, AC-14).
     *
     * @param from     range start (inclusive)
     * @param to       range end (inclusive)
     * @param pageable pagination and sorting
     * @return a page of audit records inside the range
     */
    Page<AuditRecord> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);

    /** All records of the given entity types (role-scoped listing, #13). */
    Page<AuditRecord> findByEntityTypeIn(java.util.Collection<AuditEntityType> entityTypes, Pageable pageable);

    /** Records by one actor, limited to the given entity types (#13). */
    Page<AuditRecord> findByActorIdAndEntityTypeIn(Long actorId, java.util.Collection<AuditEntityType> entityTypes,
            Pageable pageable);

    /** Records inside a date range, limited to the given entity types (#13). */
    Page<AuditRecord> findByOccurredAtBetweenAndEntityTypeIn(Instant from, Instant to,
            java.util.Collection<AuditEntityType> entityTypes, Pageable pageable);
}
