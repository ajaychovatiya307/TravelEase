package com.travelease.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Boots a real (empty) Spring application so the {@link ProductionSafetyEnvironmentPostProcessor}
 * registered in common-lib's {@code META-INF/spring.factories} runs exactly as it does in each service.
 */
class ProductionSafetyStartupTest {

    private static final String STRONG_SECRET = "startup-test-secret-0123456789-abcdefghij";

    @Configuration(proxyBeanMethods = false)
    static class EmptyApp {
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties, String... profiles) {
        return new SpringApplicationBuilder(EmptyApp.class)
                .web(WebApplicationType.NONE)
                .profiles(profiles)
                .properties(properties)
                .run();
    }

    private static Map<String, Object> safeProd() {
        return Map.of(
                "security.jwt.secret", STRONG_SECRET,
                "security.internal.secret", "startup-test-internal-0123456789-abcdefghij",
                "spring.datasource.username", "travelease_app",
                "spring.datasource.password", "Xq7#long-unique-db-password");
    }

    @Test
    @DisplayName("production starts when the secret and DB credentials are properly supplied")
    void productionStartsWithProperSecrets() {
        try (ConfigurableApplicationContext context = start(safeProd(), "prod")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("production fails when the JWT secret is missing (env placeholder unresolved)")
    void productionFailsWithoutJwtSecret() {
        Map<String, Object> props = Map.of(
                "security.jwt.secret", "${TRAVELEASE_JWT_SECRET_NOT_SET_IN_TEST:}",
                "spring.datasource.username", "travelease_app",
                "spring.datasource.password", "Xq7#long-unique-db-password");

        assertThatThrownBy(() -> start(props, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.jwt.secret");
    }

    @Test
    @DisplayName("production rejects the previously published signing secret")
    void productionRejectsPublishedSecret() {
        Map<String, Object> props = new java.util.HashMap<>(safeProd());
        props.put("security.jwt.secret", "dev-only-shared-secret-change-me-before-production-0000");

        assertThatThrownBy(() -> start(props, "production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published");
    }

    @Test
    @DisplayName("production fails when DB credentials are missing (env placeholders unresolved)")
    void productionFailsWithoutDbCredentials() {
        Map<String, Object> props = Map.of(
                "security.jwt.secret", STRONG_SECRET,
                "spring.datasource.username", "${TRAVELEASE_DB_USERNAME_NOT_SET_IN_TEST}",
                "spring.datasource.password", "${TRAVELEASE_DB_PASSWORD_NOT_SET_IN_TEST}");

        assertThatThrownBy(() -> start(props, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.username")
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("production rejects the old root/root default without echoing the value")
    void productionRejectsRootRoot() {
        Map<String, Object> props = Map.of(
                "security.jwt.secret", STRONG_SECRET,
                "spring.datasource.username", "root",
                "spring.datasource.password", "root");

        assertThatThrownBy(() -> start(props, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("known default");
    }

    @Test
    @DisplayName("production refuses to start with demo seeding switched on, even alongside the dev profile")
    void productionRejectsDemoSeeding() {
        Map<String, Object> props = new java.util.HashMap<>(safeProd());
        props.put(DemoDataSeedingGuard.DEMO_SEEDING_PROPERTY, "true");

        assertThatThrownBy(() -> start(props, "dev", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DemoDataSeedingGuard.DEMO_SEEDING_PROPERTY);
    }

    @Test
    @DisplayName("an explicitly configured dev environment starts without production credentials")
    void developmentStarts() {
        Map<String, Object> props = Map.of(
                "security.jwt.secret", STRONG_SECRET,
                DemoDataSeedingGuard.DEMO_SEEDING_PROPERTY, "true");

        try (ConfigurableApplicationContext context = start(props, "dev")) {
            assertThat(DemoDataSeedingGuard.isDemoSeedingPermitted(context.getEnvironment())).isTrue();
        }
    }

    @Test
    @DisplayName("production requires a separate, strong internal service secret (#12)")
    void productionRequiresDistinctInternalSecret() {
        Map<String, Object> missing = new java.util.HashMap<>(safeProd());
        missing.remove("security.internal.secret");
        assertThatThrownBy(() -> start(missing, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.internal.secret");

        Map<String, Object> reused = new java.util.HashMap<>(safeProd());
        reused.put("security.internal.secret", STRONG_SECRET);
        assertThatThrownBy(() -> start(reused, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    @DisplayName("document-storing services must configure an explicit storage directory in production (#18)")
    void productionRequiresExplicitStorageDirectory() {
        Map<String, Object> props = new java.util.HashMap<>(safeProd());
        props.put("travelease.storage.required", "true");
        props.put("travelease.storage.base-dir", "relative/documents");

        assertThatThrownBy(() -> start(props, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("travelease.storage.base-dir");
    }
}
