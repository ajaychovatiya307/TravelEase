package com.travelease.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.travelease.admin.entity.EmployeeManagerMapping;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.EmployeeManagerMappingRepository;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.common.config.DemoDataSeedingGuard;
import com.travelease.common.security.RoleType;

/**
 * Exercises the real {@link DataSeeder} logic and {@link DemoDataSeedingGuard} decisions against an
 * in-memory account/mapping store (repositories are backed by maps, not stubbed per assertion), so
 * the create-only / never-reset rules are what is actually under test.
 */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    private static final String SEED_PASSWORD = "dev-seed-password-for-tests";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeManagerMappingRepository mappingRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    /** In-memory "database" behind the mocked repositories. */
    private final Map<String, UserAccount> accountsByEmail = new LinkedHashMap<>();
    private final List<EmployeeManagerMapping> mappings = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void wireInMemoryStore() {
        lenient().when(userAccountRepository.findByEmailAndDeletedFalse(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(accountsByEmail.get(inv.<String>getArgument(0))));
        lenient().when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount account = inv.getArgument(0);
            if (account.getId() == null) {
                account.setId(ids.getAndIncrement());
            }
            accountsByEmail.put(account.getEmail(), account);
            return account;
        });
        lenient().when(mappingRepository.findByEmployeeIdAndDeletedFalse(anyLong()))
                .thenAnswer(inv -> mappings.stream()
                        .filter(m -> m.getEmployeeId().equals(inv.getArgument(0)))
                        .findFirst());
        lenient().when(mappingRepository.save(any(EmployeeManagerMapping.class))).thenAnswer(inv -> {
            mappings.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private DataSeeder seeder(MockEnvironment environment) {
        return new DataSeeder(userAccountRepository, mappingRepository, passwordEncoder, environment, SEED_PASSWORD);
    }

    private static MockEnvironment env(boolean demoEnabled, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        if (demoEnabled) {
            environment.setProperty(DemoDataSeedingGuard.DEMO_SEEDING_PROPERTY, "true");
        }
        return environment;
    }

    private UserAccount existingAccount(String email, String rawPassword, RoleType role) {
        UserAccount account = new UserAccount();
        account.setId(ids.getAndIncrement());
        account.setFullName("Existing " + role);
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setActive(true);
        account.setDeleted(false);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        account.addRole(userRole);
        accountsByEmail.put(email, account);
        return account;
    }

    @Test
    @DisplayName("default (flag absent) never seeds, even with the dev profile active")
    void notEnabledByDefault() {
        seeder(env(false, "dev")).run();

        verifyNoInteractions(userAccountRepository, mappingRepository);
    }

    @Test
    @DisplayName("production profile never seeds: the flag combined with prod fails startup without writing")
    void productionProfileRefusesSeeding() {
        assertThatThrownBy(() -> seeder(env(true, "prod")).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production profile");

        verifyNoInteractions(userAccountRepository, mappingRepository);
    }

    @Test
    @DisplayName("prod combined with dev (contradictory) fails safe instead of seeding")
    void productionPlusDevelopmentProfileRefusesSeeding() {
        assertThatThrownBy(() -> seeder(env(true, "dev", "production")).run())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(userAccountRepository, mappingRepository);
    }

    @Test
    @DisplayName("flag without any development profile is ambiguous and fails safe")
    void flagWithoutDevelopmentProfileRefusesSeeding() {
        assertThatThrownBy(() -> seeder(env(true)).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development profile");

        verifyNoInteractions(userAccountRepository, mappingRepository);
    }

    @Test
    @DisplayName("explicitly enabled dev seeding creates the demo accounts and reporting chain on an empty store")
    void developmentSeedingCreatesDemoData() {
        seeder(env(true, "dev")).run();

        assertThat(accountsByEmail).containsOnlyKeys(
                DataSeeder.ASHA_EMAIL, DataSeeder.RAHUL_EMAIL, DataSeeder.PRIYA_EMAIL,
                DataSeeder.FINANCE_EMAIL, DataSeeder.ADMIN_EMAIL, DataSeeder.ALICE_EMAIL);
        assertThat(passwordEncoder.matches(SEED_PASSWORD, accountsByEmail.get(DataSeeder.ADMIN_EMAIL).getPasswordHash()))
                .isTrue();
        assertThat(accountsByEmail.get(DataSeeder.RAHUL_EMAIL).getRoles())
                .extracting(UserRole::getRole)
                .containsExactlyInAnyOrder(RoleType.EMPLOYEE, RoleType.MANAGER);
        assertThat(mappings).hasSize(4);
        EmployeeManagerMapping priya = mappings.stream()
                .filter(m -> m.getEmployeeId().equals(accountsByEmail.get(DataSeeder.PRIYA_EMAIL).getId()))
                .findFirst().orElseThrow();
        assertThat(priya.getManagerId()).isNull();
    }

    @Test
    @DisplayName("repeated dev seeding is idempotent: no duplicates and no password resets")
    void repeatedSeedingIsIdempotent() {
        seeder(env(true, "local")).run();
        UserAccount finance = accountsByEmail.get(DataSeeder.FINANCE_EMAIL);
        String hashAfterFirstRun = finance.getPasswordHash();
        int accountsAfterFirstRun = accountsByEmail.size();
        int mappingsAfterFirstRun = mappings.size();

        // a user changes their password between restarts
        finance.setPasswordHash(passwordEncoder.encode("changed-by-user"));
        String userChosenHash = finance.getPasswordHash();
        seeder(env(true, "local")).run();

        assertThat(accountsByEmail).hasSize(accountsAfterFirstRun);
        assertThat(mappings).hasSize(mappingsAfterFirstRun);
        assertThat(accountsByEmail.get(DataSeeder.FINANCE_EMAIL).getPasswordHash())
                .isEqualTo(userChosenHash)
                .isNotEqualTo(hashAfterFirstRun);
    }

    @Test
    @DisplayName("existing Finance users keep their passwords and are never saved by the seeder")
    void existingFinancePasswordsUnchanged() {
        UserAccount realFinance = existingAccount("fiona.finance@company.example", "real-finance-secret", RoleType.FINANCE);
        UserAccount demoEmailFinance = existingAccount(DataSeeder.FINANCE_EMAIL, "already-rotated", RoleType.FINANCE);
        String realHash = realFinance.getPasswordHash();
        String demoHash = demoEmailFinance.getPasswordHash();

        seeder(env(true, "dev")).run();

        assertThat(realFinance.getPasswordHash()).isEqualTo(realHash);
        assertThat(demoEmailFinance.getPasswordHash()).isEqualTo(demoHash);
        assertThat(passwordEncoder.matches(SEED_PASSWORD, demoEmailFinance.getPasswordHash())).isFalse();
        verify(userAccountRepository, never()).save(realFinance);
        verify(userAccountRepository, never()).save(demoEmailFinance);
    }

    @Test
    @DisplayName("an existing account with a demo email is not overwritten (roles, password, active flag)")
    void existingUsersNotOverwritten() {
        UserAccount admin = existingAccount(DataSeeder.ADMIN_EMAIL, "admin-own-password", RoleType.EMPLOYEE);
        admin.setActive(false);
        String hash = admin.getPasswordHash();

        seeder(env(true, "dev")).run();

        UserAccount after = accountsByEmail.get(DataSeeder.ADMIN_EMAIL);
        assertThat(after).isSameAs(admin);
        assertThat(after.getPasswordHash()).isEqualTo(hash);
        assertThat(after.isActive()).isFalse();
        assertThat(after.getRoles()).extracting(UserRole::getRole).containsExactly(RoleType.EMPLOYEE);
        verify(userAccountRepository, never()).save(admin);
    }

    @Test
    @DisplayName("enabled dev seeding without a seed password fails instead of creating password-less accounts")
    void missingSeedPasswordFails() {
        DataSeeder noPassword = new DataSeeder(userAccountRepository, mappingRepository, passwordEncoder,
                env(true, "dev"), "");

        assertThatThrownBy(noPassword::run).isInstanceOf(IllegalStateException.class);
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }
}
