package com.travelease.travelrequest.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.validation.DocumentConstraintValidator;
import com.travelease.travelrequest.config.SecurityConfig;
import com.travelease.travelrequest.dto.ApprovalDecisionRequest;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.dto.TravelRequestResponse;
import com.travelease.travelrequest.entity.DecisionType;
import com.travelease.travelrequest.entity.TravelRequestStatus;
import com.travelease.travelrequest.exception.GlobalExceptionHandler;
import com.travelease.travelrequest.service.TravelRequestDocumentService;
import com.travelease.travelrequest.service.TravelRequestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc API tests for the approval and document-upload travel-request
 * endpoints (tasks.md M-02; api-contract.md §4.1/§8; spec.md FR-APR-01,
 * FR-APR-02, FR-DOC-01; AC-03, AC-04, AC-11):
 *
 * <ul>
 *   <li>200 OK with the APPROVED request on a valid approve decision (AC-03)</li>
 *   <li>200 OK with the REJECTED request and the stored rejection reason on a
 *       valid reject decision carrying a reason (AC-04)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field {@code reason} when a
 *       {@code REJECTED} decision carries no reason (VAL-12; service tier)</li>
 *   <li>403 from {@code @PreAuthorize} when a non-approver (EMPLOYEE) calls the
 *       approval endpoint; the service is never reached</li>
 *   <li>403 {@code FORBIDDEN_ACTION} error-body mapping when the service tier raises
 *       {@code ForbiddenActionException}. The service is mocked here, so this only covers
 *       the HTTP mapping; the assigned-approver / self-approval rule itself is exercised
 *       end-to-end in {@code TravelRequestApprovalAuthorizationIT}</li>
 *   <li>400 {@code VALIDATION_ERROR} with field {@code decision} when the
 *       decision field is missing (Bean Validation tier); service untouched</li>
 *   <li>201 Created with the {@code DocumentSummary} on a valid PDF upload
 *       (AC-11)</li>
 *   <li>400 {@code VALIDATION_ERROR} with field {@code file} / {@code fileType}
 *       when the document service rejects an oversized or unsupported
 *       (non-PDF/JPG/PNG) file (VAL-11)</li>
 * </ul>
 *
 * <p>Setup notes (Spring Boot 3.2.5; mirrors the proven M-01 slice in
 * {@code TravelRequestControllerTest}): the approval and document endpoints
 * live on {@link TravelRequestController}, so the slice targets that
 * controller. The module has no top-level {@code @SpringBootApplication}
 * class yet, so the slice is bootstrapped from the nested
 * {@link TestApplication}; its {@link SecurityConfig} import keeps the real
 * JWT resource-server filter chain and {@code @EnableMethodSecurity}
 * (i.e. the {@code @PreAuthorize} gating on the controller) in effect exactly
 * as in production, and its {@code DevJwtDecoders} fallback provides an
 * offline HMAC {@code JwtDecoder}, so no auto-configuration exclusions are
 * needed. Authenticated requests use spring-security-test's {@code jwt()}
 * post-processor; the {@code ROLE_*} authority is set explicitly because the
 * mock token bypasses the {@code JwtRoleConverters} claim-to-authority
 * conversion that the real filter chain would apply. Paths address the
 * servlet mapping directly ({@code /api/travel-requests}), not the {@code /api}
 * context path of the running server. Only {@code @MockBean}-style mocks
 * (Boot 3.2.5; {@code @MockitoBean} exists only from 3.4) of
 * {@link TravelRequestService} and {@link TravelRequestDocumentService}; only
 * what each test needs is stubbed.</p>
 */
