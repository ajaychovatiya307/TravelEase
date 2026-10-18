package com.travelease.travelrequest.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;

import com.travelease.common.dto.DocumentContent;
import com.travelease.common.dto.PagedResponse;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.StorageException;
import com.travelease.travelrequest.dto.ApprovalDecisionRequest;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;

/**
 * Service interface for Travel Request submission and edit workflows (tasks.md H-02, H-03).
 * 
 * <p>Enforces business rules and state transitions for travel request creation, editing,
 * approval, and resubmission. All state-changing methods are marked {@code @Transactional}
 * and emit domain events after successful commit (Constitution §9).</p>
 */
public interface TravelRequestService {

    /**
     * Submit a new travel request (H-02; FR-TR-01, FR-TR-03).
     *
     * <p>Enforces VAL-01–04 (destination, dates, justification, documents):
     * <ul>
     *   <li>VAL-01/03: Structural validation handled by Bean Validation on DTO</li>
     *   <li>VAL-02: Business part (start date not in past, date ordering) validated here</li>
     *   <li>VAL-04: Policy-mandated documents validated here</li>
     * </ul>
     *
     * <p>Calls Admin Service synchronously to resolve the approver via
     * {@code EmployeeManagerRoutingService}, then sets status to PENDING and saves.
     * Emits a {@code CREATED} domain event after transaction commit.</p>
     *
     * @param employeeId the authenticated employee submitting the request (logical UserAccount ID)
     * @param dto the travel request details (validated by Bean Validation first)
     * @return the created travel request in PENDING status
     * @throws com.travelease.common.exception.ValidationException 
     *         if start date is in the past or end date &lt; start date (VAL-02 business part)
     * @throws com.travelease.common.exception.PolicyViolationException 
     *         if policy mandates a document but none provided (VAL-04 business part)
     * @throws Exception if routing/Admin Service is unavailable (Q20 fallback?)
     */
    TravelRequestResponse submitTravelRequest(Long employeeId, TravelRequestCreateRequest dto);

    /**
     * Edit an existing travel request (H-02, H-03; FR-TR-06, FR-TR-07, FR-APR-03).
     *
     * <p>Enforces VAL-13 (edit only when Pending or resubmitting Rejected).
     * Enforces VAL-01–04 on the new request details, including VAL-02 (start date not in past).
     * 
     * <p>When status is PENDING: updates travel details, preserves status and approver,
     * emits {@code UPDATED} event.
     * 
     * <p>When status is REJECTED (resubmission, AC-05): updates travel details, sets status
     * back to PENDING, links {@code resubmissionOfId} to the rejected request, re-resolves
     * approver, emits {@code RESUBMITTED} event (FR-APR-03).</p>
     *
     * <p>Ownership check (must be the employee who created the request) is performed at
     * the Controller layer via RBAC (@PreAuthorize-style checking).</p>
     *
     * @param id primary key of the travel request to edit
     * @param employeeId the authenticated employee performing the edit (matched against entity.employeeId)
     * @param dto the updated travel request details (validated by Bean Validation first)
     * @return the updated/resubmitted travel request (PENDING status after resubmission)
     * @throws com.travelease.common.exception.ResourceNotFoundException 
     *         if the travel request does not exist or has been soft-deleted
     * @throws com.travelease.common.exception.ValidationException 
     *         if VAL-13 state constraint is violated (not Pending/Rejected) or VAL-02 business part fails
     * @throws com.travelease.common.exception.PolicyViolationException 
     *         if policy-mandated documents are missing (VAL-04)
     */
    TravelRequestResponse editTravelRequest(Long id, Long employeeId, TravelRequestUpdateRequest dto);

