package com.travelease.admin.dto;

import java.time.LocalDate;

/**
 * Read shape for Admin employee-to-manager mappings (api-contract.md §5.13; tasks.md E-05).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Audit/soft-delete fields
 * ({@code createdAt}, {@code deleted}, {@code version}) are internal and must not
 * appear in the API surface.</p>
 */
public record EmployeeManagerMappingResponse(
        /** Primary key of the mapping (api-contract.md §5.13). */
        Long id,

        /** Employee this mapping belongs to (UserAccount.id reference, api-contract.md §5.13). */
        Long employeeId,

        /**
         * Manager assigned to the employee (UserAccount.id reference); null for
         * top-of-hierarchy managers, whose approvals fall back to Finance
         * (clarification Q3, FR-TR-05; api-contract.md §5.13).
         */
        Long managerId,

        /** Date from which the mapping is effective (api-contract.md §5.13). */
        LocalDate effectiveFrom
) {
}