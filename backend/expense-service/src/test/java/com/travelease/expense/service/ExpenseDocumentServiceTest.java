package com.travelease.expense.service;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.document.UploadStatus;
import com.travelease.common.dto.DocumentSummary;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.exception.ForbiddenActionException;
import com.travelease.common.exception.ResourceNotFoundException;
import com.travelease.common.exception.ValidationException;
import com.travelease.common.storage.DocumentStorageService;
import com.travelease.common.storage.StorageException;
import com.travelease.common.validation.DocumentConstraintValidator;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseDocument;
import com.travelease.expense.mapper.ExpenseClaimMapper;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;
import com.travelease.expense.service.impl.ExpenseDocumentServiceImpl;
import com.travelease.expense.service.impl.ExpenseFailedDocumentRecorder;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpenseDocumentServiceImpl} (tasks.md L-04), mirroring the
 * travel-request document upload tests and covering supporting-document upload
 * orchestration for expense claims with positive and negative cases per
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
 *   <li>6th document on the same expense claim → {@link ValidationException}
 *       (field {@code "documentIds"}) before storage or persistence happens
 *       (VAL-11, AC-11)</li>
 *   <li>storage write failure → the {@link StorageException} propagates (which keeps
 *       the owning claim in Draft state) and the FAILED audit marker (status {@code FAILED},
 *       {@code null} storage reference) is recorded by the independent
 *       {@code REQUIRES_NEW} {@code ExpenseFailedDocumentRecorder} (tasks.md N-04)
 *       (FR-DOC-03, AC-12)</li>
 * </ul>
 *
 * <p>The service is plain (non-Spring) business logic with constructor-injected
 * collaborators, so the repository, storage service, mapper and the
 * {@code REQUIRES_NEW} failed-document recorder are mocked with Mockito and the
 * service is injected via {@code @InjectMocks} - no Spring context
 * is needed. The shared {@link DocumentConstraintValidator} (tasks.md J-03) is a
 * spy on a real instance so the actual VAL-11 rules (5 MB limit, non-empty check,
 * max-5-documents, type allow-list and MIME mapping) run under test instead of
 * being stubbed away.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseDocumentServiceImpl uploadDocument (tasks.md L-04; spec.md FR-DOC-01, FR-DOC-02, FR-DOC-03; VAL-11; AC-11, AC-12)")
class ExpenseDocumentServiceTest {

    private static final Long EXPENSE_CLAIM_ID = 20L;
    private static final Long EMPLOYEE_ID = 1L;
    private static final Long OTHER_EMPLOYEE_ID = 2L;
    private static final Long TRAVEL_REQUEST_ID = 10L;
    private static final String FILE_NAME = "taxi-receipt.pdf";
    private static final String STORAGE_REFERENCE = "store-123";

    @Mock
    private ExpenseDocumentRepository documentRepository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private ExpenseFailedDocumentRecorder failedDocumentRecorder;

    @Mock
    private ExpenseClaimMapper expenseClaimMapper;

    @Mock
    private ExpenseClaimRepository claimRepository;

    /** Real (spied) validator so the actual VAL-11 rules run under test (tasks.md J-03). */
    @Spy
    private DocumentConstraintValidator documentConstraintValidator = new DocumentConstraintValidator();

    /** Real (spied) claim policy so the attachable-status rule runs under test. */
    @Spy
    private com.travelease.expense.validation.ExpenseClaimPolicyValidator policyValidator =
            new com.travelease.expense.validation.ExpenseClaimPolicyValidator();

    @InjectMocks
    private ExpenseDocumentServiceImpl documentService;

    private static ExpenseClaim expenseClaim() {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setId(EXPENSE_CLAIM_ID);
        claim.setEmployeeId(EMPLOYEE_ID);
        claim.setTravelRequestId(TRAVEL_REQUEST_ID);
        // Newly created claims are DRAFTs, which accept receipt uploads
        claim.setStatus(com.travelease.expense.entity.ExpenseClaimStatus.DRAFT);
        return claim;
    }

    private static byte[] content(int sizeBytes) {
        byte[] bytes = new byte[sizeBytes];
        Arrays.fill(bytes, (byte) 1);
        return bytes;
    }

    @Nested
    @DisplayName("uploadDocument — positive cases (FR-DOC-01, FR-DOC-02, AC-11)")
    class UploadPositiveCases {

