package com.travelease.audit.service;

import com.travelease.audit.entity.AuditRecord;
import com.travelease.audit.repository.AuditRecordRepository;
import com.travelease.audit.service.impl.AuditIngestionServiceImpl;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.event.DomainEventEnvelope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditIngestionServiceImpl} (tasks.md L-08; spec.md FR-AUD-01; AC-14),
 * covering the core ingestion guarantee with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>every simulated domain event produces EXACTLY ONE {@link AuditRecord}: a
 *       parameterised sweep over ALL {@link AuditActionType} values asserts the persisted
 *       record carries the correct {@code entityType}, {@code entityId}, {@code action},
 *       {@code actorId}, {@code beforeStatus}, {@code afterStatus} and {@code occurredAt}
 *       (captured with an {@link ArgumentCaptor} on the single {@code save} call)</li>
 *   <li>{@link AuditEntityType#EXPENSE_CLAIM} events are covered in addition to
 *       {@link AuditEntityType#TRAVEL_REQUEST} (e.g. REIMBURSED: before APPROVED → after
 *       REIMBURSED), so both entity types are exercised</li>
 *   <li>{@code resolveActorId} fallback behaviour: no authentication context → system actor
 *       {@code 0L}; unauthenticated token → {@code 0L}; authenticated numeric String
 *       principal → that user id; non-numeric or non-String principal → {@code 0L}</li>
 *   <li>unparseable {@code entityType}/{@code action}/{@code entityId} →
 *       {@link IllegalArgumentException} ("Invalid event format: ...") with no repository
 *       interaction</li>
 * </ul>
 *
 * <p>Notes on the implementation under test:</p>
 * <ul>
 *   <li>{@code beforeStatus} is deliberately left {@code null} for CREATED and UPDATED -
 *       a creation or update has no prior lifecycle status to record (the
 *       {@link AuditRecord} entity documents {@code before_status} as nullable on purpose);
 *       APPROVED/REJECTED/RESUBMITTED carry before-status {@code "PENDING"} and REIMBURSED
 *       carries before-status {@code "APPROVED"} per the implementation's transition rules.</li>
 *   <li>The mock {@code save} is stubbed to return its argument because the implementation
 *       reads {@code savedRecord.getId()} for its success log line; the generated id is
 *       database-issued ({@code GenerationType.IDENTITY}) and is not set by the service.</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with a constructor-injected
 * repository, so the repository is mocked with Mockito and the service is injected via
 * {@code @InjectMocks} - no Spring context is needed. The Spring Security context is set
 * up per test on the thread-local {@link SecurityContextHolder} and cleared afterwards.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditIngestionServiceImpl (FR-AUD-01, AC-14; tasks.md L-08)")
class AuditIngestionServiceTest {

    private static final AuditEntityType TRAVEL_REQUEST = AuditEntityType.TRAVEL_REQUEST;
    private static final AuditEntityType EXPENSE_CLAIM = AuditEntityType.EXPENSE_CLAIM;
    private static final Long TRAVEL_REQUEST_ID = 123L;
    private static final Long EXPENSE_CLAIM_ID = 456L;
    private static final Instant FIXED_TIMESTAMP = Instant.parse("2025-06-15T09:30:00Z");
    private static final Long SYSTEM_ACTOR_ID = 0L;

    @Mock
    private AuditRecordRepository auditRecordRepository;

    @InjectMocks
    private AuditIngestionServiceImpl ingestionService;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static DomainEventEnvelope eventFor(AuditEntityType entityType, long entityId,
                                                AuditActionType action, Instant timestamp) {
        return new DomainEventEnvelope(entityType.name(), String.valueOf(entityId), action.name(), timestamp);
    }

    /**
     * Stubs {@code save} to echo its argument (the implementation logs
     * {@code savedRecord.getId()} after persisting) and returns nothing else.
     */
    private void stubSaveReturnsArgument() {
        when(auditRecordRepository.save(any(AuditRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AuditRecord captureSoleSavedRecord() {
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    /** Expected before-status per the implementation's state-transition rules. */
    private static String expectedBeforeStatus(AuditActionType action) {
        return switch (action) {
            case APPROVED, REJECTED, RESUBMITTED -> "PENDING";
            case REIMBURSED -> "APPROVED";
            case SUBMITTED -> "DRAFT";
            case REASSIGNED -> "PENDING";
            case CREATED, UPDATED, CANCELLED -> null;
            case DEACTIVATED, ACTIVATED, ROLE_GRANTED, ROLE_REVOKED, PASSWORD_RESET, PASSWORD_CHANGED -> null;
        };
    }

    /** Expected after-status per the implementation's lifecycle mapping. */
    private static String expectedAfterStatus(AuditActionType action) {
        return switch (action) {
            case CREATED -> "DRAFT";
            case UPDATED, RESUBMITTED -> "PENDING";
            case APPROVED -> "APPROVED";
            case REJECTED -> "REJECTED";
            case REIMBURSED -> "REIMBURSED";
            case SUBMITTED -> "PENDING";
            case CANCELLED -> "CANCELLED";
            case REASSIGNED -> "PENDING";
            case DEACTIVATED, ACTIVATED, ROLE_GRANTED, ROLE_REVOKED, PASSWORD_RESET, PASSWORD_CHANGED -> null;
        };
    }

    @Nested
    @DisplayName("one AuditRecord per domain event (FR-AUD-01, AC-14)")
    class IngestionCompleteness {

        @ParameterizedTest
        @EnumSource
        @DisplayName("saves exactly one AuditRecord for TRAVEL_REQUEST action {0}")
        void savesExactlyOneAuditRecordPerAction(AuditActionType action) {
            stubSaveReturnsArgument();

            ingestionService.ingestEvent(eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, action, FIXED_TIMESTAMP));

            AuditRecord saved = captureSoleSavedRecord();

            assertThat(saved.getEntityType()).isEqualTo(TRAVEL_REQUEST);
            assertThat(saved.getEntityId()).isEqualTo(TRAVEL_REQUEST_ID);
            assertThat(saved.getAction()).isEqualTo(action);
            // No SecurityContext is set in this test, so resolveActorId falls back to the system actor
            assertThat(saved.getActorId()).isEqualTo(SYSTEM_ACTOR_ID);

            String expectedBefore = expectedBeforeStatus(action);
            if (expectedBefore == null) {
                // CREATED and UPDATED have no prior lifecycle status; the impl leaves beforeStatus null on purpose
                assertThat(saved.getBeforeStatus())
                        .as("beforeStatus for action %s must be null by design", action)
                        .isNull();
            } else {
                assertThat(saved.getBeforeStatus()).isEqualTo(expectedBefore);
            }
            assertThat(saved.getAfterStatus()).isEqualTo(expectedAfterStatus(action));
            assertThat(saved.getOccurredAt()).isEqualTo(FIXED_TIMESTAMP);
            // The id is generated by the database (IDENTITY strategy); the service never sets it
            assertThat(saved.getId()).isNull();

            verifyNoMoreInteractions(auditRecordRepository);
        }
    }

    @Nested
    @DisplayName("EXPENSE_CLAIM entity type coverage (data-model.md §8)")
    class ExpenseClaimIngestion {

        @Test
        @DisplayName("saves one EXPENSE_CLAIM/REIMBURSED record: before APPROVED, after REIMBURSED")
        void savesExpenseClaimReimbursedRecord() {
            stubSaveReturnsArgument();

            ingestionService.ingestEvent(eventFor(EXPENSE_CLAIM, EXPENSE_CLAIM_ID, AuditActionType.REIMBURSED, FIXED_TIMESTAMP));

            AuditRecord saved = captureSoleSavedRecord();

            assertThat(saved.getEntityType()).isEqualTo(EXPENSE_CLAIM);
            assertThat(saved.getEntityId()).isEqualTo(EXPENSE_CLAIM_ID);
            assertThat(saved.getAction()).isEqualTo(AuditActionType.REIMBURSED);
            assertThat(saved.getActorId()).isEqualTo(SYSTEM_ACTOR_ID);
            assertThat(saved.getBeforeStatus()).isEqualTo("APPROVED");
            assertThat(saved.getAfterStatus()).isEqualTo("REIMBURSED");
            assertThat(saved.getOccurredAt()).isEqualTo(FIXED_TIMESTAMP);

            verifyNoMoreInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("saves one EXPENSE_CLAIM/CREATED record with null before-status and after DRAFT")
        void savesExpenseClaimCreatedRecord() {
            stubSaveReturnsArgument();

            ingestionService.ingestEvent(eventFor(EXPENSE_CLAIM, EXPENSE_CLAIM_ID, AuditActionType.CREATED, FIXED_TIMESTAMP));

            AuditRecord saved = captureSoleSavedRecord();

            assertThat(saved.getEntityType()).isEqualTo(EXPENSE_CLAIM);
            assertThat(saved.getEntityId()).isEqualTo(EXPENSE_CLAIM_ID);
            assertThat(saved.getAction()).isEqualTo(AuditActionType.CREATED);
            assertThat(saved.getBeforeStatus())
                    .as("CREATED has no prior lifecycle status, so beforeStatus stays null")
                    .isNull();
            assertThat(saved.getAfterStatus()).isEqualTo("DRAFT");
            assertThat(saved.getOccurredAt()).isEqualTo(FIXED_TIMESTAMP);

            verifyNoMoreInteractions(auditRecordRepository);
        }
    }

    @Nested
    @DisplayName("actor resolution (resolveActorId)")
    class ActorResolution {

        @Test
        @DisplayName("uses system actor 0 when no authentication context is present")
        void usesSystemActorWhenNoContext() {
            stubSaveReturnsArgument();

            ingestionService.ingestEvent(eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.CREATED, FIXED_TIMESTAMP));

            assertThat(captureSoleSavedRecord().getActorId()).isEqualTo(SYSTEM_ACTOR_ID);
        }

        @Test
        @DisplayName("uses the numeric String principal as actorId when authenticated")
        void usesNumericPrincipalAsActorId() {
            stubSaveReturnsArgument();
            setAuthenticatedPrincipal("42");

            ingestionService.ingestEvent(eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.APPROVED, FIXED_TIMESTAMP));

            assertThat(captureSoleSavedRecord().getActorId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("uses the JWT subject as actorId for resource-server authentication")
        void usesJwtSubjectAsActorId() {
            stubSaveReturnsArgument();
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject("11")
                    .build();
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
            authentication.setAuthenticated(true);
            SecurityContext context = new SecurityContextImpl();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            ingestionService.ingestEvent(
                    eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.APPROVED, FIXED_TIMESTAMP));

            assertThat(captureSoleSavedRecord().getActorId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("falls back to system actor 0 when the token is not authenticated")
        void fallsBackToSystemActorWhenUnauthenticated() {
            stubSaveReturnsArgument();
            setUnauthenticatedPrincipal("42");

            ingestionService.ingestEvent(eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.APPROVED, FIXED_TIMESTAMP));

            assertThat(captureSoleSavedRecord().getActorId()).isEqualTo(SYSTEM_ACTOR_ID);
        }

        @Test
        @DisplayName("falls back to system actor 0 when the String principal is not a numeric id")
        void fallsBackToSystemActorForNonNumericPrincipal() {
            stubSaveReturnsArgument();
            setAuthenticatedPrincipal("system-scheduler");

            ingestionService.ingestEvent(eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.UPDATED, FIXED_TIMESTAMP));

            assertThat(captureSoleSavedRecord().getActorId()).isEqualTo(SYSTEM_ACTOR_ID);
        }

        @Test
        @DisplayName("falls back to system actor 0 for a non-String principal (custom principal object)")
        void fallsBackToSystemActorForNonStringPrincipal() {
            stubSaveReturnsArgument();
            // The impl only knows how to extract a Long id from a String principal; any other
            // principal type (e.g. a custom JWT principal object) falls through to the default
            setAuthenticatedPrincipal(new Object());

            ingestionService.ingestEvent(eventFor(TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.UPDATED, FIXED_TIMESTAMP));

            assertThat(captureSoleSavedRecord().getActorId()).isEqualTo(SYSTEM_ACTOR_ID);
        }

        private static void setAuthenticatedPrincipal(Object principal) {
            SecurityContext context = new SecurityContextImpl();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
            SecurityContextHolder.setContext(context);
        }

        private static void setUnauthenticatedPrincipal(Object principal) {
            SecurityContext context = new SecurityContextImpl();
            // Two-arg constructor with null credentials leaves the token unauthenticated
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
            SecurityContextHolder.setContext(context);
        }
    }

    @Nested
    @DisplayName("invalid events (Constitution §10)")
    class InvalidEvents {

        @Test
        @DisplayName("wraps an unparseable action in IllegalArgumentException and saves nothing")
        void throwsForUnknownAction() {
            DomainEventEnvelope event = new DomainEventEnvelope("TRAVEL_REQUEST", "123", "NOT_AN_ACTION", FIXED_TIMESTAMP);

            assertThatThrownBy(() -> ingestionService.ingestEvent(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid event format");

            verifyNoInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("wraps an unparseable entity type in IllegalArgumentException and saves nothing")
        void throwsForUnknownEntityType() {
            DomainEventEnvelope event = new DomainEventEnvelope("UNKNOWN_ENTITY", "123", "CREATED", FIXED_TIMESTAMP);

            assertThatThrownBy(() -> ingestionService.ingestEvent(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid event format");

            verifyNoInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("wraps a non-numeric entity id in IllegalArgumentException and saves nothing")
        void throwsForNonNumericEntityId() {
            DomainEventEnvelope event = new DomainEventEnvelope("TRAVEL_REQUEST", "not-a-number", "CREATED", FIXED_TIMESTAMP);

            assertThatThrownBy(() -> ingestionService.ingestEvent(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid event format")
                    .hasCauseInstanceOf(NumberFormatException.class);

            verifyNoInteractions(auditRecordRepository);
        }
    }

    @Test
    @DisplayName("surfaces an unexpected persistence failure to the event ingestion caller")
    void surfacesUnexpectedPersistenceFailure() {
        DomainEventEnvelope event = eventFor(
                TRAVEL_REQUEST, TRAVEL_REQUEST_ID, AuditActionType.CREATED, FIXED_TIMESTAMP);
        when(auditRecordRepository.save(any(AuditRecord.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> ingestionService.ingestEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to ingest audit event for entityId=123")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(auditRecordRepository).save(any(AuditRecord.class));
    }

    @Nested
    @DisplayName("outbox delivery (#12/#13): idempotency, actor and metadata from the envelope")
    class OutboxDelivery {

        private DomainEventEnvelope delivered(String eventId, Long actorId, java.util.Map<String, String> metadata) {
            return new DomainEventEnvelope("TRAVEL_REQUEST", "7", "APPROVED", Instant.parse("2026-10-08T15:00:00Z"),
                    List.of(), eventId, actorId, metadata);
        }

        @Test
        @DisplayName("a re-delivered event id is acknowledged without a second audit record")
        void duplicateEventIgnored() {
            when(auditRecordRepository.existsByEventId("evt-1")).thenReturn(true);

            ingestionService.ingestEvent(delivered("evt-1", 2L, java.util.Map.of()));

            verify(auditRecordRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("the producer-recorded actor wins over the (service) security context; event id and details are stored")
        void actorAndDetailsFromEnvelope() {
            stubSaveReturnsArgument();
            when(auditRecordRepository.existsByEventId("evt-2")).thenReturn(false);

            ingestionService.ingestEvent(delivered("evt-2", 2L,
                    java.util.Map.of("toApproverId", "6", "fromApproverId", "3")));

            AuditRecord saved = captureSoleSavedRecord();
            assertThat(saved.getActorId()).isEqualTo(2L);
            assertThat(saved.getEventId()).isEqualTo("evt-2");
            assertThat(saved.getDetails()).isEqualTo("fromApproverId=3; toApproverId=6");
        }
    }
}
