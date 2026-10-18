package com.travelease.travelrequest.integration;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.TravelRequestDocumentService;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingServiceClient;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end-within-service integration tests for the Travel Request workflow
 * (tasks.md M-07; Constitution §10; spec.md FR-TR-01, FR-TR-06, FR-APR-01–03;
 * AC-01, AC-03, AC-05):
 *
 * <ul>
 *   <li>submit → approve: a PENDING request transitions to APPROVED and the
 *       decision is durably persisted (full Controller→Service→Repository
 *       round-trip against the in-memory H2 database, AC-01, AC-03)</li>
 *   <li>submit → reject → resubmit: a REJECTED request, edited by its owner,
 *       resubmits as a new PENDING request linked to the preserved rejected
 *       cycle, with the new cycle's rejection reason clear (AC-05, FR-APR-03)</li>
 * </ul>
 *
 * <p>Only the external-service boundaries (Constitution §2: no cross-service
 * DB access) are mocked: {@link EmployeeManagerRoutingServiceClient} (Admin Service
 * approver resolution), {@link TravelRequestPolicyClient} (Admin Service document
 * mandate, stubbed to the MVP default of no mandate) and {@link TravelRequestDocumentService}
 * (object storage, not exercised by this workflow). Everything else - controller, service, mapper,
 * validators, repositories, and the real JPA/H2 persistence layer - runs for real,
 * per tasks.md M-07's "Controller-to-Repository flow" requirement.</p>
 */
@SpringBootTest(classes = TravelRequestIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        // Force an in-memory H2 database (Constitution §10) regardless of the module's
        // main application.yml datasource (e.g. Postgres, whose driver is not on the test classpath).
        "spring.datasource.url=jdbc:h2:mem:travelrequest-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Travel Request workflow IT (tasks.md M-07; FR-TR-01, FR-TR-06, FR-APR-01–03; AC-01, AC-03, AC-05)")
class TravelRequestWorkflowIT {

    private static final long EMPLOYEE_ID = 1L;
    private static final long APPROVER_ID = 2L;

    private static final String START_DATE_ISO = "2027-01-10";
    private static final String END_DATE_ISO = "2027-01-15";
    private static final String JUSTIFICATION = "Quarterly client review on-site workshop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TravelRequestRepository travelRequestRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeManagerRoutingServiceClient routingServiceClient;

    @MockBean
    private TravelRequestPolicyClient travelRequestPolicyClient;

    @MockBean
    private TravelRequestDocumentService travelRequestDocumentService;

    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(EMPLOYEE_ID)).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private RequestPostProcessor approverJwt() {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(APPROVER_ID)).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private String createBody(String destination) {
        return """
                {
                    "destination": "%s",
                    "startDate": "%s",
                    "endDate": "%s",
                    "justification": "%s",
                    "documentIds": []
                }
                """.formatted(destination, START_DATE_ISO, END_DATE_ISO, JUSTIFICATION);
    }

    private long submitTravelRequest(String destination) throws Exception {
        when(routingServiceClient.resolveApprover(anyLong())).thenReturn(APPROVER_ID);
        // Admin-managed mandate flag (VAL-04, FR-TR-02): no documents mandated in this IT (MVP default)
        when(travelRequestPolicyClient.documentsMandatory()).thenReturn(false);

        String response = mockMvc.perform(post("/api/travel-requests")
                        .with(employeeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(destination)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        return Long.parseLong(objectMapper.readTree(response).get("id").asText());
    }

    @Nested
    @DisplayName("submit -> approve")
    class SubmitThenApprove {

        @Test
        @Transactional
        @DisplayName("a submitted request is approved and durably persisted as APPROVED (AC-01, AC-03)")
        void submitThenApproveApprovedRequest() throws Exception {
            long requestId = submitTravelRequest("Bengaluru");

            mockMvc.perform(post("/api/travel-requests/" + requestId + "/approval")
                            .with(approverJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision": "APPROVED"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.rejectionReason").doesNotExist());

            TravelRequest persisted = travelRequestRepository.findByIdAndDeletedFalse(requestId).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(TravelRequestStatus.APPROVED);
            assertThat(persisted.getRejectionReason()).isNull();
        }
    }

    @Nested
    @DisplayName("submit -> reject -> resubmit")
    class SubmitRejectResubmit {

        @Test
        @Transactional
        @DisplayName("a rejected request, edited by its owner, creates a linked PENDING cycle (AC-05, FR-APR-03)")
        void submitRejectThenResubmitReturnsToPending() throws Exception {
            long requestId = submitTravelRequest("Singapore");

            mockMvc.perform(post("/api/travel-requests/" + requestId + "/approval")
                            .with(approverJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision": "REJECTED", "reason": "Budget not approved for this quarter"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("Budget not approved for this quarter"));

            when(routingServiceClient.resolveApprover(anyLong())).thenReturn(APPROVER_ID);

            String resubmissionResponse = mockMvc.perform(put("/api/travel-requests/" + requestId)
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("Singapore (revised)"))) // resubmission, FR-APR-03
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.resubmissionOfId").value(requestId))
                    .andExpect(jsonPath("$.destination").value("Singapore (revised)"))
                    .andReturn().getResponse().getContentAsString();

            long resubmissionId = Long.parseLong(objectMapper.readTree(resubmissionResponse).get("id").asText());
            assertThat(resubmissionId).isNotEqualTo(requestId);

            TravelRequest rejectedCycle = travelRequestRepository.findByIdAndDeletedFalse(requestId).orElseThrow();
            assertThat(rejectedCycle.getStatus()).isEqualTo(TravelRequestStatus.REJECTED);
            assertThat(rejectedCycle.getRejectionReason())
                    .isEqualTo("Budget not approved for this quarter");

            TravelRequest pendingCycle = travelRequestRepository.findByIdAndDeletedFalse(resubmissionId).orElseThrow();
            assertThat(pendingCycle.getStatus()).isEqualTo(TravelRequestStatus.PENDING);
            assertThat(pendingCycle.getResubmissionOfId()).isEqualTo(requestId);
            assertThat(pendingCycle.getRejectionReason()).isNull();
        }
    }
}
