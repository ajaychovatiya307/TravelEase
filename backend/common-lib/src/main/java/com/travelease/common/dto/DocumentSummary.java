package com.travelease.common.dto;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;

/**
 * Immutable response DTO representing document metadata (api-contract.md §5.6).
 * Used in both {@code TravelRequestResponse} and {@code ExpenseClaimResponse}.
 * 
 * <p>Carries only metadata; actual binary content is never returned (FR-DOC-02).
 * {@code storageReference} identifies the object in external storage.</p>
 */
public class DocumentSummary {

    private String id;
    private String fileName;
    private DocumentFileType fileType;
    private long fileSizeBytes;
    private String storageReference;
    private UploadStatus uploadStatus;

    // Constructors
    public DocumentSummary() {
    }

    public DocumentSummary(String id, String fileName, DocumentFileType fileType, long fileSizeBytes,
            String storageReference, UploadStatus uploadStatus) {
        this.id = id;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSizeBytes = fileSizeBytes;
        this.storageReference = storageReference;
        this.uploadStatus = uploadStatus;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public DocumentFileType getFileType() {
        return fileType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFileType(DocumentFileType fileType) {
        this.fileType = fileType;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public void setStorageReference(String storageReference) {
        this.storageReference = storageReference;
    }

    public void setUploadStatus(UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }
}
