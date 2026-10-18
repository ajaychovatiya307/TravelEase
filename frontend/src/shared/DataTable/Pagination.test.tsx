import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { PaginationState } from './DataTable.types'
import { Pagination } from './Pagination'

const populatedPagination: PaginationState = {
  page: 1,
  size: 20,
  totalElements: 45,
  totalPages: 3,
}

describe('Pagination (N-01 / C-03)', () => {
  it('renders the result summary and current page', () => {
    render(
      <Pagination
        pagination={populatedPagination}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )

    expect(screen.getByText('45 results')).toBeInTheDocument()
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
  })

  it('invokes page callbacks for previous and next controls', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(
      <Pagination
        pagination={populatedPagination}
        onPageChange={onPageChange}
        onSizeChange={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(onPageChange).toHaveBeenNthCalledWith(1, 0)
    expect(onPageChange).toHaveBeenNthCalledWith(2, 2)
  })

  it('disables Previous on the first page and Next on the last page', () => {
    const { rerender } = render(
      <Pagination
        pagination={{ ...populatedPagination, page: 0 }}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).not.toBeDisabled()

    rerender(
      <Pagination
        pagination={{ ...populatedPagination, page: 2 }}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: 'Previous' })).not.toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('renders an empty page state and emits the selected page size', async () => {
    const user = userEvent.setup()
    const onSizeChange = vi.fn()
    render(
      <Pagination
        pagination={{ page: 0, size: 20, totalElements: 0, totalPages: 0 }}
        onPageChange={vi.fn()}
        onSizeChange={onSizeChange}
        sizeOptions={[10, 20, 50]}
      />,
    )

    expect(screen.getByText('0 results')).toBeInTheDocument()
    expect(screen.getByText('Page 0 of 0')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('Rows per page'), '50')
    expect(onSizeChange).toHaveBeenCalledWith(50)
  })
})
