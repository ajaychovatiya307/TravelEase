import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

import { useListQueryState } from '../../../hooks/useListQueryState'
import type { ListQueryState } from '../../../hooks/useListQueryState'
import { useExpenseList } from '../../../hooks/useExpenses'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { DataTable } from '../../../shared/DataTable'
import { ResourceErrorState } from '../../../shared/ResourceErrorState'
import './ReviewQueuePage.css'
import { ExpenseRow } from '../../../components/expenses/ExpenseRow'
import type { ExpenseClaimResponse, ExpenseClaimStatus } from '../../../models/expenseClaim'

const columns: DataTableColumn<ExpenseClaimResponse>[] = [
  {
    key: 'travelRequestId',
    header: 'Travel Request',
    render: (claim) => <ExpenseRow claim={claim} field="travelRequestId" />,
  },
  {
    key: 'amount',
    header: 'Amount',
    sortable: true,
    render: (claim) => <ExpenseRow claim={claim} field="amount" />,
  },
  {
    key: 'currency',
    header: 'Currency',
    render: (claim) => <ExpenseRow claim={claim} field="currency" />,
  },
  {
    key: 'category',
    header: 'Category',
    render: (claim) => <ExpenseRow claim={claim} field="category" />,
  },
  {
    key: 'expenseDate',
    header: 'Expense Date',
    sortable: true,
    render: (claim) => <ExpenseRow claim={claim} field="expenseDate" />,
  },
  {
    key: 'status',
    header: 'Status',
    sortable: true,
    render: (claim) => <ExpenseRow claim={claim} field="status" />,
  },
  {
    // frontend-spec.md §4.7 / frontend-clarification.md Q8: duplicate-flagged
    // rows are visually flagged here via the shared `DuplicateIndicator`
    // (icon + label, not color alone).
    key: 'duplicateFlag',
    header: 'Duplicate',
    render: (claim) => <ExpenseRow claim={claim} field="duplicateFlag" />,
  },
]

/**
 * Tri-state options for the `duplicateFlag` filter (FR-FIN-02). The option
 * values are the wire format `useExpenseList`'s `parseBooleanFilter`
 * understands (`'true'`/`'false'`); the empty value removes the filter
 * entirely (`useListQueryState.setFilter`), which is the filter's clear
 * control (frontend-clarification.md Q19).
 */
const DUPLICATE_FILTER_OPTIONS = [
  { value: '', label: 'All claims' },
  { value: 'true', label: 'Duplicates only' },
  { value: 'false', label: 'Non-duplicates' },
] as const

const STATUS_FILTER_OPTIONS: readonly { value: ExpenseClaimStatus; label: string }[] = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'REIMBURSED', label: 'Reimbursed' },
]

/**
 * Expense Review Queue (P-14, frontend-spec.md §1/§4.7;
 * frontend-tasks.md I-09).
 *
 * Finance-wide queue of expense claims awaiting a review decision, rendered
 * as a paginated/sortable `DataTable` of `ExpenseRow` cells (same columns as
 * the P-09 list, frontend-spec.md §6; frontend-api-mapping.md §1.2). Unlike
 * the P-08 travel approval queue, this view is NOT scoped to the caller:
 * Finance reviews claims from all employees (frontend-spec.md §4.7 — "Finance
 * view unscoped (reviewable queue)"), so `useExpenseList` is called without a
 * caller-id argument.
 *
 * Per frontend-spec.md §4.7 and frontend-clarification.md Q8 the queue
 * defaults to `status = PENDING`, mirroring the P-08 mechanism:
 * - The `status = PENDING` default is applied to the query state on load so
 *   the very first fetch is already PENDING-scoped, and written into the URL
 *   via the D-04 controls when the URL carries no `status`, so the default
 *   is visible on load, refresh, and after every subsequent view mutation.
 *   An explicit `status` param in the URL always wins over the default (e.g.
 *   the `?status=APPROVED` review queue of frontend-spec.md UJ-06).
 * - A `duplicateFlag` filter is available to the user as a tri-state control
 *   ("All claims / Duplicates only / Non-duplicates"); the selection is
 *   persisted in the URL like every other filter (D-04) and combines with
 *   `status` via AND server-side (frontend-clarification.md Q19).
 *
 * A row click navigates to the Expense Claim Detail route (P-10,
 * `/expenses/:id` — routeTable.ts), where the Finance Review (P-13) action is
 * available for `PENDING` claims (frontend-spec.md §4.8).
 *
 * Route access (FINANCE only) is enforced by the `RequireRole` guard
 * (routeTable.ts P-14, D-02), so no data is fetched for unauthenticated or
 * ineligible sessions — the page mirrors the P-03 list in this respect.
 */
