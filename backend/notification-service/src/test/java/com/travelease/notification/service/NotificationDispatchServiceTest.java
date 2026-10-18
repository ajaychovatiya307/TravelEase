package com.travelease.notification.service;

import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.notification.entity.DeliveryStatus;
import com.travelease.notification.entity.NotificationEvent;
import com.travelease.notification.entity.NotificationType;
import com.travelease.notification.client.FinanceRecipientClient;
import com.travelease.notification.repository.NotificationEventRepository;
import com.travelease.notification.service.impl.NotificationDispatchServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationDispatchServiceImpl} (tasks.md L-09; spec.md
 * FR-NOT-01, FR-NOT-02; clarification Q22; AC-15), covering the dispatch and retry
 * behaviour with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>successful dispatch: the initial {@code PENDING} save is followed by a second
 *       save of the SAME event with {@code DeliveryStatus.SENT} and a non-null
 *       {@code lastAttemptAt} (captured with an {@link ArgumentCaptor}), and
 *       {@link NotificationRetryScheduler#scheduleRetry} is never invoked</li>
 *   <li>one notification event per recipient ID supplied by the owning service</li>
 *   <li>dispatch failure (FR-NOT-02, Q22): the private dispatch stub always succeeds,
 *       so the failure branch is reached by making the repository {@code save} inside
 *       {@code attemptDispatch}'s success path throw — the event ends up
 *       {@code FAILED_RETRYING} with {@code retryCount} incremented, {@code lastAttemptAt}
 *       set, and {@code scheduleRetry} invoked; the exception NEVER propagates to the
 *       caller of {@code dispatchNotification}</li>
 *   <li>unexpected exception swallowed (Q22): a failure on the FIRST {@code save}
 *       (event creation) is logged and swallowed — nothing is persisted, no retry is
 *       scheduled, and the caller sees no exception (non-blocking side effect)</li>
 *   <li>invalid envelope (unknown action, unknown entity type, non-numeric entity id) →
 *       {@link IllegalArgumentException} ("Invalid event format: ...") with no
 *       repository or scheduler interaction</li>
 *   <li>non-notifiable action (UPDATED) returns quietly with
 *       {@code verifyNoInteractions} on the repository</li>
 *   <li>action → notification type mapping: APPROVED → APPROVAL, REJECTED → REJECTION,
 *       REIMBURSED → REIMBURSEMENT, with the parsed entityType/entityId on the record</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so both are mocked with Mockito and the service is injected via
 * {@code @InjectMocks} - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchServiceImpl (FR-NOT-01, FR-NOT-02, AC-15; tasks.md L-09)")
class NotificationDispatchServiceTest {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.TRAVEL_REQUEST;
    private static final Long ENTITY_ID = 10L;
    private static final Instant FIXED_TIMESTAMP = Instant.parse("2025-06-15T09:30:00Z");

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @Mock
    private NotificationRetryScheduler retryScheduler;

    @Mock
    private FinanceRecipientClient financeRecipientClient;

    @InjectMocks
    private NotificationDispatchServiceImpl dispatchService;

    /** Assigns database-like IDs on first persistence (IDENTITY strategy emulation). */
    private final AtomicLong idSequence = new AtomicLong(1);

    private static DomainEventEnvelope eventFor(AuditActionType action) {
        AuditEntityType entityType = action == AuditActionType.REIMBURSED
                ? AuditEntityType.EXPENSE_CLAIM
                : ENTITY_TYPE;
        return new DomainEventEnvelope(
                entityType.name(), String.valueOf(ENTITY_ID), action.name(), FIXED_TIMESTAMP, List.of(ENTITY_ID));
    }

    /**
     * Stubs {@code save} to assign an id on first persistence and echo its argument
     * (the implementation logs {@code savedEvent.getId()} after persisting, and the
     * same instance is later mutated and re-saved by the dispatch attempt).
     */
    private void stubSaveEchoesArgument() {
        when(notificationEventRepository.save(any(NotificationEvent.class)))
                .thenAnswer(invocation -> {
                    NotificationEvent event = invocation.getArgument(0);
                    if (event.getId() == null) {
                        event.setId(idSequence.getAndIncrement());
                    }
                    return event;
                });
    }

    private List<NotificationEvent> captureAllSavedEvents() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    /**
     * The mocked {@code save} echoes the same instance, so each recipient's event is
     * captured twice (initial + post-attempt). Deduplicating by reference yields one
     * entry per recipient, in creation order.
     */
    private List<NotificationEvent> distinctSavedEvents(List<NotificationEvent> allSaved) {
        return allSaved.stream().distinct().toList();
    }

    @Nested
    @DisplayName("successful dispatch (FR-NOT-01)")
    class SuccessfulDispatch {

        @Test
        @DisplayName("marks the same persisted event SENT with lastAttemptAt and never schedules a retry")
        void marksEventSentAndDoesNotScheduleRetry() {
            stubSaveEchoesArgument();

            dispatchService.dispatchNotification(eventFor(AuditActionType.APPROVED));

            List<NotificationEvent> savedEvents = captureAllSavedEvents();

            // One recipient (entity owner) → initial PENDING save + post-dispatch save
            assertThat(savedEvents).hasSize(2);
            // The second save is of the SAME event instance (the impl mutates the saved
            // entity and persists it again)
            assertThat(savedEvents.get(1)).isSameAs(savedEvents.get(0));

            NotificationEvent finalEvent = savedEvents.get(1);
            assertThat(finalEvent.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(finalEvent.getLastAttemptAt()).isNotNull();
            // Success path must not count a retry
            assertThat(finalEvent.getRetryCount()).isZero();
            assertThat(finalEvent.getRecipientId()).isEqualTo(ENTITY_ID);
            assertThat(finalEvent.getNotificationType()).isEqualTo(NotificationType.APPROVAL);
            assertThat(finalEvent.getEntityType()).isEqualTo(ENTITY_TYPE);
            assertThat(finalEvent.getEntityId()).isEqualTo(ENTITY_ID);
            assertThat(finalEvent.getCreatedAt()).isNotNull();

            verifyNoInteractions(retryScheduler);
        }

        @ParameterizedTest(name = "{0} notifies recipients {1}")
        @MethodSource("recipientCases")
        @DisplayName("creates exactly one notification event per resolved recipient")
        void createsOneNotificationEventPerRecipient(AuditActionType action, List<Long> expectedRecipients) {
            stubSaveEchoesArgument();

            dispatchService.dispatchNotification(eventFor(action));

            List<NotificationEvent> allSaved = captureAllSavedEvents();
            List<NotificationEvent> perRecipient = distinctSavedEvents(allSaved);

            // Each recipient's event is saved twice (PENDING creation + post-dispatch update)
            assertThat(allSaved).hasSize(expectedRecipients.size() * 2);
            assertThat(perRecipient).hasSize(expectedRecipients.size());
            assertThat(perRecipient)
                    .extracting(NotificationEvent::getRecipientId)
                    .containsExactlyElementsOf(expectedRecipients);

            verifyNoInteractions(retryScheduler);
        }

        static Stream<Arguments> recipientCases() {
            return Stream.of(
                    Arguments.of(AuditActionType.CREATED, List.of(ENTITY_ID)),
                    Arguments.of(AuditActionType.APPROVED, List.of(ENTITY_ID)),
                    Arguments.of(AuditActionType.REJECTED, List.of(ENTITY_ID)),
                    Arguments.of(AuditActionType.RESUBMITTED, List.of(ENTITY_ID)),
                    Arguments.of(AuditActionType.REIMBURSED, List.of(ENTITY_ID)));
        }

        @Test
        @DisplayName("expense submission notifies each active Finance recipient returned by admin-service")
        void notifiesActualFinanceRecipientsForExpenseSubmission() {
            stubSaveEchoesArgument();
            when(financeRecipientClient.findActiveFinanceUserIds()).thenReturn(List.of(41L, 42L, 42L));
            DomainEventEnvelope event = new DomainEventEnvelope(
                    "EXPENSE_CLAIM", "15", "SUBMITTED", FIXED_TIMESTAMP, List.of());

            dispatchService.dispatchNotification(event);

            List<NotificationEvent> notifications = distinctSavedEvents(captureAllSavedEvents());
            assertThat(notifications)
                    .extracting(NotificationEvent::getRecipientId)
                    .containsExactly(41L, 42L);
            assertThat(notifications)
                    .extracting(NotificationEvent::getMessage)
                    .containsOnly("A new expense claim is awaiting your review.");
            assertThat(notifications).allSatisfy(notification ->
                    assertThat(notification.isRead()).isFalse());
        }

        @Test
        @DisplayName("a newly created DRAFT expense claim notifies nobody (Finance only sees submitted claims)")
        void draftExpenseCreationNotifiesNobody() {
            dispatchService.dispatchNotification(new DomainEventEnvelope(
                    "EXPENSE_CLAIM", "15", "CREATED", FIXED_TIMESTAMP, List.of()));

            org.mockito.Mockito.verifyNoInteractions(financeRecipientClient);
            org.mockito.Mockito.verify(notificationEventRepository, org.mockito.Mockito.never())
                    .save(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
        }

        @Test
        @DisplayName("a resubmitted (corrected) expense claim notifies the Finance roster again")
        void resubmittedExpenseNotifiesFinance() {
            stubSaveEchoesArgument();
            when(financeRecipientClient.findActiveFinanceUserIds()).thenReturn(List.of(41L));

            dispatchService.dispatchNotification(new DomainEventEnvelope(
                    "EXPENSE_CLAIM", "15", "RESUBMITTED", FIXED_TIMESTAMP, List.of()));

            List<NotificationEvent> notifications = distinctSavedEvents(captureAllSavedEvents());
            assertThat(notifications).extracting(NotificationEvent::getRecipientId).containsExactly(41L);
            assertThat(notifications).extracting(NotificationEvent::getMessage)
                    .containsOnly("A corrected expense claim has been resubmitted for your review.");
        }

        @Test
        @DisplayName("a cancelled travel request notifies the approver carried on the event")
        void cancelledTravelRequestNotifiesApprover() {
            stubSaveEchoesArgument();

            dispatchService.dispatchNotification(new DomainEventEnvelope(
                    "TRAVEL_REQUEST", "9", "CANCELLED", FIXED_TIMESTAMP, List.of(2L)));

            List<NotificationEvent> notifications = distinctSavedEvents(captureAllSavedEvents());
            assertThat(notifications).extracting(NotificationEvent::getRecipientId).containsExactly(2L);
            assertThat(notifications).extracting(NotificationEvent::getMessage)
                    .containsOnly("A travel request assigned to you has been withdrawn by the requester.");
        }
    }

    @Nested
    @DisplayName("failure handling (FR-NOT-02, Q22 non-blocking)")
    class FailureHandling {

        @Test
        @DisplayName("dispatch failure marks the event FAILED_RETRYING, increments retryCount, and schedules a retry")
        void marksFailedRetryingAndSchedulesRetry() {
            // The private dispatch stub always returns true, so the failure branch is
            // reached by making the save INSIDE attemptDispatch's success path throw:
            // 1st save (event creation) → echo, 2nd save (success branch) → throw,
            // 3rd save (attemptDispatch's catch block) → echo again.
            when(notificationEventRepository.save(any(NotificationEvent.class)))
                    .thenAnswer(invocation -> {
                        NotificationEvent event = invocation.getArgument(0);
                        if (event.getId() == null) {
                            event.setId(idSequence.getAndIncrement());
                        }
                        return event;
                    })
                    .thenThrow(new RuntimeException("simulated dispatch failure"))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Q22: the failure is logged and handled internally, never propagated
            assertThatCode(() -> dispatchService.dispatchNotification(eventFor(AuditActionType.APPROVED)))
                    .doesNotThrowAnyException();

            List<NotificationEvent> savedEvents = captureAllSavedEvents();
            assertThat(savedEvents).hasSize(3);

            NotificationEvent finalEvent = savedEvents.get(2);
            assertThat(finalEvent.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED_RETRYING);
            // retryCount incremented from the initial 0
            assertThat(finalEvent.getRetryCount()).isEqualTo(1);
            assertThat(finalEvent.getLastAttemptAt()).isNotNull();
            assertThat(finalEvent.getRecipientId()).isEqualTo(ENTITY_ID);
            assertThat(finalEvent.getNotificationType()).isEqualTo(NotificationType.APPROVAL);

            verify(retryScheduler, times(1)).scheduleRetry(finalEvent.getId());
        }

        @Test
        @DisplayName("unexpected exception on the first save is surfaced (5xx) so the producer's outbox retries the event")
        void surfacesUnexpectedExceptionOnEventCreation() {
            when(notificationEventRepository.save(any(NotificationEvent.class)))
                    .thenThrow(new RuntimeException("database down"));

            // Producers stay non-blocking (Q22 / Constitution §9) because delivery is asynchronous via
            // the outbox; the consumer must NOT acknowledge a failed dispatch, or the event would be lost
            // (previously it was swallowed and returned 202).
            assertThatThrownBy(() -> dispatchService.dispatchNotification(eventFor(AuditActionType.REIMBURSED)))
                    .isInstanceOf(IllegalStateException.class);

            // The failing save was attempted once; nothing was persisted
            verify(notificationEventRepository, times(1)).save(any(NotificationEvent.class));
            verifyNoInteractions(retryScheduler);
        }
    }

    @Nested
    @DisplayName("invalid events (Constitution §10)")
    class InvalidEvents {

        @ParameterizedTest(name = "entityType={0}, entityId={1}, action={2} → IllegalArgumentException")
        @MethodSource("invalidEnvelopeCases")
        @DisplayName("wraps an unparseable envelope in IllegalArgumentException and saves nothing")
        void throwsForInvalidEnvelope(String entityType, String entityId, String action) {
            DomainEventEnvelope event = new DomainEventEnvelope(entityType, entityId, action, FIXED_TIMESTAMP);

            assertThatThrownBy(() -> dispatchService.dispatchNotification(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid event format");

            verifyNoInteractions(notificationEventRepository);
            verifyNoInteractions(retryScheduler);
        }

        static Stream<Arguments> invalidEnvelopeCases() {
            return Stream.of(
                    Arguments.of("TRAVEL_REQUEST", "10", "NOT_AN_ACTION"),
                    Arguments.of("UNKNOWN_ENTITY", "10", "APPROVED"),
                    Arguments.of("TRAVEL_REQUEST", "not-a-number", "APPROVED"));
        }
    }

    @Nested
    @DisplayName("non-notifiable actions (MVP mapping)")
    class NonNotifiableActions {

        @ParameterizedTest
        @EnumSource(value = AuditActionType.class, names = {"UPDATED"})
        @DisplayName("{0} returns quietly without any repository interaction")
        void createsNothingForNonNotifiableAction(AuditActionType action) {
            assertThatCode(() -> dispatchService.dispatchNotification(eventFor(action)))
                    .doesNotThrowAnyException();

            verifyNoInteractions(notificationEventRepository);
            verifyNoInteractions(retryScheduler);
        }
    }

    @Nested
    @DisplayName("notification type mapping (FR-NOT-01)")
    class NotificationTypeMapping {

        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("actionToNotificationTypeCases")
        @DisplayName("persisted events carry the mapped notification type and parsed entity fields")
        void mapsActionToNotificationType(AuditActionType action, NotificationType expectedType) {
            stubSaveEchoesArgument();

            dispatchService.dispatchNotification(eventFor(action));

            List<NotificationEvent> perRecipient = distinctSavedEvents(captureAllSavedEvents());

            assertThat(perRecipient).isNotEmpty();
            for (NotificationEvent event : perRecipient) {
                assertThat(event.getNotificationType())
                        .as("notification type for action %s", action)
                        .isEqualTo(expectedType);
                assertThat(event.getEntityType()).isEqualTo(action == AuditActionType.REIMBURSED
                        ? AuditEntityType.EXPENSE_CLAIM
                        : ENTITY_TYPE);
                assertThat(event.getEntityId()).isEqualTo(ENTITY_ID);
            }

            verifyNoInteractions(retryScheduler);
        }

        static Stream<Arguments> actionToNotificationTypeCases() {
            return Stream.of(
                    Arguments.of(AuditActionType.CREATED, NotificationType.APPROVAL),
                    Arguments.of(AuditActionType.APPROVED, NotificationType.APPROVAL),
                    Arguments.of(AuditActionType.REJECTED, NotificationType.REJECTION),
                    Arguments.of(AuditActionType.RESUBMITTED, NotificationType.APPROVAL),
                    Arguments.of(AuditActionType.REIMBURSED, NotificationType.REIMBURSEMENT));
        }
    }
}