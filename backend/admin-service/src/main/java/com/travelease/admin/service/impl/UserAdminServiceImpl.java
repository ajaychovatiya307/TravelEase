package com.travelease.admin.service.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.travelease.admin.audit.AdminAuditEvents;
import com.travelease.admin.dto.UserAccountCreateRequest;
import com.travelease.admin.dto.UserAccountRequest;
import com.travelease.admin.dto.UserAccountResponse;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.mapper.UserAccountMapper;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.service.UserAdminService;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.security.RoleType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user/role management business logic (tasks.md H-08; spec.md FR-ADM-01).
 *
 * <p>Enforces the unique-email business rule (data-model.md §5.1) here, since
 * repositories perform data access only (Constitution §5) - the {@code @Table}
 * unique constraint is the last line of defense, not the primary check surfaced
 * to callers.</p>
 */
@Service
public class UserAdminServiceImpl implements UserAdminService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditEvents auditEvents;

    public UserAdminServiceImpl(
            UserAccountRepository userAccountRepository,
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            AdminAuditEvents auditEvents) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditEvents = auditEvents;
    }

    @Override
    @Transactional
    public UserAccountResponse createUser(UserAccountCreateRequest request) {
        userAccountRepository.findByEmailAndDeletedFalse(request.email()).ifPresent(existing -> {
            throw new DuplicateResourceException("A user account with email " + request.email() + " already exists");
        });
        UserAccount account = userAccountMapper.toEntity(request);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        UserAccount saved = userAccountRepository.save(account);
        // #13: who created which account with which roles (no name/email/password in the audit trail)
        auditEvents.publish(AuditEntityType.USER_ACCOUNT, saved.getId(), AuditActionType.CREATED, Map.of(
                "roles", roleNames(activeRoles(saved)),
                "active", String.valueOf(saved.isActive())));
        return userAccountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserAccountResponse updateUser(Long id, UserAccountRequest request) {
        UserAccount account = userAccountRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount", id));

        userAccountRepository.findByEmailAndDeletedFalse(request.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("A user account with email " + request.email() + " already exists");
                });

        // capture the before-state for the audit trail (#13)
        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(account.getFullName(), request.fullName())) {
            changedFields.add("fullName");
        }
        if (!Objects.equals(account.getEmail(), request.email())) {
            changedFields.add("email");
        }
        boolean wasActive = account.isActive();
        Set<RoleType> rolesBefore = activeRoles(account);

        account.setFullName(request.fullName());
        account.setEmail(request.email());
        account.setActive(Boolean.TRUE.equals(request.active()));
        replaceRoles(account, request.roles());

        Set<RoleType> rolesAfter = EnumSet.noneOf(RoleType.class);
        rolesAfter.addAll(request.roles());
        if (wasActive != account.isActive() || !rolesBefore.equals(rolesAfter)) {
            // roles/active travel inside issued tokens: invalidate them so the change applies now (#14)
            account.bumpCredentialsVersion();
        }
        UserAccount saved = userAccountRepository.save(account);
        publishUserChanges(saved.getId(), changedFields, wasActive, saved.isActive(), rolesBefore, rolesAfter);
        return userAccountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountResponse getUser(Long id) {
        UserAccount account = userAccountRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount", id));
        return userAccountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserAccountResponse> listUsers(Pageable pageable) {
        return userAccountRepository.findByDeletedFalse(pageable).map(userAccountMapper::toResponse);
    }

    /** Replaces the account's role assignments in place, relying on orphanRemoval to drop stale rows. */
    private void replaceRoles(UserAccount account, List<RoleType> roles) {
        new ArrayList<>(account.getRoles()).forEach(account::removeRole);
        userAccountRepository.saveAndFlush(account);
        for (RoleType role : roles) {
            UserRole userRole = new UserRole();
            userRole.setRole(role);
            account.addRole(userRole);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserContact getContact(Long userId) {
        return userAccountRepository.findByIdAndDeletedFalse(userId)
                .map(account -> new UserContact(account.getId(), account.getEmail(), account.isActive()))
                .orElse(new UserContact(userId, null, false));
    }

    /** One explicit audit event per kind of change, so privilege changes can never hide in an "update". */
    private void publishUserChanges(Long userId, List<String> changedFields, boolean wasActive, boolean isActive,
            Set<RoleType> rolesBefore, Set<RoleType> rolesAfter) {
        if (!changedFields.isEmpty()) {
            auditEvents.publish(AuditEntityType.USER_ACCOUNT, userId, AuditActionType.UPDATED,
                    Map.of("fields", String.join(",", changedFields)));
        }
        if (wasActive != isActive) {
            auditEvents.publish(AuditEntityType.USER_ACCOUNT, userId,
                    isActive ? AuditActionType.ACTIVATED : AuditActionType.DEACTIVATED, Map.of());
        }
        Set<RoleType> granted = EnumSet.noneOf(RoleType.class);
        granted.addAll(rolesAfter);
        granted.removeAll(rolesBefore);
        Set<RoleType> revoked = EnumSet.noneOf(RoleType.class);
        revoked.addAll(rolesBefore);
        revoked.removeAll(rolesAfter);
        if (!granted.isEmpty()) {
            auditEvents.publish(AuditEntityType.USER_ACCOUNT, userId, AuditActionType.ROLE_GRANTED,
                    Map.of("roles", roleNames(granted)));
        }
        if (!revoked.isEmpty()) {
            auditEvents.publish(AuditEntityType.USER_ACCOUNT, userId, AuditActionType.ROLE_REVOKED,
                    Map.of("roles", roleNames(revoked)));
        }
    }

    private static Set<RoleType> activeRoles(UserAccount account) {
        Set<RoleType> roles = EnumSet.noneOf(RoleType.class);
        account.getRoles().stream().filter(role -> !role.isDeleted() && role.getRole() != null)
                .forEach(role -> roles.add(role.getRole()));
        return roles;
    }

    private static String roleNames(Set<RoleType> roles) {
        return roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
