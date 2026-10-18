package com.travelease.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.travelease.admin.dto.ExpenseCategoryRequest;
import com.travelease.admin.dto.ExpenseCategoryResponse;

/**
 * Admin-facing management of expense categories (tasks.md H-09; spec.md FR-ADM-02,
 * FR-EXP-04; api-contract.md §4.3).
 */
public interface ExpenseCategoryService {

    /**
     * Creates a new expense category.
     *
     * @param request validated create payload
     * @return the persisted category's response shape
     */
    ExpenseCategoryResponse createExpenseCategory(ExpenseCategoryRequest request);

    /**
     * Updates an existing expense category.
     *
     * @param id      id of the category to update
     * @param request validated update payload
     * @return the updated category's response shape
     */
    ExpenseCategoryResponse updateExpenseCategory(Long id, ExpenseCategoryRequest request);

    /**
     * Fetches a single expense category.
     *
     * @param id id of the category
     * @return the category's response shape
     */
    ExpenseCategoryResponse getExpenseCategory(Long id);

    /**
     * Lists non-deleted expense categories, paginated.
     *
     * @param pageable pagination and sort parameters
     * @return a page of expense category response shapes
     */
    Page<ExpenseCategoryResponse> listExpenseCategories(Pageable pageable);
}
