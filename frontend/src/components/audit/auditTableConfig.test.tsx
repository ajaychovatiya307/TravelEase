import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { AuditRowView } from '../../models/viewModels'
import { auditColumns, auditDefaultSort } from './auditTableConfig'

const row: AuditRowView = {
  id: 'AUD-1',
  entityType: 'TRAVEL_REQUEST',
  entityId: 'TR-1',
  actorId: 'U-1',
  action: 'APPROVED',
  beforeStatus: 'PENDING',
  afterStatus: 'APPROVED',
  occurredAt: '2026-03-01T12:30:00Z',
  occurredAtFormatted: 'Mar 1, 2026, 12:30 PM',
  actionLabel: 'Approved',
}

describe('auditTableConfig (K-04)', () => {
  it('defines the six Audit Log columns in specification order', () => {
    expect(auditColumns).toHaveLength(6)
    expect(auditColumns.map((column) => column.header)).toEqual([
      'Entity Type',
      'Entity Id',
      'Actor',
      'Action',
      'Before → After',
      'Occurred At',
    ])
    expect(auditColumns.map((column) => column.key)).toEqual([
      'entityType',
      'entityId',
      'actorId',
      'action',
      'beforeStatus',
      'occurredAt',
    ])
  })

  it('defaults sorting to occurredAt descending', () => {
    expect(auditDefaultSort).toEqual({ field: 'occurredAt', direction: 'desc' })
  })

  it('does not define a row-action column', () => {
    expect(auditColumns.some((column) => column.key === 'actions')).toBe(false)
    expect(auditColumns.some((column) => column.header === 'Actions')).toBe(false)
  })

  it('renders the action label and before-to-after status transition', () => {
    render(
      <>
        <span>{auditColumns.find((column) => column.key === 'action')?.render(row)}</span>
        <span>
          {auditColumns.find((column) => column.key === 'beforeStatus')?.render(row)}
        </span>
      </>,
    )

    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByText('PENDING → APPROVED')).toBeInTheDocument()
  })

  it('renders occurredAt as a time element using the formatted timestamp', () => {
    const column = auditColumns.find((candidate) => candidate.key === 'occurredAt')
    render(<>{column?.render(row)}</>)

    const time = screen.getByText('Mar 1, 2026, 12:30 PM')
    expect(time.tagName).toBe('TIME')
    expect(time).toHaveAttribute('datetime', '2026-03-01T12:30:00Z')
  })

  it('uses a placeholder when a created record has no before status', () => {
    const column = auditColumns.find((candidate) => candidate.key === 'beforeStatus')
    render(<>{column?.render({ ...row, beforeStatus: null })}</>)

    expect(screen.getByText('— → APPROVED')).toBeInTheDocument()
  })
})
