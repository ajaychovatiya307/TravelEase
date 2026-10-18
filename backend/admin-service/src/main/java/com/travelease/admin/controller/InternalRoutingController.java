package com.travelease.admin.controller;

import com.travelease.admin.dto.ApproverResolutionResponse;
import com.travelease.admin.service.EmployeeManagerRoutingService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, service-to-service routing endpoint (tasks.md H-01; spec.md FR-TR-05,
 * clarification Q20). Called synchronously by travel-request-service at submission
 * time; not part of the public api-contract.md surface.
 */
@RestController
@RequestMapping("/internal/employees")
public class InternalRoutingController {

    private final EmployeeManagerRoutingService routingService;

    public InternalRoutingController(EmployeeManagerRoutingService routingService) {
        this.routingService = routingService;
    }

    /**
     * Resolves the approver for the given employee (FR-TR-05).
     *
     * @param employeeId logical UserAccount reference for the requesting employee
     * @return the resolved approver's logical UserAccount reference
     */
    /** Can this user be (re)assigned approvals? Used by travel-request-service reassignment (#11). */
    @GetMapping("/{userId}/approver-eligibility")
    public EmployeeManagerRoutingService.ApproverEligibility approverEligibility(@PathVariable Long userId) {
        return routingService.checkApproverEligibility(userId);
    }

    @GetMapping("/{employeeId}/approver")
    public ApproverResolutionResponse resolveApprover(@PathVariable Long employeeId) {
        return new ApproverResolutionResponse(routingService.resolveApprover(employeeId));
    }
}
