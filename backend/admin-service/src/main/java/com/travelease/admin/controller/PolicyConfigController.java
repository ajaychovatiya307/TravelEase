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

import com.travelease.admin.dto.PolicyConfigRequest;
import com.travelease.admin.dto.PolicyConfigResponse;
import com.travelease.admin.service.PolicyConfigService;

import jakarta.validation.Valid;

/**
 * Policy configuration administration REST controller (tasks.md I-05; api-contract.md §4.3, §6.3).
 *
 * <p>Thin controller delegating all business logic to {@link PolicyConfigService}.
 * Per Constitution §4: no business rules, no cross-service calls, no DB access.
 * POST/PUT endpoints enforce `@PreAuthorize("hasRole('ADMIN')")`;
 * GET endpoints allow ADMIN and FINANCE per api-contract.md §4.3.</p>
 */
@RestController
@RequestMapping("/api/admin/policy-configs")
public class PolicyConfigController {

    private final PolicyConfigService policyConfigService;

    public PolicyConfigController(PolicyConfigService policyConfigService) {
        this.policyConfigService = policyConfigService;
    }

    /**
     * Creates a new policy configuration for an expense category (FR-ADM-02, Q7).
     *
     * @param request validated create payload per api-contract.md §6.3
     * @return 201 Created with the persisted policy config response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PolicyConfigResponse> createPolicyConfig(@Valid @RequestBody PolicyConfigRequest request) {
        PolicyConfigResponse response = policyConfigService.createPolicyConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a single policy configuration (FR-ADM-02).
     *
     * @param id the policy configuration ID
     * @return 200 OK with the configuration response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<PolicyConfigResponse> getPolicyConfig(@PathVariable Long id) {
        PolicyConfigResponse response = policyConfigService.getPolicyConfig(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists policy configurations with pagination (FR-ADM-02, Constitution §5).
     * Accessible to ADMIN and FINANCE roles per api-contract.md §4.3.
     *
     * @param pageable pagination and sort parameters
     * @return 200 OK with a paginated list of configurations
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'EMPLOYEE', 'MANAGER')")
    public ResponseEntity<Page<PolicyConfigResponse>> listPolicyConfigs(Pageable pageable) {
        Page<PolicyConfigResponse> response = policyConfigService.listPolicyConfigs(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing policy configuration (FR-ADM-02).
     *
     * @param id the policy configuration ID
     * @param request validated update payload
     * @return 200 OK with the updated configuration response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PolicyConfigResponse> updatePolicyConfig(@PathVariable Long id,
            @Valid @RequestBody PolicyConfigRequest request) {
        PolicyConfigResponse response = policyConfigService.updatePolicyConfig(id, request);
        return ResponseEntity.ok(response);
    }
}
