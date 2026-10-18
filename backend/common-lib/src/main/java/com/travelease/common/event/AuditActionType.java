package com.travelease.common.event;

/**
 * Audit action classification persisted in AuditRecord.action (data-model.md §6.1,
 * FR-AUD-01). Published by Travel Request and Expense services after each
 * state-changing transaction commits.
 */
public enum AuditActionType {
    CREATED,
    UPDATED,
    APPROVED,
    REJECTED,
    RESUBMITTED,
    REIMBURSED,
    /** Expense claim moved from DRAFT to PENDING (sent to Finance review). */
    SUBMITTED,
    /** Travel request withdrawn by its owner. */
    CANCELLED,
    /** Pending travel request moved to a different approver by an administrator. */
    REASSIGNED,
    /** User account deactivated / reactivated by an administrator. */
    DEACTIVATED,
    ACTIVATED,
    /** Role assignments added to / removed from a user account. */
    ROLE_GRANTED,
    ROLE_REVOKED,
    /** Temporary password issued by an administrator (value never recorded). */
    PASSWORD_RESET,
    /** User changed their own password (value never recorded). */
    PASSWORD_CHANGED
}
