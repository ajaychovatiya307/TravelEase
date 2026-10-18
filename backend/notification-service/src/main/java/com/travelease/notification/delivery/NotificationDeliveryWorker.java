package com.travelease.notification.delivery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.travelease.notification.client.RecipientDirectoryClient;
import com.travelease.notification.entity.DeliveryStatus;
import com.travelease.notification.entity.NotificationEvent;
import com.travelease.notification.repository.NotificationEventRepository;

/**
 * Sends notification e-mails after the ingestion transaction committed (#17), so a rolled-back
 * ingestion can never have e-mailed anyone. Each notification is e-mailed at most once successfully:
 * it is marked SENT right after the SMTP server accepts it and is never picked up again. Failures are
 * retried with exponential backoff up to {@code max-attempts}, then marked FAILED_EXHAUSTED (the in-app
 * notification remains visible either way). Runs only when e-mail is enabled.
 */
@Component
public class NotificationDeliveryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationEventRepository repository;
    private final EmailNotificationSender emailSender;
    private final RecipientDirectoryClient directory;
    private final TransactionTemplate transactions;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Clock clock;

    public NotificationDeliveryWorker(NotificationEventRepository repository, EmailNotificationSender emailSender,
            RecipientDirectoryClient directory, PlatformTransactionManager transactionManager,
            @Value("${travelease.notifications.email.max-attempts:5}") int maxAttempts,
            @Value("${travelease.notifications.email.base-backoff:PT1M}") Duration baseBackoff) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.directory = directory;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${travelease.notifications.email.poll-interval-ms:15000}",
            initialDelayString = "${travelease.notifications.email.initial-delay-ms:15000}")
    public void scheduledDelivery() {
        if (emailSender.isEnabled()) {
            deliverDue();
        }
    }

    /** One delivery pass. @return number of e-mails sent */
    public int deliverDue() {
        Instant now = clock.instant();
        List<NotificationEvent> due = new ArrayList<>(repository.findByDeliveryStatus(DeliveryStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        repository.findByDeliveryStatus(DeliveryStatus.FAILED_RETRYING, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent().stream()
                .filter(n -> n.getLastAttemptAt() == null || !now.isBefore(n.getLastAttemptAt().plus(backoff(n.getRetryCount()))))
                .forEach(due::add);
        int sent = 0;
        for (NotificationEvent notification : due) {
            if (deliver(notification.getId())) {
                sent++;
            }
        }
        return sent;
    }

    private boolean deliver(Long notificationId) {
        Boolean result = transactions.execute(status -> {
            NotificationEvent notification = repository.findById(notificationId).orElse(null);
            if (notification == null || notification.getDeliveryStatus() == DeliveryStatus.SENT
                    || notification.getDeliveryStatus() == DeliveryStatus.FAILED_EXHAUSTED) {
                return false;
            }
            notification.setLastAttemptAt(clock.instant());
            try {
                Optional<String> email = directory.activeEmailOf(notification.getRecipientId());
                if (email.isEmpty()) {
                    // nobody to e-mail (no address / inactive user): permanent, not retried
                    notification.setDeliveryStatus(DeliveryStatus.FAILED_EXHAUSTED);
                    LOGGER.warn("Notification {} not e-mailed: recipient {} has no active e-mail address",
                            notification.getId(), notification.getRecipientId());
                    repository.save(notification);
                    return false;
                }
                emailSender.send(notification, email.get());
                notification.setDeliveryStatus(DeliveryStatus.SENT);
                repository.save(notification);
                return true;
            } catch (RuntimeException ex) {
                notification.setRetryCount(notification.getRetryCount() + 1);
                boolean exhausted = notification.getRetryCount() >= maxAttempts;
                notification.setDeliveryStatus(exhausted ? DeliveryStatus.FAILED_EXHAUSTED : DeliveryStatus.FAILED_RETRYING);
                repository.save(notification);
                LOGGER.warn("E-mail for notification {} failed (attempt {}/{}): {}", notification.getId(),
                        notification.getRetryCount(), maxAttempts, ex.getClass().getSimpleName());
                return false;
            }
        });
        return Boolean.TRUE.equals(result);
    }

    Duration backoff(int retryCount) {
        return baseBackoff.multipliedBy(1L << Math.min(Math.max(retryCount - 1, 0), 10));
    }
}
