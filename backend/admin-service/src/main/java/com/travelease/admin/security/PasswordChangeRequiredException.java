package com.travelease.admin.security;

import com.travelease.common.exception.TravelEaseException;

/**
 * The credentials were correct but the account holds an administrator-issued temporary password:
 * it must be replaced (POST /api/auth/complete-password-change) before a token is issued. HTTP 403.
 */
public class PasswordChangeRequiredException extends TravelEaseException {

    public static final String ERROR_CODE = "PASSWORD_CHANGE_REQUIRED";

    public PasswordChangeRequiredException() {
        super(ERROR_CODE, "Your password was reset by an administrator. Choose a new password to continue.");
    }
}
