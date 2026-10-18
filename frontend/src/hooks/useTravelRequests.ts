// Travel request data-fetching hooks (F-06, frontend-tasks.md — "F. Services").
// Wraps `travelRequestService` (F-02) with `ServerDataState` (useAsyncResource.ts)
// for the Travel Request list/detail pages (P-03, P-04, P-07, P-08).

import type { TravelRequestResponse, TravelRequestStatus } from '../models/travelRequest'
import type { PaginatedResult } from '../models/pagination'
import * as travelRequestService from '../services/travelRequestService'
import type {
  TravelRequestListParams,
  TravelRequestListScope,
} from '../services/travelRequestService'
import type { ListQueryState } from './useListQueryState'
import { sortToParam } from './useListQueryState'
import type { ServerDataState } from './useAsyncResource'
import { useAsyncResource } from './useAsyncResource'

/**
 * Maps the URL-synchronized `ListQueryState` (D-04) to the service's
 * `TravelRequestListParams` (frontend-api-mapping.md §1.1): `status` is the
 * only recognized filter. Which requests are listed is chosen by `scope`
 * (own requests vs. approval queue); ownership itself is always the
 * authenticated user server-side, so no user id is sent.
 */
function toListParams(queryState: ListQueryState, scope?: TravelRequestListScope): TravelRequestListParams {
  return {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
    status: queryState.filters.status as TravelRequestStatus | undefined,
    scope,
  }
}

/**
 * P-03 Travel Request List / P-08 Approval Queue: paginated list, re-fetched
 * whenever the query state changes (D-04) or `refetch()` is called.
 */
export function useTravelRequestList(
  queryState: ListQueryState,
  scope?: TravelRequestListScope,
): ServerDataState<PaginatedResult<TravelRequestResponse>> {
  const params = toListParams(queryState, scope)
  return useAsyncResource(
    () => travelRequestService.list(params),
    [params.page, params.size, params.sort, params.status, scope],
  )
}

/**
 * P-04 Travel Request Detail: single record by id, re-fetched whenever `id`
 * changes or `refetch()` is called (e.g. after a decision/edit navigates back
 * to the same detail page).
 */
export function useTravelRequest(id: string): ServerDataState<TravelRequestResponse> {
  return useAsyncResource(() => travelRequestService.getById(id), [id])
}
