package com.travelease.expense.entity;

/**
 * Lifecycle status of an expense claim (data-model.md §8). DRAFT supports mid-submission
 * document upload failure handling (FR-DOC-03); PENDING/APPROVED/REJECTED/REIMBURSED
 * cover the Finance review and reimbursement workflow (FR-EXP-06, FR-FIN-01, FR-FIN-03).
 */
public enum ExpenseClaimStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    REIMBURSED
}
