package com.travelease.admin.entity;

import java.math.BigDecimal;

import com.travelease.common.audit.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-category expense policy configuration (data-model.md §5.4; spec.md FR-ADM-02, VAL-10, NFR-05).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields
 * ({@code createdAt/By}, {@code updatedAt/By}, {@code deleted}, {@code version};
 * data-model.md §1). {@code categoryCode} is a plain identifier column referencing
 * {@code ExpenseCategory.code} - deliberately NOT a JPA association, and there is
 * no unique constraint on it because the category → config relationship may be
 * historised (multiple rows per category over time; data-model.md §5.4, §11).
 * {@code receiptThresholdAmount} is the source of truth for the receipt-requirement
 * validation (VAL-10), and {@code currency} matches the single base currency (NFR-05).</p>
 */
@Entity
@Table(name = "policy_config")
public class PolicyConfig extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Plain identifier column referencing {@code ExpenseCategory.code} (no JPA association). */
    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    /**
     * Receipt threshold amount for this category - source of truth for VAL-10
     * (claims at or above this amount require a receipt). Must be {@code >= 0};
     * that rule is enforced at the validation/service layer, not by a DB check.
     */
    @Column(name = "receipt_threshold_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal receiptThresholdAmount;

    /** Single base currency (NFR-05). */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Inactive configurations are ignored by claim validation but retained for history. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public BigDecimal getReceiptThresholdAmount() {
        return receiptThresholdAmount;
    }

    public void setReceiptThresholdAmount(BigDecimal receiptThresholdAmount) {
        this.receiptThresholdAmount = receiptThresholdAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}