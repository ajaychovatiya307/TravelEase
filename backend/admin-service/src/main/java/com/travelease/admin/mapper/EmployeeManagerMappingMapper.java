package com.travelease.admin.mapper;

import com.travelease.admin.dto.EmployeeManagerMappingRequest;
import com.travelease.admin.dto.EmployeeManagerMappingResponse;
import com.travelease.admin.entity.EmployeeManagerMapping;

import org.springframework.stereotype.Component;

/**
 * Entity ↔ DTO mapping for {@link EmployeeManagerMapping} (tasks.md G-04; api-contract.md §5.13).
 *
 * <p>Isolates mapping logic from Controller/Service per Constitution §6; no
 * internal-only fields ({@code deleted}, {@code version}, audit metadata) are copied
 * into {@link EmployeeManagerMappingResponse}.</p>
 */
@Component
public class EmployeeManagerMappingMapper {

    /**
     * Builds a new {@link EmployeeManagerMapping} from a create/update request
     * (FR-ADM-01, FR-TR-05). The caller is responsible for persisting it and for
     * existence/uniqueness checks on {@code employeeId}/{@code managerId}.
     *
     * @param request validated create/update payload
     * @return a new, unsaved {@code EmployeeManagerMapping}
     */
    public EmployeeManagerMapping toEntity(EmployeeManagerMappingRequest request) {
        EmployeeManagerMapping mapping = new EmployeeManagerMapping();
        mapping.setEmployeeId(request.employeeId());
        mapping.setManagerId(request.managerId());
        mapping.setEffectiveFrom(request.effectiveFrom());
        return mapping;
    }

    /**
     * Converts a persisted {@link EmployeeManagerMapping} to its API response shape
     * (api-contract.md §5.13).
     *
     * @param mapping persisted mapping
     * @return response DTO with no internal-only fields
     */
    public EmployeeManagerMappingResponse toResponse(EmployeeManagerMapping mapping) {
        return new EmployeeManagerMappingResponse(
                mapping.getId(),
                mapping.getEmployeeId(),
                mapping.getManagerId(),
                mapping.getEffectiveFrom());
    }
}
