package com.travelease.expense.service;

import com.travelease.common.dto.DocumentContent;
import com.travelease.common.dto.DocumentSummary;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseDocument;

/**
 * Service interface for managing expense claim document uploads (tasks.md H-06;
 * spec.md FR-DOC-01–03; Constitution §11).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Orchestrate document uploads to external object storage (Q16)</li>
 *   <li>Validate file type, size, and count per VAL-11</li>
 *   <li>Persist document metadata to the relational database</li>
 *   <li>Maintain upload status (PENDING → CONFIRMED/FAILED)</li>
 *   <li>Leave owning claim in Draft state on upload failure (FR-DOC-03, Q23)</li>
 * </ul>
 *
 * <p>All methods are transactional (per Constitution §9); external storage operations
 * are coordinated within the transaction boundary, and failures are captured without
 * rolling back the owning claim.</p>
 */
public interface ExpenseDocumentService {

    /**
     * Uploads a document to an expense claim with full orchestration.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate file type (PDF/JPG/PNG only), size (&lt;= 5 MB), and count (&lt;= 5 per claim)</li>
     *   <li>Upload binary content to external storage</li>
     *   <li>Persist document metadata with uploadStatus = CONFIRMED</li>
     *   <li>Return the document summary to the caller</li>
     * </ol>
     *
     * <p>On any failure (validation, storage, persistence), the owning expense claim
     * remains in its current state (typically Draft for in-progress submission); no
     * automatic status change occurs per FR-DOC-03.</p>
     *
     * @param expenseClaim the owning expense claim; assumed non-null and in database
     * @param fileName display name of the file; assumed non-blank
     * @param fileType file type (PDF, JPG, PNG); validated per VAL-11
     * @param fileContent binary file content; assumed non-empty and within size limits
     * @return the document metadata (id, fileName, fileType, etc.)
     * @throws com.travelease.common.exception.ValidationException if file type is invalid, 
     *         size exceeds limit, or document count exceeds 5 per claim (VAL-11)
     * @throws com.travelease.common.storage.StorageException if external storage upload fails; 
     *         the owning claim remains in Draft state with the document marked FAILED (FR-DOC-03)
     */
    DocumentSummary uploadDocument(
            ExpenseClaim expenseClaim,
            String fileName,
            String fileType,
            byte[] fileContent);

    /**
     * Uploads a document to an expense claim identified by ID, enforcing
     * owner-only access (tasks.md H-06; spec.md FR-DOC-01–03; NFR-10).
     *
     * <p>Loads the claim by ID (non-deleted only), verifies the authenticated
     * employee is the claim owner, and delegates the heavy lifting (file
     * type/size/count validation per VAL-11, storage upload, and document
     * metadata persistence) to the claim-based overload. Mirrors the
     * travel-request upload pattern; moving claim loading and ownership
     * enforcement from the Controller into the Service keeps the controller
     * thin per Constitution §2/§3.</p>
     *
     * @param expenseClaimId the ID of the owning expense claim
     * @param employeeId the authenticated caller's employee ID (JWT subject)
     * @param fileName display name of the file; assumed non-blank
     * @param fileType file type (PDF, JPG, PNG); validated per VAL-11
     * @param fileContent binary file content; assumed non-empty and within size limits
     * @return the document metadata (id, fileName, fileType, etc.)
     * @throws com.travelease.common.exception.ResourceNotFoundException if the claim
     *         does not exist or has been soft-deleted
     * @throws com.travelease.common.exception.ForbiddenActionException if the caller
     *         is not the claim owner (NFR-10)
     * @throws com.travelease.common.exception.ValidationException if file type is invalid,
     *         size exceeds limit, or document count exceeds 5 per claim (VAL-11)
     * @throws com.travelease.common.storage.StorageException if external storage upload fails;
     *         the owning claim remains in Draft state with the document marked FAILED (FR-DOC-03)
     */
    DocumentSummary uploadDocument(
            Long expenseClaimId,
            Long employeeId,
            String fileName,
            String fileType,
            byte[] fileContent);

    /**
     * Reads a CONFIRMED document's stored bytes. Callers must authorize access to the parent claim
     * first; this method performs no authorization of its own.
     *
     * @throws com.travelease.common.exception.ResourceNotFoundException if the upload never completed
     * @throws com.travelease.common.storage.StoredFileNotFoundException if the bytes are missing
     */
    DocumentContent readContent(ExpenseDocument document);
}
