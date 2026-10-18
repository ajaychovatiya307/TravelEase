package com.travelease.admin.mapper;

import com.travelease.admin.dto.TravelRequestPolicyRequest;
import com.travelease.admin.dto.TravelRequestPolicyResponse;
import com.travelease.admin.entity.TravelRequestPolicy;

import org.springframework.stereotype.Component;

/**
 * Entity ↔ DTO mapping for {@link TravelRequestPolicy} (spec.md VAL-04, FR-TR-02,
 * FR-ADM-02; api-contract.md §5.14).
 *
 * <p>Isolates mapping logic from Controller/Service per Constitution §6; no
 * internal-only fields ({@code deleted}, {@code version}, audit metadata) are copied
 * into {@link TravelRequestPolicyResponse}.</p>
 */
@Component
public class TravelRequestPolicyMapper {

    /**
     * Applies a validated request to a travel-request policy entity (VAL-04,
     * FR-TR-02, FR-ADM-02). The singleton policy is always kept active by the
     * service upsert, so {@code active} is set to {@code true} in both the create
     * and the in-place update path.
     *
     * @param policy  existing (update) or new (create) policy entity
     * @param request validated upsert payload
     */
    public void applyRequest(TravelRequestPolicy policy, TravelRequestPolicyRequest request) {
        policy.setDocumentsMandatory(request.documentsMandatory());
        policy.setActive(true);
    }

    /**
     * Converts a persisted {@link TravelRequestPolicy} to its API response shape
     * (api-contract.md §5.14).
     *
     * @param policy persisted travel-request policy
     * @return response DTO with no internal-only fields
     */
    public TravelRequestPolicyResponse toResponse(TravelRequestPolicy policy) {
        return new TravelRequestPolicyResponse(
                policy.getId(),
                Boolean.TRUE.equals(policy.getDocumentsMandatory()),
                policy.isActive());
    }
}