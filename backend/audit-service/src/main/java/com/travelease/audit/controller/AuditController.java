package com.travelease.audit.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.audit.dto.AuditRecordResponse;
import com.travelease.audit.service.AuditQueryService;
import com.travelease.common.dto.PagedResponse;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ValidationException;

/**
 * Audit record query REST controller (tasks.md I-06; api-contract.md §4.4, §7).
 *
 * <p>Thin read-only controller delegating all business logic to {@link AuditQueryService}.
 * Per Constitution §4: no business rules, no cross-service calls, no DB access.
 * All methods enforce `@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")` per api-contract.md §4.4.</p>
 *
 * <p>Supports multiple query filter combinations (api-contract.md §7):
 * <ul>
 * <li>By entity: {@code entityType} + {@code entityId} → {@link AuditQueryService#getAuditTrail(AuditEntityType, Long, Pageable)}</li>
 * <li>By actor: {@code actorId} → {@link AuditQueryService#getAuditsByActor(Long, Pageable)}</li>
 * <li>By date range: {@code from} + {@code to} → {@link AuditQueryService#getAuditsByDateRange(Instant, Instant, Pageable)}</li>
 * </ul>
 * Filters are optional; an unfiltered request returns the newest records.
 * If multiple are provided, precedence is: entity > actor > date range (FR-AUD-01, AC-14).</p>
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    /** Entity types every audit reader may see (#13). */
    private static final Set<AuditEntityType> WORKFLOW_TYPES = EnumSet.copyOf(Arrays.stream(AuditEntityType.values())
            .filter(AuditEntityType::isWorkflowRecord).toList());

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    /**
     * Queries audit records with optional filters and pagination (FR-AUD-01, AC-14).
     *
     * <p>Query parameters (api-contract.md §7):
     * <ul>
     * <li>{@code entityType}: optional, enum of TRAVEL_REQUEST or EXPENSE_CLAIM</li>
     * <li>{@code entityId}: optional, must be provided with {@code entityType}</li>
     * <li>{@code actorId}: optional, user ID whose actions to retrieve</li>
     * <li>{@code from}: optional, ISO-8601 date-time string, start of date range (inclusive)</li>
     * <li>{@code to}: optional, ISO-8601 date-time string, end of date range (inclusive)</li>
     * <li>{@code page}, {@code size}, {@code sort}: standard pagination parameters</li>
     * </ul>
     *
     * <p>Filters may be omitted to retrieve all audit records. If supplied,
     * entityType/entityId and from/to must be provided as pairs.</p>
     *
     * @param entityType optional entity type filter
     * @param entityId   optional entity ID filter (requires entityType)
     * @param actorId    optional actor ID filter
     * @param from       optional date range start (ISO-8601)
     * @param to         optional date range end (ISO-8601)
     * @param pageable   pagination and sort parameters
     * @return 200 OK with paginated audit records matching the filters
     * @throws ValidationException if a filter combination is incomplete
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<PagedResponse<AuditRecordResponse>> queryAuditRecords(
            @RequestParam(name = "entityType", required = false) Optional<AuditEntityType> entityType,
            @RequestParam(name = "entityId", required = false) Optional<Long> entityId,
            @RequestParam(name = "actorId", required = false) Optional<Long> actorId,
            @RequestParam(name = "from", required = false) Optional<Instant> from,
            @RequestParam(name = "to", required = false) Optional<Instant> to,
            Pageable pageable,
            Authentication authentication) {

        // #13: administrative records (accounts, roles, passwords, policies) are visible to ADMIN only;
        // FINANCE sees the travel/expense workflow trail.
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin && entityType.isPresent() && !entityType.get().isWorkflowRecord()) {
            throw new ForbiddenActionException("Administrative audit records are restricted to administrators");
        }

        // Filter precedence: entity (entityType + entityId) > actor > date range
        if (entityType.isPresent() && entityId.isPresent()) {
            // Entity-specific audit trail
            Page<AuditRecordResponse> response = auditQueryService.getAuditTrail(entityType.get(), entityId.get(),
                    pageable);
            return ResponseEntity.ok(PagedResponse.from(response));
        }

        if (actorId.isPresent()) {
            // Actor-specific audit records
            Page<AuditRecordResponse> response = admin
                    ? auditQueryService.getAuditsByActor(actorId.get(), pageable)
                    : auditQueryService.getAuditsByActor(actorId.get(), pageable, WORKFLOW_TYPES);
            return ResponseEntity.ok(PagedResponse.from(response));
        }

        if (from.isPresent() && to.isPresent()) {
            // Date range filter
            Page<AuditRecordResponse> response = admin
                    ? auditQueryService.getAuditsByDateRange(from.get(), to.get(), pageable)
                    : auditQueryService.getAuditsByDateRange(from.get(), to.get(), pageable, WORKFLOW_TYPES);
            return ResponseEntity.ok(PagedResponse.from(response));
        }

        if (from.isPresent() || to.isPresent()) {
            throw new ValidationException("Both from and to must be provided for a date-range filter");
        }

        if (entityType.isPresent() || entityId.isPresent()) {
            throw new ValidationException("Both entityType and entityId must be provided for an entity filter");
        }

        Page<AuditRecordResponse> response = admin
                ? auditQueryService.getAllAudits(pageable)
                : auditQueryService.getAllAudits(pageable, WORKFLOW_TYPES);
        return ResponseEntity.ok(PagedResponse.from(response));
    }
}
