package com.travelease.audit.service;

import com.travelease.audit.dto.AuditRecordResponse;
import com.travelease.audit.entity.AuditRecord;
import com.travelease.audit.repository.AuditRecordRepository;
import com.travelease.audit.service.impl.AuditQueryServiceImpl;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditQueryServiceImpl} (tasks.md L-08; spec.md FR-AUD-01; AC-14),
 * verifying that each read-only query filter delegates to exactly one paginated repository
 * finder with the exact arguments and returns the mapped {@link AuditRecordResponse} page:
 *
 * <ul>
 *   <li>{@code getAuditTrail(entityType, entityId, pageable)} →
 *       {@code findByEntityTypeAndEntityId}, returns the mapped subset</li>
 *   <li>{@code getAuditsByActor(actorId, pageable)} →
 *       {@code findByActorId}, returns the mapped subset</li>
 *   <li>{@code getAuditsByDateRange(from, to, pageable)} →
 *       {@code findByOccurredAtBetween}, returns the mapped subset</li>
 *   <li>empty repository pages propagate as empty (content-less) result pages without error</li>
 *   <li>negative cases per Constitution §10: null {@code Pageable} and an inverted
 *       date range ({@code from > to}) are rejected before any repository access</li>
 * </ul>
 *
 * <p>{@code AuditRecordMapper.toResponse} is a static pure converter (utility class with a
 * private constructor), so the real mapping runs as-is inside the service - no spy is
 * needed. The service is plain (non-Spring) business logic with a constructor-injected
 * repository, so the repository is mocked with Mockito and the service is injected via
 * {@code @InjectMocks} - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditQueryServiceImpl (FR-AUD-01, AC-14; tasks.md L-08)")
class AuditQueryServiceTest {

