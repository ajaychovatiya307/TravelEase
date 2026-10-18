// Travel Request table configuration tests (K-01). The config is pure data
// plus render functions, so column metadata is asserted directly and the
// row renders are mounted against a fully typed `TravelRequestRowView`
// fixture, matching the fixture style of TravelRequestSummary.test.tsx.

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { TravelRequestRowView } from '../../models/viewModels'
import { travelRequestColumns, travelRequestDefaultSort } from './travelRequestTableConfig'

/** Fully typed fixture for a PENDING travel request row; any field overridable. */
function makeRow(overrides: Partial<TravelRequestRowView> = {}): TravelRequestRowView {
  return {
    id: 'TR-1',
    employeeId: 'U1',
    destination: 'Mumbai',
    startDate: '2026-03-02',
    endDate: '2026-03-05',
    justification: 'Client site visit for the Q2 rollout.',
    status: 'PENDING',
    approverId: null,
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-02-20T09:15:00Z',
    updatedAt: '2026-02-21T10:30:00Z',
    dateRange: 'Mar 2, 2026 – Mar 5, 2026',
    statusLabel: 'Pending',
    ...overrides,
  }
}

function renderColumn(key: string, row: TravelRequestRowView) {
  const column = travelRequestColumns.find((candidate) => candidate.key === key)
  if (!column) throw new Error(`No column with key "${key}"`)
  return render(<>{column.render(row)}</>)
}

describe('travelRequestTableConfig (K-01)', () => {
  it('defines exactly five columns with the frontend-spec.md §6 headers in order', () => {
    expect(travelRequestColumns).toHaveLength(5)
    expect(travelRequestColumns.map((column) => column.header)).toEqual([
      'Destination',
      'Dates',
      'Status',
      'Approver',
      'Created',
    ])
  })

  it('uses the backend sortable field names as column keys, in order', () => {
    expect(travelRequestColumns.map((column) => column.key)).toEqual([
      'destination',
      'startDate',
      'status',
      'approverId',
      'createdAt',
    ])
  })

  it('marks every column sortable', () => {
    expect(travelRequestColumns.every((column) => column.sortable)).toBe(true)
  })

  it('defaults the sort to createdAt desc', () => {
    expect(travelRequestDefaultSort).toEqual({ field: 'createdAt', direction: 'desc' })
  })

  it('renders the Dates column from the flattened dateRange', () => {
    renderColumn('startDate', makeRow())

    expect(screen.getByText('Mar 2, 2026 – Mar 5, 2026')).toBeInTheDocument()
  })

  it('renders the Status column through the shared StatusBadge', () => {
    renderColumn('status', makeRow({ status: 'PENDING' }))

    const badge = screen.getByText('Pending').closest('.status-badge') as HTMLElement
    expect(badge).toHaveClass('status-badge--pending')
  })

  it('shows "Not yet assigned" in the Approver column while approverId is null', () => {
    renderColumn('approverId', makeRow({ approverId: null }))

    expect(screen.getByText('Not yet assigned')).toBeInTheDocument()
  })

  it('renders the assigned approver id when one is set', () => {
    renderColumn('approverId', makeRow({ approverId: 'U2' }))

    expect(screen.getByText('U2')).toBeInTheDocument()
    expect(screen.queryByText('Not yet assigned')).not.toBeInTheDocument()
  })
})