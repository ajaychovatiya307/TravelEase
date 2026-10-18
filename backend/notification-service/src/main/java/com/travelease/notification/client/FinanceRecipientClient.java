package com.travelease.notification.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.travelease.common.security.internal.InternalServiceClientInterceptor;
import com.travelease.common.security.internal.InternalServiceTokens;

/** Looks up current Finance recipients through admin-service's internal API. */
@Component
public class FinanceRecipientClient {

    private final RestTemplate restTemplate;
    private final String financeRecipientsUrl;

    public FinanceRecipientClient(
            @Value("${admin-service.finance-recipients-url:http://localhost:8082/internal/recipients/finance}")
            String financeRecipientsUrl,
            @Value("${security.internal.secret:}") String internalSecret,
            @Value("${spring.application.name}") String serviceName) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restTemplate = new RestTemplate(requestFactory);
        // admin-service /internal/** accepts only the internal service credential (never a user token)
        this.restTemplate.getInterceptors().add(
                new InternalServiceClientInterceptor(new InternalServiceTokens(internalSecret, serviceName)));
        this.financeRecipientsUrl = financeRecipientsUrl;
    }

    public List<Long> findActiveFinanceUserIds() {
        HttpHeaders headers = new HttpHeaders();

        List<Long> recipients = restTemplate.exchange(
                financeRecipientsUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Long>>() {
                }).getBody();
        if (recipients == null) {
            throw new IllegalStateException("Admin service returned no Finance recipient list");
        }
        return recipients;
    }
}
