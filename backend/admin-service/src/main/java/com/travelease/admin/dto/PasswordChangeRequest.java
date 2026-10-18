package com.travelease.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** Self-service password change (authenticated): the current password must be re-entered. */
public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
}
