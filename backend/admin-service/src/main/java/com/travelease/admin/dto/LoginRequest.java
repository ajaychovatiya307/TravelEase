package com.travelease.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** Credentials for username/password authentication; username is the account email. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
