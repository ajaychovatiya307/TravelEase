package com.travelease.travelrequest.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for editing a {@code Pending} travel request or resubmitting a
 * {@code Rejected} one (tasks.md E-01; api-contract.md §5.2; spec.md FR-TR-06,
 * FR-APR-03, AC-05, AC-13).
 *
 * <p>Same shape as {@link TravelRequestCreateRequest}. The state rules - edit
 * allowed only while {@code Pending} / resubmission after rejection (VAL-13,
 * BR-09) - are enforced at the Service layer, not by Bean Validation.</p>
 */
public record TravelRequestUpdateRequest(

        /** Travel destination; required, non-blank (VAL-01). */
        @NotBlank(message = "destination must not be blank")
        String destination,

        /** Travel start date; required (VAL-02, format part). */
        @NotNull(message = "startDate is required")
        LocalDate startDate,

        /** Travel end date; required (VAL-02, format part). */
        @NotNull(message = "endDate is required")
        LocalDate endDate,

        /** Business justification; required, minimum length enforced (VAL-03, BR-01). */
        @NotNull(message = "justification is required")
        @Size(min = 10, max = 2000, message = "justification must be between 10 and 2000 characters")
        String justification,

        /** References to pre-uploaded document metadata; optional unless policy mandates (VAL-04). */
        List<String> documentIds) {
}