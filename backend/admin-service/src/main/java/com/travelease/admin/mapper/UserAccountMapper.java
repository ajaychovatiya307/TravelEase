package com.travelease.admin.mapper;

import java.util.List;

import com.travelease.admin.dto.UserAccountCreateRequest;
import com.travelease.admin.dto.UserAccountRequest;
import com.travelease.admin.dto.UserAccountResponse;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.common.security.RoleType;

import org.springframework.stereotype.Component;

/**
 * Entity ↔ DTO mapping for {@link UserAccount} (tasks.md G-04; api-contract.md §5.12).
 *
 * <p>Isolates mapping logic from Controller/Service per Constitution §6; no
 * internal-only fields ({@code deleted}, {@code version}, audit metadata) are copied
 * into {@link UserAccountResponse}.</p>
 */
@Component
public class UserAccountMapper {

    /**
     * Builds a new {@link UserAccount} (with its {@link UserRole} assignments) from a
     * create/update request (FR-ADM-01). The caller is responsible for persisting the
     * aggregate and for any uniqueness checks (email, duplicate role).
     *
     * @param request validated create/update payload
     * @return a new, unsaved {@code UserAccount} aggregate
     */
    public UserAccount toEntity(UserAccountRequest request) {
        return toEntity(request.fullName(), request.email(), request.roles(), request.active());
    }

    /** Builds a new account from create-specific profile fields. */
    public UserAccount toEntity(UserAccountCreateRequest request) {
        return toEntity(request.fullName(), request.email(), request.roles(), request.active());
    }

    private UserAccount toEntity(
            String fullName,
            String email,
            List<RoleType> roles,
            Boolean active) {
        UserAccount account = new UserAccount();
        account.setFullName(fullName);
        account.setEmail(email);
        account.setActive(Boolean.TRUE.equals(active));
        for (RoleType role : roles) {
            UserRole userRole = new UserRole();
            userRole.setRole(role);
            account.addRole(userRole);
        }
        return account;
    }

    /**
     * Converts a persisted {@link UserAccount} to its API response shape
     * (api-contract.md §5.12).
     *
     * @param account persisted user account
     * @return response DTO with no internal-only fields
     */
    public UserAccountResponse toResponse(UserAccount account) {
        List<RoleType> roles = account.getRoles().stream()
                .map(UserRole::getRole)
                .toList();
        return new UserAccountResponse(
                account.getId(),
                account.getFullName(),
                account.getEmail(),
                roles,
                account.isActive());
    }
}
