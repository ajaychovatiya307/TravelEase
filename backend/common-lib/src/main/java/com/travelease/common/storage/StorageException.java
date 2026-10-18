package com.travelease.common.storage;

/**
 * Exception thrown when document storage operations fail (upload, delete, retrieve).
 * Callers should treat this as a transient or permanent failure and handle accordingly
 * per the business context (e.g., upload failure → draft retention per FR-DOC-03).
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
