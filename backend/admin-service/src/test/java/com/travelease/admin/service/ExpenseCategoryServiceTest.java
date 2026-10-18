package com.travelease.admin.service;

import java.util.List;
import java.util.Optional;

import com.travelease.admin.dto.ExpenseCategoryRequest;
import com.travelease.admin.dto.ExpenseCategoryResponse;
import com.travelease.admin.entity.ExpenseCategory;
import com.travelease.admin.mapper.ExpenseCategoryMapper;
import com.travelease.admin.repository.ExpenseCategoryRepository;
import com.travelease.admin.service.impl.ExpenseCategoryServiceImpl;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpenseCategoryServiceImpl} (tasks.md L-07; spec.md
 * FR-ADM-02; AC-08), covering admin expense category create/update/read/list with
 * positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>create: unique code → saved with the {@code active} default of {@code true}
 *       when the request omits it, response mapped</li>
 *   <li>create: duplicate code via {@code existsByCodeAndDeletedFalse} →
 *       {@link DuplicateResourceException}, nothing saved</li>
 *   <li>update: existing category and the uniqueness check excluding its own id passes
 *       ({@code existsByCodeAndDeletedFalseAndIdNot}) → fields replaced in place,
 *       response mapped</li>
 *   <li>update: code already owned by a different category via
 *       {@code existsByCodeAndDeletedFalseAndIdNot} → {@link DuplicateResourceException},
 *       nothing saved</li>
 *   <li>get: found → mapped response; not found → {@link ResourceNotFoundException}</li>
 *   <li>list: repository page of non-deleted categories mapped to a
 *       {@code Page<ExpenseCategoryResponse>}</li>
 * </ul>
 *
 * <p>The repository is mocked with Mockito; the mapper is a plain (non-Spring) pure
 * converter, so a real {@code @Spy} instance is injected to keep the entity ↔ DTO
 * mapping under test as well - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseCategoryServiceImpl (tasks.md L-07; spec.md FR-ADM-02; AC-08)")
class ExpenseCategoryServiceTest {

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Spy
    private ExpenseCategoryMapper expenseCategoryMapper = new ExpenseCategoryMapper();

    /** Audit publishing (#13); verified explicitly where the event content matters. */

    @Mock

    private com.travelease.admin.audit.AdminAuditEvents auditEvents;


    @InjectMocks
    private ExpenseCategoryServiceImpl expenseCategoryService;

    private static ExpenseCategoryRequest request(String code, String label, Boolean active) {
        return new ExpenseCategoryRequest(code, label, active);
    }

