package com.travelease.expense.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.travelease.common.audit.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Expense claim raised against an approved travel request (data-model.md §4.1;
 * spec.md FR-EXP-01–07, FR-FIN-01–03).
 *
 * <p>Extends {@link Auditable}. {@code travelRequestId} is a plain identifier column
 * (a logical, cross-service reference to travel-request-service) - it is deliberately
 * NOT a JPA association (Constitution §2: no cross-service DB access).</p>
 */
@Entity
@Table(name = "expense_claim")
public class ExpenseClaim extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Logical reference to the owning user (UserAccount.id, owned by admin-service). */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Logical, cross-service reference to TravelRequest.id - plain column, no JPA association. */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Category code referencing the configurable ExpenseCategory (owned by admin-service). */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExpenseClaimStatus status;

    /** Set true when amount/date/category match an existing claim under the same trip (FR-EXP-07). */
    @Column(name = "duplicate_flag", nullable = false)
    private boolean duplicateFlag = false;

    /** Populated only when status = REJECTED. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Set when status = REIMBURSED (FR-FIN-03). */
    @Column(name = "reimbursed_at")
    private Instant reimbursedAt;

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

    public Long getTravelRequestId() {
        return travelRequestId;
    }

    public void setTravelRequestId(Long travelRequestId) {
        this.travelRequestId = travelRequestId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public ExpenseClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ExpenseClaimStatus status) {
        this.status = status;
    }

    public boolean isDuplicateFlag() {
        return duplicateFlag;
    }

    public void setDuplicateFlag(boolean duplicateFlag) {
        this.duplicateFlag = duplicateFlag;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getReimbursedAt() {
        return reimbursedAt;
    }

    public void setReimbursedAt(Instant reimbursedAt) {
        this.reimbursedAt = reimbursedAt;
    }
}