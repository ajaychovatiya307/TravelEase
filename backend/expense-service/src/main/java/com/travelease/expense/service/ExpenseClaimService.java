package com.travelease.expense.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

import com.travelease.expense.dto.ExpenseClaimCreateRequest;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseClaimUpdateRequest;
import com.travelease.expense.entity.ExpenseClaimStatus;

/**
 * Service interface for managing expense claim submissions and edits (tasks.md H-05;
 * spec.md FR-EXP-01–07; Constitution §4).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate expense claims per VAL-05–10, VAL-13</li>
 *   <li>Coordinate with Travel Request Service for approval and date-range validation</li>
 *   <li>Detect duplicate claims and set duplicate flag (non-blocking)</li>
 *   <li>Persist claims and emit CREATED/UPDATED events asynchronously</li>
 * </ul>
 *
 * <p>All methods are transactional; cross-service calls via REST are not retried at
 * this layer (per Constitution §9, side effects are async and never block).</p>
 */
public interface ExpenseClaimService {

    /**
     * Creates a new expense claim, as a private DRAFT, against an approved travel request.
     *
     * <p>Validation (VAL-05–09):
     * <ul>
     *   <li>VAL-05: Amount > 0</li>
     *   <li>VAL-06: Currency required and non-blank</li>
     *   <li>VAL-07: Category must match a predefined category</li>
     *   <li>VAL-08: Expense date must fall within travel request date range</li>
     *   <li>VAL-09: Travel request must be APPROVED and owned by the same employee</li>
     * </ul>
     *
     * <p>The receipt mandate (VAL-10) is NOT enforced on create: documents can only
     * be uploaded to an existing claim ({@code POST /api/expenses/{id}/documents}),
     * so an above-threshold claim is created first (DRAFT) and its supporting
     * document is attached right afterwards (create-then-upload flow). The mandate
     * is enforced when the draft is sent to Finance — see {@link #submitForReview}.
     * Request-supplied {@code documentIds} are not consulted at create time.</p>
     *
     * <p>Duplicate detection: Queries existing claims under the same travel request
     * with identical amount/date/category; if found, sets duplicateFlag = true (non-blocking,
     * per FR-EXP-07).</p>
     *
     * <p>Events: After successful commit, a CREATED event is emitted asynchronously
     * for notification and audit logging.</p>
     *
     * @param travelRequestId ID of the approved travel request (string, opaque per api-contract §3.1)
     * @param employeeId ID of the submitting employee (string)
     * @param request the expense claim request details (with Bean Validation applied)
     * @return the created expense claim with ID and initial status (PENDING)
     * @throws ValidationException if any VAL-05–09 validation fails
     * @throws ResourceNotFoundException if the travel request does not exist
     * @throws PolicyViolationException if VAL-08 or VAL-09 business rule fails
     */
    ExpenseClaimResponse submitExpenseClaim(
            String travelRequestId,
            String employeeId,
            ExpenseClaimCreateRequest request);

    /**
     * Edits an existing expense claim (allowed only when status = PENDING).
     *
     * <p>Validation: same as {@link #submitExpenseClaim} (VAL-05–09), plus VAL-13
     * (edit allowed only when status = PENDING) and VAL-10 — the receipt mandate,
     * moved here from create. It is enforced AFTER the update is applied: if the
     * resulting amount exceeds the category's active receipt threshold and the
     * claim has no attached (non-deleted) documents, the edit fails with a
     * {@code documentIds} field error (422 POLICY_VIOLATION) — the same error
     * contract the create path used before. This keeps the create-then-upload
     * flow intact (see {@link #submitExpenseClaim}). Duplicate detection is
     * re-run, potentially changing the duplicateFlag on update.</p>
     *
     * <p>Events: After successful commit, an UPDATED event is emitted asynchronously.</p>
     *
     * @param expenseClaimId ID of the expense claim to edit
     * @param requesterId authenticated caller's UserAccount id (JWT subject); must own the claim
     * @param request the updated expense claim details
     * @return the updated expense claim
     * @throws ValidationException if any VAL-05–09 or VAL-13 validation fails
     * @throws ResourceNotFoundException if the expense claim does not exist
     * @throws PolicyViolationException if VAL-13 (edit-only-when-Pending) or VAL-10
     *       (amount above threshold without attached documents) fails
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller does not own the claim
     */
    ExpenseClaimResponse editExpenseClaim(
            String expenseClaimId,
            Long requesterId,
            ExpenseClaimUpdateRequest request);

    /**
     * Retrieves a single expense claim by ID (FR-EXP-06).
     *
     * <p>Returns the expense claim in its current state, including all related
     * documents and metadata. Record-level access is enforced here: FINANCE/ADMIN may read any
     * claim; anyone else only their own.</p>
     *
     * @param expenseClaimId ID of the expense claim to retrieve
     * @param requesterId authenticated caller's UserAccount id (JWT subject)
     * @param requesterRoles caller's role names without the ROLE_ prefix
     * @return the expense claim details
     * @throws ResourceNotFoundException if the expense claim does not exist or has been soft-deleted
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller may not read the claim
     */
    ExpenseClaimResponse getExpenseClaim(String expenseClaimId, Long requesterId, Set<String> requesterRoles);

    /**
     * Lists all non-deleted expense claims with pagination (FR-EXP-06, Constitution §5).
     *
     * <p>Returns all expense claims sorted and paginated as requested. This
     * method is intended for roles with cross-employee access, such as FINANCE
     * and ADMIN.</p>
     *
     * @param pageable pagination and sort parameters
     * @return a paginated list of expense claim details
     */
    Page<ExpenseClaimResponse> listExpenseClaims(Pageable pageable);

    /**
     * Lists cross-employee claims matching the optional Finance queue filters.
     */
    Page<ExpenseClaimResponse> listExpenseClaims(
            ExpenseClaimStatus status, Boolean duplicateFlag, Pageable pageable);

    /**
     * Lists only one employee's non-deleted expense claims with pagination.
     *
     * @param employeeId authenticated employee's identifier
     * @param pageable pagination and sort parameters
     * @return a paginated list of that employee's expense claims
     */
    Page<ExpenseClaimResponse> listExpenseClaims(String employeeId, Pageable pageable);

    /**
     * Lists an employee's claims matching the optional filters while retaining
     * the caller's ownership scope.
     */
    Page<ExpenseClaimResponse> listExpenseClaims(
            String employeeId, ExpenseClaimStatus status, Boolean duplicateFlag, Pageable pageable);

    /**
     * Sends a DRAFT claim (first submission) or a corrected REJECTED claim (resubmission) to
     * Finance review, moving it to PENDING. Owner only. Re-validates the base currency, that the
     * travel request is still APPROVED and owned, the expense date range and the receipt mandate
     * (VAL-10, counting only successfully uploaded documents) at submission time.
     *
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller does not own the claim
     * @throws PolicyViolationException if the claim is not DRAFT/REJECTED or a rule is violated
     */
    ExpenseClaimResponse submitForReview(String expenseClaimId, Long requesterId);

    /**
     * Returns one of the claim's documents after applying the {@link #getExpenseClaim} access rule.
     *
     * @throws ResourceNotFoundException if the claim, or a completed document belonging to it, does not exist
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller may not view the claim
     */
    com.travelease.common.dto.DocumentContent downloadDocument(String expenseClaimId, Long documentId,
            Long requesterId, Set<String> requesterRoles);
}
