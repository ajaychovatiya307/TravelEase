// Expense Claim table configuration (K-02).
// Column definitions + default sort for the P-09 Expense Claim Table,
// per frontend-spec.md §6: columns Travel Request, Amount, Currency,
// Category, Date, Status, Duplicate; default sort `createdAt desc`.
//
// Each column `key` is the backend sortable field name — it flows straight
// into `onSortChange({ field: column.key, direction })` from the shared
// DataTable header. The row action (open detail) is handled by the page via
// `onRowClick`, so no row-action column is defined here.

import type { DataTableColumn, SortState } from '../../shared/DataTable'
import { DuplicateIndicator } from '../../shared/DuplicateIndicator'
import { StatusBadge } from '../../shared/StatusBadge'
import type { ExpenseRowView } from '../../models/viewModels'

export const expenseColumns: DataTableColumn<ExpenseRowView>[] = [
  {
    key: 'travelRequestId',
    header: 'Travel Request',
    sortable: true,
    render: (row) => row.travelRequestId,
  },
  {
    key: 'amount',
    header: 'Amount',
    sortable: true,
    render: (row) => row.amount,
  },
  {
    key: 'currency',
    header: 'Currency',
    sortable: true,
    render: (row) => row.currency,
  },
  {
    key: 'category',
    header: 'Category',
    sortable: true,
    render: (row) => row.category,
  },
  {
    key: 'expenseDate',
    header: 'Date',
    sortable: true,
    render: (row) => <time dateTime={row.expenseDate}>{row.expenseDate}</time>,
  },
  {
    key: 'status',
    header: 'Status',
    sortable: true,
    render: (row) => <StatusBadge status={row.status} />,
  },
  {
    key: 'duplicateFlag',
    header: 'Duplicate',
    sortable: true,
    render: (row) => <DuplicateIndicator duplicateFlag={row.isDuplicate} />,
  },
]

export const expenseDefaultSort: SortState = { field: 'createdAt', direction: 'desc' }