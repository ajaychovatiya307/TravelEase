package com.travelease.notification.client;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.travelease.common.security.internal.InternalServiceClientInterceptor;
import com.travelease.common.security.internal.InternalServiceTokens;

/** Looks up a recipient's e-mail address in admin-service (internal credential) for e-mail delivery (#17). */
@Component
public class RecipientDirectoryClient {

    private final RestTemplate restTemplate;
    private final String adminInternalBaseUrl;

    public RecipientDirectoryClient(
            @Value("${admin-service.internal-base-url:http://localhost:8082}") String adminInternalBaseUrl,
            @Value("${security.internal.secret:}") String internalSecret,
            @Value("${spring.application.name}") String serviceName) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(requestFactory);
        this.restTemplate.getInterceptors().add(
                new InternalServiceClientInterceptor(new InternalServiceTokens(internalSecret, serviceName)));
        this.adminInternalBaseUrl = adminInternalBaseUrl;
    }

    /** @return the address of an active user, or empty when the user has none / is inactive or unknown */
    public Optional<String> activeEmailOf(Long userId) {
        UserContact contact = restTemplate.getForObject(
                adminInternalBaseUrl + "/internal/users/" + userId + "/contact", UserContact.class);
        if (contact == null || !contact.active() || contact.email() == null || contact.email().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(contact.email());
    }

    record UserContact(Long userId, String email, boolean active) {
    }
}
