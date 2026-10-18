package com.travelease.expense.repository;

import java.util.List;
import java.util.Optional;

import com.travelease.expense.entity.ExpenseDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for expense claim supporting documents
 * (tasks.md D-05; data-model.md §4.2).
 *
 * <p>Finders scope to non-deleted rows ({@code deleted = false}) so soft-deleted
 * documents are excluded from all reads (NFR-04). {@link #countByExpenseClaimIdAndDeletedFalse}
 * backs the max-5-documents-per-claim validation (VAL-11, FR-DOC-01).</p>
 */
@Repository
public interface ExpenseDocumentRepository extends JpaRepository<ExpenseDocument, Long> {

    /**
     * All non-deleted documents attached to an expense claim (FR-DOC-01, AC-11).
     *
     * @param expenseClaimId owning expense claim id
     * @return non-deleted documents for the given expense claim
     */
    List<ExpenseDocument> findByExpenseClaimIdAndDeletedFalse(Long expenseClaimId);

    /** Loads one document only if it belongs to the given claim (no cross-claim document access). */
    Optional<ExpenseDocument> findByIdAndExpenseClaimIdAndDeletedFalse(Long id, Long expenseClaimId);

    /**
     * Count of non-deleted documents already attached to an expense claim, used to
     * enforce the 5-file-per-claim limit before accepting a new upload (VAL-11).
     *
     * @param expenseClaimId owning expense claim id
     * @return number of non-deleted documents currently attached
     */
    long countByExpenseClaimIdAndDeletedFalse(Long expenseClaimId);
}