package com.travelease.admin.dto;

/**
 * Read shape for the admin-managed travel-request policy (spec.md VAL-04,
 * FR-TR-02, FR-ADM-02; Constitution §6).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Audit/soft-delete
 * fields ({@code createdAt}, {@code deleted}, {@code version}) are internal and
 * must not appear in the API surface.</p>
 */
public record TravelRequestPolicyResponse(
        /** Primary key of the travel-request policy. */
        Long id,

        /** Whether at least one supporting document is mandated (VAL-04, FR-TR-02). */
        boolean documentsMandatory,

        /** Whether this policy is active. */
        boolean active
) {
}