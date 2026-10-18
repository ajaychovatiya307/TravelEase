package com.travelease.travelrequest.service.impl;

/**
 * REST client for calling Admin Service's EmployeeManagerRoutingService endpoint.
 * 
 * <p>Injected into TravelRequestServiceImpl to resolve travel request approvers
 * synchronously during submission (H-01, H-02; clarification Q20, FR-TR-05).
 * 
 * <p>This interface defines the contract; implementation would use RestTemplate,
 * WebClient, or a generated client (e.g., OpenFeign) to make the actual HTTP call.</p>
 */
public interface EmployeeManagerRoutingServiceClient {

    /**
     * Calls Admin Service to resolve the approver for the given employee.
     *
     * <p>Resolves: direct manager → top manager's manager → Finance fallback.
     * See FR-TR-05, clarification Q3, spec.md EC-09.</p>
     *
     * @param employeeId the requesting employee (logical UserAccount ID)
     * @return logical UserAccount ID of the resolved approver
     * @throws Exception if the Admin Service is unreachable
     */
    Long resolveApprover(Long employeeId);

    /**
     * Asks admin-service whether a user can be assigned approvals (exists, active, MANAGER or FINANCE).
     *
     * @return null when eligible, otherwise the reason
     */
    String approverIneligibilityReason(Long userId);
}
