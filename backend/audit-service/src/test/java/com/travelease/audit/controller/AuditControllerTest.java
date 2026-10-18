package com.travelease.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.travelease.audit.dto.AuditRecordResponse;
import com.travelease.audit.service.AuditQueryService;
import com.travelease.common.dto.PagedResponse;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.exception.ValidationException;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final Page<AuditRecordResponse> RESULTS = new PageImpl<>(java.util.List.of());

    private static final org.springframework.security.core.Authentication ADMIN =
            new org.springframework.security.authentication.TestingAuthenticationToken("1", null, "ROLE_ADMIN");
    private static final org.springframework.security.core.Authentication FINANCE =
            new org.springframework.security.authentication.TestingAuthenticationToken("2", null, "ROLE_FINANCE");

    @Mock
    private AuditQueryService auditQueryService;

    @InjectMocks
    private AuditController auditController;

    @Test
    void queriesByEntityWhenEntityFilterIsComplete() {
        when(auditQueryService.getAuditTrail(AuditEntityType.TRAVEL_REQUEST, 12L, PAGE))
                .thenReturn(RESULTS);

        ResponseEntity<PagedResponse<AuditRecordResponse>> response = auditController.queryAuditRecords(
                Optional.of(AuditEntityType.TRAVEL_REQUEST), Optional.of(12L), Optional.of(7L),
                Optional.of(Instant.parse("2026-01-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-01-02T00:00:00Z")), PAGE, ADMIN);

        assertThat(response.getBody()).isEqualTo(PagedResponse.from(RESULTS));
        verify(auditQueryService).getAuditTrail(AuditEntityType.TRAVEL_REQUEST, 12L, PAGE);
    }

    @Test
    void givesEntityFilterPrecedenceOverActorAndDateFilters() {
        when(auditQueryService.getAuditTrail(AuditEntityType.EXPENSE_CLAIM, 22L, PAGE))
                .thenReturn(RESULTS);

        auditController.queryAuditRecords(
                Optional.of(AuditEntityType.EXPENSE_CLAIM), Optional.of(22L), Optional.of(7L),
                Optional.of(Instant.parse("2026-01-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-01-02T00:00:00Z")), PAGE, ADMIN);

        verify(auditQueryService).getAuditTrail(AuditEntityType.EXPENSE_CLAIM, 22L, PAGE);
        verifyNoMoreInteractions(auditQueryService);
    }

    @Test
    void queriesByActorWhenEntityFilterIsIncomplete() {
        when(auditQueryService.getAuditsByActor(7L, PAGE)).thenReturn(RESULTS);

        ResponseEntity<PagedResponse<AuditRecordResponse>> response = auditController.queryAuditRecords(
                Optional.of(AuditEntityType.TRAVEL_REQUEST), Optional.empty(), Optional.of(7L),
                Optional.empty(), Optional.empty(), PAGE, ADMIN);

        assertThat(response.getBody()).isEqualTo(PagedResponse.from(RESULTS));
        verify(auditQueryService).getAuditsByActor(7L, PAGE);
    }

    @Test
    void queriesByDateRangeWhenDateFilterIsComplete() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        when(auditQueryService.getAuditsByDateRange(from, to, PAGE)).thenReturn(RESULTS);

        ResponseEntity<PagedResponse<AuditRecordResponse>> response = auditController.queryAuditRecords(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(from), Optional.of(to), PAGE, ADMIN);

        assertThat(response.getBody()).isEqualTo(PagedResponse.from(RESULTS));
        verify(auditQueryService).getAuditsByDateRange(from, to, PAGE);
    }

    @Test
    void returnsAllRecordsWhenNoFiltersAreProvided() {
        when(auditQueryService.getAllAudits(PAGE)).thenReturn(RESULTS);

        ResponseEntity<PagedResponse<AuditRecordResponse>> response = auditController.queryAuditRecords(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), PAGE, ADMIN);

        assertThat(response.getBody()).isEqualTo(PagedResponse.from(RESULTS));
        verify(auditQueryService).getAllAudits(PAGE);
    }

    @Test
    void rejectsIncompleteDateRange() {
        assertThatThrownBy(() -> auditController.queryAuditRecords(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Instant.parse("2026-01-01T00:00:00Z")), Optional.empty(), PAGE, ADMIN))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Both from and to");
        verifyNoInteractions(auditQueryService);
    }

    @Test
    void financeSeesOnlyWorkflowRecordsInListings() {
        java.util.Set<AuditEntityType> workflow = java.util.EnumSet.of(AuditEntityType.TRAVEL_REQUEST,
                AuditEntityType.EXPENSE_CLAIM);
        when(auditQueryService.getAllAudits(PAGE, workflow)).thenReturn(RESULTS);
        when(auditQueryService.getAuditsByActor(7L, PAGE, workflow)).thenReturn(RESULTS);

        auditController.queryAuditRecords(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), PAGE, FINANCE);
        auditController.queryAuditRecords(Optional.empty(), Optional.empty(), Optional.of(7L), Optional.empty(),
                Optional.empty(), PAGE, FINANCE);

        verify(auditQueryService).getAllAudits(PAGE, workflow);
        verify(auditQueryService).getAuditsByActor(7L, PAGE, workflow);
        verifyNoMoreInteractions(auditQueryService);
    }

    @Test
    void financeCannotQueryAdministrativeRecords() {
        assertThatThrownBy(() -> auditController.queryAuditRecords(Optional.of(AuditEntityType.USER_ACCOUNT),
                Optional.of(3L), Optional.empty(), Optional.empty(), Optional.empty(), PAGE, FINANCE))
                .isInstanceOf(com.travelease.common.exception.ForbiddenActionException.class);
        verifyNoInteractions(auditQueryService);
    }

    @Test
    void adminCanQueryAdministrativeRecords() {
        when(auditQueryService.getAuditTrail(AuditEntityType.USER_ACCOUNT, 3L, PAGE)).thenReturn(RESULTS);

        auditController.queryAuditRecords(Optional.of(AuditEntityType.USER_ACCOUNT), Optional.of(3L),
                Optional.empty(), Optional.empty(), Optional.empty(), PAGE, ADMIN);

        verify(auditQueryService).getAuditTrail(AuditEntityType.USER_ACCOUNT, 3L, PAGE);
    }
}
