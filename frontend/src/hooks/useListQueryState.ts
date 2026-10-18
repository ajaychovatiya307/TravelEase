/**
 * URL-synchronized list query state (frontend-tasks.md D-04).
 *
 * Every list/table page (P-03, P-08, P-09, P-14, P-16–P-20) keeps its
 * `page`/`size`/`sort`/`filters` view state in the URL query string so that
 * browser back/forward and refresh preserve the view (frontend-plan.md §3/§7,
 * frontend-clarification.md Q20). This hook is the single read/write surface
 * for that state and the data source for the Section F list hooks, whose
 * service calls build request params from `toSearchParams()`.
 *
 * Rules implemented here:
 * - Read: state is derived from the current search params; absent or invalid
 *   values fall back to defaults (page 0, size 20, `createdAt,desc`, no
 *   filters). Filters are every non-reserved param with a non-empty value
 *   (Q19: simple string values, combined with AND server-side).
 * - Write: every mutation pushes a NEW history entry (react-router v7
 *   `setSearchParams` push semantics), so Back restores the prior view (Q20).
 * - Q23: changing the sort OR any filter resets `page` to 0 automatically;
 *   `setPage` never touches sort/filters. `setSize` is also treated as a
 *   pagination-context change and resets `page` to 0 (documented extension
 *   of Q23, consistent with Q21's fixed page-size options 10/20/50).
 *
 * Pure state hook: no UI, no styling, no fetching.
 */
import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

import type { SortState } from '../shared/DataTable'

/**
 * View state of a list/table page, kept in the URL query string. Mirrors the
 * API-facing params of api-contract.md §7.
 */
export interface ListQueryState {
  /** Zero-based page index (api-contract.md §7). */
  page: number
  /** Page size; one of {@link PAGE_SIZE_OPTIONS}. */
  size: number
  /** Sort, serialized in the URL as `field,direction` (e.g. `createdAt,desc`). */
  sort: SortState
  /** Resource-specific filter params (e.g. status, duplicateFlag, approverId, entityType, fromDate/toDate). */
  filters: Record<string, string>
}

export interface ListQueryStateOptions {
  /** Override the `createdAt,desc` sort used when the URL carries no valid `sort`. */
  defaultSort?: SortState
  /** Override the default size of 20; must be one of PAGE_SIZE_OPTIONS. */
  defaultSize?: number
}

export interface ListQueryStateControls {
  queryState: ListQueryState
  /** Paginate within the current filter/sort context. Never touches sort/filters (Q23). */
  setPage: (page: number) => void
  /**
   * Change the page size to one of PAGE_SIZE_OPTIONS and reset `page` to 0
   * (a new size changes the pagination context — documented Q23 extension).
   * Values outside PAGE_SIZE_OPTIONS keep the current size.
   */
  setSize: (size: number) => void
  /** Change the sort and reset `page` to 0 (Q23). */
  setSort: (sort: SortState) => void
  /** Replace every filter and reset `page` to 0 (Q23). */
  setFilters: (filters: Record<string, string>) => void
  /** Set one filter and reset `page` to 0 (Q23). An empty value removes the filter. */
  setFilter: (key: string, value: string) => void
  /** Remove one filter and reset `page` to 0 (Q23). */
  clearFilter: (key: string) => void
  /** Remove every filter and reset `page` to 0 (Q23). */
  clearAllFilters: () => void
  /**
   * Serialize the current state to exactly the API-facing query params of
   * api-contract.md §7 (`page`, `size`, `sort=field,dir`, then each filter
   * key=value) — ready to spread directly into service request params (F-06).
   */
  toSearchParams: () => URLSearchParams
}

/** Fixed user-selectable page sizes (frontend-clarification.md Q21). */
export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 20, 50]

/** Default page size (frontend-clarification.md Q21, api-contract.md §7). */
export const DEFAULT_PAGE_SIZE: number = 20

/** Default sort applied when the URL carries no valid `sort` param. */
export const DEFAULT_SORT: SortState = { field: 'createdAt', direction: 'desc' }

/** Params managed by this hook; every other non-empty param in the URL is a filter. */
const RESERVED_PARAMS: ReadonlySet<string> = new Set(['page', 'size', 'sort'])

function sanitizePage(value: number): number {
  return Number.isInteger(value) && value >= 0 ? value : 0
}

function sanitizeSize(value: number, fallback: number): number {
  return Number.isInteger(value) && PAGE_SIZE_OPTIONS.includes(value) ? value : fallback
}

function parsePageParam(raw: string | null): number {
  if (raw === null) return 0
  return sanitizePage(Number(raw))
}

function parseSizeParam(raw: string | null, fallback: number): number {
  if (raw === null) return fallback
  return sanitizeSize(Number(raw), fallback)
}