    /**
     * Approve or reject a pending travel request (H-03; FR-APR-01, FR-APR-02).
     *
     * <p>Creates an {@code ApprovalDecision} record and updates the {@code TravelRequest}
     * status accordingly. Enforces VAL-12 (mandatory reason on rejection).
     * 
     * <p>Approval transitions status to APPROVED. Rejection transitions status to REJECTED
     * and stores the rejection reason in the request.</p>
     * 
     * <p>Emits {@code APPROVED} or {@code REJECTED} domain event after commit.</p>
     *
     * <p>Record-level authorization is enforced here, not only by the controller's role check:
     * the caller must be the request's assigned approver and must not be the requester.</p>
     *
     * @param id primary key of the travel request to approve/reject
     * @param approverId the authenticated approver user ID (logical UserAccount reference)
     * @param dto the decision details (decision + optional reason)
     * @return the updated travel request with new status
     * @throws com.travelease.common.exception.ResourceNotFoundException 
     *         if the travel request does not exist or has been soft-deleted
     * @throws com.travelease.common.exception.ValidationException 
     *         if status is not PENDING or reason is missing on REJECTED decision (VAL-12)
     * @throws com.travelease.common.exception.ForbiddenActionException
     *         if the caller is not the assigned approver, or is the requester
     */
    TravelRequestResponse decideApproval(Long id, Long approverId, ApprovalDecisionRequest dto);

    /**
     * Fetch a single travel request with its documents, enforcing role-based
     * visibility (I-01; api-contract.md §4.1 GET /{id}; FR-TR-04, NFR-10).
     *
     * <p>Read-only: performs no state changes and emits no domain events.
     * Loads the request excluding soft-deleted rows (NFR-04) and applies the
     * visibility rules:
     * <ul>
     *   <li>EMPLOYEE: may view only own requests (employeeId equals requesterId)</li>
     *   <li>MANAGER: may view own requests and requests assigned to them to approve
     *       (approverId equals requesterId)</li>
     *   <li>FINANCE and ADMIN: may view any request</li>
     * </ul>
     *
     * <p>Role strings use the values of
     * {@code com.travelease.common.security.Roles} (e.g. {@code "MANAGER"});
     * the {@code "ROLE_"} prefix has already been stripped by the caller (security
     * layer) before reaching this method.</p>
     *
     * @param id primary key of the travel request
     * @param requesterId the authenticated caller (logical UserAccount ID)
     * @param requesterRoles the caller's roles without the {@code "ROLE_"} prefix
     *        (values from {@code com.travelease.common.security.Roles}); never null in
     *        practice, treated as empty when null
     * @return the travel request with its non-deleted documents
     * @throws com.travelease.common.exception.ResourceNotFoundException
     *         if the travel request does not exist or has been soft-deleted
     * @throws com.travelease.common.exception.ForbiddenActionException
     *         if the caller's roles do not grant visibility of this request
     */
    TravelRequestResponse getTravelRequest(Long id, Long requesterId, Set<String> requesterRoles);

    /**
     * List travel requests with role-based scoping and an optional status filter
     * (I-01; api-contract.md §4.1 GET list; FR-TR-04, FR-APR-01, NFR-10).
     *
     * <p>Read-only: performs no state changes and emits no domain events.
     * Excludes soft-deleted rows (NFR-04) and is paginated (NFR-07).
     * Scoping rules:
     * <ul>
     *   <li>EMPLOYEE: forced to the caller's own requests</li>
     *   <li>MANAGER: requests assigned to the caller to approve (approval queue)</li>
     *   <li>FINANCE and ADMIN: all requests</li>
     * </ul>
     *
     * <p>When a non-null {@code status} is supplied, the role scope is further
     * filtered by lifecycle status. Results are mapped per element (joining
     * non-deleted documents) and wrapped in the shared pagination envelope
     * (api-contract.md §7; tasks.md E-06).</p>
     *
     * <p>Role strings use the values of
     * {@code com.travelease.common.security.Roles}; the {@code "ROLE_"} prefix
     * has already been stripped by the caller (security layer) before reaching
     * this method.</p>
     *
     * @param requesterId the authenticated caller (logical UserAccount ID)
     * @param requesterRoles the caller's roles without the {@code "ROLE_"} prefix
     *        (values from {@code com.travelease.common.security.Roles}); never null in
     *        practice, treated as empty when null
     * @param status optional lifecycle status filter; {@code null} means no filter
     * @param pageable pagination and sort parameters
     * @return page of travel requests visible to the caller, wrapped in the shared
     *         {@code PagedResponse} envelope (api-contract.md §7)
     * @throws com.travelease.common.exception.ForbiddenActionException
     *         if the caller holds no role that grants list access
     */
    PagedResponse<TravelRequestResponse> listTravelRequests(Long requesterId, Set<String> requesterRoles,
            TravelRequestStatus status, Pageable pageable);

