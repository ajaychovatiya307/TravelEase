package com.travelease.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Both storage implementations: round trip, path traversal containment, missing files (#6). */
class DocumentStorageDownloadTest {

    @TempDir
    static Path parent;

    static Stream<Arguments> implementations() throws Exception {
        Path fsRoot = Files.createDirectories(parent.resolve("fs-" + System.nanoTime()));
        return Stream.of(
                Arguments.of("LocalFileSystem", new LocalFileSystemDocumentStorageService(fsRoot.toString()), fsRoot));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementations")
    void storedBytesAreReadBack(String name, DocumentStorageService storage, Path root) {
        byte[] content = "receipt-bytes".getBytes(StandardCharsets.UTF_8);
        String reference = storage.uploadFile(content, "../../evil name.pdf", "application/pdf");

        assertThat(storage.downloadFile(reference)).isEqualTo(content);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementations")
    void traversalReferencesNeverLeaveTheRoot(String name, DocumentStorageService storage, Path root) throws Exception {
        Path secret = Files.writeString(root.getParent().resolve("secret-" + name + ".txt"), "top secret");
        String escape = "../" + secret.getFileName();

        assertThatThrownBy(() -> storage.downloadFile(escape)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.downloadFile("local://" + escape)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.downloadFile(secret.toAbsolutePath().toString()))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.downloadFile("")).isInstanceOf(StorageException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementations")
    void missingFileIsDistinguishable(String name, DocumentStorageService storage, Path root) {
        assertThatThrownBy(() -> storage.downloadFile("does-not-exist.pdf"))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @org.junit.jupiter.api.Test
    void legacyLocalReferencesStillResolveInsideTheRoot() throws Exception {
        Path root = Files.createDirectories(parent.resolve("legacy-" + System.nanoTime()));
        Files.writeString(root.resolve("abc-receipt.pdf"), "old upload");
        LocalFileSystemDocumentStorageService storage = new LocalFileSystemDocumentStorageService(root.toString());

        org.assertj.core.api.Assertions.assertThat(storage.downloadFile("local://abc-receipt.pdf"))
                .isEqualTo("old upload".getBytes(StandardCharsets.UTF_8));
    }
}
