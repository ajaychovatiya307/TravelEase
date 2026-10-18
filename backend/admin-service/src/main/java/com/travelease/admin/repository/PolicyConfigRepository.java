package com.travelease.admin.repository;

import java.util.List;
import java.util.Optional;

import com.travelease.admin.entity.PolicyConfig;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for per-category policy configurations (tasks.md D-08;
 * data-model.md §5.4; spec.md FR-ADM-02, FR-EXP-05, AC-08).
 *
 * <p>Finders scope to non-deleted rows ({@code deleted = false}) so soft-deleted
 * configurations are excluded from all reads (NFR-04). {@link
 * #findByCategoryCodeAndActiveTrueAndDeletedFalse} resolves the single active
 * policy for a category - the receipt-threshold lookup used by the Expense Service
 * validation (FR-EXP-05, VAL-10) - and {@link #existsByCategoryCodeAndActiveTrueAndDeletedFalse}
 * backs the one-active-policy-per-category conflict check (FR-ADM-02).</p>
 */
@Repository
public interface PolicyConfigRepository extends JpaRepository<PolicyConfig, Long> {

    /**
     * Fetch a single policy configuration, excluding soft-deleted rows (FR-ADM-02).
     *
     * @param id primary key of the policy configuration
     * @return the non-deleted policy configuration with the given id, or empty if absent
     */
    Optional<PolicyConfig> findByIdAndDeletedFalse(Long id);

    /**
     * The single active policy for a category, excluding soft-deleted rows; the
     * receipt-threshold lookup consumed by the Expense Service validation
     * (FR-EXP-05, VAL-10).
     *
     * @param categoryCode plain identifier column referencing {@code ExpenseCategory.code}
     * @return the active non-deleted policy for the given category, or empty if absent
     */
    Optional<PolicyConfig> findByCategoryCodeAndActiveTrueAndDeletedFalse(String categoryCode);

    /**
     * One-active-policy-per-category conflict check for create/update, excluding
     * soft-deleted rows (FR-ADM-02).
     *
     * @param categoryCode plain identifier column referencing {@code ExpenseCategory.code}
     * @return true if an active non-deleted policy for the given category exists
     */
    boolean existsByCategoryCodeAndActiveTrueAndDeletedFalse(String categoryCode);

    /**
     * Same one-active-policy-per-category conflict check, excluding the record being
     * updated, so a config can keep its own active category assignment (FR-ADM-02).
     *
     * @param categoryCode plain identifier column referencing {@code ExpenseCategory.code}
     * @param id           id of the policy configuration being updated
     * @return true if a different active non-deleted policy for the given category exists
     */
    boolean existsByCategoryCodeAndActiveTrueAndDeletedFalseAndIdNot(String categoryCode, Long id);

    /**
     * All active, non-deleted policy configurations (FR-EXP-05).
     *
     * @return active non-deleted policy configurations
     */
    List<PolicyConfig> findByActiveTrueAndDeletedFalse();

    /**
     * All non-deleted policy configurations for admin listing, including inactive
     * ones that are retained for history (FR-ADM-02).
     *
     * @return all non-deleted policy configurations
     */
    List<PolicyConfig> findByDeletedFalse();

    /**
     * Paginated variant of {@link #findByDeletedFalse()}, backing the Admin policy
     * config list screen (api-contract.md §4.3 {@code GET /api/admin/policy-configs}).
     *
     * @param pageable pagination and sort parameters
     * @return page of non-deleted policy configurations
     */
    Page<PolicyConfig> findByDeletedFalse(Pageable pageable);
}