package com.travelease.admin.service;

import java.util.List;

/**
 * Resolves the approver for a submitted travel request (tasks.md H-01; spec.md
 * FR-TR-05, BR-06). Exposed internally for travel-request-service to call
 * synchronously at submission time (clarification Q20).
 */
public interface EmployeeManagerRoutingService {

    /**
     * Resolves the approver for the given employee: their direct manager from the
     * static Employee→Manager mapping (clarification Q5). If that manager mapping
     * has no further manager (top of hierarchy - {@code managerId} is null), the
     * approver falls back to a Finance user (clarification Q3, FR-TR-05, spec.md EC-09).
     *
     * @param employeeId logical UserAccount reference for the requesting employee
     * @return logical UserAccount reference for the resolved approver
     */
    Long resolveApprover(Long employeeId);

    /** Returns active, non-deleted Finance user IDs for downstream notification routing. */
    List<Long> findActiveFinanceUserIds();

    /** Whether a user may be assigned approvals: exists, active, holds MANAGER or FINANCE (#11). */
    ApproverEligibility checkApproverEligibility(Long userId);

    /** @param reason why the user is not eligible; null when eligible */
    record ApproverEligibility(Long userId, boolean eligible, String reason) {
    }
}