        @Test
        @DisplayName("uploads a valid PDF: CONFIRMED metadata with the storage reference is saved and the mapped summary is returned")
        void uploadsValidPdf() {
            ExpenseClaim claim = expenseClaim();
            byte[] fileContent = content(1024);
            DocumentSummary expected = new DocumentSummary(
                    "1", FILE_NAME, DocumentFileType.PDF, 1024L, STORAGE_REFERENCE, UploadStatus.CONFIRMED);

            when(documentRepository.countByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(0L);
            when(storageService.uploadFile(fileContent, FILE_NAME, "application/pdf")).thenReturn(STORAGE_REFERENCE);
            when(documentRepository.save(any(ExpenseDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(expenseClaimMapper.toDocumentSummary(any(ExpenseDocument.class))).thenReturn(expected);

            DocumentSummary actual = documentService.uploadDocument(claim, FILE_NAME, "PDF", fileContent);

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<ExpenseDocument> captor = ArgumentCaptor.forClass(ExpenseDocument.class);
            verify(documentRepository).save(captor.capture());
            ExpenseDocument saved = captor.getValue();
            assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.CONFIRMED);
            assertThat(saved.getStorageReference()).isEqualTo(STORAGE_REFERENCE);
            assertThat(saved.getFileType()).isEqualTo(DocumentFileType.PDF);
            assertThat(saved.getFileName()).isEqualTo(FILE_NAME);
            assertThat(saved.getFileSizeBytes()).isEqualTo(1024L);
            assertThat(saved.getExpenseClaim()).isSameAs(claim);
            assertThat(saved.isDeleted()).isFalse();

            verify(storageService).uploadFile(fileContent, FILE_NAME, "application/pdf");
        }

        @ParameterizedTest(name = "uploads a valid {0}: CONFIRMED metadata saved with the {1} MIME type")
        @CsvSource({"JPG, image/jpeg", "PNG, image/png"})
        @DisplayName("uploads a valid {0}: CONFIRMED metadata saved with the correct {1} MIME type")
        void uploadsValidJpgAndPng(String fileType, String mimeType) {
            ExpenseClaim claim = expenseClaim();
            String fileName = "receipt." + fileType.toLowerCase();
            byte[] fileContent = content(2048);
            DocumentSummary expected = new DocumentSummary(
                    "2", fileName, DocumentFileType.valueOf(fileType), 2048L, STORAGE_REFERENCE, UploadStatus.CONFIRMED);

            when(documentRepository.countByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(1L);
            when(storageService.uploadFile(fileContent, fileName, mimeType)).thenReturn(STORAGE_REFERENCE);
            when(documentRepository.save(any(ExpenseDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(expenseClaimMapper.toDocumentSummary(any(ExpenseDocument.class))).thenReturn(expected);

            DocumentSummary actual = documentService.uploadDocument(claim, fileName, fileType, fileContent);

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<ExpenseDocument> captor = ArgumentCaptor.forClass(ExpenseDocument.class);
            verify(documentRepository).save(captor.capture());
            ExpenseDocument saved = captor.getValue();
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

            assertThatThrownBy(() -> documentService.uploadDocument(expenseClaim(), FILE_NAME, "PDF", fileContent))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("file");
                        assertThat(ex.getMessage()).contains("maximum allowed");
                    });

            verifyNoInteractions(storageService);
            verify(documentRepository, never()).save(any(ExpenseDocument.class));
        }

        @Test
        @DisplayName("rejects an empty (0-byte) file with a ValidationException (field \"file\"); storage and persistence never happen")
        void rejectsEmptyFile() {
            assertThatThrownBy(() -> documentService.uploadDocument(expenseClaim(), FILE_NAME, "PDF", new byte[0]))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("file");
                        assertThat(ex.getMessage()).contains("empty");
                    });

            verifyNoInteractions(storageService);
            verify(documentRepository, never()).save(any(ExpenseDocument.class));
        }

        @ParameterizedTest(name = "rejects unsupported file type \"{0}\" with a ValidationException (field \"fileType\")")
        @ValueSource(strings = {"TXT", "docx", "exe"})
        @DisplayName("rejects an unsupported file type with a ValidationException (field \"fileType\"); storage never called")
        void rejectsUnsupportedFileType(String fileType) {
            assertThatThrownBy(() -> documentService.uploadDocument(expenseClaim(), FILE_NAME, fileType, content(1024)))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                            .extracting(ErrorResponse.FieldError::field)
                            .containsExactly("fileType"));

            verifyNoInteractions(storageService, documentRepository, expenseClaimMapper);
        }

        @Test
        @DisplayName("rejects the 6th document on an expense claim with a ValidationException (field \"documentIds\"); storage never called")
        void rejectsSixthDocument() {
            when(documentRepository.countByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID))
                    .thenReturn((long) DocumentConstraintValidator.MAX_DOCUMENTS);

            assertThatThrownBy(() -> documentService.uploadDocument(expenseClaim(), FILE_NAME, "PDF", content(1024)))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .containsExactly("documentIds");
                        assertThat(ex.getMessage()).contains("Maximum number of documents");
                        assertThat(ex.getMessage()).contains("expense claim");
                    });

