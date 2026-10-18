// Expense claim data-fetching hooks (F-06, frontend-tasks.md — "F. Services").
// Wraps `expenseService` (F-03) with `ServerDataState` (useAsyncResource.ts)
// for the Expense list/detail pages (P-09, P-10, P-13, P-14).

import type { ExpenseClaimResponse, ExpenseClaimStatus } from '../models/expenseClaim'
import type { PaginatedResult } from '../models/pagination'
import * as expenseService from '../services/expenseService'
import type { ExpenseListParams } from '../services/expenseService'
import type { ListQueryState } from './useListQueryState'
import { sortToParam } from './useListQueryState'
import type { ServerDataState } from './useAsyncResource'
import { useAsyncResource } from './useAsyncResource'

/** `true`/`false` string filter value -> boolean; anything else -> `undefined`. */
function parseBooleanFilter(value: string | undefined): boolean | undefined {
  if (value === 'true') return true
  if (value === 'false') return false
  return undefined
}

/**
 * Maps the URL-synchronized `ListQueryState` (D-04) to the service's
 * `ExpenseListParams` (frontend-api-mapping.md §1.2): `status`/`employeeId`/
 * `duplicateFlag` (P-14 review-queue filter, FR-FIN-02) are the recognized
 * filters.
 */
function toListParams(queryState: ListQueryState): ExpenseListParams {
  return {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
    status: queryState.filters.status as ExpenseClaimStatus | undefined,
    employeeId: queryState.filters.employeeId,
    duplicateFlag: parseBooleanFilter(queryState.filters.duplicateFlag),
  }
}

/**
 * P-09 Expense Claim List / P-14 Expense Review Queue: paginated list,
 * re-fetched whenever the query state changes (D-04) or `refetch()` is
 * called.
 */
export function useExpenseList(
  queryState: ListQueryState,
): ServerDataState<PaginatedResult<ExpenseClaimResponse>> {
  const params = toListParams(queryState)
  return useAsyncResource(
    () => expenseService.list(params),
    [params.page, params.size, params.sort, params.status, params.employeeId, params.duplicateFlag],
  )
}

/**
 * P-10 Expense Claim Detail: single record by id, re-fetched whenever `id`
 * changes or `refetch()` is called (e.g. after a review/reimburse action
 * navigates back to the same detail page).
 */
export function useExpenseClaim(id: string): ServerDataState<ExpenseClaimResponse> {
  return useAsyncResource(() => expenseService.getById(id), [id])
}
