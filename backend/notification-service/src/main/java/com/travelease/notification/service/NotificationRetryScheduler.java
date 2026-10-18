package com.travelease.notification.service;

/**
 * Service interface for scheduling notification delivery retries (tasks.md H-11; spec.md FR-NOT-02).
 *
 * <p>Provides a mechanism to schedule asynchronous retry attempts for notifications
 * that failed initial delivery. Retries are non-blocking and never propagate failures
 * back to the originating workflow (Q22, Constitution §9).</p>
 *
 * <p>Production implementations might use a message queue (e.g., RabbitMQ, Kafka)
 * or a scheduled task runner with exponential backoff.</p>
 */
public interface NotificationRetryScheduler {

    /**
     * Schedules an async retry attempt for a failed notification (FR-NOT-02).
     *
     * <p>This method is called when initial dispatch fails. The implementation
     * schedules the retry asynchronously (e.g., via message queue or scheduled task)
     * without blocking the caller.</p>
     *
     * @param notificationEventId the ID of the notification event to retry
     */
    void scheduleRetry(Long notificationEventId);
}
