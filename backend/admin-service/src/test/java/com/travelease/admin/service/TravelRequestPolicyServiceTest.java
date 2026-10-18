package com.travelease.admin.service;

import java.util.Optional;

import com.travelease.admin.dto.TravelRequestPolicyRequest;
import com.travelease.admin.dto.TravelRequestPolicyResponse;
import com.travelease.admin.entity.TravelRequestPolicy;
import com.travelease.admin.mapper.TravelRequestPolicyMapper;
import com.travelease.admin.repository.TravelRequestPolicyRepository;
import com.travelease.admin.service.impl.TravelRequestPolicyServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TravelRequestPolicyServiceImpl} (spec.md VAL-04, FR-TR-02,
 * FR-ADM-02, NFR-04), covering the singleton travel-request policy lookup and
 * upsert with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>get: active row exists via {@code findFirstByActiveTrueAndDeletedFalse} →
 *       mapped response (clarification Q7 default not triggered)</li>
 *   <li>get: no active row exists → safe MVP default
 *       ({@code documentsMandatory = false}, {@code id = null}, {@code active = true})
 *       and <b>no exception thrown</b> - travel-request submission is never
 *       blocked by a missing policy (VAL-04, clarification Q7)</li>
 *   <li>upsert: no active row exists → a new row is created with the request's
 *       {@code documentsMandatory} flag and {@code active = true}, exactly one
 *       save, response mapped from the saved row</li>
 *   <li>upsert: an active row exists → the <i>same</i> row instance is updated
 *       in place (same id, flag changed to the request's value), exactly one
 *       save (no duplicate row), response reflects the update (FR-ADM-02,
 *       singleton invariant, data-model.md §5.6)</li>
 * </ul>
 *
 * <p>The repository is mocked with Mockito; the mapper is a plain (non-Spring)
 * pure converter, so a real {@code @Spy} instance is injected to keep the
 * entity ↔ DTO mapping under test as well - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TravelRequestPolicyServiceImpl (spec.md VAL-04, FR-TR-02, FR-ADM-02)")
class TravelRequestPolicyServiceTest {

    @Mock
    private TravelRequestPolicyRepository travelRequestPolicyRepository;

    @Spy
    private TravelRequestPolicyMapper travelRequestPolicyMapper = new TravelRequestPolicyMapper();

    /** Audit publishing (#13); verified explicitly where the event content matters. */

    @Mock

    private com.travelease.admin.audit.AdminAuditEvents auditEvents;


    @InjectMocks
    private TravelRequestPolicyServiceImpl travelRequestPolicyService;

    private static TravelRequestPolicyRequest request(Boolean documentsMandatory) {
        return new TravelRequestPolicyRequest(documentsMandatory);
    }

    private static TravelRequestPolicy policy(Long id, Boolean documentsMandatory, boolean active) {
        TravelRequestPolicy policy = new TravelRequestPolicy();
        policy.setId(id);
        policy.setDocumentsMandatory(documentsMandatory);
        policy.setActive(active);
        return policy;
    }

    @Nested
    @DisplayName("getActivePolicy")
    class GetActivePolicy {

        @Test
        @DisplayName("returns the mapped active policy row when one exists")
        void returnsMappedActivePolicy() {
            TravelRequestPolicy active = policy(1L, true, true);
            when(travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse())
                    .thenReturn(Optional.of(active));

            TravelRequestPolicyResponse response = travelRequestPolicyService.getActivePolicy();

            assertThat(response).isEqualTo(new TravelRequestPolicyResponse(1L, true, true));
            verify(travelRequestPolicyMapper).toResponse(active);
        }

        @Test
        @DisplayName("returns the safe MVP default (documentsMandatory = false) when no active row exists, without throwing")
        void returnsSafeDefaultWhenNoActivePolicyExists() {
            when(travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse())
                    .thenReturn(Optional.empty());

            assertThatCode(travelRequestPolicyService::getActivePolicy)
                    .doesNotThrowAnyException();

            TravelRequestPolicyResponse response = travelRequestPolicyService.getActivePolicy();
            assertThat(response).isEqualTo(new TravelRequestPolicyResponse(null, false, true));
            verify(travelRequestPolicyMapper, never()).toResponse(any(TravelRequestPolicy.class));
        }
    }

    @Nested
    @DisplayName("upsertPolicy")
    class UpsertPolicy {

        @Test
        @DisplayName("creates a new active row with the request flag when no policy exists, saving exactly once")
        void createsActiveRowWhenNoneExists() {
            when(travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse())
                    .thenReturn(Optional.empty());
            when(travelRequestPolicyRepository.save(any(TravelRequestPolicy.class)))
                    .thenAnswer(invocation -> {
                        TravelRequestPolicy created = invocation.getArgument(0);
                        created.setId(1L);
                        return created;
                    });

            TravelRequestPolicyResponse response = travelRequestPolicyService.upsertPolicy(request(true));

            ArgumentCaptor<TravelRequestPolicy> captor = ArgumentCaptor.forClass(TravelRequestPolicy.class);
            verify(travelRequestPolicyRepository, times(1)).save(captor.capture());
            TravelRequestPolicy saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getDocumentsMandatory()).isTrue();
            assertThat(saved.isActive()).isTrue();

            assertThat(response).isEqualTo(new TravelRequestPolicyResponse(1L, true, true));
        }

        @Test
        @DisplayName("updates the existing active row in place when one exists, saving exactly once (no duplicate row)")
        void updatesExistingActiveRowInPlace() {
            TravelRequestPolicy existing = policy(1L, false, true);
            TravelRequestPolicyRequest upsert = request(true);
            when(travelRequestPolicyRepository.findFirstByActiveTrueAndDeletedFalse())
                    .thenReturn(Optional.of(existing));
            when(travelRequestPolicyRepository.save(any(TravelRequestPolicy.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            TravelRequestPolicyResponse response = travelRequestPolicyService.upsertPolicy(upsert);

            ArgumentCaptor<TravelRequestPolicy> captor = ArgumentCaptor.forClass(TravelRequestPolicy.class);
            verify(travelRequestPolicyRepository, times(1)).save(captor.capture());
            TravelRequestPolicy saved = captor.getValue();
            assertThat(saved).isSameAs(existing);
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getDocumentsMandatory()).isTrue();
            assertThat(saved.isActive()).isTrue();
            verify(travelRequestPolicyMapper).applyRequest(existing, upsert);

            assertThat(response).isEqualTo(new TravelRequestPolicyResponse(1L, true, true));
        }
    }
}