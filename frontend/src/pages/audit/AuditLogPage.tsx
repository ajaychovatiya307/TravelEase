// Audit Log Viewer page (I-15, frontend-tasks.md — "I. Pages"): hosts the
// P-20 Audit Log screen (frontend-spec.md §4.13; frontend-api-mapping.md
// §1.4 P-20 row) on top of `useAuditList` (F-06) and the URL-synchronized
// list query state (D-04, `useListQueryState`). The page is read-only by
// contract (FR-AUD-01): a paginated, sortable, filterable table of
// immutable audit records — no create/edit/delete controls and no row-click
// navigation (frontend-spec.md §4.13 acceptance criteria).
//
// View state: `page`/`size`/`sort`/`filters` live in the URL query string
// (D-04); `useAuditList` maps `entityType`/`entityId`/`actorId`/`from`/`to`
// plus page/size/sort to the `auditService.list` params (frontend-api-
// mapping.md §1.4 P-20 row). Default sort is `occurredAt,desc` — audit
// records are listed by occurrence time, newest first (TR-09).
//
// Rows are derived per TR-09 (frontend-api-mapping.md §2): the envelope's
// `content` array is mapped to `AuditRowView` (E-07), adding
// `occurredAtFormatted` (local date-time display string) and `actionLabel`
// (human-readable action); the remaining columns read the DTO fields
// directly. The "Before → After" column renders `beforeStatus → afterStatus`,
// or just `afterStatus` when `beforeStatus` is null (a `CREATED` action has
// no before-status — models/audit.ts).
//
// Fetch errors (frontend-api-mapping.md §1.4 P-20 row): `FORBIDDEN_ACTION`
// (403 — the caller is not Admin/Finance) redirects to `/access-denied`;
// anything else → error banner.
//
// Filter commit (D-04): `AuditFilterBar.onApply` feeds
// `controls.setFilters` with every empty-string value dropped — an empty
// value means "not filtered on this key", and `setFilters` replaces the
// whole filter set. `onClear` feeds `controls.clearAllFilters`.

import { useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'

import { AuditFilterBar } from '../../components/audit/AuditFilterBar'
import { useAuth } from '../../context/AuthContext'
import type { AuditFilterValues } from '../../components/audit/AuditFilterBar'
import { useAuditList } from '../../hooks/useAudit'
import { useListQueryState } from '../../hooks/useListQueryState'
import type { AuditActionType, AuditRecordResponse } from '../../models/audit'
import type { AuditRowView } from '../../models/viewModels'
import { isClassifiedErrorInstance } from '../../services/httpClient'
import { DataTable } from '../../shared/DataTable'
import type { DataTableColumn, PaginationState } from '../../shared/DataTable'
import { ErrorBanner } from '../../shared/ErrorBanner'
import { ResourceErrorState } from '../../shared/ResourceErrorState'
import './AuditLogPage.css'

/** Human-readable label per `AuditActionType` (TR-09, frontend-api-mapping.md §2). */
const ACTION_LABELS: Record<AuditActionType, string> = {
  CREATED: 'Created',
  UPDATED: 'Updated',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  RESUBMITTED: 'Resubmitted',
  REIMBURSED: 'Reimbursed',
  SUBMITTED: 'Submitted',
  CANCELLED: 'Cancelled',
  REASSIGNED: 'Reassigned',
  DEACTIVATED: 'Deactivated',
  ACTIVATED: 'Activated',
  ROLE_GRANTED: 'Role granted',
  ROLE_REVOKED: 'Role revoked',
  PASSWORD_RESET: 'Password reset',
  PASSWORD_CHANGED: 'Password changed',
}

/** Renders an ISO 8601 contract timestamp (`occurredAt`) as local `YYYY-MM-DD HH:mm`. */
function formatOccurredAt(occurredAt: string): string {
  const dateTimePart = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(occurredAt)
  return dateTimePart !== null ? `${dateTimePart[1]} ${dateTimePart[2]}` : occurredAt
}

/**
 * Maps one `AuditRecordResponse` to its display shape `AuditRowView` (TR-09,
 * frontend-api-mapping.md §2): formatted local date-time plus human-readable
 * action label; the remaining columns read the DTO fields directly.
 */
function toAuditRowView(record: AuditRecordResponse): AuditRowView {
  return {
    ...record,
    occurredAtFormatted: formatOccurredAt(record.occurredAt),
    actionLabel: ACTION_LABELS[record.action],
  }
}

/** Read-only table columns (frontend-spec.md §4.13) — no row actions anywhere. */
const COLUMNS: DataTableColumn<AuditRowView>[] = [
  {
    key: 'occurredAt',
    header: 'Timestamp',
    render: (row) => row.occurredAtFormatted,
    sortable: true,
  },
  { key: 'action', header: 'Action', render: (row) => row.actionLabel },
  { key: 'entityType', header: 'Entity Type', render: (row) => row.entityType },
  { key: 'entityId', header: 'Entity Id', render: (row) => row.entityId },
  { key: 'actorId', header: 'Actor', render: (row) => row.actorId },
  {
    key: 'statusTransition',
    header: 'Before → After',
    render: (row) =>
      row.afterStatus === null
        ? '—'
        : row.beforeStatus !== null
          ? `${row.beforeStatus} → ${row.afterStatus}`
          : row.afterStatus,
  },
  { key: 'details', header: 'Details', render: (row) => row.details ?? '' },
]

/**
 * P-20 Audit Log Viewer (`/audit`): read-only, paginated audit table with
 * the `AuditFilterBar` filters, sorted by occurrence time (newest first).
 * Fetch errors follow the P-20 mapping (frontend-api-mapping.md §1.4):
 * `FORBIDDEN_ACTION` → redirect to `/access-denied`, anything else → error
 * banner.
 */
export function AuditLogPage() {
  const navigate = useNavigate()
  const { authState } = useAuth()
  const controls = useListQueryState({
    defaultSort: { field: 'occurredAt', direction: 'desc' },
  })
  const { queryState } = controls
  const { data, isLoading, error, refetch } = useAuditList(queryState)

  useEffect(() => {
    if (
      error !== null &&
      isClassifiedErrorInstance(error) &&
      error.category === 'FORBIDDEN_ACTION'
    ) {
      navigate('/access-denied')
    }
  }, [error, navigate])

  const rows: AuditRowView[] = useMemo(() => (data?.content ?? []).map(toAuditRowView), [data])

  const pagination: PaginationState =
    data !== null
      ? {
          page: data.page,
          size: data.size,
          totalElements: data.totalElements,
          totalPages: data.totalPages,
        }
      : { page: queryState.page, size: queryState.size, totalElements: 0, totalPages: 0 }

  const filterValues: AuditFilterValues = {
    entityType: queryState.filters.entityType ?? '',
    entityId: queryState.filters.entityId ?? '',
    actorId: queryState.filters.actorId ?? '',
    from: queryState.filters.from ?? '',
    to: queryState.filters.to ?? '',
  }

  // Commit the applied filters: drop empty-string values (empty = "not
  // filtered on this key") — `setFilters` replaces the whole filter set
  // (D-04), so omitted keys are the removed ones.
  const handleApplyFilters = (values: AuditFilterValues): void => {
    const next: Record<string, string> = {}
    for (const [key, value] of Object.entries(values)) {
      if (value.length > 0) next[key] = value
    }
    controls.setFilters(next)
  }

  if (error !== null) {
    if (isClassifiedErrorInstance(error) && error.category === 'FORBIDDEN_ACTION') {
      return null
    }
    if (isClassifiedErrorInstance(error) && error.category === 'RESOURCE_NOT_FOUND') {
      return <ResourceErrorState error={error} onRetry={refetch} resourceName="Audit records" />
    }
    return <ErrorBanner message="Something went wrong. Please try again later." onDismiss={refetch} />
  }

  return (
    <section className="screen audit-log-page">
      <header className="page-header">
        <div>
          <h1 className="audit-log-page__heading">Audit Log</h1>
          <p className="audit-log-page__note">Read-only audit trail — records are immutable.</p>
        </div>
      </header>

      <div className="card audit-log-page__filter-bar">
        <AuditFilterBar
          filters={filterValues}
          onApply={handleApplyFilters}
          onClear={controls.clearAllFilters}
          disabled={isLoading}
          includeAdministrative={authState.roles.includes('ADMIN')}
        />
      </div>

      <div className="card audit-log-page__table">
        <DataTable
          columns={COLUMNS}
          rows={rows}
          getRowId={(row) => row.id}
          isLoading={isLoading}
          emptyMessage="No audit records match the current filters."
          pagination={pagination}
          onPageChange={controls.setPage}
          onSizeChange={controls.setSize}
          sort={queryState.sort}
          onSortChange={controls.setSort}
        />
      </div>
    </section>
  )
}
