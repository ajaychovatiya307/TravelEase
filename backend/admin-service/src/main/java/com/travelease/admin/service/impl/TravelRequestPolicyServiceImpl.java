package com.travelease.admin.service.impl;

import com.travelease.admin.audit.AdminAuditEvents;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.admin.dto.TravelRequestPolicyRequest;
import com.travelease.admin.dto.TravelRequestPolicyResponse;
import com.travelease.admin.entity.TravelRequestPolicy;
import com.travelease.admin.mapper.TravelRequestPolicyMapper;
import com.travelease.admin.repository.TravelRequestPolicyRepository;
import com.travelease.admin.service.TravelRequestPolicyService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Singleton travel-request policy business logic (spec.md VAL-04, FR-TR-02,
 * FR-ADM-02, NFR-04).
 *
 * <p>Enforces the singleton invariant: at most one non-deleted active row exists
 * at any time (data-model.md §5.6). The mandate lookup deliberately degrades to
 * the safe MVP default - {@code documentsMandatory = false} (non-mandated;
 * clarification Q7) - instead of throwing when no active row exists, so
 * travel-request submission is never blocked by a missing policy. Inactive or
 * soft-deleted rows are ignored by the lookup but retained for history
 * (NFR-04); business rules are kept out of the DTO/repository layers
 * (Constitution §5/§6/§7).</p>
 */
@Service
public class TravelRequestPolicyServiceImpl implements TravelRequestPolicyService {

    private final TravelRequestPolicyRepository travelRequestPolicyRepository;
    private final TravelRequestPolicyMapper travelRequestPolicyMapper;

    private final AdminAuditEvents auditEvents;

    public TravelRequestPolicyServiceImpl(TravelRequestPolicyRepository travelRequestPolicyRepository,
            TravelRequestPolicyMapper travelRequestPolicyMapper, AdminAuditEvents auditEvents) {
        this.travelRequestPolicyRepository = travelRequestPolicyRepository;
        this.travelRequestPolicyMapper = travelRequestPolicyMapper;
        this.auditEvents = auditEvents;
    }

    @Override
    @Transactional(readOnly = true)
    public TravelRequestPolicyResponse getActivePolicy() {
        return travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse()
                .map(travelRequestPolicyMapper::toResponse)
                // Safe MVP default (clarification Q7): non-mandated when no active row exists.
                .orElseGet(() -> new TravelRequestPolicyResponse(null, false, true));
    }

    @Override
    @Transactional
    public TravelRequestPolicyResponse upsertPolicy(TravelRequestPolicyRequest request) {
        // Singleton invariant: update the single active row in place, or create it.
        TravelRequestPolicy policy = travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse()
                .orElseGet(TravelRequestPolicy::new);
        boolean created = policy.getId() == null;
        String previousMandate = String.valueOf(policy.getDocumentsMandatory());
        travelRequestPolicyMapper.applyRequest(policy, request);
        TravelRequestPolicy saved = travelRequestPolicyRepository.save(policy);
        auditEvents.publish(AuditEntityType.TRAVEL_REQUEST_POLICY, saved.getId(),
                created ? AuditActionType.CREATED : AuditActionType.UPDATED, java.util.Map.of(
                        "fromDocumentsMandatory", previousMandate,
                        "toDocumentsMandatory", String.valueOf(saved.getDocumentsMandatory())));
        return travelRequestPolicyMapper.toResponse(saved);
    }
}