export function ReviewQueuePage() {
  const navigate = useNavigate()
  const { queryState, setPage, setSize, setSort, setFilter } = useListQueryState()

  // frontend-spec.md §4.7: default filter `status = PENDING`, applied on load.
  // Derived (not URL-derived) so the first fetch is already PENDING-scoped
  // before the URL-sync effect below writes the default into the query string.
  const statusFilter = queryState.filters.status ?? 'PENDING'
  const effectiveQueryState: ListQueryState = {
    ...queryState,
    filters: { ...queryState.filters, status: statusFilter },
  }

  // Reflect the default in the URL when a deep link / first visit carries no
  // `status` param (D-04: the mutation pushes a new history entry). A URL
  // that already carries an explicit `status` is left untouched.
  useEffect(() => {
    if (queryState.filters.status === undefined) {
      setFilter('status', 'PENDING')
    }
  }, [queryState.filters.status, setFilter])

  // Finance-wide: no caller-id argument (contrast P-08's approverId = self
  // scope). The `duplicateFlag` filter rides the URL state (FR-FIN-02).
  const { data, isLoading, error, refetch } = useExpenseList(effectiveQueryState)

  // Normalize to the control's tri-state: an absent or unrecognized URL
  // value (parseBooleanFilter would drop it from the request) reads as "All
  // claims".
  const rawDuplicateFilter = queryState.filters.duplicateFlag
  const duplicateFlagFilter =
    rawDuplicateFilter === 'true' || rawDuplicateFilter === 'false' ? rawDuplicateFilter : ''

  const pagination = {
    page: data?.page ?? queryState.page,
    size: data?.size ?? queryState.size,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
  }

  const handleSortChange = (sort: SortState) => {
    setSort(sort)
  }

  return (
    <section className="screen review-queue-page">
      <header className="page-header review-queue-page__header">
        <div>
          <h1>Expense Review Queue</h1>
          <p>Expense claims pending Finance review.</p>
        </div>
      </header>

      <div className="card">
      <div className="card-body review-queue-page__filters">
        <div className="review-queue-page__filter">
          <label htmlFor="review-queue-status-filter">Status</label>
          <select
            id="review-queue-status-filter"
            className="input review-queue-page__filter-select"
            value={statusFilter}
            onChange={(event) => setFilter('status', event.target.value)}
          >
            {STATUS_FILTER_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="review-queue-page__filter">
          <label htmlFor="review-queue-duplicate-flag-filter">Duplicates</label>
          <select
            id="review-queue-duplicate-flag-filter"
            className="input review-queue-page__filter-select"
            value={duplicateFlagFilter}
            onChange={(event) => setFilter('duplicateFlag', event.target.value)}
          >
            {DUPLICATE_FILTER_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error ? (
        <ResourceErrorState error={error} onRetry={refetch} resourceName="Expense claims" />
      ) : null}

      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        getRowId={(claim) => claim.id}
        isLoading={isLoading}
        emptyMessage="No expense claims match the current filters."
        pagination={pagination}
        onPageChange={setPage}
        onSizeChange={setSize}
        sort={queryState.sort}
        onSortChange={handleSortChange}
        // frontend-spec.md §4.7: from the Queue a row click navigates to the
        // Expense Claim Detail (P-10, routeTable.ts), where the Finance Review
        // (P-13) decision is made.
        onRowClick={(claim) => navigate(`/expenses/${claim.id}`)}
      />
      </div>
    </section>
  )
}