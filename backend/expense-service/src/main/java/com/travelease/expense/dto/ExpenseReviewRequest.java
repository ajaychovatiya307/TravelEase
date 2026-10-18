package com.travelease.expense.dto;

import com.travelease.common.entity.DecisionType;
import com.travelease.common.validation.RequiredWhenRejected;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for Finance review of an expense claim (api-contract.md §5.10).
 * 
 * <p>Enforces that decision is present. Conditional validation for reason 
 * (required and non-blank when decision = REJECTED, VAL-12) is enforced by the
 * shared {@code @RequiredWhenRejected} custom constraint (tasks.md J-02).</p>
 * 
 * <p>Service layer may perform additional business-rule validations (e.g.,
 * status must be PENDING).</p>
 */
@RequiredWhenRejected
public class ExpenseReviewRequest {

    @NotNull(message = "decision is required")
    private DecisionType decision;

    /**
     * Reason for approval or rejection. Required (non-blank) when decision 
     * is REJECTED (VAL-12). Validated by custom constraint (J-04).
     */
    private String reason;

    // Constructors
    public ExpenseReviewRequest() {
    }

    public ExpenseReviewRequest(DecisionType decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    // Getters
    public DecisionType getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    // Setters
    public void setDecision(DecisionType decision) {
        this.decision = decision;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
