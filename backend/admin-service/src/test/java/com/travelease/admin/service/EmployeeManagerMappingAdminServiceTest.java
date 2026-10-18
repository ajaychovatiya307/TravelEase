package com.travelease.admin.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.travelease.admin.dto.EmployeeManagerMappingRequest;
import com.travelease.admin.dto.EmployeeManagerMappingResponse;
import com.travelease.admin.entity.EmployeeManagerMapping;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.mapper.EmployeeManagerMappingMapper;
import com.travelease.admin.repository.EmployeeManagerMappingRepository;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.service.impl.EmployeeManagerMappingAdminServiceImpl;
import com.travelease.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmployeeManagerMappingAdminServiceImpl} (tasks.md L-07; spec.md FR-ADM-01),
 * covering admin employee→manager mapping create-or-update/list with positive and negative cases
 * per Constitution §10:
 *
 * <ul>
 *   <li>create: both employee and manager exist → new mapping saved with
 *       {@code employeeId}/{@code managerId}/{@code effectiveFrom}, response mapped</li>
 *   <li>create: employee (or manager) has no user account → {@link ResourceNotFoundException}
 *       from the private {@code requireExistingUser} check, nothing saved</li>
 *   <li>uniqueness (data-model.md §5.3): an employee with an existing active mapping is
 *       updated in place rather than duplicated (api-contract.md §4.3 upsert semantics)</li>
 *   <li>nullable {@code managerId} (clarification Q3, spec.md EC-09): top-of-hierarchy mappings
 *       are accepted without a manager existence check</li>
 *   <li>list: repository page of non-deleted mappings mapped to a response page</li>
 * </ul>
 *
 * <p>The repositories are mocked with Mockito; the mapper is a plain (non-Spring) pure converter,
 * so a real {@code @Spy} instance is injected to keep the entity ↔ DTO mapping under test as
 * well - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeManagerMappingAdminServiceImpl (tasks.md L-07; spec.md FR-ADM-01)")
