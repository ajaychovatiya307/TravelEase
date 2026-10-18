package com.travelease.expense.service.impl;

import com.travelease.common.dto.DocumentContent;
import com.travelease.expense.validation.ExpenseClaimPolicyValidator;
import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.dto.DocumentSummary;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.DocumentStorageService;
import com.travelease.common.storage.StorageException;
import com.travelease.common.validation.DocumentConstraintValidator;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseDocument;
import com.travelease.expense.mapper.ExpenseClaimMapper;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;
import com.travelease.expense.service.ExpenseDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ExpenseDocumentService} (tasks.md H-06).
 * Orchestrates document uploads to external storage with full validation
 * per VAL-11 and upload status management per FR-DOC-03, mirroring H-04
 * for expense claims.
 */
@Service
public class ExpenseDocumentServiceImpl implements ExpenseDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseDocumentServiceImpl.class);

    private final ExpenseDocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final ExpenseClaimMapper expenseClaimMapper;
    private final DocumentConstraintValidator documentConstraintValidator;
    private final ExpenseFailedDocumentRecorder failedDocumentRecorder;
    private final ExpenseClaimRepository claimRepository;
    private final ExpenseClaimPolicyValidator policyValidator;

    public ExpenseDocumentServiceImpl(
            ExpenseDocumentRepository documentRepository,
            DocumentStorageService storageService,
            ExpenseClaimMapper expenseClaimMapper,
            DocumentConstraintValidator documentConstraintValidator,
            ExpenseFailedDocumentRecorder failedDocumentRecorder,
            ExpenseClaimRepository claimRepository,
            ExpenseClaimPolicyValidator policyValidator) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.expenseClaimMapper = expenseClaimMapper;
        this.documentConstraintValidator = documentConstraintValidator;
        this.failedDocumentRecorder = failedDocumentRecorder;
        this.claimRepository = claimRepository;
        this.policyValidator = policyValidator;
    }

    /**
     * Orchestrates document upload: validate → upload to storage → persist metadata → return summary.
     * On storage failure, the document is persisted with uploadStatus = FAILED, keeping the
     * owning claim in Draft per FR-DOC-03.
     */
    @Override
    @Transactional
    public DocumentSummary uploadDocument(
            ExpenseClaim expenseClaim,
            String fileName,
            String fileType,
            byte[] fileContent) throws ValidationException, StorageException {

        logger.debug("Uploading document {} for expense claim {}", fileName, expenseClaim.getId());

        // Step 1: Validate file type
        DocumentFileType parsedFileType = documentConstraintValidator.validateFileType(fileType);

        // Step 2: Validate file size
        documentConstraintValidator.validateFileSize(fileContent.length);

        // Step 3: Validate document count
        documentConstraintValidator.validateDocumentCount(
                documentRepository.countByExpenseClaimIdAndDeletedFalse(expenseClaim.getId()),
                "expense claim");

        // Step 4: Upload to external storage (may throw StorageException)
        String storageReference;
        try {
            String mimeType = documentConstraintValidator.resolveMimeType(parsedFileType);
            storageReference = storageService.uploadFile(fileContent, fileName, mimeType);
            logger.debug("File uploaded to storage with reference: {}", storageReference);
        } catch (StorageException ex) {
            // Record the FAILED marker via the REQUIRES_NEW recorder so it commits independently
            // of this (now rollback-only) transaction, leaving claim in Draft state (FR-DOC-03, N-04);
            // the exception is logged by the caller's GlobalExceptionHandler, not here.
            failedDocumentRecorder.recordFailedDocument(expenseClaim, fileName, parsedFileType, fileContent.length);
            throw ex;
        }

        // Step 5: Create and persist document metadata with CONFIRMED status
        ExpenseDocument document = createDocumentEntity(
                expenseClaim, fileName, parsedFileType, fileContent.length, storageReference, UploadStatus.CONFIRMED);
        ExpenseDocument savedDocument = documentRepository.save(document);

        logger.info("Document {} uploaded successfully for expense claim {}", 
                savedDocument.getId(), expenseClaim.getId());

        return expenseClaimMapper.toDocumentSummary(savedDocument);
    }

    /**
     * Uploads a document to an expense claim identified by ID, enforcing
     * owner-only access (FR-DOC-01–03; NFR-10; Constitution §2/§3).
     *
     * <p>Loads the claim (non-deleted only), verifies the caller is the claim
     * owner, and delegates the full orchestration to the claim-based
     * overload. Mirrors the travel-request upload pattern
     * ({@code TravelRequestServiceImpl.uploadDocument}).</p>
     */
    @Override
    @Transactional
    public DocumentSummary uploadDocument(
            Long expenseClaimId,
            Long employeeId,
            String fileName,
            String fileType,
            byte[] fileContent) throws ValidationException, StorageException {

        // Load claim and verify it exists / not soft-deleted (NFR-04)
        ExpenseClaim claim = claimRepository.findByIdAndDeletedFalse(expenseClaimId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "expenseClaimId", "Expense claim not found: " + expenseClaimId));

        // Enforce owner-only upload (NFR-10)
        if (!claim.getEmployeeId().equals(employeeId)) {
            throw new ForbiddenActionException("upload documents", "the claim owner");
        }
        // No attachments once Finance has approved/reimbursed the claim
        policyValidator.validateDocumentsAttachable(claim.getStatus());

        // Delegate upload orchestration to the claim-based overload
        return uploadDocument(claim, fileName, fileType, fileContent);
    }

    /**
     * Creates an ExpenseDocument entity with metadata.
     */
    private ExpenseDocument createDocumentEntity(
            ExpenseClaim expenseClaim,
            String fileName,
            DocumentFileType fileType,
            long fileSizeBytes,
            String storageReference,
            UploadStatus uploadStatus) {

        ExpenseDocument document = new ExpenseDocument();
        document.setExpenseClaim(expenseClaim);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSizeBytes(fileSizeBytes);
        document.setStorageReference(storageReference);
        document.setUploadStatus(uploadStatus);
        document.setDeleted(false);

        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContent readContent(ExpenseDocument document) {
        if (document.getUploadStatus() != UploadStatus.CONFIRMED) {
            throw new ResourceNotFoundException("documentId", "Document is not available: its upload did not complete");
        }
        byte[] content = storageService.downloadFile(document.getStorageReference());
        // MIME type comes from the validated stored file type, never from client-supplied headers
        return new DocumentContent(document.getFileName(),
                documentConstraintValidator.resolveMimeType(document.getFileType()), content);
    }
}
