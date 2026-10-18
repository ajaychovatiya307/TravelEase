package com.travelease.common.storage;

/**
 * Abstraction for external object-storage operations (S3, GCS, etc.)
 * Supporting document upload orchestration per Constitution §11 (FR-DOC-02, Q16).
 *
 * <p>Implementations handle the actual binary storage; this service is called
 * by document upload services to confirm storage before committing metadata
 * to the relational database (upload orchestration per tasks.md H-04/H-06).
 * Failures are not retried at this layer - they trigger a FAILED upload status
 * and the owning request remains in Draft state per FR-DOC-03.</p>
 */
public interface DocumentStorageService {

    /**
     * Uploads binary content to external object storage and returns a storage reference.
     * Called during document upload orchestration (tasks.md H-04/H-06) within a transaction;
     * failure must be caught and converted to an upload status of FAILED, leaving the
     * owning record in Draft state per FR-DOC-03.
     *
     * @param fileContent binary file content; assumed non-empty
     * @param fileName original file name (for logging/reference only); assumed non-blank
     * @param contentType MIME type (e.g., "application/pdf", "image/jpeg", "image/png")
     * @return a storage reference string that can be used to retrieve/delete the file later
     * @throws StorageException if the upload fails (e.g., network error, quota exceeded)
     */
    String uploadFile(byte[] fileContent, String fileName, String contentType) throws StorageException;

    /**
     * Deletes a file from external object storage via its storage reference.
     * Called during cleanup or soft-delete operations. Failures are logged but not
     * propagated (best-effort deletion).
     *
     * @param storageReference the reference returned by {@link #uploadFile}; assumed non-blank
     * @throws StorageException if deletion fails critically
     */
    void deleteFile(String storageReference) throws StorageException;

    /**
     * Reads a stored file's bytes. Implementations must only read inside their storage root,
     * whatever the reference contains.
     *
     * @param storageReference reference previously returned by {@link #uploadFile}
     * @return the file content
     * @throws StoredFileNotFoundException if no file exists for the reference
     * @throws StorageException if the reference is invalid or the file cannot be read
     */
    byte[] downloadFile(String storageReference) throws StorageException;

    /**
     * Retrieves file metadata (size, content type) from external object storage.
     * Used for validation and audit purposes.
     *
     * @param storageReference the reference returned by {@link #uploadFile}; assumed non-blank
     * @return file metadata (size in bytes, content type, etc.)
     * @throws StorageException if the file is not found or retrieval fails
     */
    FileMetadata getFileMetadata(String storageReference) throws StorageException;

    /**
     * Metadata about a file stored in external object storage.
     */
    record FileMetadata(
            long sizeBytes,
            String contentType,
            long lastModifiedTimestamp) {
    }
}
