package com.travelease.common.exception;

/**
 * Thrown when a referenced entity does not exist (tasks.md F-02; Constitution §8;
 * api-contract.md §8). Carries the fixed error code {@code RESOURCE_NOT_FOUND},
 * which the per-service {@code @RestControllerAdvice} handler (F-03) maps to HTTP 404
 * per the Constitution §8 status table.
 * <p>
 * Examples: {@code GET /api/travel-requests/{id}} where the {@code travelRequestId}
 * is unknown; an expense claim submitted against a non-existent travel request.
 * <p>
 * Related acceptance criteria (tasks.md F-02): AC-02, AC-04, AC-06–09, AC-11, AC-13.
 */
public class ResourceNotFoundException extends TravelEaseException {

    /** Fixed error code (api-contract.md §8); callers never pass it. */
    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Convenience constructor for lookups by identifier.
     *
     * @param resourceType human-readable type name of the missing resource, e.g. {@code "TravelRequest"}
     * @param identifier   value that was looked up, e.g. {@code 42}
     */
    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(ERROR_CODE, resourceType + " with id " + identifier + " not found");
    }
}