package com.travelease.common.security;

/**
 * RBAC role-name constants shared across all services (clarification Q1/Q2, NFR-10).
 * Values align with the RoleType enum and the role strings used in Spring Security
 * method-level checks.
 */
public final class Roles {

    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String MANAGER = "MANAGER";
    public static final String FINANCE = "FINANCE";
    public static final String ADMIN = "ADMIN";

    private Roles() {
        // constants holder - not instantiable
    }
}