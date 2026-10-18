package com.travelease.admin.repository;

import java.util.Optional;

import com.travelease.admin.entity.TravelRequestPolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the admin-managed travel-request policy
 * (spec.md VAL-04, FR-TR-02, FR-ADM-02, NFR-04).
 *
 * <p>The policy is a singleton: at most one non-deleted active row exists at
 * any time. Finders scope to non-deleted rows ({@code deleted = false}) so
 * soft-deleted rows are excluded from all reads (NFR-04). {@link
 * #findFirstByActiveTrueAndDeletedFalse} resolves that single active policy -
 * the document-mandate lookup consumed by the Travel-Request Service at
 * submission time (VAL-04, FR-TR-02).</p>
 */
@Repository
public interface TravelRequestPolicyRepository extends JpaRepository<TravelRequestPolicy, Long> {

    /**
     * The single active travel-request policy, excluding soft-deleted rows;
     * the document-mandate lookup consumed by the Travel-Request Service at
     * submission time (VAL-04, FR-TR-02).
     *
     * @return the active non-deleted travel-request policy, or empty if absent
     */
    Optional<TravelRequestPolicy> findFirstByActiveTrueAndDeletedFalse();
}