    /**
     * Lists travel requests for an explicit scope, so a caller holding several roles can see each
     * view independently. Ownership always comes from {@code requesterId} (the JWT subject).
     *
     * @param scope {@code null} keeps the legacy role-derived default of
     *        {@link #listTravelRequests(Long, Set, TravelRequestStatus, Pageable)}
     * @throws com.travelease.common.exception.ForbiddenActionException when the caller's roles do
     *         not allow the requested scope ({@code APPROVALS}: MANAGER/FINANCE; {@code ALL}: FINANCE/ADMIN)
     */
    PagedResponse<TravelRequestResponse> listTravelRequests(Long requesterId, Set<String> requesterRoles,
            TravelRequestListScope scope, TravelRequestStatus status, Pageable pageable);

    /**
     * Withdraws a travel request (FR-TR cancellation). Only the owner may cancel; a PENDING request
     * may always be withdrawn, an APPROVED one only before its start date. Concurrent changes are
     * detected through the entity version (optimistic locking).
     *
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller is not the owner
     * @throws ValidationException if the request's status or dates make it ineligible
     */
    TravelRequestResponse cancelTravelRequest(Long id, Long requesterId);

    /**
     * Administrator reassignment of a PENDING request to another approver (#11), e.g. when the original
     * approver left. The new approver must be active and hold MANAGER or FINANCE (checked with
     * admin-service), and can never be the requester (self-approval stays impossible). Audited as
     * REASSIGNED and notified to the new approver.
     */
    TravelRequestResponse reassignApprover(Long id, Long newApproverId);

    /** As the scoped list, additionally filtered by assigned approver (ALL scope only; FINANCE/ADMIN). */
    PagedResponse<TravelRequestResponse> listTravelRequests(Long requesterId, Set<String> requesterRoles,
            TravelRequestListScope scope, TravelRequestStatus status, Long approverIdFilter, Pageable pageable);

    /**
     * Returns a document's content after applying the same visibility rule as
     * {@link #getTravelRequest} to the parent request.
     *
     * @throws com.travelease.common.exception.ResourceNotFoundException if the request, or a
     *         completed document belonging to it, does not exist
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller may not view the request
     */
    DocumentContent downloadDocument(Long travelRequestId, Long documentId, Long requesterId,
            Set<String> requesterRoles);

    /**
     * Uploads a document to an existing travel request (tasks.md I-02, controller
     * support; api-contract.md §4.1; FR-DOC-01–03).
     *
     * <p>Loads the owning travel request (excluding soft-deleted rows, NFR-04),
     * verifies that the authenticated caller is the request's owner, and then
     * delegates the full upload orchestration (file type/size/count validation
     * per VAL-11, external storage write, and document metadata persistence) to
     * {@code TravelRequestDocumentService#uploadDocument}.</p>
     *
     * <p>RBAC (EMPLOYEE role) is enforced at the Controller layer; ownership is
     * enforced here so that no employee may attach documents to another
     * employee's request.</p>
     *
     * @param travelRequestId primary key of the owning travel request
     * @param employeeId the authenticated employee performing the upload (logical UserAccount ID)
     * @param fileName display name of the file; assumed non-blank
     * @param fileType file type (PDF, JPG, PNG); validated per VAL-11 by the delegate
     * @param fileContent binary file content; assumed non-empty
     * @return the persisted document metadata summary
     * @throws com.travelease.common.exception.ResourceNotFoundException
     *         if the travel request does not exist or has been soft-deleted
     * @throws com.travelease.common.exception.ForbiddenActionException
     *         if the caller is not the owner of the travel request
     * @throws com.travelease.common.exception.ValidationException
     *         if file type, size, or per-request document count violates VAL-11
     * @throws com.travelease.common.storage.StorageException
     *         if the external storage upload fails (FR-DOC-03)
     */
    DocumentSummary uploadDocument(Long travelRequestId, Long employeeId, String fileName, String fileType,
            byte[] fileContent) throws ValidationException, StorageException;
}
