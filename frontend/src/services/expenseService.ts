// Expense service (F-03, frontend-tasks.md — "F. Services").
// Implements the seven `expenseService` functions of the Expense Service area
// (frontend-api-mapping.md §1.2) on top of the shared HTTP client
// (httpClient.ts — F-01; frontend-plan.md §6 "Service Layer Design"):
// endpoint paths, methods, params/bodies, and response types follow
// frontend-api-mapping.md §1.2 and api-contract.md §4.2 (Endpoint Catalog),
// §5.5–§5.11 (DTOs), and §7 (Pagination & Filtering).
//
// Error handling: every function resolves with the documented typed response
// (models/expenseClaim.ts, models/document.ts, models/pagination.ts) or
// rejects with a `ClassifiedError`-shaped Error produced by the shared client
// (frontend-plan.md §11/§13) — the page-level mapping in
// frontend-api-mapping.md §1.2 (e.g. 404 RESOURCE_NOT_FOUND → Not Found
// state, 409 CONFLICT → conflict banner) is handled by the UI layer, not here.

import type {
  ExpenseClaimCreateRequest,
  ExpenseClaimResponse,
  ExpenseClaimStatus,
  ExpenseClaimUpdateRequest,
  ExpenseReimburseRequest,
  ExpenseReviewRequest,
} from '../models/expenseClaim'
import type { DocumentSummary, DocumentUploadRequest } from '../models/document'
import type { PaginatedResult } from '../models/pagination'
import { get, getBlob, post, put } from './httpClient'

/**
 * Query parameters for `GET /api/expenses` (frontend-api-mapping.md §1.2,
 * api-contract.md §7): zero-based `page` (default 0), `size` (default 20, max
 * enforced server-side), repeatable `sort` (`field,asc|desc`), plus the
 * service-specific `status`/`employeeId` filters and the Finance review-queue
 * `duplicateFlag` filter (P-14, FR-FIN-02). Ownership scoping (Employee →
 * own claims, Finance → review queue) is applied server-side regardless of
 * these params (api-contract.md §7).
 */
export type ExpenseListParams = {
  page?: number
  size?: number
  sort?: string | string[]
  status?: ExpenseClaimStatus
  employeeId?: string
  duplicateFlag?: boolean
}

/**
 * Lists expense claims (`GET /api/expenses` — frontend-api-mapping.md §1.2,
 * P-09 Expense Claim List / P-14 Expense Review Queue). Resolves with the
 * shared paginated envelope (api-contract.md §7).
 */
export function list(params?: ExpenseListParams): Promise<PaginatedResult<ExpenseClaimResponse>> {
  return get<PaginatedResult<ExpenseClaimResponse>>('/api/expenses', params)
}

/**
 * Retrieves a single expense claim (`GET /api/expenses/{id}` —
 * frontend-api-mapping.md §1.2, P-10 Expense Claim Detail; read model
 * api-contract.md §5.9, no internal-only fields).
 */
export function getById(id: string): Promise<ExpenseClaimResponse> {
  return get<ExpenseClaimResponse>(`/api/expenses/${id}`)
}

/**
 * Submits a new expense claim against an `Approved` travel request
 * (`POST /api/expenses` — frontend-api-mapping.md §1.2, P-11 New Expense
 * Claim; body: `ExpenseClaimCreateRequest`, api-contract.md §5.7 — VAL-09
 * approved-trip and VAL-10 receipt-threshold checks are enforced
 * server-side).
 */
export function create(payload: ExpenseClaimCreateRequest): Promise<ExpenseClaimResponse> {
  return post<ExpenseClaimResponse>('/api/expenses', payload)
}

/**
 * Edits a `Pending` expense claim (`PUT /api/expenses/{id}` —
 * frontend-api-mapping.md §1.2, P-12 Edit Expense Claim; body:
 * `ExpenseClaimUpdateRequest`, api-contract.md §5.8 — status must be
 * `PENDING`, VAL-13).
 */
export function update(
  id: string,
  payload: ExpenseClaimUpdateRequest,
): Promise<ExpenseClaimResponse> {
  return put<ExpenseClaimResponse>(`/api/expenses/${id}`, payload)
}

/**
 * Uploads a supporting document (`POST /api/expenses/{id}/documents` —
 * frontend-api-mapping.md §1.2, P-11/P-12 Document Upload). The multipart
 * body (api-contract.md §5.5) carries the `file` part (PDF/JPG/PNG, size
 * ≤5MB — VAL-11, enforced server-side) and the `fileName` form field; the
 * `FormData` is passed through the shared client untouched so the transport
 * sets the `multipart/form-data` boundary (frontend-plan.md §13).
 */
export function uploadDocument(
  id: string,
  request: DocumentUploadRequest,
): Promise<DocumentSummary> {
  const formData = new FormData()
  formData.append('file', request.file, request.fileName)
  formData.append('fileName', request.fileName)
  return post<DocumentSummary>(`/api/expenses/${id}/documents`, formData)
}

/**
 * Approves or rejects a `Pending` expense claim (`POST
 * /api/expenses/{id}/review` — frontend-api-mapping.md §1.2, P-13 Expense
 * Review; body: `ExpenseReviewRequest`, api-contract.md §5.10 — `reason` is
 * required and non-blank for `REJECTED`, VAL-12 analog; Finance role only).
 */
export function review(id: string, payload: ExpenseReviewRequest): Promise<ExpenseClaimResponse> {
  return post<ExpenseClaimResponse>(`/api/expenses/${id}/review`, payload)
}

/**
 * Marks an `Approved` expense claim as `Reimbursed` (`POST
 * /api/expenses/{id}/reimburse` — frontend-api-mapping.md §1.2, P-15
 * Reimbursement Action; body: `ExpenseReimburseRequest`, api-contract.md
 * §5.11 — `reimbursedAt` is optional and defaults to server time; the claim
 * must be `APPROVED`, FR-FIN-03; Finance role only).
 */
export function reimburse(
  id: string,
  payload: ExpenseReimburseRequest,
): Promise<ExpenseClaimResponse> {
  return post<ExpenseClaimResponse>(`/api/expenses/${id}/reimburse`, payload)
}

/**
 * Sends a DRAFT (or corrected REJECTED) claim to Finance review (`POST /api/expenses/{id}/submit`).
 * The server re-validates the receipt requirement against the claim's successfully uploaded documents.
 */
export function submit(id: string): Promise<ExpenseClaimResponse> {
  return post<ExpenseClaimResponse>(`/api/expenses/${id}/submit`)
}

/** Downloads one of the claim's documents (`GET /api/expenses/{id}/documents/{documentId}`). */
export function downloadDocument(id: string, documentId: string): Promise<Blob> {
  return getBlob(`/api/expenses/${id}/documents/${documentId}`)
}
