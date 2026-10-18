package com.travelease.travelrequest.integration;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Full-context bootstrap for the {@code @SpringBootTest} workflow IT (tasks.md M-07).
 *
 * <p>Unlike the {@code @WebMvcTest} slices' {@code TestApplication} (controller package,
 * narrow {@code @ComponentScan}), this boots the whole production tree
 * ({@code com.travelease.travelrequest}) plus auto-configuration, so the real
 * Controller→Service→Repository chain runs against an in-memory H2 database
 * (Constitution §10). {@code @EntityScan}/{@code @EnableJpaRepositories} use explicit
 * base packages because this config class lives in the {@code .integration}
 * sub-package, a sibling of {@code .entity}/{@code .repository} rather than an ancestor.</p>
 *
 * <p>Excludes: the {@code .controller} package's own {@code @SpringBootConfiguration}
 * test classes (would otherwise trip Boot's "multiple @SpringBootConfiguration classes
 * found" check) and {@code TravelRequestDataSeeder} (a {@code CommandLineRunner} whose
 * startup seed rows would make workflow assertions depend on seeding order).</p>
 *
 * <p>JPA auditing is enabled by the production {@code JpaAuditingConfig}
 * (its {@code @EnableJpaAuditing} plus {@code AuditorAware<String>} bean),
 * picked up by {@code @ComponentScan} - duplicating either in this test config
 * would collide with it and break context loading, and without them
 * {@code Auditable.createdBy/updatedBy} (NOT NULL columns) would be left null
 * by {@code AuditingEntityListener} and every real {@code repository.save(...)}
 * in this test would fail the DB constraint.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = {"com.travelease.travelrequest.entity", "com.travelease.common.outbox"})
@EnableJpaRepositories(basePackages = {"com.travelease.travelrequest.repository", "com.travelease.common.outbox"})
@org.springframework.context.annotation.Import(com.travelease.common.outbox.OutboxConfiguration.class)
@ComponentScan(
        // common: shared @Components (document storage, validators) the production entry point also scans
        basePackages = {"com.travelease.travelrequest", "com.travelease.common"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*TestApplication$"),
                // the production entry point declares its own JPA/outbox wiring; this config replaces it
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*ServiceApplication$"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*DataSeeder$")
        })
class TravelRequestIntegrationTestConfig {
}
