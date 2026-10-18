package com.travelease.expense.service;

import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseReimburseRequest;
import com.travelease.expense.dto.ExpenseReviewRequest;

/**
 * Service interface for Finance review and reimbursement of expense claims (tasks.md H-07;
 * spec.md FR-FIN-01–03; Constitution §4).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Review (approve/reject) pending expense claims with rejection reason (BR-03, BR-07, VAL-12)</li>
 *   <li>Mark approved claims as reimbursed with timestamp (FR-FIN-03)</li>
 *   <li>Validate state transitions (PENDING → APPROVED/REJECTED, APPROVED → REIMBURSED)</li>
 *   <li>Emit domain events post-commit (APPROVED, REJECTED, REIMBURSED) for audit and notification</li>
 * </ul>
 *
 * <p>All methods are transactional and side-effect-free (events are emitted after commit,
 * per Constitution §9).</p>
 */
public interface ExpenseReviewService {

    /**
     * Finance team reviews an expense claim (approve or reject) (FR-FIN-01, FR-FIN-02).
     *
     * <p>Business-rule validation (BR-03, BR-07):
     * <ul>
     *   <li>Claim must exist and not be soft-deleted</li>
     *   <li>Claim status must be PENDING (VAL-13 variant, enforced here not at controller level)</li>
     *   <li>Rejection reason required when decision = REJECTED (VAL-12)</li>
     * </ul>
     *
     * <p>State transition:
     * <ul>
     *   <li>If decision = APPROVED: status → APPROVED, rejectionReason → cleared</li>
     *   <li>If decision = REJECTED: status → REJECTED, rejectionReason → populated from request</li>
     * </ul>
     *
     * <p>Events: Post-commit, emits either APPROVED or REJECTED event for audit/notification.</p>
     *
     * @param expenseClaimId the ID of the expense claim (string, opaque per api-contract §3.1)
     * @param reviewerId authenticated Finance reviewer's UserAccount id; must not own the claim
     * @param request the review decision and optional reason (reason required for REJECTED)
     * @return the updated expense claim with new status and rejection reason (if applicable)
     * @throws ResourceNotFoundException if the claim does not exist
     * @throws PolicyViolationException if the claim is not in PENDING status
     * @throws ValidationException if reason is missing for REJECTED decision (VAL-12)
     * @throws com.travelease.common.exception.ForbiddenActionException if the reviewer owns the claim
     */
    ExpenseClaimResponse reviewExpenseClaim(String expenseClaimId, Long reviewerId, ExpenseReviewRequest request);

    /**
     * Marks an approved expense claim as reimbursed (FR-FIN-03).
     *
     * <p>Business-rule validation:
     * <ul>
     *   <li>Claim must exist and not be soft-deleted</li>
     *   <li>Claim status must be APPROVED (enforced here, not at controller level)</li>
     *   <li>Rejection reason cleared when transitioning to REIMBURSED</li>
     * </ul>
     *
     * <p>State transition: status → REIMBURSED, reimbursedAt → timestamp from request or server time.</p>
     *
     * <p>Events: Post-commit, emits REIMBURSED event for audit/notification.</p>
     *
     * @param expenseClaimId the ID of the expense claim (string)
     * @param reviewerId authenticated Finance user's UserAccount id; must not own the claim
     * @param request optional reimbursed-at timestamp; defaults to server time if not provided
     * @return the updated expense claim with status REIMBURSED and reimbursedAt timestamp
     * @throws ResourceNotFoundException if the claim does not exist
     * @throws PolicyViolationException if the claim is not in APPROVED status
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller owns the claim
     */
    ExpenseClaimResponse markReimbursed(String expenseClaimId, Long reviewerId, ExpenseReimburseRequest request);
}
