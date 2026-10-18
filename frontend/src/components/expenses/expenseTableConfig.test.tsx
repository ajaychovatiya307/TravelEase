// Expense Claim table configuration tests (K-02). The config is pure data
// plus render functions, so column metadata is asserted directly and the
// row renders are mounted against a fully typed `ExpenseRowView` fixture,
// matching the fixture style of travelRequestTableConfig.test.tsx.

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { ExpenseRowView } from '../../models/viewModels'
import { expenseColumns, expenseDefaultSort } from './expenseTableConfig'

/** Fully typed fixture for a PENDING expense claim row; any field overridable. */
function makeRow(overrides: Partial<ExpenseRowView> = {}): ExpenseRowView {
  return {
    id: 'EXP-1',
    employeeId: 'U1',
    travelRequestId: 'TR-1',
    amount: 1250,
    currency: 'EUR',
    category: 'Lodging',
    expenseDate: '2026-03-02',
    status: 'PENDING',
    duplicateFlag: false,
    rejectionReason: null,
    reimbursedAt: null,
    documents: [],
    createdAt: '2026-02-20T09:15:00Z',
    updatedAt: '2026-02-21T10:30:00Z',
    isDuplicate: false,
    ...overrides,
  }
}

function renderColumn(key: string, row: ExpenseRowView) {
  const column = expenseColumns.find((candidate) => candidate.key === key)
  if (!column) throw new Error(`No column with key "${key}"`)
  return render(<>{column.render(row)}</>)
}

describe('expenseTableConfig (K-02)', () => {
  it('defines exactly seven columns with the frontend-spec.md §6 headers in order', () => {
    expect(expenseColumns).toHaveLength(7)
    expect(expenseColumns.map((column) => column.header)).toEqual([
      'Travel Request',
      'Amount',
      'Currency',
      'Category',
      'Date',
      'Status',
      'Duplicate',
    ])
  })

  it('uses the backend sortable field names as column keys, in order', () => {
    expect(expenseColumns.map((column) => column.key)).toEqual([
      'travelRequestId',
      'amount',
      'currency',
      'category',
      'expenseDate',
      'status',
      'duplicateFlag',
    ])
  })

  it('marks every column sortable', () => {
    expect(expenseColumns.every((column) => column.sortable)).toBe(true)
  })

  it('defaults the sort to createdAt desc', () => {
    expect(expenseDefaultSort).toEqual({ field: 'createdAt', direction: 'desc' })
  })

  it('renders the Amount column with the raw amount value', () => {
    renderColumn('amount', makeRow({ amount: 1248.5 }))

    expect(screen.getByText('1248.5')).toBeInTheDocument()
  })

  it('renders the Date column as a time element with the expenseDate', () => {
    renderColumn('expenseDate', makeRow({ expenseDate: '2026-03-02' }))

    const time = screen.getByText('2026-03-02') as HTMLElement
    expect(time.tagName).toBe('TIME')
    expect(time).toHaveAttribute('datetime', '2026-03-02')
  })

  it('renders the Status column through the shared StatusBadge', () => {
    renderColumn('status', makeRow({ status: 'PENDING' }))

    const badge = screen.getByText('Pending').closest('.status-badge') as HTMLElement
    expect(badge).toHaveClass('status-badge--pending')
  })

  it('shows the DuplicateIndicator text when isDuplicate is true', () => {
    renderColumn('duplicateFlag', makeRow({ isDuplicate: true }))

    const indicator = screen.getByText('Possible duplicate').closest(
      '.duplicate-indicator',
    ) as HTMLElement
    expect(indicator).toHaveClass('duplicate-indicator')
  })

  it('renders nothing in the Duplicate column when isDuplicate is false', () => {
    renderColumn('duplicateFlag', makeRow({ isDuplicate: false }))

    expect(screen.queryByText('Possible duplicate')).not.toBeInTheDocument()
  })
})