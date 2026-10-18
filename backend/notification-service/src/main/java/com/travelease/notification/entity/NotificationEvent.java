package com.travelease.notification.entity;

import java.time.Instant;

import com.travelease.common.event.AuditEntityType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Asynchronous delivery-tracking event for user notifications (data-model.md §7.1;
 * spec.md FR-NOT-01, FR-NOT-02).
 *
 * <p>This entity exists purely to track asynchronous delivery of notification
 * events. Delivery failures are retried and logged, and never block or roll back
 * the originating workflow transaction (clarification Q22). Because the record
 * carries its own {@code createdAt} and needs no soft-delete or update audit
 * fields, this entity does NOT extend {@code Auditable}.</p>
 *
 * <p>{@code entityType}/{@code entityId} and {@code recipientId} are plain
 * identifier columns - there is no cross-service JPA association (Constitution
 * §2); {@code recipientId} is a logical reference to {@code UserAccount.id}.
 * {@code lastAttemptAt} is deliberately nullable: null until the first dispatch
 * attempt.</p>
 */
@Entity
@Table(name = "notification_event", indexes = {
        @Index(name = "idx_notification_event_status_last_attempt", columnList = "delivery_status, last_attempt_at")
}, uniqueConstraints = {
        // One notification per recipient per source event: re-delivered events never duplicate
        @jakarta.persistence.UniqueConstraint(name = "uk_notification_source_event_recipient",
                columnNames = {"source_event_id", "recipient_id"})
})
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private AuditEntityType entityType;

    /** Logical reference to the originating entity in its owning service; no cross-service JPA association. */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Human-readable message shown to the recipient. Nullable for rows created before this field existed. */
    @Column(name = "message", length = 500)
    private String message;

    /** Logical reference to the recipient (UserAccount.id); required per FR-NOT-01. */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus;

    /** Number of delivery attempts beyond the initial dispatch (data-model.md §7.1; default 0). */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Nullable on purpose - null until the first dispatch attempt (FR-NOT-02). */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "is_read", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean read;

    /** Producer event id (outbox) this notification was created from; null for legacy rows. */
    @Column(name = "source_event_id", length = 36)
    private String sourceEventId;

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(AuditEntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}