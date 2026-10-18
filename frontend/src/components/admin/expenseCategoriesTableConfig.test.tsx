import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { ExpenseCategoryResponse } from '../../models/admin'
import {
  expenseCategoriesColumns,
  expenseCategoriesDefaultSort,
} from './expenseCategoriesTableConfig'

const row: ExpenseCategoryResponse = {
  id: 'C-1',
  code: 'MEALS',
  label: 'Meals',
  active: true,
}

describe('expenseCategoriesTableConfig (K-03)', () => {
  it('defines the Expense Category columns and Code ascending default sort', () => {
    expect(expenseCategoriesColumns.map((column) => column.header)).toEqual([
      'Code',
      'Label',
      'Active',
    ])
    expect(expenseCategoriesColumns.map((column) => column.key)).toEqual([
      'code',
      'label',
      'active',
    ])
    expect(expenseCategoriesColumns.every((column) => column.sortable)).toBe(true)
    expect(expenseCategoriesDefaultSort).toEqual({ field: 'code', direction: 'asc' })
  })

  it('renders category code and label', () => {
    render(
      <>
        <span>{expenseCategoriesColumns.find((column) => column.key === 'code')?.render(row)}</span>
        <span>{expenseCategoriesColumns.find((column) => column.key === 'label')?.render(row)}</span>
      </>,
    )

    expect(screen.getByText('MEALS')).toBeInTheDocument()
    expect(screen.getByText('Meals')).toBeInTheDocument()
  })
})
