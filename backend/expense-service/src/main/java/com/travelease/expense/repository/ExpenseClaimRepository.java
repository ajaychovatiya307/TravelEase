package com.travelease.expense.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for expense claims (tasks.md D-04; data-model.md §4.1).
 *
 * <p>Every finder method scopes to non-deleted rows ({@code deleted = false}) by
 * including {@code AndDeletedFalse} in its derived query name, so soft-deleted
 * claims are excluded from all reads (NFR-04). All multi-row finders are
 * paginated to keep review/approval queues query-performant (NFR-07).</p>
 */
@Repository
public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {

    /**
     * Fetch a single claim, excluding soft-deleted rows (AC-13).
     *
     * @param id primary key of the expense claim
     * @return the non-deleted claim with the given id, or empty if absent
     */
    Optional<ExpenseClaim> findByIdAndDeletedFalse(Long id);

    /**
     * An employee's own claims, paginated, excluding soft-deleted rows.
     *
     * @param employeeId owning employee (logical UserAccount reference)
     * @param pageable pagination and sort parameters
     * @return page of the employee's non-deleted claims
     */
    Page<ExpenseClaim> findByEmployeeIdAndDeletedFalse(Long employeeId, Pageable pageable);

    /**
     * The finance review queue, paginated, excluding soft-deleted rows (FR-FIN-01, FR-FIN-02).
     *
     * @param status lifecycle status to match
     * @param pageable pagination and sort parameters
     * @return page of non-deleted claims in the given status
     */
    Page<ExpenseClaim> findByStatusAndDeletedFalse(ExpenseClaimStatus status, Pageable pageable);

    Page<ExpenseClaim> findByStatusAndDuplicateFlagAndDeletedFalse(
            ExpenseClaimStatus status, boolean duplicateFlag, Pageable pageable);

    /**
     * An employee's claims filtered by lifecycle status, paginated,
     * excluding soft-deleted rows.
     *
     * @param employeeId owning employee (logical UserAccount reference)
     * @param status lifecycle status to match
     * @param pageable pagination and sort parameters
     * @return page of the employee's non-deleted claims in the given status
     */
    Page<ExpenseClaim> findByEmployeeIdAndStatusAndDeletedFalse(Long employeeId, ExpenseClaimStatus status,
            Pageable pageable);

    Page<ExpenseClaim> findByEmployeeIdAndDuplicateFlagAndDeletedFalse(
            Long employeeId, boolean duplicateFlag, Pageable pageable);

    Page<ExpenseClaim> findByEmployeeIdAndStatusAndDuplicateFlagAndDeletedFalse(
            Long employeeId, ExpenseClaimStatus status, boolean duplicateFlag, Pageable pageable);

    /**
     * All non-deleted claims filtered by duplicate flag, paginated (FR-EXP-07).
     *
     * @param duplicateFlag duplicate flag to match
     * @param pageable pagination and sort parameters
     * @return page of non-deleted claims with the given duplicate flag
     */
    Page<ExpenseClaim> findByDuplicateFlagAndDeletedFalse(boolean duplicateFlag, Pageable pageable);

    /**
     * All non-deleted claims, paginated (NFR-04).
     *
     * @param pageable pagination and sort parameters
     * @return page of all non-deleted claims
     */
    Page<ExpenseClaim> findByDeletedFalse(Pageable pageable);

    /** All non-deleted claims except one status (Finance/Admin lists exclude private DRAFTs). */
    Page<ExpenseClaim> findByStatusNotAndDeletedFalse(ExpenseClaimStatus status, Pageable pageable);

    /** As {@link #findByStatusNotAndDeletedFalse} with a duplicate-flag filter. */
    Page<ExpenseClaim> findByStatusNotAndDuplicateFlagAndDeletedFalse(ExpenseClaimStatus status,
            boolean duplicateFlag, Pageable pageable);

    /**
     * Duplicate candidates for the given trip: non-deleted claims under the same
     * travel request with identical amount, expense date and category (FR-EXP-07;
     * consumed by the duplicate detector, tasks.md J-05).
     *
     * @param travelRequestId logical, cross-service reference to the owning travel request
     * @param amount claim amount to match
     * @param expenseDate expense date to match
     * @param category expense category code to match
     * @return all matching non-deleted claims (may be empty)
     */
    List<ExpenseClaim> findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalse(
            Long travelRequestId, BigDecimal amount, LocalDate expenseDate, String category);

    /**
     * Duplicate candidates for the given trip excluding one claim - the variant used
     * when an existing claim is edited, so the claim itself is not reported as its own
     * duplicate (FR-EXP-07).
     *
     * @param travelRequestId logical, cross-service reference to the owning travel request
     * @param amount claim amount to match
     * @param expenseDate expense date to match
     * @param category expense category code to match
     * @param id primary key of the claim to exclude
     * @return all matching non-deleted claims other than the excluded one (may be empty)
     */
    List<ExpenseClaim> findByTravelRequestIdAndAmountAndExpenseDateAndCategoryAndDeletedFalseAndIdNot(
            Long travelRequestId, BigDecimal amount, LocalDate expenseDate, String category, Long id);
}