package com.travelease.notification.service;

import com.travelease.common.event.DomainEventEnvelope;

/**
 * Service interface for notification dispatch (tasks.md H-11; spec.md FR-NOT-01, FR-NOT-02).
 *
 * <p>Consumes domain events published by Travel Request and Expense services after
 * state-changing transactions commit, creates persistent {@link com.travelease.notification.entity.NotificationEvent} records,
 * and dispatches notifications asynchronously. Failed deliveries are retried and logged;
 * dispatch failures never block or propagate back to the originating workflow (Q22, Constitution §9).</p>
 *
 * <p>Designed to be invoked asynchronously by {@link com.travelease.notification.event.NotificationEventListener}
 * to ensure notification dispatch never blocks the originating workflow.</p>
 */
public interface NotificationDispatchService {

    /**
     * Processes a domain event and dispatches corresponding notifications (FR-NOT-01, FR-NOT-02, AC-15).
     *
     * <p>Workflow:
     * <ul>
     *   <li>Parses the event to extract entity type, entity ID, and action</li>
     *   <li>Determines the recipient(s) based on entity type and action:
     *     <ul>
     *       <li>For TRAVEL_REQUEST events: notifies the employee and the approver</li>
     *       <li>For EXPENSE_CLAIM events: notifies the employee, manager/finance reviewer</li>
     *     </ul>
     *   </li>
     *   <li>Maps action to {@link com.travelease.notification.entity.NotificationType} (APPROVAL, REJECTION, REIMBURSEMENT)</li>
     *   <li>Creates persistent {@link com.travelease.notification.entity.NotificationEvent} records with status PENDING</li>
     *   <li>Attempts initial dispatch (mocked/stubbed for MVP); updates deliveryStatus to SENT or FAILED_RETRYING</li>
     *   <li>On dispatch failure, schedules async retry via {@link NotificationRetryScheduler}</li>
     * </ul>
     *
     * <p>This method is idempotent within transactional scope and designed to be called by async listeners.
     * Dispatch failures are logged but never propagated back to the caller (Constitution §9).</p>
     *
     * @param event the domain event published after a service transaction commits
     * @throws IllegalArgumentException if event structure is invalid (e.g., unknown entityType or action)
     */
    void dispatchNotification(DomainEventEnvelope event);
}
