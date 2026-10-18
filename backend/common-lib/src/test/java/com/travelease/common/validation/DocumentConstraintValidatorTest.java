package com.travelease.common.validation;

import com.travelease.common.document.DocumentFileType;
import com.travelease.common.error.ErrorResponse;
import com.travelease.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DocumentConstraintValidator} (tasks.md J-03), covering the
 * VAL-11 document rules (FR-DOC-01, AC-11) with positive and negative cases per
 * Constitution §10:
 *
 * <ul>
 *   <li>file type: PDF/JPG/PNG only, case-insensitive</li>
 *   <li>file size: at most {@code MAX_FILE_SIZE_BYTES} (5 MB) and at least 1 byte</li>
 *   <li>document count: at most {@code MAX_DOCUMENTS} (5) per owning record</li>
 *   <li>MIME resolution for storage uploads</li>
 * </ul>
 *
 * <p>The validator is plain (non-Spring) business logic, so it is instantiated
 * directly - no Spring context is needed. Error messages and failing-field names
 * asserted here are part of the api-contract.md §8 contract shared by both
 * upload services.
 */
@DisplayName("DocumentConstraintValidator (VAL-11, FR-DOC-01, AC-11; tasks.md J-03)")
class DocumentConstraintValidatorTest {

    private DocumentConstraintValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DocumentConstraintValidator();
    }

    @Nested
    @DisplayName("validateFileType (VAL-11)")
    class ValidateFileType {

        @Test
        @DisplayName("accepts lowercase 'pdf' and returns PDF")
        void acceptsLowercasePdf() {
            assertThat(validator.validateFileType("pdf")).isEqualTo(DocumentFileType.PDF);
        }

        @Test
        @DisplayName("accepts uppercase 'JPG' and returns JPG")
        void acceptsUppercaseJpg() {
            assertThat(validator.validateFileType("JPG")).isEqualTo(DocumentFileType.JPG);
        }

        @Test
        @DisplayName("accepts mixed-case 'Png' and returns PNG")
        void acceptsMixedCasePng() {
            assertThat(validator.validateFileType("Png")).isEqualTo(DocumentFileType.PNG);
        }

        @Test
        @DisplayName("throws VALIDATION_ERROR with failing field 'fileType' for unsupported type 'DOCX'")
        void rejectsUnsupportedButKnownType() {
            assertThatThrownBy(() -> validator.validateFileType("DOCX"))
                    .isInstanceOfSatisfying(ValidationException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                        assertThat(ex.getFieldErrors())
                                .extracting(ErrorResponse.FieldError::field)
                                .contains("fileType");
                    });
        }

        @Test
        @DisplayName("throws ValidationException with 'Invalid file type' message for garbage value 'exe'")
        void rejectsGarbageType() {
            assertThatThrownBy(() -> validator.validateFileType("exe"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invalid file type");
        }
    }

    @Nested
    @DisplayName("validateFileSize (VAL-11, 5 MB limit)")
    class ValidateFileSize {

        @Test
        @DisplayName("accepts a file of exactly MAX_FILE_SIZE_BYTES (5 MB)")
        void acceptsExactlyMaxSize() {
            assertThatCode(() -> validator.validateFileSize(DocumentConstraintValidator.MAX_FILE_SIZE_BYTES))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects a file one byte above the 5 MB limit")
        void rejectsSizeAboveMax() {
            assertThatThrownBy(() -> validator.validateFileSize(DocumentConstraintValidator.MAX_FILE_SIZE_BYTES + 1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("5 MB");
        }

        @Test
        @DisplayName("rejects an empty (0 byte) file")
        void rejectsEmptyFile() {
            assertThatThrownBy(() -> validator.validateFileSize(0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("empty");
        }
    }

    @Nested
    @DisplayName("validateDocumentCount (VAL-11, 5 documents limit)")
    class ValidateDocumentCount {

        @Test
        @DisplayName("accepts a current count of 4 (limit not yet reached)")
        void acceptsCountBelowLimit() {
            assertThatCode(() -> validator.validateDocumentCount(4, "travel request"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects the 6th document on a travel request and names the owner in the message")
        void rejectsCountAtLimitForTravelRequest() {
            assertThatThrownBy(() -> validator.validateDocumentCount(5, "travel request"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Maximum number of documents (5)")
                    .hasMessageContaining("travel request");
        }

        @Test
        @DisplayName("rejects the 6th document on an expense claim and names the owner in the message")
        void rejectsCountAtLimitForExpenseClaim() {
            assertThatThrownBy(() -> validator.validateDocumentCount(5, "expense claim"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Maximum number of documents (5)")
                    .hasMessageContaining("expense claim");
        }
    }

    @Nested
    @DisplayName("resolveMimeType (tasks.md J-03)")
    class ResolveMimeType {

        @Test
        @DisplayName("maps PDF to 'application/pdf'")
        void mapsPdfMimeType() {
            assertThat(validator.resolveMimeType(DocumentFileType.PDF)).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("maps JPG to 'image/jpeg'")
        void mapsJpgMimeType() {
            assertThat(validator.resolveMimeType(DocumentFileType.JPG)).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("maps PNG to 'image/png'")
        void mapsPngMimeType() {
            assertThat(validator.resolveMimeType(DocumentFileType.PNG)).isEqualTo("image/png");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for a null file type")
        void rejectsNullFileType() {
            assertThatThrownBy(() -> validator.resolveMimeType(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}