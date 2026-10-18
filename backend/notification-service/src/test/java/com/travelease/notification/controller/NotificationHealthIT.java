package com.travelease.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.travelease.notification.NotificationServiceApplication;

/**
 * With e-mail delivery off (the default) the service must report healthy. The application.yml leaves
 * {@code spring.mail.host} empty, which still activates Spring's mail health check; without the
 * {@code management.health.mail.enabled} switch that check fails and the whole service reports DOWN.
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications-health-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "server.servlet.context-path="
})
@DisplayName("Notification service health with e-mail disabled")
class NotificationHealthIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("reports UP without an SMTP server")
    void healthyWithoutSmtp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
