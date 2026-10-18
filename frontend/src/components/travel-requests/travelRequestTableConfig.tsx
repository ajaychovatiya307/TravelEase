// Travel Request table configuration (K-01).
// Column definitions + default sort for the P-03/P-08 Travel Request Table,
// per frontend-spec.md §6: columns Destination, Dates, Status, Approver,
// Created; default sort `createdAt desc`.
//
// Each column `key` is the backend sortable field name — it flows straight
// into `onSortChange({ field: column.key, direction })` from the shared
// DataTable header. The row action (open detail) is handled by the page via
// `onRowClick`, so no row-action column is defined here.

import type { DataTableColumn, SortState } from '../../shared/DataTable'
import { StatusBadge } from '../../shared/StatusBadge'
import type { TravelRequestRowView } from '../../models/viewModels'

export const travelRequestColumns: DataTableColumn<TravelRequestRowView>[] = [
  {
    key: 'destination',
    header: 'Destination',
    sortable: true,
    render: (row) => row.destination,
  },
  {
    key: 'startDate',
    header: 'Dates',
    sortable: true,
    render: (row) => row.dateRange,
  },
  {
    key: 'status',
    header: 'Status',
    sortable: true,
    render: (row) => <StatusBadge status={row.status} />,
  },
  {
    key: 'approverId',
    header: 'Approver',
    sortable: true,
    render: (row) => row.approverId ?? 'Not yet assigned',
  },
  {
    key: 'createdAt',
    header: 'Created',
    sortable: true,
    render: (row) => <time dateTime={row.createdAt}>{row.createdAt}</time>,
  },
]

export const travelRequestDefaultSort: SortState = { field: 'createdAt', direction: 'desc' }