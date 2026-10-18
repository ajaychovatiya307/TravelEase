package com.travelease.travelrequest.integration;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Record-level authorization of travel-request approval decisions (AC-03, NFR-10), exercised through
 * the real Controller -> Service -> Repository stack on H2 with real method security. Only the Admin
 * Service routing/policy clients are mocked (external boundary): they decide which approver a new
 * request is routed to, exactly as in production.
 *
 * <p>Replaces the controller test that "proved" the assigned-approver rule by mocking the service to
 * throw 403 — that test could not detect the rule's absence.</p>
 */
@SpringBootTest(classes = TravelRequestIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.datasource.url=jdbc:h2:mem:travelrequest-authz-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Travel request approval authorization IT (AC-03, NFR-10)")
class TravelRequestApprovalAuthorizationIT {

    private static final long EMPLOYEE_ID = 1L;
    /** Holds EMPLOYEE + MANAGER (the "manager acting as employee" case, clarification Q1/Q2). */
    private static final long DUAL_ROLE_MANAGER_ID = 2L;
    private static final long OTHER_MANAGER_ID = 3L;
    private static final long FINANCE_ID = 4L;
    private static final long OTHER_FINANCE_ID = 5L;

    private static final String APPROVE_BODY = "{\"decision\":\"APPROVED\"}";

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

    @BeforeEach
    void noDocumentMandate() {
        when(travelRequestPolicyClient.documentsMandatory()).thenReturn(false);
    }

    private static RequestPostProcessor user(long id, String... roles) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(id)).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    /** Submits a request as {@code requester}, routed by the Admin Service to {@code routedApprover}. */
    private long submitAs(RequestPostProcessor requester, Long routedApprover) throws Exception {
        when(routingServiceClient.resolveApprover(anyLong())).thenReturn(routedApprover);
        String body = """
                {"destination":"Pune","startDate":"%s","endDate":"%s",
                 "justification":"Customer onsite planning workshop","documentIds":[]}
                """.formatted(LocalDate.now().plusDays(30), LocalDate.now().plusDays(33));
        String response = mockMvc.perform(post("/api/travel-requests")
                        .with(requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions decide(long requestId, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post("/api/travel-requests/" + requestId + "/approval")
                .with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(APPROVE_BODY));
    }

    private TravelRequestStatus persistedStatus(long requestId) {
        return travelRequestRepository.findById(requestId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("the assigned approver can approve a pending request")
    void assignedApproverCanApprove() throws Exception {
        long id = submitAs(user(EMPLOYEE_ID, "EMPLOYEE"), DUAL_ROLE_MANAGER_ID);

        decide(id, user(DUAL_ROLE_MANAGER_ID, "EMPLOYEE", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("a different manager gets 403 and the request stays PENDING")
    void otherManagerForbidden() throws Exception {
        long id = submitAs(user(EMPLOYEE_ID, "EMPLOYEE"), DUAL_ROLE_MANAGER_ID);

        decide(id, user(OTHER_MANAGER_ID, "MANAGER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"));

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);
    }

    @Test
    @DisplayName("a Finance user who is not the assigned approver cannot approve on role alone")
    void unassignedFinanceForbidden() throws Exception {
        long id = submitAs(user(EMPLOYEE_ID, "EMPLOYEE"), DUAL_ROLE_MANAGER_ID);

        decide(id, user(FINANCE_ID, "FINANCE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"));

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);
    }

    @Test
    @DisplayName("a dual-role manager cannot approve their own request (routed to someone else)")
    void dualRoleManagerCannotApproveOwnRequest() throws Exception {
        RequestPostProcessor rahul = user(DUAL_ROLE_MANAGER_ID, "EMPLOYEE", "MANAGER");
        long id = submitAs(rahul, OTHER_MANAGER_ID);

        decide(id, rahul).andExpect(status().isForbidden());

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);
    }

    @Test
    @DisplayName("self-approval is refused even if routing is misconfigured to make the requester the approver")
    void selfApprovalRefusedEvenWhenMisrouted() throws Exception {
        RequestPostProcessor rahul = user(DUAL_ROLE_MANAGER_ID, "EMPLOYEE", "MANAGER");
        long id = submitAs(rahul, DUAL_ROLE_MANAGER_ID);

        decide(id, rahul)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot approve or reject your own travel request"));

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Finance fallback: the Finance user routed as approver for a top-of-hierarchy manager can approve")
    void routedFinanceFallbackCanApprove() throws Exception {
        // Top-of-hierarchy manager: the Admin Service routes the approval to a specific Finance user (Q3, EC-09)
        long id = submitAs(user(OTHER_MANAGER_ID, "EMPLOYEE", "MANAGER"), FINANCE_ID);

        decide(id, user(OTHER_FINANCE_ID, "FINANCE")).andExpect(status().isForbidden());
        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);

        decide(id, user(FINANCE_ID, "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("a request with no assigned approver cannot be decided by anyone (fails closed)")
    void missingApproverFailsClosed() throws Exception {
        TravelRequest orphan = new TravelRequest();
        orphan.setEmployeeId(EMPLOYEE_ID);
        orphan.setDestination("Delhi");
        orphan.setStartDate(LocalDate.now().plusDays(10));
        orphan.setEndDate(LocalDate.now().plusDays(12));
        orphan.setJustification("Legacy row without an approver assignment");
        orphan.setStatus(TravelRequestStatus.PENDING);
        orphan.setApproverId(null);
        orphan.setDeleted(false);
        orphan.setCreatedAt(java.time.Instant.now());
        orphan.setCreatedBy("it");
        orphan.setUpdatedAt(java.time.Instant.now());
        orphan.setUpdatedBy("it");
        long id = travelRequestRepository.saveAndFlush(orphan).getId();

        decide(id, user(OTHER_MANAGER_ID, "MANAGER")).andExpect(status().isForbidden());
        decide(id, user(FINANCE_ID, "FINANCE")).andExpect(status().isForbidden());

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);
    }

    @Test
    @DisplayName("an employee without MANAGER/FINANCE role is rejected by method security (403)")
    void employeeRoleForbidden() throws Exception {
        long id = submitAs(user(EMPLOYEE_ID, "EMPLOYEE"), DUAL_ROLE_MANAGER_ID);

        decide(id, user(OTHER_MANAGER_ID + 10, "EMPLOYEE")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated decision attempt gets 401")
    void unauthenticatedRejected() throws Exception {
        long id = submitAs(user(EMPLOYEE_ID, "EMPLOYEE"), DUAL_ROLE_MANAGER_ID);

        mockMvc.perform(post("/api/travel-requests/" + id + "/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE_BODY))
                .andExpect(status().isUnauthorized());

        assertThat(persistedStatus(id)).isEqualTo(TravelRequestStatus.PENDING);
    }
}
