package com.travelease.admin.security;

import java.util.Locale;

import com.travelease.common.exception.ValidationException;

/**
 * Rules for user-chosen passwords (#14): 10–72 characters (BCrypt uses at most 72 bytes), at least one
 * letter and one digit, not the same as the current password, and not containing the account's email
 * name. Violations are 400 VALIDATION_ERROR on {@code newPassword}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
        // utility holder - not instantiable
    }

    public static void validate(String newPassword, String email) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH || newPassword.length() > MAX_LENGTH) {
            throw new ValidationException("newPassword",
                    "Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (!newPassword.chars().anyMatch(Character::isLetter) || !newPassword.chars().anyMatch(Character::isDigit)) {
            throw new ValidationException("newPassword", "Password must contain at least one letter and one digit");
        }
        if (email != null && email.contains("@")) {
            String name = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (name.length() >= 3 && newPassword.toLowerCase(Locale.ROOT).contains(name)) {
                throw new ValidationException("newPassword", "Password must not contain your user name");
            }
        }
    }
}
