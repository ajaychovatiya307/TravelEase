package com.travelease.expense.client.dto;

import java.math.BigDecimal;

/**
 * Local representation of a Policy Config response from the Admin Service.
 * Used by the Expense Service for cross-service REST calls (Constitution §2, §3).
 *
 * <p>This DTO is local to the Expense Service and mirrors the Admin Service's
 * public API contract (api-contract.md §5.14). It enables the Expense Service to
 * consume policy configuration data without depending on the Admin Service's
 * internal entity/DTO structures.</p>
 */
public record PolicyConfigResponse(
        Long id,
        String categoryCode,
        BigDecimal receiptThresholdAmount,
        String currency,
        Boolean active) {
}
