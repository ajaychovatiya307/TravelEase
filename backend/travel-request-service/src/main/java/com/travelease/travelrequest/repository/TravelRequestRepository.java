package com.travelease.travelrequest.repository;

import java.util.Optional;

import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for travel requests (tasks.md D-01; data-model.md §3.1).
 *
 * <p>Every finder method scopes to non-deleted rows ({@code deleted = false}) by
 * including {@code AndDeletedFalse} in its derived query name, so soft-deleted
 * requests are excluded from all reads (NFR-04). All multi-row finders are
 * paginated to keep review/approval queues query-performant (NFR-07).</p>
 */
@Repository
public interface TravelRequestRepository extends JpaRepository<TravelRequest, Long> {

    /**
     * Fetch a single request, excluding soft-deleted rows (FR-TR-04).
     *
     * @param id primary key of the travel request
     * @return the non-deleted request with the given id, or empty if absent
     */
    Optional<TravelRequest> findByIdAndDeletedFalse(Long id);

    /**
     * An employee's own requests, paginated, excluding soft-deleted rows (FR-TR-04).
     *
     * @param employeeId owning employee (logical UserAccount reference)
     * @param pageable pagination and sort parameters
     * @return page of the employee's non-deleted requests
     */
    Page<TravelRequest> findByEmployeeIdAndDeletedFalse(Long employeeId, Pageable pageable);

    /**
     * A manager's approval queue, paginated, excluding soft-deleted rows (FR-APR-01).
     *
     * @param approverId assigned approver (logical UserAccount reference)
     * @param pageable pagination and sort parameters
     * @return page of non-deleted requests assigned to the given approver
     */
    Page<TravelRequest> findByApproverIdAndDeletedFalse(Long approverId, Pageable pageable);

    /**
     * All non-deleted requests filtered by lifecycle status, paginated (FR-TR-06).
     *
     * @param status lifecycle status to match
     * @param pageable pagination and sort parameters
     * @return page of non-deleted requests in the given status
     */
    Page<TravelRequest> findByStatusAndDeletedFalse(TravelRequestStatus status, Pageable pageable);

    /**
     * An employee's requests filtered by lifecycle status, paginated,
     * excluding soft-deleted rows (FR-TR-06).
     *
     * @param employeeId owning employee (logical UserAccount reference)
     * @param status lifecycle status to match
     * @param pageable pagination and sort parameters
     * @return page of the employee's non-deleted requests in the given status
     */
    Page<TravelRequest> findByEmployeeIdAndStatusAndDeletedFalse(Long employeeId, TravelRequestStatus status,
            Pageable pageable);

    /**
     * A manager's approval queue filtered by lifecycle status, paginated,
     * excluding soft-deleted rows (FR-APR-01).
     *
     * @param approverId assigned approver (logical UserAccount reference)
     * @param status lifecycle status to match
     * @param pageable pagination and sort parameters
     * @return page of the approver's non-deleted requests in the given status
     */
    Page<TravelRequest> findByApproverIdAndStatusAndDeletedFalse(Long approverId, TravelRequestStatus status,
            Pageable pageable);

    /**
     * All non-deleted requests, paginated (NFR-04).
     *
     * @param pageable pagination and sort parameters
     * @return page of all non-deleted requests
     */
    Page<TravelRequest> findByDeletedFalse(Pageable pageable);
}