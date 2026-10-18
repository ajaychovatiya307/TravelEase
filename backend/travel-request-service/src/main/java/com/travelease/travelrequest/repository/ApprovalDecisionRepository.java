package com.travelease.travelrequest.repository;

import java.util.List;

import com.travelease.travelrequest.entity.ApprovalDecision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for approval decisions (tasks.md D-03;
 * data-model.md §3.3).
 *
 * <p>Backs the approval workflow reads (FR-APR-01–03, AC-03/AC-04/AC-05). A travel
 * request may accumulate multiple decisions across resubmission cycles, and
 * {@link #findByTravelRequestIdOrderByDecidedAtAsc} returns that full history in
 * chronological ({@code decidedAt}) order. {@code ApprovalDecision} is immutable
 * and append-only: it does not extend {@code Auditable} and has no soft-delete
 * field, so unlike the other repositories in this package no
 * {@code deleted = false} scoping is needed.</p>
 */
@Repository
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, Long> {

    /**
     * The complete decision history of one travel request, oldest first
     * (FR-APR-01–03, AC-03/AC-04/AC-05).
     *
     * @param travelRequestId owning travel request id
     * @return all decisions recorded for the given travel request, ordered by
     *         {@code decidedAt} ascending
     */
    List<ApprovalDecision> findByTravelRequestIdOrderByDecidedAtAsc(Long travelRequestId);
}