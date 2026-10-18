package com.travelease.expense.client;

import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.expense.client.dto.TravelRequestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * REST client for calling the Travel Request Service from Expense Service (tasks.md H-05).
 * Handles cross-service synchronous validation per VAL-09 and Constitution §2
 * (no cross-service DB access; REST API only).
 *
 * <p>Used during expense claim submission/edit to confirm:
 * <ul>
 *   <li>The referenced travel request exists and is APPROVED (VAL-09)</li>
 *   <li>The employee owns the travel request (ownership check, VAL-09)</li>
 * </ul>
 *
 * <p>Per Constitution §4 Service layer isolation, HTTP transport concerns (RestTemplate)
 * are confined to this client; the Service layer receives only typed DTOs.</p>
 */
@Component
public class TravelRequestClient {

    private static final Logger logger = LoggerFactory.getLogger(TravelRequestClient.class);

    private final RestTemplate restTemplate;

    /** Base URL of the Travel Request Service; real service listens on 8081, not 8001. */
    private final String travelRequestBaseUrl;

    public TravelRequestClient(RestTemplate restTemplate,
            @Value("${travel-request-service.base-url:http://localhost:8081/api}") String travelRequestBaseUrl) {
        this.restTemplate = restTemplate;
        this.travelRequestBaseUrl = travelRequestBaseUrl;
    }

    /**
     * Fetches a travel request by ID from the Travel Request Service.
     *
     * @param travelRequestId the ID of the travel request (as string per api-contract §3.1)
     * @return the travel request response with full details
     * @throws ResourceNotFoundException if the travel request does not exist
     * @throws RestClientException if the call fails (network error, service down, etc.)
     */
    public TravelRequestResponse getTravelRequest(String travelRequestId)
            throws ResourceNotFoundException, RestClientException {

        try {
            String url = travelRequestBaseUrl + "/travel-requests/" + travelRequestId;
            logger.debug("Calling Travel Request Service: GET {}", url);

            // forward the caller's bearer token - the travel-request-service endpoint requires authentication
            HttpEntity<Void> request = new HttpEntity<>(forwardedAuthHeaders());
            TravelRequestResponse response = restTemplate.exchange(
                    url, HttpMethod.GET, request, TravelRequestResponse.class).getBody();

            if (response == null) {
                throw new ResourceNotFoundException("travelRequestId",
                        "Travel request not found: " + travelRequestId);
            }

            logger.debug("Travel request {} retrieved: status={}", travelRequestId, response.status());
            return response;

        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("travelRequestId",
                    "Travel request not found: " + travelRequestId);
        }
    }

    /**
     * Validates that a travel request is approved and owned by the given employee.
     * Called during expense claim submission (VAL-09).
     *
     * @param travelRequestId the ID of the travel request
     * @param employeeId the ID of the employee claiming the expense
     * @throws ResourceNotFoundException if the travel request does not exist
     * @throws com.travelease.common.exception.PolicyViolationException
     *         if the travel request is not APPROVED or not owned by the employee
     */
    public void validateTravelRequestApprovedAndOwned(String travelRequestId, String employeeId) {
        TravelRequestResponse travelRequest = getTravelRequest(travelRequestId);

        // Validate status (a withdrawn trip gets an explicit message)
        if ("CANCELLED".equals(travelRequest.status())) {
            throw new com.travelease.common.exception.PolicyViolationException(
                    "Expense claims cannot be filed against a cancelled travel request");
        }
        if (!"APPROVED".equals(travelRequest.status())) {
            throw new com.travelease.common.exception.PolicyViolationException(
                    "Travel request must be in APPROVED status to submit an expense claim");
        }

        // Validate ownership
        if (!travelRequest.employeeId().equals(employeeId)) {
            throw new com.travelease.common.exception.ForbiddenActionException(
                    "Employee does not own the referenced travel request");
        }

        logger.debug("Travel request {} validated: approved and owned by employee {}", 
                travelRequestId, employeeId);
    }

    /**
     * Gets the date range for a travel request (used to validate expense date).
     *
     * @param travelRequestId the ID of the travel request
     * @return object containing startDate and endDate
     * @throws ResourceNotFoundException if the travel request does not exist
     */
    public TravelRequestDateRange getTravelRequestDateRange(String travelRequestId) {
        TravelRequestResponse travelRequest = getTravelRequest(travelRequestId);
        return new TravelRequestDateRange(travelRequest.startDate(), travelRequest.endDate());
    }

    /**
     * Simple record to hold travel request date range.
     */
    public static record TravelRequestDateRange(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
    }

    /** Copies the current request's {@code Authorization} header onto the downstream call. */
    private HttpHeaders forwardedAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String authHeader = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
        }
        return headers;
    }
}
