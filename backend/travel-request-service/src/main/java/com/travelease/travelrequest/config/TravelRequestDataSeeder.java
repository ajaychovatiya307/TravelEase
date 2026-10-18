package com.travelease.travelrequest.config;

import java.time.Instant;
import java.time.LocalDate;

import com.travelease.common.audit.Auditable;
import com.travelease.common.config.DemoDataSeedingGuard;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.repository.TravelRequestRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthetic data seeder for travel requests (tasks.md K-03; data-model.md §9; NFR-08),
 * mirroring the admin-service {@code DataSeeder} conventions established in K-01/K-02.
 *
 * <p>Implemented as a Spring {@link CommandLineRunner} component rather than a raw
 * {@code data.sql} script: the seeded entity extends {@link Auditable} and carries NOT NULL
 * audit columns ({@code createdAt/By}, {@code updatedAt/By}) plus a JPA-managed
 * {@code @Version} column, which a Java seeder populates reliably through the entity
 * lifecycle (Constitution §5/§9, NFR-04).</p>
 *
 * <p>Seeds the §9 travel-request baseline: TR1 U1→Bengaluru 2026-11-03..2026-11-06
 * (APPROVED, approver U2), TR2 U2→Singapore 2026-11-10..2026-11-12 (PENDING, approver U3)
 * and TR3 U3→New York 2026-12-01..2026-12-05 (PENDING, approver U4). {@code employeeId} and
 * {@code approverId} are plain logical {@code UserAccount} references with no JPA
 * association (Constitution §2). Assumed correspondence: the K-01 admin {@code DataSeeder}
 * inserts U1..U5 in that insertion order against IDENTITY-generated primary keys, so
 * U1=1, U2=2, U3=3, U4=4 (and U5=5) — the rows below therefore hard-code ids 1..4.</p>
 *
 * <p>{@code rejectionReason} and {@code resubmissionOfId} are left null (no seeded
 * rejections/resubmissions); {@code deleted} is false on every row.</p>
 *
 * <p>Idempotent: before seeding, the runner probes for any non-deleted request owned by the
 * U1 employee (id 1) via
 * {@link TravelRequestRepository#findByEmployeeIdAndDeletedFalse} and skips the seed set
 * (logging a message) when it has already been applied.</p>
 *
 * <p>Since travel-request-service defines no {@code AuditorAware} bean, this seeder
 * explicitly stamps {@code createdAt/By} and {@code updatedAt/By} ({@code "system-seed"})
 * on every entity before saving — those columns are NOT NULL and would otherwise be left
 * null by the audit listener.</p>
 */
@Component
public class TravelRequestDataSeeder implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelRequestDataSeeder.class);

    /** Audit actor recorded on all seeded rows (no {@code AuditorAware} bean exists in travel-request-service). */
    private static final String SEED_ACTOR = "system-seed";

    /** Logical id of the U1 employee (K-01 admin seed, see class Javadoc for the id correspondence). */
    private static final Long U1_EMPLOYEE_ID = 1L;

    /** Single-row probe page for the idempotency check. */
    private static final PageRequest PROBE_PAGE = PageRequest.of(0, 1);

    private final TravelRequestRepository travelRequestRepository;
    private final Environment environment;

    public TravelRequestDataSeeder(TravelRequestRepository travelRequestRepository, Environment environment) {
        this.travelRequestRepository = travelRequestRepository;
        this.environment = environment;
    }

    /**
     * Seeds the §9 travel-request baseline (tasks.md K-03; data-model.md §9; NFR-08)
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
        if (travelRequestRepository.findByEmployeeIdAndDeletedFalse(U1_EMPLOYEE_ID, PROBE_PAGE).hasContent()) {
            LOGGER.info("Travel request seed data already present (found request for employee {}) - skipping (tasks.md K-03)",
                    U1_EMPLOYEE_ID);
            return;
        }

        Instant now = Instant.now();

        // data-model.md §9 travel-request baseline (NFR-08): logical UserAccount ids 1..4
        // correspond to U1..U4 seeded by the K-01 admin DataSeeder.
        saveTravelRequest(new SeedTravelRequest(1L, "Bengaluru", LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 6), "Client workshop on Q4 delivery plan",
                TravelRequestStatus.APPROVED, 2L), now);
        saveTravelRequest(new SeedTravelRequest(2L, "Singapore", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), "Regional leadership offsite",
                TravelRequestStatus.PENDING, 3L), now);
        saveTravelRequest(new SeedTravelRequest(3L, "New York", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5), "Annual partner summit",
                TravelRequestStatus.PENDING, 4L), now);

        LOGGER.info("Seeded synthetic travel requests (tasks.md K-03; data-model.md §9; NFR-08): TR1 (U1 -> Bengaluru, APPROVED), TR2 (U2 -> Singapore, PENDING), TR3 (U3 -> New York, PENDING); total travel_request rows now {}",
                travelRequestRepository.count());
    }

    /**
     * Builds, audit-stamps, and persists one travel request (data-model.md §3.1).
     * {@code rejectionReason} and {@code resubmissionOfId} are deliberately left null —
     * the seeded rows contain no rejections or resubmissions — and {@code deleted} is
     * false.
     *
     * @param seed values for the seeded request
     * @param now audit timestamp for the seeded row
     * @return the persisted request with its generated id
     */
    private TravelRequest saveTravelRequest(SeedTravelRequest seed, Instant now) {
        TravelRequest request = new TravelRequest();
        request.setEmployeeId(seed.employeeId());
        request.setDestination(seed.destination());
        request.setStartDate(seed.startDate());
        request.setEndDate(seed.endDate());
        request.setJustification(seed.justification());
        request.setStatus(seed.status());
        request.setApproverId(seed.approverId());
        request.setDeleted(false);
        stampAudit(request, now);
        return travelRequestRepository.save(request);
    }

    private record SeedTravelRequest(
            Long employeeId,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String justification,
            TravelRequestStatus status,
            Long approverId) {
    }

    /**
     * Stamps the NOT NULL audit columns on a seeded entity (data-model.md §1, NFR-04).
     * Required because travel-request-service has no {@code AuditorAware} bean, so
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