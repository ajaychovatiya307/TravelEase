package com.travelease.expense.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.travelease.common.audit.Auditable;
import com.travelease.common.config.DemoDataSeedingGuard;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.repository.ExpenseClaimRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthetic data seeder for expense claims (tasks.md K-03; data-model.md §9; NFR-08),
 * mirroring the admin-service {@code DataSeeder} conventions established in K-01/K-02.
 *
 * <p>Implemented as a Spring {@link CommandLineRunner} component rather than a raw
 * {@code data.sql} script: the seeded entity extends {@link Auditable} and carries NOT NULL
 * audit columns ({@code createdAt/By}, {@code updatedAt/By}) plus a JPA-managed
 * {@code @Version} column, which a Java seeder populates reliably through the entity
 * lifecycle (Constitution §5/§9, NFR-04).</p>
 *
 * <p>Seeds the §9 expense-claim baseline: EX1 and EX2, both raised by U1 (logical
 * {@code UserAccount} id 1, see the K-01 admin seed id correspondence) against the seeded
 * Approved trip TR1 (logical {@code TravelRequest} id 1, seeded by the K-03
 * {@code TravelRequestDataSeeder}): 2500.00 INR, category {@code TRANSPORT} — the same
 * category code the K-02 admin seeder creates — expense date 2026-11-04, status PENDING.
 * {@code travelRequestId} is a plain identifier column (a logical, cross-service reference
 * to travel-request-service, no JPA association, Constitution §2).</p>
 *
 * <p>EX2 is the deliberate demo duplicate of EX1 (same amount, expense date and category
 * under the same trip) and is seeded with {@code duplicateFlag = true} so the Finance
 * review queue can immediately demo the duplicate indicator (FR-EXP-07, AC-09).
 * {@code rejectionReason} and {@code reimbursedAt} stay null; {@code deleted} is false on
 * every row.</p>
 *
 * <p>Idempotent: before seeding, the runner probes for any non-deleted claim owned by the
 * U1 employee (id 1) via
 * {@link ExpenseClaimRepository#findByEmployeeIdAndDeletedFalse} and skips the seed set
 * (logging a message) when it has already been applied.</p>
 *
 * <p>Since expense-service defines no {@code AuditorAware} bean, this seeder explicitly
 * stamps {@code createdAt/By} and {@code updatedAt/By} ({@code "system-seed"}) on every
 * entity before saving — those columns are NOT NULL and would otherwise be left null by
 * the audit listener.</p>
 */
@Component
public class ExpenseClaimDataSeeder implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpenseClaimDataSeeder.class);

    /** Audit actor recorded on all seeded rows (no {@code AuditorAware} bean exists in expense-service). */
    private static final String SEED_ACTOR = "system-seed";

    /** Logical id of the U1 employee (K-01 admin seed; U1..U5 are inserted in order against IDENTITY keys, so U1=1). */
    private static final Long U1_EMPLOYEE_ID = 1L;

    /** Logical id of the seeded Approved trip TR1 (K-03 {@code TravelRequestDataSeeder}). */
    private static final Long TR1_TRAVEL_REQUEST_ID = 1L;

    /** Seeded claim amount (data-model.md §9; NFR-05 base currency INR). */
    private static final BigDecimal SEED_AMOUNT = new BigDecimal("2500.00");

    /** Single base currency for the seeded claims (NFR-05). */
    private static final String SEED_CURRENCY = "INR";

    /** Category code matching the K-02 seeded ExpenseCategory (tasks.md K-02; FR-ADM-02). */
    private static final String SEED_CATEGORY = "TRANSPORT";

    /** Fixed expense date for the seeded claims (data-model.md §9; NFR-08). */
    private static final LocalDate SEED_EXPENSE_DATE = LocalDate.of(2026, 11, 4);

    /** Single-row probe page for the idempotency check. */
    private static final PageRequest PROBE_PAGE = PageRequest.of(0, 1);

    private final ExpenseClaimRepository expenseClaimRepository;
    private final Environment environment;

    public ExpenseClaimDataSeeder(ExpenseClaimRepository expenseClaimRepository, Environment environment) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.environment = environment;
    }

    /**
     * Seeds the §9 expense-claim baseline (tasks.md K-03; data-model.md §9; NFR-08)
     * unless the seed has already been applied. Runs at application startup after the
     * context is ready.
     *
     * @param args application arguments (unused)
     */
    @Override
    @Transactional
    public void run(String... args) {
        // Demo data only: opt-in for development profiles, never in production (DemoDataSeedingGuard).
        if (!DemoDataSeedingGuard.isDemoSeedingPermitted(environment)) {
            LOGGER.info("Demo data seeding disabled ({} is not enabled for a development profile)",
                    DemoDataSeedingGuard.DEMO_SEEDING_PROPERTY);
            return;
        }
        if (expenseClaimRepository.findByEmployeeIdAndDeletedFalse(U1_EMPLOYEE_ID, PROBE_PAGE).hasContent()) {
            LOGGER.info("Expense claim seed data already present (found claim for employee {}) - skipping (tasks.md K-03)",
                    U1_EMPLOYEE_ID);
            return;
        }

        Instant now = Instant.now();

        // data-model.md §9 expense-claim baseline (NFR-08): U1 raises two identical
        // transport claims against the seeded Approved trip TR1; EX2 is deliberately
        // flagged as the duplicate of EX1 for the Finance review queue demo (FR-EXP-07, AC-09).
        saveExpenseClaim(SEED_AMOUNT, false, now);
        saveExpenseClaim(SEED_AMOUNT, true, now);

        LOGGER.info("Seeded synthetic expense claims (tasks.md K-03; data-model.md §9; NFR-08): EX1 (U1, TR1, {} {}, {}, duplicateFlag=false), EX2 (U1, TR1, {} {}, {}, duplicateFlag=true); total expense_claim rows now {}",
                SEED_AMOUNT, SEED_CURRENCY, SEED_CATEGORY, SEED_AMOUNT, SEED_CURRENCY, SEED_CATEGORY,
                expenseClaimRepository.count());
    }

    /**
     * Builds, audit-stamps, and persists one expense claim (data-model.md §4.1).
     * {@code rejectionReason} and {@code reimbursedAt} are deliberately left null — the
     * seeded rows are fresh PENDING claims, none reimbursed or rejected — and
     * {@code deleted} is false.
     *
     * @param amount claim amount (2 decimals)
     * @param duplicateFlag true for the deliberately seeded duplicate claim (EX2) so the
     *                      Finance review queue can demo the duplicate indicator
     *                      (FR-EXP-07, AC-09)
     * @param now audit timestamp for the seeded row
     * @return the persisted claim with its generated id
     */
    private ExpenseClaim saveExpenseClaim(BigDecimal amount, boolean duplicateFlag, Instant now) {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setEmployeeId(U1_EMPLOYEE_ID);
        claim.setTravelRequestId(TR1_TRAVEL_REQUEST_ID);
        claim.setAmount(amount);
        claim.setCurrency(SEED_CURRENCY);
        claim.setCategory(SEED_CATEGORY);
        claim.setExpenseDate(SEED_EXPENSE_DATE);
        claim.setStatus(ExpenseClaimStatus.PENDING);
        claim.setDuplicateFlag(duplicateFlag);
        claim.setDeleted(false);
        stampAudit(claim, now);
        return expenseClaimRepository.save(claim);
    }

    /**
     * Stamps the NOT NULL audit columns on a seeded entity (data-model.md §1, NFR-04).
     * Required because expense-service has no {@code AuditorAware} bean, so
     * {@code @CreatedBy}/{@code @UpdatedBy} would otherwise be left null.
     *
     * @param entity entity to stamp (any {@link Auditable} subclass)
     * @param now audit timestamp for the seeded row
     */
    private void stampAudit(Auditable entity, Instant now) {
        entity.setCreatedAt(now);
        entity.setCreatedBy(SEED_ACTOR);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(SEED_ACTOR);
    }
}