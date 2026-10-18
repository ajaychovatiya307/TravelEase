import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { useAuth } from '../../../context/AuthContext'
import { sortToParam, useListQueryState } from '../../../hooks/useListQueryState'
import { useTravelRequestList } from '../../../hooks/useTravelRequests'
import * as travelRequestService from '../../../services/travelRequestService'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { DataTable } from '../../../shared/DataTable'
import { TravelRequestRow } from '../../../components/travel-requests/TravelRequestRow'
import type { TravelRequestResponse, TravelRequestStatus } from '../../../models/travelRequest'
import { downloadCsv, fetchAllPages } from '../../../utils/csvExport'
import './TravelRequestListPage.css'

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

const exportColumns = [
  { header: 'ID', value: (request: TravelRequestResponse) => request.id },
  { header: 'Employee ID', value: (request: TravelRequestResponse) => request.employeeId },
  { header: 'Destination', value: (request: TravelRequestResponse) => request.destination },
  { header: 'Start Date', value: (request: TravelRequestResponse) => request.startDate },
  { header: 'End Date', value: (request: TravelRequestResponse) => request.endDate },
  { header: 'Justification', value: (request: TravelRequestResponse) => request.justification },
  { header: 'Status', value: (request: TravelRequestResponse) => request.status },
  { header: 'Approver ID', value: (request: TravelRequestResponse) => request.approverId },
  {
    header: 'Rejection Reason',
    value: (request: TravelRequestResponse) => request.rejectionReason,
  },
  { header: 'Created At', value: (request: TravelRequestResponse) => request.createdAt },
  { header: 'Updated At', value: (request: TravelRequestResponse) => request.updatedAt },
]

export function TravelRequestListPage() {
  const navigate = useNavigate()
  const { authState } = useAuth()
  const { queryState, setPage, setSize, setSort } = useListQueryState()
  const isAdmin = authState.roles.includes('ADMIN')
  const [isExporting, setIsExporting] = useState(false)
  const [exportError, setExportError] = useState(false)
  // "My travel requests" for anyone who files requests (incl. a manager who is also an
  // employee); oversight roles without the EMPLOYEE role see every request.
  const canFileRequests = authState.roles.includes('EMPLOYEE')
  const isOversight = isAdmin || authState.roles.includes('FINANCE')
  const { data, isLoading, error, refetch } = useTravelRequestList(
    queryState,
    !canFileRequests && isOversight ? 'ALL' : 'MINE',
  )

  const pagination = {
    page: data?.page ?? queryState.page,
    size: data?.size ?? queryState.size,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
  }

  const handleSortChange = (sort: SortState) => {
    setSort(sort)
  }

  const handleExport = async () => {
    setIsExporting(true)
    setExportError(false)
    try {
      const requests = await fetchAllPages((page, size) =>
        travelRequestService.list({
          page,
          size,
          sort: sortToParam(queryState.sort),
          status: queryState.filters.status as TravelRequestStatus | undefined,
        }),
      )
      downloadCsv(
        `travel-requests-${new Date().toISOString().slice(0, 10)}.csv`,
        requests,
        exportColumns,
      )
    } catch {
      setExportError(true)
    } finally {
      setIsExporting(false)
    }
  }

  return (
    <section className="screen" data-screen="travel-requests" data-section="travel">
      <div className="page-header">
        <div>
          <h1>{isAdmin ? 'Travel Requests' : 'My Travel Requests'}</h1>
          <p>
            {isAdmin
              ? 'View and export travel requests.'
              : 'View and manage your submitted travel requests.'}
          </p>
        </div>
        <div className="travel-request-list-page__actions">
          {isAdmin ? (
            <button
              className="button button-secondary"
              type="button"
              disabled={isExporting}
              onClick={handleExport}
            >
              {isExporting ? 'Exporting…' : 'Export CSV'}
            </button>
          ) : null}
          <Link className="button button-primary" to="/travel-requests/new">
            <i className="icon icon-plus" />
            New Travel Request
          </Link>
        </div>
      </div>

      {exportError ? (
        <p className="required" role="alert" style={{ fontSize: 13 }}>
          Travel requests could not be exported. Please try again.
        </p>
      ) : null}

      <div className="card">
        {error ? (
          <>
            <p className="empty-state" role="alert">
              Travel requests could not be loaded. Please try again.
            </p>
            <button
              className="button button-secondary button-small"
              type="button"
              onClick={refetch}
            >
              Dismiss
            </button>
          </>
        ) : (
          <DataTable
            columns={columns}
            referenceLayout
            rows={data?.content ?? []}
            getRowId={(request) => request.id}
            isLoading={isLoading}
            emptyMessage="No travel requests found."
            pagination={pagination}
            onPageChange={setPage}
            onSizeChange={setSize}
            sort={queryState.sort}
            onSortChange={handleSortChange}
            onRowClick={(request) => navigate(`/travel-requests/${request.id}`)}
          />
        )}
      </div>
    </section>
  )
}
