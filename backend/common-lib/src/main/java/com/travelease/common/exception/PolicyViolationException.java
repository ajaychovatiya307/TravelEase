package com.travelease.common.exception;

import com.travelease.common.error.ErrorResponse;

import java.util.List;

/**
 * Thrown on business rule/policy failures detected in the service layer (tasks.md F-02;
 * Constitution §8; api-contract.md §8). Carries the fixed error code
 * {@code POLICY_VIOLATION}, which the per-service {@code @RestControllerAdvice}
 * handler (F-03) maps to HTTP 422 per the Constitution §8 status table.
 * {@code fieldErrors} may be populated when the violation is attributable to a
 * specific field, e.g. {@code documentIds} (api-contract.md §8).
 * <p>
 * Examples: expense date outside the linked travel request's date range; missing
 * required receipt above the category threshold.
 * <p>
 * Related acceptance criteria (tasks.md F-02): AC-02, AC-04, AC-06–09, AC-11, AC-13.
 */
public class PolicyViolationException extends TravelEaseException {

    /** Fixed error code (api-contract.md §8); callers never pass it. */
    private static final String ERROR_CODE = "POLICY_VIOLATION";

    public PolicyViolationException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyViolationException(String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(ERROR_CODE, message, fieldErrors);
    }

    public PolicyViolationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Convenience constructor for a policy violation attributable to a single field;
     * the top-level message mirrors the field detail.
     *
     * @param field        name of the field the violation is attributable to, e.g. {@code "documentIds"}
     * @param fieldMessage human-readable detail for that field
     */
    public PolicyViolationException(String field, String fieldMessage) {
        super(ERROR_CODE, fieldMessage, List.of(new ErrorResponse.FieldError(field, fieldMessage)));
    }
}