package com.travelease.expense.client.dto;

/**
 * Enum representing travel request status values from the Travel Request Service API.
 * Used by the Expense Service to validate cross-service responses (Constitution §2, §3).
 *
 * <p>This enum mirrors the Travel Request Service's public API status values.
 * It enables the Expense Service to consume status information without depending on
 * the other service's internal status entity definitions.</p>
 */
public enum TravelRequestStatusEnum {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    RESUBMITTED
}
