// Admin Policy Config table configuration (K-03).
// Per frontend-spec.md §6: Category, Threshold, Currency, Active;
// default sort `categoryCode asc`.

import type { PolicyConfigResponse } from '../../models/admin'
import type { DataTableColumn, SortState } from '../../shared/DataTable'

export const policyConfigsColumns: DataTableColumn<PolicyConfigResponse>[] = [
  {
    key: 'categoryCode',
    header: 'Category',
    sortable: true,
    render: (row) => row.categoryCode,
  },
  {
    key: 'receiptThresholdAmount',
    header: 'Threshold',
    sortable: true,
    render: (row) => row.receiptThresholdAmount,
  },
  {
    key: 'currency',
    header: 'Currency',
    sortable: true,
    render: (row) => row.currency,
  },
  {
    key: 'active',
    header: 'Active',
    sortable: true,
    render: (row) => (row.active ? 'Active' : 'Inactive'),
  },
]

export const policyConfigsDefaultSort: SortState = {
  field: 'categoryCode',
  direction: 'asc',
}
