package com.travelease.admin.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.travelease.admin.dto.PolicyConfigRequest;
import com.travelease.admin.dto.PolicyConfigResponse;
import com.travelease.admin.entity.ExpenseCategory;
import com.travelease.admin.entity.PolicyConfig;
import com.travelease.admin.mapper.PolicyConfigMapper;
import com.travelease.admin.repository.ExpenseCategoryRepository;
import com.travelease.admin.repository.PolicyConfigRepository;
import com.travelease.admin.service.impl.PolicyConfigServiceImpl;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.validation.BusinessConstants;

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
 * Unit tests for {@link PolicyConfigServiceImpl} (tasks.md L-07; spec.md FR-ADM-02;
 * AC-08), covering admin policy config create/update/read/list with positive and
 * negative cases per Constitution §10:
 *
 * <ul>
 *   <li>create: existing category code (threshold &ge; 0, base currency) → saved with
 *       the {@code active} default of {@code true} when the request omits it, response mapped</li>
 *   <li>create: unknown category code via {@code findByCodeAndDeletedFalse}
 *       (private {@code requireExistingCategory}) → {@link ResourceNotFoundException},
 *       nothing saved</li>
 *   <li>create: an active policy already exists for the category via
 *       {@code existsByCategoryCodeAndActiveTrueAndDeletedFalse} →
 *       {@link DuplicateResourceException}, nothing saved</li>
 *   <li>create: currency other than the single base currency (NFR-05) →
 *       {@link PolicyViolationException} (field {@code "currency"}), nothing saved</li>
 *   <li>update: existing config and the uniqueness check excluding its own id passes
 *       ({@code existsByCategoryCodeAndActiveTrueAndDeletedFalseAndIdNot}) → fields
 *       replaced in place, response mapped</li>
 *   <li>update: currency other than the single base currency (NFR-05) →
 *       {@link PolicyViolationException}, existing config left unmodified</li>
 *   <li>get: found → mapped response; not found → {@link ResourceNotFoundException}</li>
 *   <li>list: repository page of non-deleted configs mapped to a
 *       {@code Page<PolicyConfigResponse>}</li>
 * </ul>
 *
 * <p>The repositories are mocked with Mockito; the mapper is a plain (non-Spring) pure
 * converter, so a real {@code @Spy} instance is injected to keep the entity ↔ DTO
 * mapping under test as well - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyConfigServiceImpl (tasks.md L-07; spec.md FR-ADM-02; AC-08)")
class PolicyConfigServiceTest {

    @Mock
    private PolicyConfigRepository policyConfigRepository;

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Spy
    private PolicyConfigMapper policyConfigMapper = new PolicyConfigMapper();

    /** Audit publishing (#13); verified explicitly where the event content matters. */

    @Mock

    private com.travelease.admin.audit.AdminAuditEvents auditEvents;


    @InjectMocks
    private PolicyConfigServiceImpl policyConfigService;

    private static PolicyConfigRequest request(String categoryCode, BigDecimal threshold, String currency,
            Boolean active) {
        return new PolicyConfigRequest(categoryCode, threshold, currency, active);
    }

