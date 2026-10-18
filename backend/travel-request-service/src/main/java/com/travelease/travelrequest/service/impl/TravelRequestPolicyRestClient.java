package com.travelease.travelrequest.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.travelease.travelrequest.client.dto.TravelRequestPolicySnapshot;

/**
 * REST implementation of {@link TravelRequestPolicyClient} (spec.md VAL-04, FR-TR-02),
 * calling admin-service's internal, non-public policy endpoint synchronously.
 */
@Component
public class TravelRequestPolicyRestClient implements TravelRequestPolicyClient {

    private static final Logger logger = LoggerFactory.getLogger(TravelRequestPolicyRestClient.class);

    private final RestTemplate restTemplate;

    /** Base URL of the Admin Service; real admin-service listens on 8082, not 8003. */
    private final String adminServiceInternalBaseUrl;

    public TravelRequestPolicyRestClient(RestTemplate restTemplate,
            @Value("${admin-service.internal-base-url:http://localhost:8082}") String adminServiceInternalBaseUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceInternalBaseUrl = adminServiceInternalBaseUrl;
    }

    @Override
    public boolean documentsMandatory() {
        String url = adminServiceInternalBaseUrl + "/internal/travel-request-policy";
        logger.debug("Calling Admin Service: GET {}", url);

        try {
            // authenticated as this service by the RestTemplate's internal-credential interceptor
            HttpEntity<Void> request = new HttpEntity<>(new HttpHeaders());
            TravelRequestPolicySnapshot response = restTemplate.exchange(
                    url, HttpMethod.GET, request, TravelRequestPolicySnapshot.class).getBody();
            // Safe default (clarification Q7): no documents mandated if the snapshot is absent
            return response != null && response.documentsMandatory();
        } catch (RestClientException ex) {
            throw new RestClientException(
                    "Failed to read travel-request policy mandate via " + url, ex);
        }
    }

}