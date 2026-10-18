package com.travelease.travelrequest.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.common.dto.DocumentContent;
import com.travelease.common.dto.PagedResponse;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.security.Roles;
import com.travelease.common.storage.StorageException;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
import com.travelease.travelrequest.dto.ApprovalDecisionRequest;
import com.travelease.travelrequest.entity.ApprovalDecision;
import com.travelease.travelrequest.entity.DecisionType;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestDocument;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.mapper.TravelRequestMapper;
import com.travelease.travelrequest.repository.ApprovalDecisionRepository;
import com.travelease.travelrequest.repository.TravelRequestDocumentRepository;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.TravelRequestDocumentService;
import com.travelease.travelrequest.service.TravelRequestListScope;
import com.travelease.travelrequest.service.TravelRequestService;
import com.travelease.travelrequest.validation.TravelRequestPolicyValidator;

/**
 * Travel Request service implementation (tasks.md H-02, H-03).
 * 
 * <p>Provides core business logic for travel request submission, editing, approval,
 * and resubmission workflows. All state-changing methods are transactional and emit
 * domain events post-commit (Constitution §9).</p>
 */
@Service
public class TravelRequestServiceImpl implements TravelRequestService {

    private static final String TRAVEL_REQUEST_ENTITY_TYPE = "TRAVEL_REQUEST";

    /** 403 message when the caller is not the request's assigned approver (AC-03). */
    static final String NOT_ASSIGNED_APPROVER_MESSAGE = "Only the assigned approver can decide travel request approvals";

    private final TravelRequestRepository travelRequestRepository;
    private final TravelRequestDocumentRepository travelRequestDocumentRepository;
    private final TravelRequestDocumentService travelRequestDocumentService;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final TravelRequestMapper travelRequestMapper;
    private final EmployeeManagerRoutingServiceClient routingServiceClient;
    private final TravelRequestPolicyClient travelRequestPolicyClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TravelRequestPolicyValidator policyValidator;

    public TravelRequestServiceImpl(
            TravelRequestRepository travelRequestRepository,
            TravelRequestDocumentRepository travelRequestDocumentRepository,
            TravelRequestDocumentService travelRequestDocumentService,
            ApprovalDecisionRepository approvalDecisionRepository,
            TravelRequestMapper travelRequestMapper,
            EmployeeManagerRoutingServiceClient routingServiceClient,
            TravelRequestPolicyClient travelRequestPolicyClient,
            ApplicationEventPublisher eventPublisher,
            TravelRequestPolicyValidator policyValidator) {
        this.travelRequestRepository = travelRequestRepository;
        this.travelRequestDocumentRepository = travelRequestDocumentRepository;
        this.travelRequestDocumentService = travelRequestDocumentService;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.travelRequestMapper = travelRequestMapper;
        this.routingServiceClient = routingServiceClient;
        this.travelRequestPolicyClient = travelRequestPolicyClient;
        this.eventPublisher = eventPublisher;
        this.policyValidator = policyValidator;
    }

    @Override
    @Transactional
    public TravelRequestResponse submitTravelRequest(Long employeeId, TravelRequestCreateRequest dto) {
        // VAL-02 business: start date not in past, date ordering (J-04)
        policyValidator.validateTravelDates(dto.startDate(), dto.endDate());

        // VAL-04 business: check if policy mandates documents
        validatePolicyMandatedDocuments(dto.documentIds());

        // Map DTO to entity and set initial state
        TravelRequest entity = travelRequestMapper.toEntity(dto);
        entity.setEmployeeId(employeeId);
        entity.setStatus(TravelRequestStatus.PENDING);

        // H-01: Synchronously resolve approver from Admin Service
        Long approverId = routingServiceClient.resolveApprover(employeeId);
        entity.setApproverId(approverId);

        // Persist the new request
        TravelRequest saved = travelRequestRepository.save(entity);

        // Emit domain event (FR-AUD-01, FR-NOT-01)
        publishEvent(saved.getId(), TRAVEL_REQUEST_ENTITY_TYPE, "CREATED", List.of(approverId));

        // Fetch documents (if any were referenced in dto) and map to response
        List<TravelRequestDocument> documents = travelRequestDocumentRepository
                .findByTravelRequestIdAndDeletedFalse(saved.getId());

        return travelRequestMapper.toResponse(saved, documents);
    }

