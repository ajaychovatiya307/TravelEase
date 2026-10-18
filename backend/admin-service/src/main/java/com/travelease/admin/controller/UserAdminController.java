package com.travelease.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.admin.dto.UserAccountCreateRequest;
import com.travelease.admin.dto.UserAccountRequest;
import com.travelease.admin.dto.UserAccountResponse;
import com.travelease.admin.service.UserAdminService;

import jakarta.validation.Valid;

/**
 * User account administration REST controller (tasks.md I-05; api-contract.md §4.3, §6.1).
 *
 * <p>Thin controller delegating all business logic to {@link UserAdminService}.
 * Per Constitution §4: no business rules, no cross-service calls, no DB access.
 * All methods enforce `@PreAuthorize("hasRole('ADMIN')")` per api-contract.md §4.3.</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * Creates a new user account with initial role assignments (FR-ADM-01, AC-01).
     *
     * @param request validated create payload per api-contract.md §6.1
     * @return 201 Created with the persisted account response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAccountResponse> createUser(@Valid @RequestBody UserAccountCreateRequest request) {
        UserAccountResponse response = userAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a single user account (FR-ADM-01).
     *
     * @param id the user account ID
     * @return 200 OK with the account response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAccountResponse> getUser(@PathVariable Long id) {
        UserAccountResponse response = userAdminService.getUser(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists user accounts with pagination (FR-ADM-01, Constitution §5).
     *
     * @param pageable pagination and sort parameters
     * @return 200 OK with a paginated list of accounts
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserAccountResponse>> listUsers(Pageable pageable) {
        Page<UserAccountResponse> response = userAdminService.listUsers(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing user account's fields and role assignments (FR-ADM-01).
     *
     * @param id the user account ID
     * @param request validated update payload
     * @return 200 OK with the updated account response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAccountResponse> updateUser(@PathVariable Long id,
            @Valid @RequestBody UserAccountRequest request) {
        UserAccountResponse response = userAdminService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }
}
