package com.travelease.common.web;

import java.nio.charset.StandardCharsets;

import com.travelease.common.dto.DocumentContent;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Builds safe HTTP responses for document downloads, shared by the document-owning services. */
public final class DocumentResponses {

    private DocumentResponses() {
        // utility holder - not instantiable
    }

    /**
     * Serves the document as an attachment: the browser never renders it in the app's origin,
     * {@code nosniff} stops content-type guessing, and private documents are not cached.
     */
    public static ResponseEntity<byte[]> attachment(DocumentContent document) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(safeFileName(document.fileName()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(document.content());
    }

    /** Strips path components and control characters from a stored client file name. */
    static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"]", "_").trim();
        return base.isEmpty() ? "document" : base;
    }
}
