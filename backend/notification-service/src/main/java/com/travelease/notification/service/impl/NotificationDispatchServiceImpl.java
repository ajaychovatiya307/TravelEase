package com.travelease.notification.service.impl;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.notification.entity.DeliveryStatus;
import com.travelease.notification.entity.NotificationEvent;
import com.travelease.notification.entity.NotificationType;
import com.travelease.notification.repository.NotificationEventRepository;
import com.travelease.notification.client.FinanceRecipientClient;
import com.travelease.notification.delivery.EmailNotificationSender;
import com.travelease.notification.service.NotificationDispatchService;
import com.travelease.notification.service.NotificationRetryScheduler;

/**
 * Notification dispatch service implementation (tasks.md H-11).
 * 
 * <p>Consumes domain events published by Travel Request and Expense services,
 * creates persistent notification event records, and dispatches notifications
 * asynchronously. Failed deliveries are retried via the retry scheduler.</p>
 */
@Service
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchServiceImpl.class);

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationRetryScheduler retryScheduler;
    private final FinanceRecipientClient financeRecipientClient;
    private final EmailNotificationSender emailSender;

    /** Mock dispatch outcome, overridable for failure-path testing (tasks.md H-11; no real channel for MVP). */
    @Value("${notification.mock-dispatch.always-succeed:true}")
    private boolean mockDispatchAlwaysSucceed = true;

    public NotificationDispatchServiceImpl(
            NotificationEventRepository notificationEventRepository,
            NotificationRetryScheduler retryScheduler,
            FinanceRecipientClient financeRecipientClient,
            EmailNotificationSender emailSender) {
        this.notificationEventRepository = notificationEventRepository;
        this.retryScheduler = retryScheduler;
        this.financeRecipientClient = financeRecipientClient;
        this.emailSender = emailSender;
    }

    @Override
    @Transactional
    public void dispatchNotification(DomainEventEnvelope event) {
        LOGGER.debug("Processing notification event: entityType={}, entityId={}, action={}, timestamp={}",
                event.entityType(), event.entityId(), event.action(), event.timestamp());

        try {
            // Parse entity type and action
            AuditEntityType entityType = AuditEntityType.valueOf(event.entityType());
            AuditActionType actionType = AuditActionType.valueOf(event.action());
            Long entityId = Long.valueOf(event.entityId());

            // Determine notification type from action
            NotificationType notificationType = mapActionToNotificationType(actionType);
            if (notificationType == null) {
                LOGGER.debug("No notification required for action: {}", actionType);
                return;  // Some actions don't trigger notifications (e.g., UPDATED)
            }

            // Submission events notify the current Finance roster; other workflow
            // events carry the actual employee/approver IDs from the owning service.
            List<Long> recipientIds = resolveRecipients(event, entityType, actionType);

            // Create and persist notification events for each recipient
            for (Long recipientId : recipientIds.stream().distinct().toList()) {
                // Idempotent re-delivery (outbox is at-least-once): one notification per recipient per event
                if (event.eventId() != null
                        && notificationEventRepository.existsBySourceEventIdAndRecipientId(event.eventId(), recipientId)) {
                    LOGGER.info("Duplicate notification for event {} and recipient {} ignored", event.eventId(), recipientId);
                    continue;
                }
                NotificationEvent notificationEvent = new NotificationEvent();
                notificationEvent.setSourceEventId(event.eventId());
                notificationEvent.setEntityType(entityType);
                notificationEvent.setEntityId(entityId);
                notificationEvent.setRecipientId(recipientId);
                notificationEvent.setMessage(messageFor(entityType, actionType));
                notificationEvent.setNotificationType(notificationType);
                notificationEvent.setDeliveryStatus(DeliveryStatus.PENDING);
                notificationEvent.setRetryCount(0);
                notificationEvent.setCreatedAt(Instant.now());
                notificationEvent.setLastAttemptAt(null);  // No attempt yet

                NotificationEvent savedEvent = notificationEventRepository.save(notificationEvent);
                LOGGER.info("Created notification event: id={}, recipient={}, type={}, entityType={}, entityId={}",
                        savedEvent.getId(), recipientId, notificationType, entityType, entityId);

                if (emailSender != null && emailSender.isEnabled()) {
                    // #17: stays PENDING; NotificationDeliveryWorker e-mails it after this transaction commits
                    continue;
                }
                attemptDispatch(savedEvent);
            }

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid event format for entityType=" + event.entityType()
                            + ", action=" + event.action() + ": " + e.getMessage(),
                    e);
        } catch (Exception e) {
            // Do not acknowledge: a 5xx makes the producer's outbox retry later (e.g. admin-service
            // was briefly unavailable for the Finance roster). Already-created rows roll back with
            // this transaction and the per-recipient idempotency check prevents duplicates.
            LOGGER.error("Unexpected error during notification dispatch for entityId={}: {}",
                    event.entityId(), e.getMessage());
            throw new IllegalStateException("Notification dispatch failed; the event will be retried", e);
        }
    }

    /**
     * Maps an audit action to a notification type.
     *
     * @param actionType the audit action type
     * @return the corresponding notification type, or null if action should not trigger a notification
     */
    private NotificationType mapActionToNotificationType(AuditActionType actionType) {
        return switch (actionType) {
            case CREATED -> NotificationType.APPROVAL;
            case APPROVED -> NotificationType.APPROVAL;
            case REJECTED -> NotificationType.REJECTION;
            case REIMBURSED -> NotificationType.REIMBURSEMENT;
            case RESUBMITTED -> NotificationType.APPROVAL;
            // Reuse APPROVAL (an approval/review-queue event) so no notification_type schema change is needed
            case SUBMITTED -> NotificationType.APPROVAL;
            case CANCELLED -> NotificationType.APPROVAL;
            case REASSIGNED -> NotificationType.APPROVAL;
            default -> null;  // UPDATED does not trigger a notification in MVP
        };
    }

    private List<Long> resolveRecipients(
            DomainEventEnvelope event, AuditEntityType entityType, AuditActionType actionType) {
        if (entityType == AuditEntityType.EXPENSE_CLAIM) {
            // Claims are created as private DRAFTs; Finance is notified when one enters review
            if (actionType == AuditActionType.CREATED) {
                return List.of();
            }
            if (actionType == AuditActionType.SUBMITTED || actionType == AuditActionType.RESUBMITTED) {
                return financeRecipientClient.findActiveFinanceUserIds();
            }
        }
        List<Long> recipientIds = event.recipientIds() == null ? List.of() : event.recipientIds();
        LOGGER.debug("Resolved {} recipients for {} {} action", recipientIds.size(), entityType, actionType);
        return recipientIds;
    }

    private String messageFor(AuditEntityType entityType, AuditActionType actionType) {
        return switch (entityType) {
            case TRAVEL_REQUEST -> switch (actionType) {
                case CREATED -> "A travel request is awaiting your approval.";
                case RESUBMITTED -> "A resubmitted travel request is awaiting your approval.";
                case APPROVED -> "Your travel request has been approved.";
                case REJECTED -> "Your travel request has been rejected.";
                case CANCELLED -> "A travel request assigned to you has been withdrawn by the requester.";
                case REASSIGNED -> "A travel request has been reassigned to you for approval.";
                default -> throw new IllegalArgumentException("Unsupported travel notification action: " + actionType);
            };
            case EXPENSE_CLAIM -> switch (actionType) {
                case CREATED, SUBMITTED -> "A new expense claim is awaiting your review.";
                case RESUBMITTED -> "A corrected expense claim has been resubmitted for your review.";
                case APPROVED -> "Your expense claim has been approved.";
                case REJECTED -> "Your expense claim has been rejected.";
                case REIMBURSED -> "Your expense claim has been reimbursed.";
                default -> throw new IllegalArgumentException("Unsupported expense notification action: " + actionType);
            };
            default -> throw new IllegalArgumentException("No notifications for entity type " + entityType);
        };
    }

    /**
     * Attempts to dispatch a notification (mocked for MVP).
     *
     * <p>For MVP, this always succeeds. In production, this would integrate with
     * email/SMS/push notification providers. On failure, schedules a retry via the
     * {@link NotificationRetryScheduler}.</p>
     *
     * @param event the notification event to dispatch
     */
    private void attemptDispatch(NotificationEvent event) {
        try {
            boolean dispatchSuccess = mockDispatchNotification(event);

            if (dispatchSuccess) {
                // Update delivery status to SENT
                event.setDeliveryStatus(DeliveryStatus.SENT);
                event.setLastAttemptAt(Instant.now());
                notificationEventRepository.save(event);
                LOGGER.info("Notification dispatched successfully: id={}, recipient={}", event.getId(), event.getRecipientId());
            } else {
                // Mark as failed and schedule retry
                event.setDeliveryStatus(DeliveryStatus.FAILED_RETRYING);
                event.setLastAttemptAt(Instant.now());
                event.setRetryCount(event.getRetryCount() + 1);
                notificationEventRepository.save(event);
                LOGGER.warn("Notification dispatch failed: id={}, recipient={}. Scheduled for retry.",
                        event.getId(), event.getRecipientId());

                // Schedule async retry (non-blocking)
                retryScheduler.scheduleRetry(event.getId());
            }
        } catch (Exception e) {
            LOGGER.warn("Error during notification dispatch attempt for id={}. Error: {}",
                    event.getId(), e.getMessage(), e);

            // Mark as failed and schedule retry
            event.setDeliveryStatus(DeliveryStatus.FAILED_RETRYING);
            event.setLastAttemptAt(Instant.now());
            event.setRetryCount(event.getRetryCount() + 1);
            notificationEventRepository.save(event);
            retryScheduler.scheduleRetry(event.getId());
        }
    }

    /**
     * Mocks notification dispatch for MVP.
     *
     * <p>In production, this would invoke actual notification channels (email, SMS, etc.).
     * For now, this is a stub that can be configured to simulate failures for testing.</p>
     *
     * @param event the notification event to dispatch
     * @return true if dispatch succeeded, false otherwise
     */
    private boolean mockDispatchNotification(NotificationEvent event) {
        LOGGER.debug("Mock dispatching notification: id={}, type={}, recipient={}",
                event.getId(), event.getNotificationType(), event.getRecipientId());
        return mockDispatchAlwaysSucceed;
    }
}
