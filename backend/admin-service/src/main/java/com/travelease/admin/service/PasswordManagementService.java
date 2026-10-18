package com.travelease.admin.service;

import com.travelease.admin.dto.PasswordResetResponse;

/** Password lifecycle (#14). Every operation invalidates previously issued tokens (credentials version). */
public interface PasswordManagementService {

    /** Self-service change; the current password must match. */
    void changePassword(Long userId, String currentPassword, String newPassword);

    /** Replaces an administrator-issued temporary password; unauthenticated but rate-limited by the caller. */
    void completeRequiredChange(String username, String temporaryPassword, String newPassword);

    /** Administrator reset: issues a random temporary password (returned once) and forces a change. */
    PasswordResetResponse resetPassword(Long adminId, Long targetUserId);
}
