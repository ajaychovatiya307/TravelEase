package com.travelease.travelrequest.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.travelease.common.exception.PolicyViolationException;
import org.springframework.web.client.RestTemplate;

import com.travelease.travelrequest.client.dto.ApproverResolutionResponse;

/**
 * REST implementation of {@link EmployeeManagerRoutingServiceClient} (tasks.md H-01, H-02),
 * calling admin-service's internal, non-public routing endpoint synchronously (clarification Q20).
 */
@Component
public class EmployeeManagerRoutingRestClient implements EmployeeManagerRoutingServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeManagerRoutingRestClient.class);

    private final RestTemplate restTemplate;

    /** Base URL of the Admin Service; real admin-service listens on 8082, not 8003. */
    private final String adminServiceInternalBaseUrl;

    public EmployeeManagerRoutingRestClient(RestTemplate restTemplate,
            @Value("${admin-service.internal-base-url:http://localhost:8082}") String adminServiceInternalBaseUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceInternalBaseUrl = adminServiceInternalBaseUrl;
    }

    @Override
    public Long resolveApprover(Long employeeId) {
        String url = adminServiceInternalBaseUrl + "/internal/employees/" + employeeId + "/approver";
        logger.debug("Calling Admin Service: GET {}", url);

        try {
            // authenticated as this service by the RestTemplate's internal-credential interceptor
            HttpEntity<Void> request = new HttpEntity<>(new HttpHeaders());
            ApproverResolutionResponse response = restTemplate.exchange(
                    url, HttpMethod.GET, request, ApproverResolutionResponse.class).getBody();
            return response == null ? null : response.approverId();
        } catch (HttpClientErrorException ex) {
            // admin-service refused (no reporting line, inactive approver, no active Finance user):
            // surface its explanation as a business-rule violation instead of a 500 (#11)
            throw new PolicyViolationException("approver", adminMessage(ex,
                    "No active approver is configured for you. Ask an administrator to update your reporting line."));
        } catch (RestClientException ex) {
            throw new RestClientException(
                    "Failed to resolve approver for employeeId=" + employeeId + " via " + url, ex);
        }
    }

    @Override
    public String approverIneligibilityReason(Long userId) {
        String url = adminServiceInternalBaseUrl + "/internal/employees/" + userId + "/approver-eligibility";
        ApproverEligibilityResponse response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), ApproverEligibilityResponse.class).getBody();
        if (response == null) {
            return "Approver eligibility could not be confirmed";
        }
        return response.eligible() ? null : (response.reason() == null ? "Not eligible" : response.reason());
    }

    /** Admin-service eligibility answer (#11). */
    record ApproverEligibilityResponse(Long userId, boolean eligible, String reason) {
    }

    private static String adminMessage(HttpClientErrorException ex, String fallback) {
        try {
            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(ex.getResponseBodyAsString());
            com.fasterxml.jackson.databind.JsonNode fieldErrors = body.path("fieldErrors");
            if (fieldErrors.isArray() && !fieldErrors.isEmpty() && fieldErrors.get(0).hasNonNull("message")) {
                return fieldErrors.get(0).get("message").asText();
            }
            return body.hasNonNull("message") ? body.get("message").asText() : fallback;
        } catch (Exception parseFailure) {
            return fallback;
        }
    }

}
