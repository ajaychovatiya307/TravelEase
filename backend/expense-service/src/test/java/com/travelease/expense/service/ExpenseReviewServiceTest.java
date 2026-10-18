package com.travelease.expense.service;

import com.travelease.common.entity.DecisionType;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.event.DomainEventEnvelope;
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
import com.travelease.expense.service.impl.ExpenseReviewServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpenseReviewServiceImpl} (tasks.md L-06; spec.md FR-EXP-07,
 * FR-FIN-01, FR-FIN-02, FR-FIN-03; AC-09, AC-10), covering the Finance review and
 * reimbursement workflow with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>approving a {@code PENDING} claim: status becomes {@code APPROVED}, any prior
 *       rejection reason is cleared, an {@code EXPENSE_CLAIM}/{@code APPROVED}
 *       {@link DomainEventEnvelope} is published, and the mapped response is returned
 *       (FR-FIN-01, AC-09)</li>
 *   <li>rejecting a {@code PENDING} claim with a reason: status becomes
 *       {@code REJECTED}, the reason is stored, and a {@code REJECTED} event is
 *       published (FR-FIN-02, AC-09)</li>
 *   <li>rejecting without a reason (null or blank): a {@link ValidationException} on
 *       field {@code reason} is thrown and nothing is saved (VAL-12)</li>
 *   <li>reviewing a non-{@code PENDING} claim: a {@link PolicyViolationException} is
 *       thrown and nothing is saved (FR-FIN-02)</li>
 *   <li>claim not found: a {@link ResourceNotFoundException} is thrown (AC-13)</li>
 *   <li>reimbursing an {@code APPROVED} claim with a provided timestamp: status becomes
 *       {@code REIMBURSED}, {@code reimbursedAt} equals the provided value, and a
 *       {@code REIMBURSED} event is published (FR-FIN-03, AC-10)</li>
 *   <li>reimbursing with a null timestamp: {@code reimbursedAt} defaults to the server
 *       time (FR-FIN-03)</li>
 *   <li>reimbursing a non-{@code APPROVED} claim: a {@link PolicyViolationException}
 *       is thrown and nothing is saved (FR-FIN-03)</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so both repositories, the mapper and the
 * {@link ApplicationEventPublisher} are mocked with Mockito and the service is
 * injected via {@code @InjectMocks} - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseReviewServiceImpl reviewExpenseClaim / markReimbursed (tasks.md L-06; spec.md FR-EXP-07, FR-FIN-01, FR-FIN-02, FR-FIN-03; AC-09, AC-10)")
class ExpenseReviewServiceTest {

    private static final Long EXPENSE_CLAIM_ID = 20L;
    private static final String EXPENSE_CLAIM_ID_STR = "20";
    private static final Long EMPLOYEE_ID = 1L;

