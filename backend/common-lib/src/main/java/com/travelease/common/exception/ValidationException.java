package com.travelease.common.exception;

import com.travelease.common.error.ErrorResponse;

import java.util.List;

/**
 * Thrown on field-level format/presence failures (tasks.md F-02; Constitution §8;
 * api-contract.md §8). Carries the fixed error code {@code VALIDATION_ERROR}, which
 * the per-service {@code @RestControllerAdvice} handler (F-03) maps to HTTP 400 per
 * the Constitution §8 status table. {@code fieldErrors} is populated for this code
 * (api-contract.md §8).
 * <p>
 * Examples: missing {@code destination} on a travel request; a past
 * {@code startDate}; an invalid document file type.
 * <p>
 * Related acceptance criteria (tasks.md F-02): AC-02, AC-04, AC-06–09, AC-11, AC-13.
 */
public class ValidationException extends TravelEaseException {

    /** Fixed error code (api-contract.md §8); callers never pass it. */
    private static final String ERROR_CODE = "VALIDATION_ERROR";

    public ValidationException(String message) {
        super(ERROR_CODE, message);
    }

    public ValidationException(String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(ERROR_CODE, message, fieldErrors);
    }

    public ValidationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Convenience constructor for a single failing field; the top-level message
     * mirrors the field detail.
     *
     * @param field        name of the failing request field, e.g. {@code "destination"}
     * @param fieldMessage human-readable detail for that field
     */
    public ValidationException(String field, String fieldMessage) {
        super(ERROR_CODE, fieldMessage, List.of(new ErrorResponse.FieldError(field, fieldMessage)));
    }
}