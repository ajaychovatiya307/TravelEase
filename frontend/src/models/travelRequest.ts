// Travel request DTOs and enums (E-01).
// Mirrors api-contract.md §5.1–§5.4 and backend/data-model.md §8: field names,
// types, nullability, and enum string values must stay in sync with the contract.
// Internal-only fields (e.g. `deleted`, `version`, audit metadata) are intentionally
// absent per api-contract.md §5.3 (Constitution §6).
//
// `DocumentSummary` (with its `DocumentFileType`/`UploadStatus` enums) is defined
// in src/models/document.ts (E-02) and re-exported here because
// `TravelRequestResponse.documents` references it.
//
// `date`/`timestamp` contract types are `string` (ISO 8601) in JSON payloads.

import type { DocumentSummary } from './document'

// Re-exported to preserve the public API established in E-01; the canonical home
// is src/models/document.ts (E-02).
export type { DocumentFileType, DocumentSummary, UploadStatus } from './document'

/** api-contract.md §5.3 / data-model.md §8: TravelRequestStatus. */
export type TravelRequestStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

/** api-contract.md §5.4 / data-model.md §8: DecisionType. */
export type DecisionType = 'APPROVED' | 'REJECTED'

/** api-contract.md §5.1: POST /api/travel-requests. */
export interface TravelRequestCreateRequest {
  destination: string
  startDate: string
  endDate: string
  justification: string
  documentIds?: string[]
}

/** api-contract.md §5.2: PUT /api/travel-requests/{id} — same shape as the create request. */
export type TravelRequestUpdateRequest = TravelRequestCreateRequest

/** api-contract.md §5.3: travel request read model (no internal-only fields). */
export interface TravelRequestResponse {
  id: string
  employeeId: string
  destination: string
  startDate: string
  endDate: string
  justification: string
  status: TravelRequestStatus
  approverId: string | null
  rejectionReason: string | null
  resubmissionOfId: string | null
  documents: DocumentSummary[]
  createdAt: string
  updatedAt: string
}

/**
 * api-contract.md §5.4: POST /api/travel-requests/{id}/approval.
 * `reason` is required and non-blank when `decision` is `REJECTED` (VAL-12); omitted
 * when `APPROVED`.
 */
export interface ApprovalDecisionRequest {
  decision: DecisionType
  reason?: string
}
