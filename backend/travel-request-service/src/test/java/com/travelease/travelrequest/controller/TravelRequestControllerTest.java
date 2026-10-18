package com.travelease.travelrequest.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.travelrequest.config.SecurityConfig;
import com.travelease.travelrequest.dto.TravelRequestCreateRequest;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.dto.TravelRequestUpdateRequest;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.exception.GlobalExceptionHandler;
import com.travelease.travelrequest.service.TravelRequestDocumentService;
import com.travelease.travelrequest.service.TravelRequestListScope;
import com.travelease.travelrequest.service.TravelRequestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the public travel-request endpoints (tasks.md M-01;
 * api-contract.md §4.1/§8; spec.md FR-TR-01, FR-TR-03, FR-TR-06, FR-TR-07;
 * AC-01, AC-02, AC-13):
 *
 * <ul>
 *   <li>201 Created with the created request on a valid submission (AC-01)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field errors on missing/blank structural
 *       fields (Bean Validation tier) (AC-02)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field {@code startDate} when the service
 *       tier rejects a past start date (VAL-02 business part) (AC-02)</li>
 *   <li>structured error on editing an {@code APPROVED} request (VAL-13, AC-13) -
 *       note: tasks.md M-01 mentions "409" here, but the handler maps
 *       {@link ValidationException} to 400 (see {@code Editing}); the 409
 *       {@code DUPLICATE_RESOURCE} mapping is reserved for hard duplicates</li>
 *   <li>200 with the request body / 404 {@code RESOURCE_NOT_FOUND} on GET /{id}</li>
 *   <li>401 without a bearer token, rejected by the security filter before the
 *       service is reached</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5): the module has no top-level
 * {@code @SpringBootApplication} class yet, so the slice is bootstrapped from the
 * nested {@link TestApplication}. The app's {@link SecurityConfig} is imported so
 * the real JWT resource-server filter chain and {@code @EnableMethodSecurity}
 * (i.e. the {@code @PreAuthorize} gating on the controller) are in effect exactly
 * as in production; its {@code DevJwtDecoders} fallback provides an offline HMAC
 * {@code JwtDecoder}, so no auto-configuration exclusions are needed.
 * Authenticated requests use spring-security-test's {@code jwt()} post-processor;
 * the {@code ROLE_EMPLOYEE} authority is set explicitly because the mock token
 * bypasses the {@code JwtRoleConverters} claim-to-authority conversion that the
 * real filter chain would apply. Paths address the servlet mapping directly
 * ({@code /api/travel-requests}), not the {@code /api} context path of the
 * running server. Only {@code @MockBean}-style mocks (Boot 3.2.5;
 * {@code @MockitoBean} exists only from 3.4) of {@link TravelRequestService} and
 * {@link TravelRequestDocumentService}; only what each test needs is stubbed.</p>
 */
