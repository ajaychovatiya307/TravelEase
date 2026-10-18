package com.travelease.common.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelease.common.security.internal.InternalServiceClientInterceptor;
import com.travelease.common.security.internal.InternalServiceTokens;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Transactional outbox for an event-producing service. Deliberately NOT annotated with
 * {@code @Configuration}: the common package is component-scanned by every service, but only producers
 * (travel-request, expense, admin) opt in with {@code @Import(OutboxConfiguration.class)} plus JPA
 * scanning of {@code com.travelease.common.outbox}.
 *
 * <p>Properties ({@code travelease.outbox.*}): {@code destinations.<name>=<url>} (required, at least one),
 * {@code dispatch-enabled} (default true), {@code batch-size} (50), {@code max-attempts} (10),
 * {@code base-backoff} (5s), {@code max-backoff} (15m), {@code poll-interval-ms} (5000).
 * Delivery authenticates with {@code security.internal.secret}.</p>
 */
@EnableScheduling
public class OutboxConfiguration {

    static Map<String, String> destinations(Environment environment) {
        Map<String, String> configured = Binder.get(environment)
                .bind("travelease.outbox.destinations", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        if (configured.isEmpty()) {
            throw new IllegalStateException("travelease.outbox.destinations must configure at least one destination");
        }
        return new LinkedHashMap<>(configured);
    }

    @Bean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OutboxEventWriter outboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper,
            Environment environment, Clock outboxClock) {
        return new OutboxEventWriter(repository, objectMapper, destinations(environment), outboxClock);
    }

    @Bean
    public OutboxDispatcher outboxDispatcher(OutboxEventRepository repository, Environment environment,
            PlatformTransactionManager transactionManager, Clock outboxClock) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        InternalServiceTokens tokens = new InternalServiceTokens(
                environment.getProperty("security.internal.secret"),
                environment.getProperty("spring.application.name"));
        restTemplate.getInterceptors().add(new InternalServiceClientInterceptor(tokens));

        OutboxDispatcher.OutboxSettings settings = new OutboxDispatcher.OutboxSettings(
                environment.getProperty("travelease.outbox.dispatch-enabled", Boolean.class, true),
                environment.getProperty("travelease.outbox.batch-size", Integer.class, 50),
                environment.getProperty("travelease.outbox.max-attempts", Integer.class, 10),
                environment.getProperty("travelease.outbox.base-backoff", Duration.class, Duration.ofSeconds(5)),
                environment.getProperty("travelease.outbox.max-backoff", Duration.class, Duration.ofMinutes(15)));
        return new OutboxDispatcher(repository, restTemplate, new TransactionTemplate(transactionManager),
                destinations(environment), settings, outboxClock);
    }
}
