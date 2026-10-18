package com.travelease.expense.service.impl;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseDocument;
import com.travelease.expense.repository.ExpenseDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists FAILED document audit markers in a transaction independent from the
 * caller's (tasks.md N-04 remediation).
 *
 * <p>{@code ExpenseDocumentServiceImpl#uploadDocument} rethrows the
 * {@code StorageException} after marking a document as FAILED. Because
 * {@code StorageException} is a {@code RuntimeException}, the surrounding Spring
 * transaction is marked rollback-only, so a FAILED row saved in that same
 * transaction is rolled back live and the "document marked FAILED" trail
 * required by FR-DOC-03 never persists. Saving the marker with
 * {@link Propagation#REQUIRES_NEW} suspends the caller's (rollback-only)
 * transaction and commits the row in its own, so it survives the rollback.</p>
 */
@Component
public class ExpenseFailedDocumentRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseFailedDocumentRecorder.class);

    private final ExpenseDocumentRepository documentRepository;

    public ExpenseFailedDocumentRecorder(ExpenseDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Records a failed document upload as a {@link UploadStatus#FAILED} row with a
     * {@code null} storage reference.
     *
     * <p>{@code REQUIRES_NEW} is required here: the caller's transaction is
     * rollback-only because the {@code StorageException} propagates, so the
     * FAILED audit marker must commit in its own transaction to survive
     * (FR-DOC-03, tasks.md N-04 remediation).</p>
     *
     * @param expenseClaim  the expense claim the document belongs to
     * @param fileName      the original file name of the uploaded document
     * @param fileType      the parsed document file type
     * @param fileSizeBytes the size of the uploaded content in bytes
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedDocument(
            ExpenseClaim expenseClaim,
            String fileName,
            DocumentFileType fileType,
            long fileSizeBytes) {

        ExpenseDocument document = new ExpenseDocument();
        document.setExpenseClaim(expenseClaim);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSizeBytes(fileSizeBytes);
        document.setStorageReference(null);
        document.setUploadStatus(UploadStatus.FAILED);
        document.setDeleted(false);

        documentRepository.save(document);

        logger.warn("Recorded failed document upload '{}' ({} bytes) for expense claim {}: uploadStatus=FAILED, storageReference=null",
                fileName, fileSizeBytes, expenseClaim.getId());
    }
}