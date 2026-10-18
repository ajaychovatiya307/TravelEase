package com.travelease.notification.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.travelease.common.web.ApiExceptionHandler;

/**
 * HTTP error mapping for the notification service. All mappings (validation 400, forbidden 403,
 * not found 404, conflicts 409, policy 422, rate limit 429, generic 500 without details) are
 * shared through {@link ApiExceptionHandler} so every service answers with the same envelope
 * (api-contract.md §8). Add only notification-specific handlers here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {
}
