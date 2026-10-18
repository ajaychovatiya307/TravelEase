import './Pagination.css'
import type { PaginationState } from './DataTable.types'

interface PaginationProps {
  pagination: PaginationState
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
  sizeOptions?: number[]
}

/** Numbered page controls + size selector, shared by every DataTable instance (ui-model.md §3). */
export function Pagination({
  pagination,
  onPageChange,
  onSizeChange,
  sizeOptions = [10, 20, 50],
}: PaginationProps) {
  const { page, size, totalPages, totalElements } = pagination
  const isFirstPage = page <= 0
  const isLastPage = totalPages === 0 || page >= totalPages - 1

  return (
    <div className="table-footer">
      <span>
        {totalElements} result{totalElements === 1 ? '' : 's'}
      </span>
      <div className="pager">
        <button
          className="button button-secondary button-small"
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirstPage}
        >
          <i className="icon icon-chevron-left" />
          Previous
        </button>
        <span className="numeric">
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        <button
          className="button button-secondary button-small"
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={isLastPage}
        >
          Next
          <i className="icon icon-chevron-right" />
        </button>
      </div>
      <label className="rows-per-page">
        Rows per page
        <select value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
          {sizeOptions.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
