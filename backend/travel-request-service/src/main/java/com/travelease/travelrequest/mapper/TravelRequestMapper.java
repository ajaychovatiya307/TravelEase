package com.travelease.travelrequest.mapper;

import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestDocument;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written mapper for Travel Request entity ↔ DTO conversions (tasks.md G-01).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@code TravelRequest} entity → {@code TravelRequestResponse} DTO</li>
 *   <li>{@code TravelRequestCreateRequest} DTO → {@code TravelRequest} entity</li>
 *   <li>{@code TravelRequestUpdateRequest} DTO → {@code TravelRequest} entity (for updates)</li>
 *   <li>{@code TravelRequestDocument} entity → {@code DocumentSummary} DTO</li>
 * </ul>
 *
 * <p>Adheres to Constitution §6: internal-only fields ({@code deleted}, {@code version},
 * {@code createdBy}, {@code updatedBy}) are never mapped into response DTOs.
 * Document references (e.g., {@code documentIds} in request DTOs) are not directly mapped
 * to entity state; they are resolved by the Service layer.</p>
 */
@Component
public class TravelRequestMapper {

    /**
     * Maps a TravelRequest entity to TravelRequestResponse DTO.
     * Excludes internal-only fields and joins the document list.
     *
     * @param entity the source entity; may be null
     * @param documents the associated documents; may be null
     * @return the mapped response DTO, or null if entity is null
     */
    public TravelRequestResponse toResponse(TravelRequest entity, List<TravelRequestDocument> documents) {
        if (entity == null) {
            return null;
        }

        List<DocumentSummary> documentSummaries = documents != null
                ? documents.stream()
                .map(this::toDocumentSummary)
                .toList()
                : List.of();

        return new TravelRequestResponse(
                entity.getId().toString(),
                entity.getEmployeeId().toString(),
                entity.getDestination(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getJustification(),
                entity.getStatus(),
                entity.getApproverId() != null ? entity.getApproverId().toString() : null,
                entity.getRejectionReason(),
                entity.getResubmissionOfId() != null ? entity.getResubmissionOfId().toString() : null,
                documentSummaries,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Maps a TravelRequestCreateRequest DTO to a new TravelRequest entity.
     * Sets initial status to PENDING; document references are resolved by the Service layer.
     *
     * @param dto the source DTO; assumed non-null
     * @return the mapped entity with status = PENDING
     */
    public TravelRequest toEntity(TravelRequestCreateRequest dto) {
        TravelRequest entity = new TravelRequest();
        entity.setDestination(dto.destination());
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setJustification(dto.justification());
        entity.setStatus(TravelRequestStatus.PENDING);
        // documentIds are handled by the Service layer; not mapped directly to entity
        return entity;
    }

    /**
     * Maps edited travel details onto a new request cycle. The service assigns
     * ownership, approver, status, and resubmission lineage.
     */
    public TravelRequest toEntity(TravelRequestUpdateRequest dto) {
        TravelRequest entity = new TravelRequest();
        updateEntity(dto, entity);
        return entity;
    }

    /**
     * Maps a TravelRequestUpdateRequest DTO to an existing TravelRequest entity.
     * Updates travel details only; status and approver state are managed by the Service layer.
     * Document references (documentIds) are resolved by the Service layer.
     *
     * @param dto the source DTO; assumed non-null
     * @param entity the target entity to update; assumed non-null
     */
    public void updateEntity(TravelRequestUpdateRequest dto, TravelRequest entity) {
        entity.setDestination(dto.destination());
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setJustification(dto.justification());
        // documentIds and status are managed by the Service layer; not updated here
    }

    /**
     * Maps a TravelRequestDocument entity to a DocumentSummary DTO.
     * Excludes internal-only fields (travel request reference, deleted, version, audit fields).
     *
     * @param document the source entity; assumed non-null
     * @return the mapped DTO
     */
    public DocumentSummary toDocumentSummary(TravelRequestDocument document) {
        return new DocumentSummary(
                document.getId().toString(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getStorageReference(),
                document.getUploadStatus()
        );
    }
}
