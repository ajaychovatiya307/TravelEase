package com.travelease.admin.mapper;

import com.travelease.admin.dto.PolicyConfigRequest;
import com.travelease.admin.dto.PolicyConfigResponse;
import com.travelease.admin.entity.PolicyConfig;

import org.springframework.stereotype.Component;

/**
 * Entity ↔ DTO mapping for {@link PolicyConfig} (tasks.md G-04; api-contract.md §5.14).
 *
 * <p>Isolates mapping logic from Controller/Service per Constitution §6; no
 * internal-only fields ({@code deleted}, {@code version}, audit metadata) are copied
 * into {@link PolicyConfigResponse}.</p>
 */
@Component
public class PolicyConfigMapper {

    /**
     * Builds a new {@link PolicyConfig} from a create/update request (FR-ADM-02,
     * VAL-10). The caller is responsible for persisting it and for existence checks
     * on {@code categoryCode} and the base-currency match (NFR-05).
     *
     * @param request validated create/update payload
     * @return a new, unsaved {@code PolicyConfig}
     */
    public PolicyConfig toEntity(PolicyConfigRequest request) {
        PolicyConfig config = new PolicyConfig();
        config.setCategoryCode(request.categoryCode());
        config.setReceiptThresholdAmount(request.receiptThresholdAmount());
        config.setCurrency(request.currency());
        config.setActive(Boolean.TRUE.equals(request.active()));
        return config;
    }

    /**
     * Converts a persisted {@link PolicyConfig} to its API response shape
     * (api-contract.md §5.14).
     *
     * @param config persisted policy configuration
     * @return response DTO with no internal-only fields
     */
    public PolicyConfigResponse toResponse(PolicyConfig config) {
        return new PolicyConfigResponse(
                config.getId(),
                config.getCategoryCode(),
                config.getReceiptThresholdAmount(),
                config.getCurrency(),
                config.isActive());
    }
}
