package com.travelease.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.travelease.notification.entity.DeliveryStatus;
import com.travelease.notification.entity.NotificationEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for notification delivery-tracking events (tasks.md D-10;
 * data-model.md §7.1; spec.md FR-NOT-02, AC-15).
 *
 * <p>{@link NotificationEvent} carries its own lifecycle state ({@code deliveryStatus},
 * {@code retryCount}, {@code lastAttemptAt}) and is not soft-deletable (Constitution §5
 * soft-delete only applies to domain entities with an {@code Auditable} base), so this
 * repository extends the full {@link JpaRepository} surface without a default-scope
 * filter.</p>
 */
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    /**
     * Retry-worker polling query: events in a given delivery state, oldest attempt
     * first, so the retry worker processes the longest-waiting events first
     * (FR-NOT-02).
     *
     * @param deliveryStatus the delivery state to poll for (e.g. {@code FAILED_RETRYING})
     * @param pageable       pagination/sorting (e.g. {@code lastAttemptAt,asc})
     * @return a page of events in the given delivery state
     */
    Page<NotificationEvent> findByDeliveryStatus(DeliveryStatus deliveryStatus, Pageable pageable);

    /**
     * All notification events addressed to one recipient, paginated (supports a
     * future recipient-facing notification history view).
     *
     * @param recipientId logical reference to {@code UserAccount.id}
     * @param pageable    pagination/sorting
     * @return a page of notification events for that recipient
     */
    Page<NotificationEvent> findByRecipientId(Long recipientId, Pageable pageable);

    /**
     * Non-paginated variant of the retry-worker polling query, for batch
     * retry-processing jobs that need the full eligible set in one pass
     * (FR-NOT-02).
     *
     * @param deliveryStatus the delivery state to poll for
     * @param lastAttemptAt  only events last attempted at or before this instant
     * @return events eligible for another retry attempt
     */
    List<NotificationEvent> findByDeliveryStatusAndLastAttemptAtLessThanEqual(DeliveryStatus deliveryStatus, Instant lastAttemptAt);

    List<NotificationEvent> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    Optional<NotificationEvent> findByIdAndRecipientId(Long id, Long recipientId);

    /** Idempotency probe: was this recipient already notified for this source event? */
    boolean existsBySourceEventIdAndRecipientId(String sourceEventId, Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);
}
