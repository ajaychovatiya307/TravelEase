package com.travelease.expense.validation;

import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.repository.ExpenseClaimRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DuplicateExpenseDetector} (tasks.md L-06; spec.md FR-EXP-07,
 * FR-FIN-01, FR-FIN-02, FR-FIN-03; AC-09, AC-10), covering the non-blocking
 * duplicate-detection queries used when submitting and editing expense claims:
 *
 * <ul>
 *   <li>no existing claim matches the new claim's attributes: the detector returns
 *       {@code false} and the repository is queried with the exact
 *       travel-request/amount/date/category arguments (FR-EXP-07)</li>
 *   <li>an existing claim matches: the detector returns {@code true} - it only flags,
 *       never throws, so the caller can set {@code duplicateFlag} for manual Finance
 *       review (FR-EXP-07, AC-09)</li>
 *   <li>edit variant: the detector excludes the claim being edited, so a claim is
 *       never reported as its own duplicate - no other match returns {@code false}
 *       with the {@code AndIdNot} query receiving the exclude id, and another
 *       matching claim returns {@code true} (FR-EXP-07)</li>
 * </ul>
 *
 * <p>The detector is plain (non-Spring) read-only logic with a constructor-injected
 * repository, so only the {@code ExpenseClaimRepository} is mocked and the detector
 * is injected via {@code @InjectMocks} - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DuplicateExpenseDetector isPotentialDuplicate / isPotentialDuplicateExcluding (tasks.md L-06; spec.md FR-EXP-07, FR-FIN-01, FR-FIN-02, FR-FIN-03; AC-09, AC-10)")
class DuplicateExpenseDetectorTest {

    private static final Long TRAVEL_REQUEST_ID = 10L;
    private static final Long EXISTING_CLAIM_ID = 20L;
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2025, 6, 15);
    private static final String CATEGORY = "MEAL";

    @Mock
    private ExpenseClaimRepository expenseClaimRepository;

    @InjectMocks
    private DuplicateExpenseDetector duplicateExpenseDetector;

    private static ExpenseClaim existingClaim() {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setId(EXISTING_CLAIM_ID);
        claim.setTravelRequestId(TRAVEL_REQUEST_ID);
        claim.setAmount(AMOUNT);
        claim.setCurrency("EUR");
        claim.setCategory(CATEGORY);
        claim.setExpenseDate(EXPENSE_DATE);
        claim.setStatus(ExpenseClaimStatus.PENDING);
        claim.setDeleted(false);
        return claim;
    }

    @Nested
    @DisplayName("isPotentialDuplicate (FR-EXP-07, AC-09)")
    class IsPotentialDuplicate {

        @Test
        @DisplayName("returns false for a first claim whose attributes match no existing claim; the query is called with the exact arguments")
        void returnsFalseWhenNoExistingMatch() {
            when(expenseClaimRepository.findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalse(
                    TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY)).thenReturn(List.of());

            boolean result = duplicateExpenseDetector.isPotentialDuplicate(TRAVEL_REQUEST_ID, AMOUNT,
                    EXPENSE_DATE, CATEGORY);

            assertThat(result).isFalse();
            verify(expenseClaimRepository)
                    .findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalse(
                            TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY);
        }

        @Test
        @DisplayName("returns true when an identical non-deleted claim already exists (flag, never throw - FR-EXP-07, AC-09)")
        void returnsTrueWhenExistingMatch() {
            when(expenseClaimRepository.findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalse(
                    TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY))
                    .thenReturn(List.of(existingClaim()));

            boolean result = duplicateExpenseDetector.isPotentialDuplicate(TRAVEL_REQUEST_ID, AMOUNT,
                    EXPENSE_DATE, CATEGORY);

            assertThat(result).isTrue();
            verify(expenseClaimRepository)
                    .findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalse(
                            TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY);
        }
    }

    @Nested
    @DisplayName("isPotentialDuplicateExcluding (FR-EXP-07, VAL-13)")
    class IsPotentialDuplicateExcluding {

        @Test
        @DisplayName("returns false when editing a claim with no OTHER match; the AndIdNot query receives the exclude id")
        void returnsFalseWhenOnlyTheClaimItselfMatches() {
            when(expenseClaimRepository.findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalseAndIdNot(
                    TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY, EXISTING_CLAIM_ID)).thenReturn(List.of());

            boolean result = duplicateExpenseDetector.isPotentialDuplicateExcluding(TRAVEL_REQUEST_ID, AMOUNT,
                    EXPENSE_DATE, CATEGORY, EXISTING_CLAIM_ID);

            assertThat(result).isFalse();
            verify(expenseClaimRepository)
                    .findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalseAndIdNot(
                            TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY, EXISTING_CLAIM_ID);
        }

        @Test
        @DisplayName("returns true when another matching claim exists besides the one being edited (FR-EXP-07, AC-09)")
        void returnsTrueWhenOtherMatchExists() {
            ExpenseClaim otherClaim = existingClaim();
            otherClaim.setId(EXISTING_CLAIM_ID + 1L);
            when(expenseClaimRepository.findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalseAndIdNot(
                    TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY, EXISTING_CLAIM_ID))
                    .thenReturn(List.of(otherClaim));

            boolean result = duplicateExpenseDetector.isPotentialDuplicateExcluding(TRAVEL_REQUEST_ID, AMOUNT,
                    EXPENSE_DATE, CATEGORY, EXISTING_CLAIM_ID);

            assertThat(result).isTrue();
            verify(expenseClaimRepository)
                    .findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalseAndIdNot(
                            TRAVEL_REQUEST_ID, AMOUNT, EXPENSE_DATE, CATEGORY, EXISTING_CLAIM_ID);
        }
    }
}