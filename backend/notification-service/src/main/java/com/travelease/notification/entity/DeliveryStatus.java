package com.travelease.notification.entity;

/**
 * Delivery lifecycle for a NotificationEvent (data-model.md §7.1). Failures are retried
 * asynchronously and logged; they never block or roll back the originating workflow
 * transaction (clarification Q22, FR-NOT-02).
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED_RETRYING,
    FAILED_EXHAUSTED
}
