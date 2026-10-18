package com.travelease.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Write payload for the admin-managed travel-request policy (spec.md VAL-04,
 * FR-TR-02, FR-ADM-02; Constitution §6).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage). Only TIER-1
 * structural rules (required) are enforced here; singleton handling (at most
 * one non-deleted active row) belongs to the Service layer and is deliberately
 * not encoded.</p>
 */
public record TravelRequestPolicyRequest(
        /**
         * VAL-04 / FR-TR-02 mandate flag - when {@code true}, submitting a
         * travel request requires at least one supporting document.
         */
        @NotNull(message = "documentsMandatory is required") Boolean documentsMandatory
) {
}