    @Override
    @Transactional
    public TravelRequestResponse editTravelRequest(Long id, Long employeeId, TravelRequestUpdateRequest dto) {
        // Load request and verify it exists/not soft-deleted
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Travel request not found: " + id));

        // Ownership check
        if (!entity.getEmployeeId().equals(employeeId)) {
            throw new ValidationException("ownership", "You can only edit your own travel requests");
        }

        // VAL-13: Edit allowed only when Pending or Rejected (resubmission, AC-05) (J-04)
        policyValidator.validateEditable(entity.getStatus());
        boolean isResubmission = entity.getStatus() == TravelRequestStatus.REJECTED;

        // VAL-02 business: validate dates again (J-04)
        policyValidator.validateTravelDates(dto.startDate(), dto.endDate());

        // VAL-04 business: validate documents if policy mandates
        validatePolicyMandatedDocuments(dto.documentIds());

        TravelRequest saved;
        if (isResubmission) {
            // Keep the rejected cycle and its decisions immutable; a resubmit is
            // a new request linked to the rejected request, not an in-place reset.
            TravelRequest nextCycle = travelRequestMapper.toEntity(dto);
            nextCycle.setEmployeeId(employeeId);
            nextCycle.setStatus(TravelRequestStatus.PENDING);
            nextCycle.setResubmissionOfId(id);
            nextCycle.setApproverId(routingServiceClient.resolveApprover(employeeId));
            saved = travelRequestRepository.save(nextCycle);
            copyDocumentsToResubmission(entity, saved);
        } else {
            travelRequestMapper.updateEntity(dto, entity);
            saved = travelRequestRepository.save(entity);
        }

        // Emit domain event (RESUBMITTED if rejecting a REJECTED request, else UPDATED)
        String eventAction = isResubmission ? "RESUBMITTED" : "UPDATED";
        List<Long> recipientIds = isResubmission ? List.of(saved.getApproverId()) : List.of();
        publishEvent(saved.getId(), TRAVEL_REQUEST_ENTITY_TYPE, eventAction, recipientIds);

        // Fetch documents and map to response
        List<TravelRequestDocument> documents = travelRequestDocumentRepository
                .findByTravelRequestIdAndDeletedFalse(saved.getId());

        return travelRequestMapper.toResponse(saved, documents);
    }

    private void copyDocumentsToResubmission(TravelRequest rejected, TravelRequest resubmission) {
        List<TravelRequestDocument> originalDocuments =
                travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(rejected.getId());
        if (originalDocuments.isEmpty()) {
            return;
        }

        List<TravelRequestDocument> copiedDocuments = originalDocuments.stream()
                .map(document -> {
                    TravelRequestDocument copy = new TravelRequestDocument();
                    copy.setTravelRequest(resubmission);
                    copy.setFileName(document.getFileName());
                    copy.setFileType(document.getFileType());
                    copy.setFileSizeBytes(document.getFileSizeBytes());
                    copy.setStorageReference(document.getStorageReference());
                    copy.setUploadStatus(document.getUploadStatus());
                    return copy;
                })
                .toList();
        travelRequestDocumentRepository.saveAll(copiedDocuments);
    }

