package com.travelease.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Generic, immutable pagination envelope shared by ALL list endpoints
 * (api-contract.md §7; tasks.md E-06).
 *
 * <p>Immutable record (Constitution §6 - no entity leakage): the canonical response
 * shape {@code content}/{@code page}/{@code size}/{@code totalElements}/{@code totalPages}
 * for every paged list endpoint in every service. Controllers and services build it from
 * a Spring Data {@link Page} via {@link #from(Page)}.</p>
 */
public record PagedResponse<T>(
        /** Items on this page; empty (never null) when no rows match (api-contract.md §7; tasks.md E-06). */
        List<T> content,

        /** Zero-based page index (api-contract.md §7; tasks.md E-06). */
        int page,

        /** Requested page size (api-contract.md §7; tasks.md E-06). */
        int size,

        /** Total number of matching rows across all pages (api-contract.md §7; tasks.md E-06). */
        long totalElements,

        /** Total number of pages at the current size; zero when {@code totalElements} is zero (api-contract.md §7; tasks.md E-06). */
        int totalPages
) {

    /**
     * Builds the envelope from a Spring Data page (api-contract.md §7; tasks.md E-06).
     *
     * @param page Spring Data page returned by the repository; must not be null
     * @param <T>  item type
     * @return immutable pagination envelope for the page
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}