@WebMvcTest(controllers = TravelRequestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
@DisplayName("TravelRequestController approval & document-upload API (tasks.md M-02; api-contract.md §4.1/§8; spec.md FR-APR-01, FR-APR-02, FR-DOC-01; AC-03, AC-04, AC-11)")
class TravelRequestApprovalControllerTest {

    private static final long EMPLOYEE_ID = 1L;
    private static final long MANAGER_ID = 2L;
    private static final long REQUEST_ID = 100L;

    private static final String DESTINATION = "Mumbai";
    private static final String START_DATE_ISO = "2027-03-15";
    private static final String END_DATE_ISO = "2027-03-19";
    private static final String JUSTIFICATION = "Client on-site support for the quarterly business review";

    private static final String REJECTION_REASON = "Budget not approved";

    /** Exact VAL-12 message raised by {@code TravelRequestServiceImpl.decideApproval}. */
    private static final String VAL12_REASON_REQUIRED_MESSAGE = "Rejection reason is required and must be non-blank (VAL-12)";

    /** Exact message raised when the caller is not the assigned approver. */
    private static final String NOT_ASSIGNED_APPROVER_MESSAGE =
            "Only the assigned approver can decide travel request approvals";

    /** Exact VAL-11 size message raised by the shared document constraint validator. */
    private static final String FILE_SIZE_EXCEEDED_MESSAGE = "File size exceeds maximum allowed (5 MB)";

    /** Exact message raised by the document service's contentType normalization. */
    private static final String UNSUPPORTED_FILE_TYPE_MESSAGE = "Unsupported file type: text/plain";

    private static final String APPROVE_BODY = "{\"decision\": \"APPROVED\"}";
    private static final String REJECT_BODY = "{\"decision\": \"REJECTED\", \"reason\": \"Budget not approved\"}";
    private static final String REJECT_NO_REASON_BODY = "{\"decision\": \"REJECTED\"}";
    private static final String EMPTY_BODY = "{}";

    private static final String UPLOAD_FILE_NAME = "doc.pdf";
    private static final String DOCUMENT_ID = "1";
    private static final String STORAGE_REFERENCE = "travel-requests/100/doc.pdf";
    private static final byte[] UPLOAD_CONTENT = "travel-attachment".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TravelRequestService travelRequestService;

    @MockBean
    private TravelRequestDocumentService travelRequestDocumentService;

    /**
     * Bearer-style JWT for the requesting employee: subject "1" (the caller's
     * logical UserAccount ID per api-contract.md §2) plus the {@code roles}
     * claim and the matching {@code ROLE_EMPLOYEE} authority (see class
     * Javadoc).
     */
    private RequestPostProcessor employeeJwt() {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(EMPLOYEE_ID)).claim("roles", List.of("EMPLOYEE")))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    /**
     * Bearer-style JWT for the approving manager: subject "2" plus the
     * {@code roles} claim and the matching {@code ROLE_MANAGER} authority, so
     * {@code @PreAuthorize("hasAnyRole('MANAGER','FINANCE')")} is satisfied.
     */
    private RequestPostProcessor managerJwt() {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(MANAGER_ID)).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private TravelRequestResponse response(TravelRequestStatus status, String rejectionReason) {
        return new TravelRequestResponse(
                String.valueOf(REQUEST_ID),
                String.valueOf(EMPLOYEE_ID),
                DESTINATION,
                LocalDate.parse(START_DATE_ISO),
                LocalDate.parse(END_DATE_ISO),
                JUSTIFICATION,
                status,
                String.valueOf(MANAGER_ID),
                rejectionReason,
                null,
                List.of(),
                Instant.parse("2027-03-15T09:00:00Z"),
                Instant.parse("2027-03-15T10:30:00Z"));
    }

    @Nested
    @DisplayName("POST /api/travel-requests/{id}/approval (approve/reject)")
    class Approval {

        @Test
        @DisplayName("MANAGER approval without reason returns 200 with the APPROVED request (AC-03)")
        void approveReturns200WithApprovedRequest() throws Exception {
            when(travelRequestService.decideApproval(eq(REQUEST_ID), eq(MANAGER_ID), any(ApprovalDecisionRequest.class)))
                    .thenReturn(response(TravelRequestStatus.APPROVED, null));

            mockMvc.perform(post("/api/travel-requests/" + REQUEST_ID + "/approval")
                            .with(managerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(String.valueOf(REQUEST_ID)))
                    .andExpect(jsonPath("$.employeeId").value(String.valueOf(EMPLOYEE_ID)))
                    .andExpect(jsonPath("$.destination").value(DESTINATION))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.rejectionReason").isEmpty());

            // The approver id must come from the JWT subject, not from the body.
            ArgumentCaptor<ApprovalDecisionRequest> captor =
                    ArgumentCaptor.forClass(ApprovalDecisionRequest.class);
            verify(travelRequestService).decideApproval(eq(REQUEST_ID), eq(MANAGER_ID), captor.capture());
            ApprovalDecisionRequest sent = captor.getValue();
            assertThat(sent.decision()).isEqualTo(DecisionType.APPROVED);
            assertThat(sent.reason()).isNull();
        }

        @Test
        @DisplayName("MANAGER rejection with reason returns 200 with the REJECTED request and the stored reason (AC-04)")
        void rejectWithReasonReturns200WithRejectedRequest() throws Exception {
            when(travelRequestService.decideApproval(eq(REQUEST_ID), eq(MANAGER_ID), any(ApprovalDecisionRequest.class)))
                    .thenReturn(response(TravelRequestStatus.REJECTED, REJECTION_REASON));

            mockMvc.perform(post("/api/travel-requests/" + REQUEST_ID + "/approval")
                            .with(managerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REJECT_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(String.valueOf(REQUEST_ID)))
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value(REJECTION_REASON));

            ArgumentCaptor<ApprovalDecisionRequest> captor =
                    ArgumentCaptor.forClass(ApprovalDecisionRequest.class);
            verify(travelRequestService).decideApproval(eq(REQUEST_ID), eq(MANAGER_ID), captor.capture());
            ApprovalDecisionRequest sent = captor.getValue();
            assertThat(sent.decision()).isEqualTo(DecisionType.REJECTED);
            assertThat(sent.reason()).isEqualTo(REJECTION_REASON);
        }

        @Test
        @DisplayName("rejection without reason (VAL-12) returns 400 VALIDATION_ERROR for field reason (AC-04)")
        void rejectWithoutReasonReturns400WithReasonFieldError() throws Exception {
            // The DTO tier deliberately carries no declarative annotation on
            // reason (the J-02 custom constraint is not wired into the DTO
            // tier), so a REJECTED decision without a reason reaches the
            // service, which raises the exact VAL-12 ValidationException.
            when(travelRequestService.decideApproval(eq(REQUEST_ID), eq(MANAGER_ID), any(ApprovalDecisionRequest.class)))
                    .thenThrow(new ValidationException("reason", VAL12_REASON_REQUIRED_MESSAGE));

            mockMvc.perform(post("/api/travel-requests/" + REQUEST_ID + "/approval")
                            .with(managerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REJECT_NO_REASON_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        }

        @Test
        @DisplayName("EMPLOYEE calling the approval endpoint is rejected with 403 by @PreAuthorize, service never called (AC-03)")
        void employeeRoleReturns403() throws Exception {
            mockMvc.perform(post("/api/travel-requests/" + REQUEST_ID + "/approval")
                            .with(employeeJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_BODY))
                    // Spring's default 403 response for a method-security denial
                    // is asserted by status only (body may be empty).
                    .andExpect(status().isForbidden());

            verifyNoInteractions(travelRequestService);
        }

        @Test
        @DisplayName("MANAGER who is not the assigned approver gets 403 FORBIDDEN_ACTION from the service tier (AC-03)")
        void notAssignedApproverReturns403ForbiddenAction() throws Exception {
            when(travelRequestService.decideApproval(eq(REQUEST_ID), eq(MANAGER_ID), any(ApprovalDecisionRequest.class)))
                    .thenThrow(new ForbiddenActionException(NOT_ASSIGNED_APPROVER_MESSAGE));

            mockMvc.perform(post("/api/travel-requests/" + REQUEST_ID + "/approval")
                            .with(managerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPROVE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN_ACTION"))
                    .andExpect(jsonPath("$.message").value(NOT_ASSIGNED_APPROVER_MESSAGE));
        }

        @Test
        @DisplayName("missing decision field returns 400 VALIDATION_ERROR for field decision, service untouched (AC-03)")
        void missingDecisionReturns400WithDecisionFieldError() throws Exception {
            mockMvc.perform(post("/api/travel-requests/" + REQUEST_ID + "/approval")
                            .with(managerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EMPTY_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("decision"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("decision is required"));

            verifyNoInteractions(travelRequestService);
        }
    }

    @Nested
    @DisplayName("POST /api/travel-requests/{id}/documents (multipart upload)")
    class DocumentUpload {

        @Test
        @DisplayName("valid PDF upload returns 201 with the document summary and the part metadata (AC-11)")
        void validUploadReturns201WithSummary() throws Exception {
            DocumentSummary summary = new DocumentSummary(
                    DOCUMENT_ID, UPLOAD_FILE_NAME, DocumentFileType.PDF,
                    UPLOAD_CONTENT.length, STORAGE_REFERENCE, UploadStatus.CONFIRMED);
            when(travelRequestDocumentService.uploadDocument(
                    eq(REQUEST_ID), eq(EMPLOYEE_ID), eq(UPLOAD_FILE_NAME),
                    eq((long) UPLOAD_CONTENT.length), eq("application/pdf"), any(byte[].class)))
                    .thenReturn(summary);

            mockMvc.perform(multipart("/api/travel-requests/" + REQUEST_ID + "/documents")
                            .file(new MockMultipartFile("file", UPLOAD_FILE_NAME, "application/pdf", UPLOAD_CONTENT))
                            .param("fileName", UPLOAD_FILE_NAME)
                            .with(employeeJwt()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(DOCUMENT_ID))
                    .andExpect(jsonPath("$.fileName").value(UPLOAD_FILE_NAME))
                    .andExpect(jsonPath("$.fileType").value("PDF"))
                    .andExpect(jsonPath("$.fileSizeBytes").value(UPLOAD_CONTENT.length))
                    .andExpect(jsonPath("$.storageReference").value(STORAGE_REFERENCE))
                    .andExpect(jsonPath("$.uploadStatus").value("CONFIRMED"));

            // The controller must hand the service the part's bytes, size and MIME type.
            ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(travelRequestDocumentService).uploadDocument(
                    eq(REQUEST_ID), eq(EMPLOYEE_ID), eq(UPLOAD_FILE_NAME),
                    eq((long) UPLOAD_CONTENT.length), eq("application/pdf"), contentCaptor.capture());
            assertThat(contentCaptor.getValue()).isEqualTo(UPLOAD_CONTENT);
        }

        @Test
        @DisplayName("file above the 5 MB limit returns 400 VALIDATION_ERROR for field file (VAL-11, AC-11)")
        void oversizedUploadReturns400WithFileFieldError() throws Exception {
            byte[] oversized = new byte[(int) (DocumentConstraintValidator.MAX_FILE_SIZE_BYTES + 1)];
            Arrays.fill(oversized, (byte) 1);
            when(travelRequestDocumentService.uploadDocument(
                    eq(REQUEST_ID), eq(EMPLOYEE_ID), eq(UPLOAD_FILE_NAME),
                    eq((long) oversized.length), eq("application/pdf"), any(byte[].class)))
                    .thenThrow(new ValidationException("file", FILE_SIZE_EXCEEDED_MESSAGE));

            mockMvc.perform(multipart("/api/travel-requests/" + REQUEST_ID + "/documents")
                            .file(new MockMultipartFile("file", UPLOAD_FILE_NAME, "application/pdf", oversized))
                            .param("fileName", UPLOAD_FILE_NAME)
                            .with(employeeJwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(FILE_SIZE_EXCEEDED_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(FILE_SIZE_EXCEEDED_MESSAGE));
        }

        @Test
        @DisplayName("unsupported file type (text/plain) returns 400 VALIDATION_ERROR for field fileType (VAL-11, AC-11)")
        void unsupportedTypeUploadReturns400WithFileTypeFieldError() throws Exception {
            byte[] textContent = "plain text notes".getBytes(StandardCharsets.UTF_8);
            when(travelRequestDocumentService.uploadDocument(
                    eq(REQUEST_ID), eq(EMPLOYEE_ID), eq("notes.txt"),
                    eq((long) textContent.length), eq("text/plain"), any(byte[].class)))
                    .thenThrow(new ValidationException("fileType", UNSUPPORTED_FILE_TYPE_MESSAGE));

            mockMvc.perform(multipart("/api/travel-requests/" + REQUEST_ID + "/documents")
                            .file(new MockMultipartFile("file", "notes.txt", "text/plain", textContent))
                            .param("fileName", "notes.txt")
                            .with(employeeJwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(UNSUPPORTED_FILE_TYPE_MESSAGE))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("fileType"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(UNSUPPORTED_FILE_TYPE_MESSAGE));
        }
    }
}