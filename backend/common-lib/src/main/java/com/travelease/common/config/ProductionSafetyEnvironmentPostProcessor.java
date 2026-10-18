package com.travelease.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.travelease.common.security.JwtSecretPolicy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fails startup of any service that depends on common-lib when its configuration is unsafe for
 * production. Registered through {@code META-INF/spring.factories} so every service gets the same
 * checks without per-service wiring; runs after config files and profiles have been loaded.
 *
 * <p>Always: demo seeding must not be requested for a production/ambiguous environment
 * ({@link DemoDataSeedingGuard}).</p>
 *
 * <p>With a production profile active ({@code prod}/{@code production}):</p>
 * <ul>
 *   <li>{@code security.jwt.secret} must satisfy {@link JwtSecretPolicy};</li>
 *   <li>{@code spring.datasource.username}/{@code password} must be set, and must not be a known
 *       default credential.</li>
 * </ul>
 * Error messages name the offending setting but never echo secret values.
 */
public class ProductionSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String JWT_SECRET_PROPERTY = "security.jwt.secret";
    static final String DB_USERNAME_PROPERTY = "spring.datasource.username";
    static final String INTERNAL_SECRET_PROPERTY = "security.internal.secret";
    static final String DB_PASSWORD_PROPERTY = "spring.datasource.password";

    /** Default/placeholder database passwords that must never reach production. */
    private static final Set<String> INSECURE_DB_PASSWORDS =
            Set.of("root", "password", "admin", "changeme", "change-me", "secret", "sa");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        DemoDataSeedingGuard.isDemoSeedingPermitted(environment);
        if (!DemoDataSeedingGuard.isProduction(environment)) {
            return;
        }

        List<String> problems = new ArrayList<>();
        try {
            JwtSecretPolicy.requireUsableSecret(resolve(environment, JWT_SECRET_PROPERTY));
        } catch (IllegalStateException ex) {
            problems.add(ex.getMessage());
        }

        // service-to-service credential (#12): required, strong, and different from the user-token secret
        String internalSecret = resolve(environment, INTERNAL_SECRET_PROPERTY);
        try {
            JwtSecretPolicy.requireUsableSecret(internalSecret);
            if (internalSecret.equals(resolve(environment, JWT_SECRET_PROPERTY))) {
                problems.add(INTERNAL_SECRET_PROPERTY + " must differ from security.jwt.secret.");
            }
        } catch (IllegalStateException ex) {
            problems.add(INTERNAL_SECRET_PROPERTY + " (TRAVELEASE_INTERNAL_SECRET) is missing or too weak.");
        }

        String username = resolve(environment, DB_USERNAME_PROPERTY);
        String password = resolve(environment, DB_PASSWORD_PROPERTY);
        if (username == null || username.isBlank()) {
            problems.add(DB_USERNAME_PROPERTY + " is not configured (set TRAVELEASE_DB_USERNAME).");
        }
        if (password == null || password.isBlank()) {
            problems.add(DB_PASSWORD_PROPERTY + " is not configured (set TRAVELEASE_DB_PASSWORD).");
        } else if (INSECURE_DB_PASSWORDS.contains(password.trim().toLowerCase(Locale.ROOT))
                || password.equals(username)) {
            problems.add(DB_PASSWORD_PROPERTY + " is a known default/insecure value; supply a real credential.");
        }

        // services that store documents must use an explicit, absolute, persistent location in production
        if (Boolean.parseBoolean(String.valueOf(resolve(environment, "travelease.storage.required")))) {
            String explicitDir = System.getenv("TRAVELEASE_STORAGE_DIR");
            String configured = resolve(environment, "travelease.storage.base-dir");
            if (explicitDir == null || explicitDir.isBlank()
                    || configured == null || !java.nio.file.Paths.get(configured).isAbsolute()) {
                problems.add("travelease.storage.base-dir must be set explicitly to an absolute path (set TRAVELEASE_STORAGE_DIR).");
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with an unsafe production configuration:\n - "
                    + String.join("\n - ", problems));
        }
    }

    /** Resolves a property, treating an unresolvable placeholder (missing env var) as "not configured". */
    private static String resolve(ConfigurableEnvironment environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        // after ConfigDataEnvironmentPostProcessor so profile-specific files are already applied
        return Ordered.LOWEST_PRECEDENCE;
    }
}