class EmployeeManagerMappingAdminServiceTest {

    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2026, 2, 1);

    @Mock
    private EmployeeManagerMappingRepository mappingRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Spy
    private EmployeeManagerMappingMapper mappingMapper = new EmployeeManagerMappingMapper();

    /** Audit publishing (#13); verified explicitly where the event content matters. */

    @Mock

    private com.travelease.admin.audit.AdminAuditEvents auditEvents;


    @InjectMocks
    private EmployeeManagerMappingAdminServiceImpl mappingAdminService;

    private static UserAccount user(long id) {
        UserAccount account = new UserAccount();
        account.setId(id);
        return account;
    }

    private static EmployeeManagerMapping mapping(Long id, Long employeeId, Long managerId, LocalDate effectiveFrom) {
        EmployeeManagerMapping mapping = new EmployeeManagerMapping();
        mapping.setId(id);
        mapping.setEmployeeId(employeeId);
        mapping.setManagerId(managerId);
        mapping.setEffectiveFrom(effectiveFrom);
        return mapping;
    }

    @Nested
    @DisplayName("createOrUpdateMapping (create)")
    class CreateMapping {

        @Test
        @DisplayName("saves a new mapping for an existing employee and maps the response")
        void savesNewMapping() {
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user(1L)));
            when(userAccountRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(user(2L)));
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
            when(mappingRepository.save(any(EmployeeManagerMapping.class)))
                    .thenAnswer(invocation -> {
                        EmployeeManagerMapping created = invocation.getArgument(0);
                        created.setId(100L);
                        return created;
                    });

            EmployeeManagerMappingResponse response = mappingAdminService.createOrUpdateMapping(
                    new EmployeeManagerMappingRequest(1L, 2L, EFFECTIVE_FROM));

            ArgumentCaptor<EmployeeManagerMapping> captor = ArgumentCaptor.forClass(EmployeeManagerMapping.class);
            verify(mappingRepository).save(captor.capture());
            EmployeeManagerMapping saved = captor.getValue();
            assertThat(saved.getEmployeeId()).isEqualTo(1L);
            assertThat(saved.getManagerId()).isEqualTo(2L);
            assertThat(saved.getEffectiveFrom()).isEqualTo(EFFECTIVE_FROM);
            // manager existence was verified via requireExistingUser
            verify(userAccountRepository).findByIdAndDeletedFalse(2L);

            assertThat(response).isEqualTo(new EmployeeManagerMappingResponse(100L, 1L, 2L, EFFECTIVE_FROM));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the employee has no user account and saves nothing")
        void throwsWhenEmployeeDoesNotExist() {
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

            EmployeeManagerMappingRequest mappingRequest =
                    new EmployeeManagerMappingRequest(1L, 2L, EFFECTIVE_FROM);

            assertThatThrownBy(() -> mappingAdminService.createOrUpdateMapping(mappingRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            // employee check fails first - manager is never checked, nothing is saved
            verify(userAccountRepository, never()).findByIdAndDeletedFalse(2L);
            verifyNoInteractions(mappingRepository);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the manager has no user account and saves nothing")
        void throwsWhenManagerDoesNotExist() {
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user(1L)));
            when(userAccountRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.empty());

            EmployeeManagerMappingRequest mappingRequest =
                    new EmployeeManagerMappingRequest(1L, 2L, EFFECTIVE_FROM);

            assertThatThrownBy(() -> mappingAdminService.createOrUpdateMapping(mappingRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(mappingRepository);
        }
    }

    @Nested
    @DisplayName("createOrUpdateMapping (one active mapping per employee, data-model.md §5.3)")
    class UpsertMapping {

        @Test
        @DisplayName("updates the existing active mapping in place instead of inserting a duplicate row")
        void updatesExistingMappingInPlace() {
            EmployeeManagerMapping existing = mapping(10L, 1L, 3L, LocalDate.of(2025, 1, 1));
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user(1L)));
            when(userAccountRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(user(2L)));
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(mappingRepository.save(any(EmployeeManagerMapping.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            EmployeeManagerMappingResponse response = mappingAdminService.createOrUpdateMapping(
                    new EmployeeManagerMappingRequest(1L, 2L, EFFECTIVE_FROM));

            ArgumentCaptor<EmployeeManagerMapping> captor = ArgumentCaptor.forClass(EmployeeManagerMapping.class);
            verify(mappingRepository).save(captor.capture());
            EmployeeManagerMapping saved = captor.getValue();
            // the same row is updated, not a new insert
            assertThat(saved).isSameAs(existing);
            assertThat(saved.getId()).isEqualTo(10L);
            assertThat(saved.getEmployeeId()).isEqualTo(1L);
            assertThat(saved.getManagerId()).isEqualTo(2L);
            assertThat(saved.getEffectiveFrom()).isEqualTo(EFFECTIVE_FROM);
            // the "new entity" path was never taken
            verify(mappingMapper, never()).toEntity(any(EmployeeManagerMappingRequest.class));

            assertThat(response).isEqualTo(new EmployeeManagerMappingResponse(10L, 1L, 2L, EFFECTIVE_FROM));
        }
    }

    @Nested
    @DisplayName("createOrUpdateMapping (nullable managerId, clarification Q3, spec.md EC-09)")
    class NullableManagerId {

        @Test
        @DisplayName("accepts a null managerId (top-of-hierarchy) without checking manager existence")
        void acceptsNullManagerId() {
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user(1L)));
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
            when(mappingRepository.save(any(EmployeeManagerMapping.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            EmployeeManagerMappingResponse response = mappingAdminService.createOrUpdateMapping(
                    new EmployeeManagerMappingRequest(1L, null, EFFECTIVE_FROM));

            ArgumentCaptor<EmployeeManagerMapping> captor = ArgumentCaptor.forClass(EmployeeManagerMapping.class);
            verify(mappingRepository).save(captor.capture());
            assertThat(captor.getValue().getEmployeeId()).isEqualTo(1L);
            assertThat(captor.getValue().getManagerId()).isNull();
            assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(EFFECTIVE_FROM);
            // only the employee existence check ran; anyLong() never matches null
            verify(userAccountRepository, times(1)).findByIdAndDeletedFalse(anyLong());

            assertThat(response).isEqualTo(new EmployeeManagerMappingResponse(null, 1L, null, EFFECTIVE_FROM));
        }
    }

    @Nested
    @DisplayName("listMappings")
    class ListMappings {

        @Test
        @DisplayName("maps the repository page of non-deleted mappings to a response page")
        void mapsPage() {
            Pageable pageable = Pageable.ofSize(20);
            when(mappingRepository.findByDeletedFalse(pageable))
                    .thenReturn(new PageImpl<>(List.of(
                            mapping(10L, 1L, 2L, EFFECTIVE_FROM),
                            mapping(11L, 3L, null, LocalDate.of(2025, 6, 30))), pageable, 2));

            Page<EmployeeManagerMappingResponse> page = mappingAdminService.listMappings(pageable);

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).containsExactly(
                    new EmployeeManagerMappingResponse(10L, 1L, 2L, EFFECTIVE_FROM),
                    new EmployeeManagerMappingResponse(11L, 3L, null, LocalDate.of(2025, 6, 30)));
        }
    }
}