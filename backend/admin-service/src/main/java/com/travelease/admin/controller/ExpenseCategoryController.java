package com.travelease.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.admin.dto.ExpenseCategoryRequest;
import com.travelease.admin.dto.ExpenseCategoryResponse;
import com.travelease.admin.service.ExpenseCategoryService;

import jakarta.validation.Valid;

/**
 * Expense category administration REST controller (tasks.md I-05; api-contract.md §4.3, §6.4).
 *
 * <p>Thin controller delegating all business logic to {@link ExpenseCategoryService}.
 * Per Constitution §4: no business rules, no cross-service calls, no DB access.
 * POST/PUT endpoints enforce `@PreAuthorize("hasRole('ADMIN')")`;
 * GET endpoints allow EMPLOYEE, FINANCE, and ADMIN per api-contract.md §4.3.</p>
 */
@RestController
@RequestMapping("/api/admin/expense-categories")
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    public ExpenseCategoryController(ExpenseCategoryService expenseCategoryService) {
        this.expenseCategoryService = expenseCategoryService;
    }

    /**
     * Creates a new expense category (FR-ADM-02, FR-EXP-04).
     *
     * @param request validated create payload per api-contract.md §6.4
     * @return 201 Created with the persisted category response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseCategoryResponse> createExpenseCategory(
            @Valid @RequestBody ExpenseCategoryRequest request) {
        ExpenseCategoryResponse response = expenseCategoryService.createExpenseCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a single expense category (FR-EXP-04).
     *
     * @param id the expense category ID
     * @return 200 OK with the category response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'FINANCE', 'ADMIN')")
    public ResponseEntity<ExpenseCategoryResponse> getExpenseCategory(@PathVariable Long id) {
        ExpenseCategoryResponse response = expenseCategoryService.getExpenseCategory(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists active expense categories with pagination (FR-EXP-04, Constitution §5).
     * Accessible to EMPLOYEE, FINANCE, and ADMIN roles per api-contract.md §4.3.
     *
     * @param pageable pagination and sort parameters
     * @return 200 OK with a paginated list of categories
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'FINANCE', 'ADMIN')")
    public ResponseEntity<Page<ExpenseCategoryResponse>> listExpenseCategories(Pageable pageable) {
        Page<ExpenseCategoryResponse> response = expenseCategoryService.listExpenseCategories(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing expense category (FR-ADM-02, FR-EXP-04).
     *
     * @param id the expense category ID
     * @param request validated update payload
     * @return 200 OK with the updated category response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseCategoryResponse> updateExpenseCategory(@PathVariable Long id,
            @Valid @RequestBody ExpenseCategoryRequest request) {
        ExpenseCategoryResponse response = expenseCategoryService.updateExpenseCategory(id, request);
        return ResponseEntity.ok(response);
    }
}
