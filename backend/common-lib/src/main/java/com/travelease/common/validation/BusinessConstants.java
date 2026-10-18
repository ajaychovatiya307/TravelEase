package com.travelease.common.validation;

/**
 * Single source of truth for MVP business constants shared across services.
 *
 * <p>{@link #BASE_CURRENCY} is the single base currency mandated by NFR-05
 * (spec.md §5) and clarification Q18: there is no automatic currency conversion
 * in the MVP, and foreign-currency amounts are manually pre-converted by the
 * Employee before entry. Services validate incoming currency values against
 * this constant — expense claims per VAL-06/NFR-05 and policy configurations
 * per FR-ADM-02/NFR-05 — so no service may define its own base-currency
 * constant (e.g. the former {@code SEED_CURRENCY} in admin-service).</p>
 */
public final class BusinessConstants {

    /** Single base currency for the MVP (NFR-05, spec.md §5; clarification Q18). */
    public static final String BASE_CURRENCY = "INR";

    private BusinessConstants() {
        // Utility class; not instantiable.
    }
}