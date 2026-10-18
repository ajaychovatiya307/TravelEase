package com.travelease.travelrequest.dto;

import com.travelease.common.validation.RequiredWhenRejected;
import com.travelease.travelrequest.entity.DecisionType;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for approving or rejecting a {@code Pending} travel request
 * (tasks.md E-02; api-contract.md §5.4; spec.md FR-APR-01, FR-APR-02, AC-03, AC-04).
 *
 * <p>{@code reason} is required and non-blank only when {@code decision} is
 * {@code REJECTED} (VAL-12), enforced by the shared {@code @RequiredWhenRejected}
 * custom constraint (tasks.md J-02).</p>
 */
@RequiredWhenRejected
public record ApprovalDecisionRequest(

        /** The decision taken; {@code APPROVED} or {@code REJECTED}; required. */
        @NotNull(message = "decision is required")
        DecisionType decision,

        /** Rejection reason; required and non-blank only when decision = REJECTED (VAL-12, wired in J-02). */
        String reason) {
}