package com.travelease.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Shared shape for expense claim write payloads (api-contract.md §5.7/§5.8).
 *
 * <p>Holds the structural/format validations common to create and update (VAL-05–07):
 * amount &gt; 0, currency present, category code present. Business-rule validations
 * (approved trip ownership, date range, receipt requirement, edit-when-PENDING) are
 * deferred to the Service layer (VAL-08–10, VAL-13).</p>
 */
public abstract class ExpenseClaimWriteRequest {

    @NotBlank(message = "travelRequestId is required")
    private String travelRequestId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0 (VAL-05)")
    private BigDecimal amount;

    @NotBlank(message = "currency is required (VAL-06)")
    private String currency;

    @NotBlank(message = "category is required")
    private String category;

    @NotNull(message = "expenseDate is required")
    private LocalDate expenseDate;

    /** Document IDs referenced (required if amount exceeds threshold, VAL-10). Validated at Service layer. */
    private List<String> documentIds;

    protected ExpenseClaimWriteRequest() {
    }

    protected ExpenseClaimWriteRequest(String travelRequestId, BigDecimal amount, String currency,
            String category, LocalDate expenseDate, List<String> documentIds) {
        this.travelRequestId = travelRequestId;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.expenseDate = expenseDate;
        this.documentIds = documentIds;
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

    public List<String> getDocumentIds() {
        return documentIds;
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

    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }
}
