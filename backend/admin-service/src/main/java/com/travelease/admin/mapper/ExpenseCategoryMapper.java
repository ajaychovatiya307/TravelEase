package com.travelease.admin.mapper;

import com.travelease.admin.dto.ExpenseCategoryRequest;
import com.travelease.admin.dto.ExpenseCategoryResponse;
import com.travelease.admin.entity.ExpenseCategory;

import org.springframework.stereotype.Component;

/**
 * Entity ↔ DTO mapping for {@link ExpenseCategory} (tasks.md G-04; api-contract.md §5.15).
 *
 * <p>Isolates mapping logic from Controller/Service per Constitution §6; no
 * internal-only fields ({@code deleted}, {@code version}, audit metadata) are copied
 * into {@link ExpenseCategoryResponse}.</p>
 */
@Component
public class ExpenseCategoryMapper {

    /**
     * Builds a new {@link ExpenseCategory} from a create/update request (FR-ADM-02,
     * FR-EXP-04). The caller is responsible for persisting it and for the
     * code-uniqueness check (VAL-07).
     *
     * @param request validated create/update payload
     * @return a new, unsaved {@code ExpenseCategory}
     */
    public ExpenseCategory toEntity(ExpenseCategoryRequest request) {
        ExpenseCategory category = new ExpenseCategory();
        category.setCode(request.code());
        category.setLabel(request.label());
        category.setActive(Boolean.TRUE.equals(request.active()));
        return category;
    }

    /**
     * Converts a persisted {@link ExpenseCategory} to its API response shape
     * (api-contract.md §5.15).
     *
     * @param category persisted expense category
     * @return response DTO with no internal-only fields
     */
    public ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return new ExpenseCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getLabel(),
                category.isActive());
    }
}
