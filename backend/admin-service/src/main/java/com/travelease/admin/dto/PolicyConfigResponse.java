package com.travelease.admin.dto;

import java.math.BigDecimal;

/**
 * Read shape for Admin expense policy configurations (api-contract.md §5.14; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Audit/soft-delete fields
 * ({@code createdAt}, {@code deleted}, {@code version}) are internal and must not
 * appear in the API surface.</p>
 */
public record PolicyConfigResponse(
        /** Primary key of the policy configuration. */
        Long id,

        /** Category identifier (api-contract.md §5.14). */
        String categoryCode,

        /** Receipt threshold amount for this category (api-contract.md §5.14). */
        BigDecimal receiptThresholdAmount,

        /** Single base currency (api-contract.md §5.14; NFR-05). */
        String currency,

        /** Whether this configuration is active (api-contract.md §5.14). */
        Boolean active
) {
}