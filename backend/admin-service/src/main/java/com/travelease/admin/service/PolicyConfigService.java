package com.travelease.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.travelease.admin.dto.PolicyConfigRequest;
import com.travelease.admin.dto.PolicyConfigResponse;

/**
 * Admin-facing management of per-category policy thresholds (tasks.md H-09;
 * spec.md FR-ADM-02; api-contract.md §4.3).
 */
public interface PolicyConfigService {

    /**
     * Creates a new policy configuration for a category.
     *
     * @param request validated create payload
     * @return the persisted configuration's response shape
     */
    PolicyConfigResponse createPolicyConfig(PolicyConfigRequest request);

    /**
     * Updates an existing policy configuration.
     *
     * @param id      id of the configuration to update
     * @param request validated update payload
     * @return the updated configuration's response shape
     */
    PolicyConfigResponse updatePolicyConfig(Long id, PolicyConfigRequest request);

    /**
     * Fetches a single policy configuration.
     *
     * @param id id of the configuration
     * @return the configuration's response shape
     */
    PolicyConfigResponse getPolicyConfig(Long id);

    /**
     * Lists non-deleted policy configurations, paginated.
     *
     * @param pageable pagination and sort parameters
     * @return a page of policy configuration response shapes
     */
    Page<PolicyConfigResponse> listPolicyConfigs(Pageable pageable);
}
