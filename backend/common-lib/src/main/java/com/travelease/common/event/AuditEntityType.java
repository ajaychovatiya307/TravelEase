package com.travelease.common.event;

/**
 * Logical entity types referenced by audit and notification events (data-model.md §8).
 * No DB-level foreign key exists across services; entityId is resolved against the
 * owning service (Constitution §2).
 */
public enum AuditEntityType {
    TRAVEL_REQUEST,
    EXPENSE_CLAIM,
    // Administrative records (admin-service, #13)
    USER_ACCOUNT,
    EMPLOYEE_MANAGER_MAPPING,
    POLICY_CONFIG,
    EXPENSE_CATEGORY,
    TRAVEL_REQUEST_POLICY;

    /** Workflow records carry a lifecycle status; administrative records do not. */
    public boolean isWorkflowRecord() {
        return this == TRAVEL_REQUEST || this == EXPENSE_CLAIM;
    }
}