function parseSortParam(raw: string | null, fallback: SortState): SortState {
  if (raw === null) return fallback
  const [field, direction] = raw.split(',')
  if (field && (direction === 'asc' || direction === 'desc')) {
    return { field, direction }
  }
  return fallback
}

/** `field,direction` wire format of the `sort` param (api-contract.md §3.4). */
export function sortToParam(sort: SortState): string {
  return `${sort.field},${sort.direction}`
}

/**
 * Parse query-string state into a {@link ListQueryState}, falling back to
 * defaults for absent/invalid values. Filters are every non-reserved param
 * with a non-empty value; on duplicate keys the first value wins.
 */
export function parseListQueryState(
  searchParams: URLSearchParams,
  options: ListQueryStateOptions = {},
): ListQueryState {
  const defaultSort = options.defaultSort ?? DEFAULT_SORT
  const defaultSize = sanitizeSize(options.defaultSize ?? DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE)
  const filters: Record<string, string> = {}
  for (const [key, value] of searchParams.entries()) {
    if (RESERVED_PARAMS.has(key) || value === '' || key in filters) continue
    filters[key] = value
  }
  return {
    page: parsePageParam(searchParams.get('page')),
    size: parseSizeParam(searchParams.get('size'), defaultSize),
    sort: parseSortParam(searchParams.get('sort'), defaultSort),
    filters,
  }
}

/**
 * Serialize a {@link ListQueryState} to the API-facing query params of
 * api-contract.md §7: `page`, `size`, `sort=field,dir`, then each filter.
 */
export function serializeListQueryState(state: ListQueryState): URLSearchParams {
  const params = new URLSearchParams()
  params.set('page', String(sanitizePage(state.page)))
  params.set('size', String(state.size))
  params.set('sort', sortToParam(state.sort))
  for (const [key, value] of Object.entries(state.filters)) {
    if (value !== '') params.set(key, value)
  }
  return params
}

/**
 * Read/write hook for the URL-synchronized list query state.
 *
 * @example
 * ```tsx
 * const { queryState, setPage, setSort, setFilter, clearAllFilters, toSearchParams } =
 *   useListQueryState()
 * // service layer (F-06): getTravelRequests({ params: toSearchParams() })
 * ```
 */
export function useListQueryState(options: ListQueryStateOptions = {}): ListQueryStateControls {
  const [searchParams, setSearchParams] = useSearchParams()

  const defaultSort = options.defaultSort ?? DEFAULT_SORT
  const defaultSize = sanitizeSize(options.defaultSize ?? DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE)

  const queryState = parseListQueryState(searchParams, { defaultSort, defaultSize })

  // Each mutation is parsed from the CURRENT params (functional update) so
  // successive mutations compose, and is pushed as a NEW history entry (Q20).
  const apply = useCallback(
    (mutate: (state: ListQueryState) => ListQueryState) => {
      setSearchParams((prev) =>
        serializeListQueryState(mutate(parseListQueryState(prev, { defaultSort, defaultSize }))),
      )
    },
    [setSearchParams, defaultSort, defaultSize],
  )

  const setPage = useCallback(
    (page: number) => {
      apply((state) => ({ ...state, page: sanitizePage(page) }))
    },
    [apply],
  )

  const setSize = useCallback(
    (size: number) => {
      apply((state) => ({ ...state, page: 0, size: sanitizeSize(size, state.size) }))
    },
    [apply],
  )

  const setSort = useCallback(
    (sort: SortState) => {
      apply((state) => ({ ...state, page: 0, sort: { ...sort } }))
    },
    [apply],
  )

  const setFilters = useCallback(
    (filters: Record<string, string>) => {
      apply((state) => ({ ...state, page: 0, filters: { ...filters } }))
    },
    [apply],
  )

  const setFilter = useCallback(
    (key: string, value: string) => {
      apply((state) => {
        const filters = { ...state.filters }
        if (value === '') {
          delete filters[key]
        } else {
          filters[key] = value
        }
        return { ...state, page: 0, filters }
      })
    },
    [apply],
  )

  const clearFilter = useCallback(
    (key: string) => {
      apply((state) => {
        const filters = { ...state.filters }
        delete filters[key]
        return { ...state, page: 0, filters }
      })
    },
    [apply],
  )

  const clearAllFilters = useCallback(
    () => {
      apply((state) => ({ ...state, page: 0, filters: {} }))
    },
    [apply],
  )

  const toSearchParams = useCallback(() => serializeListQueryState(queryState), [queryState])

  return {
    queryState,
    setPage,
    setSize,
    setSort,
    setFilters,
    setFilter,
    clearFilter,
    clearAllFilters,
    toSearchParams,
  }
}