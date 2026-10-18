package com.travelease.admin.dto;

/**
 * One-time display of an administrator-issued temporary password. It is never stored in clear text,
 * never logged or audited, and the user must replace it at next sign-in.
 */
public record PasswordResetResponse(String temporaryPassword) {
}
