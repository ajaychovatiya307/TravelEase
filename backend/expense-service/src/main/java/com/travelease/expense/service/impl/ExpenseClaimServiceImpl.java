package com.travelease.expense.service.impl;

import com.travelease.common.document.UploadStatus;
import com.travelease.common.dto.DocumentContent;
import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.security.Roles;
import com.travelease.expense.client.PolicyConfigClient;
import com.travelease.expense.client.TravelRequestClient;
import com.travelease.expense.dto.ExpenseClaimCreateRequest;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseClaimUpdateRequest;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.entity.ExpenseDocument;
import com.travelease.expense.mapper.ExpenseClaimMapper;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;
import com.travelease.expense.service.ExpenseClaimService;
import com.travelease.expense.service.ExpenseDocumentService;
import com.travelease.expense.validation.DuplicateExpenseDetector;
import com.travelease.expense.validation.ExpenseClaimPolicyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link ExpenseClaimService} (tasks.md H-05).
 * Handles expense claim submission and editing with full validation,
 * cross-service travel request verification, and duplicate detection.
 */
@Service
public class ExpenseClaimServiceImpl implements ExpenseClaimService {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseClaimServiceImpl.class);

    private final ExpenseClaimRepository expenseClaimRepository;
    private final ExpenseDocumentRepository expenseDocumentRepository;
    private final TravelRequestClient travelRequestClient;
    private final PolicyConfigClient policyConfigClient;
    private final ExpenseClaimMapper expenseClaimMapper;
    private final ExpenseClaimPolicyValidator policyValidator;
    private final DuplicateExpenseDetector duplicateExpenseDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final ExpenseDocumentService expenseDocumentService;

    public ExpenseClaimServiceImpl(
            ExpenseClaimRepository expenseClaimRepository,
            ExpenseDocumentRepository expenseDocumentRepository,
            TravelRequestClient travelRequestClient,
            PolicyConfigClient policyConfigClient,
            ExpenseClaimMapper expenseClaimMapper,
            ExpenseClaimPolicyValidator policyValidator,
            DuplicateExpenseDetector duplicateExpenseDetector,
            ApplicationEventPublisher eventPublisher,
            ExpenseDocumentService expenseDocumentService) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.expenseDocumentRepository = expenseDocumentRepository;
        this.travelRequestClient = travelRequestClient;
        this.policyConfigClient = policyConfigClient;
        this.expenseClaimMapper = expenseClaimMapper;
        this.policyValidator = policyValidator;
        this.duplicateExpenseDetector = duplicateExpenseDetector;
        this.eventPublisher = eventPublisher;
        this.expenseDocumentService = expenseDocumentService;
    }

    /**
     * Submits a new expense claim (H-05).
     *
     * <p>Creation intentionally does NOT enforce the receipt mandate (VAL-10):
     * documents can only be uploaded to an EXISTING claim
     * ({@code POST /api/expenses/{id}/documents}), so an above-threshold claim is
     * created first (PENDING) and its supporting document is attached afterwards
     * (create-then-upload flow). The mandate is enforced on the EDIT path instead —
     * see {@link #editExpenseClaim}. Request-supplied {@code documentIds} are no
     * longer consulted at create time.</p>
     */
    @Override
    @Transactional
    public ExpenseClaimResponse submitExpenseClaim(
            String travelRequestId,
            String employeeId,
            ExpenseClaimCreateRequest request) {

        logger.debug("Submitting expense claim for travel request {} by employee {}", 
                travelRequestId, employeeId);

        // Step 2: Enforce the single base currency (NFR-05, TC-VAL-06, VAL-06); the remaining
        // structural rules (VAL-05, VAL-07) are enforced by Bean Validation on the @Valid DTO.
        policyValidator.validateBaseCurrency(request.getCurrency());

        // Step 3: Validate travel request (VAL-08, VAL-09)
        travelRequestClient.validateTravelRequestApprovedAndOwned(travelRequestId, employeeId);

        // Step 4: Validate expense date is within trip range (VAL-08)
        validateExpenseDateInRange(travelRequestId, request.getExpenseDate());

        // Step 5: Create entity
        ExpenseClaim claim = new ExpenseClaim();
        claim.setEmployeeId(Long.parseLong(employeeId));
        claim.setTravelRequestId(Long.parseLong(travelRequestId));
        claim.setAmount(request.getAmount());
        claim.setCurrency(request.getCurrency());
        claim.setCategory(request.getCategory());
        claim.setExpenseDate(request.getExpenseDate());
        // Created as a private DRAFT: receipts are attached next, then POST /{id}/submit sends it to
        // Finance review once every rule (including the receipt mandate) holds.
        claim.setStatus(ExpenseClaimStatus.DRAFT);
        claim.setDeleted(false);

        // Step 6: Run duplicate detection (FR-EXP-07)
        boolean isDuplicate = duplicateExpenseDetector.isPotentialDuplicate(
                Long.parseLong(travelRequestId),
                request.getAmount(),
                request.getExpenseDate(),
                request.getCategory());
        claim.setDuplicateFlag(isDuplicate);

        // Step 7: Persist
        ExpenseClaim savedClaim = expenseClaimRepository.save(claim);
        logger.info("Expense claim {} created as DRAFT", savedClaim.getId());

        // Step 8: Emit event (audit only: Finance is notified on submission, not for drafts)
        publishExpenseClaimEvent(savedClaim, "CREATED");

        return expenseClaimMapper.toResponse(savedClaim, List.of());
    }

    /**
     * Edits an existing expense claim while it is PENDING (H-05, VAL-13).
     *
     * <p>The receipt mandate (VAL-10) lives HERE rather than on create: after the
     * update is applied, if the resulting amount exceeds the category's active
     * receipt threshold and the claim has no attached documents, the update is
     * rejected with the same {@code documentIds} field error (POLICY_VIOLATION)
     * the create path used, so the frontend error mapping is unchanged. Attached
     * documents are the claim's non-deleted rows, loaded the same way
     * {@link #getExpenseClaim(String)} loads them. This supports the
     * create-then-upload flow: create an above-threshold claim without documents,
     * attach the receipt via {@code POST /api/expenses/{id}/documents}, then edit.</p>
     */
    @Override
    @Transactional
    public ExpenseClaimResponse editExpenseClaim(
            String expenseClaimId,
            Long requesterId,
            ExpenseClaimUpdateRequest request) {

        logger.debug("Editing expense claim {}", expenseClaimId);

        // Step 1: Load existing claim
        ExpenseClaim claim = expenseClaimRepository.findByIdAndDeletedFalse(Long.parseLong(expenseClaimId))
                .orElseThrow(() -> new ResourceNotFoundException("expenseClaimId",
                        "Expense claim not found: " + expenseClaimId));

        // Step 2: Only the owner may edit (NFR-10), then only while editable (VAL-13)
        requireOwner(claim, requesterId, "edit expense claims");
        validateEditPermission(claim);

        // Step 4: Enforce the single base currency (NFR-05, TC-VAL-06, VAL-06); the remaining
        // structural rules (VAL-05, VAL-07) are enforced by Bean Validation on the @Valid DTO.
        policyValidator.validateBaseCurrency(request.getCurrency());

        // Step 5: Validate expense date is within trip range (VAL-08)
        validateExpenseDateInRange(String.valueOf(claim.getTravelRequestId()), request.getExpenseDate());

        // Step 6: Update fields
        claim.setAmount(request.getAmount());
        claim.setCurrency(request.getCurrency());
        claim.setCategory(request.getCategory());
        claim.setExpenseDate(request.getExpenseDate());

        // Step 7: A PENDING claim is already in Finance's queue, so it must stay compliant with the
        // receipt mandate (VAL-10). DRAFT/REJECTED claims are checked when they are (re)submitted.
        if (claim.getStatus() == ExpenseClaimStatus.PENDING) {
            validateReceiptRequirement(claim);
        }

        // Step 8: Re-run duplicate detection with this claim excluded (FR-EXP-07)
        boolean isDuplicate = duplicateExpenseDetector.isPotentialDuplicateExcluding(
                claim.getTravelRequestId(),
                request.getAmount(),
                request.getExpenseDate(),
                request.getCategory(),
                Long.parseLong(expenseClaimId));
        claim.setDuplicateFlag(isDuplicate);

        // Step 9: Persist
        ExpenseClaim updatedClaim = expenseClaimRepository.save(claim);
        logger.info("Expense claim {} updated successfully", updatedClaim.getId());

        // Step 10: Emit event (async, non-blocking)
        publishExpenseClaimEvent(updatedClaim, "UPDATED");

        return expenseClaimMapper.toResponse(updatedClaim, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseClaimResponse getExpenseClaim(String expenseClaimId, Long requesterId, Set<String> requesterRoles) {
        logger.debug("Retrieving expense claim {}", expenseClaimId);

        ExpenseClaim claim = expenseClaimRepository.findByIdAndDeletedFalse(Long.parseLong(expenseClaimId))
                .orElseThrow(() -> new ResourceNotFoundException("expenseClaimId",
                        "Expense claim not found: " + expenseClaimId));

        assertClaimVisible(claim, requesterId, requesterRoles);

        // Fetch the claim's non-deleted documents so read responses surface them (N-04)
        List<ExpenseDocument> documents =
                expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(claim.getId());

        return expenseClaimMapper.toResponse(claim, documents);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> listExpenseClaims(Pageable pageable) {
        return listExpenseClaims(null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> listExpenseClaims(
            ExpenseClaimStatus status, Boolean duplicateFlag, Pageable pageable) {
        logger.debug("Listing expense claims, page={}", pageable.getPageNumber());

        // DRAFT claims are private to their owner until submitted; the all-claims (Finance/Admin)
        // view never includes them.
        if (status == ExpenseClaimStatus.DRAFT) {
            return Page.empty(pageable);
        }
        Page<ExpenseClaim> claims;
        if (status != null && duplicateFlag != null) {
            claims = expenseClaimRepository.findByStatusAndDuplicateFlagAndDeletedFalse(
                    status, duplicateFlag, pageable);
        } else if (status != null) {
            claims = expenseClaimRepository.findByStatusAndDeletedFalse(status, pageable);
        } else if (duplicateFlag != null) {
            claims = expenseClaimRepository.findByStatusNotAndDuplicateFlagAndDeletedFalse(
                    ExpenseClaimStatus.DRAFT, duplicateFlag, pageable);
        } else {
            claims = expenseClaimRepository.findByStatusNotAndDeletedFalse(ExpenseClaimStatus.DRAFT, pageable);
        }
        return mapClaimPage(claims);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> listExpenseClaims(String employeeId, Pageable pageable) {
        return listExpenseClaims(employeeId, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> listExpenseClaims(
            String employeeId, ExpenseClaimStatus status, Boolean duplicateFlag, Pageable pageable) {
        logger.debug("Listing expense claims for employee {}, page={}", employeeId, pageable.getPageNumber());

        Long ownerId = Long.parseLong(employeeId);
        Page<ExpenseClaim> claims;
        if (status != null && duplicateFlag != null) {
            claims = expenseClaimRepository.findByEmployeeIdAndStatusAndDuplicateFlagAndDeletedFalse(
                    ownerId, status, duplicateFlag, pageable);
        } else if (status != null) {
            claims = expenseClaimRepository.findByEmployeeIdAndStatusAndDeletedFalse(ownerId, status, pageable);
        } else if (duplicateFlag != null) {
            claims = expenseClaimRepository.findByEmployeeIdAndDuplicateFlagAndDeletedFalse(
                    ownerId, duplicateFlag, pageable);
        } else {
            claims = expenseClaimRepository.findByEmployeeIdAndDeletedFalse(ownerId, pageable);
        }
        return mapClaimPage(claims);
    }

    private Page<ExpenseClaimResponse> mapClaimPage(Page<ExpenseClaim> claims) {
        return claims
                .map(claim -> expenseClaimMapper.toResponse(claim,
                        expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(claim.getId())));
    }

    /**
     * Validates that the expense date falls within the travel request's date range (VAL-08).
     */
    private void validateExpenseDateInRange(String travelRequestId, LocalDate expenseDate) {
        TravelRequestClient.TravelRequestDateRange dateRange = 
                travelRequestClient.getTravelRequestDateRange(travelRequestId);

        policyValidator.validateExpenseDateInRange(expenseDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * Enforces the receipt-above-threshold requirement (VAL-10) on the EDIT path
     * by resolving the category's active threshold from the Admin Service and
     * delegating the rule itself to {@link ExpenseClaimPolicyValidator}.
     *
     * <p>"Attached documents" are the claim's non-deleted documents, loaded from
     * the repository the same way {@link #getExpenseClaim(String)} does — the
     * mandate is checked against what is actually attached to the claim, since
     * documents can only be uploaded to an existing claim (create-then-upload
     * flow). When the rule fails, the validator throws the same
     * {@code documentIds} field error the old create path produced, keeping the
     * frontend error mapping intact.</p>
     *
     * @param claim the claim in its post-update state (new amount/category applied)
     */
    private void validateReceiptRequirement(ExpenseClaim claim) {
        BigDecimal activeThreshold = policyConfigClient.getActiveReceiptThreshold(claim.getCategory());
        // Only successfully stored documents count: a FAILED upload row is not a receipt
        List<String> attachedDocumentIds = expenseDocumentRepository
                .findByExpenseClaimIdAndDeletedFalse(claim.getId())
                .stream()
                .filter(document -> document.getUploadStatus() == UploadStatus.CONFIRMED)
                .map(document -> String.valueOf(document.getId()))
                .toList();
        policyValidator.validateReceiptRequirement(claim.getAmount(), activeThreshold, attachedDocumentIds);
    }

    @Override
    @Transactional
    public ExpenseClaimResponse submitForReview(String expenseClaimId, Long requesterId) {
        ExpenseClaim claim = expenseClaimRepository.findByIdAndDeletedFalse(Long.parseLong(expenseClaimId))
                .orElseThrow(() -> new ResourceNotFoundException("expenseClaimId",
                        "Expense claim not found: " + expenseClaimId));

        requireOwner(claim, requesterId, "submit this expense claim");
        // DRAFT -> PENDING, or REJECTED -> PENDING; anything else (incl. a second submit) is refused
        policyValidator.validateSubmittable(claim.getStatus());
        boolean resubmission = claim.getStatus() == ExpenseClaimStatus.REJECTED;

        // Re-validate everything at the moment the claim enters Finance review: the trip may have
        // been cancelled and the data/receipts may have changed since the draft was created.
        policyValidator.validateBaseCurrency(claim.getCurrency());
        travelRequestClient.validateTravelRequestApprovedAndOwned(
                String.valueOf(claim.getTravelRequestId()), String.valueOf(claim.getEmployeeId()));
        validateExpenseDateInRange(String.valueOf(claim.getTravelRequestId()), claim.getExpenseDate());
        validateReceiptRequirement(claim);
        claim.setDuplicateFlag(duplicateExpenseDetector.isPotentialDuplicateExcluding(
                claim.getTravelRequestId(), claim.getAmount(), claim.getExpenseDate(),
                claim.getCategory(), claim.getId()));

        // The previous rejection reason stays visible until Finance decides again (review clears/replaces it)
        claim.setStatus(ExpenseClaimStatus.PENDING);
        // saveAndFlush: a concurrent submit/edit (stale @Version) fails here and maps to 409
        ExpenseClaim saved = expenseClaimRepository.saveAndFlush(claim);
        logger.info("Expense claim {} {} for Finance review", saved.getId(), resubmission ? "resubmitted" : "submitted");

        publishExpenseClaimEvent(saved, resubmission ? "RESUBMITTED" : "SUBMITTED");
        return expenseClaimMapper.toResponse(saved, expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContent downloadDocument(String expenseClaimId, Long documentId, Long requesterId,
            Set<String> requesterRoles) {
        ExpenseClaim claim = expenseClaimRepository.findByIdAndDeletedFalse(Long.parseLong(expenseClaimId))
                .orElseThrow(() -> new ResourceNotFoundException("expenseClaimId",
                        "Expense claim not found: " + expenseClaimId));
        // Same rule as GET /{id}: the owner, or FINANCE/ADMIN
        assertClaimVisible(claim, requesterId, requesterRoles);
        ExpenseDocument document = expenseDocumentRepository
                .findByIdAndExpenseClaimIdAndDeletedFalse(documentId, claim.getId())
                .orElseThrow(() -> new ResourceNotFoundException("documentId", "Document not found: " + documentId));
        return expenseDocumentService.readContent(document);
    }

    /** Record-level read access: FINANCE/ADMIN review every claim; everyone else only their own. */
    private void assertClaimVisible(ExpenseClaim claim, Long requesterId, Set<String> requesterRoles) {
        Set<String> roles = requesterRoles == null ? Set.of() : requesterRoles;
        if (!roles.contains(Roles.FINANCE) && !roles.contains(Roles.ADMIN)) {
            requireOwner(claim, requesterId, "view this expense claim");
        }
    }

    /** Throws 403 unless the caller is the claim's owner. */
    private void requireOwner(ExpenseClaim claim, Long requesterId, String action) {
        if (requesterId == null || !requesterId.equals(claim.getEmployeeId())) {
            throw new ForbiddenActionException(action, "the claim owner");
        }
    }

    /** Validates that the claim is in an editable status (VAL-13). */
    private void validateEditPermission(ExpenseClaim claim) {
        policyValidator.validateEditable(claim.getStatus());
    }

    /**
     * Publishes a domain event for async consumption by notification and audit services.
     * Events are emitted after the transaction commits (per Constitution §9, §11).
     */
    private void publishExpenseClaimEvent(ExpenseClaim claim, String eventType) {
        logger.debug("Publishing event: type={}, claimId={}", eventType, claim.getId());
        DomainEventEnvelope event = new DomainEventEnvelope(
                "EXPENSE_CLAIM",
                claim.getId().toString(),
                eventType,
                Instant.now());
        eventPublisher.publishEvent(event);
    }
}
