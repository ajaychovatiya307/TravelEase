package com.travelease.notification.controller;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Shared bootstrap configuration for every {@code @WebMvcTest} slice in this
 * package (tasks.md N-02 ingestion-endpoint tests).
 *
 * <p>{@code @ComponentScan} (scanning this configuration's own package, where
 * the production controllers also live) is required for the slices to register
 * the controller bean — a bare {@code @SpringBootConfiguration} performs no
 * component scanning, leaving the controller unmapped. The slice's type-exclude
 * filter still applies to this scan, so only the controller named in each test's
 * {@code @WebMvcTest(controllers = ...)} is picked up. Deliberately no
 * {@code @EnableAutoConfiguration} here: {@code @WebMvcTest} already limits
 * auto-configuration to the WebMvc slice, and enabling it would drag in
 * JPA/DataSource auto-configuration.</p>
 */
@SpringBootConfiguration
@ComponentScan
class NotificationTestApplication {
}