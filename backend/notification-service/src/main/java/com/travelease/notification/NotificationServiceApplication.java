package com.travelease.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bootstrap for notification-service (tasks.md A-01); scans common-lib's package for its shared @Component beans. */
@SpringBootApplication(scanBasePackages = {"com.travelease.notification", "com.travelease.common"})
// scheduling: e-mail delivery worker (#17)
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
