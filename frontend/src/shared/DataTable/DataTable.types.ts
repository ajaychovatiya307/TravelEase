import type { ReactNode } from 'react'

export interface DataTableColumn<T> {
  key: string
  header: string
  render: (row: T) => ReactNode
  sortable?: boolean
}

export interface SortState {
  field: string
  direction: 'asc' | 'desc'
}

export interface PaginationState {
  page: number
  size: number
  totalElements: number
  totalPages: number
}
