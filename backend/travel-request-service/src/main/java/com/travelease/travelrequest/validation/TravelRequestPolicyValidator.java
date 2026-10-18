package com.travelease.travelrequest.validation;

import java.time.LocalDate;

import com.travelease.common.exception.ValidationException;
import com.travelease.travelrequest.entity.TravelRequestStatus;

import org.springframework.stereotype.Component;

/**
 * Centralized business-rule/policy checks for travel requests (tasks.md J-04;
 * spec.md FR-TR-03, VAL-02, VAL-13), invoked from {@code TravelRequestServiceImpl}
 * (H-02/H-03) rather than inlined there, so each rule is independently unit-testable
 * (Constitution §10).
 */
@Component
public class TravelRequestPolicyValidator {

    /**
     * VAL-02: the travel start date must not be in the past, and the end date must
     * be on or after the start date (FR-TR-03).
     *
     * @param startDate requested trip start date
     * @param endDate   requested trip end date
     * @throws ValidationException if either rule is violated
     */
    public void validateTravelDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isBefore(LocalDate.now())) {
            throw new ValidationException("startDate", "Travel start date cannot be in the past (VAL-02)");
        }
        if (endDate.isBefore(startDate)) {
            throw new ValidationException("endDate", "End date must be on or after start date (VAL-02)");
        }
    }

    /**
     * VAL-13: a travel request may only be edited while {@code Pending}, or
     * resubmitted while {@code Rejected} (BR-09). Any other status is read-only.
     *
     * @param status current status of the travel request being edited
     * @throws ValidationException if the status does not permit an edit/resubmission
     */
    public void validateEditable(TravelRequestStatus status) {
        if (status != TravelRequestStatus.PENDING && status != TravelRequestStatus.REJECTED) {
            throw new ValidationException("status",
                    "Travel request can only be edited while PENDING or resubmitted while REJECTED. Current status: " + status);
        }
    }
}
