package com.travelease.expense.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.validation.BusinessConstants;
import com.travelease.expense.entity.ExpenseClaimStatus;

import org.springframework.stereotype.Component;

/**
 * Centralized business-rule/policy checks for expense claims (tasks.md J-04;
 * spec.md FR-EXP-03, FR-EXP-05, FR-EXP-06, NFR-05, VAL-06, VAL-08, VAL-10, VAL-13),
 * invoked from {@code ExpenseClaimServiceImpl} (H-05) rather than inlined there, so
 * each rule is independently unit-testable (Constitution §10).
 *
 * <p>Approved-trip ownership (VAL-09) is a cross-service check and stays in
 * {@code TravelRequestClient} (Constitution §2 - no cross-service DB access); it is
 * not duplicated here.</p>
 */
@Component
public class ExpenseClaimPolicyValidator {

    /**
     * NFR-05 (spec.md §5; TC-VAL-06, VAL-06; clarification Q18): the MVP supports a
     * single base currency only — {@link BusinessConstants#BASE_CURRENCY}. There is
     * no automatic currency conversion: foreign-currency amounts must be manually
     * pre-converted to the base currency by the employee before entry, so a missing
     * or non-base currency is rejected at the service layer.
     *
     * @param currency currency code of the claimed amount
     * @throws PolicyViolationException (field {@code "currency"}) if the currency is
     *         missing or is not the base currency
     */
    public void validateBaseCurrency(String currency) {
        if (currency == null || currency.isBlank()
                || !BusinessConstants.BASE_CURRENCY.equalsIgnoreCase(currency)) {
            throw new PolicyViolationException("currency",
                    "Only the base currency " + BusinessConstants.BASE_CURRENCY
                            + " is accepted; foreign-currency amounts must be pre-converted before entry (NFR-05, Q18)");
        }
    }

    /**
     * VAL-08: the expense date must fall within the linked travel request's
     * approved date range (FR-EXP-03).
     *
     * @param expenseDate   date the expense was incurred
     * @param tripStartDate linked travel request's start date
     * @param tripEndDate   linked travel request's end date
     * @throws PolicyViolationException if the expense date falls outside the range
     */
    public void validateExpenseDateInRange(LocalDate expenseDate, LocalDate tripStartDate, LocalDate tripEndDate) {
        if (expenseDate.isBefore(tripStartDate) || expenseDate.isAfter(tripEndDate)) {
            throw new PolicyViolationException("expenseDate",
                    "Expense date must fall within travel request date range: " + tripStartDate + " to " + tripEndDate);
        }
    }

    /**
     * VAL-10: a supporting document is required when the claimed amount exceeds the
     * category's configured receipt threshold (FR-EXP-05). A {@code null} threshold
     * means no active policy is configured for the category, so no receipt is
     * mandated.
     *
     * @param amount         claimed amount
     * @param activeThreshold the category's active receipt threshold, or {@code null} if unconfigured
     * @param documentIds    document references attached to the claim
     * @throws PolicyViolationException if the amount exceeds the threshold and no document is attached
     */
    public void validateReceiptRequirement(BigDecimal amount, BigDecimal activeThreshold, List<String> documentIds) {
        if (activeThreshold == null) {
            return;
        }
        boolean exceedsThreshold = amount.compareTo(activeThreshold) > 0;
        boolean hasDocument = documentIds != null && !documentIds.isEmpty();
        if (exceedsThreshold && !hasDocument) {
            throw new PolicyViolationException("documentIds",
                    "A supporting document is required when the amount exceeds the category threshold (VAL-10)");
        }
    }

    /**
     * VAL-13: an expense claim may only be edited while {@code Pending} (FR-EXP-06).
     *
     * @param status current status of the expense claim being edited
     * @throws PolicyViolationException if the claim is not {@code Pending}
     */
    /**
     * VAL-13: a claim can be edited while DRAFT, while PENDING (before Finance decides), or while
     * REJECTED (to correct it before resubmitting). APPROVED/REIMBURSED claims are final.
     */
    public void validateEditable(ExpenseClaimStatus status) {
        if (status != ExpenseClaimStatus.DRAFT && status != ExpenseClaimStatus.PENDING
                && status != ExpenseClaimStatus.REJECTED) {
            throw new PolicyViolationException(
                    "Expense claim can only be edited while DRAFT, PENDING or REJECTED; current status: " + status);
        }
    }

    /** Only a DRAFT (first submission) or a REJECTED claim (resubmission) can be sent to Finance review. */
    public void validateSubmittable(ExpenseClaimStatus status) {
        if (status != ExpenseClaimStatus.DRAFT && status != ExpenseClaimStatus.REJECTED) {
            throw new PolicyViolationException(
                    "Only DRAFT or REJECTED expense claims can be submitted for review; current status: " + status);
        }
    }

    /** Supporting documents can be attached while the claim is still changeable by its owner. */
    public void validateDocumentsAttachable(ExpenseClaimStatus status) {
        if (status != ExpenseClaimStatus.DRAFT && status != ExpenseClaimStatus.PENDING
                && status != ExpenseClaimStatus.REJECTED) {
            throw new PolicyViolationException(
                    "Documents can only be attached while the claim is DRAFT, PENDING or REJECTED; current status: "
                            + status);
        }
    }
}
