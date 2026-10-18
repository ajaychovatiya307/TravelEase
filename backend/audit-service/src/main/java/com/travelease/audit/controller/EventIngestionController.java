package com.travelease.audit.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.audit.service.AuditIngestionService;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.event.DomainEventEnvelope;

/**
 * HTTP domain-event ingestion REST controller (tasks.md N-02 fix; spec.md FR-AUD-01;
 * api-contract.md §2/§8).
 *
 * <p>Receives domain events forwarded over HTTP by the publisher services
 * (travel-request-service, expense-service) after their own state-changing
 * transactions commit (FR-AUD-01, AC-14). In-process Spring events never cross JVM
 * boundaries, so without this endpoint approvals and rejections performed in other
 * services leave no audit trail (N-02 verification failure). Events published within
 * this JVM still flow through the in-process
 * {@link com.travelease.audit.event.AuditEventListener}.</p>
 *
 * <p>Thin controller delegating all business logic to {@link AuditIngestionService};
 * per Constitution §4: no business rules, no cross-service calls, no DB access.
 * Ingestion is a non-blocking side effect per Constitution §9 — rejecting a malformed
 * envelope with 400 here cannot affect the publisher's already-committed transaction.</p>
 *
 * <p>Security (api-contract.md §2, NFR-10): deliberately no method-level
 * {@code @PreAuthorize}; the resource-server baseline in {@code SecurityConfig}
 * enforces {@code anyRequest().authenticated()}, which is the right level for this
 * internal service-to-service endpoint (any authenticated caller, any role).</p>
 */
@RestController
@RequestMapping("/api/events")
public class EventIngestionController {

    private final AuditIngestionService auditIngestionService;

    public EventIngestionController(AuditIngestionService auditIngestionService) {
        this.auditIngestionService = auditIngestionService;
    }

    /**
     * Ingests a domain event forwarded over HTTP by a publisher service after its
     * transaction commits (FR-AUD-01, AC-14).
     *
     * <p>Delegates to {@link AuditIngestionService#ingestEvent(DomainEventEnvelope)},
     * which persists an immutable audit record. The service rejects envelopes with an
     * unknown {@code entityType} or {@code action}, or a non-numeric {@code entityId},
     * with {@link IllegalArgumentException} — translated here to the shared structured
     * 400 error body (Constitution §8, api-contract.md §8).</p>
     *
     * @param event the domain event envelope (entityType, entityId, action, timestamp)
     * @return 202 Accepted when the event was accepted for ingestion, or 400 Bad
     *         Request with a {@code VALIDATION_ERROR} body when the envelope is invalid
     */
    @PostMapping
    public ResponseEntity<Object> ingestEvent(@RequestBody DomainEventEnvelope event) {
        try {
            auditIngestionService.ingestEvent(event);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException ex) {
            ErrorResponse response = new ErrorResponse(
                    Instant.now(),
                    HttpStatus.BAD_REQUEST.value(),
                    "VALIDATION_ERROR",
                    ex.getMessage(),
                    null);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }
}