package com.travelease.common.security;

/**
 * RBAC roles shared across all services (clarification Q1/Q2, NFR-10). A user may be
 * assigned multiple roles simultaneously; permitted actions are evaluated per role, not
 * per user identity.
 */
public enum RoleType {
    EMPLOYEE,
    MANAGER,
    FINANCE,
    ADMIN
}
