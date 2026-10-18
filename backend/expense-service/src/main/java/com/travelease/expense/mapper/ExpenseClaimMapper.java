package com.travelease.expense.mapper;

import java.util.List;

import com.travelease.common.dto.DocumentSummary;
import com.travelease.expense.dto.ExpenseClaimCreateRequest;
import com.travelease.expense.dto.ExpenseClaimResponse;
import com.travelease.expense.dto.ExpenseClaimUpdateRequest;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseDocument;

import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between {@link ExpenseClaim}/{@link ExpenseDocument} and their
 * DTOs (tasks.md G-03; api-contract.md §5.7–5.9). Isolated here so Controllers/Services
 * never perform inline entity-DTO mapping (Constitution §6).
 *
 * <p>Internal-only fields ({@code deleted}, {@code version}, audit metadata) are
 * deliberately never copied into {@link ExpenseClaimResponse} (Constitution §6
 * DTO-only boundary).</p>
 */
@Component
public class ExpenseClaimMapper {

    /**
     * Maps an {@link ExpenseClaim} (with its documents) to the response DTO.
     *
     * @param claim     the persisted expense claim
     * @param documents the claim's non-deleted documents (fetched separately, Constitution §5)
     * @return the response DTO, with no internal-only fields leaked
     */
    public ExpenseClaimResponse toResponse(ExpenseClaim claim, List<ExpenseDocument> documents) {
        if (claim == null) {
            return null;
        }
        return new ExpenseClaimResponse(
                String.valueOf(claim.getId()),
                String.valueOf(claim.getEmployeeId()),
                String.valueOf(claim.getTravelRequestId()),
                claim.getAmount(),
                claim.getCurrency(),
                claim.getCategory(),
                claim.getExpenseDate(),
                claim.getStatus(),
                claim.isDuplicateFlag(),
                claim.getRejectionReason(),
                claim.getReimbursedAt(),
                toDocumentSummaries(documents),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    /**
     * Maps one {@link ExpenseDocument} to its metadata-only {@link DocumentSummary}
     * (never the binary content, FR-DOC-02).
     *
     * @param document the document metadata entity
     * @return the response-facing document summary
     */
    public DocumentSummary toDocumentSummary(ExpenseDocument document) {
        if (document == null) {
            return null;
        }
        return new DocumentSummary(
                String.valueOf(document.getId()),
                document.getFileName(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getStorageReference(),
                document.getUploadStatus());
    }

    /**
     * Maps a list of {@link ExpenseDocument}s to their response-facing summaries.
     *
     * @param documents the document metadata entities
     * @return the response-facing document summaries, in the same order
     */
    public List<DocumentSummary> toDocumentSummaries(List<ExpenseDocument> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream()
                .map(this::toDocumentSummary)
                .toList();
    }

    /**
     * Copies a create-request's fields onto a new entity. {@code employeeId} (caller
     * identity), {@code status}, and {@code duplicateFlag} are resolved by the Service
     * layer, not this mapper (business logic stays out of the mapping layer).
     *
     * @param request the validated create request
     * @return a new, not-yet-persisted entity with request fields populated
     */
    public ExpenseClaim toEntity(ExpenseClaimCreateRequest request) {
        if (request == null) {
            return null;
        }
        ExpenseClaim claim = new ExpenseClaim();
        claim.setTravelRequestId(parseId(request.getTravelRequestId()));
        claim.setAmount(request.getAmount());
        claim.setCurrency(request.getCurrency());
        claim.setCategory(request.getCategory());
        claim.setExpenseDate(request.getExpenseDate());
        return claim;
    }

    /**
     * Applies an update-request's fields onto an existing, already-loaded entity.
     * Status-transition eligibility (VAL-13) is enforced by the Service layer before
     * this method is called, not here.
     *
     * @param request the validated update request
     * @param claim   the existing entity to update in place
     */
    public void updateEntity(ExpenseClaimUpdateRequest request, ExpenseClaim claim) {
        if (request == null || claim == null) {
            return;
        }
        claim.setTravelRequestId(parseId(request.getTravelRequestId()));
        claim.setAmount(request.getAmount());
        claim.setCurrency(request.getCurrency());
        claim.setCategory(request.getCategory());
        claim.setExpenseDate(request.getExpenseDate());
    }

    private Long parseId(String id) {
        return id == null ? null : Long.valueOf(id);
    }
}
