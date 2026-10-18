package com.travelease.travelrequest.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting a new travel request (tasks.md E-01;
 * api-contract.md §5.1; spec.md FR-TR-01, FR-TR-02, AC-01, AC-02).
 *
 * <p>Carries only the structural (Bean Validation) tier of the rules - the
 * business parts of VAL-02 (start date not in the past, date ordering) and
 * VAL-04 (policy-mandated documents) are enforced at the Service layer
 * (Constitution §7 tier 2).</p>
 */
public record TravelRequestCreateRequest(

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