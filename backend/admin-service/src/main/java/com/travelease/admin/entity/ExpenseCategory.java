package com.travelease.admin.entity;

import com.travelease.common.audit.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Configurable expense category reference table (data-model.md §5.5; spec.md FR-EXP-04, FR-ADM-02, VAL-07).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields
 * ({@code createdAt/By}, {@code updatedAt/By}, {@code deleted}, {@code version};
 * data-model.md §1). This is the configurable reference table backing
 * {@code ExpenseClaim.category} (expense-service): Admin maintains the category
 * list (FR-ADM-02) rather than it being a hardcoded enum, and {@code code} is the
 * key used to validate claim categories (VAL-07). {@code code} is required and
 * unique (data-model.md §5.5; §11 - expressed via
 * {@code @Table(uniqueConstraints = ...)}).</p>
 */
@Entity
@Table(name = "expense_category", uniqueConstraints = @UniqueConstraint(name = "uk_expense_category_code", columnNames = "code"))
public class ExpenseCategory extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Required and unique; uniqueness is enforced by the {@code @Table} constraint above (VAL-07 key). */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /** Required display name shown to end users (data-model.md §5.5). */
    @Column(name = "label", nullable = false)
    private String label;

    /** Inactive categories are excluded from claim entry but retained for history. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}