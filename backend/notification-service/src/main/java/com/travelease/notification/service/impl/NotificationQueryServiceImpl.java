package com.travelease.notification.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.travelease.notification.dto.NotificationResponse;
import com.travelease.notification.entity.NotificationEvent;
import com.travelease.notification.repository.NotificationEventRepository;
import com.travelease.notification.service.NotificationQueryService;

@Service
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationEventRepository notificationEventRepository;

    public NotificationQueryServiceImpl(NotificationEventRepository notificationEventRepository) {
        this.notificationEventRepository = notificationEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> findForRecipient(Long recipientId) {
        return notificationEventRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<NotificationResponse> findPageForRecipient(Long recipientId,
            org.springframework.data.domain.Pageable pageable) {
        return notificationEventRepository.findByRecipientId(recipientId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        return notificationEventRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long notificationId, Long recipientId) {
        NotificationEvent notification = notificationEventRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationEventRepository.save(notification);
        }
        return toResponse(notification);
    }

    private NotificationResponse toResponse(NotificationEvent notification) {
        String message = notification.getMessage() != null
                ? notification.getMessage()
                : "You have an update regarding your "
                        + notification.getEntityType().name().toLowerCase().replace('_', ' ') + ".";
        return new NotificationResponse(
                notification.getId(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getNotificationType(),
                notification.getRecipientId(),
                message,
                notification.isRead(),
                notification.getCreatedAt());
    }
}
