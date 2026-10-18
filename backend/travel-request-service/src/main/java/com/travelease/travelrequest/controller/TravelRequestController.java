package com.travelease.travelrequest.controller;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.common.dto.PagedResponse;
import com.travelease.travelrequest.dto.ApprovalDecisionRequest;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.dto.DocumentUploadRequest;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.service.TravelRequestDocumentService;
import com.travelease.travelrequest.service.TravelRequestListScope;
import com.travelease.travelrequest.service.TravelRequestService;
import com.travelease.common.web.DocumentResponses;

/**
 * Public travel-request REST endpoints (tasks.md I-01, I-02; api-contract.md §4.1;
 * spec.md FR-TR-01–07, FR-APR-01–03; AC-01–05, AC-11–12, AC-13).
 *
 * <p>Constitution §3 thin controller: no business logic here. Structural
 * validation is delegated to Bean Validation ({@code @Valid}), all business
 * rules (VAL-01–04, VAL-12, VAL-13) and ownership/visibility checks to
 * {@link TravelRequestService} and {@link TravelRequestDocumentService}.
 * The boundary is DTO-only (no entity fields leak, Constitution §6). Coarse
 * role gating is enforced per method via {@code @PreAuthorize} (method security
 * enabled in {@code SecurityConfig}); fine-grained visibility is enforced by
 * the services from the caller id and roles passed in. Service exceptions are
 * translated to structured error responses by the global exception handler, so
 * no try/catch is used here.</p>
 */
@RestController
@RequestMapping("/api/travel-requests")
public class TravelRequestController {

    /** Name of the JWT claim carrying the caller's roles (api-contract.md §2). */
    private static final String ROLES_CLAIM = "roles";

    /** Prefix applied to roles when converting them to Spring Security authorities. */
    private static final String ROLE_PREFIX = "ROLE_";

    private final TravelRequestService travelRequestService;
    private final TravelRequestDocumentService documentService;

    public TravelRequestController(
            TravelRequestService travelRequestService,
            TravelRequestDocumentService documentService) {
        this.travelRequestService = travelRequestService;
        this.documentService = documentService;
    }

    /**
     * Submit a new travel request as the authenticated employee
     * (FR-TR-01; AC-01, AC-02).
     *
     * @param jwt     authenticated bearer token whose subject is the employee's
     *                logical UserAccount ID
     * @param request validated travel request details
     * @return HTTP 201 Created with the created request in PENDING status
     */
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<TravelRequestResponse> submitTravelRequest(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TravelRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(travelRequestService.submitTravelRequest(currentUserId(jwt), request));
    }