    @Override
    @Transactional
    public TravelRequestResponse decideApproval(Long id, Long approverId, 
            ApprovalDecisionRequest dto) {
        // Load request and verify it exists/not soft-deleted
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Travel request not found: " + id));

        // AC-03 / NFR-10: only the approver assigned at submission (direct manager, or the Finance
        // user chosen by the top-of-hierarchy fallback) may decide, never the requester themselves.
        authorizeApprovalDecision(entity, approverId);

        // Validate request is in PENDING status for approval
        if (entity.getStatus() != TravelRequestStatus.PENDING) {
            throw new ValidationException(
                    "status",
                    "Travel request must be in PENDING status to approve/reject. Current status: " + entity.getStatus()
            );
        }

        // VAL-12: Reason is required and non-blank when decision is REJECTED
        if (dto.decision() == DecisionType.REJECTED
                && (dto.reason() == null || dto.reason().isBlank())) {
            throw new ValidationException(
                    "reason",
                    "Rejection reason is required and must be non-blank (VAL-12)"
            );
        }

        // Create approval decision record
        ApprovalDecision decision = new ApprovalDecision();
        decision.setTravelRequest(entity);
        decision.setDecidedBy(approverId);
        decision.setDecision(dto.decision());
        decision.setReason(dto.reason());  // Will be null for APPROVED, populated for REJECTED
        decision.setDecidedAt(java.time.Instant.now());

        // Save the decision record
        approvalDecisionRepository.save(decision);

        // Update travel request status based on decision
        if (dto.decision() == DecisionType.APPROVED) {
            entity.setStatus(TravelRequestStatus.APPROVED);
            entity.setRejectionReason(null);  // Clear any prior rejection reason
        } else {  // REJECTED
            entity.setStatus(TravelRequestStatus.REJECTED);
            entity.setRejectionReason(dto.reason());
        }

        // Persist the updated request
        TravelRequest saved = travelRequestRepository.save(entity);

        // Emit domain event
        String eventAction = dto.decision() == DecisionType.APPROVED 
                ? "APPROVED" 
                : "REJECTED";
        publishEvent(saved.getId(), TRAVEL_REQUEST_ENTITY_TYPE, eventAction, List.of(entity.getEmployeeId()));

        // Fetch documents and map to response
        List<TravelRequestDocument> documents = travelRequestDocumentRepository
                .findByTravelRequestIdAndDeletedFalse(saved.getId());

        return travelRequestMapper.toResponse(saved, documents);
    }

    @Override
    @Transactional(readOnly = true)
    public TravelRequestResponse getTravelRequest(Long id, Long requesterId, Set<String> requesterRoles) {
        // Load request and verify it exists / not soft-deleted (NFR-04)
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("TravelRequest", id));

        // Enforce role-based visibility (api-contract.md §4.1 GET /{id})
        assertRequestVisible(entity, requesterId, requesterRoles);

        // Fetch documents and map to response
        List<TravelRequestDocument> documents = travelRequestDocumentRepository
                .findByTravelRequestIdAndDeletedFalse(entity.getId());

