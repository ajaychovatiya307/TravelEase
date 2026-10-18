package com.travelease.admin.repository;

import java.util.List;
import java.util.Optional;

import com.travelease.admin.entity.UserRole;
import com.travelease.common.security.RoleType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for user role assignments (tasks.md D-06; data-model.md §5.2).
 *
 * <p>A user account may hold multiple roles simultaneously (clarification Q2);
 * {@link #existsByUserAccountIdAndRole} backs the unique (userId, role) conflict
 * check before inserting a new assignment.</p>
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * All role assignments for a user account (FR-ADM-01, clarification Q2).
     *
     * @param userAccountId owning user account id
     * @return role assignments for the given user account
     */
    List<UserRole> findByUserAccountId(Long userAccountId);

    /**
     * All non-deleted assignments for the given role.
     *
     * @param role role to look up
     * @return non-deleted assignments for the given role
     */
    List<UserRole> findByRoleAndDeletedFalse(RoleType role);

    /**
     * Whether a user already holds the given role, used to prevent duplicate
     * (userId, role) assignment (data-model.md §5.2 unique constraint).
     *
     * @param userAccountId owning user account id
     * @param role role to check
     * @return true if the user account already holds this role
     */
    boolean existsByUserAccountIdAndRole(Long userAccountId, RoleType role);

    /**
     * The first non-deleted user account holding the given role, used by the
     * approval routing resolver to fall back to Finance when a travel request's
     * approver chain has no higher manager (clarification Q3, FR-TR-05, spec.md EC-09).
     *
     * @param role role to look up (typically {@code FINANCE})
     * @return a non-deleted role assignment for a user holding the given role, or empty if none exists
     */
    Optional<UserRole> findFirstByRoleAndDeletedFalse(RoleType role);
}
