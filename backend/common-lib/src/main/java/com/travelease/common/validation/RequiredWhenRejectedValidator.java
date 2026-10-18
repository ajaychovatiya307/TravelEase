package com.travelease.common.validation;

import java.lang.reflect.Method;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link RequiredWhenRejected}: reads {@code decision}/{@code reason}
 * via record accessor or JavaBean getter (DTO-agnostic, tasks.md J-02) and fails
 * only when {@code decision} is the {@code REJECTED} enum constant and
 * {@code reason} is null/blank.
 */
public class RequiredWhenRejectedValidator implements ConstraintValidator<RequiredWhenRejected, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Object decision = readProperty(value, "decision", "getDecision");
        String reason = (String) readProperty(value, "reason", "getReason");

        if (decision == null || !"REJECTED".equals(decision.toString())) {
            return true;
        }
        if (reason != null && !reason.isBlank()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "reason is required and must be non-blank when decision is REJECTED")
                .addPropertyNode("reason")
                .addConstraintViolation();
        return false;
    }

    // Supports both record accessors (e.g. "decision") and JavaBean getters (e.g. "getDecision").
    private Object readProperty(Object target, String recordAccessor, String getterName) {
        for (String name : new String[] { recordAccessor, getterName }) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (NoSuchMethodException e) {
                // try next accessor style
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to read property via " + name + "() on " + target.getClass(), e);
            }
        }
        throw new IllegalStateException(
                "No accessor " + recordAccessor + "()/" + getterName + "() found on " + target.getClass());
    }
}