            verifyNoInteractions(storageService);
            verify(documentRepository, never()).save(any(ExpenseDocument.class));
        }
    }

    @Nested
    @DisplayName("uploadDocument — storage failure (FR-DOC-03, AC-12)")
    class StorageFailureCases {

        @Test
        @DisplayName("propagates the StorageException and records the document as FAILED via the REQUIRES_NEW recorder, keeping the claim in Draft")
        void persistsFailedDocumentWhenStorageUploadFails() {
            ExpenseClaim claim = expenseClaim();
            byte[] fileContent = content(1024);

            when(documentRepository.countByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(0L);
            when(storageService.uploadFile(fileContent, FILE_NAME, "application/pdf"))
                    .thenThrow(new StorageException("S3 endpoint unreachable"));

            assertThatThrownBy(() -> documentService.uploadDocument(claim, FILE_NAME, "PDF", fileContent))
                    .isInstanceOf(StorageException.class)
                    .hasMessage("S3 endpoint unreachable");

            // The FAILED audit marker (FAILED status, null storage reference) is recorded by the
            // REQUIRES_NEW recorder (tasks.md N-04), not inline in this rollback-only transaction.
            verify(failedDocumentRecorder).recordFailedDocument(claim, FILE_NAME, DocumentFileType.PDF, 1024L);
            verify(documentRepository, never()).save(any(ExpenseDocument.class));

            verifyNoInteractions(expenseClaimMapper);
        }
    }

    @Nested
    @DisplayName("uploadDocument by id — ownership enforcement (NFR-10, Constitution §2/§3)")
    class Ownership {

        @Test
        @DisplayName("rejects an upload by an employee who does not own the claim with a ForbiddenActionException; storage and persistence never happen")
        void rejectsUploadByNonOwner() {
            byte[] fileContent = content(1024);

            when(claimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(expenseClaim()));

            assertThatThrownBy(() -> documentService.uploadDocument(
                    EXPENSE_CLAIM_ID, OTHER_EMPLOYEE_ID, FILE_NAME, "PDF", fileContent))
                    .isInstanceOf(ForbiddenActionException.class)
                    .hasMessage("Only the claim owner can upload documents");

            verifyNoInteractions(storageService, expenseClaimMapper, failedDocumentRecorder);
            verify(documentRepository, never()).save(any(ExpenseDocument.class));
        }

        @Test
        @DisplayName("rejects an upload for a nonexistent (or soft-deleted) claim with a ResourceNotFoundException; storage and persistence never happen")
        void rejectsUploadForMissingClaim() {
            byte[] fileContent = content(1024);

            when(claimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.uploadDocument(
                    EXPENSE_CLAIM_ID, EMPLOYEE_ID, FILE_NAME, "PDF", fileContent))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");

            verifyNoInteractions(storageService, expenseClaimMapper, failedDocumentRecorder);
            verify(documentRepository, never()).save(any(ExpenseDocument.class));
        }

        @Test
        @DisplayName("uploads a valid PDF for the claim owner: CONFIRMED metadata is saved and the mapped summary is returned")
        void uploadsValidPdfAsOwner() {
            byte[] fileContent = content(1024);
            DocumentSummary expected = new DocumentSummary(
                    "1", FILE_NAME, DocumentFileType.PDF, 1024L, STORAGE_REFERENCE, UploadStatus.CONFIRMED);

            when(claimRepository.findByIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(Optional.of(expenseClaim()));
            when(documentRepository.countByExpenseClaimIdAndDeletedFalse(EXPENSE_CLAIM_ID)).thenReturn(0L);
            when(storageService.uploadFile(fileContent, FILE_NAME, "application/pdf")).thenReturn(STORAGE_REFERENCE);
            when(documentRepository.save(any(ExpenseDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(expenseClaimMapper.toDocumentSummary(any(ExpenseDocument.class))).thenReturn(expected);

            DocumentSummary actual = documentService.uploadDocument(
                    EXPENSE_CLAIM_ID, EMPLOYEE_ID, FILE_NAME, "PDF", fileContent);

            assertThat(actual).isSameAs(expected);
            verify(storageService).uploadFile(fileContent, FILE_NAME, "application/pdf");
        }
    }
}