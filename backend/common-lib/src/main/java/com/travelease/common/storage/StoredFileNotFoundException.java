package com.travelease.common.storage;

/**
 * Raised when a document's metadata exists but its stored bytes do not (deleted or never written).
 * Mapped to HTTP 404 by the services, unlike other {@link StorageException}s (503).
 */
public class StoredFileNotFoundException extends StorageException {

    public StoredFileNotFoundException(String message) {
        super(message);
    }

    public StoredFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
