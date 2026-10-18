package com.travelease.admin.controller;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import com.travelease.admin.service.AuthenticationService;

import static org.mockito.Mockito.mock;

/**
 * Shared bootstrap configuration for every {@code @WebMvcTest} slice in this
 * package (tasks.md M-05 and the later admin controller tests).
 *
 * <p>The module has no top-level {@code @SpringBootApplication} class, so each
 * WebMvc slice needs an explicit {@code @SpringBootConfiguration} — and exactly
 * ONE per package: the bootstrapper fails with "Found multiple
 * {@code @SpringBootConfiguration} annotated classes" when several test classes
 * each declare a nested one. Hence this single shared top-level config.</p>
 *
 * <p>{@code @ComponentScan} (scanning this configuration's own package, where
 * the production controllers also live) is required for the slices to register
 * the controller bean — a bare {@code @SpringBootConfiguration} performs no
 * component scanning, leaving the controller unmapped (M-01's root-cause bug:
 * only {@code /error} was registered). The slice's type-exclude filter still
 * applies to this scan, so only the controller named in each test's
 * {@code @WebMvcTest(controllers = ...)} is picked up — the other admin
 * controllers in this package are not registered and their service
 * collaborators are not required. Deliberately no
 * {@code @EnableAutoConfiguration} here: {@code @WebMvcTest} already limits
 * auto-configuration to the WebMvc slice, and enabling it would drag in
 * JPA/DataSource auto-configuration.</p>
 */
@SpringBootConfiguration
@ComponentScan
class AdminTestApplication {

    @Bean
    @Primary
    AuthenticationService authenticationServiceForMvcSlices() {
        return mock(AuthenticationService.class);
    }

    /** Password lifecycle (#14) is mocked for slices that do not exercise it. */
    @Bean
    @Primary
    com.travelease.admin.service.PasswordManagementService passwordManagementServiceForMvcSlices() {
        return mock(com.travelease.admin.service.PasswordManagementService.class);
    }

    /** The real limiter (in-memory) so login slices exercise the actual lockout behaviour. */
    @Bean
    com.travelease.admin.security.LoginAttemptLimiter loginAttemptLimiter() {
        return new com.travelease.admin.security.LoginAttemptLimiter(5, 50,
                java.time.Duration.ofMinutes(15), java.time.Duration.ofMinutes(15));
    }
}