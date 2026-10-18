import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { UserAccountResponse } from '../../models/admin'
import { usersColumns, usersDefaultSort } from './usersTableConfig'

const row: UserAccountResponse = {
  id: 'U-1',
  fullName: 'Alex Morgan',
  email: 'alex@example.com',
  roles: ['ADMIN', 'FINANCE'],
  active: true,
}

describe('usersTableConfig (K-03)', () => {
  it('defines the Users columns and Name ascending default sort', () => {
    expect(usersColumns.map((column) => column.header)).toEqual([
      'Name',
      'Email',
      'Roles',
      'Active',
    ])
    expect(usersColumns.map((column) => column.key)).toEqual([
      'fullName',
      'email',
      'roles',
      'active',
    ])
    expect(usersColumns.every((column) => column.sortable)).toBe(true)
    expect(usersDefaultSort).toEqual({ field: 'fullName', direction: 'asc' })
  })

  it('renders roles and active state', () => {
    render(
      <>
        <span>{usersColumns.find((column) => column.key === 'roles')?.render(row)}</span>
        <span>{usersColumns.find((column) => column.key === 'active')?.render(row)}</span>
      </>,
    )

    expect(screen.getByText('ADMIN, FINANCE')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
  })
})
