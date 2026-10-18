package com.travelease.admin.config;

import java.time.Instant;
import java.util.Locale;

import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.security.PasswordPolicy;
import com.travelease.common.security.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator of a fresh environment (#18), where demo seeding is never allowed.
 *
 * <p>Runs only when both {@code travelease.bootstrap.admin.email} and {@code .password} are set
 * (TRAVELEASE_BOOTSTRAP_ADMIN_EMAIL / TRAVELEASE_BOOTSTRAP_ADMIN_PASSWORD) and no active ADMIN exists.
 * The account must change its password at first login, so the bootstrap value is single-use and the
 * variables can be removed once the administrator has signed in. Existing accounts are never modified.</p>
 */
@Component
public class BootstrapAdminInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final String BOOTSTRAP_ACTOR = "system-bootstrap";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    @Autowired
    public BootstrapAdminInitializer(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
                                     @Value("${travelease.bootstrap.admin.email:}") String email,
                                     @Value("${travelease.bootstrap.admin.password:}") String password) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isEmpty() || password.isEmpty()) {
            return;
        }
        if (userAccountRepository.existsByDeletedFalseAndActiveTrueAndRoles_Role(RoleType.ADMIN)) {
            LOGGER.info("Bootstrap administrator skipped: an active ADMIN already exists");
            return;
        }
        if (userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse(email).isPresent()) {
            throw new IllegalStateException(
                    "Bootstrap administrator email belongs to an existing account; choose a different address");
        }
        try {
            PasswordPolicy.validate(password, email);
        } catch (RuntimeException ex) {
            // never echo the value; name the setting only
            throw new IllegalStateException(
                    "TRAVELEASE_BOOTSTRAP_ADMIN_PASSWORD does not satisfy the password policy", ex);
        }

        Instant now = Instant.now();
        UserAccount account = new UserAccount();
        account.setFullName("Administrator");
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setActive(true);
        account.setDeleted(false);
        account.setMustChangePassword(true);
        stamp(account, now);
        UserRole role = new UserRole();
        role.setRole(RoleType.ADMIN);
        stamp(role, now);
        account.addRole(role);
        userAccountRepository.save(account);
        LOGGER.info("Bootstrap administrator created; a password change is required at first login");
    }

    private static void stamp(com.travelease.common.audit.Auditable entity, Instant now) {
        entity.setCreatedAt(now);
        entity.setCreatedBy(BOOTSTRAP_ACTOR);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(BOOTSTRAP_ACTOR);
    }
}
