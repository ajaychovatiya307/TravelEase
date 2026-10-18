package com.travelease.common.exception;

import com.travelease.common.error.ErrorResponse;

import java.time.Instant;
import java.util.List;

/**
 * Common root exception of every backend service (tasks.md F-01; Constitution §8;
 * api-contract.md §8). Every concrete exception type defined per service (F-02)
 * extends this root, so a single {@code @RestControllerAdvice} handler per service
 * (F-03) can translate any of them into the shared structured error body.
 * <p>
 * The exception carries only the machine-readable error code (mapped to
 * {@link ErrorResponse#error}) and optional field-level validation details (mapped
 * to {@link ErrorResponse#fieldErrors}). Which HTTP status accompanies a given error
 * code is decided by the per-service handler according to the Constitution §8 table,
 * not by this class.
 */
public class TravelEaseException extends RuntimeException {

    /**
     * Machine-readable error code mapped to {@link ErrorResponse#error}
     * (api-contract.md §8 codes, e.g. {@code VALIDATION_ERROR}, {@code CONFLICT}).
     */
    private final String errorCode;

    /**
     * Optional field-level validation details mapped to {@link ErrorResponse#fieldErrors}.
     * Never {@code null} - defaults to an immutable empty list.
     */
    private final List<ErrorResponse.FieldError> fieldErrors;

    public TravelEaseException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public TravelEaseException(String errorCode, String message, List<ErrorResponse.FieldError> fieldErrors) {
        this(errorCode, message, fieldErrors, null);
    }

    public TravelEaseException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, List.of(), cause);
    }

    public TravelEaseException(String errorCode, String message, List<ErrorResponse.FieldError> fieldErrors, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }

    /**
     * Translates this exception into the shared structured error body (F-03).
     * The {@code httpStatus} argument is supplied by the calling handler per the
     * Constitution §8 status table; everything else comes from this exception.
     *
     * @param httpStatus HTTP status to embed in the response body
     * @return the structured {@link ErrorResponse} body
     */
    public ErrorResponse toErrorResponse(int httpStatus) {
        return new ErrorResponse(Instant.now(), httpStatus, errorCode, getMessage(), fieldErrors);
    }
}