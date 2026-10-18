package com.travelease.common.dto;

/**
 * A stored document's bytes plus the metadata needed to serve it (download endpoints).
 *
 * @param fileName    original client file name (sanitized again when written to a header)
 * @param contentType MIME type derived from the validated stored file type, never from client input
 * @param content     file bytes
 */
public record DocumentContent(String fileName, String contentType, byte[] content) {
}
