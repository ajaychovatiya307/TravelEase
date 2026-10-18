package com.travelease.travelrequest.integration;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingServiceClient;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #11: inactive approvers and administrator reassignment, through the real stack on H2. */
@SpringBootTest(classes = TravelRequestIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.datasource.url=jdbc:h2:mem:travelrequest-reassign-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Approver reassignment IT (#11)")
class TravelRequestReassignmentIT {

    private static final long EMPLOYEE = 1L;
    private static final long DEPARTED_MANAGER = 2L;
    private static final long NEW_MANAGER = 3L;
    private static final long ADMIN = 5L;
    private static final long PLAIN_EMPLOYEE = 8L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TravelRequestRepository repository;

    @Autowired
    private ApplicationEvents events;

    @MockBean
    private EmployeeManagerRoutingServiceClient routingClient;

    @MockBean
    private TravelRequestPolicyClient policyClient;

    @BeforeEach
    void stubs() {
        when(policyClient.documentsMandatory()).thenReturn(false);
        when(routingClient.approverIneligibilityReason(NEW_MANAGER)).thenReturn(null);
        when(routingClient.approverIneligibilityReason(PLAIN_EMPLOYEE)).thenReturn("User holds neither MANAGER nor FINANCE");
    }

    private static RequestPostProcessor user(long id, String... roles) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(id)).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    private long submitRoutedTo(long approver) throws Exception {
        when(routingClient.resolveApprover(anyLong())).thenReturn(approver);
        String response = mockMvc.perform(post("/api/travel-requests").with(user(EMPLOYEE, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"destination":"Pune","startDate":"%s","endDate":"%s",
                                 "justification":"Customer onsite workshop","documentIds":[]}
                                """.formatted(LocalDate.now().plusDays(20), LocalDate.now().plusDays(22))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions reassign(long id, RequestPostProcessor caller, long approverId) throws Exception {
        return mockMvc.perform(post("/api/travel-requests/" + id + "/reassign").with(caller)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approverId\":" + approverId + "}"));
    }

    private ResultActions approve(long id, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post("/api/travel-requests/" + id + "/approval").with(caller)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"));
    }

    @Test
    @DisplayName("an admin moves a stuck request to an eligible approver, who can then decide it; the old one cannot")
    void reassignThenNewApproverDecides() throws Exception {
        long id = submitRoutedTo(DEPARTED_MANAGER);

        reassign(id, user(ADMIN, "ADMIN"), NEW_MANAGER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approverId").value(String.valueOf(NEW_MANAGER)))
                .andExpect(jsonPath("$.status").value("PENDING"));

        DomainEventEnvelope event = events.stream(DomainEventEnvelope.class)
                .filter(e -> e.action().equals("REASSIGNED")).findFirst().orElseThrow();
        assertThat(event.recipientIds()).containsExactly(NEW_MANAGER);
        assertThat(event.metadata()).containsEntry("fromApproverId", String.valueOf(DEPARTED_MANAGER))
                .containsEntry("toApproverId", String.valueOf(NEW_MANAGER));

        approve(id, user(DEPARTED_MANAGER, "MANAGER")).andExpect(status().isForbidden());
        approve(id, user(NEW_MANAGER, "MANAGER")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("only administrators can reassign")
    void onlyAdmins() throws Exception {
        long id = submitRoutedTo(DEPARTED_MANAGER);

        reassign(id, user(NEW_MANAGER, "MANAGER"), NEW_MANAGER).andExpect(status().isForbidden());
        reassign(id, user(4L, "FINANCE"), NEW_MANAGER).andExpect(status().isForbidden());
        reassign(id, user(EMPLOYEE, "EMPLOYEE"), NEW_MANAGER).andExpect(status().isForbidden());
        assertThat(repository.findById(id).orElseThrow().getApproverId()).isEqualTo(DEPARTED_MANAGER);
    }

    @Test
    @DisplayName("reassignment never targets the requester, an ineligible user, or a decided request")
    void guards() throws Exception {
        long id = submitRoutedTo(DEPARTED_MANAGER);

        reassign(id, user(ADMIN, "ADMIN"), EMPLOYEE).andExpect(status().isBadRequest());
        reassign(id, user(ADMIN, "ADMIN"), PLAIN_EMPLOYEE)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"));
        assertThat(repository.findById(id).orElseThrow().getApproverId()).isEqualTo(DEPARTED_MANAGER);

        approve(id, user(DEPARTED_MANAGER, "MANAGER")).andExpect(status().isOk());
        reassign(id, user(ADMIN, "ADMIN"), NEW_MANAGER).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an employee whose approver is inactive gets a clear 422 at submission, not a 500")
    void inactiveApproverAtSubmission() throws Exception {
        when(routingClient.resolveApprover(anyLong())).thenThrow(new PolicyViolationException("approver",
                "Your assigned approver is no longer active. Ask an administrator to update your reporting line."));

        mockMvc.perform(post("/api/travel-requests").with(user(EMPLOYEE, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"destination":"Pune","startDate":"%s","endDate":"%s",
                                 "justification":"Customer onsite workshop","documentIds":[]}
                                """.formatted(LocalDate.now().plusDays(20), LocalDate.now().plusDays(22))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("POLICY_VIOLATION"));
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("administrators can list the requests waiting on a given approver; others cannot")
    void approverFilter() throws Exception {
        long stuck = submitRoutedTo(DEPARTED_MANAGER);
        submitRoutedTo(NEW_MANAGER);

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/travel-requests")
                        .param("scope", "ALL").param("approverId", String.valueOf(DEPARTED_MANAGER))
                        .with(user(ADMIN, "ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.path("content").findValuesAsText("id")).containsExactly(String.valueOf(stuck));

        mockMvc.perform(get("/api/travel-requests").param("scope", "ALL")
                        .param("approverId", String.valueOf(DEPARTED_MANAGER)).with(user(NEW_MANAGER, "MANAGER")))
                .andExpect(status().isForbidden());
    }
}
