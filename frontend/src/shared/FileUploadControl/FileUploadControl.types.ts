// Mirrors api-contract.md §5.6. Canonical home moves to models/document.ts once E-02 lands.
export type UploadStatus = 'PENDING' | 'CONFIRMED' | 'FAILED'

export interface UploadedDocument {
  documentId: string
  fileName: string
  uploadStatus: UploadStatus
}
