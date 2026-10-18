package com.travelease.travelrequest.controller;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Shared bootstrap configuration for every {@code @WebMvcTest} slice in this
 * package (tasks.md M-01, M-02, and later controller tests).
 *
 * <p>The module has no top-level {@code @SpringBootApplication} class, so each
 * WebMvc slice needs an explicit {@code @SpringBootConfiguration} — and exactly
 * ONE per package: the bootstrapper fails with "Found multiple
 * {@code @SpringBootConfiguration} annotated classes" when several test classes
 * each declare a nested one. Hence this single shared top-level config.</p>
 *
 * <p>{@code @ComponentScan} (scanning this configuration's own package, where
 * the production controller also lives) is required for the slices to register
 * the controller bean — a bare {@code @SpringBootConfiguration} performs no
 * component scanning, leaving the controller unmapped (M-01's root-cause bug:
 * only {@code /error} was registered). Deliberately no
 * {@code @EnableAutoConfiguration} here: {@code @WebMvcTest} already limits
 * auto-configuration to the WebMvc slice, and enabling it would drag in
 * JPA/DataSource auto-configuration.</p>
 */
@SpringBootConfiguration
@ComponentScan
class TravelRequestTestApplication {
}
