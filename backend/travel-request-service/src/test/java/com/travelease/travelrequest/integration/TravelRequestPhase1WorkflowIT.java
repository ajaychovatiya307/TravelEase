package com.travelease.travelrequest.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.travelease.common.event.DomainEventEnvelope;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.impl.EmployeeManagerRoutingServiceClient;
import com.travelease.travelrequest.service.impl.TravelRequestPolicyClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Travel-request cancellation (#10), list scopes for multi-role users (#8) and document download (#6),
 * through the real Controller -> Service -> Repository stack on H2, the real document service and
 * on-disk storage in a temp directory. Only the Admin Service routing/policy clients are mocked.
 */
@SpringBootTest(classes = TravelRequestIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.datasource.url=jdbc:h2:mem:travelrequest-phase1-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Travel request cancellation, list scopes and document download IT (#6, #8, #10)")
class TravelRequestPhase1WorkflowIT {

    private static final long ASHA = 1L;              // EMPLOYEE, reports to Rahul
    private static final long RAHUL = 2L;             // EMPLOYEE + MANAGER, reports to Priya
    private static final long PRIYA = 3L;             // MANAGER
    private static final long FINANCE = 4L;
    private static final long ADMIN = 5L;
    private static final long OTHER_EMPLOYEE = 6L;
    private static final byte[] PDF = "%PDF-1.4 itinerary".getBytes(StandardCharsets.UTF_8);

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("travelease.storage.base-dir", () -> storageRoot.toString());
    }

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
    void noDocumentMandate() {
        when(policyClient.documentsMandatory()).thenReturn(false);
    }

    private static RequestPostProcessor user(long id, String... roles) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(id)).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    private static RequestPostProcessor asha() {
        return user(ASHA, "EMPLOYEE");
    }

    private static RequestPostProcessor rahul() {
        return user(RAHUL, "EMPLOYEE", "MANAGER");
    }

    private long submit(RequestPostProcessor requester, long approver, String destination) throws Exception {
        when(routingClient.resolveApprover(anyLong())).thenReturn(approver);
        String body = """
                {"destination":"%s","startDate":"%s","endDate":"%s",
                 "justification":"Quarterly customer planning workshop","documentIds":[]}
                """.formatted(destination, LocalDate.now().plusDays(20), LocalDate.now().plusDays(22));
        String response = mockMvc.perform(post("/api/travel-requests").with(requester)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions cancel(long id, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post("/api/travel-requests/" + id + "/cancel").with(caller));
    }

    private void approve(long id, RequestPostProcessor approver) throws Exception {
        mockMvc.perform(post("/api/travel-requests/" + id + "/approval").with(approver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }

    private TravelRequestStatus statusOf(long id) {
        return repository.findById(id).orElseThrow().getStatus();
    }

    private List<String> ids(ResultActions result) throws Exception {
        JsonNode page = objectMapper.readTree(result.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return page.path("content").findValuesAsText("id");
    }

    @Nested
    @DisplayName("#10 cancellation")
    class Cancellation {

        @Test
        @DisplayName("the owner cancels a PENDING request; it leaves the approval queue and cannot be approved")
        void ownerCancelsPending() throws Exception {
            long id = submit(asha(), RAHUL, "Pune");

            cancel(id, asha()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

            assertThat(statusOf(id)).isEqualTo(TravelRequestStatus.CANCELLED);
            DomainEventEnvelope cancelled = events.stream(DomainEventEnvelope.class)
                    .filter(e -> e.action().equals("CANCELLED")).findFirst().orElseThrow();
            assertThat(cancelled.entityId()).isEqualTo(String.valueOf(id));
            assertThat(cancelled.recipientIds()).containsExactly(RAHUL);

            mockMvc.perform(post("/api/travel-requests/" + id + "/approval").with(rahul())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isBadRequest());
            assertThat(statusOf(id)).isEqualTo(TravelRequestStatus.CANCELLED);
        }

        @Test
        @DisplayName("an APPROVED request can be withdrawn before the trip starts")
        void ownerCancelsApprovedFutureTrip() throws Exception {
            long id = submit(asha(), RAHUL, "Pune");
            approve(id, rahul());

            cancel(id, asha()).andExpect(status().isOk());
            assertThat(statusOf(id)).isEqualTo(TravelRequestStatus.CANCELLED);
        }

        @Test
        @DisplayName("an APPROVED trip that has started cannot be cancelled")
        void startedTripCannotBeCancelled() throws Exception {
            TravelRequest started = new TravelRequest();
            started.setEmployeeId(ASHA);
            started.setApproverId(RAHUL);
            started.setDestination("Delhi");
            started.setStartDate(LocalDate.now());
            started.setEndDate(LocalDate.now().plusDays(2));
            started.setJustification("Trip already under way");
            started.setStatus(TravelRequestStatus.APPROVED);
            started.setDeleted(false);
            started.setCreatedAt(Instant.now());
            started.setCreatedBy("it");
            started.setUpdatedAt(Instant.now());
            started.setUpdatedBy("it");
            long id = repository.saveAndFlush(started).getId();

            cancel(id, asha()).andExpect(status().isBadRequest());
            assertThat(statusOf(id)).isEqualTo(TravelRequestStatus.APPROVED);
        }

        @Test
        @DisplayName("REJECTED and already CANCELLED requests are not eligible")
        void ineligibleStatuses() throws Exception {
            long rejected = submit(asha(), RAHUL, "Pune");
            mockMvc.perform(post("/api/travel-requests/" + rejected + "/approval").with(rahul())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"REJECTED\",\"reason\":\"No budget\"}"))
                    .andExpect(status().isOk());
            cancel(rejected, asha()).andExpect(status().isBadRequest());

            long twice = submit(asha(), RAHUL, "Goa");
            cancel(twice, asha()).andExpect(status().isOk());
            cancel(twice, asha()).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("only the owner can cancel: other employees and the approver get 403, anonymous 401")
        void onlyOwnerCancels() throws Exception {
            long id = submit(asha(), RAHUL, "Pune");

            cancel(id, user(OTHER_EMPLOYEE, "EMPLOYEE")).andExpect(status().isForbidden());
            cancel(id, rahul()).andExpect(status().isForbidden());
            cancel(id, user(FINANCE, "FINANCE")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/travel-requests/" + id + "/cancel")).andExpect(status().isUnauthorized());

            assertThat(statusOf(id)).isEqualTo(TravelRequestStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("#8 list scopes")
    class ListScopes {

        @Test
        @DisplayName("a manager who is also an employee sees their own requests and their approval queue separately")
        void dualRoleManagerSeesBothViews() throws Exception {
            long rahulsOwn = submit(rahul(), PRIYA, "Chennai");
            long ashasForRahul = submit(asha(), RAHUL, "Pune");

            assertThat(ids(mockMvc.perform(get("/api/travel-requests").param("scope", "MINE").with(rahul()))))
                    .containsExactly(String.valueOf(rahulsOwn));
            assertThat(ids(mockMvc.perform(get("/api/travel-requests").param("scope", "APPROVALS").with(rahul()))))
                    .containsExactly(String.valueOf(ashasForRahul));
        }

        @Test
        @DisplayName("ownership comes from the token: an employeeId parameter cannot reveal someone else's requests")
        void employeeIdParameterIgnored() throws Exception {
            long rahulsOwn = submit(rahul(), PRIYA, "Chennai");
            long ashasOwn = submit(asha(), RAHUL, "Pune");

            List<String> visible = ids(mockMvc.perform(get("/api/travel-requests")
                    .param("scope", "MINE").param("employeeId", String.valueOf(RAHUL)).with(asha())));
            assertThat(visible).containsExactly(String.valueOf(ashasOwn)).doesNotContain(String.valueOf(rahulsOwn));
        }

        @Test
        @DisplayName("scopes are role-gated: employees cannot list approvals or everything")
        void scopesAreRoleGated() throws Exception {
            mockMvc.perform(get("/api/travel-requests").param("scope", "APPROVALS").with(asha()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/travel-requests").param("scope", "ALL").with(rahul()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/travel-requests").param("scope", "EVERYTHING").with(asha()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Finance sees everything with ALL, but only routed requests in APPROVALS")
        void financeScopes() throws Exception {
            long routedToFinance = submit(user(PRIYA, "EMPLOYEE", "MANAGER"), FINANCE, "Mumbai");
            long routedToRahul = submit(asha(), RAHUL, "Pune");

            RequestPostProcessor finance = user(FINANCE, "FINANCE");
            assertThat(ids(mockMvc.perform(get("/api/travel-requests").param("scope", "APPROVALS").with(finance))))
                    .containsExactly(String.valueOf(routedToFinance));
            assertThat(ids(mockMvc.perform(get("/api/travel-requests").param("scope", "ALL").with(finance))))
                    .contains(String.valueOf(routedToFinance), String.valueOf(routedToRahul));
        }

        @Test
        @DisplayName("empty results, status filtering and pagination are preserved")
        void emptyFilterAndPagination() throws Exception {
            assertThat(ids(mockMvc.perform(get("/api/travel-requests").param("scope", "MINE")
                    .with(user(OTHER_EMPLOYEE, "EMPLOYEE"))))).isEmpty();

            submit(asha(), RAHUL, "Pune");
            long cancelled = submit(asha(), RAHUL, "Goa");
            cancel(cancelled, asha()).andExpect(status().isOk());

            assertThat(ids(mockMvc.perform(get("/api/travel-requests").param("scope", "MINE")
                    .param("status", "CANCELLED").with(asha())))).containsExactly(String.valueOf(cancelled));

            JsonNode firstPage = objectMapper.readTree(mockMvc.perform(get("/api/travel-requests")
                            .param("scope", "MINE").param("size", "1").with(asha()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(firstPage.path("content")).hasSize(1);
            assertThat(firstPage.path("totalElements").asInt()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("#6 document download")
    class Download {

        private long uploadDocument(long requestId) throws Exception {
            String response = mockMvc.perform(multipart("/api/travel-requests/" + requestId + "/documents")
                            .file(new MockMultipartFile("file", "itinerary.pdf", "application/pdf", PDF))
                            .param("fileName", "itinerary.pdf")
                            .with(asha()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return Long.parseLong(objectMapper.readTree(response).get("id").asText());
        }

        @Test
        @DisplayName("owner, assigned approver, Finance and Admin can download the document")
        void authorizedDownloads() throws Exception {
            long id = submit(asha(), RAHUL, "Pune");
            long documentId = uploadDocument(id);
            String url = "/api/travel-requests/" + id + "/documents/" + documentId;

            for (RequestPostProcessor caller : List.of(asha(), rahul(), user(FINANCE, "FINANCE"), user(ADMIN, "ADMIN"))) {
                byte[] body = mockMvc.perform(get(url).with(caller))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "application/pdf"))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andReturn().getResponse().getContentAsByteArray();
                assertThat(body).isEqualTo(PDF);
            }
        }

        @Test
        @DisplayName("other employees and unassigned managers cannot download (403); anonymous 401")
        void unauthorizedDownloads() throws Exception {
            long id = submit(asha(), RAHUL, "Pune");
            long documentId = uploadDocument(id);
            String url = "/api/travel-requests/" + id + "/documents/" + documentId;

            mockMvc.perform(get(url).with(user(OTHER_EMPLOYEE, "EMPLOYEE"))).andExpect(status().isForbidden());
            mockMvc.perform(get(url).with(user(PRIYA, "MANAGER"))).andExpect(status().isForbidden());
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a document of another request is not served through this request's URL (404)")
        void crossRequestDocument() throws Exception {
            long ashaRequest = submit(asha(), RAHUL, "Pune");
            long otherRequest = submit(asha(), RAHUL, "Goa");
            long otherDocument = uploadDocument(otherRequest);

            mockMvc.perform(get("/api/travel-requests/" + ashaRequest + "/documents/" + otherDocument).with(asha()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("missing stored file or unknown document id returns 404")
        void missingFile() throws Exception {
            long id = submit(asha(), RAHUL, "Pune");
            long documentId = uploadDocument(id);
            try (Stream<Path> files = Files.list(storageRoot)) {
                for (Path file : files.toList()) {
                    Files.delete(file);
                }
            }

            mockMvc.perform(get("/api/travel-requests/" + id + "/documents/" + documentId).with(asha()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/travel-requests/" + id + "/documents/987654").with(asha()))
                    .andExpect(status().isNotFound());
        }
    }
}
