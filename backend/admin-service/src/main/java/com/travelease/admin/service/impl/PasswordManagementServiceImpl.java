package com.travelease.admin.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;

import com.travelease.admin.audit.AdminAuditEvents;
import com.travelease.admin.dto.PasswordResetResponse;
import com.travelease.admin.entity.UserAccount;
import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.security.PasswordPolicy;
import com.travelease.admin.service.PasswordManagementService;
import com.travelease.common.event.AuditActionType;
import com.travelease.common.event.AuditEntityType;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordManagementServiceImpl implements PasswordManagementService {

    /** Unambiguous characters for temporary passwords (no 0/O, 1/l/I). */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditEvents auditEvents;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordManagementServiceImpl(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
            AdminAuditEvents auditEvents) {
        this(userAccountRepository, passwordEncoder, auditEvents, Clock.systemUTC());
    }

    PasswordManagementServiceImpl(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
            AdminAuditEvents auditEvents, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserAccount account = userAccountRepository.findByIdAndDeletedFalse(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount", userId));
        if (account.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ValidationException("currentPassword", "The current password is incorrect");
        }
        applyNewPassword(account, currentPassword, newPassword);
        auditEvents.publish(AuditEntityType.USER_ACCOUNT, account.getId(), AuditActionType.PASSWORD_CHANGED, Map.of());
    }

    @Override
    @Transactional
    public void completeRequiredChange(String username, String temporaryPassword, String newPassword) {
        UserAccount account = userAccountRepository.findByEmailIgnoreCaseAndDeletedFalse(username.trim())
                .filter(UserAccount::isActive)
                .filter(candidate -> candidate.getPasswordHash() != null
                        && passwordEncoder.matches(temporaryPassword, candidate.getPasswordHash()))
                // same generic answer for unknown users and wrong passwords (no account enumeration)
                .orElseThrow(() -> new ValidationException("credentials", INVALID_CREDENTIALS));
        if (!account.isMustChangePassword()) {
            throw new ValidationException("credentials", "No password change is pending for this account");
        }
        applyNewPassword(account, temporaryPassword, newPassword);
        auditEvents.publishAs(account.getId(), AuditEntityType.USER_ACCOUNT, account.getId(),
                AuditActionType.PASSWORD_CHANGED, Map.of("afterReset", "true"));
    }

    @Override
    @Transactional
    public PasswordResetResponse resetPassword(Long adminId, Long targetUserId) {
        if (adminId != null && adminId.equals(targetUserId)) {
            // an administrator changes their own password with the current one, never via a reset
            throw new ForbiddenActionException("Use 'change password' for your own account");
        }
        UserAccount account = userAccountRepository.findByIdAndDeletedFalse(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount", targetUserId));
        String temporaryPassword = generateTemporaryPassword();
        account.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        account.setMustChangePassword(true);
        account.setPasswordChangedAt(clock.instant());
        account.bumpCredentialsVersion();
        userAccountRepository.save(account);
        // the value is returned once to the administrator and never recorded anywhere
        auditEvents.publish(AuditEntityType.USER_ACCOUNT, account.getId(), AuditActionType.PASSWORD_RESET,
                Map.of("mustChangePassword", "true"));
        return new PasswordResetResponse(temporaryPassword);
    }

    private void applyNewPassword(UserAccount account, String previousPassword, String newPassword) {
        PasswordPolicy.validate(newPassword, account.getEmail());
        if (newPassword.equals(previousPassword)) {
            throw new ValidationException("newPassword", "The new password must be different from the current one");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setMustChangePassword(false);
        account.setPasswordChangedAt(clock.instant());
        account.bumpCredentialsVersion();
        userAccountRepository.save(account);
    }

    String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH - 2; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        // guarantee the letter+digit policy for the replacement check
        builder.append((char) ('a' + random.nextInt(26))).append((char) ('2' + random.nextInt(8)));
        return builder.toString();
    }
}