    private static ExpenseCategory category(Long id, String code, String label) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(id);
        category.setCode(code);
        category.setLabel(label);
        category.setActive(true);
        return category;
    }

    @Nested
    @DisplayName("createExpenseCategory")
    class CreateExpenseCategory {

        @Test
        @DisplayName("saves the category with active defaulted to true when omitted and maps the response")
        void savesCategoryWithActiveDefault() {
            when(expenseCategoryRepository.existsByCodeAndDeletedFalse("MEAL")).thenReturn(false);
            when(expenseCategoryRepository.save(any(ExpenseCategory.class)))
                    .thenAnswer(invocation -> {
                        ExpenseCategory created = invocation.getArgument(0);
                        created.setId(1L);
                        return created;
                    });

            ExpenseCategoryResponse response = expenseCategoryService.createExpenseCategory(
                    request("MEAL", "Meals & Snacks", null));

            ArgumentCaptor<ExpenseCategory> captor = ArgumentCaptor.forClass(ExpenseCategory.class);
            verify(expenseCategoryRepository).save(captor.capture());
            ExpenseCategory saved = captor.getValue();
            assertThat(saved.getCode()).isEqualTo("MEAL");
            assertThat(saved.getLabel()).isEqualTo("Meals & Snacks");
            assertThat(saved.isActive()).isTrue();

            assertThat(response).isEqualTo(new ExpenseCategoryResponse(1L, "MEAL", "Meals & Snacks", true));
        }

        @Test
        @DisplayName("throws DuplicateResourceException when the code already exists and saves nothing")
        void throwsOnDuplicateCode() {
            when(expenseCategoryRepository.existsByCodeAndDeletedFalse("MEAL")).thenReturn(true);

            ExpenseCategoryRequest duplicateCodeRequest = request("MEAL", "Meals & Snacks", null);

            assertThatThrownBy(() -> expenseCategoryService.createExpenseCategory(duplicateCodeRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("MEAL");

            verify(expenseCategoryRepository, never()).save(any(ExpenseCategory.class));
        }
    }

    @Nested
    @DisplayName("updateExpenseCategory")
    class UpdateExpenseCategory {

        @Test
        @DisplayName("updates the category in place when the uniqueness check excluding its own id passes")
        void updatesCategoryInPlace() {
            ExpenseCategory existing = category(1L, "MEAL", "Old Label");
            when(expenseCategoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(expenseCategoryRepository.existsByCodeAndDeletedFalseAndIdNot("MEAL", 1L)).thenReturn(false);
            when(expenseCategoryRepository.save(any(ExpenseCategory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ExpenseCategoryResponse response = expenseCategoryService.updateExpenseCategory(1L,
                    request("MEAL", "New Label", null));

            ArgumentCaptor<ExpenseCategory> captor = ArgumentCaptor.forClass(ExpenseCategory.class);
            verify(expenseCategoryRepository).save(captor.capture());
            ExpenseCategory saved = captor.getValue();
            assertThat(saved).isSameAs(existing);
            assertThat(saved.getCode()).isEqualTo("MEAL");
            assertThat(saved.getLabel()).isEqualTo("New Label");
            assertThat(saved.isActive()).isTrue();

            assertThat(response).isEqualTo(new ExpenseCategoryResponse(1L, "MEAL", "New Label", true));
        }

        @Test
        @DisplayName("throws DuplicateResourceException when the new code is owned by a different category and saves nothing")
        void throwsWhenCodeOwnedByAnotherCategory() {
            ExpenseCategory existing = category(1L, "MEAL", "Meals & Snacks");
            when(expenseCategoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(expenseCategoryRepository.existsByCodeAndDeletedFalseAndIdNot("HOTEL", 1L)).thenReturn(true);

            ExpenseCategoryRequest conflictingCodeRequest = request("HOTEL", "Hotels & Stays", true);

            assertThatThrownBy(() -> expenseCategoryService.updateExpenseCategory(1L, conflictingCodeRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("HOTEL");

            verify(expenseCategoryRepository, never()).save(any(ExpenseCategory.class));
        }
    }

    @Nested
    @DisplayName("getExpenseCategory")
    class GetExpenseCategory {

        @Test
        @DisplayName("maps the category to a response when found")
        void returnsMappedCategory() {
            ExpenseCategory existing = category(1L, "MEAL", "Meals & Snacks");
            when(expenseCategoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));

            ExpenseCategoryResponse response = expenseCategoryService.getExpenseCategory(1L);

            assertThat(response).isEqualTo(new ExpenseCategoryResponse(1L, "MEAL", "Meals & Snacks", true));
            verify(expenseCategoryMapper).toResponse(existing);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the category does not exist")
        void throwsWhenNotFound() {
            when(expenseCategoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseCategoryService.getExpenseCategory(1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(expenseCategoryMapper, never()).toResponse(any(ExpenseCategory.class));
        }
    }

    @Nested
    @DisplayName("listExpenseCategories")
    class ListExpenseCategories {

        @Test
        @DisplayName("maps the repository page of non-deleted categories to a response page")
        void mapsPage() {
            Pageable pageable = Pageable.ofSize(20);
            ExpenseCategory first = category(1L, "MEAL", "Meals & Snacks");
            ExpenseCategory second = category(2L, "TRAVEL", "Travel & Transport");
            when(expenseCategoryRepository.findByDeletedFalse(pageable))
                    .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));

            Page<ExpenseCategoryResponse> page = expenseCategoryService.listExpenseCategories(pageable);

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).containsExactly(
                    new ExpenseCategoryResponse(1L, "MEAL", "Meals & Snacks", true),
                    new ExpenseCategoryResponse(2L, "TRAVEL", "Travel & Transport", true));
        }
    }
}