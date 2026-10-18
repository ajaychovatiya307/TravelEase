package com.travelease.notification.dto;

import java.time.Instant;

import com.travelease.common.event.AuditEntityType;
import com.travelease.notification.entity.NotificationType;

/** Recipient-facing notification representation. */
public record NotificationResponse(
        Long id,
        AuditEntityType entityType,
        Long entityId,
        NotificationType notificationType,
        Long recipientId,
        String message,
        boolean read,
        Instant createdAt) {
}
