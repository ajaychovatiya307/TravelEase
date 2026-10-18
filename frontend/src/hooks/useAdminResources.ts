// Admin resource data-fetching hooks (F-06, frontend-tasks.md — "F. Services").
// Wraps `adminService` (F-04) with `ServerDataState` (useAsyncResource.ts) for
// the four Admin list pages (P-16 Users, P-17 Mappings, P-18 Policy Configs,
// P-19 Expense Categories).

import type {
  EmployeeManagerMappingResponse,
  ExpenseCategoryResponse,
  PolicyConfigResponse,
  RoleType,
  UserAccountResponse,
} from '../models/admin'
import type { PaginatedResult } from '../models/pagination'
import { adminService } from '../services/adminService'
import type {
  EmployeeManagerMappingListParams,
  ExpenseCategoryListParams,
  PolicyConfigListParams,
  UserListParams,
} from '../services/adminService'
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
 * P-16 Admin – Users: paginated list, re-fetched whenever the query state
 * changes (D-04) or `refetch()` is called. Recognized filters: `active`,
 * `role` (frontend-api-mapping.md §1.3).
 */
export function useAdminUsers(
  queryState: ListQueryState,
): ServerDataState<PaginatedResult<UserAccountResponse>> {
  const params: UserListParams = {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
    active: parseBooleanFilter(queryState.filters.active),
    role: queryState.filters.role as RoleType | undefined,
  }
  return useAsyncResource(
    () => adminService.users.list(params),
    [params.page, params.size, params.sort, params.active, params.role],
  )
}

/**
 * P-17 Admin – Employee-Manager Mappings: paginated list, re-fetched
 * whenever the query state changes (D-04) or `refetch()` is called. No
 * resource-specific filters (frontend-api-mapping.md §1.3).
 */
export function useAdminMappings(
  queryState: ListQueryState,
): ServerDataState<PaginatedResult<EmployeeManagerMappingResponse>> {
  const params: EmployeeManagerMappingListParams = {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
  }
  return useAsyncResource(
    () => adminService.mappings.list(params),
    [params.page, params.size, params.sort],
  )
}

/**
 * P-18 Admin – Policy Configuration: paginated list, re-fetched whenever the
 * query state changes (D-04) or `refetch()` is called. Recognized filter:
 * `categoryCode` (also backs the expense-form threshold lookup, P-11/P-12).
 */
export function usePolicyConfigs(
  queryState: ListQueryState,
): ServerDataState<PaginatedResult<PolicyConfigResponse>> {
  const params: PolicyConfigListParams = {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
    categoryCode: queryState.filters.categoryCode,
  }
  return useAsyncResource(
    () => adminService.policyConfigs.list(params),
    [params.page, params.size, params.sort, params.categoryCode],
  )
}

/**
 * P-19 Admin – Expense Categories: paginated list, re-fetched whenever the
 * query state changes (D-04) or `refetch()` is called. Recognized filter:
 * `active` (also backs the expense-form category lookup, P-11/P-12).
 */
export function useExpenseCategories(
  queryState: ListQueryState,
): ServerDataState<PaginatedResult<ExpenseCategoryResponse>> {
  const params: ExpenseCategoryListParams = {
    page: queryState.page,
    size: queryState.size,
    sort: sortToParam(queryState.sort),
    active: parseBooleanFilter(queryState.filters.active),
  }
  return useAsyncResource(
    () => adminService.expenseCategories.list(params),
    [params.page, params.size, params.sort, params.active],
  )
}
