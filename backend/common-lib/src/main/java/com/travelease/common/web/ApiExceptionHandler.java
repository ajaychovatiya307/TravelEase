package com.travelease.common.web;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import com.travelease.common.error.ErrorResponse;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.TooManyRequestsException;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.StorageException;
import com.travelease.common.storage.StoredFileNotFoundException;

/**
 * Shared HTTP error mapping for every TravelEase service (api-contract.md §8 error envelope).
 * Each service declares {@code @RestControllerAdvice class GlobalExceptionHandler extends ApiExceptionHandler}
 * and may add domain-specific handlers.
 *
 * <p>Semantics: client mistakes are 4xx with a contract error code the web app understands
 * ({@code VALIDATION_ERROR}, {@code FORBIDDEN_ACTION}, {@code RESOURCE_NOT_FOUND}, {@code CONFLICT},
 * {@code DUPLICATE_RESOURCE}, {@code POLICY_VIOLATION}); anything unexpected stays a 500
 * {@code INTERNAL_ERROR} whose body never contains exception details or stack traces.</p>
 */
public abstract class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    protected static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    protected static final String FORBIDDEN_ACTION = "FORBIDDEN_ACTION";
    protected static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    protected static final String CONFLICT = "CONFLICT";
    protected static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    protected static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message,
            List<ErrorResponse.FieldError> fieldErrors) {
        return new ResponseEntity<>(new ErrorResponse(Instant.now(), status.value(), code, message, fieldErrors),
                status);
    }

    private static ResponseEntity<ErrorResponse> invalidField(String field, String message) {
        return respond(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Request validation failed",
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    // ---- Domain exceptions (common-lib) -------------------------------------------------------

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        LOGGER.debug("Validation error: {}", ex.getMessage());
        return new ResponseEntity<>(ex.toErrorResponse(HttpStatus.BAD_REQUEST.value()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        LOGGER.debug("Resource not found: {}", ex.getMessage());
        return new ResponseEntity<>(ex.toErrorResponse(HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex) {
        return new ResponseEntity<>(ex.toErrorResponse(HttpStatus.CONFLICT.value()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePolicyViolation(PolicyViolationException ex) {
        LOGGER.debug("Policy violation: {}", ex.getMessage());
        return new ResponseEntity<>(ex.toErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY.value()),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenActionException ex) {
        LOGGER.info("Forbidden action: {}", ex.getMessage());
        return new ResponseEntity<>(ex.toErrorResponse(HttpStatus.FORBIDDEN.value()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return new ResponseEntity<>(ex.toErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value()),
                HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---- Security ------------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, FORBIDDEN_ACTION, "You do not have permission to perform this action",
                null);
    }

    // ---- Malformed input -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Malformed or unreadable request body", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return invalidField(ex.getName(), "Invalid value");
    }

    /**
     * Several endpoints take opaque string ids and parse them in the service (api-contract.md §3.1);
     * a non-numeric id is a client error, not a server failure.
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ErrorResponse> handleBadIdentifier(NumberFormatException ex) {
        return invalidField("id", "Invalid identifier");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return invalidField(ex.getParameterName(), "Required parameter is missing");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return invalidField(ex.getRequestPartName(), "Required part is missing");
    }

    /** Uploads above spring.servlet.multipart limits (5 MB per file): the documented VALIDATION_ERROR on 'file'. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return invalidField("file", "File size exceeds maximum allowed (5 MB)");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND, "No endpoint exists at this path", null);
    }

    // ---- Conflicts ---------------------------------------------------------------------------------

    /** Another user changed the record first (stale @Version): ask the client to reload. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        LOGGER.info("Concurrent modification detected: {}", ex.getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, CONFLICT,
                "This record was changed by someone else. Reload it and try again.", null);
    }

    /** A unique/foreign-key constraint rejected the write (e.g. a concurrent duplicate insert). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrityViolation(DataIntegrityViolationException ex) {
        LOGGER.info("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, CONFLICT, "The request conflicts with existing data", null);
    }

    // ---- Document storage ------------------------------------------------------------------------

    @ExceptionHandler(StoredFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStoredFileNotFound(StoredFileNotFoundException ex) {
        LOGGER.warn("Stored document missing");
        return respond(HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND, "The requested document file is no longer available",
                null);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex) {
        LOGGER.error("Document storage failure: {}", ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_ERROR",
                "Document storage is temporarily unavailable - please retry the upload", null);
    }

    /** Code that raises Spring's {@code ResponseStatusException} keeps its intended status. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            LOGGER.error("Server error status raised", ex);
            return respond(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "An unexpected error occurred", null);
        }
        String code = switch (status) {
            case NOT_FOUND -> RESOURCE_NOT_FOUND;
            case FORBIDDEN -> FORBIDDEN_ACTION;
            case CONFLICT -> CONFLICT;
            case UNAUTHORIZED -> "UNAUTHORIZED";
            default -> VALIDATION_ERROR;
        };
        String reason = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        return respond(status, code, reason, null);
    }

    // ---- Everything else ----------------------------------------------------------------------------

    /** Unexpected failures: full detail in the server log only, a generic body for the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        LOGGER.error("Unexpected error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "An unexpected error occurred", null);
    }
}
