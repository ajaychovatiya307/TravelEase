import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { EmployeeManagerMappingResponse } from '../../models/admin'
import { mappingsColumns, mappingsDefaultSort } from './mappingsTableConfig'

const row: EmployeeManagerMappingResponse = {
  id: 'M-1',
  employeeId: 'EMP-1',
  managerId: 'MGR-1',
  effectiveFrom: '2026-01-01',
}

describe('mappingsTableConfig (K-03)', () => {
  it('defines the Mapping columns and Employee ascending default sort', () => {
    expect(mappingsColumns.map((column) => column.header)).toEqual([
      'Employee',
      'Manager',
      'Effective From',
    ])
    expect(mappingsColumns.map((column) => column.key)).toEqual([
      'employeeId',
      'managerId',
      'effectiveFrom',
    ])
    expect(mappingsColumns.every((column) => column.sortable)).toBe(true)
    expect(mappingsDefaultSort).toEqual({ field: 'employeeId', direction: 'asc' })
  })

  it('renders the effective date as a time element', () => {
    const column = mappingsColumns.find((candidate) => candidate.key === 'effectiveFrom')
    render(<>{column?.render(row)}</>)

    const time = screen.getByText('2026-01-01')
    expect(time.tagName).toBe('TIME')
    expect(time).toHaveAttribute('datetime', '2026-01-01')
  })
})
