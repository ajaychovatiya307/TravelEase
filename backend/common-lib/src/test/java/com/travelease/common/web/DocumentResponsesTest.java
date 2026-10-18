package com.travelease.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.travelease.common.dto.DocumentContent;
import com.travelease.common.validation.DocumentConstraintValidator;

class DocumentResponsesTest {

    @Test
    void attachmentHeadersAreSafe() {
        ResponseEntity<byte[]> response = DocumentResponses.attachment(
                new DocumentContent("../../etc/passwd\"\r\nX-Injected: 1.pdf", "application/pdf", new byte[] {1, 2}));

        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).hasToString("application/pdf");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getCacheControl()).contains("no-store");
        String disposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment;").doesNotContain("../").doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void safeFileNameStripsPathsAndControls() {
        assertThat(DocumentResponses.safeFileName("C:\\Users\\a\\receipt.pdf")).isEqualTo("receipt.pdf");
        assertThat(DocumentResponses.safeFileName("  ")).isEqualTo("document");
        assertThat(DocumentResponses.safeFileName(null)).isEqualTo("document");
    }

    @Test
    void fileTypeIsDerivedFromContentTypeOrExtension() {
        assertThat(DocumentConstraintValidator.deriveFileType("application/pdf", "x")).isEqualTo("PDF");
        assertThat(DocumentConstraintValidator.deriveFileType("image/jpeg", "x")).isEqualTo("JPG");
        assertThat(DocumentConstraintValidator.deriveFileType("image/png;charset=x", "x")).isEqualTo("PNG");
        assertThat(DocumentConstraintValidator.deriveFileType("application/octet-stream", "scan.JPEG")).isEqualTo("JPG");
        assertThat(DocumentConstraintValidator.deriveFileType(null, "notes.txt")).isEqualTo("txt");
        assertThat(DocumentConstraintValidator.deriveFileType(null, null)).isEqualTo("unknown");
    }
}
