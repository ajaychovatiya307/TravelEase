package com.travelease.travelrequest.client.dto;

/**
 * Mirrors admin-service's internal {@code TravelRequestPolicySnapshot}
 * (spec.md VAL-04, FR-TR-02).
 *
 * @param documentsMandatory whether at least one supporting document is
 *                           required to submit a travel request
 */
public record TravelRequestPolicySnapshot(boolean documentsMandatory) {
}