        return travelRequestMapper.toResponse(entity, documents);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TravelRequestResponse> listTravelRequests(Long requesterId, Set<String> requesterRoles,
            TravelRequestStatus status, Pageable pageable) {
        return listTravelRequests(requesterId, requesterRoles, null, status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TravelRequestResponse> listTravelRequests(Long requesterId, Set<String> requesterRoles,
            TravelRequestListScope scope, TravelRequestStatus status, Long approverIdFilter, Pageable pageable) {
        if (approverIdFilter == null) {
            return listTravelRequests(requesterId, requesterRoles, scope, status, pageable);
        }
        Set<String> roles = requesterRoles == null ? Set.of() : requesterRoles;
        if (scope != TravelRequestListScope.ALL || (!roles.contains(Roles.FINANCE) && !roles.contains(Roles.ADMIN))) {
            throw new ForbiddenActionException("filter travel requests by approver", "FINANCE or ADMIN (scope ALL)");
        }
        Page<TravelRequest> page = status != null
                ? travelRequestRepository.findByApproverIdAndStatusAndDeletedFalse(approverIdFilter, status, pageable)
                : travelRequestRepository.findByApproverIdAndDeletedFalse(approverIdFilter, pageable);
        return PagedResponse.from(toResponses(page));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TravelRequestResponse> listTravelRequests(Long requesterId, Set<String> requesterRoles,
            TravelRequestListScope scope, TravelRequestStatus status, Pageable pageable) {
        Set<String> roles = requesterRoles == null ? Set.of() : requesterRoles;
        boolean hasStatusFilter = status != null;

        if (scope != null) {
            return PagedResponse.from(toResponses(findForScope(scope, requesterId, roles, status, pageable)));
        }

        // Scope by caller role (api-contract.md §4.1 GET list):
        // EMPLOYEE -> own requests, MANAGER -> approval queue, FINANCE/ADMIN -> all
        Page<TravelRequest> page;
        if (roles.contains(Roles.MANAGER)) {
            page = hasStatusFilter
                    ? travelRequestRepository.findByApproverIdAndStatusAndDeletedFalse(requesterId, status, pageable)
                    : travelRequestRepository.findByApproverIdAndDeletedFalse(requesterId, pageable);
        } else if (roles.contains(Roles.EMPLOYEE)) {
            page = hasStatusFilter
                    ? travelRequestRepository.findByEmployeeIdAndStatusAndDeletedFalse(requesterId, status, pageable)
                    : travelRequestRepository.findByEmployeeIdAndDeletedFalse(requesterId, pageable);
        } else if (roles.contains(Roles.FINANCE) || roles.contains(Roles.ADMIN)) {
            page = hasStatusFilter
                    ? travelRequestRepository.findByStatusAndDeletedFalse(status, pageable)
                    : travelRequestRepository.findByDeletedFalse(pageable);
        } else {
            throw new ForbiddenActionException(
                    "list travel requests",
                    "EMPLOYEE, MANAGER, FINANCE or ADMIN");
        }

        return PagedResponse.from(toResponses(page));
    }

    /**
     * Uploads a document to a travel request, enforcing owner-only access
     * (tasks.md I-02, controller support; FR-DOC-01–03).
     *
     * <p>RBAC (EMPLOYEE role) is enforced at the Controller layer; ownership is
     * enforced here. The heavy lifting (file type/size/count validation per
     * VAL-11, storage upload, and document metadata persistence) is delegated to
     * {@code TravelRequestDocumentService}. Read/write transactional because the
     * delegate persists document metadata and may transition document status.</p>
     */
    @Override
    @Transactional
    public DocumentSummary uploadDocument(Long travelRequestId, Long employeeId, String fileName,
            String fileType, byte[] fileContent) throws ValidationException, StorageException {
        // Load request and verify it exists / not soft-deleted (NFR-04)
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(travelRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("TravelRequest", travelRequestId));

        // Enforce owner-only upload (api-contract.md §4.1)
        if (!entity.getEmployeeId().equals(employeeId)) {
            throw new ForbiddenActionException("upload documents", "the request owner");
        }

        // Delegate upload orchestration to TravelRequestDocumentService
        return travelRequestDocumentService.uploadDocument(entity, fileName, fileType, fileContent);
    }

    /**
     * Validates policy-mandated documents per VAL-04 ("At least one document required
     * only if mandated by policy"; FR-TR-02, FR-ADM-02).
     *
     * <p>The mandate flag is admin-managed and defaults to {@code false} for the MVP
     * (clarification Q7). It is resolved synchronously from Admin Service's internal
     * endpoint via {@link TravelRequestPolicyClient} at submission time; a null or
     * empty {@code documentIds} list then violates the policy and submission fails
     * with a {@link PolicyViolationException} (422 POLICY_VIOLATION).</p>
     */
    /** Scope-explicit queries; ownership/assignment always keyed by the authenticated caller. */
    private Page<TravelRequest> findForScope(TravelRequestListScope scope, Long requesterId, Set<String> roles,
            TravelRequestStatus status, Pageable pageable) {
        boolean hasStatusFilter = status != null;
        switch (scope) {
            case MINE:
                if (requesterId == null || roles.isEmpty()) {
                    throw new ForbiddenActionException("list travel requests", "an authenticated user");
                }
                return hasStatusFilter
                        ? travelRequestRepository.findByEmployeeIdAndStatusAndDeletedFalse(requesterId, status, pageable)
                        : travelRequestRepository.findByEmployeeIdAndDeletedFalse(requesterId, pageable);
            case APPROVALS:
                if (!roles.contains(Roles.MANAGER) && !roles.contains(Roles.FINANCE)) {
                    throw new ForbiddenActionException("view an approval queue", "MANAGER or FINANCE");
                }
                return hasStatusFilter
                        ? travelRequestRepository.findByApproverIdAndStatusAndDeletedFalse(requesterId, status, pageable)
                        : travelRequestRepository.findByApproverIdAndDeletedFalse(requesterId, pageable);
            case ALL:
            default:
                if (!roles.contains(Roles.FINANCE) && !roles.contains(Roles.ADMIN)) {
                    throw new ForbiddenActionException("list all travel requests", "FINANCE or ADMIN");
                }
                return hasStatusFilter
                        ? travelRequestRepository.findByStatusAndDeletedFalse(status, pageable)
                        : travelRequestRepository.findByDeletedFalse(pageable);
        }
    }

    /** Maps entities to response DTOs, joining non-deleted documents per request. */
    private Page<TravelRequestResponse> toResponses(Page<TravelRequest> page) {
        return page.map(entity -> travelRequestMapper.toResponse(
                entity,
                travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(entity.getId())));
    }

    @Override
    @Transactional
    public TravelRequestResponse cancelTravelRequest(Long id, Long requesterId) {
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("TravelRequest", id));

        if (requesterId == null || !requesterId.equals(entity.getEmployeeId())) {
            throw new ForbiddenActionException("cancel this travel request", "the request owner");
        }
        TravelRequestStatus current = entity.getStatus();
        if (current == TravelRequestStatus.APPROVED) {
            if (!entity.getStartDate().isAfter(java.time.LocalDate.now())) {
                throw new ValidationException("status",
                        "An approved travel request can only be cancelled before its start date");
            }
        } else if (current != TravelRequestStatus.PENDING) {
            throw new ValidationException("status",
                    "Only PENDING or APPROVED travel requests can be cancelled. Current status: " + current);
        }

        entity.setStatus(TravelRequestStatus.CANCELLED);
        // saveAndFlush surfaces a concurrent decision/cancel (stale @Version) inside this call as 409
        TravelRequest saved = travelRequestRepository.saveAndFlush(entity);

        // Audit (actor = owner) + tell the approver the item left their queue / the approved trip is off
        List<Long> recipients = saved.getApproverId() == null ? List.of() : List.of(saved.getApproverId());
        publishEvent(saved.getId(), TRAVEL_REQUEST_ENTITY_TYPE, "CANCELLED", recipients);

        return travelRequestMapper.toResponse(saved,
                travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(saved.getId()));
    }

    @Override
    @Transactional
    public TravelRequestResponse reassignApprover(Long id, Long newApproverId) {
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("TravelRequest", id));
        if (entity.getStatus() != TravelRequestStatus.PENDING) {
            throw new ValidationException("status",
                    "Only PENDING travel requests can be reassigned. Current status: " + entity.getStatus());
        }
        if (newApproverId == null) {
            throw new ValidationException("approverId", "A new approver is required");
        }
        if (newApproverId.equals(entity.getEmployeeId())) {
            // the self-approval rule (Phase 0) cannot be bypassed through reassignment
            throw new ValidationException("approverId", "A request cannot be assigned to its own requester");
        }
        if (newApproverId.equals(entity.getApproverId())) {
            throw new ValidationException("approverId", "The request is already assigned to this approver");
        }
        String ineligible = routingServiceClient.approverIneligibilityReason(newApproverId);
        if (ineligible != null) {
            throw new PolicyViolationException("approverId", "The selected approver cannot receive approvals: " + ineligible);
        }

        Long previousApproverId = entity.getApproverId();
        entity.setApproverId(newApproverId);
        TravelRequest saved = travelRequestRepository.saveAndFlush(entity);

        publishEvent(new DomainEventEnvelope(TRAVEL_REQUEST_ENTITY_TYPE, saved.getId().toString(), "REASSIGNED",
                java.time.Instant.now(), List.of(newApproverId)).withMetadata(java.util.Map.of(
                        "fromApproverId", String.valueOf(previousApproverId),
                        "toApproverId", String.valueOf(newApproverId))));
        return travelRequestMapper.toResponse(saved,
                travelRequestDocumentRepository.findByTravelRequestIdAndDeletedFalse(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContent downloadDocument(Long travelRequestId, Long documentId, Long requesterId,
            Set<String> requesterRoles) {
        TravelRequest entity = travelRequestRepository.findByIdAndDeletedFalse(travelRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("TravelRequest", travelRequestId));
        // Same rule as GET /{id}: owner, assigned approver, FINANCE or ADMIN
        assertRequestVisible(entity, requesterId, requesterRoles);

        TravelRequestDocument document = travelRequestDocumentRepository
                .findByIdAndTravelRequestIdAndDeletedFalse(documentId, travelRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        return travelRequestDocumentService.readContent(document);
    }

    /**
     * Record-level authorization for an approval decision. Fails closed: a request with no assigned
     * approver cannot be decided by anyone, and the caller's role alone never grants the right —
     * a FINANCE user may decide only when routing assigned that user as the approver (the
     * top-of-hierarchy fallback, clarification Q3, EC-09).
     *
     * @throws ForbiddenActionException (HTTP 403) when the caller is the requester or is not the assigned approver
     */
    private void authorizeApprovalDecision(TravelRequest entity, Long callerId) {
        if (callerId == null) {
            throw new ForbiddenActionException(NOT_ASSIGNED_APPROVER_MESSAGE);
        }
        if (callerId.equals(entity.getEmployeeId())) {
            throw new ForbiddenActionException("You cannot approve or reject your own travel request");
        }
        if (entity.getApproverId() == null || !entity.getApproverId().equals(callerId)) {
            throw new ForbiddenActionException(NOT_ASSIGNED_APPROVER_MESSAGE);
        }
    }

    private void validatePolicyMandatedDocuments(List<String> documentIds) {
        if (travelRequestPolicyClient.documentsMandatory()
                && (documentIds == null || documentIds.isEmpty())) {
            throw new PolicyViolationException("documentIds",
                    "A supporting document is required for travel request submission (policy mandate, VAL-04)");
        }
    }

    /**
     * Publishes a domain event via Spring's ApplicationEventPublisher (Constitution §9).
     * The event is picked up asynchronously by Audit and Notification services.
     */
    private void publishEvent(DomainEventEnvelope event) {
        eventPublisher.publishEvent(event);
    }

    private void publishEvent(Long entityId, String entityType, String action, List<Long> recipientIds) {
        DomainEventEnvelope event = new DomainEventEnvelope(
                entityType,
                entityId.toString(),
                action,
                java.time.Instant.now(),
                recipientIds
        );
        eventPublisher.publishEvent(event);
    }

    /**
     * Enforces the read visibility rule of GET /{id} (api-contract.md §4.1; NFR-10):
     * FINANCE/ADMIN may view any request, MANAGER may view own requests or requests
     * assigned to them to approve, EMPLOYEE may view only own requests.
     *
     * <p>Role strings are the values of {@link Roles}; the {@code "ROLE_"} prefix has
     * already been stripped by the caller (security layer).</p>
     */
    private void assertRequestVisible(TravelRequest entity, Long requesterId, Set<String> requesterRoles) {
        Set<String> roles = requesterRoles == null ? Set.of() : requesterRoles;

        // FINANCE and ADMIN can view any request
        if (roles.contains(Roles.FINANCE) || roles.contains(Roles.ADMIN)) {
            return;
        }

        // MANAGER can view own requests or requests assigned to them to approve
        if (roles.contains(Roles.MANAGER)) {
            boolean ownRequest = requesterId != null && requesterId.equals(entity.getEmployeeId());
            boolean assignedApprover = requesterId != null && requesterId.equals(entity.getApproverId());
            if (!ownRequest && !assignedApprover) {
                throw new ForbiddenActionException(
                        "view this travel request",
                        "the owning EMPLOYEE, the assigned MANAGER approver, FINANCE or ADMIN");
            }
            return;
        }

        // EMPLOYEE can view only own requests
        if (roles.contains(Roles.EMPLOYEE)) {
            if (requesterId == null || !requesterId.equals(entity.getEmployeeId())) {
                throw new ForbiddenActionException(
                        "view this travel request",
                        "the owning EMPLOYEE, the assigned MANAGER approver, FINANCE or ADMIN");
            }
            return;
        }

        // No role grants read access to travel requests
        throw new ForbiddenActionException(
                "view travel requests",
                "EMPLOYEE, MANAGER, FINANCE or ADMIN");
    }
}
