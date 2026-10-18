// Travel request service (F-02, frontend-tasks.md — "F. Services").
// Implements the six `travelRequestService` functions of the Travel Request
// Service area (frontend-api-mapping.md §1.1) on top of the shared HTTP
// client (httpClient.ts — F-01; frontend-plan.md §6 "Service Layer Design"):
// endpoint paths, methods, params/bodies, and response types follow
// frontend-api-mapping.md §1.1 and api-contract.md §4.1 (Endpoint Catalog),
// §5.1–§5.6 (DTOs), and §7 (Pagination & Filtering).
//
// Error handling: every function resolves with the documented typed response
// (models/travelRequest.ts, models/document.ts, models/pagination.ts) or
// rejects with a `ClassifiedError`-shaped Error produced by the shared client
// (frontend-plan.md §11/§13) — the page-level mapping in
// frontend-api-mapping.md §1.1 (e.g. 404 RESOURCE_NOT_FOUND → Not Found
// state, 409 CONFLICT → conflict banner) is handled by the UI layer, not here.

import type {
  ApprovalDecisionRequest,
  TravelRequestCreateRequest,
  TravelRequestResponse,
  TravelRequestStatus,
  TravelRequestUpdateRequest,
} from '../models/travelRequest'
import type { DocumentSummary, DocumentUploadRequest } from '../models/document'
import type { PaginatedResult } from '../models/pagination'
import { get, getBlob, post, put } from './httpClient'

/**
 * Query parameters for `GET /api/travel-requests` (frontend-api-mapping.md
 * §1.1, api-contract.md §7): zero-based `page` (default 0), `size` (default
 * 20, max enforced server-side), repeatable `sort` (`field,asc|desc`), plus
 * the service-specific `status` filter and the explicit `scope`. Ownership is
 * always the authenticated user server-side: `MINE` = requests the caller
 * submitted, `APPROVALS` = requests routed to the caller for a decision,
 * `ALL` = everything (Finance/Admin). Omitting `scope` keeps the legacy
 * role-derived default.
 */
export type TravelRequestListScope = 'MINE' | 'APPROVALS' | 'ALL'

export type TravelRequestListParams = {
  page?: number
  size?: number
  sort?: string | string[]
  status?: TravelRequestStatus
  scope?: TravelRequestListScope
}

/**
 * Lists travel requests (`GET /api/travel-requests` — frontend-api-mapping.md
 * §1.1, P-03 Travel Request List / P-08 Approval Queue). Resolves with the
 * shared paginated envelope (api-contract.md §7).
 */
export function list(
  params?: TravelRequestListParams,
): Promise<PaginatedResult<TravelRequestResponse>> {
  return get<PaginatedResult<TravelRequestResponse>>('/api/travel-requests', params)
}

/**
 * Retrieves a single travel request (`GET /api/travel-requests/{id}` —
 * frontend-api-mapping.md §1.1, P-04 Travel Request Detail; read model
 * api-contract.md §5.3, no internal-only fields).
 */
export function getById(id: string): Promise<TravelRequestResponse> {
  return get<TravelRequestResponse>(`/api/travel-requests/${id}`)
}

/**
 * Submits a new travel request (`POST /api/travel-requests` —
 * frontend-api-mapping.md §1.1, P-05 New Travel Request; body:
 * `TravelRequestCreateRequest`, api-contract.md §5.1).
 */
export function create(payload: TravelRequestCreateRequest): Promise<TravelRequestResponse> {
  return post<TravelRequestResponse>('/api/travel-requests', payload)
}

/**
 * Edits a `Pending` or resubmits a `Rejected` travel request
 * (`PUT /api/travel-requests/{id}` — frontend-api-mapping.md §1.1, P-06
 * Edit/Resubmit; body: `TravelRequestUpdateRequest`, api-contract.md §5.2).
 */
export function update(
  id: string,
  payload: TravelRequestUpdateRequest,
): Promise<TravelRequestResponse> {
  return put<TravelRequestResponse>(`/api/travel-requests/${id}`, payload)
}

/**
 * Uploads a supporting document (`POST /api/travel-requests/{id}/documents`
 * — frontend-api-mapping.md §1.1, P-05/P-06 Document Upload). The multipart
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
  return post<DocumentSummary>(`/api/travel-requests/${id}/documents`, formData)
}

/**
 * Approves or rejects a `Pending` travel request (`POST
 * /api/travel-requests/{id}/approval` — frontend-api-mapping.md §1.1, P-07
 * Travel Request Approval; body: `ApprovalDecisionRequest`, api-contract.md
 * §5.4 — `reason` is required and non-blank for `REJECTED`, VAL-12).
 */
export function decide(
  id: string,
  payload: ApprovalDecisionRequest,
): Promise<TravelRequestResponse> {
  return post<TravelRequestResponse>(`/api/travel-requests/${id}/approval`, payload)
}

/**
 * Withdraws a travel request (`POST /api/travel-requests/{id}/cancel`): owner only; PENDING at any
 * time, APPROVED before the trip starts.
 */
export function cancel(id: string): Promise<TravelRequestResponse> {
  return post<TravelRequestResponse>(`/api/travel-requests/${id}/cancel`)
}

/**
 * Moves a PENDING request to another approver (`POST /api/travel-requests/{id}/reassign`, ADMIN only,
 * #11). The server rejects the requester, the current approver and ineligible accounts (422).
 */
export function reassign(id: string, approverId: string): Promise<TravelRequestResponse> {
  return post<TravelRequestResponse>(`/api/travel-requests/${id}/reassign`, {
    approverId: Number(approverId),
  })
}

/** Downloads one of the request's documents (`GET /api/travel-requests/{id}/documents/{documentId}`). */
export function downloadDocument(id: string, documentId: string): Promise<Blob> {
  return getBlob(`/api/travel-requests/${id}/documents/${documentId}`)
}
