package com.travelease.travelrequest.mapper;

import com.travelease.travelrequest.dto.ApprovalDecisionRequest;
import com.travelease.travelrequest.entity.ApprovalDecision;
import com.travelease.travelrequest.entity.TravelRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Hand-written mapper for ApprovalDecision entity ↔ DTO conversions (tasks.md G-02).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@code ApprovalDecisionRequest} DTO → {@code ApprovalDecision} entity</li>
 *   <li>Helper methods to extract approval decision fragments for embedding in
 *       {@code TravelRequestResponse} (e.g., latest {@code rejectionReason},
 *       {@code approverId})</li>
 * </ul>
 *
 * <p>ApprovalDecision entities are not directly exposed in API responses; instead,
 * their data is embedded as fragments in {@code TravelRequestResponse} (Constitution §6,
 * api-contract.md §5.3). The mapper supports both individual decision mapping and
 * history aggregation.</p>
 */
@Component
public class ApprovalDecisionMapper {

    /**
     * Maps an ApprovalDecisionRequest DTO to a new ApprovalDecision entity.
     * Sets the decision and reason; {@code decidedBy} and {@code decidedAt} must
     * be populated by the Service layer (they depend on the authenticated user and
     * current time).
     *
     * @param dto the source DTO; assumed non-null
     * @param travelRequest the owning travel request; assumed non-null
     * @return the mapped entity with decision and reason populated
     */
    public ApprovalDecision toEntity(ApprovalDecisionRequest dto, TravelRequest travelRequest) {
        ApprovalDecision entity = new ApprovalDecision();
        entity.setTravelRequest(travelRequest);
        entity.setDecision(dto.decision());
        entity.setReason(dto.reason());
        // decidedBy and decidedAt are set by the Service layer
        return entity;
    }

    /**
     * Populates approval metadata into an ApprovalDecision entity from authenticated context.
     * Called after {@link #toEntity(ApprovalDecisionRequest, TravelRequest)} to finalize
     * the entity before persistence.
     *
     * @param entity the target entity; assumed non-null
     * @param decidedBy the ID of the approver; assumed non-null
     * @param decidedAt the timestamp of the decision; assumed non-null
     */
    public void setApprovalMetadata(ApprovalDecision entity, Long decidedBy, Instant decidedAt) {
        entity.setDecidedBy(decidedBy);
        entity.setDecidedAt(decidedAt);
    }

    /**
     * Extracts the rejection reason from the latest approval decision.
     * Used to populate the {@code rejectionReason} field in {@code TravelRequestResponse}.
     *
     * @param decision the latest approval decision; may be null
     * @return the rejection reason if decision is REJECTED, or null otherwise
     */
    public String extractRejectionReason(ApprovalDecision decision) {
        if (decision == null) {
            return null;
        }
        return decision.getReason();
    }

    /**
     * Extracts the approver ID from the latest approval decision.
     * Used to populate the {@code approverId} field in {@code TravelRequestResponse}.
     *
     * @param decision the latest approval decision; may be null
     * @return the ID of the approver if decision exists, or null otherwise
     */
    public Long extractApproverId(ApprovalDecision decision) {
        if (decision == null) {
            return null;
        }
        return decision.getDecidedBy();
    }
}
