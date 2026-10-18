package com.travelease.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Write payload for Admin expense category create/update (api-contract.md §5.15; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Only TIER-1 structural
 * rules (format/required) are enforced here; business rules such as code uniqueness
 * belong to the Service layer and are deliberately not encoded.</p>
 */
public record ExpenseCategoryRequest(
        /** Category code; non-blank (api-contract.md §5.15). */
        @NotBlank String code,

        /** Category label; non-blank (api-contract.md §5.15). */
        @NotBlank String label,

        /** Optional; defaults to {@code true} when omitted (api-contract.md §5.15). */
        Boolean active
) {

    /**
     * Compact canonical constructor - applies the {@code active} default
     * (api-contract.md §5.15: "no (default {@code true})").
     */
    public ExpenseCategoryRequest {
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}