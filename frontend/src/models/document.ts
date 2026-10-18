// Document DTOs and enums (E-02).
// Mirrors api-contract.md §5.5–§5.6: field names, types, nullability, and enum
// string values must stay in sync with the contract.
//
// `DocumentSummary` is the canonical read model shared by `TravelRequestResponse`
// (api-contract.md §5.3) and `ExpenseClaimResponse` (§5.9); other model files
// import it from here.
//
// Contract types `long` (fileSizeBytes) are `number` in JSON payloads; the
// `file` part of `DocumentUploadRequest` is binary in a multipart payload and is
// modelled as `Blob`.

/** api-contract.md §5.6: DocumentFileType. */
export type DocumentFileType = 'PDF' | 'JPG' | 'PNG'

/** api-contract.md §5.6: UploadStatus. */
export type UploadStatus = 'PENDING' | 'CONFIRMED' | 'FAILED'

/**
 * api-contract.md §5.5: POST .../documents (multipart). `file` is the binary
 * multipart part (PDF/JPG/PNG, size <= 5MB — VAL-11, enforced server-side);
 * `fileName` is a form field, or derived from the part.
 */
export interface DocumentUploadRequest {
  file: Blob
  fileName: string
}

/**
 * api-contract.md §5.6: document metadata (response); used in
 * `TravelRequestResponse`/`ExpenseClaimResponse`. `storageReference` is metadata
 * only, never binary content (FR-DOC-02).
 */
export interface DocumentSummary {
  id: string
  fileName: string
  fileType: DocumentFileType
  fileSizeBytes: number
  storageReference: string
  uploadStatus: UploadStatus
}
