package com.travelease.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.admin.dto.EmployeeManagerMappingRequest;
import com.travelease.admin.dto.EmployeeManagerMappingResponse;
import com.travelease.admin.service.EmployeeManagerMappingAdminService;

import jakarta.validation.Valid;

/**
 * Employee→Manager mapping administration REST controller (tasks.md I-05; api-contract.md §4.3, §6.2).
 *
 * <p>Thin controller delegating all business logic to {@link EmployeeManagerMappingAdminService}.
 * Per Constitution §4: no business rules, no cross-service calls, no DB access.
 * All methods enforce `@PreAuthorize("hasRole('ADMIN')")` per api-contract.md §4.3.</p>
 */
@RestController
@RequestMapping("/api/admin/employee-manager-mappings")
public class EmployeeManagerMappingController {

    private final EmployeeManagerMappingAdminService mappingAdminService;

    public EmployeeManagerMappingController(EmployeeManagerMappingAdminService mappingAdminService) {
        this.mappingAdminService = mappingAdminService;
    }

    /**
     * Creates or updates an Employee→Manager mapping (upsert semantics on employeeId).
     * Endpoint is used for both creation and updates, since employeeId is unique
     * (api-contract.md §4.3; FR-ADM-01, Q5).
     *
     * @param request validated create/update payload per api-contract.md §6.2
     * @return 201 Created if new, 200 OK if update; body contains the persisted mapping response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeManagerMappingResponse> createOrUpdateMapping(
            @Valid @RequestBody EmployeeManagerMappingRequest request) {
        EmployeeManagerMappingResponse response = mappingAdminService.createOrUpdateMapping(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists Employee→Manager mappings with pagination (FR-ADM-01, Constitution §5).
     *
     * @param pageable pagination and sort parameters
     * @return 200 OK with a paginated list of mappings
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<EmployeeManagerMappingResponse>> listMappings(Pageable pageable) {
        Page<EmployeeManagerMappingResponse> response = mappingAdminService.listMappings(pageable);
        return ResponseEntity.ok(response);
    }
}
