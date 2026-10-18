package com.travelease.admin.config;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.travelease.admin.entity.EmployeeManagerMapping;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.EmployeeManagerMappingRepository;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.common.audit.Auditable;
import com.travelease.common.config.DemoDataSeedingGuard;
import com.travelease.common.security.RoleType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in demo account seeder for local development (tasks.md K-01; data-model.md §9; NFR-08, Q26).
 *
 * <p>Runs only when {@link DemoDataSeedingGuard} permits it — i.e. {@code travelease.seed.demo-data.enabled=true}
 * with a development profile and no production profile (see {@code application-dev.yml}). It never
 * runs in production, and fails startup when the flag is combined with a production profile.</p>
 *
 * <p>Seeds the §9 baseline — U1 Asha Rao (EMPLOYEE, reports to U2), U2 Rahul Mehta (EMPLOYEE +
 * MANAGER, clarification Q1/Q2, reports to U3), U3 Priya Nair (MANAGER, top-of-hierarchy with a
 * null manager so approvals fall back to Finance, clarification Q3, EC-09), U4 Finance Desk
 * (FINANCE), U5 Sam Admin (ADMIN), plus Alice Employee (EMPLOYEE, reports to U2).</p>
 *
 * <p>Create-only and idempotent: each account is created only when no account with its email
 * exists, and each mapping only when the employee has no mapping yet. Existing accounts —
 * including their passwords, roles and active flag — are never modified, so restarting with the
 * seeder enabled cannot reset anyone's password.</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSeeder.class);

    /** Audit actor recorded on all seeded rows (no {@code AuditorAware} bean exists in admin-service). */
    private static final String SEED_ACTOR = "system-seed";

    /** Fixed effective date for all seeded mappings (data-model.md §9; NFR-08). */
    private static final LocalDate SEED_EFFECTIVE_FROM = LocalDate.of(2026, 1, 1);

    static final String ASHA_EMAIL = "asha.rao@travelease.example";
    static final String RAHUL_EMAIL = "rahul.mehta@travelease.example";
    static final String PRIYA_EMAIL = "priya.nair@travelease.example";
    static final String FINANCE_EMAIL = "finance.desk@travelease.example";
    static final String ADMIN_EMAIL = "sam.admin@travelease.example";
    static final String ALICE_EMAIL = "alice.employee@travelease.example";

    /** Creation order matters: it keeps U1..U5 at ids 1..5 on an empty database (data-model.md §9). */
    private static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
            new DemoAccount("Asha Rao", ASHA_EMAIL, List.of(RoleType.EMPLOYEE)),
            new DemoAccount("Rahul Mehta", RAHUL_EMAIL, List.of(RoleType.EMPLOYEE, RoleType.MANAGER)),
            new DemoAccount("Priya Nair", PRIYA_EMAIL, List.of(RoleType.MANAGER)),
            new DemoAccount("Finance Desk", FINANCE_EMAIL, List.of(RoleType.FINANCE)),
            new DemoAccount("Sam Admin", ADMIN_EMAIL, List.of(RoleType.ADMIN)),
            new DemoAccount("Alice Employee", ALICE_EMAIL, List.of(RoleType.EMPLOYEE)));

    /** Employee -> manager chain; a null manager email marks the top of the hierarchy. */
    private static final List<DemoMapping> DEMO_MAPPINGS = List.of(
            new DemoMapping(ASHA_EMAIL, RAHUL_EMAIL),
            new DemoMapping(RAHUL_EMAIL, PRIYA_EMAIL),
            new DemoMapping(PRIYA_EMAIL, null),
            new DemoMapping(ALICE_EMAIL, RAHUL_EMAIL));

    private final UserAccountRepository userAccountRepository;
    private final EmployeeManagerMappingRepository employeeManagerMappingRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String seedLoginPassword;

    public DataSeeder(UserAccountRepository userAccountRepository,
                      EmployeeManagerMappingRepository employeeManagerMappingRepository,
                      PasswordEncoder passwordEncoder,
                      Environment environment,
                      @Value("${security.seed.password:}") String seedLoginPassword) {
        this.userAccountRepository = userAccountRepository;
        this.employeeManagerMappingRepository = employeeManagerMappingRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.seedLoginPassword = seedLoginPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!DemoDataSeedingGuard.isDemoSeedingPermitted(environment)) {
            LOGGER.info("Demo account seeding disabled ({} is not enabled for a development profile)",
                    DemoDataSeedingGuard.DEMO_SEEDING_PROPERTY);
            return;
        }
        if (seedLoginPassword == null || seedLoginPassword.isBlank()) {
            throw new IllegalStateException(
                    "Demo seeding is enabled but security.seed.password is empty (set TRAVELEASE_SEED_PASSWORD)");
        }

        Instant now = Instant.now();
        Map<String, Long> idsByEmail = new HashMap<>();
        int created = 0;
        for (DemoAccount demo : DEMO_ACCOUNTS) {
            Optional<UserAccount> existing = userAccountRepository.findByEmailAndDeletedFalse(demo.email());
            if (existing.isPresent()) {
                idsByEmail.put(demo.email(), existing.get().getId());
                continue;
            }
            idsByEmail.put(demo.email(), saveAccount(demo, now).getId());
            created++;
        }

        int mapped = 0;
        for (DemoMapping demo : DEMO_MAPPINGS) {
            Long employeeId = idsByEmail.get(demo.employeeEmail());
            Long managerId = demo.managerEmail() == null ? null : idsByEmail.get(demo.managerEmail());
            if (employeeId == null || (demo.managerEmail() != null && managerId == null)) {
                continue;
            }
            if (employeeManagerMappingRepository.findByEmployeeIdAndDeletedFalse(employeeId).isPresent()) {
                continue;
            }
            saveMapping(employeeId, managerId, now);
            mapped++;
        }

        LOGGER.info("Demo seeding complete: {} account(s) and {} mapping(s) created; existing accounts left untouched",
                created, mapped);
    }

    private UserAccount saveAccount(DemoAccount demo, Instant now) {
        UserAccount account = new UserAccount();
        account.setFullName(demo.fullName());
        account.setEmail(demo.email());
        account.setPasswordHash(passwordEncoder.encode(seedLoginPassword));
        account.setActive(true);
        account.setDeleted(false);
        stampAudit(account, now);
        for (RoleType role : demo.roles()) {
            UserRole userRole = new UserRole();
            userRole.setRole(role);
            stampAudit(userRole, now);
            account.addRole(userRole);
        }
        return userAccountRepository.save(account);
    }

    private void saveMapping(Long employeeId, Long managerId, Instant now) {
        EmployeeManagerMapping mapping = new EmployeeManagerMapping();
        mapping.setEmployeeId(employeeId);
        mapping.setManagerId(managerId);
        mapping.setEffectiveFrom(SEED_EFFECTIVE_FROM);
        stampAudit(mapping, now);
        employeeManagerMappingRepository.save(mapping);
    }

    private void stampAudit(Auditable entity, Instant now) {
        entity.setCreatedAt(now);
        entity.setCreatedBy(SEED_ACTOR);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(SEED_ACTOR);
    }

    private record DemoAccount(String fullName, String email, List<RoleType> roles) {
    }

    private record DemoMapping(String employeeEmail, String managerEmail) {
    }
}
