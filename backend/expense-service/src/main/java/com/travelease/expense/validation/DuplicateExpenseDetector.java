package com.travelease.expense.validation;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.travelease.expense.repository.ExpenseClaimRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Potential-duplicate detection for expense claims (tasks.md J-05; spec.md FR-EXP-07,
 * BR-10). Flags, never blocks - the caller ({@code ExpenseClaimServiceImpl}, H-05)
 * uses the boolean result to set {@code ExpenseClaim.duplicateFlag} for manual
 * Finance review; it never throws to reject a submission.
 *
 * <p>Read-only; scoped to non-deleted claims under the same travel request with
 * identical amount, expense date and category (data-model.md §4.1 recommended index).</p>
 */
@Component
public class DuplicateExpenseDetector {

    private final ExpenseClaimRepository expenseClaimRepository;

    public DuplicateExpenseDetector(ExpenseClaimRepository expenseClaimRepository) {
        this.expenseClaimRepository = expenseClaimRepository;
    }

    /**
     * Whether a new claim with the given attributes matches an existing non-deleted
     * claim under the same travel request (FR-EXP-07).
     *
     * @param travelRequestId logical, cross-service reference to the owning travel request
     * @param amount claim amount
     * @param expenseDate expense date
     * @param category expense category code
     * @return true if at least one matching claim already exists
     */
    @Transactional(readOnly = true)
    public boolean isPotentialDuplicate(Long travelRequestId, BigDecimal amount, LocalDate expenseDate,
            String category) {
        return !expenseClaimRepository
                .findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalse(travelRequestId, amount,
                        expenseDate, category)
                .isEmpty();
    }

    /**
     * Same check as {@link #isPotentialDuplicate}, excluding the claim being edited
     * so it is never reported as its own duplicate (FR-EXP-07, used when editing a
     * {@code Pending} claim, VAL-13).
     *
     * @param travelRequestId logical, cross-service reference to the owning travel request
     * @param amount claim amount
     * @param expenseDate expense date
     * @param category expense category code
     * @param excludeClaimId primary key of the claim being edited
     * @return true if at least one other matching claim exists
     */
    @Transactional(readOnly = true)
    public boolean isPotentialDuplicateExcluding(Long travelRequestId, BigDecimal amount, LocalDate expenseDate,
            String category, Long excludeClaimId) {
        return !expenseClaimRepository
                .findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalseAndIdNot(travelRequestId,
                        amount, expenseDate, category, excludeClaimId)
                .isEmpty();
    }
}
