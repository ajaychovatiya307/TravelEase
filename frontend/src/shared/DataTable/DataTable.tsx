import { useEffect, useState } from 'react'
import { TABLE_CARD_BREAKPOINT } from '../../styles/tokens'
import { EmptyState } from '../EmptyState'
import { LoadingSkeleton } from '../LoadingSkeleton'
import { Pagination } from './Pagination'
import './DataTable.css'
import type { DataTableColumn, PaginationState, SortState } from './DataTable.types'

interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  rows: T[]
  getRowId: (row: T) => string
  isLoading?: boolean
  emptyMessage?: string
  pagination: PaginationState
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
  sort?: SortState
  onSortChange?: (sort: SortState) => void
  onRowClick?: (row: T) => void
  referenceLayout?: boolean
}

function useIsBelowBreakpoint(breakpoint: number): boolean {
  const [isBelow, setIsBelow] = useState(() =>
    typeof window === 'undefined' ? false : window.innerWidth < breakpoint,
  )
  useEffect(() => {
    const handleResize = () => setIsBelow(window.innerWidth < breakpoint)
    handleResize()
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [breakpoint])
  return isBelow
}

/**
 * Generic paginated/sortable/filterable table (frontend-constitution.md §11). Entity-agnostic:
 * callers supply columns/rows; below frontend-clarification.md Q24's breakpoint it renders a
 * stacked card layout instead of horizontal columns.
 */
export function DataTable<T>({
  columns,
  rows,
  getRowId,
  isLoading = false,
  emptyMessage = 'No records found',
  pagination,
  onPageChange,
  onSizeChange,
  sort,
  onSortChange,
  onRowClick,
  referenceLayout = false,
}: DataTableProps<T>) {
  const isBelowBreakpoint = useIsBelowBreakpoint(TABLE_CARD_BREAKPOINT)
  const isCardLayout = !referenceLayout && isBelowBreakpoint

  if (isLoading) {
    if (!referenceLayout) return <LoadingSkeleton shape="table" />
    return (
      <p className="empty-state loading-skeleton--table" role="status" aria-label="Loading">
        Loading…
      </p>
    )
  }

  if (rows.length === 0) {
    return referenceLayout ? <p className="empty-state">{emptyMessage}</p> : <EmptyState message={emptyMessage} />
  }

  const handleSort = (column: DataTableColumn<T>) => {
    if (!column.sortable || !onSortChange) return
    const direction = sort?.field === column.key && sort.direction === 'asc' ? 'desc' : 'asc'
    onSortChange({ field: column.key, direction })
  }

  if (isCardLayout) {
    return (
      <div className="data-table data-table--cards" data-testid="data-table-cards">
        <ul className="data-table__card-list">
          {rows.map((row) => (
            <li
              key={getRowId(row)}
              className="data-table__card"
              onClick={onRowClick ? () => onRowClick(row) : undefined}
            >
              {columns.map((column) => (
                <div key={column.key} className="data-table__card-field">
                  <span className="data-table__card-label">{column.header}</span>
                  <span className="data-table__card-value">{column.render(row)}</span>
                </div>
              ))}
            </li>
          ))}
        </ul>
        <Pagination pagination={pagination} onPageChange={onPageChange} onSizeChange={onSizeChange} />
      </div>
    )
  }

  return (
    <>
      <div className="table-scroll">
        <table className="data-table" data-testid="data-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  className={column.key === 'amount' ? 'align-right' : undefined}
                >
                  {column.sortable ? (
                    <span
                      role="button"
                      tabIndex={0}
                      onClick={() => handleSort(column)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          handleSort(column)
                        }
                      }}
                    >
                      {column.header}
                    </span>
                  ) : (
                    column.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={getRowId(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={onRowClick ? 'data-table__row--clickable' : undefined}
              >
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={column.key === 'amount' ? 'numeric align-right' : undefined}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination pagination={pagination} onPageChange={onPageChange} onSizeChange={onSizeChange} />
    </>
  )
}
