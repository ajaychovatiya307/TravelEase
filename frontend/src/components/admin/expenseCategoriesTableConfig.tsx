// Admin Expense Category table configuration (K-03).
// Per frontend-spec.md §6: Code, Label, Active; default sort `code asc`.

import type { ExpenseCategoryResponse } from '../../models/admin'
import type { DataTableColumn, SortState } from '../../shared/DataTable'

export const expenseCategoriesColumns: DataTableColumn<ExpenseCategoryResponse>[] = [
  {
    key: 'code',
    header: 'Code',
    sortable: true,
    render: (row) => row.code,
  },
  {
    key: 'label',
    header: 'Label',
    sortable: true,
    render: (row) => row.label,
  },
  {
    key: 'active',
    header: 'Active',
    sortable: true,
    render: (row) => (row.active ? 'Active' : 'Inactive'),
  },
]

export const expenseCategoriesDefaultSort: SortState = {
  field: 'code',
  direction: 'asc',
}
