package com.travelease.travelrequest.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.travelease.common.security.internal.InternalServiceClientInterceptor;
import com.travelease.common.security.internal.InternalServiceTokens;

/**
 * Shared {@link RestTemplate} for the calls to admin-service's {@code /internal/**} endpoints (approver
 * routing, travel-request policy). They authenticate as this service with the internal credential,
 * never with the end user's token.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
            @Value("${security.internal.secret:}") String internalSecret,
            @Value("${spring.application.name}") String serviceName) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalInterceptors(new InternalServiceClientInterceptor(
                        new InternalServiceTokens(internalSecret, serviceName)))
                .build();
    }
}
