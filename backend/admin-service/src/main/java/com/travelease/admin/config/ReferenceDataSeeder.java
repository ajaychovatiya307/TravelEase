package com.travelease.admin.config;

import java.math.BigDecimal;
import java.time.Instant;

import com.travelease.admin.entity.ExpenseCategory;
import com.travelease.admin.entity.PolicyConfig;
import com.travelease.admin.entity.TravelRequestPolicy;
import com.travelease.admin.repository.ExpenseCategoryRepository;
import com.travelease.admin.repository.PolicyConfigRepository;
import com.travelease.admin.repository.TravelRequestPolicyRepository;
import com.travelease.common.audit.Auditable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baseline reference data every environment needs: the default expense categories with their
 * policy configurations (tasks.md K-02; data-model.md §9; FR-ADM-02, AC-08) and the singleton
 * travel-request policy row (spec.md VAL-04, FR-TR-02; clarification Q7).
 *
 * <p>Contains no user accounts or credentials, so it runs in every environment. It is strictly
 * insert-if-missing: each row is guarded independently and existing rows (including ones an Admin
 * has since edited) are never modified. Demo users live in {@link DataSeeder}, which is opt-in.</p>
 */
@Component
public class ReferenceDataSeeder implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    /** Audit actor recorded on seeded rows (admin-service defines no {@code AuditorAware} bean). */
    private static final String SEED_ACTOR = "system-seed";

    /** Single base currency for all seeded policy thresholds (NFR-05). */
    private static final String SEED_CURRENCY = "INR";

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final PolicyConfigRepository policyConfigRepository;
    private final TravelRequestPolicyRepository travelRequestPolicyRepository;

    public ReferenceDataSeeder(ExpenseCategoryRepository expenseCategoryRepository,
                               PolicyConfigRepository policyConfigRepository,
                               TravelRequestPolicyRepository travelRequestPolicyRepository) {
        this.expenseCategoryRepository = expenseCategoryRepository;
        this.policyConfigRepository = policyConfigRepository;
        this.travelRequestPolicyRepository = travelRequestPolicyRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Instant now = Instant.now();
        saveCategoryWithPolicy("TRANSPORT", "Transport", "1000.00", now);
        saveCategoryWithPolicy("LODGING", "Lodging", "3000.00", now);
        saveCategoryWithPolicy("MEALS", "Meals", "500.00", now);
        seedTravelRequestPolicy(now);
    }

    /**
     * Inserts the default non-mandated travel-request policy unless an active row exists, so the
     * document-mandate lookup used at submission time always resolves (spec.md VAL-04, Q7).
     */
    private void seedTravelRequestPolicy(Instant now) {
        if (travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse().isPresent()) {
            return;
        }
        TravelRequestPolicy policy = new TravelRequestPolicy();
        policy.setDocumentsMandatory(false);
        policy.setActive(true);
        policy.setDeleted(false);
        stampAudit(policy, now);
        travelRequestPolicyRepository.save(policy);
        LOGGER.info("Seeded default travel-request policy (documentsMandatory=false)");
    }

    /** Inserts a category and its active policy configuration, skipping whichever already exists. */
    private void saveCategoryWithPolicy(String code, String label, String receiptThreshold, Instant now) {
        if (!expenseCategoryRepository.existsByCodeAndDeletedFalse(code)) {
            ExpenseCategory category = new ExpenseCategory();
            category.setCode(code);
            category.setLabel(label);
            category.setActive(true);
            category.setDeleted(false);
            stampAudit(category, now);
            expenseCategoryRepository.save(category);
            LOGGER.info("Seeded expense category {}", code);
        }
        if (!policyConfigRepository.existsByCategoryCodeAndActiveTrueAndDeletedFalse(code)) {
            PolicyConfig policy = new PolicyConfig();
            policy.setCategoryCode(code);
            policy.setReceiptThresholdAmount(new BigDecimal(receiptThreshold));
            policy.setCurrency(SEED_CURRENCY);
            policy.setActive(true);
            policy.setDeleted(false);
            stampAudit(policy, now);
            policyConfigRepository.save(policy);
            LOGGER.info("Seeded policy configuration for {}", code);
        }
    }

    private void stampAudit(Auditable entity, Instant now) {
        entity.setCreatedAt(now);
        entity.setCreatedBy(SEED_ACTOR);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(SEED_ACTOR);
    }
}
