package com.travelease.admin.dto;

import java.util.List;

import com.travelease.common.security.RoleType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create payload for an admin-managed account, including its initial credential. */
public record UserAccountCreateRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotNull @NotEmpty List<RoleType> roles,
        @NotBlank @Size(min = 8, max = 72) String password,
        Boolean active
) {
    public UserAccountCreateRequest {
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}
