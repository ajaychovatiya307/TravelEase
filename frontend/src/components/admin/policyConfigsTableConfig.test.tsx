import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { PolicyConfigResponse } from '../../models/admin'
import { policyConfigsColumns, policyConfigsDefaultSort } from './policyConfigsTableConfig'

const row: PolicyConfigResponse = {
  id: 'P-1',
  categoryCode: 'MEALS',
  receiptThresholdAmount: 100,
  currency: 'EUR',
  active: false,
}

describe('policyConfigsTableConfig (K-03)', () => {
  it('defines the Policy Config columns and Category ascending default sort', () => {
    expect(policyConfigsColumns.map((column) => column.header)).toEqual([
      'Category',
      'Threshold',
      'Currency',
      'Active',
    ])
    expect(policyConfigsColumns.map((column) => column.key)).toEqual([
      'categoryCode',
      'receiptThresholdAmount',
      'currency',
      'active',
    ])
    expect(policyConfigsColumns.every((column) => column.sortable)).toBe(true)
    expect(policyConfigsDefaultSort).toEqual({ field: 'categoryCode', direction: 'asc' })
  })

  it('renders threshold and inactive state', () => {
    render(
      <>
        <span>
          {policyConfigsColumns.find((column) => column.key === 'receiptThresholdAmount')?.render(row)}
        </span>
        <span>{policyConfigsColumns.find((column) => column.key === 'active')?.render(row)}</span>
      </>,
    )

    expect(screen.getByText('100')).toBeInTheDocument()
    expect(screen.getByText('Inactive')).toBeInTheDocument()
  })
})
