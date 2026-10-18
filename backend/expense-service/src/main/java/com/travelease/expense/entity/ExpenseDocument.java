package com.travelease.expense.entity;

import com.travelease.common.audit.Auditable;
import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Metadata for a document attached to an expense claim (data-model.md §4.2;
 * spec.md FR-DOC-01–03). Only metadata is persisted - the binary content lives in
 * external object storage and is referenced by {@code storageReference} (FR-DOC-02).
 *
 * <p>Extends {@link Auditable} for the shared audit/soft-delete fields.</p>
 */
@Entity
@Table(name = "expense_document")
public class ExpenseDocument extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Owning expense claim (same service/database - the only JPA association on this entity). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_claim_id", nullable = false)
    private ExpenseClaim expenseClaim;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private DocumentFileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    /** Pointer into external object storage; never binary content (FR-DOC-02). */
    @Column(name = "storage_reference", nullable = false)
    private String storageReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ExpenseClaim getExpenseClaim() {
        return expenseClaim;
    }

    public void setExpenseClaim(ExpenseClaim expenseClaim) {
        this.expenseClaim = expenseClaim;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public DocumentFileType getFileType() {
        return fileType;
    }

    public void setFileType(DocumentFileType fileType) {
        this.fileType = fileType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public void setStorageReference(String storageReference) {
        this.storageReference = storageReference;
    }

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }
}