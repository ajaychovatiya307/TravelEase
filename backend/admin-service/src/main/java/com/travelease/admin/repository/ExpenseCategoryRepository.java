package com.travelease.admin.repository;

import java.util.List;
import java.util.Optional;

import com.travelease.admin.entity.ExpenseCategory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for expense categories (tasks.md D-08;
 * data-model.md §5.5; spec.md FR-ADM-02, FR-EXP-04, AC-08).
 *
 * <p>Finders scope to non-deleted rows ({@code deleted = false}) so soft-deleted
 * categories are excluded from all reads (NFR-04). {@link #findByCodeAndDeletedFalse}
 * resolves the unique category code used to validate claim categories (VAL-07),
 * {@link #existsByCodeAndDeletedFalse} backs the unique-code conflict check on
 * create/update (data-model.md §5.4/§5.5), and {@link #findByActiveTrueAndDeletedFalse}
 * feeds the Expense Service category validation (FR-EXP-04).</p>
 */
@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    /**
     * Fetch a single category, excluding soft-deleted rows (FR-ADM-02).
     *
     * @param id primary key of the expense category
     * @return the non-deleted category with the given id, or empty if absent
     */
    Optional<ExpenseCategory> findByIdAndDeletedFalse(Long id);

    /**
     * Lookup by unique category code, excluding soft-deleted rows; the code is the
     * key used to validate claim categories (VAL-07, FR-EXP-04).
     *
     * @param code unique category code (data-model.md §5.5)
     * @return the non-deleted category with the given code, or empty if absent
     */
    Optional<ExpenseCategory> findByCodeAndDeletedFalse(String code);

    /**
     * Uniqueness check for the unique {@code code} on category create/update,
     * excluding soft-deleted rows (data-model.md §5.4/§5.5, FR-ADM-02).
     *
     * @param code candidate category code
     * @return true if a non-deleted category with the given code exists
     */
    boolean existsByCodeAndDeletedFalse(String code);

    /**
     * Same unique-code conflict check, excluding the record being updated, so a
     * category can keep its own code (data-model.md §5.5, FR-ADM-02).
     *
     * @param code candidate category code
     * @param id   id of the category being updated
     * @return true if a different non-deleted category with the given code exists
     */
    boolean existsByCodeAndDeletedFalseAndIdNot(String code, Long id);

    /**
     * All active, non-deleted categories, consumed by the Expense Service
     * category validation (FR-EXP-04).
     *
     * @return active non-deleted expense categories
     */
    List<ExpenseCategory> findByActiveTrueAndDeletedFalse();

    /**
     * All non-deleted categories for admin listing, including inactive ones that
     * are retained for history (FR-ADM-02).
     *
     * @return all non-deleted expense categories
     */
    List<ExpenseCategory> findByDeletedFalse();

    /**
     * Paginated variant of {@link #findByDeletedFalse()}, backing the Admin expense
     * category list screen (api-contract.md §4.3 {@code GET /api/admin/expense-categories}).
     *
     * @param pageable pagination and sort parameters
     * @return page of non-deleted expense categories
     */
    Page<ExpenseCategory> findByDeletedFalse(Pageable pageable);
}