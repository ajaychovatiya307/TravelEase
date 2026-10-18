package com.travelease.common.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.env.Environment;

/**
 * Single decision point for whether demo/synthetic data (demo accounts with a published password,
 * sample travel requests and expense claims) may be written at startup.
 *
 * <p>Demo seeding is strictly opt-in. It is permitted only when
 * {@value #DEMO_SEEDING_PROPERTY}{@code =true} AND at least one development profile
 * ({@code dev}, {@code local}, {@code test}) is active AND no production profile
 * ({@code prod}, {@code production}) is active. When the flag is on but the profiles are missing or
 * contradictory, startup fails instead of seeding.</p>
 */
public final class DemoDataSeedingGuard {

    public static final String DEMO_SEEDING_PROPERTY = "travelease.seed.demo-data.enabled";

    static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "local", "test");

    private DemoDataSeedingGuard() {
        // utility holder - not instantiable
    }

    /**
     * @return {@code true} only when demo seeding was explicitly enabled for a development environment;
     *         {@code false} when it was not requested
     * @throws IllegalStateException when seeding was requested for a production or ambiguous environment
     */
    public static boolean isDemoSeedingPermitted(Environment environment) {
        boolean requested = Boolean.parseBoolean(
                environment.getProperty(DEMO_SEEDING_PROPERTY, "false").trim());
        if (!requested) {
            return false;
        }
        if (isProduction(environment)) {
            throw new IllegalStateException(DEMO_SEEDING_PROPERTY
                    + "=true is not allowed while a production profile is active "
                    + Arrays.toString(environment.getActiveProfiles())
                    + ". Remove the flag or the production profile.");
        }
        boolean development = Arrays.stream(environment.getActiveProfiles())
                .map(DemoDataSeedingGuard::normalize)
                .anyMatch(DEVELOPMENT_PROFILES::contains);
        if (!development) {
            throw new IllegalStateException(DEMO_SEEDING_PROPERTY
                    + "=true requires an explicit development profile (one of " + DEVELOPMENT_PROFILES
                    + "); active profiles: " + Arrays.toString(environment.getActiveProfiles()));
        }
        return true;
    }

    /** @return whether any production profile is active. */
    public static boolean isProduction(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(DemoDataSeedingGuard::normalize)
                .anyMatch(PRODUCTION_PROFILES::contains);
    }

    private static String normalize(String profile) {
        return profile.trim().toLowerCase(Locale.ROOT);
    }
}
