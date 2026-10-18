package com.travelease.admin.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Write payload for Admin employee-to-manager mapping create/update (api-contract.md §5.13; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Only TIER-1 structural
 * rules (required) are enforced here; business rules such as "employeeId/managerId
 * must reference existing users" (FR-ADM-01) belong to the Service layer and are
 * deliberately not encoded.</p>
 */
public record EmployeeManagerMappingRequest(
        /** Employee to assign a manager to; must reference an existing user (existence enforced at the service layer, api-contract.md §5.13). */
        @NotNull Long employeeId,

        /**
         * Manager assigned to the employee; deliberately nullable - top-of-hierarchy
         * managers have no manager, and their approvals fall back to Finance
         * (clarification Q3, FR-TR-05; api-contract.md §5.13).
         */
        Long managerId,

        /** Date from which the mapping is effective (api-contract.md §5.13). */
        @NotNull LocalDate effectiveFrom
) {
}