package com.travelease.travelrequest.service;

import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.StorageException;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.common.dto.DocumentContent;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestDocument;

/**
 * Service interface for managing travel request document uploads (tasks.md H-04;
 * spec.md FR-DOC-01–03; Constitution §11).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Orchestrate document uploads to external object storage (Q16)</li>
 *   <li>Validate file type, size, and count per VAL-11</li>
 *   <li>Persist document metadata to the relational database</li>
 *   <li>Maintain upload status (PENDING → CONFIRMED/FAILED)</li>
 *   <li>Leave owning request in Draft state on upload failure (FR-DOC-03, Q23)</li>
 * </ul>
 *
 * <p>All methods are transactional (per Constitution §9); external storage operations
 * are coordinated within the transaction boundary, and failures are captured without
 * rolling back the owning request.</p>
 */
public interface TravelRequestDocumentService {

    /**
     * Uploads a document to a travel request with full orchestration.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate file type (PDF/JPG/PNG only), size (&lt;= 5 MB), and count (&lt;= 5 per request)</li>
     *   <li>Upload binary content to external storage</li>
     *   <li>Persist document metadata with uploadStatus = CONFIRMED</li>
     *   <li>Return the document summary to the caller</li>
     * </ol>
     *
     * <p>On any failure (validation, storage, persistence), the owning travel request
     * remains in its current state (typically Draft for in-progress submission); no
     * automatic status change occurs per FR-DOC-03.</p>
     *
     * @param travelRequest the owning travel request; assumed non-null and in database
     * @param fileName display name of the file; assumed non-blank
     * @param fileType file type (PDF, JPG, PNG); validated per VAL-11
     * @param fileContent binary file content; assumed non-empty and within size limits
     * @return the document metadata (id, fileName, fileType, etc.)
     * @throws ValidationException if file type is invalid, size exceeds limit,
     *         or document count exceeds 5 per request (VAL-11)
     * @throws StorageException if external storage upload fails; the owning request
     *         remains in Draft state with the document marked FAILED (FR-DOC-03)
     */
    DocumentSummary uploadDocument(
            TravelRequest travelRequest,
            String fileName,
            String fileType,
            byte[] fileContent) throws ValidationException, StorageException;

    /**
     * Uploads a document to a travel request by ID (controller-friendly overload).
     *
     * <p>Loads the travel request entity, performs ownership validation, and delegates
     * to the primary {@link #uploadDocument(TravelRequest, String, String, byte[])}
     * method for orchestration.</p>
     *
     * @param travelRequestId primary key of the travel request
     * @param employeeId the authenticated employee performing the upload (ownership check)
     * @param fileName display name of the file
     * @param fileSizeBytes size of the file in bytes (for validation)
     * @param contentType the MIME type or file extension (e.g., "application/pdf", "image/jpeg")
     * @param fileContent binary file content
     * @return the document metadata (id, fileName, fileType, etc.)
     * @throws com.travelease.common.exception.ResourceNotFoundException
     *         if the travel request does not exist or has been soft-deleted
     * @throws ValidationException if ownership check fails, or if file type is invalid,
     *         size exceeds limit, or document count exceeds 5 per request (VAL-11)
     * @throws StorageException if external storage upload fails
     */
    DocumentSummary uploadDocument(
            Long travelRequestId,
            Long employeeId,
            String fileName,
            long fileSizeBytes,
            String contentType,
            byte[] fileContent) throws ValidationException, StorageException;

    /**
     * Reads a CONFIRMED document's stored bytes. Callers must authorize access to the parent
     * travel request first; this method performs no authorization of its own.
     *
     * @throws com.travelease.common.exception.ResourceNotFoundException if the upload never completed
     * @throws com.travelease.common.storage.StoredFileNotFoundException if the bytes are missing
     */
    DocumentContent readContent(TravelRequestDocument document) throws StorageException;
}
