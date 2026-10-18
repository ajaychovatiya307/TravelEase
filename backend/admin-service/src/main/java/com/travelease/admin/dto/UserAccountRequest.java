package com.travelease.admin.dto;

import java.util.List;

import com.travelease.common.security.RoleType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Write payload for Admin user account create/update (api-contract.md §5.12; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Only TIER-1 structural
 * rules (format/required) are enforced here; business rules such as email uniqueness
 * (FR-ADM-01) belong to the Service layer and are deliberately not encoded.</p>
 */
public record UserAccountRequest(
        /** Display name; non-blank (api-contract.md §5.12). */
        @NotBlank String fullName,

        /** Email address; format-validated here, uniqueness enforced at the service layer (api-contract.md §5.12). */
        @NotBlank @Email String email,

        /** One or more RBAC roles per user (clarification Q1/Q2; api-contract.md §5.12). */
        @NotNull @NotEmpty List<RoleType> roles,

        /** Optional; defaults to {@code true} when omitted (api-contract.md §5.12). */
        Boolean active
) {

    /**
     * Compact canonical constructor - applies the {@code active} default
     * (api-contract.md §5.12: "no (default {@code true})").
     */
    public UserAccountRequest {
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}