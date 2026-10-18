package com.travelease.notification.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.notification.service.NotificationDispatchService;

/**
 * Event listener for domain events published by Travel Request and Expense services (tasks.md H-11).
 * 
 * <p>Listens for {@link DomainEventEnvelope} events and delegates to {@link NotificationDispatchService}
 * to create notification records and dispatch notifications. This listener is configured to run after
 * successful transaction commit ({@code TransactionPhase.AFTER_COMMIT}), ensuring notifications are
 * only created when the originating transaction has succeeded.</p>
 *
 * <p>Per Constitution §9, side effects (including notification dispatch) are asynchronous and
 * non-blocking — if this listener fails, it does not roll back the originating transaction.</p>
 */
@Component
public class NotificationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationDispatchService notificationDispatchService;

    public NotificationEventListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    /**
     * Listens for domain events and initiates notification dispatch.
     *
     * <p>This method is invoked after the publishing transaction commits ({@code AFTER_COMMIT}),
     * ensuring that notifications are only created when the source transaction succeeds.
     * If notification dispatch fails, a log warning is recorded but the failure does not propagate
     * back to the originating workflow (non-blocking side effect per Constitution §9).</p>
     *
     * @param event the domain event envelope published by a service
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEventEnvelope event) {
        LOGGER.debug("Notification event listener received event: entityType={}, entityId={}, action={}",
                event.entityType(), event.entityId(), event.action());

        try {
            notificationDispatchService.dispatchNotification(event);
            LOGGER.info("Successfully dispatched notification for event: entityType={}, entityId={}, action={}",
                    event.entityType(), event.entityId(), event.action());
        } catch (Exception e) {
            // Log error but do not propagate exception (Constitution §9 - non-blocking side effects)
            LOGGER.warn("Failed to dispatch notification for event: entityType={}, entityId={}, action={}. Error: {}",
                    event.entityType(), event.entityId(), event.action(), e.getMessage(), e);
            // In production, consider alerting/monitoring on repeated failures
        }
    }
}
