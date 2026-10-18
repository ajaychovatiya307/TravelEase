package com.travelease.common.validation;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Shared supporting-document constraint validator centralizing the VAL-11 rules
 * (FR-DOC-01, AC-11) reused by both the travel-request-service and expense-service
 * document upload services (tasks.md J-03).
 *
 * <p>Previously each upload service carried these checks as private helpers with
 * duplicated limits and messages; this component is the single source of truth so
 * the two services cannot drift apart. Error messages and failing-field names are
 * part of the API contract (api-contract.md §8) and are kept byte-identical to the
 * pre-J-03 behaviour of the two services.</p>
 */
@Component
public class DocumentConstraintValidator {

    /** Maximum file size: 5 MB (VAL-11, FR-DOC-01). */
    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    /** Maximum number of documents per owning record (VAL-11, FR-DOC-01). */
    public static final int MAX_DOCUMENTS = 5;

    /** Allowed file types (VAL-11). */
    private static final List<DocumentFileType> ALLOWED_FILE_TYPES = List.of(
            DocumentFileType.PDF,
            DocumentFileType.JPG,
            DocumentFileType.PNG
    );

    /** MIME type mappings for storage uploads (tasks.md J-03). */
    private static final Map<DocumentFileType, String> MIME_TYPE_MAP = Map.of(
            DocumentFileType.PDF, "application/pdf",
            DocumentFileType.JPG, "image/jpeg",
            DocumentFileType.PNG, "image/png"
    );

    /**
     * Parses and validates the file type string (VAL-11, FR-DOC-01, AC-11).
     * Accepts case-insensitive values (e.g. {@code "pdf"}, {@code "Jpg"}).
     *
     * @param fileType raw file type string from the upload request
     * @return the matching {@link DocumentFileType}
     * @throws ValidationException if the file type is not PDF, JPG, or PNG
     */
    public DocumentFileType validateFileType(String fileType) throws ValidationException {
        try {
            DocumentFileType parsedType = DocumentFileType.valueOf(fileType.toUpperCase());
            if (!ALLOWED_FILE_TYPES.contains(parsedType)) {
                throw new ValidationException("fileType",
                        "File type not allowed. Only PDF, JPG, PNG are supported.");
            }
            return parsedType;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("fileType",
                    "Invalid file type: " + fileType + ". Only PDF, JPG, PNG are supported.");
        }
    }

    /**
     * Validates file size against the VAL-11 limit (FR-DOC-01, AC-11): the file
     * must be at least 1 byte and at most {@link #MAX_FILE_SIZE_BYTES} (5 MB).
     *
     * @param fileSizeBytes size of the uploaded file in bytes
     * @throws ValidationException if the file exceeds 5 MB or is empty
     */
    public void validateFileSize(long fileSizeBytes) throws ValidationException {
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("file",
                    "File size exceeds maximum allowed (5 MB)");
        }
        if (fileSizeBytes == 0) {
            throw new ValidationException("file",
                    "File is empty");
        }
    }

    /**
     * Validates that the owning record does not already hold
     * {@link #MAX_DOCUMENTS} documents (VAL-11, FR-DOC-01, AC-11).
     *
     * @param currentCount number of existing (non-deleted) documents on the owner,
     *                     as fetched by the calling service
     * @param ownerLabel   owner label for the error message, e.g.
     *                     {@code "travel request"} or {@code "expense claim"}
     * @throws ValidationException if the document count limit is reached
     */
    public void validateDocumentCount(long currentCount, String ownerLabel) throws ValidationException {
        if (currentCount >= MAX_DOCUMENTS) {
            throw new ValidationException("documentIds",
                    "Maximum number of documents (5) reached for this " + ownerLabel);
        }
    }

    /**
     * Resolves the MIME type used for storage uploads, centralizing the mapping
     * previously duplicated in the two document upload services (tasks.md J-03).
     *
     * @param fileType parsed file type; must not be null
     * @return the MIME type, e.g. {@code "application/pdf"}, {@code "image/jpeg"},
     *         {@code "image/png"}
     * @throws IllegalArgumentException if {@code fileType} is null or unsupported
     */
    /**
     * Derives the declared file type for an upload whose client did not send one explicitly,
     * from the multipart content type, falling back to the file extension. The result is still
     * checked by {@link #validateFileType(String)}.
     *
     * @return {@code PDF}, {@code JPG}, {@code PNG}, or the raw extension/content type when unknown
     *         (so validation reports it as unsupported)
     */
    public static String deriveFileType(String contentType, String fileName) {
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT).trim();
        if (type.startsWith("application/pdf")) {
            return DocumentFileType.PDF.name();
        }
        if (type.startsWith("image/jpeg") || type.startsWith("image/jpg")) {
            return DocumentFileType.JPG.name();
        }
        if (type.startsWith("image/png")) {
            return DocumentFileType.PNG.name();
        }
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (extension) {
            case "pdf" -> DocumentFileType.PDF.name();
            case "jpg", "jpeg" -> DocumentFileType.JPG.name();
            case "png" -> DocumentFileType.PNG.name();
            default -> extension.isEmpty() ? (type.isEmpty() ? "unknown" : type) : extension;
        };
    }

    public String resolveMimeType(DocumentFileType fileType) {
        if (fileType == null) {
            throw new IllegalArgumentException("fileType must not be null");
        }
        String mimeType = MIME_TYPE_MAP.get(fileType);
        if (mimeType == null) {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
        return mimeType;
    }
}