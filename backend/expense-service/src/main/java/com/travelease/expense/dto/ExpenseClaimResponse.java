package com.travelease.expense.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.travelease.common.dto.DocumentSummary;
import com.travelease.expense.entity.ExpenseClaimStatus;

/**
 * Response DTO representing an expense claim (api-contract.md §5.9).
 * 
 * <p>Returned by GET, POST (create), and PUT (update) endpoints. Includes full
 * lifecycle state, audit timestamps, and associated document metadata.
 * Internal-only fields ({@code deleted}, {@code version}) are never mapped here
 * (Constitution §6: DTO-only boundary).</p>
 */
public class ExpenseClaimResponse {

    private String id;
    private String employeeId;
    private String travelRequestId;
    private BigDecimal amount;
    private String currency;
    private String category;
    private LocalDate expenseDate;
    private ExpenseClaimStatus status;
    private boolean duplicateFlag;
    private String rejectionReason;
    private Instant reimbursedAt;
    private List<DocumentSummary> documents;
    private Instant createdAt;
    private Instant updatedAt;

    // Constructors
    public ExpenseClaimResponse() {
    }

    // Flat response DTO mirroring api-contract.md §5.9; a builder would add no value here.
    @SuppressWarnings("java:S107")
    public ExpenseClaimResponse(String id, String employeeId, String travelRequestId, BigDecimal amount, 
            String currency, String category, LocalDate expenseDate, ExpenseClaimStatus status, 
            boolean duplicateFlag, String rejectionReason, Instant reimbursedAt, 
            List<DocumentSummary> documents, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.travelRequestId = travelRequestId;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.expenseDate = expenseDate;
        this.status = status;
        this.duplicateFlag = duplicateFlag;
        this.rejectionReason = rejectionReason;
        this.reimbursedAt = reimbursedAt;
        this.documents = documents;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getTravelRequestId() {
        return travelRequestId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public ExpenseClaimStatus getStatus() {
        return status;
    }

    public boolean isDuplicateFlag() {
        return duplicateFlag;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getReimbursedAt() {
        return reimbursedAt;
    }

    public List<DocumentSummary> getDocuments() {
        return documents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public void setTravelRequestId(String travelRequestId) {
        this.travelRequestId = travelRequestId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public void setStatus(ExpenseClaimStatus status) {
        this.status = status;
    }

    public void setDuplicateFlag(boolean duplicateFlag) {
        this.duplicateFlag = duplicateFlag;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public void setReimbursedAt(Instant reimbursedAt) {
        this.reimbursedAt = reimbursedAt;
    }

    public void setDocuments(List<DocumentSummary> documents) {
        this.documents = documents;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
