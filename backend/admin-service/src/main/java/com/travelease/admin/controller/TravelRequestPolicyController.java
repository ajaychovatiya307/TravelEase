package com.travelease.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.admin.dto.TravelRequestPolicyRequest;
import com.travelease.admin.dto.TravelRequestPolicyResponse;
import com.travelease.admin.service.TravelRequestPolicyService;

import jakarta.validation.Valid;

/**
 * Travel-request policy administration REST controller (spec.md FR-ADM-02,
 * VAL-04, FR-TR-02).
 *
 * <p>Thin controller delegating all business logic to
 * {@link TravelRequestPolicyService}. Per Constitution §4: no business rules,
 * no cross-service calls, no DB access. GET allows ADMIN and FINANCE;
 * PUT (singleton upsert of the document mandate) is ADMIN-only. The mandate
 * flag set here is enforced by the Travel-Request Service at submission time
 * (VAL-04, FR-TR-02), not by admin-service.</p>
 */
@RestController
@RequestMapping("/api/admin/travel-request-policy")
public class TravelRequestPolicyController {

    private final TravelRequestPolicyService travelRequestPolicyService;

    public TravelRequestPolicyController(TravelRequestPolicyService travelRequestPolicyService) {
        this.travelRequestPolicyService = travelRequestPolicyService;
    }

    /**
     * Retrieves the active travel-request policy, or the safe MVP default
     * ({@code documentsMandatory = false}) when none exists (FR-ADM-02,
     * VAL-04, clarification Q7).
     *
     * @return 200 OK with the active policy response, or the default when absent
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<TravelRequestPolicyResponse> getActivePolicy() {
        TravelRequestPolicyResponse response = travelRequestPolicyService.getActivePolicy();
        return ResponseEntity.ok(response);
    }

    /**
     * Creates or updates (in place) the singleton travel-request policy,
     * setting the VAL-04 / FR-TR-02 document mandate (FR-ADM-02).
     *
     * @param request validated upsert payload
     * @return 200 OK with the persisted policy response
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TravelRequestPolicyResponse> upsertPolicy(
            @Valid @RequestBody TravelRequestPolicyRequest request) {
        TravelRequestPolicyResponse response = travelRequestPolicyService.upsertPolicy(request);
        return ResponseEntity.ok(response);
    }
}