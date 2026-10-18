package com.travelease.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level constraint enforcing VAL-12: {@code reason} must be present and
 * non-blank when {@code decision = REJECTED} (tasks.md J-02; spec.md FR-APR-02).
 *
 * <p>Applied to DTOs exposing a {@code decision} and {@code reason} accessor
 * (record component or JavaBean getter), e.g. {@code ApprovalDecisionRequest},
 * {@code ExpenseReviewRequest}. Shared here so both travel-request-service and
 * expense-service reuse one implementation (Constitution §2).</p>
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RequiredWhenRejectedValidator.class)
@Documented
public @interface RequiredWhenRejected {

    String message() default "reason is required and must be non-blank when decision is REJECTED";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
