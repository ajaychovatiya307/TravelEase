package com.travelease.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travelease.admin.entity.EmployeeManagerMapping;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.EmployeeManagerMappingRepository;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.repository.UserRoleRepository;
import com.travelease.admin.service.impl.EmployeeManagerRoutingServiceImpl;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.security.RoleType;

/** Approver routing (FR-TR-05, BR-06, EC-09) with active-approver rules (#11). */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeManagerRoutingServiceImpl (FR-TR-05, BR-06, EC-09; #11)")
class EmployeeManagerRoutingServiceTest {

    @Mock
    private EmployeeManagerMappingRepository mappingRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private EmployeeManagerRoutingServiceImpl routingService;

    private static EmployeeManagerMapping mappingWithManager(Long employeeId, Long managerId) {
        EmployeeManagerMapping mapping = new EmployeeManagerMapping();
        mapping.setEmployeeId(employeeId);
        mapping.setManagerId(managerId);
        return mapping;
    }

    private static UserAccount user(long id, boolean active, RoleType... roles) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setActive(active);
        for (RoleType role : roles) {
            UserRole userRole = new UserRole();
            userRole.setRole(role);
            account.addRole(userRole);
        }
        return account;
    }

    private void existing(UserAccount account) {
        when(userAccountRepository.findByIdAndDeletedFalse(account.getId())).thenReturn(Optional.of(account));
    }

    private static UserRole financeRoleOf(UserAccount account) {
        return account.getRoles().stream().filter(r -> r.getRole() == RoleType.FINANCE).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("manager routing")
    class ManagerRouting {

        @Test
        @DisplayName("returns the active direct manager")
        void returnsDirectManager() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(1L)).thenReturn(Optional.of(mappingWithManager(1L, 2L)));
            existing(user(2L, true, RoleType.EMPLOYEE, RoleType.MANAGER));

            assertThat(routingService.resolveApprover(1L)).isEqualTo(2L);
        }

        @Test
        @DisplayName("refuses (422) to route to a deactivated manager instead of leaving the request stuck")
        void inactiveManagerRefused() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(1L)).thenReturn(Optional.of(mappingWithManager(1L, 2L)));
            existing(user(2L, false, RoleType.MANAGER));

            assertThatThrownBy(() -> routingService.resolveApprover(1L))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining("no longer active");
        }

        @Test
        @DisplayName("refuses a mapped 'manager' who no longer holds an approver role, or no longer exists")
        void managerWithoutApproverRoleRefused() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(1L)).thenReturn(Optional.of(mappingWithManager(1L, 2L)));
            existing(user(2L, true, RoleType.EMPLOYEE));
            assertThatThrownBy(() -> routingService.resolveApprover(1L)).isInstanceOf(PolicyViolationException.class);

            when(userAccountRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> routingService.resolveApprover(1L)).isInstanceOf(PolicyViolationException.class);
        }
    }

    @Nested
    @DisplayName("top-of-hierarchy Finance fallback (clarification Q3, spec.md EC-09)")
    class FinanceFallback {

        @Test
        @DisplayName("routes to the lowest-id ACTIVE Finance user, skipping inactive ones")
        void skipsInactiveFinance() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(3L)).thenReturn(Optional.of(mappingWithManager(3L, null)));
            UserAccount inactive = user(4L, false, RoleType.FINANCE);
            UserAccount activeHigher = user(9L, true, RoleType.FINANCE);
            UserAccount activeLower = user(6L, true, RoleType.FINANCE);
            when(userRoleRepository.findByRoleAndDeletedFalse(RoleType.FINANCE))
                    .thenReturn(List.of(financeRoleOf(inactive), financeRoleOf(activeHigher), financeRoleOf(activeLower)));

            assertThat(routingService.resolveApprover(3L)).isEqualTo(6L);
        }

        @Test
        @DisplayName("fails clearly when no active Finance user exists")
        void noActiveFinance() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(3L)).thenReturn(Optional.of(mappingWithManager(3L, null)));
            when(userRoleRepository.findByRoleAndDeletedFalse(RoleType.FINANCE))
                    .thenReturn(List.of(financeRoleOf(user(4L, false, RoleType.FINANCE))));

            assertThatThrownBy(() -> routingService.resolveApprover(3L))
                    .isInstanceOf(PolicyViolationException.class)
                    .hasMessageContaining("No active Finance approver");
        }

        @Test
        @DisplayName("a top-of-hierarchy Finance user is never routed to themselves")
        void neverSelf() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(6L)).thenReturn(Optional.of(mappingWithManager(6L, null)));
            when(userRoleRepository.findByRoleAndDeletedFalse(RoleType.FINANCE))
                    .thenReturn(List.of(financeRoleOf(user(6L, true, RoleType.FINANCE)), financeRoleOf(user(8L, true, RoleType.FINANCE))));

            assertThat(routingService.resolveApprover(6L)).isEqualTo(8L);
        }
    }

    @Nested
    @DisplayName("negative cases (Constitution §10)")
    class NegativeCases {

        @Test
        @DisplayName("throws ResourceNotFoundException when no mapping exists for the employee")
        void throwsWhenNoMapping() {
            when(mappingRepository.findByEmployeeIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> routingService.resolveApprover(99L)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("approver eligibility (#11 reassignment)")
    class Eligibility {

        @Test
        void activeManagerOrFinanceIsEligible() {
            existing(user(2L, true, RoleType.MANAGER));
            existing(user(4L, true, RoleType.FINANCE));
            assertThat(routingService.checkApproverEligibility(2L).eligible()).isTrue();
            assertThat(routingService.checkApproverEligibility(4L).eligible()).isTrue();
        }

        @Test
        void inactiveUnknownOrNonApproverIsNot() {
            existing(user(2L, false, RoleType.MANAGER));
            existing(user(3L, true, RoleType.EMPLOYEE));
            when(userAccountRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThat(routingService.checkApproverEligibility(2L).reason()).isEqualTo("User is inactive");
            assertThat(routingService.checkApproverEligibility(3L).eligible()).isFalse();
            assertThat(routingService.checkApproverEligibility(99L).eligible()).isFalse();
        }
    }
}
