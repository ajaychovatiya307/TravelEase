package com.travelease.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a new expense claim (api-contract.md §5.7).
 *
 * <p>Fields and structural validations (VAL-05–07) are inherited from
 * {@link ExpenseClaimWriteRequest}; business-rule validations (approved trip
 * ownership, date range, receipt requirement) are deferred to the Service layer
 * (VAL-08–10, VAL-13).</p>
 */
public class ExpenseClaimCreateRequest extends ExpenseClaimWriteRequest {

    public ExpenseClaimCreateRequest() {
        super();
    }

    public ExpenseClaimCreateRequest(String travelRequestId, BigDecimal amount, String currency,
            String category, LocalDate expenseDate, List<String> documentIds) {
        super(travelRequestId, amount, currency, category, expenseDate, documentIds);
    }
}
