// Audit data-fetching hook (F-06, frontend-tasks.md — "F. Services").
// Wraps `auditService` (F-05) with `ServerDataState` (useAsyncResource.ts)
// for the read-only Audit Log Viewer page (P-20).

import type { AuditEntityType, AuditRecordResponse } from '../models/audit'
import type { PaginatedResult } from '../models/pagination'
import { auditService } from '../services/auditService'
import type { AuditListParams } from '../services/auditService'
import type { ListQueryState } from './useListQueryState'
import { sortToParam } from './useListQueryState'
import type { ServerDataState } from './useAsyncResource'
import { useAsyncResource } from './useAsyncResource'

function toAuditInstant(value: string | undefined, boundary: 'start' | 'end'): string | undefined {
  if (value === undefined || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value
  }
  return boundary === 'start'
    ? `${value}T00:00:00Z`
    : `${value}T23:59:59.999999999Z`
}

/**
 * Maps the URL-synchronized `ListQueryState` (D-04) to the service's
 * `AuditListParams` (frontend-api-mapping.md §1.4): `entityType`/`entityId`/
 * `actorId`/`from`/`to` are the recognized `AuditFilterBar` (P-20) filters.
 */
function toListParams(queryState: ListQueryState): AuditListParams {
  return {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
    entityType: queryState.filters.entityType as AuditEntityType | undefined,
    entityId: queryState.filters.entityId,
    actorId: queryState.filters.actorId,
    from: toAuditInstant(queryState.filters.from, 'start'),
    to: toAuditInstant(queryState.filters.to, 'end'),
  }
}

/**
 * P-20 Audit Log Viewer: paginated list, re-fetched whenever the query state
 * changes (D-04) or `refetch()` is called.
 */
export function useAuditList(
  queryState: ListQueryState,
): ServerDataState<PaginatedResult<AuditRecordResponse>> {
  const params = toListParams(queryState)
  return useAsyncResource(
    () => auditService.list(params),
    [params.page, params.size, params.sort, params.entityType, params.entityId, params.actorId, params.from, params.to],
  )
}
