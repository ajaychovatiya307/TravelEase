package com.travelease.travelrequest.service.impl;

/**
 * REST client for calling Admin Service's internal travel-request policy endpoint.
 *
 * <p>Injected into TravelRequestServiceImpl to enforce the supporting-document
 * mandate synchronously during submission (VAL-04, FR-TR-02; FR-ADM-02).
 *
 * <p>This interface defines the contract; the implementation uses RestTemplate
 * to make the actual HTTP call.</p>
 */
public interface TravelRequestPolicyClient {

    /**
     * Reads the admin-managed travel-request document mandate (VAL-04, FR-TR-02)
     * synchronously from Admin Service's internal endpoint.
     *
     * <p>The mandate flag is admin-managed (FR-ADM-02) and defaults to
     * {@code false} for the MVP (clarification Q7).</p>
     *
     * @return {@code true} when at least one supporting document is required for
     *         travel request submission, {@code false} otherwise
     * @throws Exception if the Admin Service is unreachable
     */
    boolean documentsMandatory();
}