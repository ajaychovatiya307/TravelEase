// Admin Users table configuration (K-03).
// Per frontend-spec.md §6: Name, Email, Roles, Active; default sort `fullName asc`.

import type { UserAccountResponse } from '../../models/admin'
import type { DataTableColumn, SortState } from '../../shared/DataTable'

export const usersColumns: DataTableColumn<UserAccountResponse>[] = [
  {
    key: 'fullName',
    header: 'Name',
    sortable: true,
    render: (row) => row.fullName,
  },
  {
    key: 'email',
    header: 'Email',
    sortable: true,
    render: (row) => row.email,
  },
  {
    key: 'roles',
    header: 'Roles',
    sortable: true,
    render: (row) => row.roles.join(', '),
  },
  {
    key: 'active',
    header: 'Active',
    sortable: true,
    render: (row) => (row.active ? 'Active' : 'Inactive'),
  },
]

export const usersDefaultSort: SortState = { field: 'fullName', direction: 'asc' }
