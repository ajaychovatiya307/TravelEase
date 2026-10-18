package com.travelease.travelrequest.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.travelease.common.security.Roles;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.mapper.TravelRequestMapper;
import com.travelease.travelrequest.repository.TravelRequestDocumentRepository;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.impl.TravelRequestServiceImpl;

@ExtendWith(MockitoExtension.class)
class TravelRequestServiceCoverageTest {

    private static final PageRequest PAGE = PageRequest.of(0, 10);

    @Mock
    private TravelRequestRepository travelRequestRepository;
    @Mock
    private TravelRequestDocumentRepository documentRepository;
    @Mock
    private TravelRequestDocumentService documentService;
    @Mock
    private TravelRequestMapper mapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private TravelRequestServiceImpl service;

    @Test
    void getsRequestForEmployeeOwner() {
        TravelRequest request = request(10L, 1L, 2L);
        TravelRequestResponse response = response();
        when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(request));
        when(documentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
        when(mapper.toResponse(request, List.of())).thenReturn(response);

        service.getTravelRequest(10L, 1L, Set.of(Roles.EMPLOYEE));

        verify(mapper).toResponse(request, List.of());
    }

    @Test
    void getsRequestForAssignedManager() {
        TravelRequest request = request(10L, 1L, 2L);
        when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(request));
        when(documentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
        when(mapper.toResponse(request, List.of())).thenReturn(response());

        service.getTravelRequest(10L, 2L, Set.of(Roles.MANAGER));

        verify(mapper).toResponse(request, List.of());
    }

    @Test
    void getsRequestForFinance() {
        TravelRequest request = request(10L, 1L, 2L);
        when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(request));
        when(documentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
        when(mapper.toResponse(request, List.of())).thenReturn(response());

        service.getTravelRequest(10L, 99L, Set.of(Roles.FINANCE));

        verify(mapper).toResponse(request, List.of());
    }

    @Test
    void rejectsManagerViewingUnassignedRequest() {
        TravelRequest request = request(10L, 1L, 2L);

        when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(this::getUnassignedManagerRequest)
                .isInstanceOf(RuntimeException.class);
        verify(documentRepository, never()).findByTravelRequestIdAndDeletedFalse(10L);
    }

    @Test
    void rejectsEmployeeViewingAnotherEmployeesRequest() {
        TravelRequest request = request(10L, 1L, 2L);

        when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(this::getAnotherEmployeesRequest)
                .isInstanceOf(RuntimeException.class);
        verify(documentRepository, never()).findByTravelRequestIdAndDeletedFalse(10L);
    }

    private void getUnassignedManagerRequest() {
        service.getTravelRequest(10L, 99L, Set.of(Roles.MANAGER));
    }

    private void getAnotherEmployeesRequest() {
        service.getTravelRequest(10L, 99L, Set.of(Roles.EMPLOYEE));
    }