@WebMvcTest(controllers = TravelRequestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("TravelRequestController API (tasks.md M-01; api-contract.md §4.1/§8; spec.md FR-TR-01, FR-TR-03, FR-TR-06, FR-TR-07; AC-01, AC-02, AC-13)")
class TravelRequestControllerTest {

    private static final long EMPLOYEE_ID = 1L;
    private static final long REQUEST_ID = 100L;
    private static final long EDITED_REQUEST_ID = 42L;
    private static final long UNKNOWN_REQUEST_ID = 404L;

    private static final String DESTINATION = "Mumbai";
    private static final String START_DATE_ISO = "2027-03-15";
    private static final String END_DATE_ISO = "2027-03-19";
    private static final String JUSTIFICATION = "Client on-site support for the quarterly business review";

    private static final String PAST_START_DATE_MESSAGE = "Travel start date cannot be in the past (VAL-02)";
    private static final String NOT_EDITABLE_MESSAGE =
            "Travel request can only be edited while PENDING or resubmitted while REJECTED. Current status: APPROVED";

    private static final String VALID_CREATE_BODY = """
            {
                "destination": "%s",
                "startDate": "%s",
                "endDate": "%s",
                "justification": "%s",
                "documentIds": []
            }
            """.formatted(DESTINATION, START_DATE_ISO, END_DATE_ISO, JUSTIFICATION);

    /** destination omitted (VAL-01) and justification blank (VAL-03) - structural tier. */
    private static final String INVALID_CREATE_BODY = """
            {
                "startDate": "%s",
                "endDate": "%s",
                "justification": ""
            }
            """.formatted(START_DATE_ISO, END_DATE_ISO);

    private static final String VALID_UPDATE_BODY = """
            {
                "destination": "Pune",
                "startDate": "2027-03-20",
                "endDate": "2027-03-22",
                "justification": "%s",
                "documentIds": []
            }
            """.formatted(JUSTIFICATION);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TravelRequestService travelRequestService;

    @MockBean
    private TravelRequestDocumentService travelRequestDocumentService;

    /**
     * Bearer-style JWT for the authenticated employee: subject "1" (the caller's
     * logical UserAccount ID per api-contract.md §2) plus the {@code roles} claim
     * and the matching {@code ROLE_EMPLOYEE} authority (see class Javadoc).
     */
    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(EMPLOYEE_ID)).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private TravelRequestResponse createdResponse() {
        return new TravelRequestResponse(
                String.valueOf(REQUEST_ID),
                String.valueOf(EMPLOYEE_ID),
                DESTINATION,
                LocalDate.parse(START_DATE_ISO),
                LocalDate.parse(END_DATE_ISO),
                JUSTIFICATION,
                TravelRequestStatus.PENDING,
                "7",
                null,
                null,
                List.of(),
                Instant.parse("2027-03-15T09:00:00Z"),
                Instant.parse("2027-03-15T09:00:01Z"));
    }

    @Nested
    @DisplayName("POST /api/travel-requests (submit)")
    class Submission {

        @Test
        @DisplayName("valid submission returns 201 with the created request and the JWT subject as employeeId (AC-01)")
        void validSubmissionReturns201WithCreatedRequest() throws Exception {
            when(travelRequestService.submitTravelRequest(eq(EMPLOYEE_ID), any(TravelRequestCreateRequest.class)))
                    .thenReturn(createdResponse());

            mockMvc.perform(post("/api/travel-requests")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(String.valueOf(REQUEST_ID)))
                    .andExpect(jsonPath("$.employeeId").value(String.valueOf(EMPLOYEE_ID)))
                    .andExpect(jsonPath("$.destination").value(DESTINATION))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.approverId").value("7"));

            // The caller id must come from the JWT subject, not from the body.
            ArgumentCaptor<TravelRequestCreateRequest> captor =
                    ArgumentCaptor.forClass(TravelRequestCreateRequest.class);
            verify(travelRequestService).submitTravelRequest(eq(EMPLOYEE_ID), captor.capture());
            TravelRequestCreateRequest sent = captor.getValue();
            assertThat(sent.destination()).isEqualTo(DESTINATION);
            assertThat(sent.startDate()).isEqualTo(LocalDate.parse(START_DATE_ISO));
            assertThat(sent.endDate()).isEqualTo(LocalDate.parse(END_DATE_ISO));
            assertThat(sent.justification()).isEqualTo(JUSTIFICATION);
        }

        @Test
        @DisplayName("missing destination / blank justification returns 400 VALIDATION_ERROR with field errors, service untouched (AC-02)")
        void missingFieldsReturn400WithFieldErrors() throws Exception {
            mockMvc.perform(post("/api/travel-requests")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.length()", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'destination')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'justification')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[0].message").exists());

            verifyNoInteractions(travelRequestService);
        }

        @Test
        @DisplayName("past start date rejected at the service tier returns 400 VALIDATION_ERROR for field startDate (AC-02)")
        void pastStartDateReturns400WithStartDateFieldError() throws Exception {
            when(travelRequestService.submitTravelRequest(eq(EMPLOYEE_ID), any(TravelRequestCreateRequest.class)))
                    .thenThrow(new ValidationException("startDate", PAST_START_DATE_MESSAGE));

            mockMvc.perform(post("/api/travel-requests")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(PAST_START_DATE_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("startDate"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(PAST_START_DATE_MESSAGE));
        }
    }

    @Nested
    @DisplayName("PUT /api/travel-requests/{id} (edit)")
    class Editing {

        @Test
        @DisplayName("editing an APPROVED request (VAL-13) returns the structured 400 VALIDATION_ERROR for field status (AC-13)")
        void editingApprovedRequestReturnsValidationError() throws Exception {
            // DEVIATION from tasks.md M-01, which mentions "409 on editing an Approved
            // request": the VAL-13 rule raises a ValidationException (field "status")
            // and GlobalExceptionHandler maps ValidationException to 400
            // VALIDATION_ERROR per api-contract.md §8 (409 is DUPLICATE_RESOURCE
            // only). We assert the status code and error code the handler actually
            // produces rather than inventing a 409 it never returns.
            when(travelRequestService.editTravelRequest(eq(EDITED_REQUEST_ID), eq(EMPLOYEE_ID),
                    any(TravelRequestUpdateRequest.class)))
                    .thenThrow(new ValidationException("status", NOT_EDITABLE_MESSAGE));

            mockMvc.perform(put("/api/travel-requests/" + EDITED_REQUEST_ID)
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(NOT_EDITABLE_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(NOT_EDITABLE_MESSAGE));
        }
    }

    @Nested
    @DisplayName("GET /api/travel-requests/{id}")
    class Fetching {

        @Test
        @DisplayName("existing request returns 200 with the request body")
        void existingRequestReturns200() throws Exception {
            when(travelRequestService.getTravelRequest(eq(REQUEST_ID), eq(EMPLOYEE_ID), anySet()))
                    .thenReturn(createdResponse());

            mockMvc.perform(get("/api/travel-requests/" + REQUEST_ID).with(employeeJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(String.valueOf(REQUEST_ID)))
                    .andExpect(jsonPath("$.employeeId").value(String.valueOf(EMPLOYEE_ID)))
                    .andExpect(jsonPath("$.destination").value(DESTINATION))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("list request uses JWT authorities when roles claim is absent")
        void listRequestUsesAuthoritiesWhenRolesClaimIsAbsent() throws Exception {
            when(travelRequestService.listTravelRequests(
                    eq(EMPLOYEE_ID), anySet(), eq((TravelRequestListScope) null), eq(null), any()))
                    .thenReturn(new com.travelease.common.dto.PagedResponse<>(
                            List.of(), 0, 20, 0, 0));

            mockMvc.perform(get("/api/travel-requests")
                            .with(jwt().jwt(builder -> builder.subject(String.valueOf(EMPLOYEE_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                            .param("size", "20"))
                    .andExpect(status().isOk());

            verify(travelRequestService).listTravelRequests(
                    eq(EMPLOYEE_ID), anySet(), eq((TravelRequestListScope) null), eq(null), any());
        }

        @Test
        @DisplayName("unknown id returns 404 RESOURCE_NOT_FOUND")
        void unknownIdReturns404() throws Exception {
            when(travelRequestService.getTravelRequest(eq(UNKNOWN_REQUEST_ID), eq(EMPLOYEE_ID), anySet()))
                    .thenThrow(new ResourceNotFoundException("TravelRequest", UNKNOWN_REQUEST_ID));

            mockMvc.perform(get("/api/travel-requests/" + UNKNOWN_REQUEST_ID).with(employeeJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            "TravelRequest with id " + UNKNOWN_REQUEST_ID + " not found"));
        }

        @Test
        @DisplayName("a concurrent (stale @Version) update maps to 409 CONFLICT, not 500 (HTTP mapping only)")
        void staleUpdateReturns409() throws Exception {
            when(travelRequestService.cancelTravelRequest(eq(UNKNOWN_REQUEST_ID), eq(EMPLOYEE_ID)))
                    .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                            com.travelease.travelrequest.entity.TravelRequest.class, UNKNOWN_REQUEST_ID));

            mockMvc.perform(post("/api/travel-requests/" + UNKNOWN_REQUEST_ID + "/cancel").with(employeeJwt()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }
    }

    @Nested
    @DisplayName("Security")
    class TokenlessAccess {

        @Test
        @DisplayName("POST without a bearer token is rejected with 401 by the security filter, service never called")
        void postWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/travel-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(travelRequestService);
        }
    }
}