// ApprovalHistoryList component tests (I-05, P-04). The component is pure
// presentation, so no router or providers are needed — every test mounts it
// with fully typed `ApprovalHistoryEntry` fixtures. Entries arrive in
// chronological order and must render in the given order (no re-sorting).

import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { ApprovalHistoryEntry } from '../../models/viewModels'
import { ApprovalHistoryList } from './ApprovalHistoryList'

/** Typed fixture producing `ApprovalHistoryEntry` objects; any field overridable. */
function makeEntry(overrides: Partial<ApprovalHistoryEntry> = {}): ApprovalHistoryEntry {
  return {
    decidedBy: 'U2',
    decision: 'APPROVED',
    reason: null,
    decidedAt: '2026-02-22T10:00:00Z',
    ...overrides,
  }
}

describe('ApprovalHistoryList (I-05, P-04)', () => {
  it('renders the "Approval history" heading as an h2', () => {
    render(<ApprovalHistoryList entries={[makeEntry()]} />)

    expect(screen.getByRole('heading', { level: 2, name: 'Approval history' })).toBeInTheDocument()
  })

  it('renders an approved entry with its decision text, decider, and timestamp', () => {
    render(
      <ApprovalHistoryList
        entries={[makeEntry({ decidedBy: 'U2', decidedAt: '2026-02-22T10:00:00Z' })]}
      />,
    )

    // The decision is text + icon (the icon is hidden from assistive tech);
    // the visible text — never color alone — carries the meaning.
    const decision = screen.getByText('Approved')
    expect(decision.closest('.approval-history-list__decision--approved')).not.toBeNull()
    expect(screen.getByText('Decided by')).toBeInTheDocument()
    expect(screen.getByText('U2')).toBeInTheDocument()
    expect(screen.getByText('Decided at')).toBeInTheDocument()
    const time = screen.getByText('2026-02-22T10:00:00Z')
    expect(time).toHaveAttribute('datetime', '2026-02-22T10:00:00Z')
  })

  it('renders a rejected entry with its reason', () => {
    render(
      <ApprovalHistoryList
        entries={[
          makeEntry({
            decision: 'REJECTED',
            decidedBy: 'U3',
            reason: 'The trip exceeds the remaining quarterly budget.',
            decidedAt: '2026-02-23T15:30:00Z',
          }),
        ]}
      />,
    )

    const decision = screen.getByText('Rejected')
    expect(decision.closest('.approval-history-list__decision--rejected')).not.toBeNull()
    expect(screen.getByText('U3')).toBeInTheDocument()
    expect(screen.getByText('2026-02-23T15:30:00Z')).toBeInTheDocument()
    const reason = screen.getByText('The trip exceeds the remaining quarterly budget.')
    expect(reason.closest('.approval-history-list__reason')).not.toBeNull()
  })

  it('renders no reason for an approved entry whose reason is null', () => {
    const { container } = render(<ApprovalHistoryList entries={[makeEntry({ reason: null })]} />)

    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.queryByText('Reason')).not.toBeInTheDocument()
    expect(container.querySelector('.approval-history-list__reason')).toBeNull()
  })

  it('renders entries in the order given, without re-sorting', () => {
    const entries = [
      makeEntry({
        decision: 'REJECTED',
        decidedBy: 'U1',
        reason: 'First submission rejected.',
        decidedAt: '2026-02-20T09:00:00Z',
      }),
      makeEntry({
        decision: 'APPROVED',
        decidedBy: 'U2',
        decidedAt: '2026-02-22T10:00:00Z',
      }),
    ]
    render(<ApprovalHistoryList entries={entries} />)

    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('Rejected')).toBeInTheDocument()
    expect(within(items[0]).getByText('U1')).toBeInTheDocument()
    expect(within(items[0]).getByText('2026-02-20T09:00:00Z')).toBeInTheDocument()
    expect(within(items[1]).getByText('Approved')).toBeInTheDocument()
    expect(within(items[1]).getByText('U2')).toBeInTheDocument()
    expect(within(items[1]).getByText('2026-02-22T10:00:00Z')).toBeInTheDocument()
  })

  it('shows the shared EmptyState message when there are no entries', () => {
    const { container } = render(<ApprovalHistoryList entries={[]} />)

    expect(screen.getByText('No approval decisions recorded yet.')).toBeInTheDocument()
    expect(container.querySelector('.empty-state')).not.toBeNull()
    expect(container.querySelector('ol')).toBeNull()
  })
})