    /**
     * Fetch a single travel request (FR-TR-04, NFR-10).
     *
     * <p>Visibility scoping (EMPLOYEE: own requests only; MANAGER: own plus
     * assigned-for-approval; FINANCE/ADMIN: all) is enforced in the service;
     * violations surface as 403 via the global exception handler.</p>
     *
     * @param jwt authenticated bearer token carrying caller identity and roles
     * @param id  primary key of the travel request
     * @return the travel request with its non-deleted documents
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','FINANCE','ADMIN')")
    public TravelRequestResponse getTravelRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return travelRequestService.getTravelRequest(id, currentUserId(jwt), currentRoles(jwt));
    }

    /**
     * List travel requests visible to the caller, with an optional status
     * filter (FR-TR-04, FR-APR-01, NFR-07, NFR-10).
     *
     * <p>Role-based scoping is enforced in the service (EMPLOYEE: own requests;
     * MANAGER: approval queue; FINANCE/ADMIN: all). Pagination defaults to 20
     * rows per page, newest first (api-contract.md §4.1); results are wrapped
     * in the shared {@code PagedResponse} envelope (api-contract.md §7).</p>
     *
     * @param jwt      authenticated bearer token carrying caller identity and roles
     * @param status   optional lifecycle status filter; absent means no filter
     * @param pageable pagination and sort parameters (Spring binds
     *                 {@code page}/{@code size}/{@code sort} query params)
     * @return page of travel requests visible to the caller
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','FINANCE','ADMIN')")
    public PagedResponse<TravelRequestResponse> listTravelRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TravelRequestStatus status,
            @RequestParam(required = false) TravelRequestListScope scope,
            @RequestParam(required = false) Long approverId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        // scope=MINE|APPROVALS|ALL selects the view explicitly (multi-role callers); omitted keeps the
        // role-derived default. Any employeeId query parameter is ignored: ownership is the JWT subject.
        // approverId (ALL scope, FINANCE/ADMIN) finds requests waiting on a particular approver (#11)
        return approverId == null
                ? travelRequestService.listTravelRequests(currentUserId(jwt), currentRoles(jwt), scope, status, pageable)
                : travelRequestService.listTravelRequests(currentUserId(jwt), currentRoles(jwt), scope, status,
                        approverId, pageable);
    }

    /**
     * Withdraw a travel request (owner only): PENDING at any time, APPROVED before the start date.
     *
     * @return HTTP 200 OK with the CANCELLED request
     */
    /**
     * Administrator reassignment of a PENDING request to another eligible approver (#11).
     *
     * @param body {@code {"approverId": <user id>}}
     */
    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasRole('ADMIN')")
    public TravelRequestResponse reassignApprover(@PathVariable Long id, @RequestBody java.util.Map<String, Long> body) {
        return travelRequestService.reassignApprover(id, body == null ? null : body.get("approverId"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TravelRequestResponse cancelTravelRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return travelRequestService.cancelTravelRequest(id, currentUserId(jwt));
    }

    /**
     * Download one of a travel request's documents. Visible to the owner, the assigned approver,
     * FINANCE and ADMIN (same rule as {@code GET /{id}}).
     */
    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','FINANCE','ADMIN')")
    public ResponseEntity<byte[]> downloadDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long documentId) {
        return DocumentResponses.attachment(
                travelRequestService.downloadDocument(id, documentId, currentUserId(jwt), currentRoles(jwt)));
    }

    /**
     * Edit a {@code Pending} request or resubmit a {@code Rejected} one
     * (FR-TR-06, FR-APR-03; AC-05, AC-13).
     *
     * <p>Ownership (caller must be the request's employee) and state rules
     * (VAL-13) are enforced in the service.</p>
     *
     * @param jwt     authenticated bearer token whose subject is the employee's
     *                logical UserAccount ID
     * @param id      primary key of the travel request to edit
     * @param request validated updated travel request details
     * @return the updated or resubmitted travel request
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TravelRequestResponse editTravelRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TravelRequestUpdateRequest request) {
        return travelRequestService.editTravelRequest(id, currentUserId(jwt), request);
    }

    /**
     * Upload a supporting document to a travel request (FR-DOC-01–03; AC-11, AC-12).
     *
     * <p>Accepts a multipart request with a binary {@code file} part and a
     * {@code fileName} form field. Validates file type (PDF/JPG/PNG), size
     * (&lt;= 5 MB), and document count (&lt;= 5 per request) per VAL-11; delegates
     * orchestration to the document service (H-04).</p>
     *
     * <p>Ownership check (caller must be the request's employee) is enforced by
     * the service; violations surface as 403 via the global exception handler.</p>
     *
     * @param jwt           authenticated bearer token whose subject is the employee's
     *                      logical UserAccount ID
     * @param id            primary key of the travel request
     * @param uploadRequest the multipart form data (file + fileName)
     * @return HTTP 201 Created with the document metadata
     * @throws IOException if the file content cannot be read
     */
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<DocumentSummary> uploadDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid DocumentUploadRequest uploadRequest) throws IOException {
        // Extract file content from MultipartFile
        byte[] fileContent = uploadRequest.file().getBytes();

        // Delegate to the document service with file metadata
        DocumentSummary summary = documentService.uploadDocument(
                id,
                currentUserId(jwt),
                uploadRequest.fileName(),
                uploadRequest.file().getSize(),
                uploadRequest.file().getContentType(),
                fileContent);

        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    /**
     * Approve or reject a {@code Pending} travel request (FR-APR-01–03; AC-03, AC-04).
     *
     * <p>Creates an approval decision record and updates the request status
     * accordingly. Enforces VAL-12 (mandatory rejection reason). Emits domain
     * events after commit (Constitution §9).</p>
     *
     * <p>Ownership check (caller must be the assigned approver) is enforced by
     * the service; violations surface as 403 via the global exception handler.</p>
     *
     * @param jwt     authenticated bearer token whose subject is the approver's
     *                logical UserAccount ID
     * @param id      primary key of the travel request
     * @param decision the approval/rejection decision details (decision + reason)
     * @return HTTP 200 OK with the updated request (status APPROVED or REJECTED)
     */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasAnyRole('MANAGER','FINANCE')")
    public ResponseEntity<TravelRequestResponse> decideApproval(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest decision) {
        return ResponseEntity.ok(travelRequestService.decideApproval(id, currentUserId(jwt), decision));
    }

    /**
     * Resolves the authenticated caller's logical UserAccount ID from the JWT
     * subject (api-contract.md §2).
     *
     * @param jwt authenticated bearer token whose subject is the caller's UserAccount ID
     * @return the caller's logical UserAccount ID
     */
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * Resolves the caller's plain role names (e.g. {@code EMPLOYEE},
     * {@code MANAGER}) as expected by the service. Prefers the raw
     * {@code roles} claim; falls back to the Spring Security authorities of
     * the current authentication (derived from that claim by the shared
     * JWT->authority conversion), stripping the leading {@code ROLE_} prefix.
     *
     * @param jwt authenticated bearer token
     * @return role names without the {@code ROLE_} prefix; empty set when the
     *         token carries no roles
     */
    private Set<String> currentRoles(Jwt jwt) {
        List<String> rolesClaim = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (rolesClaim != null && !rolesClaim.isEmpty()) {
            return new HashSet<>(rolesClaim);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
    }
}