package com.travelease.travelrequest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.travelease.travelrequest.entity.TravelRequestStatus;

/**
 * Response body for travel-request reads and writes (tasks.md E-01;
 * api-contract.md §5.3).
 *
 * <p>DTO-only boundary: no internal-only entity fields ({@code deleted},
 * {@code version}, audit metadata) are exposed (Constitution §6). Identifiers are
 * carried as strings, the contract's opaque representation (api-contract.md §3.1).</p>
 */
public record TravelRequestResponse(
        String id,
        String employeeId,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String justification,
        TravelRequestStatus status,
        String approverId,
        String rejectionReason,
        String resubmissionOfId,
        List<DocumentSummary> documents,
        Instant createdAt,
        Instant updatedAt) {
}