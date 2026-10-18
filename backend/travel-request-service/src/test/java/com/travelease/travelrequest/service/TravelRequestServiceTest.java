package com.travelease.travelrequest.service;

import com.travelease.common.error.ErrorResponse;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TravelRequestServiceImpl} (tasks.md L-02), covering
 * travel-request submission (FR-TR-01) and edit/resubmission (FR-TR-06, FR-TR-07)
 * with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>valid submission: the entity is saved as {@code PENDING} with the approver
 *       resolved synchronously from Admin Service (FR-TR-05, H-01), a
 *       {@code CREATED} domain event is published (Constitution §9) and the mapped
 *       response is returned (AC-01)</li>
 *   <li>submission with a past start date: the {@link TravelRequestPolicyValidator}
 *       {@code VAL-02} {@link ValidationException} propagates and nothing is
 *       persisted (AC-02)</li>
 *   <li>editing an {@code APPROVED} request: the {@code VAL-13}
 *       {@link ValidationException} (field {@code "status"} - the conflict
 *       equivalent for L-02) propagates and nothing is persisted (AC-02)</li>
 *   <li>editing a non-existent request → {@link ResourceNotFoundException}</li>
 *   <li>editing someone else's request → {@link ValidationException} (field
 *       {@code "ownership"}) before any state check runs</li>
 *   <li>editing a {@code PENDING} request: the update is saved and an
 *       {@code UPDATED} (not {@code RESUBMITTED}) event is published (AC-02, AC-13)</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so all nine collaborators are mocked with Mockito and the
 * service is injected via {@code @InjectMocks} - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TravelRequestServiceImpl submit/edit (tasks.md L-02; spec.md FR-TR-01, FR-TR-03, FR-TR-06, FR-TR-07; VAL-02, VAL-13; AC-01, AC-02, AC-13)")
class TravelRequestServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2027, 1, 15);
    private static final LocalDate END_DATE = LocalDate.of(2027, 1, 20);
    private static final String JUSTIFICATION = "Client on-site support for the quarterly business review";

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

    private static TravelRequestCreateRequest createDto() {
        return new TravelRequestCreateRequest("Mumbai", START_DATE, END_DATE, JUSTIFICATION, List.of());
    }

    private static TravelRequestUpdateRequest updateDto() {
        return new TravelRequestUpdateRequest("Mumbai", START_DATE, END_DATE, JUSTIFICATION, List.of());
    }

    private static TravelRequest entity(Long id, Long employeeId, TravelRequestStatus status) {
        TravelRequest entity = new TravelRequest();
        entity.setId(id);
        entity.setEmployeeId(employeeId);
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

    @Nested
    @DisplayName("submitTravelRequest (FR-TR-01, FR-TR-03, VAL-02)")
    class SubmitTravelRequest {

        @Test
        @DisplayName("saves the request as PENDING with the resolved approver, publishes a CREATED event and returns the mapped response")
        void submitsRequest() {
            TravelRequestCreateRequest dto = createDto();
            TravelRequest mapped = entity(10L, null, TravelRequestStatus.PENDING);
            TravelRequestResponse response = response(TravelRequestStatus.PENDING, "2");

            when(travelRequestMapper.toEntity(dto)).thenReturn(mapped);
            when(routingServiceClient.resolveApprover(1L)).thenReturn(2L);
            when(travelRequestRepository.save(mapped)).thenReturn(mapped);
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
            when(travelRequestMapper.toResponse(mapped, List.of())).thenReturn(response);

            TravelRequestResponse actual = travelRequestService.submitTravelRequest(1L, dto);

            assertThat(actual).isSameAs(response);
            assertThat(mapped.getEmployeeId()).isEqualTo(1L);
            assertThat(mapped.getStatus()).isEqualTo(TravelRequestStatus.PENDING);
            assertThat(mapped.getApproverId()).isEqualTo(2L);

            verify(routingServiceClient).resolveApprover(1L);
            verify(travelRequestRepository).save(mapped);

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("TRAVEL_REQUEST");
            assertThat(event.entityId()).isEqualTo("10");
            assertThat(event.action()).isEqualTo("CREATED");
            assertThat(event.recipientIds()).containsExactly(2L);
        }

        @Test
        @DisplayName("propagates the VAL-02 ValidationException for a past start date and never persists anything")
        void rejectsPastStartDate() {
            TravelRequestCreateRequest dto = createDto();
            doThrow(new ValidationException("startDate", "Travel start date cannot be in the past (VAL-02)"))
                    .when(policyValidator).validateTravelDates(dto.startDate(), dto.endDate());

            assertThatThrownBy(() -> travelRequestService.submitTravelRequest(1L, dto))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("startDate"));

            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
            verifyNoInteractions(routingServiceClient, travelRequestMapper);
        }
    }

    @Nested
    @DisplayName("submitTravelRequest — policy-mandated document enforcement (spec.md VAL-04, FR-TR-02)")
    class DocumentMandateEnforcement {

        @Test
        @DisplayName("rejects submission with a PolicyViolationException (field \"documentIds\") when the mandate is active and documentIds is null")
        void rejectsNullDocumentIdsWhenMandated() {
            when(travelRequestPolicyClient.documentsMandatory()).thenReturn(true);
            TravelRequestCreateRequest dto = new TravelRequestCreateRequest("Mumbai", START_DATE, END_DATE, JUSTIFICATION, null);

            assertThatThrownBy(() -> travelRequestService.submitTravelRequest(1L, dto))
                    .isInstanceOfSatisfying(PolicyViolationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("documentIds"));

            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
            verifyNoInteractions(routingServiceClient, travelRequestMapper, eventPublisher);
        }

        @Test
        @DisplayName("rejects submission with a PolicyViolationException (field \"documentIds\") when the mandate is active and documentIds is empty")
        void rejectsEmptyDocumentIdsWhenMandated() {
            when(travelRequestPolicyClient.documentsMandatory()).thenReturn(true);

            assertThatThrownBy(() -> travelRequestService.submitTravelRequest(1L, createDto()))
                    .isInstanceOfSatisfying(PolicyViolationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("documentIds"));

            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
            verifyNoInteractions(routingServiceClient, travelRequestMapper, eventPublisher);
        }

        @Test
        @DisplayName("accepts submission when the mandate is active and at least one document is provided")
        void acceptsDocumentIdsWhenMandated() {
            TravelRequestCreateRequest dto = new TravelRequestCreateRequest("Mumbai", START_DATE, END_DATE, JUSTIFICATION,
                    List.of("doc-1"));
            TravelRequest mapped = entity(10L, null, TravelRequestStatus.PENDING);
            TravelRequestResponse response = response(TravelRequestStatus.PENDING, "2");

            when(travelRequestPolicyClient.documentsMandatory()).thenReturn(true);
            when(travelRequestMapper.toEntity(dto)).thenReturn(mapped);
            when(routingServiceClient.resolveApprover(1L)).thenReturn(2L);
            when(travelRequestRepository.save(mapped)).thenReturn(mapped);
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
            when(travelRequestMapper.toResponse(mapped, List.of())).thenReturn(response);

            TravelRequestResponse actual = travelRequestService.submitTravelRequest(1L, dto);

            assertThat(actual).isSameAs(response);
            assertThat(mapped.getEmployeeId()).isEqualTo(1L);
            assertThat(mapped.getStatus()).isEqualTo(TravelRequestStatus.PENDING);
            assertThat(mapped.getApproverId()).isEqualTo(2L);

            verify(travelRequestPolicyClient).documentsMandatory();
            verify(routingServiceClient).resolveApprover(1L);
            verify(travelRequestRepository).save(mapped);

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("TRAVEL_REQUEST");
            assertThat(event.entityId()).isEqualTo("10");
            assertThat(event.action()).isEqualTo("CREATED");
        }
    }

    @Nested
    @DisplayName("editTravelRequest — valid edit of a PENDING request (FR-TR-06, AC-02)")
    class EditPendingRequest {

        @Test
        @DisplayName("saves the update and publishes an UPDATED event (not RESUBMITTED)")
        void editsPendingRequest() {
            TravelRequestUpdateRequest dto = updateDto();
            TravelRequest existing = entity(10L, 1L, TravelRequestStatus.PENDING);
            TravelRequestResponse response = response(TravelRequestStatus.PENDING, "2");

            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
            when(travelRequestRepository.save(existing)).thenReturn(existing);
            when(travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
            when(travelRequestMapper.toResponse(existing, List.of())).thenReturn(response);

            TravelRequestResponse actual = travelRequestService.editTravelRequest(10L, 1L, dto);

            assertThat(actual).isSameAs(response);
            assertThat(existing.getStatus()).isEqualTo(TravelRequestStatus.PENDING);
            assertThat(existing.getResubmissionOfId()).isNull();

            verify(policyValidator).validateEditable(TravelRequestStatus.PENDING);
            verify(travelRequestRepository).save(existing);
            verify(routingServiceClient, never()).resolveApprover(any());

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("TRAVEL_REQUEST");
            assertThat(event.entityId()).isEqualTo("10");
            assertThat(event.action()).isEqualTo("UPDATED");
            assertThat(event.recipientIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("editTravelRequest — negative cases (Constitution §10)")
    class EditNegativeCases {

        @Test
        @DisplayName("rejects editing an APPROVED request with a VAL-13 ValidationException (field \"status\") and never persists anything")
        void rejectsEditingApprovedRequest() {
            TravelRequestUpdateRequest dto = updateDto();
            TravelRequest existing = entity(10L, 1L, TravelRequestStatus.APPROVED);

            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
            doThrow(new ValidationException("status",
                            "Travel request can only be edited while PENDING or resubmitted while REJECTED. Current status: APPROVED"))
                    .when(policyValidator).validateEditable(TravelRequestStatus.APPROVED);

            assertThatThrownBy(() -> travelRequestService.editTravelRequest(10L, 1L, dto))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("status"));

            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
            verifyNoInteractions(routingServiceClient, travelRequestMapper);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the request does not exist or is soft-deleted")
        void throwsWhenRequestNotFound() {
            when(travelRequestRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
            TravelRequestUpdateRequest request = updateDto();

            assertThatThrownBy(() -> travelRequestService.editTravelRequest(999L, 1L, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(policyValidator, routingServiceClient);
            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
        }

        @Test
        @DisplayName("rejects editing someone else's request with a ValidationException (field \"ownership\") before any state check")
        void rejectsForeignOwnership() {
            TravelRequest existing = entity(10L, 99L, TravelRequestStatus.PENDING);
            when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> travelRequestService.editTravelRequest(10L, 1L, updateDto()))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("ownership"));

            verify(policyValidator, never()).validateEditable(any(TravelRequestStatus.class));
            verify(travelRequestRepository, never()).save(any(TravelRequest.class));
        }
    }
}