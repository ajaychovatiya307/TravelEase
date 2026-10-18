package com.travelease.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.travelease.common.outbox.OutboxConfiguration;

/** Bootstrap for admin-service (tasks.md A-01); scans common-lib's package for its shared @Component beans. */
@SpringBootApplication(scanBasePackages = {"com.travelease.admin", "com.travelease.common"})
// Transactional outbox (common-lib): administrative audit events commit with the change (#12/#13).
@EntityScan(basePackages = {"com.travelease.admin", "com.travelease.common.outbox"})
@EnableJpaRepositories(basePackages = {"com.travelease.admin", "com.travelease.common.outbox"})
@Import(OutboxConfiguration.class)
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
