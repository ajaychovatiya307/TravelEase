package com.travelease.admin.dto;

import java.util.List;

import com.travelease.common.security.RoleType;

/**
 * Read shape for Admin user accounts (api-contract.md §5.12; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Audit/soft-delete fields
 * ({@code createdAt}, {@code deleted}, {@code version}) are internal and must not
 * appear in the API surface.</p>
 */
public record UserAccountResponse(
        /** Primary key of the user account. */
        Long id,

        /** Display name (api-contract.md §5.12). */
        String fullName,

        /** Email address (api-contract.md §5.12). */
        String email,

        /** RBAC roles assigned to this account (clarification Q2 multi-role). */
        List<RoleType> roles,

        /** Whether the account is active (api-contract.md §5.12). */
        Boolean active
) {
}