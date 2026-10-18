package com.travelease.travelrequest.service;

import com.travelease.common.error.ErrorResponse;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.travelrequest.dto.ApprovalDecisionRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
import com.travelease.travelrequest.entity.ApprovalDecision;
import com.travelease.travelrequest.entity.DecisionType;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.mapper.TravelRequestMapper;
import com.travelease.travelrequest.repository.ApprovalDecisionRepository;
import com.travelease.travelrequest.repository.TravelRequestDocumentRepository;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingServiceClient;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyClient;
import com.travelease.travelrequest.service.impl.TravelRequestServiceImpl;
import com.travelease.travelrequest.validation.TravelRequestPolicyValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TravelRequestServiceImpl#decideApproval} and the resubmission
 * branch of {@link TravelRequestServiceImpl#editTravelRequest} (tasks.md L-03), covering
 * travel-request approval, rejection and resubmission with positive and negative cases
 * per Constitution §10:
 *
 * <ul>
 *   <li>approving a {@code PENDING} request (FR-APR-01, AC-03): an
 *       {@link ApprovalDecision} record with decision {@code APPROVED} is saved, the
 *       request status becomes {@code APPROVED} with any prior {@code rejectionReason}
 *       cleared, an {@code APPROVED} domain event is published (Constitution §9) and the
 *       mapped response is returned</li>
 *   <li>rejecting a {@code PENDING} request with a reason (FR-APR-02, AC-04): the
 *       decision record stores the reason, the request status becomes {@code REJECTED}
 *       with the {@code rejectionReason} populated, a {@code REJECTED} event is published</li>
 *   <li>rejecting without a reason: the {@code VAL-12} {@link ValidationException}
 *       (field {@code "reason"}) is thrown for both {@code null} and blank reasons and
 *       nothing is persisted</li>
 *   <li>deciding a non-{@code PENDING} request: the {@link ValidationException}
 *       (field {@code "status"} - this codebase has no {@code ConflictException})
 *       propagates and nothing is persisted</li>
 *   <li>deciding a non-existent request → {@link ResourceNotFoundException}</li>
 *   <li>resubmitting a {@code REJECTED} request via {@code editTravelRequest}
 *       (FR-APR-03, AC-05): {@code resubmissionOfId} links the request to the rejected
 *       one, status resets to {@code PENDING}, the approver is re-resolved from Admin
 *       Service and a {@code RESUBMITTED} (not {@code UPDATED}) event is published</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so all nine collaborators are mocked with Mockito and the
 * {@link TravelRequestServiceImpl} is constructed via {@link InjectMocks} - mirroring
 * the conventions of {@code TravelRequestServiceTest} (tasks.md L-02).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TravelRequestServiceImpl approve/reject/resubmit (tasks.md L-03; spec.md FR-APR-01, FR-APR-02, FR-APR-03; VAL-12; AC-03, AC-04, AC-05)")
class TravelRequestApprovalServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2027, 1, 15);
    private static final LocalDate END_DATE = LocalDate.of(2027, 1, 20);
    private static final String JUSTIFICATION = "Client on-site support for the quarterly business review";
    private static final String REJECTION_REASON = "Budget not approved";

    @Mock
    private TravelRequestRepository travelRequestRepository;

    @Mock
    private TravelRequestDocumentRepository travelRequestDocumentRepository;

    @Mock
    private TravelRequestDocumentService travelRequestDocumentService;

    @Mock
    private ApprovalDecisionRepository approvalDecisionRepository;

    @Mock
    private TravelRequestMapper travelRequestMapper;

    @Mock
    private EmployeeManagerRoutingServiceClient routingServiceClient;

    @Mock
    private TravelRequestPolicyClient travelRequestPolicyClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TravelRequestPolicyValidator policyValidator;

    @InjectMocks
    private TravelRequestServiceImpl travelRequestService;

    private static TravelRequestUpdateRequest updateDto() {
        return new TravelRequestUpdateRequest("Mumbai", START_DATE, END_DATE, JUSTIFICATION, List.of());
    }

    private static TravelRequest entity(Long id, Long employeeId, TravelRequestStatus status) {
        TravelRequest entity = new TravelRequest();
        entity.setId(id);
        entity.setEmployeeId(employeeId);
        // Routed at submission to manager 2, the approver these decision tests act as.
        entity.setApproverId(2L);
        entity.setStatus(status);
        entity.setDestination("Mumbai");
        entity.setStartDate(START_DATE);
        entity.setEndDate(END_DATE);
        entity.setJustification(JUSTIFICATION);
        return entity;
    }

    private static TravelRequestResponse response(TravelRequestStatus status, String approverId) {
        return new TravelRequestResponse("10", "1", "Mumbai", START_DATE, END_DATE, JUSTIFICATION, status,
                approverId, null, null, List.of(), Instant.now(), Instant.now());
    }

    private ArgumentCaptor<DomainEventEnvelope> capturePublishedEvent() {
        ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        return eventCaptor;
    }

    @Nested
    @DisplayName("decideApproval — positive cases (FR-APR-01, FR-APR-02)")
    class DecideApprovalPositiveCases {

        @Test
        @DisplayName("approves a PENDING request: saves an APPROVED decision record, sets status APPROVED with a null rejectionReason and publishes an APPROVED event")
        void approvesPendingRequest() {
            TravelRequest existing = entity(10L, 1L, TravelRequestStatus.PENDING);
            TravelRequestResponse response = response(TravelRequestStatus.APPROVED, "2");

            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
            when(travelRequestRepository.save(existing)).thenReturn(existing);
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
            when(travelRequestMapper.toResponse(existing, List.of())).thenReturn(response);

            TravelRequestResponse actual = travelRequestService
                    .decideApproval(10L, 2L, new ApprovalDecisionRequest(DecisionType.APPROVED, null));

            assertThat(actual).isSameAs(response);
            assertThat(existing.getStatus()).isEqualTo(TravelRequestStatus.APPROVED);
            assertThat(existing.getRejectionReason()).isNull();

            ArgumentCaptor<ApprovalDecision> decisionCaptor = ArgumentCaptor.forClass(ApprovalDecision.class);
            verify(approvalDecisionRepository).save(decisionCaptor.capture());
            ApprovalDecision decision = decisionCaptor.getValue();
            assertThat(decision.getTravelRequest()).isSameAs(existing);
            assertThat(decision.getDecidedBy()).isEqualTo(2L);
            assertThat(decision.getDecision()).isEqualTo(DecisionType.APPROVED);
            assertThat(decision.getReason()).isNull();
            assertThat(decision.getDecidedAt()).isNotNull();

            verify(travelRequestRepository).save(existing);

            DomainEventEnvelope event = capturePublishedEvent().getValue();
            assertThat(event.entityType()).isEqualTo("TRAVEL_REQUEST");
            assertThat(event.entityId()).isEqualTo("10");
            assertThat(event.action()).isEqualTo("APPROVED");
            assertThat(event.recipientIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("rejects a PENDING request with a reason: saves the decision record with the reason, stores rejectionReason, sets status REJECTED and publishes a REJECTED event")
        void rejectsPendingRequestWithReason() {
            TravelRequest existing = entity(10L, 1L, TravelRequestStatus.PENDING);
            TravelRequestResponse response = response(TravelRequestStatus.REJECTED, "2");

            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
            when(travelRequestRepository.save(existing)).thenReturn(existing);
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
            when(travelRequestMapper.toResponse(existing, List.of())).thenReturn(response);

            TravelRequestResponse actual = travelRequestService
                    .decideApproval(10L, 2L, new ApprovalDecisionRequest(DecisionType.REJECTED, REJECTION_REASON));

            assertThat(actual).isSameAs(response);
            assertThat(existing.getStatus()).isEqualTo(TravelRequestStatus.REJECTED);
            assertThat(existing.getRejectionReason()).isEqualTo(REJECTION_REASON);

            ArgumentCaptor<ApprovalDecision> decisionCaptor = ArgumentCaptor.forClass(ApprovalDecision.class);
            verify(approvalDecisionRepository).save(decisionCaptor.capture());
            ApprovalDecision decision = decisionCaptor.getValue();
            assertThat(decision.getTravelRequest()).isSameAs(existing);
            assertThat(decision.getDecidedBy()).isEqualTo(2L);
            assertThat(decision.getDecision()).isEqualTo(DecisionType.REJECTED);
            assertThat(decision.getReason()).isEqualTo(REJECTION_REASON);
            assertThat(decision.getDecidedAt()).isNotNull();

            verify(travelRequestRepository).save(existing);

            DomainEventEnvelope event = capturePublishedEvent().getValue();
            assertThat(event.entityType()).isEqualTo("TRAVEL_REQUEST");
            assertThat(event.entityId()).isEqualTo("10");
            assertThat(event.action()).isEqualTo("REJECTED");
            assertThat(event.recipientIds()).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("decideApproval — negative cases (Constitution §10)")
    class DecideApprovalNegativeCases {

        @Test
        @DisplayName("rejects a REJECTED decision without a reason (VAL-12) with a ValidationException (field \"reason\") and never persists anything")
        void rejectsRejectionWithoutReason() {
            TravelRequest existing = entity(10L, 1L, TravelRequestStatus.PENDING);
            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));

            // VAL-12: reason is required and non-blank when the decision is REJECTED
            assertThatThrownBy(() -> travelRequestService
                    .decideApproval(10L, 2L, new ApprovalDecisionRequest(DecisionType.REJECTED, null)))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("reason"));

            // VAL-12 also covers blank reasons
            assertThatThrownBy(() -> travelRequestService
                    .decideApproval(10L, 2L, new ApprovalDecisionRequest(DecisionType.REJECTED, "   ")))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("reason"));

            verify(approvalDecisionRepository, never()).save(any(ApprovalDecision.class));
            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
        }

        @Test
        @DisplayName("rejects deciding an APPROVED (non-PENDING) request with a ValidationException (field \"status\") and never persists anything")
        void rejectsDecidingNonPendingRequest() {
            TravelRequest existing = entity(10L, 1L, TravelRequestStatus.APPROVED);
            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> travelRequestService
                    .decideApproval(10L, 2L, new ApprovalDecisionRequest(DecisionType.APPROVED, null)))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("status"));

            verify(approvalDecisionRepository, never()).save(any(ApprovalDecision.class));
            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the request does not exist or is soft-deleted")
        void throwsWhenRequestNotFound() {
            when(travelRequestRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
            ApprovalDecisionRequest decision = new ApprovalDecisionRequest(DecisionType.APPROVED, null);

            assertThatThrownBy(() -> travelRequestService
                    .decideApproval(999L, 2L, decision))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(approvalDecisionRepository, never()).save(any(ApprovalDecision.class));
            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
        }
    }

    @Nested
    @DisplayName("editTravelRequest — resubmission after rejection (FR-APR-03, AC-05)")
    class ResubmissionAfterRejection {

        @Test
        @DisplayName("resubmits a REJECTED request as a new linked PENDING cycle and publishes a RESUBMITTED event")
        void resubmitsRejectedRequest() {
            TravelRequestUpdateRequest dto = updateDto();
            TravelRequest existing = entity(5L, 1L, TravelRequestStatus.REJECTED);
            existing.setRejectionReason(REJECTION_REASON);
            TravelRequest nextCycle = new TravelRequest();
            nextCycle.setDestination(dto.destination());
            nextCycle.setStartDate(dto.startDate());
            nextCycle.setEndDate(dto.endDate());
            nextCycle.setJustification(dto.justification());
            TravelRequestResponse response = new TravelRequestResponse(
                    "6", "1", dto.destination(), START_DATE, END_DATE, JUSTIFICATION,
                    TravelRequestStatus.PENDING, "2", null, "5", List.of(), Instant.now(), Instant.now());

            when(travelRequestRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(existing));
            when(travelRequestMapper.toEntity(dto)).thenReturn(nextCycle);
            when(routingServiceClient.resolveApprover(1L)).thenReturn(2L);
            when(travelRequestRepository.save(nextCycle)).thenAnswer(invocation -> {
                TravelRequest saved = invocation.getArgument(0);
                saved.setId(6L);
                return saved;
            });
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(5L)).thenReturn(List.of());
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(6L)).thenReturn(List.of());
            when(travelRequestMapper.toResponse(nextCycle, List.of())).thenReturn(response);

            TravelRequestResponse actual = travelRequestService.editTravelRequest(5L, 1L, dto);

            assertThat(actual).isSameAs(response);
            assertThat(existing.getStatus()).isEqualTo(TravelRequestStatus.REJECTED);
            assertThat(existing.getRejectionReason()).isEqualTo(REJECTION_REASON);
            assertThat(existing.getResubmissionOfId()).isNull();
            assertThat(nextCycle.getId()).isEqualTo(6L);
            assertThat(nextCycle.getStatus()).isEqualTo(TravelRequestStatus.PENDING);
            assertThat(nextCycle.getResubmissionOfId()).isEqualTo(5L);
            assertThat(nextCycle.getApproverId()).isEqualTo(2L);

            verify(routingServiceClient).resolveApprover(1L);
            verify(travelRequestRepository).save(nextCycle);
            verify(travelRequestRepository, never()).save(existing);

            DomainEventEnvelope event = capturePublishedEvent().getValue();
            assertThat(event.entityType()).isEqualTo("TRAVEL_REQUEST");
            assertThat(event.entityId()).isEqualTo("6");
            assertThat(event.action()).isEqualTo("RESUBMITTED");
            assertThat(event.recipientIds()).containsExactly(2L);
        }
    }
}