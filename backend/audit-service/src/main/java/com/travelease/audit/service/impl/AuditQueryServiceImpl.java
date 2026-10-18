package com.travelease.audit.service.impl;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.audit.dto.AuditRecordResponse;
import com.travelease.audit.mapper.AuditRecordMapper;
import com.travelease.audit.repository.AuditRecordRepository;
import com.travelease.audit.service.AuditQueryService;
import com.travelease.common.event.AuditEntityType;

/**
 * Audit query service implementation (tasks.md H-10; spec.md FR-AUD-01, AC-14).
 * 
 * <p>Provides read-only, immutable queries over audit records. All methods are
 * marked with {@code @Transactional(readOnly = true)} to optimize database resource
 * allocation and signal immutability to the framework.</p>
 */
@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditQueryServiceImpl.class);

    private final AuditRecordRepository auditRecordRepository;

    public AuditQueryServiceImpl(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAllAudits(Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        return auditRecordRepository.findAll(pageable).map(AuditRecordMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAuditTrail(AuditEntityType entityType, Long entityId, Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }

        LOGGER.debug("Fetching audit trail for entityType={}, entityId={}, page={}",
                entityType, entityId, pageable.getPageNumber());

        // Fetch audit records from repository (already paginated by Spring Data)
        Page<com.travelease.audit.entity.AuditRecord> records = auditRecordRepository
                .findByEntityTypeAndEntityId(entityType, entityId, pageable);

        // Map each record to response DTO
        Page<AuditRecordResponse> responses = records.map(AuditRecordMapper::toResponse);

        LOGGER.debug("Fetched {} audit records for entity", responses.getNumberOfElements());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAuditsByActor(Long actorId, Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }

        LOGGER.debug("Fetching audit records by actor id={}, page={}",
                actorId, pageable.getPageNumber());

        // Fetch audit records produced by the specified actor
        Page<com.travelease.audit.entity.AuditRecord> records = auditRecordRepository
                .findByActorId(actorId, pageable);

        // Map each record to response DTO
        Page<AuditRecordResponse> responses = records.map(AuditRecordMapper::toResponse);

        LOGGER.debug("Fetched {} audit records for actor", responses.getNumberOfElements());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAuditsByDateRange(Instant from, Instant to, Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }

        LOGGER.debug("Fetching audit records in date range: from={}, to={}, page={}",
                from, to, pageable.getPageNumber());

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }

        // Fetch audit records within the specified date range
        Page<com.travelease.audit.entity.AuditRecord> records = auditRecordRepository
                .findByOccurredAtBetween(from, to, pageable);

        // Map each record to response DTO
        Page<AuditRecordResponse> responses = records.map(AuditRecordMapper::toResponse);

        LOGGER.debug("Fetched {} audit records in date range", responses.getNumberOfElements());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAllAudits(Pageable pageable,
            java.util.Collection<AuditEntityType> visibleTypes) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        return auditRecordRepository.findByEntityTypeIn(visibleTypes, pageable).map(AuditRecordMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAuditsByActor(Long actorId, Pageable pageable,
            java.util.Collection<AuditEntityType> visibleTypes) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        return auditRecordRepository.findByActorIdAndEntityTypeIn(actorId, visibleTypes, pageable)
                .map(AuditRecordMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditRecordResponse> getAuditsByDateRange(Instant from, Instant to, Pageable pageable,
            java.util.Collection<AuditEntityType> visibleTypes) {
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }
        return auditRecordRepository.findByOccurredAtBetweenAndEntityTypeIn(from, to, visibleTypes, pageable)
                .map(AuditRecordMapper::toResponse);
    }
}
