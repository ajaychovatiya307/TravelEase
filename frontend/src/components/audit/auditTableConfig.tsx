// Audit Log table configuration (K-04).
// Per frontend-spec.md §6: Entity Type, Entity Id, Actor, Action,
// Before → After, Occurred At; default sort `occurredAt desc`.
//
// Audit records are immutable. The linked-record navigation action is handled
// by the page via `onRowClick`, so this configuration has no action column.

import type { AuditRowView } from '../../models/viewModels'
import type { DataTableColumn, SortState } from '../../shared/DataTable'

export const auditColumns: DataTableColumn<AuditRowView>[] = [
  {
    key: 'entityType',
    header: 'Entity Type',
    sortable: true,
    render: (row) => row.entityType,
  },
  {
    key: 'entityId',
    header: 'Entity Id',
    sortable: true,
    render: (row) => row.entityId,
  },
  {
    key: 'actorId',
    header: 'Actor',
    sortable: true,
    render: (row) => row.actorId,
  },
  {
    key: 'action',
    header: 'Action',
    sortable: true,
    render: (row) => row.actionLabel,
  },
  {
    key: 'beforeStatus',
    header: 'Before → After',
    sortable: false,
    render: (row) => `${row.beforeStatus ?? '—'} → ${row.afterStatus}`,
  },
  {
    key: 'occurredAt',
    header: 'Occurred At',
    sortable: true,
    render: (row) => (
      <time dateTime={row.occurredAt}>{row.occurredAtFormatted}</time>
    ),
  },
]

export const auditDefaultSort: SortState = { field: 'occurredAt', direction: 'desc' }
