package com.travelease.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.travelease.common.security.internal.InternalServiceTokens;
import com.travelease.notification.NotificationServiceApplication;
import com.travelease.notification.client.FinanceRecipientClient;
import com.travelease.notification.client.RecipientDirectoryClient;
import com.travelease.notification.entity.DeliveryStatus;
import com.travelease.notification.entity.NotificationEvent;
import com.travelease.notification.repository.NotificationEventRepository;

/**
 * #17 real e-mail delivery through a real SMTP server (GreenMail, in-process): after-commit sending,
 * no duplicates, retry after an SMTP outage, bounded attempts, missing addresses.
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications-email-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "server.servlet.context-path=",
        "travelease.notifications.email.enabled=true",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3925",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "travelease.notifications.email.base-backoff=PT0S",
        "travelease.notifications.email.max-attempts=3",
        "travelease.notifications.email.initial-delay-ms=3600000"
})
@DisplayName("Notification e-mail delivery IT (#17)")
class NotificationEmailDeliveryIT {

    private static final ServerSetup SMTP = new ServerSetup(3925, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationDeliveryWorker worker;

    @Autowired
    private NotificationEventRepository repository;

    @MockBean
    private RecipientDirectoryClient directory;

    @MockBean
    private FinanceRecipientClient financeRecipientClient;

    private GreenMail smtp;

    @BeforeEach
    void startSmtp() {
        repository.deleteAll();
        smtp = new GreenMail(SMTP);
        smtp.start();
        when(directory.activeEmailOf(7L)).thenReturn(Optional.of("asha.rao@example.test"));
        when(directory.activeEmailOf(8L)).thenReturn(Optional.empty());
    }

    @AfterEach
    void stopSmtp() {
        smtp.stop();
    }

    private void ingest(String eventId, long recipient) throws Exception {
        mockMvc.perform(post("/api/events")
                        .header(InternalServiceTokens.HEADER, new InternalServiceTokens(
                                "test-only-internal-secret-never-deploy-0123456789", "travel-request-service").currentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"TRAVEL_REQUEST","entityId":"25","action":"APPROVED",
                                 "timestamp":"2026-10-10T10:30:00Z","recipientIds":[%d],"eventId":"%s"}
                                """.formatted(recipient, eventId)))
                .andExpect(status().isAccepted());
    }

    private NotificationEvent only() {
        List<NotificationEvent> all = repository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    @Test
    @DisplayName("a notification is e-mailed once, after commit, and never again")
    void sendsExactlyOnce() throws Exception {
        ingest("evt-email-1", 7L);
        // ingestion itself sends nothing (the worker sends after the transaction committed)
        assertThat(smtp.getReceivedMessages()).isEmpty();
        assertThat(only().getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);

        assertThat(worker.deliverDue()).isEqualTo(1);
        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("asha.rao@example.test");
        assertThat(received[0].getSubject()).contains("Your travel request has been approved.");
        assertThat(only().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);

        // further passes and a re-delivered event do not produce duplicates
        worker.deliverDue();
        ingest("evt-email-1", 7L);
        worker.deliverDue();
        assertThat(smtp.getReceivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("an SMTP outage is retried and delivered once the server is back")
    void retriesAfterOutage() throws Exception {
        ingest("evt-email-2", 7L);
        smtp.stop();

        assertThat(worker.deliverDue()).isZero();
        NotificationEvent failed = only();
        assertThat(failed.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED_RETRYING);
        assertThat(failed.getRetryCount()).isEqualTo(1);

        smtp = new GreenMail(SMTP);
        smtp.start();
        assertThat(worker.deliverDue()).isEqualTo(1);
        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(only().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("retries are bounded: after max attempts the e-mail is FAILED_EXHAUSTED (in-app notice stays)")
    void boundedRetries() throws Exception {
        ingest("evt-email-3", 7L);
        smtp.stop();

        worker.deliverDue();
        worker.deliverDue();
        worker.deliverDue();
        worker.deliverDue();

        NotificationEvent exhausted = only();
        assertThat(exhausted.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED_EXHAUSTED);
        assertThat(exhausted.getRetryCount()).isEqualTo(3);
        assertThat(exhausted.isRead()).isFalse();
    }

    @Test
    @DisplayName("a recipient without an active e-mail address is skipped without retries")
    void noAddress() throws Exception {
        ingest("evt-email-4", 8L);

        assertThat(worker.deliverDue()).isZero();
        assertThat(only().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED_EXHAUSTED);
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("enabling e-mail without an SMTP host refuses to start instead of pretending to send")
    void enabledWithoutHostFails() {
        assertThatThrownBy(() -> new EmailNotificationSender(true, "", "from@x", "http://app", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }
}
