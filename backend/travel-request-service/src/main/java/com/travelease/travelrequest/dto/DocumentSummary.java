package com.travelease.travelrequest.dto;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;

/**
 * Metadata summary of an uploaded document, embedded in travel-request responses
 * (tasks.md E-01; api-contract.md §5.6; spec.md FR-DOC-02).
 *
 * <p>Metadata only - the binary content lives in external object storage and is
 * referenced by {@code storageReference}; never binary content (FR-DOC-02).</p>
 */

public record DocumentSummary(
        String id,
        String fileName,
        DocumentFileType fileType,
        long fileSizeBytes, 
        String storageReference,
        UploadStatus uploadStatus) {
}