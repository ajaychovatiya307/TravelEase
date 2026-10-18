package com.travelease.notification.service;

import java.util.List;

import com.travelease.notification.dto.NotificationResponse;

public interface NotificationQueryService {

    List<NotificationResponse> findForRecipient(Long recipientId);

    NotificationResponse markRead(Long notificationId, Long recipientId);

    /** One page of the recipient's notifications, newest first (#17 pagination). */
    org.springframework.data.domain.Page<NotificationResponse> findPageForRecipient(Long recipientId,
            org.springframework.data.domain.Pageable pageable);

    /** Number of unread notifications for the recipient (badge count, independent of paging). */
    long countUnread(Long recipientId);
}
