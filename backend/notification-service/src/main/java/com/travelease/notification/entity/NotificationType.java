package com.travelease.notification.entity;

/**
 * Notification category corresponding to a travel/expense status-change event
 * (data-model.md §8, FR-NOT-01).
 */
public enum NotificationType {
    APPROVAL,
    REJECTION,
    REIMBURSEMENT
}
