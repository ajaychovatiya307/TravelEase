package com.travelease.expense.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Local representation of a Travel Request response from the Travel Request Service.
 * Used by the Expense Service for cross-service REST calls (Constitution §2, §3).
 *
 * <p>This DTO is local to the Expense Service and mirrors the Travel Request Service's
 * public API contract. It enables the Expense Service to consume travel request data
 * without depending on the other service's internal entity/DTO structures.</p>
 */
public record TravelRequestResponse(
        String id,
        String employeeId,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String justification,
        String status,
        String approverId,
        String rejectionReason,
        String resubmissionOfId,
        List<Object> documents,
        Instant createdAt,
        Instant updatedAt) {
}
