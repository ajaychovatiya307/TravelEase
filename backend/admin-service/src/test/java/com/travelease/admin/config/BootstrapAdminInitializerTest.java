package com.travelease.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.common.security.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("First-administrator bootstrap (#18)")
class BootstrapAdminInitializerTest {

    private static final String PASSWORD = "Initial-Pass-2026";

    private UserAccountRepository repository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
    }

    private BootstrapAdminInitializer initializer(String email, String password) {
        return new BootstrapAdminInitializer(repository, encoder, email, password);
    }

    @Test
    @DisplayName("does nothing unless both bootstrap settings are supplied")
    void disabledByDefault() {
        initializer("", "").run();
        initializer("ops@example.com", "").run();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("creates one ADMIN who must change the password at first login")
    void createsAdmin() {
        when(repository.existsByDeletedFalseAndActiveTrueAndRoles_Role(RoleType.ADMIN)).thenReturn(false);
        when(repository.findByEmailIgnoreCaseAndDeletedFalse("ops@example.com")).thenReturn(Optional.empty());

        initializer(" Ops@Example.com ", PASSWORD).run();

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        UserAccount account = saved.getValue();
        assertThat(account.getEmail()).isEqualTo("ops@example.com");
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(account.isActive()).isTrue();
        assertThat(account.getRoles()).extracting(r -> r.getRole()).containsExactly(RoleType.ADMIN);
        assertThat(encoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("is a no-op once an active administrator exists")
    void skipsWhenAdminExists() {
        when(repository.existsByDeletedFalseAndActiveTrueAndRoles_Role(RoleType.ADMIN)).thenReturn(true);
        initializer("ops@example.com", PASSWORD).run();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("refuses a weak bootstrap password without echoing it")
    void rejectsWeakPassword() {
        when(repository.existsByDeletedFalseAndActiveTrueAndRoles_Role(RoleType.ADMIN)).thenReturn(false);
        when(repository.findByEmailIgnoreCaseAndDeletedFalse(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer("ops@example.com", "short1").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVELEASE_BOOTSTRAP_ADMIN_PASSWORD")
                .hasMessageNotContaining("short1");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("never takes over an existing account")
    void rejectsExistingEmail() {
        when(repository.existsByDeletedFalseAndActiveTrueAndRoles_Role(RoleType.ADMIN)).thenReturn(false);
        when(repository.findByEmailIgnoreCaseAndDeletedFalse("ops@example.com"))
                .thenReturn(Optional.of(new UserAccount()));

        assertThatThrownBy(() -> initializer("ops@example.com", PASSWORD).run())
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }
}
