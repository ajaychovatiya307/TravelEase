package com.travelease.expense.service.impl;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.common.entity.DecisionType;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseReimburseRequest;
import com.travelease.expense.dto.ExpenseReviewRequest;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.mapper.ExpenseClaimMapper;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;
import com.travelease.expense.service.ExpenseReviewService;

/**
 * Expense Review service implementation (tasks.md H-07).
 * 
 * <p>Provides Finance workflow logic for reviewing (approving/rejecting) and
 * reimbursing expense claims. All state-changing methods are transactional and
 * emit domain events post-commit (Constitution §9).</p>
 */
@Service
public class ExpenseReviewServiceImpl implements ExpenseReviewService {

    private final ExpenseClaimRepository expenseClaimRepository;
    private final ExpenseDocumentRepository expenseDocumentRepository;
    private final ExpenseClaimMapper expenseClaimMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ExpenseReviewServiceImpl(
            ExpenseClaimRepository expenseClaimRepository,
            ExpenseDocumentRepository expenseDocumentRepository,
            ExpenseClaimMapper expenseClaimMapper,
            ApplicationEventPublisher eventPublisher) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.expenseDocumentRepository = expenseDocumentRepository;
        this.expenseClaimMapper = expenseClaimMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ExpenseClaimResponse reviewExpenseClaim(String expenseClaimId, Long reviewerId, ExpenseReviewRequest request) {
        // Parse ID from string to Long
        Long claimId = Long.valueOf(expenseClaimId);

        // Load claim and verify it exists/not soft-deleted
        ExpenseClaim claim = expenseClaimRepository.findByIdAndDeletedFalse(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim not found: " + claimId));

        // Conflict of interest: a Finance user who also files claims must not review their own
        rejectSelfAction(claim, reviewerId, "review");

        // BR-03/BR-07: Verify claim is in PENDING status (VAL-13 variant)
        if (claim.getStatus() != ExpenseClaimStatus.PENDING) {
            throw new PolicyViolationException(
                    "Expense claim can only be reviewed when in PENDING status; current status: " + claim.getStatus());
        }

        // VAL-12: Validate reason is provided when decision is REJECTED
        if (request.getDecision() == DecisionType.REJECTED
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new ValidationException("reason", "Rejection reason is required when rejecting an expense claim");
        }

        // Update claim status based on decision
        String eventAction;
        if (request.getDecision() == DecisionType.APPROVED) {
            claim.setStatus(ExpenseClaimStatus.APPROVED);
            claim.setRejectionReason(null);  // Clear any prior rejection reason
            eventAction = "APPROVED";
        } else {  // REJECTED
            claim.setStatus(ExpenseClaimStatus.REJECTED);
            claim.setRejectionReason(request.getReason());
            eventAction = "REJECTED";
        }

        // Persist the updated claim
        ExpenseClaim savedClaim = expenseClaimRepository.save(claim);

        // Emit domain event post-commit (FR-AUD-01, FR-NOT-01)
        publishEvent(savedClaim.getId(), "EXPENSE_CLAIM", eventAction, savedClaim.getEmployeeId());

        // Fetch documents and map to response
        var documents = expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(savedClaim.getId());

        return expenseClaimMapper.toResponse(savedClaim, documents);
    }

    @Override
    @Transactional
    public ExpenseClaimResponse markReimbursed(String expenseClaimId, Long reviewerId, ExpenseReimburseRequest request) {
        // Parse ID from string to Long
        Long claimId = Long.valueOf(expenseClaimId);

        // Load claim and verify it exists/not soft-deleted
        ExpenseClaim claim = expenseClaimRepository.findByIdAndDeletedFalse(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim not found: " + claimId));

        // Conflict of interest: nobody marks their own claim as reimbursed
        rejectSelfAction(claim, reviewerId, "reimburse");

        // Verify claim is in APPROVED status (FR-FIN-03)
        if (claim.getStatus() != ExpenseClaimStatus.APPROVED) {
            throw new PolicyViolationException(
                    "Expense claim can only be marked reimbursed when in APPROVED status; current status: " + claim.getStatus());
        }

        // Set reimbursement timestamp: use provided value or default to server time
        Instant reimbursedAt = request.getReimbursedAt() != null 
                ? request.getReimbursedAt() 
                : Instant.now();
        claim.setReimbursedAt(reimbursedAt);

        // Update status to REIMBURSED
        claim.setStatus(ExpenseClaimStatus.REIMBURSED);

        // Persist the updated claim
        ExpenseClaim savedClaim = expenseClaimRepository.save(claim);

        // Emit domain event post-commit (FR-AUD-01, FR-NOT-01)
        publishEvent(savedClaim.getId(), "EXPENSE_CLAIM", "REIMBURSED", savedClaim.getEmployeeId());

        // Fetch documents and map to response
        var documents = expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(savedClaim.getId());

        return expenseClaimMapper.toResponse(savedClaim, documents);
    }

    /**
     * Publishes a domain event via Spring's ApplicationEventPublisher (Constitution §9).
     * The event is picked up asynchronously by Audit and Notification services.
     *
     * @param entityId the expense claim ID
     * @param entityType the entity type string (EXPENSE_CLAIM)
     * @param action the action/event type (APPROVED, REJECTED, REIMBURSED)
     */
    private void publishEvent(Long entityId, String entityType, String action, Long recipientId) {
        DomainEventEnvelope event = new DomainEventEnvelope(
                entityType,
                entityId.toString(),
                action,
                Instant.now(),
                java.util.List.of(recipientId)
        );
        eventPublisher.publishEvent(event);
    }

    /**
     * Throws 403 when the acting Finance user owns the claim (or is unidentified). Role checks
     * stay on the controller; this is the record-level segregation-of-duties rule.
     */
    private void rejectSelfAction(ExpenseClaim claim, Long reviewerId, String action) {
        if (reviewerId == null || reviewerId.equals(claim.getEmployeeId())) {
            throw new ForbiddenActionException("You cannot " + action + " your own expense claim");
        }
    }
}
