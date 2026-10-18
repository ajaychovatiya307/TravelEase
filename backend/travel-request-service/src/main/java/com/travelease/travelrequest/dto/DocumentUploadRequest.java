package com.travelease.travelrequest.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Multipart request for uploading a supporting document to a travel request
 * (tasks.md E-02; api-contract.md §5.5; spec.md FR-DOC-01, AC-11, AC-12).
 *
 * <p>Models the multipart form at the web boundary: the binary {@code file} part
 * and the {@code fileName} form field. The file constraints of VAL-11
 * (PDF/JPG/PNG only, max 5 MB per file, max 5 documents per request) are not
 * purely declarative, so they are enforced at the Service layer via the shared
 * document constraint validator (tasks.md J-03) rather than by Bean Validation
 * here. The Controller extracts the neutral file metadata/bytes from the
 * {@link MultipartFile} before calling the Service, keeping HTTP-specific types
 * out of the Service layer (Constitution §4). Binary content is never persisted -
 * only metadata is stored (FR-DOC-02).</p>
 */
public record DocumentUploadRequest(

        /** The uploaded binary part; required. */
        @NotNull(message = "file is required")
        MultipartFile file,

        /** Display name persisted with the document metadata; required, non-blank. */
        @NotBlank(message = "fileName must not be blank")
        String fileName) {
}