package com.travelease.notification.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.travelease.notification.entity.NotificationEvent;

/**
 * E-mail channel for notifications (#17). Off unless {@code travelease.notifications.email.enabled=true};
 * when enabled it requires a configured SMTP host ({@code spring.mail.host}) and refuses to start
 * otherwise, so the application never claims e-mail delivery it cannot perform. In-app notifications
 * are unaffected either way.
 */
@Component
public class EmailNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final boolean enabled;
    private final JavaMailSender mailSender;
    private final String from;
    private final String appBaseUrl;

    public EmailNotificationSender(
            @Value("${travelease.notifications.email.enabled:false}") boolean enabled,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${travelease.notifications.email.from:no-reply@travelease.local}") String from,
            @Value("${travelease.notifications.app-base-url:http://localhost:5173}") String appBaseUrl,
            ObjectProvider<JavaMailSender> mailSender) {
        if (enabled && (smtpHost == null || smtpHost.isBlank())) {
            throw new IllegalStateException("travelease.notifications.email.enabled=true requires spring.mail.host "
                    + "(set TRAVELEASE_SMTP_HOST and related SMTP settings), or disable e-mail notifications.");
        }
        this.enabled = enabled;
        this.mailSender = enabled ? mailSender.getObject() : null;
        this.from = from;
        this.appBaseUrl = appBaseUrl;
        LOGGER.info("E-mail notifications {}", enabled ? "enabled (SMTP " + smtpHost + ")" : "disabled (in-app only)");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Sends one notification e-mail; throws on SMTP failure so the caller can retry. */
    public void send(NotificationEvent notification, String recipientEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject("TravelEase: " + notification.getMessage());
        message.setText(notification.getMessage() + "\n\nOpen TravelEase to view the details: " + appBaseUrl
                + "\n\nThis is an automated message from TravelEase.");
        mailSender.send(message);
    }
}