    private static final AuditEntityType TRAVEL_REQUEST = AuditEntityType.TRAVEL_REQUEST;
    private static final AuditEntityType EXPENSE_CLAIM = AuditEntityType.EXPENSE_CLAIM;
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);
    private static final Instant OCCURRED_AT_1 = Instant.parse("2025-06-15T09:30:00Z");
    private static final Instant OCCURRED_AT_2 = Instant.parse("2025-06-16T14:45:00Z");

    @Mock
    private AuditRecordRepository auditRecordRepository;

    @InjectMocks
    private AuditQueryServiceImpl queryService;

    private static AuditRecord createAuditRecord(long id, AuditEntityType entityType, long entityId, long actorId,
                                                 AuditActionType action, String beforeStatus, String afterStatus,
                                                 Instant occurredAt) {
        AuditRecord auditRecord = new AuditRecord();
        auditRecord.setId(id);
        auditRecord.setEntityType(entityType);
        auditRecord.setEntityId(entityId);
        auditRecord.setActorId(actorId);
        auditRecord.setAction(action);
        auditRecord.setBeforeStatus(beforeStatus);
        auditRecord.setAfterStatus(afterStatus);
        auditRecord.setOccurredAt(occurredAt);
        return auditRecord;
    }

    private static AuditRecordResponse response(AuditRecord auditRecord) {
        return new AuditRecordResponse(
                auditRecord.getId(),
                auditRecord.getEntityType(),
                auditRecord.getEntityId(),
                auditRecord.getActorId(),
                auditRecord.getAction(),
                auditRecord.getBeforeStatus(),
                auditRecord.getAfterStatus(),
                auditRecord.getOccurredAt(),
                auditRecord.getDetails());
    }

    @Test
    @DisplayName("returns all audit records through the paginated default viewer query")
    void returnsAllAuditRecords() {
        AuditRecord record = createAuditRecord(1L, TRAVEL_REQUEST, 101L, 42L,
                AuditActionType.CREATED, null, "DRAFT", OCCURRED_AT_1);
        when(auditRecordRepository.findAll(FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(record), FIRST_PAGE, 1));

        Page<AuditRecordResponse> result = queryService.getAllAudits(FIRST_PAGE);

        assertThat(result.getContent()).containsExactly(response(record));
        verify(auditRecordRepository).findAll(FIRST_PAGE);
    }

    @Nested
    @DisplayName("getAuditTrail - entity trail filter (FR-AUD-01, AC-14)")
    class GetAuditTrail {

        @Test
        @DisplayName("rejects a null page request before querying the repository")
        void rejectsNullPageable() {
            assertThatThrownBy(() -> queryService.getAuditTrail(TRAVEL_REQUEST, 101L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pageable must not be null");
            verifyNoInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("returns the mapped page for the entity and verifies the exact finder arguments")
        void returnsMappedPageForEntityTrail() {
            AuditRecord created = createAuditRecord(1L, TRAVEL_REQUEST, 101L, 42L,
                    AuditActionType.CREATED, null, "DRAFT", OCCURRED_AT_1);
            AuditRecord approved = createAuditRecord(2L, TRAVEL_REQUEST, 101L, 7L,
                    AuditActionType.APPROVED, "PENDING", "APPROVED", OCCURRED_AT_2);
            when(auditRecordRepository.findByEntityTypeAndEntityId(TRAVEL_REQUEST, 101L, FIRST_PAGE))
                    .thenReturn(new PageImpl<>(List.of(created, approved), FIRST_PAGE, 2));

            Page<AuditRecordResponse> result = queryService.getAuditTrail(TRAVEL_REQUEST, 101L, FIRST_PAGE);

            assertThat(result.getNumber()).isZero();
            assertThat(result.getSize()).isEqualTo(20);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .containsExactly(response(created), response(approved));

            verify(auditRecordRepository).findByEntityTypeAndEntityId(TRAVEL_REQUEST, 101L, FIRST_PAGE);
            verifyNoMoreInteractions(auditRecordRepository);
        }
    }

    @Nested
    @DisplayName("getAuditsByActor - actor filter (FR-AUD-01)")
    class GetAuditsByActor {

        @Test
        @DisplayName("rejects a null page request before querying the repository")
        void rejectsNullPageable() {
            assertThatThrownBy(() -> queryService.getAuditsByActor(42L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pageable must not be null");
            verifyNoInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("returns the mapped page for the actor and verifies the exact finder arguments")
        void returnsMappedPageForActor() {
            AuditRecord approvedRequest = createAuditRecord(10L, TRAVEL_REQUEST, 101L, 42L,
                    AuditActionType.APPROVED, "PENDING", "APPROVED", OCCURRED_AT_1);
            AuditRecord reimbursedClaim = createAuditRecord(11L, EXPENSE_CLAIM, 202L, 42L,
                    AuditActionType.REIMBURSED, "APPROVED", "REIMBURSED", OCCURRED_AT_2);
            when(auditRecordRepository.findByActorId(42L, FIRST_PAGE))
                    .thenReturn(new PageImpl<>(List.of(approvedRequest, reimbursedClaim), FIRST_PAGE, 2));

            Page<AuditRecordResponse> result = queryService.getAuditsByActor(42L, FIRST_PAGE);

            assertThat(result.getNumber()).isZero();
            assertThat(result.getSize()).isEqualTo(20);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .containsExactly(response(approvedRequest), response(reimbursedClaim));

            verify(auditRecordRepository).findByActorId(42L, FIRST_PAGE);
            verifyNoMoreInteractions(auditRecordRepository);
        }

        }

    @Nested
    @DisplayName("getAuditsByDateRange - date range filter (FR-AUD-01, AC-14)")
    class GetAuditsByDateRange {

        private static final Instant RANGE_FROM = Instant.parse("2025-06-01T00:00:00Z");
        private static final Instant RANGE_TO = Instant.parse("2025-06-30T23:59:59Z");

        @Test
        @DisplayName("rejects a null page request before validating the date range")
        void rejectsNullPageable() {
            assertThatThrownBy(() -> queryService.getAuditsByDateRange(RANGE_FROM, RANGE_TO, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pageable must not be null");
            verifyNoInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("returns the mapped page inside the range and verifies the exact finder arguments")
        void returnsMappedPageForDateRange() {
            AuditRecord first = createAuditRecord(20L, TRAVEL_REQUEST, 101L, 42L,
                    AuditActionType.UPDATED, null, "PENDING", OCCURRED_AT_1);
            AuditRecord second = createAuditRecord(21L, EXPENSE_CLAIM, 202L, 7L,
                    AuditActionType.CREATED, null, "DRAFT", OCCURRED_AT_2);
            when(auditRecordRepository.findByOccurredAtBetween(RANGE_FROM, RANGE_TO, FIRST_PAGE))
                    .thenReturn(new PageImpl<>(List.of(first, second), FIRST_PAGE, 2));

            Page<AuditRecordResponse> result = queryService.getAuditsByDateRange(RANGE_FROM, RANGE_TO, FIRST_PAGE);

            assertThat(result.getNumber()).isZero();
            assertThat(result.getSize()).isEqualTo(20);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactly(response(first), response(second));

            verify(auditRecordRepository).findByOccurredAtBetween(RANGE_FROM, RANGE_TO, FIRST_PAGE);
            verifyNoMoreInteractions(auditRecordRepository);
        }

        @Test
        @DisplayName("rejects an inverted range (from > to) without touching the repository")
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> queryService.getAuditsByDateRange(RANGE_TO, RANGE_FROM, FIRST_PAGE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("from must be <= to");

            verifyNoInteractions(auditRecordRepository);
        }
    }

    @Nested
    @DisplayName("empty results")
    class EmptyResults {

        @Test
        @DisplayName("an empty repository page is returned as an empty, content-less page without error")
        void returnsEmptyPageWhenNoRecordsMatch() {
            when(auditRecordRepository.findByEntityTypeAndEntityId(EXPENSE_CLAIM, 999L, FIRST_PAGE))
                    .thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            Page<AuditRecordResponse> result = queryService.getAuditTrail(EXPENSE_CLAIM, 999L, FIRST_PAGE);

            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.hasContent()).isFalse();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verify(auditRecordRepository).findByEntityTypeAndEntityId(EXPENSE_CLAIM, 999L, FIRST_PAGE);
            verifyNoMoreInteractions(auditRecordRepository);
        }
    }
}