package com.travelease.common.entity;

/**
 * Approval/review decision type used by both Travel Request and Expense Claim
 * workflows (data-model.md §8; spec.md FR-APR-01, FR-APR-02, FR-FIN-01).
 * 
 * <p>Shared in common-lib for cross-service reuse.</p>
 */
public enum DecisionType {
    APPROVED,
    REJECTED
}
