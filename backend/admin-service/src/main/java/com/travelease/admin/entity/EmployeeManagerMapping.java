package com.travelease.admin.entity;

import java.time.LocalDate;

import com.travelease.common.audit.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Employee → manager reporting relationship (data-model.md §5.3; spec.md FR-ADM-01, FR-TR-05).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields
 * ({@code createdAt/By}, {@code updatedAt/By}, {@code deleted}, {@code version};
 * data-model.md §1, §5.3). {@code employeeId} and {@code managerId} are plain
 * identifier columns - logical references to {@code UserAccount.id}, not JPA
 * associations: repositories look up the current mapping by {@code employeeId}
 * and walk up the {@code managerId} chain to resolve the approver. {@code managerId}
 * is deliberately nullable: only top-of-hierarchy managers have no manager, and
 * their approvals fall back to Finance (clarification Q3, FR-TR-05; spec.md edge
 * case EC-09). {@code employeeId} is unique (data-model.md §11 - "unique (employeeId)
 * for current active mapping" - expressed via {@code @Table(uniqueConstraints = ...)}).</p>
 */
@Entity
@Table(name = "employee_manager_mapping", uniqueConstraints = @UniqueConstraint(name = "uk_employee_manager_mapping_employee_id", columnNames = "employee_id"))
public class EmployeeManagerMapping extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Logical reference to the employee (UserAccount.id, owned by admin-service). */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /**
     * Logical reference to the employee's manager (UserAccount.id). Nullable - null only
     * for top-of-hierarchy managers, whose approvals fall back to Finance
     * (clarification Q3, FR-TR-05, spec.md EC-09).
     */
    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
}