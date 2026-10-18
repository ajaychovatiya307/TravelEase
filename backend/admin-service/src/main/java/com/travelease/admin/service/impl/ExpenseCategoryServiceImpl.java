package com.travelease.admin.service.impl;

import com.travelease.admin.audit.AdminAuditEvents;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.admin.dto.ExpenseCategoryRequest;
import com.travelease.admin.dto.ExpenseCategoryResponse;
import com.travelease.admin.entity.ExpenseCategory;
import com.travelease.admin.mapper.ExpenseCategoryMapper;
import com.travelease.admin.repository.ExpenseCategoryRepository;
import com.travelease.admin.service.ExpenseCategoryService;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ResourceNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin expense category configuration business logic (tasks.md H-09; spec.md
 * FR-ADM-02, FR-EXP-04).
 *
 * <p>Enforces the unique {@code code} business rule (data-model.md §5.5) here,
 * since repositories perform data access only (Constitution §5).</p>
 */
@Service
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseCategoryMapper expenseCategoryMapper;

    private final AdminAuditEvents auditEvents;

    public ExpenseCategoryServiceImpl(ExpenseCategoryRepository expenseCategoryRepository,
            ExpenseCategoryMapper expenseCategoryMapper, AdminAuditEvents auditEvents) {
        this.expenseCategoryRepository = expenseCategoryRepository;
        this.expenseCategoryMapper = expenseCategoryMapper;
        this.auditEvents = auditEvents;
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse createExpenseCategory(ExpenseCategoryRequest request) {
        if (expenseCategoryRepository.existsByCodeAndDeletedFalse(request.code())) {
            throw new DuplicateResourceException("An expense category with code " + request.code() + " already exists");
        }
        ExpenseCategory category = expenseCategoryMapper.toEntity(request);
        ExpenseCategory saved = expenseCategoryRepository.save(category);
        auditEvents.publish(AuditEntityType.EXPENSE_CATEGORY, saved.getId(), AuditActionType.CREATED, java.util.Map.of(
                "code", saved.getCode(), "active", String.valueOf(saved.isActive())));
        return expenseCategoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse updateExpenseCategory(Long id, ExpenseCategoryRequest request) {
        ExpenseCategory category = expenseCategoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id));
        if (expenseCategoryRepository.existsByCodeAndDeletedFalseAndIdNot(request.code(), id)) {
            throw new DuplicateResourceException("An expense category with code " + request.code() + " already exists");
        }

        String previousCode = category.getCode();
        boolean wasActive = category.isActive();
        category.setCode(request.code());
        category.setLabel(request.label());
        category.setActive(Boolean.TRUE.equals(request.active()));

        ExpenseCategory saved = expenseCategoryRepository.save(category);
        auditEvents.publish(AuditEntityType.EXPENSE_CATEGORY, saved.getId(), AuditActionType.UPDATED, java.util.Map.of(
                "fromCode", previousCode, "toCode", saved.getCode(),
                "fromActive", String.valueOf(wasActive), "toActive", String.valueOf(saved.isActive())));
        return expenseCategoryMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseCategoryResponse getExpenseCategory(Long id) {
        ExpenseCategory category = expenseCategoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id));
        return expenseCategoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseCategoryResponse> listExpenseCategories(Pageable pageable) {
        return expenseCategoryRepository.findByDeletedFalse(pageable).map(expenseCategoryMapper::toResponse);
    }
}
