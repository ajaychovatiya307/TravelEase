package com.travelease.travelrequest.service;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.DocumentStorageService;
import com.travelease.common.storage.StorageException;
import com.travelease.common.validation.DocumentConstraintValidator;
import com.travelease.travelrequest.dto.DocumentSummary;
import com.travelease.travelrequest.entity.TravelRequest;
import com.travelease.travelrequest.entity.TravelRequestDocument;
import com.travelease.travelrequest.mapper.TravelRequestMapper;
import com.travelease.travelrequest.repository.TravelRequestDocumentRepository;
import com.travelease.travelrequest.repository.TravelRequestRepository;
import com.travelease.travelrequest.service.impl.TravelRequestDocumentServiceImpl;
import com.travelease.travelrequest.service.impl.TravelRequestFailedDocumentRecorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TravelRequestDocumentServiceImpl} (tasks.md L-04), covering
 * supporting-document upload orchestration with positive and negative cases per
 * Constitution §10:
 *
 * <ul>
 *   <li>valid PDF / JPG / PNG upload: metadata is persisted with
 *       {@code CONFIRMED} status and the storage reference, the correct MIME type
 *       ({@code application/pdf} / {@code image/jpeg} / {@code image/png}) is passed
 *       to storage and the mapped {@link DocumentSummary} is returned
 *       (FR-DOC-01, FR-DOC-02, AC-11)</li>
 *   <li>file above the 5 MB limit → {@link ValidationException} (field
 *       {@code "file"}) before storage or persistence happens (VAL-11, AC-11)</li>
 *   <li>empty (0-byte) file → {@link ValidationException} (field {@code "file"})
 *       (VAL-11, AC-11)</li>
 *   <li>unsupported file type → {@link ValidationException} (field {@code "fileType"})
 *       before storage or persistence happens (VAL-11, AC-11)</li>
 *   <li>6th document on the same travel request → {@link ValidationException}
 *       (field {@code "documentIds"}) before storage or persistence happens
 *       (VAL-11, AC-11)</li>
 *   <li>storage write failure → the {@link StorageException} propagates (which keeps
 *       the owning request in Draft state) and the failure is recorded via
 *       {@link TravelRequestFailedDocumentRecorder#recordFailedDocument} in an
 *       independent REQUIRES_NEW transaction so the FAILED audit row survives the
 *       caller's rollback (FR-DOC-03, AC-12, tasks.md N-04)</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so the repository, storage service and mapper are mocked with
 * Mockito and the service is injected via {@code @InjectMocks} - no Spring context
 * is needed. The shared {@link DocumentConstraintValidator} (tasks.md J-03) is a
 * spy on a real instance so the actual VAL-11 rules (5 MB limit, non-empty check,
 * max-5-documents, type allow-list and MIME mapping) run under test instead of
 * being stubbed away.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TravelRequestDocumentServiceImpl uploadDocument (tasks.md L-04; spec.md FR-DOC-01, FR-DOC-02, FR-DOC-03; VAL-11; AC-11, AC-12)")
class TravelRequestDocumentServiceTest {

    private static final Long TRAVEL_REQUEST_ID = 10L;
    private static final Long EMPLOYEE_ID = 1L;
    private static final String FILE_NAME = "itinerary.pdf";
    private static final String STORAGE_REFERENCE = "store-123";

    @Mock
    private TravelRequestDocumentRepository documentRepository;

    @Mock
    private TravelRequestRepository travelRequestRepository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private TravelRequestMapper travelRequestMapper;

    /** Real (spied) validator so the actual VAL-11 rules run under test (tasks.md J-03). */
    @Spy
    private DocumentConstraintValidator documentConstraintValidator = new DocumentConstraintValidator();

    @Mock
    private TravelRequestFailedDocumentRecorder failedDocumentRecorder;

    @InjectMocks
    private TravelRequestDocumentServiceImpl documentService;

    private static TravelRequest travelRequest() {
        TravelRequest request = new TravelRequest();
        request.setId(TRAVEL_REQUEST_ID);
        request.setEmployeeId(EMPLOYEE_ID);
        request.setDestination("Mumbai");
        return request;
    }

    private static byte[] content(int sizeBytes) {
        byte[] bytes = new byte[sizeBytes];
        Arrays.fill(bytes, (byte) 1);
        return bytes;
    }

    @Test
    @DisplayName("loads an owned request and uploads using a MIME content type")
    void uploadsByTravelRequestId() {
        TravelRequest request = travelRequest();
        byte[] fileContent = content(1024);
        DocumentSummary expected = new DocumentSummary(
                "1", FILE_NAME, DocumentFileType.PDF, 1024L, STORAGE_REFERENCE, UploadStatus.CONFIRMED);

        when(travelRequestRepository.findByIdAndDeletedFalse(TRAVEL_REQUEST_ID))
                .thenReturn(java.util.Optional.of(request));
        when(documentRepository.countByTravelRequestIdAndDeletedFalse(TRAVEL_REQUEST_ID)).thenReturn(0L);
        when(storageService.uploadFile(fileContent, FILE_NAME, "application/pdf"))
                .thenReturn(STORAGE_REFERENCE);
        when(documentRepository.save(any(TravelRequestDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(travelRequestMapper.toDocumentSummary(any(TravelRequestDocument.class)))
                .thenReturn(expected);

        DocumentSummary actual = documentService.uploadDocument(
                TRAVEL_REQUEST_ID, EMPLOYEE_ID, FILE_NAME, fileContent.length,
                "application/pdf", fileContent);

        assertThat(actual).isSameAs(expected);
        verify(travelRequestRepository).findByIdAndDeletedFalse(TRAVEL_REQUEST_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {".pdf", "pdf", ".jpg", "jpeg", ".png", "png"})
    @DisplayName("normalizes supported file extensions")
    void uploadsBySupportedFileExtension(String fileType) {
        TravelRequest request = travelRequest();
        byte[] fileContent = content(1024);
        String expectedType = fileType.toLowerCase().contains("pdf") ? "PDF"
                : fileType.toLowerCase().contains("jpg") || fileType.toLowerCase().contains("jpeg")
                        ? "JPG" : "PNG";
        String mimeType = expectedType.equals("PDF") ? "application/pdf"
                : expectedType.equals("JPG") ? "image/jpeg" : "image/png";

        when(travelRequestRepository.findByIdAndDeletedFalse(TRAVEL_REQUEST_ID))
                .thenReturn(java.util.Optional.of(request));
        when(documentRepository.countByTravelRequestIdAndDeletedFalse(TRAVEL_REQUEST_ID)).thenReturn(0L);
        when(storageService.uploadFile(fileContent, FILE_NAME, mimeType)).thenReturn(STORAGE_REFERENCE);
        when(documentRepository.save(any(TravelRequestDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(travelRequestMapper.toDocumentSummary(any(TravelRequestDocument.class)))
                .thenReturn(null);

        documentService.uploadDocument(
                TRAVEL_REQUEST_ID, EMPLOYEE_ID, FILE_NAME, fileContent.length, fileType, fileContent);

        verify(storageService).uploadFile(fileContent, FILE_NAME, mimeType);
    }

    @Test
    @DisplayName("rejects an upload for a request owned by another employee")
    void rejectsUploadForForeignRequest() {
        TravelRequest request = travelRequest();
        byte[] fileContent = content(1024);
        when(travelRequestRepository.findByIdAndDeletedFalse(TRAVEL_REQUEST_ID))
                .thenReturn(java.util.Optional.of(request));
        assertThatThrownBy(() -> uploadForeignRequest(99L, fileContent))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(storageService, documentRepository);
    }

    @Test
    @DisplayName("rejects an upload when the request cannot be found")
    void rejectsUploadForMissingRequest() {
        when(travelRequestRepository.findByIdAndDeletedFalse(TRAVEL_REQUEST_ID))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> uploadMissingRequest(content(1024)))
                .isInstanceOf(com.travelease.common.exception.ResourceNotFoundException.class);
    }

    private void uploadMissingRequest(byte[] fileContent) {
        documentService.uploadDocument(
                TRAVEL_REQUEST_ID, EMPLOYEE_ID, FILE_NAME, fileContent.length,
                "application/pdf", fileContent);
    }

    private void uploadForeignRequest(Long employeeId, byte[] fileContent) {
        documentService.uploadDocument(
                TRAVEL_REQUEST_ID, employeeId, FILE_NAME, fileContent.length,
                "application/pdf", fileContent);
    }

    @Nested
    @DisplayName("uploadDocument — positive cases (FR-DOC-01, FR-DOC-02, AC-11)")
    class UploadPositiveCases {

        @Test
        @DisplayName("uploads a valid PDF: CONFIRMED metadata with the storage reference is saved and the mapped summary is returned")
        void uploadsValidPdf() {
            TravelRequest request = travelRequest();
            byte[] fileContent = content(1024);
            DocumentSummary expected = new DocumentSummary(
                    "1", FILE_NAME, DocumentFileType.PDF, 1024L, STORAGE_REFERENCE, UploadStatus.CONFIRMED);

            when(documentRepository.countByTravelRequestIdAndDeletedFalse(TRAVEL_REQUEST_ID)).thenReturn(0L);
            when(storageService.uploadFile(fileContent, FILE_NAME, "application/pdf")).thenReturn(STORAGE_REFERENCE);
            when(documentRepository.save(any(TravelRequestDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(travelRequestMapper.toDocumentSummary(any(TravelRequestDocument.class))).thenReturn(expected);

            DocumentSummary actual = documentService.uploadDocument(request, FILE_NAME, "PDF", fileContent);

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<TravelRequestDocument> captor = ArgumentCaptor.forClass(TravelRequestDocument.class);
            verify(documentRepository).save(captor.capture());
            TravelRequestDocument saved = captor.getValue();
            assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.CONFIRMED);
            assertThat(saved.getStorageReference()).isEqualTo(STORAGE_REFERENCE);
            assertThat(saved.getFileType()).isEqualTo(DocumentFileType.PDF);
            assertThat(saved.getFileName()).isEqualTo(FILE_NAME);
            assertThat(saved.getFileSizeBytes()).isEqualTo(1024L);
            assertThat(saved.getTravelRequest()).isSameAs(request);
            assertThat(saved.isDeleted()).isFalse();

            verify(storageService).uploadFile(fileContent, FILE_NAME, "application/pdf");
        }

        @ParameterizedTest(name = "uploads a valid {0}: CONFIRMED metadata saved with the {1} MIME type")
        @CsvSource({"JPG, image/jpeg", "PNG, image/png"})
        @DisplayName("uploads a valid {0}: CONFIRMED metadata saved with the correct {1} MIME type")
        void uploadsValidJpgAndPng(String fileType, String mimeType) {
            TravelRequest request = travelRequest();
            String fileName = "receipt." + fileType.toLowerCase();
            byte[] fileContent = content(2048);
            DocumentSummary expected = new DocumentSummary(
                    "2", fileName, DocumentFileType.valueOf(fileType), 2048L, STORAGE_REFERENCE, UploadStatus.CONFIRMED);

            when(documentRepository.countByTravelRequestIdAndDeletedFalse(TRAVEL_REQUEST_ID)).thenReturn(1L);
            when(storageService.uploadFile(fileContent, fileName, mimeType)).thenReturn(STORAGE_REFERENCE);
            when(documentRepository.save(any(TravelRequestDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(travelRequestMapper.toDocumentSummary(any(TravelRequestDocument.class))).thenReturn(expected);

            DocumentSummary actual = documentService.uploadDocument(request, fileName, fileType, fileContent);

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<TravelRequestDocument> captor = ArgumentCaptor.forClass(TravelRequestDocument.class);
            verify(documentRepository).save(captor.capture());
            TravelRequestDocument saved = captor.getValue();
            assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.CONFIRMED);
            assertThat(saved.getStorageReference()).isEqualTo(STORAGE_REFERENCE);
            assertThat(saved.getFileType()).isEqualTo(DocumentFileType.valueOf(fileType));
            assertThat(saved.getFileName()).isEqualTo(fileName);
            assertThat(saved.getFileSizeBytes()).isEqualTo(2048L);

            verify(storageService).uploadFile(fileContent, fileName, mimeType);
        }
    }

    @Nested
    @DisplayName("uploadDocument — negative cases (VAL-11, AC-11, Constitution §10)")
    class UploadNegativeCases {

        @Test
        @DisplayName("rejects a file above the 5 MB limit with a ValidationException (field \"file\"); storage and persistence never happen")
        void rejectsOversizedFile() {
            byte[] fileContent = content((int) (DocumentConstraintValidator.MAX_FILE_SIZE_BYTES + 1));

            assertThatThrownBy(() -> documentService.uploadDocument(travelRequest(), FILE_NAME, "PDF", fileContent))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("file");
                        assertThat(ex.getMessage()).contains("maximum allowed");
                    });

            verifyNoInteractions(storageService);
            verify(documentRepository, never()).save(any(TravelRequestDocument.class));
        }

        @Test
        @DisplayName("rejects an empty (0-byte) file with a ValidationException (field \"file\"); storage and persistence never happen")
        void rejectsEmptyFile() {
            assertThatThrownBy(() -> documentService.uploadDocument(travelRequest(), FILE_NAME, "PDF", new byte[0]))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("file");
                        assertThat(ex.getMessage()).contains("empty");
                    });

            verifyNoInteractions(storageService);
            verify(documentRepository, never()).save(any(TravelRequestDocument.class));
        }

        @ParameterizedTest(name = "rejects unsupported file type \"{0}\" with a ValidationException (field \"fileType\")")
        @ValueSource(strings = {"TXT", "docx", "exe"})
        @DisplayName("rejects an unsupported file type with a ValidationException (field \"fileType\"); storage never called")
        void rejectsUnsupportedFileType(String fileType) {
            assertThatThrownBy(() -> documentService.uploadDocument(travelRequest(), FILE_NAME, fileType, content(1024)))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("fileType"));

            verifyNoInteractions(storageService, documentRepository, travelRequestMapper);
        }

        @Test
        @DisplayName("rejects the 6th document on a travel request with a ValidationException (field \"documentIds\"); storage never called")
        void rejectsSixthDocument() {
            when(documentRepository.countByTravelRequestIdAndDeletedFalse(TRAVEL_REQUEST_ID))
                    .thenReturn((long) DocumentConstraintValidator.MAX_DOCUMENTS);

            assertThatThrownBy(() -> documentService.uploadDocument(travelRequest(), FILE_NAME, "PDF", content(1024)))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("documentIds");
                        assertThat(ex.getMessage()).contains("Maximum number of documents");
                    });

            verifyNoInteractions(storageService);
            verify(documentRepository, never()).save(any(TravelRequestDocument.class));
        }
    }

    @Nested
    @DisplayName("uploadDocument — storage failure (FR-DOC-03, AC-12)")
    class StorageFailureCases {

        @Test
        @DisplayName("propagates the StorageException and records the FAILED document via the recorder in an independent transaction, keeping the request in Draft")
        void persistsFailedDocumentWhenStorageUploadFails() {
            TravelRequest request = travelRequest();
            byte[] fileContent = content(1024);

            when(documentRepository.countByTravelRequestIdAndDeletedFalse(TRAVEL_REQUEST_ID)).thenReturn(0L);
            when(storageService.uploadFile(fileContent, FILE_NAME, "application/pdf"))
                    .thenThrow(new StorageException("S3 endpoint unreachable"));

            assertThatThrownBy(() -> documentService.uploadDocument(request, FILE_NAME, "PDF", fileContent))
                    .isInstanceOf(StorageException.class)
                    .hasMessage("Failed to upload document itinerary.pdf")
                    .hasCauseInstanceOf(StorageException.class);

            // The FAILED audit marker must be delegated to the recorder, which persists it in
            // its own REQUIRES_NEW transaction (tasks.md N-04) - not saved inline in this one
            verify(failedDocumentRecorder).recordFailedDocument(request, FILE_NAME, DocumentFileType.PDF, fileContent.length);
            verify(documentRepository, never()).save(any(TravelRequestDocument.class));

            verifyNoInteractions(travelRequestMapper);
        }
    }
}