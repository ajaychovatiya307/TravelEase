package com.travelease.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** Replaces a temporary (admin-issued) password before the account can sign in again. */
public record CompletePasswordChangeRequest(@NotBlank String username, @NotBlank String temporaryPassword,
        @NotBlank String newPassword) {
}
