package com.travelease.expense.client;

import java.math.BigDecimal;
import java.util.List;

import com.travelease.common.dto.PagedResponse;
import com.travelease.expense.client.dto.PolicyConfigResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * REST client for calling the Admin Service's policy-config list endpoint from the
 * Expense Service (tasks.md J-04). Resolves the active receipt threshold for a
 * category, consumed by {@code ExpenseClaimPolicyValidator#validateReceiptRequirement}
 * (VAL-10, FR-EXP-05).
 *
 * <p>Per Constitution §4 Service layer isolation, HTTP transport concerns
 * (RestTemplate) are confined to this client; the Service layer receives only a
 * resolved {@link BigDecimal} threshold (or {@code null} if unconfigured).</p>
 */
@Component
public class PolicyConfigClient {

    private static final Logger logger = LoggerFactory.getLogger(PolicyConfigClient.class);

    private final RestTemplate restTemplate;
    private final String adminServiceBaseUrl;

    // real admin-service listens on 8082, not 8003.
    public PolicyConfigClient(RestTemplate restTemplate,
            @Value("${admin-service.base-url:http://localhost:8082/api}") String adminServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceBaseUrl = adminServiceBaseUrl;
    }

    /**
     * Resolves the active receipt threshold configured for a category.
     *
     * @param categoryCode category code to look up (VAL-10, FR-EXP-05)
     * @return the active threshold amount, or {@code null} if no active policy is configured
     * @throws RestClientException if the call fails (network error, service down, etc.)
     */
    public BigDecimal getActiveReceiptThreshold(String categoryCode) {
        String url = adminServiceBaseUrl + "/admin/policy-configs?size=100";
        logger.debug("Calling Admin Service: GET {}", url);

        // forward the caller's bearer token - the admin-service endpoint requires authentication
        HttpEntity<Void> request = new HttpEntity<>(forwardedAuthHeaders());
        ResponseEntity<PagedResponse<PolicyConfigResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, request, new ParameterizedTypeReference<PagedResponse<PolicyConfigResponse>>() {
                });

        PagedResponse<PolicyConfigResponse> body = response.getBody();
        List<PolicyConfigResponse> configs = body == null ? List.of() : body.content();
        return configs.stream()
                .filter(config -> categoryCode.equals(config.categoryCode()) && Boolean.TRUE.equals(config.active()))
                .map(PolicyConfigResponse::receiptThresholdAmount)
                .findFirst()
                .orElse(null);
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
