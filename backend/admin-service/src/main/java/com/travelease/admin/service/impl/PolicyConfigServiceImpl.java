package com.travelease.admin.service.impl;

import com.travelease.admin.audit.AdminAuditEvents;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.admin.dto.PolicyConfigRequest;
import com.travelease.admin.dto.PolicyConfigResponse;
import com.travelease.admin.entity.PolicyConfig;
import com.travelease.admin.mapper.PolicyConfigMapper;
import com.travelease.admin.repository.ExpenseCategoryRepository;
import com.travelease.admin.repository.PolicyConfigRepository;
import com.travelease.admin.service.PolicyConfigService;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.validation.BusinessConstants;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin policy threshold configuration business logic (tasks.md H-09; spec.md
 * FR-ADM-02, consumed by Expense Service validation per FR-EXP-05/VAL-10).
 *
 * <p>Enforces that {@code categoryCode} references an existing {@code ExpenseCategory},
 * that at most one active configuration exists per category (data-model.md §5.4), and
 * that every configuration uses the single base currency
 * {@link BusinessConstants#BASE_CURRENCY} (spec.md NFR-05; clarification Q18) -
 * all business rules deliberately kept out of the DTO/repository layers
 * (Constitution §5/§6/§7).</p>
 */
@Service
public class PolicyConfigServiceImpl implements PolicyConfigService {

    private final PolicyConfigRepository policyConfigRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final PolicyConfigMapper policyConfigMapper;

    private final AdminAuditEvents auditEvents;

    public PolicyConfigServiceImpl(PolicyConfigRepository policyConfigRepository,
            ExpenseCategoryRepository expenseCategoryRepository, PolicyConfigMapper policyConfigMapper,
            AdminAuditEvents auditEvents) {
        this.policyConfigRepository = policyConfigRepository;
        this.expenseCategoryRepository = expenseCategoryRepository;
        this.policyConfigMapper = policyConfigMapper;
        this.auditEvents = auditEvents;
    }

    @Override
    @Transactional
    public PolicyConfigResponse createPolicyConfig(PolicyConfigRequest request) {
        validateBaseCurrency(request.currency());
        requireExistingCategory(request.categoryCode());
        if (Boolean.TRUE.equals(request.active())
                && policyConfigRepository.existsByCategoryCodeAndActiveTrueAndDeletedFalse(request.categoryCode())) {
            throw new DuplicateResourceException(
                    "An active policy configuration for category " + request.categoryCode() + " already exists");
        }
        PolicyConfig config = policyConfigMapper.toEntity(request);
        PolicyConfig saved = policyConfigRepository.save(config);
        auditEvents.publish(AuditEntityType.POLICY_CONFIG, saved.getId(), AuditActionType.CREATED, java.util.Map.of(
                "categoryCode", saved.getCategoryCode(),
                "receiptThreshold", String.valueOf(saved.getReceiptThresholdAmount()),
                "active", String.valueOf(saved.isActive())));
        return policyConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PolicyConfigResponse updatePolicyConfig(Long id, PolicyConfigRequest request) {
        validateBaseCurrency(request.currency());
        PolicyConfig config = policyConfigRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("PolicyConfig", id));
        requireExistingCategory(request.categoryCode());
        if (Boolean.TRUE.equals(request.active())
                && policyConfigRepository.existsByCategoryCodeAndActiveTrueAndDeletedFalseAndIdNot(request.categoryCode(), id)) {
            throw new DuplicateResourceException(
                    "An active policy configuration for category " + request.categoryCode() + " already exists");
        }

        String previousThreshold = String.valueOf(config.getReceiptThresholdAmount());
        boolean wasActive = config.isActive();
        config.setCategoryCode(request.categoryCode());
        config.setReceiptThresholdAmount(request.receiptThresholdAmount());
        config.setCurrency(request.currency());
        config.setActive(Boolean.TRUE.equals(request.active()));

        PolicyConfig saved = policyConfigRepository.save(config);
        // #13: receipt-threshold changes alter which claims need evidence
        auditEvents.publish(AuditEntityType.POLICY_CONFIG, saved.getId(), AuditActionType.UPDATED, java.util.Map.of(
                "categoryCode", saved.getCategoryCode(),
                "fromThreshold", previousThreshold,
                "toThreshold", String.valueOf(saved.getReceiptThresholdAmount()),
                "fromActive", String.valueOf(wasActive),
                "toActive", String.valueOf(saved.isActive())));
        return policyConfigMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyConfigResponse getPolicyConfig(Long id) {
        PolicyConfig config = policyConfigRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("PolicyConfig", id));
        return policyConfigMapper.toResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PolicyConfigResponse> listPolicyConfigs(Pageable pageable) {
        return policyConfigRepository.findByDeletedFalse(pageable).map(policyConfigMapper::toResponse);
    }

    /**
     * NFR-05 (spec.md §5; clarification Q18): the MVP supports a single base
     * currency, so a policy configuration's currency must equal
     * {@link BusinessConstants#BASE_CURRENCY} (case-insensitive); a missing or
     * foreign currency is rejected before any repository interaction.
     *
     * @param currency currency code from the create/update request
     * @throws PolicyViolationException (field {@code "currency"}) if the currency is
     *         missing or is not the base currency
     */
    private void validateBaseCurrency(String currency) {
        if (currency == null || currency.isBlank()
                || !BusinessConstants.BASE_CURRENCY.equalsIgnoreCase(currency)) {
            throw new PolicyViolationException("currency",
                    "Policy configurations must use the single base currency "
                            + BusinessConstants.BASE_CURRENCY + " (NFR-05)");
        }
    }

    private void requireExistingCategory(String categoryCode) {
        expenseCategoryRepository.findByCodeAndDeletedFalse(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", categoryCode));
    }
}
