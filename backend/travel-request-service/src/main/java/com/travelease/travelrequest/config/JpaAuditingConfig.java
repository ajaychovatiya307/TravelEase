package com.travelease.travelrequest.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code Auditable.createdAt/By} and
 * {@code updatedAt/By} (common-lib) are populated on every save (NFR-04).
 * Dev-only auditor; replace with the authenticated principal once security
 * context propagation is wired end-to-end.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of("system");
    }
}
