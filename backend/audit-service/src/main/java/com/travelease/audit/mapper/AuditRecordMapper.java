package com.travelease.audit.mapper;

import com.travelease.audit.dto.AuditRecordResponse;
import com.travelease.audit.entity.AuditRecord;

/**
 * Mapper for converting {@code AuditRecord} entity ↔ {@code AuditRecordResponse} DTO.
 * 
 * <p>Hand-written (simple one-to-one mapping); decouples the entity from the
 * read-only response shape (Constitution §6 - DTO-only boundary at API layer).</p>
 * 
 * <p>Supports FR-AUD-01 and api-contract.md §5.16 audit query responses.</p>
 */
public class AuditRecordMapper {

    /**
     * Converts an {@code AuditRecord} entity to an immutable {@code AuditRecordResponse} record.
     *
     * @param entity the audit record entity
     * @return a response record representation; never null if entity is non-null
     */
    public static AuditRecordResponse toResponse(AuditRecord entity) {
        if (entity == null) {
            return null;
        }
        return new AuditRecordResponse(
                entity.getId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getActorId(),
                entity.getAction(),
                entity.getBeforeStatus(),
                entity.getAfterStatus(),
                entity.getOccurredAt(),
                entity.getDetails()
        );
    }

    /**
     * Private constructor to prevent instantiation (utility class).
     */
    private AuditRecordMapper() {
    }
}
