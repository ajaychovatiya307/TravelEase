package com.travelease.admin.controller;

import com.travelease.admin.service.TravelRequestPolicyService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, service-to-service travel-request policy endpoint (spec.md VAL-04,
 * FR-TR-02). Called synchronously by travel-request-service at submission
 * time with the caller's forwarded bearer token (authenticated, any role -
 * no {@code @PreAuthorize} restriction, mirroring {@link InternalRoutingController});
 * not part of the public api-contract.md surface.
 */
@RestController
@RequestMapping("/internal")
public class InternalPolicyController {

    private final TravelRequestPolicyService travelRequestPolicyService;

    public InternalPolicyController(TravelRequestPolicyService travelRequestPolicyService) {
        this.travelRequestPolicyService = travelRequestPolicyService;
    }

    /**
     * Resolves the travel-request document mandate for submission-time
     * validation (VAL-04, FR-TR-02). Falls back to the safe MVP default
     * ({@code documentsMandatory = false}) when no active policy row exists
     * (clarification Q7).
     *
     * @return the current document-mandate snapshot
     */
    @GetMapping("/travel-request-policy")
    public TravelRequestPolicySnapshot resolveDocumentMandate() {
        return new TravelRequestPolicySnapshot(travelRequestPolicyService.getActivePolicy().documentsMandatory());
    }

    /**
     * Minimal internal snapshot of the active travel-request policy - only the
     * mandate flag the Travel-Request Service needs to enforce VAL-04.
     *
     * @param documentsMandatory whether at least one supporting document is
     *                           required to submit a travel request
     */
    public record TravelRequestPolicySnapshot(boolean documentsMandatory) {
    }
}