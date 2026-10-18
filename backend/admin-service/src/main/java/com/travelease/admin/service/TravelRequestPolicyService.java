package com.travelease.admin.service;

import com.travelease.admin.dto.TravelRequestPolicyRequest;
import com.travelease.admin.dto.TravelRequestPolicyResponse;

/**
 * Admin-facing management of the singleton travel-request policy (spec.md
 * VAL-04, FR-TR-02, FR-ADM-02; api-contract.md §4.3).
 *
 * <p>The active policy's {@code documentsMandatory} flag is the source of truth
 * for the VAL-04 mandate - "at least one supporting document is required only
 * when mandated by policy" - which the Travel-Request Service resolves
 * synchronously at submission time (FR-TR-02).</p>
 */
public interface TravelRequestPolicyService {

    /**
     * Resolves the active travel-request policy, or the safe MVP default
     * ({@code documentsMandatory = false}, non-mandated; clarification Q7) when
     * no active row exists. Never throws.
     *
     * @return the active policy's response shape, or the default when absent
     */
    TravelRequestPolicyResponse getActivePolicy();

    /**
     * Upserts the singleton travel-request policy: updates the existing active
     * row in place when one exists, otherwise creates a new active row (FR-ADM-02,
     * VAL-04).
     *
     * @param request validated upsert payload
     * @return the persisted policy's response shape
     */
    TravelRequestPolicyResponse upsertPolicy(TravelRequestPolicyRequest request);
}