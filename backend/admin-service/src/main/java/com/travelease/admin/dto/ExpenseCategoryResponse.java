package com.travelease.admin.dto;

/**
 * Read shape for Admin expense categories (api-contract.md §5.15; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Audit/soft-delete fields
 * ({@code createdAt}, {@code deleted}, {@code version}) are internal and must not
 * appear in the API surface.</p>
 */
public record ExpenseCategoryResponse(
        /** Primary key of the expense category. */
        Long id,

        /** Category code (api-contract.md §5.15). */
        String code,

        /** Category label (api-contract.md §5.15). */
        String label,

        /** Whether the category is active (api-contract.md §5.15). */
        Boolean active
) {
}