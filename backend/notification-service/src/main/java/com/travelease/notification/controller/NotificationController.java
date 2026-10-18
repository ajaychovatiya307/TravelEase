package com.travelease.notification.controller;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

import com.travelease.common.dto.PagedResponse;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.travelease.notification.dto.NotificationResponse;
import com.travelease.notification.service.NotificationQueryService;

/** Authenticated recipient-facing notification APIs. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    /** Largest page a client may request; larger sizes are capped (#17). */
    static final int MAX_PAGE_SIZE = 100;

    /**
     * The caller's own notifications, newest first, paginated (#17). Recipient is always the token
     * subject, so one user can never page through another user's notifications.
     */
    @GetMapping
    public PagedResponse<NotificationResponse> listNotifications(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PagedResponse.from(notificationQueryService.findPageForRecipient(authenticatedUserId(jwt),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id").descending()))));
    }

    /** Unread badge count for the caller, independent of which page is displayed. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unreadCount", notificationQueryService.countUnread(authenticatedUserId(jwt)));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return notificationQueryService.markRead(id, authenticatedUserId(jwt));
    }

    private Long authenticatedUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user identity", exception);
        }
    }
}
