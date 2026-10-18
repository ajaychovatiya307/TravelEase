package com.travelease.common.outbox;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One domain event awaiting delivery to one destination (transactional outbox). Written in the same
 * database transaction as the business change, so an event exists if and only if the change committed.
 */
@Entity
@Table(name = "outbox_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_destination", columnNames = {"event_id", "destination"}),
        indexes = @Index(name = "idx_outbox_status_next_attempt", columnList = "status, next_attempt_at"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "destination", nullable = false, length = 40)
    private String destination;

    // LONGTEXT on MySQL (a bare @Lob String would be TINYTEXT, i.e. 255 bytes, and truncate payloads)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected OutboxEvent() {
        // JPA
    }

    static OutboxEvent pending(String eventId, String destination, String payload, Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.destination = destination;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.attempts = 0;
        event.nextAttemptAt = now;
        event.createdAt = now;
        return event;
    }

    void markDelivered(Instant now) {
        status = OutboxStatus.DELIVERED;
        attempts++;
        deliveredAt = now;
        lastError = null;
    }

    void markRetry(Instant nextAttempt, String error) {
        attempts++;
        nextAttemptAt = nextAttempt;
        lastError = truncate(error);
    }

    void markFailed(String error) {
        attempts++;
        status = OutboxStatus.FAILED;
        lastError = truncate(error);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getDestination() {
        return destination;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
