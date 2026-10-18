package com.travelease.travelrequest.service.impl;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.dto.DocumentContent;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.DocumentStorageService;
import com.travelease.common.storage.StorageException;
import com.travelease.common.validation.DocumentConstraintValidator;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestDocument;
import com.travelease.travelrequest.mapper.TravelRequestMapper;
import com.travelease.travelrequest.repository.TravelRequestDocumentRepository;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.TravelRequestDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TravelRequestDocumentService} (tasks.md H-04).
 * Orchestrates document uploads to external storage with full validation
 * per VAL-11 and upload status management per FR-DOC-03. Constraint
 * validation is delegated to the shared document constraint validator
 * (tasks.md J-03).
 */
@Service
public class TravelRequestDocumentServiceImpl implements TravelRequestDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(TravelRequestDocumentServiceImpl.class);

    private final TravelRequestDocumentRepository documentRepository;
    private final TravelRequestRepository travelRequestRepository;
    private final DocumentStorageService storageService;
    private final TravelRequestMapper mapper;
    private final DocumentConstraintValidator documentConstraintValidator;
    private final TravelRequestFailedDocumentRecorder failedDocumentRecorder;

    public TravelRequestDocumentServiceImpl(
            TravelRequestDocumentRepository documentRepository,
            TravelRequestRepository travelRequestRepository,
            DocumentStorageService storageService,
            TravelRequestMapper mapper,
            DocumentConstraintValidator documentConstraintValidator,
            TravelRequestFailedDocumentRecorder failedDocumentRecorder) {
        this.documentRepository = documentRepository;
        this.travelRequestRepository = travelRequestRepository;
        this.storageService = storageService;
        this.mapper = mapper;
        this.documentConstraintValidator = documentConstraintValidator;
        this.failedDocumentRecorder = failedDocumentRecorder;
    }

    /**
     * Orchestrates document upload: validate → upload to storage → persist metadata → return summary.
     * On storage failure, the document is persisted with uploadStatus = FAILED, keeping the
     * owning request in Draft per FR-DOC-03.
     */
    @Override
    @Transactional
    public DocumentSummary uploadDocument(
            TravelRequest travelRequest,
            String fileName,
            String fileType,
            byte[] fileContent) throws ValidationException, StorageException {
        return uploadDocumentInternal(travelRequest, fileName, fileType, fileContent);
    }

    private DocumentSummary uploadDocumentInternal(
            TravelRequest travelRequest,
            String fileName,
            String fileType,
            byte[] fileContent) throws ValidationException, StorageException {

        logger.debug("Uploading document {} for travel request {}", fileName, travelRequest.getId());

        // Step 1: Validate file type
        DocumentFileType parsedFileType = documentConstraintValidator.validateFileType(fileType);

        // Step 2: Validate file size
        documentConstraintValidator.validateFileSize(fileContent.length);

        // Step 3: Validate document count
        documentConstraintValidator.validateDocumentCount(documentRepository.countByTravelRequestIdAndDeletedFalse(travelRequest.getId()), "travel request");

        // Step 4: Upload to external storage (may throw StorageException)
        String storageReference;
        try {
            String mimeType = documentConstraintValidator.resolveMimeType(parsedFileType);
            storageReference = storageService.uploadFile(fileContent, fileName, mimeType);
            logger.debug("File uploaded to storage with reference: {}", storageReference);
        } catch (StorageException ex) {
            // Persist the document with FAILED status in an independent REQUIRES_NEW transaction
            // (this transaction will roll back, but the FAILED audit row must survive),
            // leaving request in Draft state (FR-DOC-03, tasks.md N-04)
            failedDocumentRecorder.recordFailedDocument(travelRequest, fileName, parsedFileType, fileContent.length);
            throw new StorageException("Failed to upload document " + fileName, ex);
        }

        // Step 5: Create and persist document metadata with CONFIRMED status
        TravelRequestDocument document = createDocumentEntity(
                travelRequest, fileName, parsedFileType, fileContent.length, storageReference, UploadStatus.CONFIRMED);
        TravelRequestDocument savedDocument = documentRepository.save(document);

        logger.info("Document {} uploaded successfully for travel request {}", 
                savedDocument.getId(), travelRequest.getId());

        return mapper.toDocumentSummary(savedDocument);
    }

    @Override
    @Transactional
    public DocumentSummary uploadDocument(
            Long travelRequestId,
            Long employeeId,
            String fileName,
            long fileSizeBytes,
            String contentType,
            byte[] fileContent) throws ValidationException, StorageException {

        // Load the travel request entity
        TravelRequest travelRequest = travelRequestRepository.findByIdAndDeletedFalse(travelRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Travel request not found: " + travelRequestId));

        // Ownership validation: employee must own this request
        if (!travelRequest.getEmployeeId().equals(employeeId)) {
            throw new ValidationException("ownership", "You can only upload documents to your own travel requests");
        }

        // Normalize file type from content type (e.g., "application/pdf" -> "PDF", "image/jpeg" -> "JPG")
        String fileType = normalizeFileType(contentType);

        // Delegate to the primary uploadDocument method
        return uploadDocumentInternal(travelRequest, fileName, fileType, fileContent);
    }

    /**
     * Normalizes a MIME type or file name to a DocumentFileType string.
     * Handles common MIME types and file extensions.
     */
    private String normalizeFileType(String contentTypeOrExtension) throws ValidationException {
        if (contentTypeOrExtension == null || contentTypeOrExtension.isBlank()) {
            throw new ValidationException("fileType", "File type cannot be determined");
        }

        String lower = contentTypeOrExtension.toLowerCase();

        // Handle MIME types
        if (lower.contains("pdf")) {
            return "PDF";
        } else if (lower.contains("jpeg") || lower.contains("jpg")) {
            return "JPG";
        } else if (lower.contains("png")) {
            return "PNG";
        }

        // Handle file extensions (if passed as .pdf, .jpg, etc.)
        if (lower.endsWith(".pdf") || lower.equals("pdf")) {
            return "PDF";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.equals("jpg") || lower.equals("jpeg")) {
            return "JPG";
        } else if (lower.endsWith(".png") || lower.equals("png")) {
            return "PNG";
        }

        throw new ValidationException("fileType", "Unsupported file type: " + contentTypeOrExtension);
    }

    /**
     * Creates a TravelRequestDocument entity with metadata.
     */
    private TravelRequestDocument createDocumentEntity(
            TravelRequest travelRequest,
            String fileName,
            DocumentFileType fileType,
            long fileSizeBytes,
            String storageReference,
            UploadStatus uploadStatus) {

        TravelRequestDocument document = new TravelRequestDocument();
        document.setTravelRequest(travelRequest);
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
    public DocumentContent readContent(TravelRequestDocument document) throws StorageException {
        if (document.getUploadStatus() != UploadStatus.CONFIRMED) {
            throw new ResourceNotFoundException("Document is not available: its upload did not complete");
        }
        byte[] content = storageService.downloadFile(document.getStorageReference());
        // MIME type comes from the validated stored file type, never from client-supplied headers
        return new DocumentContent(document.getFileName(),
                documentConstraintValidator.resolveMimeType(document.getFileType()), content);
    }
}
