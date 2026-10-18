package com.travelease.admin.entity;

import java.util.ArrayList;
import java.util.List;

import com.travelease.common.audit.Auditable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Admin-managed user account (data-model.md §5.1; spec.md FR-ADM-01, NFR-10).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields
 * ({@code createdAt/By}, {@code updatedAt/By}, {@code deleted}, {@code version}).
 * {@code email} is unique (data-model.md §5.1; data-model.md §11 - expressed via
 * {@code @Table(uniqueConstraints = ...)}). One {@code UserAccount} owns many
 * {@code UserRole} rows, allowing a user to hold multiple roles simultaneously
 * (clarification Q2, NFR-10).</p>
 */
@Entity
@Table(name = "user_account", uniqueConstraints = @UniqueConstraint(name = "uk_user_account_email", columnNames = "email"))
public class UserAccount extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Required and unique; uniqueness is enforced by the {@code @Table} constraint above. */
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Set by an administrator's password reset: the temporary password must be replaced before use (#14). */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /**
     * Incremented whenever credentials or access change (password change/reset, deactivation). Issued
     * tokens carry it as the {@code cv} claim; admin-service rejects tokens with an older value (#14).
     */
    @Column(name = "credentials_version", nullable = false)
    private int credentialsVersion = 0;

    @Column(name = "password_changed_at")
    private java.time.Instant passwordChangedAt;

    /** Role assignments for this account (data-model.md §5.1 relationship, §11, Q2 multi-role). */
    @OneToMany(mappedBy = "userAccount", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserRole> roles = new ArrayList<>();

    /**
     * Adds a role assignment, keeping both sides of the association in sync
     * (data-model.md §5.1 - one {@code UserAccount} to many {@code UserRole}).
     */
    public void addRole(UserRole role) {
        role.setUserAccount(this);
        roles.add(role);
    }

    /**
     * Removes a role assignment, keeping both sides of the association in sync
     * (data-model.md §5.1 - one {@code UserAccount} to many {@code UserRole}).
     */
    public void removeRole(UserRole role) {
        roles.remove(role);
        role.setUserAccount(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public int getCredentialsVersion() {
        return credentialsVersion;
    }

    /** Invalidates every token issued for this account so far (admin-service enforces the {@code cv} claim). */
    public void bumpCredentialsVersion() {
        this.credentialsVersion++;
    }

    public java.time.Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(java.time.Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(List<UserRole> roles) {
        this.roles = roles;
    }
}