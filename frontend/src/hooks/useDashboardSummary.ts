// Dashboard summary data hook (I-02 part 1, frontend-tasks.md — "F. Services"
// data source for P-02 Dashboard). Aggregates the four role-section counts of
// frontend-spec.md §4.2 — "My Travel Requests & Expenses" (always), "Pending
// My Approval" (MANAGER), "Finance Review Queue" (FINANCE) — into the single
// `DashboardSummaryView` (ui-model.md §7) consumed by the Dashboard page. A
// multi-role user gets all applicable counts from the same four queries
// (frontend-clarification.md Q1); the "Admin Shortcuts" section is a static
// link set and carries no counts (ui-model.md §7).
//
// Each count is the `totalElements` of one `PENDING` list query issued through
// the existing F-06 list hooks (useTravelRequestList / useExpenseList) with a
// synthetic, count-only `ListQueryState` (size 1 — `content` is discarded,
// never rendered). `ListQueryState` is a plain data type (D-04), but
// `useListQueryState` itself is URL-synchronizing — the dashboard must not
// read or write the list pages' URL query string — so plain state object
// literals are constructed here instead.
//
// Rules-of-hooks safety: all four list hooks are called unconditionally; only
// the query params differ by role. Non-MANAGER sessions are never sent an
// approver-scoped query, and non-FINANCE sessions get an employeeId-scoped
// count, so every request stays within the caller's own data (the server
// applies ownership-based implicit filters regardless, api-contract.md §7).

import { useCallback } from 'react'

import type { DashboardSummaryView, SessionView } from '../models/viewModels'
import { useExpenseList } from './useExpenses'
import { useTravelRequestList } from './useTravelRequests'
import type { ListQueryState } from './useListQueryState'
import { DEFAULT_SORT } from './useListQueryState'
import type { ServerDataState } from './useAsyncResource'

/**
 * Count-only page size: the count comes from `totalElements`, never from
 * `content`, so the smallest supported page keeps the payload minimal.
 * Deliberately outside `PAGE_SIZE_OPTIONS` (Q21) — those govern the
 * user-selectable list page sizes, not internal count queries.
 */
const COUNT_QUERY_SIZE = 1

/**
 * Builds a count-only `ListQueryState`: page 0, {@link COUNT_QUERY_SIZE},
 * the default sort (irrelevant for a count), and the role-scoped `PENDING`
 * filter set supplied by the caller.
 */
function countQueryState(filters: Record<string, string>): ListQueryState {
  return {
    page: 0,
    size: COUNT_QUERY_SIZE,
    sort: DEFAULT_SORT,
    filters,
  }
}

/**
 * P-02 Dashboard (frontend-spec.md §4.2, ui-model.md §7): composes the four
 * role-section pending counts from the existing F-06 list hooks into a single
 * `ServerDataState<DashboardSummaryView>`.
 *
 * Composition of the four sub-states:
 * - `data`: the full `DashboardSummaryView` once all four queries have
 *   resolved (each count is the sub-result's `totalElements`); `null` before
 *   then.
 * - `isLoading`: `true` while ANY sub-query is in flight.
 * - `error`: the first non-null sub-error (`ClassifiedError`), `null`
 *   otherwise.
 * - `refetch`: re-runs all four sub-queries (stable identity, mirroring the
 *   `ServerDataState` contract of the wrapped hooks).
 *
 * All four list hooks are called unconditionally — only their query params
 * differ by role:
 * - `pendingTravelRequests`: caller's own `PENDING` travel requests
 *   (`status=PENDING`, `employeeId=self`) — always.
 * - `pendingExpenses`: caller's own `PENDING` expense claims
 *   (`status=PENDING`, `employeeId=self`) — always.
 * - `pendingApprovals`: `PENDING` travel requests assigned to the caller
 *   (MANAGER — `approverId=self`; the travel service applies
 *   "Manager → assigned-to-approve" ownership scoping server-side,
 *   travelRequestService.ts / api-contract.md §7; `useTravelRequestList`'s
 *   `toListParams` recognizes only `status`/`employeeId`, so the `approverId`
 *   key documents the intended scope). Non-manager sessions use the
 *   employeeId-scoped query instead — never a role-forbidden query.
 * - `pendingExpenseReviews`: `PENDING` expense claims awaiting review
 *   (FINANCE — no extra filter; the expense service applies "Finance →
 *   review queue" scoping server-side, expenseService.ts / api-contract.md
 *   §7). Non-finance sessions get the employeeId-scoped count (the section
 *   renders only under the FINANCE gate, so the count is unused there).
 */
export function useDashboardSummary(session: SessionView): ServerDataState<DashboardSummaryView> {
  const isManager = session.roles.includes('MANAGER')
  const isFinance = session.roles.includes('FINANCE')

  const myTravelRequests = useTravelRequestList(countQueryState({ status: 'PENDING' }), 'MINE')
  const myExpenses = useExpenseList(
    countQueryState({ status: 'PENDING', employeeId: session.userId }),
  )
  // Approvers (managers, and Finance as top-of-hierarchy fallback) count their own queue;
  // a dual-role EMPLOYEE+MANAGER gets both views independently.
  const pendingApprovals = useTravelRequestList(
    countQueryState({ status: 'PENDING' }),
    isManager || isFinance ? 'APPROVALS' : 'MINE',
  )
  const pendingExpenseReviews = useExpenseList(
    countQueryState(
      isFinance ? { status: 'PENDING' } : { status: 'PENDING', employeeId: session.userId },
    ),
  )

  const isLoading =
    myTravelRequests.isLoading ||
    myExpenses.isLoading ||
    pendingApprovals.isLoading ||
    pendingExpenseReviews.isLoading

  const error =
    myTravelRequests.error ??
    myExpenses.error ??
    pendingApprovals.error ??
    pendingExpenseReviews.error

  const data =
    myTravelRequests.data !== null &&
    myExpenses.data !== null &&
    pendingApprovals.data !== null &&
    pendingExpenseReviews.data !== null
      ? {
          pendingTravelRequests: myTravelRequests.data.totalElements,
          pendingExpenses: myExpenses.data.totalElements,
          pendingApprovals: pendingApprovals.data.totalElements,
          pendingExpenseReviews: pendingExpenseReviews.data.totalElements,
        }
      : null

  // Each sub-`refetch` is stable (useAsyncResource); composing them keeps the
  // returned `refetch` identity stable for consumers (frontend-plan.md §7).
  const refetchMyTravelRequests = myTravelRequests.refetch
  const refetchMyExpenses = myExpenses.refetch
  const refetchPendingApprovals = pendingApprovals.refetch
  const refetchPendingExpenseReviews = pendingExpenseReviews.refetch

  const refetch = useCallback(() => {
    refetchMyTravelRequests()
    refetchMyExpenses()
    refetchPendingApprovals()
    refetchPendingExpenseReviews()
  }, [refetchMyTravelRequests, refetchMyExpenses, refetchPendingApprovals, refetchPendingExpenseReviews])

  return { data, isLoading, error, refetch }
}