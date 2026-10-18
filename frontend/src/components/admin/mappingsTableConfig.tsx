// Admin Employee-Manager Mapping table configuration (K-03).
// Per frontend-spec.md §6: Employee, Manager, Effective From;
// default sort `employeeId asc`.

import type { EmployeeManagerMappingResponse } from '../../models/admin'
import type { DataTableColumn, SortState } from '../../shared/DataTable'

export const mappingsColumns: DataTableColumn<EmployeeManagerMappingResponse>[] = [
  {
    key: 'employeeId',
    header: 'Employee',
    sortable: true,
    render: (row) => row.employeeId,
  },
  {
    key: 'managerId',
    header: 'Manager',
    sortable: true,
    render: (row) => row.managerId ?? 'Not assigned',
  },
  {
    key: 'effectiveFrom',
    header: 'Effective From',
    sortable: true,
    render: (row) => (
      <time dateTime={row.effectiveFrom}>{row.effectiveFrom}</time>
    ),
  },
]

export const mappingsDefaultSort: SortState = { field: 'employeeId', direction: 'asc' }
