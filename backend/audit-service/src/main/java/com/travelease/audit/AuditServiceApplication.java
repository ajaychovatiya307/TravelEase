package com.travelease.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Bootstrap for audit-service (tasks.md A-01); scans common-lib's package for its shared @Component beans. */
@SpringBootApplication(scanBasePackages = {"com.travelease.audit", "com.travelease.common"})
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
