// Expense claim DTOs and enums (E-03).
// Mirrors api-contract.md §5.7–§5.11 and backend/data-model.md §4.1/§8: field
// names, types, nullability, and enum string values must stay in sync with the
// contract.
// Internal-only fields (e.g. `deleted`, `version`, audit metadata) are
// intentionally absent from `ExpenseClaimResponse` per Constitution §6
// (see the api-contract.md §5.3 note, which applies to all read models).
//
// `DocumentSummary` is defined in src/models/document.ts (E-02) and reused
// because `ExpenseClaimResponse.documents` references it. `DecisionType`
// (api-contract.md §5.4) is defined in src/models/travelRequest.ts (E-01) and
// re-exported here because `ExpenseReviewRequest.decision` references it.
//
// Contract `decimal` (amount) is `number` in JSON payloads; `date`/`timestamp`
// contract types are `string` (ISO 8601).

import type { DecisionType } from './travelRequest'
import type { DocumentSummary } from './document'

// Re-exported because `ExpenseReviewRequest.decision` references it; the
// canonical home is src/models/travelRequest.ts (E-01).
export type { DecisionType } from './travelRequest'

/** api-contract.md §5.9 / data-model.md §8: ExpenseClaimStatus. */
export type ExpenseClaimStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'REIMBURSED'

/**
 * api-contract.md §5.7: POST /api/expenses. `travelRequestId` must reference
 * an `Approved` travel request owned by the caller (VAL-09, FR-EXP-01);
 * `documentIds` is conditional — required when `amount` exceeds the category
 * receipt threshold (VAL-10, FR-EXP-05).
 */
export interface ExpenseClaimCreateRequest {
  travelRequestId: string
  amount: number
  currency: string
  category: string
  expenseDate: string
  documentIds?: string[]
}

/**
 * api-contract.md §5.8: PUT /api/expenses/{id} — same shape as the create
 * request. Service layer enforces VAL-13 (status must be `PENDING`).
 */
export type ExpenseClaimUpdateRequest = ExpenseClaimCreateRequest

/** api-contract.md §5.9: expense claim read model (no internal-only fields). */
export interface ExpenseClaimResponse {
  id: string
  employeeId: string
  travelRequestId: string
  amount: number
  currency: string
  category: string
  expenseDate: string
  status: ExpenseClaimStatus
  duplicateFlag: boolean
  rejectionReason: string | null
  reimbursedAt: string | null
  documents: DocumentSummary[]
  createdAt: string
  updatedAt: string
}

/**
 * api-contract.md §5.10: POST /api/expenses/{id}/review. `reason` is required
 * and non-blank when `decision` is `REJECTED` (VAL-12 analog); omitted when
 * `APPROVED`.
 */
export interface ExpenseReviewRequest {
  decision: DecisionType
  reason?: string
}

/**
 * api-contract.md §5.11: POST /api/expenses/{id}/reimburse. `reimbursedAt` is
 * optional (defaults to server time); the claim must be `APPROVED` (business
 * rule, FR-FIN-03).
 */
export interface ExpenseReimburseRequest {
  reimbursedAt?: string
}
