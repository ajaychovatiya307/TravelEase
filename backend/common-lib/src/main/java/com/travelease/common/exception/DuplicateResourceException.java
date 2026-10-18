package com.travelease.common.exception;

/**
 * Thrown when a hard duplicate submission is detected (tasks.md F-02; Constitution §8;
 * api-contract.md §8). Carries the fixed error code {@code DUPLICATE_RESOURCE},
 * which the per-service {@code @RestControllerAdvice} handler (F-03) maps to HTTP 409
 * per the Constitution §8 status table.
 * <p>
 * Example: a disallowed identical resubmission of a travel request. (State conflicts,
 * e.g. editing an {@code Approved} travel request, are 409 {@code CONFLICT} instead.)
 * <p>
 * Related acceptance criteria (tasks.md F-02): AC-02, AC-04, AC-06–09, AC-11, AC-13.
 */
public class DuplicateResourceException extends TravelEaseException {

    /** Fixed error code (api-contract.md §8); callers never pass it. */
    private static final String ERROR_CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String message) {
        super(ERROR_CODE, message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Convenience constructor for duplicates identified by resource type and
     * identifier.
     *
     * @param resourceType human-readable type name of the conflicting resource, e.g. {@code "TravelRequest"}
     * @param identifier   identifier of the conflicting resource, e.g. {@code 42}
     */
    public DuplicateResourceException(String resourceType, Object identifier) {
        super(ERROR_CODE, resourceType + " with id " + identifier + " already exists");
    }
}