    private static ExpenseCategory category(Long id, String code) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(id);
        category.setCode(code);
        category.setLabel(code);
        category.setActive(true);
        return category;
    }

    private static PolicyConfig config(Long id, String categoryCode, BigDecimal threshold, String currency,
            boolean active) {
        PolicyConfig config = new PolicyConfig();
        config.setId(id);
        config.setCategoryCode(categoryCode);
        config.setReceiptThresholdAmount(threshold);
        config.setCurrency(currency);
        config.setActive(active);
        return config;
    }

    @Nested
    @DisplayName("createPolicyConfig")
    class CreatePolicyConfig {

        @Test
        @DisplayName("saves the config with active defaulted to true when omitted and maps the response")
        void savesConfigWithActiveDefault() {
            when(expenseCategoryRepository.findByCodeAndDeletedFalse("MEAL"))
                    .thenReturn(Optional.of(category(2L, "MEAL")));
            when(policyConfigRepository.existsByCategoryCodeAndActiveTrueAndDeletedFalse("MEAL"))
                    .thenReturn(false);
            when(policyConfigRepository.save(any(PolicyConfig.class)))
                    .thenAnswer(invocation -> {
                        PolicyConfig created = invocation.getArgument(0);
                        created.setId(1L);
                        return created;
                    });

            PolicyConfigResponse response = policyConfigService.createPolicyConfig(
                    request("MEAL", new BigDecimal("500.00"), "INR", null));

            ArgumentCaptor<PolicyConfig> captor = ArgumentCaptor.forClass(PolicyConfig.class);
            verify(policyConfigRepository).save(captor.capture());
            PolicyConfig saved = captor.getValue();
            assertThat(saved.getCategoryCode()).isEqualTo("MEAL");
            assertThat(saved.getReceiptThresholdAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(saved.getCurrency()).isEqualTo("INR");
            assertThat(saved.isActive()).isTrue();

            assertThat(response).isEqualTo(new PolicyConfigResponse(1L, "MEAL", new BigDecimal("500.00"), "INR", true));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the category code does not exist and saves nothing")
        void throwsWhenCategoryDoesNotExist() {
            when(expenseCategoryRepository.findByCodeAndDeletedFalse("UNKNOWN"))
                    .thenReturn(Optional.empty());

            PolicyConfigRequest unknownCategoryRequest =
                    request("UNKNOWN", new BigDecimal("100.00"), "INR", null);

            assertThatThrownBy(() -> policyConfigService.createPolicyConfig(unknownCategoryRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(policyConfigRepository, never()).save(any(PolicyConfig.class));
            verify(policyConfigRepository, never()).existsByCategoryCodeAndActiveTrueAndDeletedFalse(any());
        }

        @Test
        @DisplayName("throws DuplicateResourceException when an active policy for the same category exists and saves nothing")
        void throwsOnDuplicateActivePolicy() {
            when(expenseCategoryRepository.findByCodeAndDeletedFalse("MEAL"))
                    .thenReturn(Optional.of(category(2L, "MEAL")));
            when(policyConfigRepository.existsByCategoryCodeAndActiveTrueAndDeletedFalse("MEAL"))
                    .thenReturn(true);

            PolicyConfigRequest duplicatePolicyRequest =
                    request("MEAL", new BigDecimal("500.00"), "INR", null);

            assertThatThrownBy(() -> policyConfigService.createPolicyConfig(duplicatePolicyRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("MEAL");

            verify(policyConfigRepository, never()).save(any(PolicyConfig.class));
        }

        @Test
        @DisplayName("throws PolicyViolationException when the currency is not the single base currency (NFR-05) and saves nothing")
        void throwsWhenCurrencyIsNotBaseCurrency() {
            assertThatThrownBy(() -> policyConfigService.createPolicyConfig(
                    request("MEAL", new BigDecimal("500.00"), "USD", null)))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining(BusinessConstants.BASE_CURRENCY);

            verify(policyConfigRepository, never()).save(any(PolicyConfig.class));
            verify(expenseCategoryRepository, never()).findByCodeAndDeletedFalse(any());
        }
    }

    @Nested
    @DisplayName("updatePolicyConfig")
    class UpdatePolicyConfig {

        @Test
        @DisplayName("updates the config in place when the uniqueness check excluding its own id passes")
        void updatesConfigInPlace() {
            PolicyConfig existing = config(1L, "MEAL", new BigDecimal("500.00"), "USD", true);
            when(policyConfigRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(expenseCategoryRepository.findByCodeAndDeletedFalse("MEAL"))
                    .thenReturn(Optional.of(category(2L, "MEAL")));
            when(policyConfigRepository.existsByCategoryCodeAndActiveTrueAndDeletedFalseAndIdNot("MEAL", 1L))
                    .thenReturn(false);
            when(policyConfigRepository.save(any(PolicyConfig.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PolicyConfigResponse response = policyConfigService.updatePolicyConfig(1L,
                    request("MEAL", new BigDecimal("750.00"), "INR", null));

            ArgumentCaptor<PolicyConfig> captor = ArgumentCaptor.forClass(PolicyConfig.class);
            verify(policyConfigRepository).save(captor.capture());
            PolicyConfig saved = captor.getValue();
            assertThat(saved).isSameAs(existing);
            assertThat(saved.getCategoryCode()).isEqualTo("MEAL");
            assertThat(saved.getReceiptThresholdAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
            assertThat(saved.getCurrency()).isEqualTo("INR");
            assertThat(saved.isActive()).isTrue();

            assertThat(response).isEqualTo(new PolicyConfigResponse(1L, "MEAL", new BigDecimal("750.00"), "INR", true));
        }

        @Test
        @DisplayName("throws PolicyViolationException when the currency is not the single base currency (NFR-05) and leaves the config unmodified")
        void throwsWhenCurrencyIsNotBaseCurrency() {
            PolicyConfig existing = config(1L, "MEAL", new BigDecimal("500.00"), "INR", true);

            assertThatThrownBy(() -> policyConfigService.updatePolicyConfig(1L,
                    request("MEAL", new BigDecimal("750.00"), "USD", null)))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining(BusinessConstants.BASE_CURRENCY);

            assertThat(existing.getCurrency()).isEqualTo("INR");
            assertThat(existing.getReceiptThresholdAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            verify(policyConfigRepository, never()).save(any(PolicyConfig.class));
        }
    }

    @Nested
    @DisplayName("getPolicyConfig")
    class GetPolicyConfig {

        @Test
        @DisplayName("maps the config to a response when found")
        void returnsMappedConfig() {
            PolicyConfig existing = config(1L, "MEAL", new BigDecimal("500.00"), "USD", true);
            when(policyConfigRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));

            PolicyConfigResponse response = policyConfigService.getPolicyConfig(1L);

            assertThat(response).isEqualTo(new PolicyConfigResponse(1L, "MEAL", new BigDecimal("500.00"), "USD", true));
            verify(policyConfigMapper).toResponse(existing);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the config does not exist")
        void throwsWhenNotFound() {
            when(policyConfigRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> policyConfigService.getPolicyConfig(1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(policyConfigMapper, never()).toResponse(any(PolicyConfig.class));
        }
    }

    @Nested
    @DisplayName("listPolicyConfigs")
    class ListPolicyConfigs {

        @Test
        @DisplayName("maps the repository page of non-deleted configs to a response page")
        void mapsPage() {
            Pageable pageable = Pageable.ofSize(20);
            PolicyConfig first = config(1L, "MEAL", new BigDecimal("500.00"), "USD", true);
            PolicyConfig second = config(2L, "TRAVEL", new BigDecimal("1000.00"), "USD", false);
            when(policyConfigRepository.findByDeletedFalse(pageable))
                    .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));

            Page<PolicyConfigResponse> page = policyConfigService.listPolicyConfigs(pageable);

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).containsExactly(
                    new PolicyConfigResponse(1L, "MEAL", new BigDecimal("500.00"), "USD", true),
                    new PolicyConfigResponse(2L, "TRAVEL", new BigDecimal("1000.00"), "USD", false));
        }
    }
}