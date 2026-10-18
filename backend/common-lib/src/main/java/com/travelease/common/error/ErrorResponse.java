package com.travelease.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Structured error response model shared by every service (Constitution §8, api-contract.md §8).
 * Shape only - the fields carry no behaviour.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors) {

    /**
     * Field-level validation detail carried inside {@link ErrorResponse}
     * (api-contract.md §8). Shape only.
     */
    public record FieldError(String field, String message) {
    }
}