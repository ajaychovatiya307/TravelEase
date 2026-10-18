package com.travelease.travelrequest.repository;

import java.util.List;
import java.util.Optional;

import com.travelease.travelrequest.entity.TravelRequestDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for travel request supporting documents
 * (tasks.md D-02; data-model.md §3.2).
 *
 * <p>Finders scope to non-deleted rows ({@code deleted = false}) so soft-deleted
 * documents are excluded from all reads (NFR-04). {@link #countByTravelRequestIdAndDeletedFalse}
 * backs the max-5-documents-per-request validation (VAL-11, FR-DOC-01).</p>
 */
@Repository
public interface TravelRequestDocumentRepository extends JpaRepository<TravelRequestDocument, Long> {

    /**
     * All non-deleted documents attached to a travel request (FR-DOC-01).
     *
     * @param travelRequestId owning travel request id
     * @return non-deleted documents for the given travel request
     */
    List<TravelRequestDocument> findByTravelRequestIdAndDeletedFalse(Long travelRequestId);

    /**
     * Loads one document only if it belongs to the given travel request, so a document id from
     * another request can never be served through this request's URL.
     */
    Optional<TravelRequestDocument> findByIdAndTravelRequestIdAndDeletedFalse(Long id, Long travelRequestId);

    /**
     * Count of non-deleted documents already attached to a travel request, used to
     * enforce the 5-file-per-request limit before accepting a new upload (VAL-11).
     *
     * @param travelRequestId owning travel request id
     * @return number of non-deleted documents currently attached
     */
    long countByTravelRequestIdAndDeletedFalse(Long travelRequestId);
}
