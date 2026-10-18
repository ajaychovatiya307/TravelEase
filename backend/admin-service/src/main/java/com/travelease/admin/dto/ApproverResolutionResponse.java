package com.travelease.admin.dto;

/**
 * Internal response shape for approver resolution (tasks.md H-01; spec.md FR-TR-05).
 * Consumed synchronously by travel-request-service, not part of the public
 * api-contract.md surface (clarification Q20).
 *
 * @param approverId logical UserAccount reference for the resolved approver
 */
public record ApproverResolutionResponse(Long approverId) {
}
