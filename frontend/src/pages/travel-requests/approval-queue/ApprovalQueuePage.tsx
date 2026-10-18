import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

import { useListQueryState } from '../../../hooks/useListQueryState'
import type { ListQueryState } from '../../../hooks/useListQueryState'
import { useTravelRequestList } from '../../../hooks/useTravelRequests'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { DataTable } from '../../../shared/DataTable'
import { ResourceErrorState } from '../../../shared/ResourceErrorState'
import './ApprovalQueuePage.css'
import { TravelRequestRow } from '../../../components/travel-requests/TravelRequestRow'
import type { TravelRequestResponse } from '../../../models/travelRequest'

const columns: DataTableColumn<TravelRequestResponse>[] = [
  {
    key: 'destination',
    header: 'Destination',
    sortable: true,
    render: (request) => <TravelRequestRow request={request} field="destination" />,
  },
  {
    key: 'dates',
    header: 'Dates',
    render: (request) => <TravelRequestRow request={request} field="dates" />,
  },
  {
    key: 'status',
    header: 'Status',
    sortable: true,
    render: (request) => <TravelRequestRow request={request} field="status" />,
  },
  {
    key: 'approver',
    header: 'Approver',
    render: (request) => <TravelRequestRow request={request} field="approver" />,
  },
  {
    key: 'created',
    header: 'Created',
    sortable: true,
    render: (request) => <TravelRequestRow request={request} field="created" />,
  },
]

/**
 * Travel Request Approval Queue (P-08, frontend-spec.md §1/§4.3;
 * frontend-tasks.md I-04).
 *
 * Manager/Finance queue of travel requests awaiting a decision, rendered as a
 * paginated/sortable `DataTable` of `TravelRequestRow` cells (same columns as
 * the P-03 list, frontend-spec.md §6; frontend-api-mapping.md §1.1). Per
 * frontend-spec.md §4.3 the queue view is implicitly scoped to
 * `approverId = self` with a default `status = PENDING` filter:
 * - Approver scoping rides `useTravelRequestList`'s caller-id argument (the
 *   `employeeId` wire param of frontend-api-mapping.md §1.1); the travel
 *   service applies "Manager/Finance → assigned-to-approve" scoping
 *   server-side regardless of these params (api-contract.md §7).
 * - The `status = PENDING` default is applied to the query state on load so
 *   the very first fetch is already PENDING-scoped, and written into the URL
 *   via the D-04 controls when the URL carries no `status`, so the default
 *   is visible on load, refresh, and after every subsequent view mutation.
 *   An explicit `status` param in the URL always wins over the default.
 *
 * A row click navigates directly to the Approval screen (P-07,
 * `/travel-requests/:id/approval` — routeTable.ts), per frontend-spec.md §4.3.
 *
 * Route access (MANAGER/FINANCE only) is enforced by the `RequireRole` guard
 * (routeTable.ts P-08, D-02), so no data is fetched for unauthenticated or
 * ineligible sessions — the page mirrors the P-03 list in this respect.
 */
export function ApprovalQueuePage() {
  const navigate = useNavigate()
  const { queryState, setPage, setSize, setSort, setFilter } = useListQueryState()

  // frontend-spec.md §4.3: default filter `status = PENDING`, applied on load.
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

  // Approver scoping: `APPROVALS` lists only requests routed to the signed-in
  // approver (resolved server-side from the token), independent of any other
  // roles the user holds.
  const { data, isLoading, error, refetch } = useTravelRequestList(effectiveQueryState, 'APPROVALS')

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
    <section className="screen approval-queue-page">
      <header className="page-header approval-queue-page__header">
        <div>
          <h1>Approval Queue</h1>
          <p>Travel requests pending your approval.</p>
        </div>
      </header>

      {error ? (
        <ResourceErrorState error={error} onRetry={refetch} resourceName="Travel requests" />
      ) : null}

      <div className="card">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          getRowId={(request) => request.id}
          isLoading={isLoading}
          emptyMessage="No travel requests pending your approval."
          pagination={pagination}
          onPageChange={setPage}
          onSizeChange={setSize}
          sort={queryState.sort}
          onSortChange={handleSortChange}
          // frontend-spec.md §4.3: from the Queue a row click navigates directly
          // to the Approval screen (P-07, routeTable.ts).
          onRowClick={(request) => navigate(`/travel-requests/${request.id}/approval`)}
        />
      </div>
    </section>
  )
}