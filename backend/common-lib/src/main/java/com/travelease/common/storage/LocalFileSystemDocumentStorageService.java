package com.travelease.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default {@link DocumentStorageService} backed by the local filesystem (tasks.md H-04/H-06).
 *
 * <p>No real object-storage provider (S3/GCS) is wired yet for this MVP (Q16 defers the
 * concrete provider to infrastructure design), so every service previously had no bean
 * satisfying {@code DocumentStorageService} and failed to start. This dev/local-ready
 * implementation keeps the application runnable end-to-end; swap in an S3/GCS-backed
 * implementation later without touching callers (interface-only dependency).</p>
 */
@Component
public class LocalFileSystemDocumentStorageService implements DocumentStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileSystemDocumentStorageService.class);

    private final Path baseDirectory;

    /** Reference prefix written by the former LocalDiskDocumentStorageService; still accepted on read. */
    private static final String LEGACY_REFERENCE_PREFIX = "local://";

    /**
     * @param baseDir explicit storage root ({@code travelease.storage.base-dir}, env TRAVELEASE_STORAGE_DIR).
     *        Older property names ({@code app.storage.base-dir}, {@code document-storage.local-path}) are
     *        honoured as fallbacks; the default is an absolute directory under the user's home, never a
     *        path relative to the process working directory. Uploads and downloads use this same root.
     */
    public LocalFileSystemDocumentStorageService(
            @Value("${travelease.storage.base-dir:${app.storage.base-dir:${document-storage.local-path:${user.home}/.travelease/documents}}}")
            String baseDir) {
        this.baseDirectory = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException ex) {
            throw new StorageException("Unable to initialize local document storage directory: " + this.baseDirectory, ex);
        }
        LOGGER.info("Document storage root: {}", this.baseDirectory);
    }

    @Override
    public String uploadFile(byte[] fileContent, String fileName, String contentType) throws StorageException {
        String storageReference = UUID.randomUUID() + "-" + sanitize(fileName);
        Path target = baseDirectory.resolve(storageReference);
        try {
            Files.write(target, fileContent);
        } catch (IOException ex) {
            throw new StorageException("Failed to store document " + fileName, ex);
        }
        LOGGER.debug("Stored document {} at {}", fileName, target);
        return storageReference;
    }

    @Override
    public void deleteFile(String storageReference) throws StorageException {
        try {
            Files.deleteIfExists(resolve(storageReference));
        } catch (IOException ex) {
            // best-effort deletion per DocumentStorageService contract
            LOGGER.warn("Failed to delete document {}: {}", storageReference, ex.getMessage());
        }
    }

    @Override
    public byte[] downloadFile(String storageReference) throws StorageException {
        Path path = resolve(storageReference);
        if (!Files.isRegularFile(path)) {
            throw new StoredFileNotFoundException("Stored file not found");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new StorageException("Failed to read stored file", ex);
        }
    }

    @Override
    public FileMetadata getFileMetadata(String storageReference) throws StorageException {
        Path file = resolve(storageReference);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            String contentType = Files.probeContentType(file);
            return new FileMetadata(attributes.size(), contentType, attributes.lastModifiedTime().toMillis());
        } catch (NoSuchFileException ex) {
            throw new StorageException("Document not found: " + storageReference, ex);
        } catch (IOException ex) {
            throw new StorageException("Failed to read document metadata: " + storageReference, ex);
        }
    }

    /** Resolves a storage reference while rejecting path traversal outside the base directory. */
    private Path resolve(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            throw new StorageException("Invalid storage reference");
        }
        String name = storageReference.startsWith(LEGACY_REFERENCE_PREFIX)
                ? storageReference.substring(LEGACY_REFERENCE_PREFIX.length())
                : storageReference;
        Path resolved = baseDirectory.resolve(name).normalize();
        if (!resolved.startsWith(baseDirectory) || resolved.equals(baseDirectory)) {
            throw new StorageException("Invalid storage reference: " + storageReference);
        }
        return resolved;
    }

    private String sanitize(String fileName) {
        return fileName == null ? "file" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
