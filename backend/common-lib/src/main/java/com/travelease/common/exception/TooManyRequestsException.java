package com.travelease.common.exception;

/** The caller exceeded a rate limit (e.g. repeated failed logins); mapped to HTTP 429. */
public class TooManyRequestsException extends TravelEaseException {

    private static final String ERROR_CODE = "TOO_MANY_REQUESTS";

    public TooManyRequestsException(String message) {
        super(ERROR_CODE, message);
    }
}
