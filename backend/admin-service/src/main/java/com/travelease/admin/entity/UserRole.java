package com.travelease.admin.entity;

import com.travelease.common.audit.Auditable;
import com.travelease.common.security.RoleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One role assignment for a user account (data-model.md §5.2; spec.md FR-ADM-01, NFR-10).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields. The unique
 * composite constraint ({@code userId}, {@code role}) prevents duplicate role
 * assignment per user, while multiple rows per user support multiple roles
 * (clarification Q2; data-model.md §11 - expressed via
 * {@code @Table(uniqueConstraints = ...)}).</p>
 */
@Entity
@Table(name = "user_role", uniqueConstraints = @UniqueConstraint(name = "uk_user_role_user_role", columnNames = {"user_id", "role"}))
public class UserRole extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Owning user account (data-model.md §5.2 {@code userId} FK → {@code UserAccount.id}).
     * Same-service association - both entities live in the admin-service database,
     * so this is a real JPA association (no cross-service FK, Constitution §2).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount userAccount;

    /** RBAC role (data-model.md §5.2; clarification Q1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private RoleType role;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public RoleType getRole() {
        return role;
    }

    public void setRole(RoleType role) {
        this.role = role;
    }
}