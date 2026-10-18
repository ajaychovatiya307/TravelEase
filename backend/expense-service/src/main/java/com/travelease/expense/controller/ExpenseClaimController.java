package com.travelease.expense.controller;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.travelease.common.dto.DocumentSummary;
import com.travelease.common.validation.DocumentConstraintValidator;
import com.travelease.common.web.DocumentResponses;
import com.travelease.expense.dto.ExpenseClaimCreateRequest;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseClaimUpdateRequest;
import com.travelease.expense.dto.ExpenseReimburseRequest;
import com.travelease.expense.dto.ExpenseReviewRequest;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.service.ExpenseClaimService;
import com.travelease.expense.service.ExpenseDocumentService;
import com.travelease.expense.service.ExpenseReviewService;

import jakarta.validation.Valid;

/**
 * Expense claim submission, retrieval, document upload, and Finance review REST controller
 * (tasks.md I-03, I-04; api-contract.md §4.2, §5.7–5.11).
 *
 * <p>Thin controller delegating all business logic to {@link ExpenseClaimService},
 * {@link ExpenseDocumentService}, and {@link ExpenseReviewService}.
 * Per Constitution §4: no business rules, no cross-service calls, no DB access.
 * All methods enforce role-based and ownership-based authorization per api-contract.md §4.2.</p>
 *
 * <p>Ownership enforcement (Constitution §3 note on api-contract.md §2):
 * <ul>
 * <li>POST /api/expenses: EMPLOYEE role only; caller's userId becomes employeeId</li>
 * <li>GET /api/expenses/{id}: EMPLOYEE sees only own; FINANCE/ADMIN see any</li>
 * <li>GET /api/expenses: EMPLOYEE forced to own filter; FINANCE sees all</li>
 * <li>PUT /api/expenses/{id}: EMPLOYEE, owner only (enforced at Service layer)</li>
 * <li>POST /api/expenses/{id}/documents: EMPLOYEE, owner only</li>
 * <li>POST /api/expenses/{id}/review: FINANCE role only</li>
 * <li>POST /api/expenses/{id}/reimburse: FINANCE role only</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseClaimController {

    private final ExpenseClaimService expenseClaimService;
    private final ExpenseDocumentService expenseDocumentService;
    private final ExpenseReviewService expenseReviewService;

    public ExpenseClaimController(ExpenseClaimService expenseClaimService,
            ExpenseDocumentService expenseDocumentService,
            ExpenseReviewService expenseReviewService) {
        this.expenseClaimService = expenseClaimService;
        this.expenseDocumentService = expenseDocumentService;
        this.expenseReviewService = expenseReviewService;
    }

    /**
     * Submits a new expense claim against an approved travel request (FR-EXP-01–07, AC-06, AC-07, AC-08, AC-09).
     *
     * <p>Validates travel request approval status, date range, duplicate detection, and
     * receipt requirements per VAL-05–10. Emits CREATED event after successful commit.</p>
     *
     * @param request validated expense claim create payload per api-contract.md §5.7
     * @param auth the authenticated user's context (extract employeeId)
     * @return 201 Created with the persisted claim response
     * @throws ValidationException if any VAL-05–10 validation fails
     * @throws ResourceNotFoundException if the travel request does not exist
     * @throws PolicyViolationException if VAL-09 (travel request approval/ownership) or VAL-10 (receipt requirement) fails
     */
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ExpenseClaimResponse> submitExpenseClaim(@Valid @RequestBody ExpenseClaimCreateRequest request,
            Authentication auth) {
        String employeeId = auth.getName();
        ExpenseClaimResponse response = expenseClaimService.submitExpenseClaim(
                request.getTravelRequestId(),
                employeeId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a single expense claim by ID (FR-EXP-06).
     *
     * <p>Authorization: EMPLOYEE (owner only), FINANCE/ADMIN (any).
     * Ownership check is performed at the Service layer for consistency.</p>
     *
     * @param id the expense claim ID
     * @param auth the authenticated user's context (for ownership checks)
     * @return 200 OK with the claim details
     * @throws ResourceNotFoundException if the claim does not exist or has been soft-deleted
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'FINANCE', 'ADMIN')")
    public ResponseEntity<ExpenseClaimResponse> getExpenseClaim(@PathVariable String id, Authentication auth) {
        // Record-level access (owner, or FINANCE/ADMIN) is enforced by the service
        ExpenseClaimResponse response = expenseClaimService.getExpenseClaim(id, currentUserId(auth), currentRoles(auth));
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all non-deleted expense claims with pagination (FR-EXP-06, Constitution §5).
     *
     * <p>Authorization: EMPLOYEE (own claims only), FINANCE (all claims, filterable by status/flag),
     * ADMIN (all claims).</p>
     *
     * <p>EMPLOYEE requests are scoped to the authenticated user's claims.
     * FINANCE and ADMIN requests can list claims across employees.</p>
     *
     * @param pageable pagination and sort parameters
     * @param auth the authenticated user's context
     * @return 200 OK with a paginated list of claims
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'FINANCE', 'ADMIN')")
    public ResponseEntity<Page<ExpenseClaimResponse>> listExpenseClaims(
            Pageable pageable,
            @RequestParam(name = "status", required = false) ExpenseClaimStatus status,
            @RequestParam(name = "duplicateFlag", required = false) Boolean duplicateFlag,
            Authentication auth) {
        boolean canListAllClaims = auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_FINANCE")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
        Page<ExpenseClaimResponse> response = canListAllClaims
                ? expenseClaimService.listExpenseClaims(status, duplicateFlag, pageable)
                : expenseClaimService.listExpenseClaims(auth.getName(), status, duplicateFlag, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing expense claim (allowed only when status = PENDING).
     * Per FR-EXP-06, VAL-13, AC-13.
     *
     * <p>Authorization: EMPLOYEE role, owner only.
     * Ownership check and edit-permission validation are performed at the Service layer.</p>
     *
     * @param id the expense claim ID
     * @param request validated update payload per api-contract.md §5.8
     * @param auth the authenticated user's context (for ownership checks)
     * @return 200 OK with the updated claim response
     * @throws ResourceNotFoundException if the claim does not exist or has been soft-deleted
     * @throws PolicyViolationException if VAL-13 (edit-only-when-Pending) fails
     * @throws ValidationException if any VAL-05–10 validation fails
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ExpenseClaimResponse> updateExpenseClaim(@PathVariable String id,
            @Valid @RequestBody ExpenseClaimUpdateRequest request, Authentication auth) {
        // Owner-only edit is enforced by the service
        ExpenseClaimResponse response = expenseClaimService.editExpenseClaim(id, currentUserId(auth), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Uploads a supporting document to an expense claim (FR-DOC-01–03, VAL-11, AC-11, AC-12).
     *
     * <p>Validates file type (PDF/JPG/PNG only), size (&lt;= 5 MB), and count (&lt;= 5 per claim).
     * On success, persists document metadata and returns 200 OK. On failure, returns appropriate error.</p>
     *
     * @param id the expense claim ID
     * @param fileName the original file name
     * @param fileType the document type (PDF, JPG, PNG)
     * @param file the multipart file content
     * @param auth the authenticated user's context (for ownership checks)
     * @return 200 OK with the document summary
     * @throws ResourceNotFoundException if the claim does not exist or has been soft-deleted
     * @throws ValidationException if file type is invalid, size exceeds limit, or document count exceeds 5 (VAL-11)
     * @throws StorageException if external storage upload fails
     */
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<DocumentSummary> uploadExpenseDocument(
            @PathVariable String id,
            @RequestParam("fileName") String fileName,
            @RequestParam(name = "fileType", required = false) String fileType,
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {

        // Extract the authenticated employee's id (JWT subject), same
        // mechanism as submitExpenseClaim
        Long employeeId = Long.parseLong(auth.getName());

        // Delegate to the document service, which loads the claim, enforces
        // owner-only upload (NFR-10) and orchestrates the upload (Constitution §2/§3)
        // fileType is optional: when the client omits it (the web app does), derive it from the
        // multipart content type / extension exactly as the travel-request upload does. The result
        // is still validated against the PDF/JPG/PNG allow-list by the document service.
        String declaredType = (fileType == null || fileType.isBlank())
                ? DocumentConstraintValidator.deriveFileType(file.getContentType(), fileName)
                : fileType;
        DocumentSummary response = expenseDocumentService.uploadDocument(
                Long.parseLong(id),
                employeeId,
                fileName,
                declaredType,
                file.getBytes());
        return ResponseEntity.ok(response);
    }

    /**
     * Finance team reviews an expense claim (approve or reject) (FR-FIN-01, FR-FIN-02, AC-10).
     *
     * <p>Validates claim status is PENDING and rejection reason is provided (VAL-12 when rejecting).
     * Transitions claim to APPROVED or REJECTED status and emits corresponding event.</p>
     *
     * @param id the expense claim ID
     * @param request the review decision (APPROVED/REJECTED) and optional reason
     * @return 200 OK with the updated claim response
     * @throws ResourceNotFoundException if the claim does not exist
     * @throws PolicyViolationException if the claim is not in PENDING status
     * @throws ValidationException if reason is missing for REJECTED decision (VAL-12)
     */
    /**
     * Send a DRAFT claim (or a corrected REJECTED claim) to Finance review. Owner only; the receipt
     * mandate and all claim rules are re-validated at this point.
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ExpenseClaimResponse> submitForReview(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(expenseClaimService.submitForReview(id, currentUserId(auth)));
    }

    /** Download a claim document: the claim owner, FINANCE or ADMIN (same rule as GET /{id}). */
    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'FINANCE', 'ADMIN')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String id, @PathVariable Long documentId,
            Authentication auth) {
        return DocumentResponses.attachment(
                expenseClaimService.downloadDocument(id, documentId, currentUserId(auth), currentRoles(auth)));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ExpenseClaimResponse> reviewExpenseClaim(
            @PathVariable String id,
            @Valid @RequestBody ExpenseReviewRequest request,
            Authentication auth) {
        ExpenseClaimResponse response = expenseReviewService.reviewExpenseClaim(id, currentUserId(auth), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Marks an approved expense claim as reimbursed (FR-FIN-03, AC-10).
     *
     * <p>Validates claim status is APPROVED and sets reimbursedAt timestamp (from request or server time).
     * Emits REIMBURSED event after commit.</p>
     *
     * @param id the expense claim ID
     * @param request the reimbursement details (reimbursedAt timestamp, optional)
     * @return 200 OK with the updated claim response
     * @throws ResourceNotFoundException if the claim does not exist
     * @throws PolicyViolationException if the claim is not in APPROVED status
     */
    @PostMapping("/{id}/reimburse")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ExpenseClaimResponse> reimburseExpenseClaim(
            @PathVariable String id,
            @Valid @RequestBody ExpenseReimburseRequest request,
            Authentication auth) {
        ExpenseClaimResponse response = expenseReviewService.markReimbursed(id, currentUserId(auth), request);
        return ResponseEntity.ok(response);
    }

    /** Caller's logical UserAccount id from the authenticated JWT subject (never from the request). */
    private static Long currentUserId(Authentication auth) {
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Caller's role names without the {@code ROLE_} prefix, from the authenticated authorities. */
    private static Set<String> currentRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }
}
