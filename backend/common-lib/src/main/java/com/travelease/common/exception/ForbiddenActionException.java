package com.travelease.common.exception;

/**
 * Thrown when the caller lacks the required role or ownership for an action
 * (tasks.md F-02; Constitution §8; api-contract.md §8). Carries the fixed error code
 * {@code FORBIDDEN_ACTION}, which the per-service {@code @RestControllerAdvice}
 * handler (F-03) maps to HTTP 403 per the Constitution §8 status table.
 * <p>
 * Examples: an Employee calling the {@code /approval} endpoint; an employee editing
 * another employee's travel request.
 * <p>
 * Related acceptance criteria (tasks.md F-02): AC-02, AC-04, AC-06–09, AC-11, AC-13.
 */
public class ForbiddenActionException extends TravelEaseException {

    /** Fixed error code (api-contract.md §8); callers never pass it. */
    private static final String ERROR_CODE = "FORBIDDEN_ACTION";

    /**
     * @param message description of the forbidden action and why the caller is
     *                denied, e.g. {@code "only MANAGER or FINANCE roles may decide approvals"}
     */
    public ForbiddenActionException(String message) {
        super(ERROR_CODE, message);
    }

    public ForbiddenActionException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Convenience constructor for role-based denials.
     *
     * @param action        description of the denied action, e.g. {@code "decide approvals"}
     * @param requiredRoles roles allowed to perform it, e.g. {@code "MANAGER or FINANCE"}
     */
    public ForbiddenActionException(String action, String requiredRoles) {
        super(ERROR_CODE, "Only " + requiredRoles + " can " + action);
    }
}