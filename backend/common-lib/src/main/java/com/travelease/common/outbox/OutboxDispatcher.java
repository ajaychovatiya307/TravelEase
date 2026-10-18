package com.travelease.common.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Delivers outbox events to their destinations with bounded, exponentially backed-off retries.
 *
 * <ul>
 *   <li>2xx: marked DELIVERED (consumers are idempotent on {@code eventId}, so an occasional duplicate
 *       delivery — e.g. a crash after the POST but before the status update — is harmless).</li>
 *   <li>4xx other than 408/429: the destination rejected the event as invalid; retrying cannot help,
 *       so it is marked FAILED immediately.</li>
 *   <li>5xx, 408, 429, timeouts, connection errors: retried later until {@code maxAttempts}, then FAILED.</li>
 * </ul>
 * Logs carry the event id, destination and HTTP status only — never the payload.
 */
public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository repository;
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactions;
    private final Map<String, String> destinations;
    private final OutboxSettings settings;
    private final Clock clock;

    public OutboxDispatcher(OutboxEventRepository repository, RestTemplate restTemplate,
            TransactionTemplate transactions, Map<String, String> destinations, OutboxSettings settings, Clock clock) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.transactions = transactions;
        this.destinations = destinations;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${travelease.outbox.poll-interval-ms:5000}",
            initialDelayString = "${travelease.outbox.initial-delay-ms:10000}")
    public void scheduledDispatch() {
        if (settings.dispatchEnabled()) {
            dispatchDue();
        }
    }

    /** Attempts every due event once. @return number of events delivered in this pass. */
    public int dispatchDue() {
        List<OutboxEvent> due = repository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                OutboxStatus.PENDING, clock.instant(), PageRequest.of(0, settings.batchSize()));
        int delivered = 0;
        for (OutboxEvent event : due) {
            if (deliver(event)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean deliver(OutboxEvent event) {
        String url = destinations.get(event.getDestination());
        if (url == null || url.isBlank()) {
            update(event, e -> e.markFailed("No URL configured for destination " + e.getDestination()));
            LOGGER.error("Outbox event {} has no configured destination '{}'", event.getEventId(), event.getDestination());
            return false;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(event.getPayload(), headers), Void.class);
            update(event, e -> e.markDelivered(clock.instant()));
            return true;
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.is4xxClientError() && status.value() != 408 && status.value() != 429) {
                update(event, e -> e.markFailed("Rejected by destination with HTTP " + status.value()));
                LOGGER.error("Outbox event {} permanently rejected by {} (HTTP {})", event.getEventId(),
                        event.getDestination(), status.value());
                return false;
            }
            scheduleRetry(event, "HTTP " + status.value());
            return false;
        } catch (RestClientException ex) {
            scheduleRetry(event, ex.getClass().getSimpleName());
            return false;
        }
    }

    private void scheduleRetry(OutboxEvent event, String reason) {
        int nextAttempt = event.getAttempts() + 1;
        if (nextAttempt >= settings.maxAttempts()) {
            update(event, e -> e.markFailed("Gave up after " + nextAttempt + " attempts: " + reason));
            LOGGER.error("Outbox event {} to {} FAILED after {} attempts ({})", event.getEventId(),
                    event.getDestination(), nextAttempt, reason);
            return;
        }
        Duration backoff = backoffFor(nextAttempt);
        update(event, e -> e.markRetry(clock.instant().plus(backoff), reason));
        LOGGER.warn("Outbox event {} to {} not delivered ({}); retry {} in {}s", event.getEventId(),
                event.getDestination(), reason, nextAttempt, backoff.toSeconds());
    }

    /** base * 2^(attempt-1), capped. */
    Duration backoffFor(int attempt) {
        long factor = 1L << Math.min(attempt - 1, 20);
        Duration candidate = settings.baseBackoff().multipliedBy(factor);
        return candidate.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : candidate;
    }

    /** Applies one state change to the freshly loaded row in its own short transaction. */
    private void update(OutboxEvent event, java.util.function.Consumer<OutboxEvent> change) {
        transactions.executeWithoutResult(status -> repository.findById(event.getId()).ifPresent(current -> {
            change.accept(current);
            repository.save(current);
        }));
    }

    /** Tunables (property prefix {@code travelease.outbox}). */
    public record OutboxSettings(boolean dispatchEnabled, int batchSize, int maxAttempts, Duration baseBackoff,
            Duration maxBackoff) {
    }
}
