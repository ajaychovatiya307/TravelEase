package com.travelease.admin.service.impl;

import com.travelease.admin.entity.EmployeeManagerMapping;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.repository.EmployeeManagerMappingRepository;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.repository.UserRoleRepository;
import com.travelease.admin.service.EmployeeManagerRoutingService.ApproverEligibility;
import com.travelease.admin.service.EmployeeManagerRoutingService;
import com.travelease.common.exception.PolicyViolationException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.security.RoleType;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic approver-routing logic (tasks.md H-01; spec.md FR-TR-05, BR-06).
 *
 * <p>Read-only - routing resolution never changes state, so every method is
 * {@code @Transactional(readOnly = true)} (Constitution §9).</p>
 */
@Service
public class EmployeeManagerRoutingServiceImpl implements EmployeeManagerRoutingService {

    static final String INACTIVE_APPROVER_MESSAGE =
            "Your assigned approver is no longer active. Ask an administrator to update your reporting line.";
    static final String NO_FINANCE_APPROVER_MESSAGE =
            "No active Finance approver is available. Ask an administrator to assign one.";

    private final EmployeeManagerMappingRepository mappingRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAccountRepository userAccountRepository;

    public EmployeeManagerRoutingServiceImpl(EmployeeManagerMappingRepository mappingRepository,
            UserRoleRepository userRoleRepository, UserAccountRepository userAccountRepository) {
        this.mappingRepository = mappingRepository;
        this.userRoleRepository = userRoleRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Resolves the approver for a new request (FR-TR-05). Only an active account holding MANAGER or
     * FINANCE can receive an approval (#11): a mapping to a deactivated/deleted manager is refused with
     * a clear 422 instead of silently routing the request into a queue nobody watches, and nothing is
     * transferred to an arbitrary user.
     */
    @Override
    @Transactional(readOnly = true)
    public Long resolveApprover(Long employeeId) {
        EmployeeManagerMapping mapping = mappingRepository.findByEmployeeIdAndDeletedFalse(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeManagerMapping", employeeId));

        Long managerId = mapping.getManagerId();
        if (managerId != null) {
            // Direct manager (also covers the "requester is a Manager" case - the mapping
            // already stores that Manager's own reporting manager, clarification Q3).
            if (!checkApproverEligibility(managerId).eligible()) {
                throw new PolicyViolationException("approver", INACTIVE_APPROVER_MESSAGE);
            }
            return managerId;
        }

        // Top-of-hierarchy manager with no manager of their own: fall back to an ACTIVE Finance user
        // (clarification Q3, FR-TR-05, spec.md EC-09), deterministically the lowest id.
        return findActiveFinanceUserIds().stream()
                .filter(financeId -> !financeId.equals(employeeId))
                .min(Long::compareTo)
                .orElseThrow(() -> new PolicyViolationException("approver", NO_FINANCE_APPROVER_MESSAGE));
    }

    /** An approver must exist, be active, and hold MANAGER or FINANCE (#11 reassignment and routing). */
    @Override
    @Transactional(readOnly = true)
    public ApproverEligibility checkApproverEligibility(Long userId) {
        Optional<UserAccount> account = userAccountRepository.findByIdAndDeletedFalse(userId);
        if (account.isEmpty()) {
            return new ApproverEligibility(userId, false, "User does not exist");
        }
        if (!account.get().isActive()) {
            return new ApproverEligibility(userId, false, "User is inactive");
        }
        boolean approverRole = account.get().getRoles().stream()
                .filter(role -> !role.isDeleted())
                .anyMatch(role -> role.getRole() == RoleType.MANAGER || role.getRole() == RoleType.FINANCE);
        return approverRole
                ? new ApproverEligibility(userId, true, null)
                : new ApproverEligibility(userId, false, "User holds neither MANAGER nor FINANCE");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findActiveFinanceUserIds() {
        return userRoleRepository.findByRoleAndDeletedFalse(RoleType.FINANCE).stream()
                .map(UserRole::getUserAccount)
                .filter(account -> account.isActive() && !account.isDeleted())
                .map(account -> account.getId())
                .distinct()
                .toList();
    }
}