    @Test
    void listsEmployeeRequestsWithoutStatusFilter() {
        TravelRequest request = request(10L, 1L, 2L);
        when(travelRequestRepository.findByEmployeeIdAndDeletedFalse(1L, PAGE))
                .thenReturn(new PageImpl<>(List.of(request), PAGE, 1));
        when(documentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
        when(mapper.toResponse(request, List.of())).thenReturn(response());

        service.listTravelRequests(1L, Set.of(Roles.EMPLOYEE), null, PAGE);

        verify(travelRequestRepository).findByEmployeeIdAndDeletedFalse(1L, PAGE);
    }

    @Test
    void listsManagerRequestsWithStatusFilter() {
        TravelRequest request = request(10L, 1L, 2L);
        when(travelRequestRepository.findByApproverIdAndStatusAndDeletedFalse(
                2L, TravelRequestStatus.PENDING, PAGE))
                .thenReturn(new PageImpl<>(List.of(request), PAGE, 1));
        when(documentRepository.findByTravelRequestIdAndDeletedFalse(10L)).thenReturn(List.of());
        when(mapper.toResponse(request, List.of())).thenReturn(response());

        service.listTravelRequests(2L, Set.of(Roles.MANAGER), TravelRequestStatus.PENDING, PAGE);

        verify(travelRequestRepository).findByApproverIdAndStatusAndDeletedFalse(
                2L, TravelRequestStatus.PENDING, PAGE);
    }

    @Test
    void listsAssignedRequestsForUserWithEmployeeAndManagerRoles() {
        when(travelRequestRepository.findByApproverIdAndStatusAndDeletedFalse(
                2L, TravelRequestStatus.PENDING, PAGE))
                .thenReturn(Page.empty());

        service.listTravelRequests(2L, Set.of(Roles.EMPLOYEE, Roles.MANAGER),
                TravelRequestStatus.PENDING, PAGE);

        verify(travelRequestRepository).findByApproverIdAndStatusAndDeletedFalse(
                2L, TravelRequestStatus.PENDING, PAGE);
        verify(travelRequestRepository, never()).findByEmployeeIdAndStatusAndDeletedFalse(
                2L, TravelRequestStatus.PENDING, PAGE);
    }

    @Test
    void listsFinanceRequestsWithoutStatusFilter() {
        when(travelRequestRepository.findByDeletedFalse(PAGE))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        service.listTravelRequests(9L, Set.of(Roles.FINANCE), null, PAGE);

        verify(travelRequestRepository).findByDeletedFalse(PAGE);
    }

    @Test
    void listsEmployeeRequestsWithStatusFilter() {
        when(travelRequestRepository.findByEmployeeIdAndStatusAndDeletedFalse(
                1L, TravelRequestStatus.PENDING, PAGE))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        service.listTravelRequests(1L, Set.of(Roles.EMPLOYEE), TravelRequestStatus.PENDING, PAGE);

        verify(travelRequestRepository).findByEmployeeIdAndStatusAndDeletedFalse(
                1L, TravelRequestStatus.PENDING, PAGE);
    }

    @Test
    void listsManagerRequestsWithoutStatusFilter() {
        when(travelRequestRepository.findByApproverIdAndDeletedFalse(2L, PAGE))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        service.listTravelRequests(2L, Set.of(Roles.MANAGER), null, PAGE);

        verify(travelRequestRepository).findByApproverIdAndDeletedFalse(2L, PAGE);
    }

    @Test
    void listsAdminRequestsWithStatusFilter() {
        when(travelRequestRepository.findByStatusAndDeletedFalse(
                TravelRequestStatus.PENDING, PAGE))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        service.listTravelRequests(9L, Set.of(Roles.ADMIN), TravelRequestStatus.PENDING, PAGE);

        verify(travelRequestRepository).findByStatusAndDeletedFalse(
                TravelRequestStatus.PENDING, PAGE);
    }

    @Test
    void rejectsListingWithoutAnAllowedRole() {
        Set<String> noRoles = Set.of();
        assertThatThrownBy(() -> service.listTravelRequests(9L, noRoles, null, PAGE))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void uploadsDocumentForOwner() {
        TravelRequest request = request(10L, 1L, 2L);
        DocumentSummary summary = new DocumentSummary(
                "1", "file.pdf", null, 10L, "ref", null);
        when(travelRequestRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(request));
        when(documentService.uploadDocument(request, "file.pdf", "PDF", new byte[] {1}))
                .thenReturn(summary);

        service.uploadDocument(10L, 1L, "file.pdf", "PDF", new byte[] {1});

        verify(documentService).uploadDocument(request, "file.pdf", "PDF", new byte[] {1});
    }

    private static TravelRequest request(Long id, Long employeeId, Long approverId) {
        TravelRequest request = new TravelRequest();
        request.setId(id);
        request.setEmployeeId(employeeId);
        request.setApproverId(approverId);
        request.setStatus(TravelRequestStatus.PENDING);
        return request;
    }

    private static TravelRequestResponse response() {
        return new TravelRequestResponse(
                "10", "1", "Destination", LocalDate.now(), LocalDate.now().plusDays(1),
                "Justification", TravelRequestStatus.PENDING, "2", null, null,
                List.of(), Instant.now(), Instant.now());
    }
}
