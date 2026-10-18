package com.travelease.admin.repository;

import java.util.Optional;

import com.travelease.admin.entity.UserAccount;
import com.travelease.common.security.RoleType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for user accounts (tasks.md D-06; data-model.md §5.1).
 *
 * <p>Finders scope to non-deleted rows ({@code deleted = false}) so soft-deleted
 * accounts are excluded from all reads (NFR-04). {@link #findByEmailAndDeletedFalse}
 * backs the unique-email conflict check (FR-ADM-01).</p>
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Fetch a single account, excluding soft-deleted rows (FR-ADM-01).
     *
     * @param id primary key of the user account
     * @return the non-deleted account with the given id, or empty if absent
     */
    Optional<UserAccount> findByIdAndDeletedFalse(Long id);

    /**
     * Lookup by unique email, excluding soft-deleted rows; used to detect duplicate
     * email conflicts on user creation/update (FR-ADM-01).
     *
     * @param email account email address
     * @return the non-deleted account with the given email, or empty if absent
     */
    Optional<UserAccount> findByEmailAndDeletedFalse(String email);

    /**
     * Case-insensitive login lookup, excluding soft-deleted rows.
     *
     * @param email username supplied at login (the account's email address)
     * @return active row with the matching email, when present
     */
    Optional<UserAccount> findByEmailIgnoreCaseAndDeletedFalse(String email);

    /**
     * All non-deleted user accounts, paginated (FR-ADM-01).
     *
     * @param pageable pagination and sort parameters
     * @return page of non-deleted user accounts
     */
    Page<UserAccount> findByDeletedFalse(Pageable pageable);

    /**
     * Whether any non-deleted, active account holds the given role; used by the first-admin bootstrap.
     *
     * @param role role to look for
     * @return true when at least one active account has the role
     */
    boolean existsByDeletedFalseAndActiveTrueAndRoles_Role(RoleType role);
}
