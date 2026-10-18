package com.travelease.admin.repository;

import java.util.Optional;

import com.travelease.admin.entity.EmployeeManagerMapping;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Employee→Manager mappings (tasks.md D-07;
 * data-model.md §5.3).
 *
 * <p>{@link #findByEmployeeIdAndDeletedFalse} resolves the current active mapping for
 * an employee; the approval routing resolver (H-01) walks the {@code managerId} chain
 * by repeatedly calling it (using the manager's id as the next {@code employeeId}),
 * stopping when a mapping has a null {@code managerId} (clarification Q3, FR-TR-05,
 * spec.md EC-09).</p>
 */
@Repository
public interface EmployeeManagerMappingRepository extends JpaRepository<EmployeeManagerMapping, Long> {

    /**
     * The current active mapping for an employee, excluding soft-deleted rows
     * (FR-ADM-01, FR-TR-05).
     *
     * @param employeeId logical UserAccount reference for the employee
     * @return the non-deleted active mapping for the given employee, or empty if absent
     */
    Optional<EmployeeManagerMapping> findByEmployeeIdAndDeletedFalse(Long employeeId);

    /**
     * All non-deleted mappings, paginated; backs the Admin mapping list screen
     * (FR-ADM-01, api-contract.md §4.3 {@code GET /api/admin/employee-manager-mappings}).
     *
     * @param pageable pagination and sort parameters
     * @return page of non-deleted mappings
     */
    Page<EmployeeManagerMapping> findByDeletedFalse(Pageable pageable);
}
