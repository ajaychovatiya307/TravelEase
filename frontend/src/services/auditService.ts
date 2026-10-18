// Audit service (F-05, frontend-tasks.md — "F. Services").
// Implements the single `auditService.list` function of the Audit Service
// area (frontend-api-mapping.md §1.4) on top of the shared HTTP client
// (httpClient.ts — F-01; frontend-plan.md §6 "Service Layer Design"):
// endpoint paths, methods, params, and response types follow
// frontend-api-mapping.md §1.4 and api-contract.md §4.4 (Endpoint Catalog),
// §5.16 (DTOs), and §7 (Pagination & Filtering).
//
// The audit trail is read-only and append-only (FR-AUD-01, data-model.md
// §6.1) — there are no create/edit/delete endpoints, so `list` is the only
// function here.
//
// Error handling: the function resolves with the documented typed response
// (models/audit.ts, models/pagination.ts) or rejects with a
// `ClassifiedError`-shaped Error produced by the shared client
// (frontend-plan.md §11/§13) — the page-level mapping in
// frontend-api-mapping.md §1.4 (403 FORBIDDEN_ACTION → Access Denied, P-21)
// is handled by the UI layer, not here.

import type { AuditEntityType, AuditRecordResponse } from '../models/audit'
import type { PaginatedResult } from '../models/pagination'
import { get } from './httpClient'

/**
 * Query parameters for `GET /api/audit` (frontend-api-mapping.md §1.4,
 * api-contract.md §7): zero-based `page` (default 0), `size` (default 20, max
 * enforced server-side), repeatable `sort` (`field,asc|desc`), plus the audit
 * filters — `entityType`/`entityId` (the trail of one entity), `actorId`
 * (the actions of one user), and the ISO-8601 `from`/`to` date range
 * (inclusive bounds on `occurredAt`). All params are optional and omitted
 * from the query string when not set; the server requires at least one filter
 * combination (`entityType` + `entityId`, `actorId`, or `from` + `to`) and
 * rejects an unfiltered list with `400 VALIDATION_ERROR`. Admin/Finance role
 * only (api-contract.md §4.4).
 */
export type AuditListParams = {
  page?: number
  size?: number
  sort?: string | string[]
  entityType?: AuditEntityType
  entityId?: string
  actorId?: string
  from?: string
  to?: string
}

/**
 * P-20 Audit Log Viewer (frontend-api-mapping.md §1.4, api-contract.md §4.4):
 * read-only, append-only audit trail (FR-AUD-01). Admin/Finance role only —
 * `403 FORBIDDEN_ACTION` surfaces through the shared client and is mapped to
 * the Access Denied page (P-21) by the UI layer.
 */
export const auditService = {
  /**
   * Lists audit records (`GET /api/audit` — frontend-api-mapping.md §1.4,
   * P-20 Audit Log Viewer). Resolves with the shared paginated envelope
   * (api-contract.md §7); the `AuditListParams` query object is serialized by
   * the shared client, with unset params omitted from the query string.
   */
  list(params?: AuditListParams): Promise<PaginatedResult<AuditRecordResponse>> {
    return get<PaginatedResult<AuditRecordResponse>>('/api/audit', params)
  },
}
