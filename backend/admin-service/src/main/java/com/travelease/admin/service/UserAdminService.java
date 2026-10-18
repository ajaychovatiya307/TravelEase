package com.travelease.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.travelease.admin.dto.UserAccountRequest;
import com.travelease.admin.dto.UserAccountCreateRequest;
import com.travelease.admin.dto.UserAccountResponse;

/**
 * Admin-facing CRUD operations for user accounts and their role assignments
 * (tasks.md H-08; spec.md FR-ADM-01; api-contract.md §4.3).
 */
public interface UserAdminService {

    /**
     * Creates a new user account with its initial role assignments.
     *
     * @param request validated create payload
     * @return the persisted account's response shape
     */
    UserAccountResponse createUser(UserAccountCreateRequest request);

    /**
     * Updates an existing user account's fields and replaces its role assignments.
     *
     * @param id      id of the account to update
     * @param request validated update payload
     * @return the updated account's response shape
     */
    UserAccountResponse updateUser(Long id, UserAccountRequest request);

    /**
     * Fetches a single user account.
     *
     * @param id id of the account
     * @return the account's response shape
     */
    UserAccountResponse getUser(Long id);

    /**
     * Lists non-deleted user accounts, paginated.
     *
     * @param pageable pagination and sort parameters
     * @return a page of user account response shapes
     */
    Page<UserAccountResponse> listUsers(Pageable pageable);

    /** E-mail and active flag for notification delivery (#17); unknown users yield an inactive contact. */
    UserContact getContact(Long userId);

    record UserContact(Long userId, String email, boolean active) {
    }
}
