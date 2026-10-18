package com.travelease.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.travelease.admin.dto.EmployeeManagerMappingRequest;
import com.travelease.admin.dto.EmployeeManagerMappingResponse;

/**
 * Admin-facing management of Employee→Manager mappings (tasks.md H-08; spec.md
 * FR-ADM-01, FR-TR-05; api-contract.md §4.3).
 */
public interface EmployeeManagerMappingAdminService {

    /**
     * Creates the employee's mapping if none exists yet, or updates the existing one
     * otherwise - {@code employeeId} is unique, so this endpoint is an upsert
     * (api-contract.md §4.3 "Create/update Employee→Manager mapping").
     *
     * @param request validated create/update payload
     * @return the persisted mapping's response shape
     */
    EmployeeManagerMappingResponse createOrUpdateMapping(EmployeeManagerMappingRequest request);

    /**
     * Lists non-deleted mappings, paginated.
     *
     * @param pageable pagination and sort parameters
     * @return a page of mapping response shapes
     */
    Page<EmployeeManagerMappingResponse> listMappings(Pageable pageable);
}
