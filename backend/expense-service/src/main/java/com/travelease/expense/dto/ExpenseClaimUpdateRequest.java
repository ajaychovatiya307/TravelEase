package com.travelease.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for updating an existing expense claim (api-contract.md §5.8).
 *
 * <p>Same shape as {@link ExpenseClaimCreateRequest}; both inherit their fields and
 * structural validations (VAL-05–07) from {@link ExpenseClaimWriteRequest}. The
 * Service layer enforces VAL-13 (edit only allowed when status is PENDING) and the
 * remaining business rules (VAL-08–10).</p>
 */
public class ExpenseClaimUpdateRequest extends ExpenseClaimWriteRequest {

    public ExpenseClaimUpdateRequest() {
        super();
    }

    public ExpenseClaimUpdateRequest(String travelRequestId, BigDecimal amount, String currency,
            String category, LocalDate expenseDate, List<String> documentIds) {
        super(travelRequestId, amount, currency, category, expenseDate, documentIds);
    }
}
