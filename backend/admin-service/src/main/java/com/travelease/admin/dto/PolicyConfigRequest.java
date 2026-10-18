package com.travelease.admin.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Write payload for Admin expense policy configuration (api-contract.md §5.14; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Only TIER-1 structural
 * rules (format/required) are enforced here; business rules such as "categoryCode
 * must reference an existing ExpenseCategory" and "currency must equal the base
 * currency" (NFR-05) belong to the Service layer and are deliberately not encoded.</p>
 */
public record PolicyConfigRequest(
        /** Category identifier; non-blank (api-contract.md §5.14). */
        @NotBlank String categoryCode,

        /** Receipt threshold amount; must be {@code >= 0} (api-contract.md §5.14; entity VAL-10). */
        @NotNull @PositiveOrZero BigDecimal receiptThresholdAmount,

        /** ISO 4217 three-letter currency code (api-contract.md §5.14; VAL-06/NFR-05 shape). */
        @NotBlank @Size(min = 3, max = 3) String currency,

        /** Optional; defaults to {@code true} when omitted (api-contract.md §5.14). */
        Boolean active
) {

    /**
     * Compact canonical constructor - applies the {@code active} default
     * (api-contract.md §5.14: "no (default {@code true})").
     */
    public PolicyConfigRequest {
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}