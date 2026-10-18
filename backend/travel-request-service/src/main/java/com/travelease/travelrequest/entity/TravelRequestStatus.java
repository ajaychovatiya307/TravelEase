package com.travelease.travelrequest.entity;

/**
 * Lifecycle status of a travel request (data-model.md §8; spec.md FR-TR-04, FR-TR-06, FR-TR-07).
 * {@code DRAFT} supports the mid-submission document-upload failure state (FR-DOC-03).
 */
public enum TravelRequestStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the owner while PENDING, or while APPROVED before the trip starts. */
    CANCELLED
}