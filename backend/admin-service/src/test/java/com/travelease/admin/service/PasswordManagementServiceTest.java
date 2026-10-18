package com.travelease.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.travelease.admin.audit.AdminAuditEvents;
import com.travelease.admin.dto.PasswordResetResponse;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.service.impl.PasswordManagementServiceImpl;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;

/** #14 password lifecycle with a real BCrypt encoder (only persistence and audit publishing are mocked). */
@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {

    private static final String CURRENT = "Current-pass-2026";
    private static final String NEW = "Brand-new-pass-2027";

    @Mock
    private UserAccountRepository repository;

    @Mock
    private AdminAuditEvents auditEvents;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private PasswordManagementServiceImpl service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        service = new PasswordManagementServiceImpl(repository, encoder, auditEvents);
        account = new UserAccount();
        account.setId(7L);
        account.setEmail("asha.rao@travelease.example");
        account.setFullName("Asha Rao");
        account.setActive(true);
        account.setPasswordHash(encoder.encode(CURRENT));
    }

    @Test
    @DisplayName("change: verifies the current password, stores a new hash and invalidates older tokens")
    void changePasswordSucceeds() {
        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(account));
        int versionBefore = account.getCredentialsVersion();

        service.changePassword(7L, CURRENT, NEW);

        assertThat(encoder.matches(NEW, account.getPasswordHash())).isTrue();
        assertThat(account.getCredentialsVersion()).isEqualTo(versionBefore + 1);
        assertThat(account.isMustChangePassword()).isFalse();
        assertThat(account.getPasswordChangedAt()).isNotNull();
        verify(auditEvents).publish(AuditEntityType.USER_ACCOUNT, 7L, AuditActionType.PASSWORD_CHANGED, Map.of());
    }

    @Test
    @DisplayName("change: a wrong current password is rejected and nothing changes")
    void wrongCurrentPassword() {
        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(account));
        String hash = account.getPasswordHash();

        assertThatThrownBy(() -> service.changePassword(7L, "not-the-password1", NEW))
                .isInstanceOf(ValidationException.class);

        assertThat(account.getPasswordHash()).isEqualTo(hash);
        verify(repository, never()).save(any());
        verifyNoInteractions(auditEvents);
    }

    @Test
    @DisplayName("change: weak, reused or name-based passwords are rejected")
    void passwordPolicy() {
        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changePassword(7L, CURRENT, "short1")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.changePassword(7L, CURRENT, "onlyletterspassword"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.changePassword(7L, CURRENT, "asha.rao-2026-pass"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.changePassword(7L, CURRENT, CURRENT)).isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("reset: issues a one-time temporary password, forces a change, and audits without the value")
    void adminResetPassword() {
        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(account));
        int versionBefore = account.getCredentialsVersion();

        PasswordResetResponse response = service.resetPassword(5L, 7L);

        String temporary = response.temporaryPassword();
        assertThat(temporary).hasSize(16);
        assertThat(encoder.matches(temporary, account.getPasswordHash())).isTrue();
        assertThat(encoder.matches(CURRENT, account.getPasswordHash())).isFalse();
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(account.getCredentialsVersion()).isEqualTo(versionBefore + 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditEvents).publish(eq(AuditEntityType.USER_ACCOUNT), eq(7L), eq(AuditActionType.PASSWORD_RESET),
                metadata.capture());
        assertThat(metadata.getValue().toString()).doesNotContain(temporary);
    }

    @Test
    @DisplayName("reset: an administrator cannot reset their own password this way; unknown users are 404")
    void resetGuards() {
        assertThatThrownBy(() -> service.resetPassword(7L, 7L)).isInstanceOf(ForbiddenActionException.class);

        when(repository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(5L, 99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(auditEvents);
    }

    @Test
    @DisplayName("complete reset: the temporary password is replaced and the flag cleared")
    void completeRequiredChange() {
        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(account));
        String temporary = service.resetPassword(5L, 7L).temporaryPassword();
        when(repository.findByEmailIgnoreCaseAndDeletedFalse("asha.rao@travelease.example")).thenReturn(Optional.of(account));

        service.completeRequiredChange(" asha.rao@travelease.example ", temporary, NEW);

        assertThat(encoder.matches(NEW, account.getPasswordHash())).isTrue();
        assertThat(account.isMustChangePassword()).isFalse();
        verify(auditEvents).publishAs(eq(7L), eq(AuditEntityType.USER_ACCOUNT), eq(7L),
                eq(AuditActionType.PASSWORD_CHANGED), anyMap());
    }

    @Test
    @DisplayName("complete reset: unknown users and wrong temporary passwords get the same generic error")
    void completeRequiredChangeIsGeneric() {
        when(repository.findByEmailIgnoreCaseAndDeletedFalse("nobody@travelease.example")).thenReturn(Optional.empty());
        account.setMustChangePassword(true);
        when(repository.findByEmailIgnoreCaseAndDeletedFalse("asha.rao@travelease.example")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.completeRequiredChange("nobody@travelease.example", "x", NEW))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors().get(0).message())
                        .isEqualTo("Invalid username or password"));
        assertThatThrownBy(() -> service.completeRequiredChange("asha.rao@travelease.example", "wrong-temp", NEW))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors().get(0).message())
                        .isEqualTo("Invalid username or password"));
        verify(repository, never()).save(any());
    }
}