    /** Finance reviewer acting on the claim; deliberately not the claim owner. */
    private static final Long FINANCE_REVIEWER_ID = 4L;
    private static final Long TRAVEL_REQUEST_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");
    private static final String CURRENCY = "EUR";
    private static final String CATEGORY = "MEAL";
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2025, 6, 15);
    private static final String REJECTION_REASON = "Receipt missing";
    private static final Instant PROVIDED_REIMBURSED_AT = Instant.parse("2025-06-21T14:00:00Z");

    @Mock
    private ExpenseClaimRepository expenseClaimRepository;

    @Mock
    private ExpenseDocumentRepository expenseDocumentRepository;

    @Mock
    private ExpenseClaimMapper expenseClaimMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExpenseReviewServiceImpl expenseReviewService;

    private static ExpenseClaim pendingClaim() {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setId(EXPENSE_CLAIM_ID);
        claim.setEmployeeId(EMPLOYEE_ID);
        claim.setTravelRequestId(TRAVEL_REQUEST_ID);
        claim.setAmount(AMOUNT);
        claim.setCurrency(CURRENCY);
        claim.setCategory(CATEGORY);
        claim.setExpenseDate(EXPENSE_DATE);
        claim.setStatus(ExpenseClaimStatus.PENDING);
        claim.setDeleted(false);
        return claim;
    }

    private static ExpenseClaim approvedClaim() {
        ExpenseClaim claim = pendingClaim();
        claim.setStatus(ExpenseClaimStatus.APPROVED);
        return claim;
    }

    private static ExpenseReviewRequest reviewRequest(DecisionType decision, String reason) {
        return new ExpenseReviewRequest(decision, reason);
    }

    private static ExpenseReimburseRequest reimburseRequest(Instant reimbursedAt) {
        return new ExpenseReimburseRequest(reimbursedAt);
    }

    private static ExpenseClaimResponse response() {
        return new ExpenseClaimResponse(EXPENSE_CLAIM_ID_STR, String.valueOf(EMPLOYEE_ID),
                String.valueOf(TRAVEL_REQUEST_ID), AMOUNT, CURRENCY, CATEGORY, EXPENSE_DATE,
                ExpenseClaimStatus.APPROVED, false, null, null, List.of(), null, null);
    }

    @Nested
    @DisplayName("reviewExpenseClaim — positive cases (FR-FIN-01, FR-FIN-02, AC-09)")
    class ReviewPositiveCases {

        @Test
        @DisplayName("approves a PENDING claim: status APPROVED, rejectionReason cleared, EXPENSE_CLAIM/APPROVED event published, mapped response returned")
        void approvesPendingClaim() {
            ExpenseClaim claim = pendingClaim();
            ExpenseClaimResponse expected = response();

            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            when(expenseClaimRepository.save(claim)).thenReturn(claim);
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(List.of());
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual = expenseReviewService.reviewExpenseClaim(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID,
                    reviewRequest(DecisionType.APPROVED, null));

            assertThat(actual).isSameAs(expected);
            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.APPROVED);
            assertThat(claim.getRejectionReason()).isNull();

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("EXPENSE_CLAIM");
            assertThat(event.entityId()).isEqualTo(EXPENSE_CLAIM_ID_STR);
            assertThat(event.action()).isEqualTo("APPROVED");
            assertThat(event.timestamp()).isNotNull();
            assertThat(event.recipientIds()).containsExactly(claim.getEmployeeId());

            verify(expenseClaimRepository).save(claim);
            verify(expenseClaimMapper).toResponse(claim, List.of());
        }

        @Test
        @DisplayName("rejects a PENDING claim with a reason: status REJECTED, reason stored, REJECTED event published, mapped response returned")
        void rejectsPendingClaimWithReason() {
            ExpenseClaim claim = pendingClaim();
            ExpenseClaimResponse expected = response();

            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            when(expenseClaimRepository.save(claim)).thenReturn(claim);
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(List.of());
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual = expenseReviewService.reviewExpenseClaim(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID,
                    reviewRequest(DecisionType.REJECTED, REJECTION_REASON));

            assertThat(actual).isSameAs(expected);
            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.REJECTED);
            assertThat(claim.getRejectionReason()).isEqualTo(REJECTION_REASON);

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("EXPENSE_CLAIM");
            assertThat(event.entityId()).isEqualTo(EXPENSE_CLAIM_ID_STR);
            assertThat(event.action()).isEqualTo("REJECTED");
            assertThat(event.timestamp()).isNotNull();
            assertThat(event.recipientIds()).containsExactly(claim.getEmployeeId());

            verify(expenseClaimRepository).save(claim);
            verify(expenseClaimMapper).toResponse(claim, List.of());
        }
    }

    @Nested
    @DisplayName("reviewExpenseClaim — negative cases (VAL-12, FR-FIN-02; Constitution §10)")
    class ReviewNegativeCases {

        @Test
        @DisplayName("rejects a REJECTED decision without a reason (VAL-12): null and blank reason both throw a ValidationException on field \"reason\"; nothing is saved")
        void rejectsDecisionWithoutReason() {
            ExpenseClaim claim = pendingClaim();
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));

            for (String reason : new String[] {null, "   "}) {
                ExpenseReviewRequest request = reviewRequest(DecisionType.REJECTED, reason);
                assertThatThrownBy(() -> expenseReviewService.reviewExpenseClaim(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID, request))
                        .isInstanceOfSatisfying(ValidationException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                            assertThat(ex.getFieldErrors())
                                    .extracting(ErrorResponse.FieldError::field)
                                    .containsExactly("reason");
                        });
            }

            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(expenseDocumentRepository, expenseClaimMapper, eventPublisher);
        }

        @Test
        @DisplayName("rejects reviewing a non-PENDING claim with a PolicyViolationException; nothing is saved and no event is published")
        void rejectsReviewOfNonPendingClaim() {
            ExpenseClaim claim = approvedClaim();
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));

            ExpenseReviewRequest approveRequest = reviewRequest(DecisionType.APPROVED, null);

            assertThatThrownBy(() -> expenseReviewService.reviewExpenseClaim(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID, approveRequest))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining("PENDING");

            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.APPROVED);
            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(expenseDocumentRepository, expenseClaimMapper, eventPublisher);
        }

        @Test
        @DisplayName("rejects reviewing a non-existent claim with a ResourceNotFoundException; nothing is saved")
        void rejectsReviewOfNonExistentClaim() {
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.empty());

            ExpenseReviewRequest approveRequest = reviewRequest(DecisionType.APPROVED, null);

            assertThatThrownBy(() -> expenseReviewService.reviewExpenseClaim(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID, approveRequest))
                    .isInstanceOfSatisfying(ResourceNotFoundException.class,
                            ex -> assertThat(ex.getMessage()).contains(EXPENSE_CLAIM_ID_STR));

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(expenseDocumentRepository, expenseClaimMapper, eventPublisher);
        }
    }

    @Nested
    @DisplayName("markReimbursed — positive cases (FR-FIN-03, AC-10)")
    class ReimbursePositiveCases {

        @Test
        @DisplayName("reimburses an APPROVED claim with a provided timestamp: status REIMBURSED, reimbursedAt equals the provided value, REIMBURSED event published")
        void reimbursesApprovedClaimWithProvidedTimestamp() {
            ExpenseClaim claim = approvedClaim();
            ExpenseClaimResponse expected = response();

            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            when(expenseClaimRepository.save(claim)).thenReturn(claim);
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(List.of());
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            ExpenseClaimResponse actual = expenseReviewService.markReimbursed(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID,
                    reimburseRequest(PROVIDED_REIMBURSED_AT));

            assertThat(actual).isSameAs(expected);
            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.REIMBURSED);
            assertThat(claim.getReimbursedAt()).isEqualTo(PROVIDED_REIMBURSED_AT);

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DomainEventEnvelope event = eventCaptor.getValue();
            assertThat(event.entityType()).isEqualTo("EXPENSE_CLAIM");
            assertThat(event.entityId()).isEqualTo(EXPENSE_CLAIM_ID_STR);
            assertThat(event.action()).isEqualTo("REIMBURSED");
            assertThat(event.timestamp()).isNotNull();
            assertThat(event.recipientIds()).containsExactly(claim.getEmployeeId());

            verify(expenseClaimRepository).save(claim);
            verify(expenseClaimMapper).toResponse(claim, List.of());
        }

        @Test
        @DisplayName("reimburses an APPROVED claim with a null timestamp: reimbursedAt defaults to the server time")
        void reimbursesApprovedClaimWithDefaultServerTime() {
            ExpenseClaim claim = approvedClaim();
            ExpenseClaimResponse expected = response();

            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));
            when(expenseClaimRepository.save(claim)).thenReturn(claim);
            when(expenseDocumentRepository.findByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(List.of());
            when(expenseClaimMapper.toResponse(any(ExpenseClaim.class), anyList())).thenReturn(expected);

            Instant before = Instant.now();
            ExpenseClaimResponse actual = expenseReviewService.markReimbursed(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID,
                    reimburseRequest(null));
            Instant after = Instant.now();

            assertThat(actual).isSameAs(expected);
            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.REIMBURSED);
            Instant actualReimbursedAt = claim.getReimbursedAt();
            assertThat(actualReimbursedAt)
                    .isNotNull()
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);

            ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().action()).isEqualTo("REIMBURSED");

            verify(expenseClaimRepository).save(claim);
        }
    }

    @Nested
    @DisplayName("markReimbursed — negative cases (FR-FIN-03; Constitution §10)")
    class ReimburseNegativeCases {

        @Test
        @DisplayName("rejects reimbursing a non-APPROVED claim with a PolicyViolationException; nothing is saved and no event is published")
        void rejectsReimburseOfNonApprovedClaim() {
            ExpenseClaim claim = pendingClaim();
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(claim));

            ExpenseReimburseRequest request = reimburseRequest(PROVIDED_REIMBURSED_AT);

            assertThatThrownBy(() -> expenseReviewService.markReimbursed(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID, request))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining("APPROVED");

            assertThat(claim.getStatus()).isEqualTo(ExpenseClaimStatus.PENDING);
            assertThat(claim.getReimbursedAt()).isNull();
            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(expenseDocumentRepository, expenseClaimMapper, eventPublisher);
        }

        @Test
        @DisplayName("rejects reimbursing a non-existent claim with a ResourceNotFoundException; nothing is saved")
        void rejectsReimburseOfNonExistentClaim() {
            when(expenseClaimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.empty());

            ExpenseReimburseRequest request = reimburseRequest(PROVIDED_REIMBURSED_AT);

            assertThatThrownBy(() -> expenseReviewService.markReimbursed(EXPENSE_CLAIM_ID_STR, FINANCE_REVIEWER_ID, request))
                    .isInstanceOfSatisfying(ResourceNotFoundException.class,
                            ex -> assertThat(ex.getMessage()).contains(EXPENSE_CLAIM_ID_STR));

            verify(expenseClaimRepository, never()).save(any(ExpenseClaim.class));
            verifyNoInteractions(expenseDocumentRepository, expenseClaimMapper, eventPublisher);
        }
    }
}