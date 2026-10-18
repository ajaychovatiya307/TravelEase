import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DataTable } from './DataTable'
import type { DataTableColumn, PaginationState } from './DataTable.types'

interface Row {
  id: string
  name: string
}

const columns: DataTableColumn<Row>[] = [{ key: 'name', header: 'Name', render: (row) => row.name }]

const rows: Row[] = [
  { id: '1', name: 'Alpha' },
  { id: '2', name: 'Beta' },
]

const pagination: PaginationState = { page: 0, size: 20, totalElements: 2, totalPages: 1 }

function setViewportWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: width })
  fireEvent(window, new Event('resize'))
}

describe('DataTable', () => {
  afterEach(() => {
    setViewportWidth(1024)
  })

  it('renders rows from generic columns/rows props', () => {
    render(
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        pagination={pagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )
    expect(screen.getByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
  })

  it('renders LoadingSkeleton while isLoading is true', () => {
    render(
      <DataTable
        columns={columns}
        rows={[]}
        getRowId={(row) => row.id}
        isLoading
        pagination={pagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
  })

  it('renders EmptyState when rows is empty', () => {
    render(
      <DataTable
        columns={columns}
        rows={[]}
        getRowId={(row) => row.id}
        emptyMessage="No records found"
        pagination={pagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )
    expect(screen.getByText('No records found')).toBeInTheDocument()
  })

  it('invokes onPageChange/onSizeChange from the pagination controls', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    const onSizeChange = vi.fn()
    render(
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        pagination={{ page: 0, size: 20, totalElements: 40, totalPages: 2 }}
        onPageChange={onPageChange}
        onSizeChange={onSizeChange}
      />,
    )
    await user.click(screen.getByText('Next'))
    expect(onPageChange).toHaveBeenCalledWith(1)

    await user.selectOptions(screen.getByLabelText('Rows per page'), '50')
    expect(onSizeChange).toHaveBeenCalledWith(50)
  })

  it('invokes onRowClick when a row is clicked', async () => {
    const user = userEvent.setup()
    const onRowClick = vi.fn()
    render(
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        pagination={pagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
        onRowClick={onRowClick}
      />,
    )
    await user.click(screen.getByText('Alpha'))
    expect(onRowClick).toHaveBeenCalledWith(rows[0])
  })

  it('renders a card layout below the configured breakpoint', () => {
    setViewportWidth(500)
    render(
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        pagination={pagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )
    expect(screen.getByTestId('data-table-cards')).toBeInTheDocument()
  })

  it('renders a table layout at/above the configured breakpoint', () => {
    setViewportWidth(1024)
    render(
      <DataTable
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        pagination={pagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )
    expect(screen.getByTestId('data-table')).toBeInTheDocument()
  })
})
