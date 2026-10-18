package com.travelease.admin.service;

import java.util.List;
import java.util.Optional;

import com.travelease.admin.dto.UserAccountRequest;
import com.travelease.admin.dto.UserAccountCreateRequest;
import com.travelease.admin.dto.UserAccountResponse;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.entity.UserRole;
import com.travelease.admin.mapper.UserAccountMapper;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.service.impl.UserAdminServiceImpl;
import com.travelease.common.exception.DuplicateResourceException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.security.RoleType;

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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserAdminServiceImpl} (tasks.md L-07; spec.md FR-ADM-01), covering
 * admin user create/update/read/list with positive and negative cases per Constitution §10:
 *
 * <ul>
 *   <li>create: unique email + roles (clarification Q2 multi-role) → account saved with one
 *       {@link UserRole} row per requested role, response mapped</li>
 *   <li>create: email conflict via {@code findByEmailAndDeletedFalse} →
 *       {@link DuplicateResourceException}, nothing saved</li>
 *   <li>update: existing account → name/email/active replaced and role rows swapped in place
 *       (private {@code replaceRoles} helper)</li>
 *   <li>update: email already owned by a different account → {@link DuplicateResourceException}</li>
 *   <li>get: found → mapped response; not found → {@link ResourceNotFoundException}</li>
 *   <li>list: repository page of non-deleted accounts mapped to a {@code Page<UserAccountResponse>}</li>
 * </ul>
 *
 * <p>The repository is mocked with Mockito; the mapper is a plain (non-Spring) pure converter,
 * so a real {@code @Spy} instance is injected to keep the entity ↔ DTO mapping under test as
 * well - no Spring context is needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminServiceImpl (tasks.md L-07; spec.md FR-ADM-01)")
class UserAdminServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserAccountMapper userAccountMapper = new UserAccountMapper();

    /** Audit publishing (#13); verified explicitly where the event content matters. */

    @Mock

    private com.travelease.admin.audit.AdminAuditEvents auditEvents;


    @InjectMocks
    private UserAdminServiceImpl userAdminService;

    private static UserAccountRequest request(String fullName, String email, List<RoleType> roles) {
        return new UserAccountRequest(fullName, email, roles, Boolean.TRUE);
    }

    private static UserAccountCreateRequest createRequest(String fullName, String email, List<RoleType> roles) {
        return new UserAccountCreateRequest(fullName, email, roles, "InitialPassword123", Boolean.TRUE);
    }

    private static UserAccount account(Long id, String fullName, String email) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setFullName(fullName);
        account.setEmail(email);
        account.setActive(true);
        return account;
    }

    private static void withRole(UserAccount account, RoleType role) {
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        account.addRole(userRole);
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("saves the account with one role row per requested role and maps the response")
        void savesAccountWithRoles() {
            when(passwordEncoder.encode("InitialPassword123")).thenReturn("$2a$12$encoded-password");
            when(userAccountRepository.findByEmailAndDeletedFalse("ajay.kumar@travelease.example"))
                    .thenReturn(Optional.empty());
            when(userAccountRepository.save(any(UserAccount.class)))
                    .thenAnswer(invocation -> {
                        UserAccount created = invocation.getArgument(0);
                        created.setId(1L);
                        return created;
                    });

            UserAccountResponse response = userAdminService.createUser(
                    createRequest("Ajay Kumar", "ajay.kumar@travelease.example",
                            List.of(RoleType.EMPLOYEE, RoleType.MANAGER)));

            ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
            verify(userAccountRepository).save(captor.capture());
            UserAccount saved = captor.getValue();
            assertThat(saved.getFullName()).isEqualTo("Ajay Kumar");
            assertThat(saved.getEmail()).isEqualTo("ajay.kumar@travelease.example");
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$encoded-password");
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getRoles()).extracting(UserRole::getRole)
                    .containsExactly(RoleType.EMPLOYEE, RoleType.MANAGER);
            assertThat(saved.getRoles())
                    .allSatisfy(role -> assertThat(role.getUserAccount()).isSameAs(saved));

            assertThat(response).isEqualTo(new UserAccountResponse(
                    1L, "Ajay Kumar", "ajay.kumar@travelease.example",
                    List.of(RoleType.EMPLOYEE, RoleType.MANAGER), true));
        }

        @Test
        @DisplayName("throws DuplicateResourceException when the email already exists and saves nothing")
        void throwsOnDuplicateEmail() {
            when(userAccountRepository.findByEmailAndDeletedFalse("ajay.kumar@travelease.example"))
                    .thenReturn(Optional.of(account(1L, "Ajay Kumar", "ajay.kumar@travelease.example")));

            UserAccountCreateRequest duplicateEmailRequest =
                    createRequest("Ajay Kumar", "ajay.kumar@travelease.example", List.of(RoleType.EMPLOYEE));

            assertThatThrownBy(() -> userAdminService.createUser(duplicateEmailRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("ajay.kumar@travelease.example");

            verify(userAccountRepository, never()).save(any(UserAccount.class));
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("updates name, email and active and replaces the role rows in place")
        void updatesAccountAndReplacesRoles() {
            UserAccount existing = account(1L, "Old Name", "old.email@travelease.example");
            existing.setPasswordHash("$2a$12$existing-password-hash");
            withRole(existing, RoleType.FINANCE);
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(userAccountRepository.findByEmailAndDeletedFalse("new.email@travelease.example"))
                    .thenReturn(Optional.empty());
            when(userAccountRepository.save(any(UserAccount.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UserAccountResponse response = userAdminService.updateUser(1L,
                    request("New Name", "new.email@travelease.example", List.of(RoleType.EMPLOYEE, RoleType.MANAGER)));

            ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
            verify(userAccountRepository).save(captor.capture());
            UserAccount saved = captor.getValue();
            assertThat(saved).isSameAs(existing);
            assertThat(saved.getFullName()).isEqualTo("New Name");
            assertThat(saved.getEmail()).isEqualTo("new.email@travelease.example");
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$existing-password-hash");
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getRoles()).extracting(UserRole::getRole)
                    .containsExactly(RoleType.EMPLOYEE, RoleType.MANAGER);

            assertThat(response).isEqualTo(new UserAccountResponse(1L, "New Name", "new.email@travelease.example",
                    List.of(RoleType.EMPLOYEE, RoleType.MANAGER), true));
        }

        @Test
        @DisplayName("#13: records field changes, role grants/revocations and deactivation as separate, redacted audit events")
        void auditsPrivilegeChangesWithoutSensitiveData() {
            UserAccount existing = account(1L, "Old Name", "old.email@travelease.example");
            existing.setPasswordHash("$2a$12$existing-password-hash");
            withRole(existing, RoleType.FINANCE);
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(userAccountRepository.findByEmailAndDeletedFalse("new.email@travelease.example"))
                    .thenReturn(Optional.empty());
            when(userAccountRepository.save(any(UserAccount.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userAdminService.updateUser(1L, new UserAccountRequest("New Name", "new.email@travelease.example",
                    List.of(RoleType.EMPLOYEE, RoleType.ADMIN), false));

            org.mockito.ArgumentCaptor<com.travelease.common.event.AuditActionType> actions =
                    org.mockito.ArgumentCaptor.forClass(com.travelease.common.event.AuditActionType.class);
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<java.util.Map<String, String>> metadata =
                    org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
            verify(auditEvents, org.mockito.Mockito.times(4)).publish(
                    org.mockito.ArgumentMatchers.eq(com.travelease.common.event.AuditEntityType.USER_ACCOUNT),
                    org.mockito.ArgumentMatchers.eq(1L), actions.capture(), metadata.capture());

            assertThat(actions.getAllValues()).containsExactly(
                    com.travelease.common.event.AuditActionType.UPDATED,
                    com.travelease.common.event.AuditActionType.DEACTIVATED,
                    com.travelease.common.event.AuditActionType.ROLE_GRANTED,
                    com.travelease.common.event.AuditActionType.ROLE_REVOKED);
            assertThat(metadata.getAllValues().get(0)).containsEntry("fields", "fullName,email");
            assertThat(metadata.getAllValues().get(2)).containsEntry("roles", "ADMIN,EMPLOYEE");
            assertThat(metadata.getAllValues().get(3)).containsEntry("roles", "FINANCE");
            String everything = metadata.getAllValues().toString();
            assertThat(everything).doesNotContain("new.email@travelease.example", "old.email@travelease.example",
                    "New Name", "Old Name", "$2a$", "password");
        }

        @Test
        @DisplayName("#13: an update that changes nothing records nothing")
        void noChangesNoAuditEvents() {
            UserAccount existing = account(1L, "Same Name", "same@travelease.example");
            withRole(existing, RoleType.EMPLOYEE);
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(userAccountRepository.findByEmailAndDeletedFalse("same@travelease.example")).thenReturn(Optional.of(existing));
            when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userAdminService.updateUser(1L, new UserAccountRequest("Same Name", "same@travelease.example",
                    List.of(RoleType.EMPLOYEE), true));

            org.mockito.Mockito.verifyNoInteractions(auditEvents);
        }

        @Test
        @DisplayName("throws DuplicateResourceException when the new email is owned by a different account")
        void throwsWhenEmailOwnedByAnotherAccount() {
            when(userAccountRepository.findByIdAndDeletedFalse(1L))
                    .thenReturn(Optional.of(account(1L, "Ajay Kumar", "ajay.kumar@travelease.example")));
            when(userAccountRepository.findByEmailAndDeletedFalse("other@travelease.example"))
                    .thenReturn(Optional.of(account(2L, "Other User", "other@travelease.example")));

            UserAccountRequest otherEmailRequest =
                    request("Ajay Kumar", "other@travelease.example", List.of(RoleType.EMPLOYEE));

            assertThatThrownBy(() -> userAdminService.updateUser(1L, otherEmailRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("other@travelease.example");

            verify(userAccountRepository, never()).save(any(UserAccount.class));
        }
    }

    @Nested
    @DisplayName("getUser")
    class GetUser {

        @Test
        @DisplayName("maps the account to a response when found")
        void returnsMappedAccount() {
            UserAccount existing = account(1L, "Ajay Kumar", "ajay.kumar@travelease.example");
            withRole(existing, RoleType.EMPLOYEE);
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));

            UserAccountResponse response = userAdminService.getUser(1L);

            assertThat(response).isEqualTo(new UserAccountResponse(1L, "Ajay Kumar",
                    "ajay.kumar@travelease.example", List.of(RoleType.EMPLOYEE), true));
            verify(userAccountMapper).toResponse(existing);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the account does not exist")
        void throwsWhenNotFound() {
            when(userAccountRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAdminService.getUser(1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userAccountMapper, never()).toResponse(any(UserAccount.class));
        }
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsers {

        @Test
        @DisplayName("maps the repository page of non-deleted accounts to a response page")
        void mapsPage() {
            Pageable pageable = Pageable.ofSize(20);
            UserAccount first = account(1L, "Ajay Kumar", "ajay.kumar@travelease.example");
            withRole(first, RoleType.EMPLOYEE);
            UserAccount second = account(2L, "Meera Shah", "meera.shah@travelease.example");
            withRole(second, RoleType.MANAGER);
            when(userAccountRepository.findByDeletedFalse(pageable))
                    .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));

            Page<UserAccountResponse> page = userAdminService.listUsers(pageable);

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).containsExactly(
                    new UserAccountResponse(1L, "Ajay Kumar", "ajay.kumar@travelease.example",
                            List.of(RoleType.EMPLOYEE), true),
                    new UserAccountResponse(2L, "Meera Shah", "meera.shah@travelease.example",
                            List.of(RoleType.MANAGER), true));
        }
    }
}