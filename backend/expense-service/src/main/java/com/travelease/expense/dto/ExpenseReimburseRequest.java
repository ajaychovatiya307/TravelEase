package com.travelease.expense.dto;

import java.time.Instant;

/**
 * Request DTO for marking an approved expense claim as reimbursed (api-contract.md §5.11).
 * 
 * <p>{@code reimbursedAt} is optional and defaults to server time if not provided.
 * Business-rule validation (expense must be APPROVED) is enforced at Service layer.</p>
 * 
 * <p>Immutable by design (Constitution §6).</p>
 */
public class ExpenseReimburseRequest {

    /**
     * Timestamp when the expense was reimbursed. Optional; if not provided,
     * Service layer defaults to current server time. Nullable here to support
     * optional request field mapping.
     */
    private Instant reimbursedAt;

    // Constructors
    public ExpenseReimburseRequest() {
    }

    public ExpenseReimburseRequest(Instant reimbursedAt) {
        this.reimbursedAt = reimbursedAt;
    }

    // Getter
    public Instant getReimbursedAt() {
        return reimbursedAt;
    }

    // Setter
    public void setReimbursedAt(Instant reimbursedAt) {
        this.reimbursedAt = reimbursedAt;
    }
}
