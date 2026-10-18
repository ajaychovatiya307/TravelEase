package com.travelease.travelrequest.client.dto;

/**
 * Mirrors admin-service's internal {@code ApproverResolutionResponse} (tasks.md H-01).
 *
 * @param approverId logical UserAccount reference for the resolved approver
 */
public record ApproverResolutionResponse(Long approverId) {
}
