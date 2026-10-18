import type { ReactNode } from 'react'
import { act, renderHook } from '@testing-library/react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import {
  DEFAULT_PAGE_SIZE,
  DEFAULT_SORT,
  PAGE_SIZE_OPTIONS,
  parseListQueryState,
  serializeListQueryState,
  sortToParam,
  useListQueryState,
} from './useListQueryState'
import type { ListQueryState, ListQueryStateOptions } from './useListQueryState'

type Controls = ReturnType<typeof useListQueryState>

interface Captured {
  result: Controls | null
  navigate: ReturnType<typeof useNavigate> | null
  /** `location.search` of every render, in order (history entries pushed so far). */
  searches: string[]
}

/**
 * Renders `useListQueryState` inside a MemoryRouter starting at `path`
 * (harness pattern of Router.test.tsx) and captures the latest hook result,
 * a `navigate` handle, and the search string of every render so history
 * push/back behavior is assertable.
 */
function renderHookAt(path: string, options?: ListQueryStateOptions) {
  const captured: Captured = { result: null, navigate: null, searches: [] }
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
  )
  renderHook(
    () => {
      captured.result = useListQueryState(options)
      captured.navigate = useNavigate()
      captured.searches.push(useLocation().search)
      return null
    },
    { wrapper },
  )
  return captured
}

function lastParams({ searches }: Captured): URLSearchParams {
  return new URLSearchParams(searches.at(-1))
}

describe('useListQueryState (D-04)', () => {
  it('exports the Q21 page-size contract: options 10/20/50 and default 20', () => {
    expect([...PAGE_SIZE_OPTIONS]).toEqual([10, 20, 50])
    expect(DEFAULT_PAGE_SIZE).toBe(20)
    expect(DEFAULT_SORT).toEqual({ field: 'createdAt', direction: 'desc' })
  })

  describe('reading (URL → state)', () => {
    it('reads page/size/sort/filters from the query string', () => {
      const { result } = renderHookAt(
        '/travel-requests?page=2&size=10&sort=createdAt,desc&status=PENDING&duplicateFlag=true',
      )
      expect(result!.queryState).toEqual({
        page: 2,
        size: 10,
        sort: { field: 'createdAt', direction: 'desc' },
        filters: { status: 'PENDING', duplicateFlag: 'true' },
      })
    })

    it('falls back to defaults on a bare URL without rewriting the URL', () => {
      const captured = renderHookAt('/travel-requests')
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: DEFAULT_PAGE_SIZE,
        sort: DEFAULT_SORT,
        filters: {},
      })
      // Reading is derived: no history entry is pushed for it.
      expect(captured.searches).toEqual([''])
    })

    it('honors custom defaultSort/defaultSize options for a bare URL', () => {
      const { result } = renderHookAt('/expenses', {
        defaultSort: { field: 'name', direction: 'asc' },
        defaultSize: 50,
      })
      expect(result!.queryState).toEqual({
        page: 0,
        size: 50,
        sort: { field: 'name', direction: 'asc' },
        filters: {},
      })
    })

    it('falls back safely for invalid params (negative page, garbage size, malformed sort)', () => {
      const { result } = renderHookAt('/travel-requests?page=-3&size=999&sort=garbage&status=')
      expect(result!.queryState).toEqual({
        page: 0,
        size: DEFAULT_PAGE_SIZE,
        sort: DEFAULT_SORT,
        filters: {}, // empty filter values are dropped
      })
    })

    it('falls back safely for fractional pages, non-option sizes, and direction-less sorts', () => {
      expect(
        parseListQueryState(new URLSearchParams('page=1.5&size=10&sort=createdAt&status=PENDING')),
      ).toEqual({ page: 0, size: 10, sort: DEFAULT_SORT, filters: { status: 'PENDING' } })
      expect(parseListQueryState(new URLSearchParams('page=4&size=abc&sort=name,weird'))).toEqual({
        page: 4,
        size: DEFAULT_PAGE_SIZE,
        sort: DEFAULT_SORT,
        filters: {},
      })
    })

    it('takes the first value when a filter key appears twice', () => {
      expect(parseListQueryState(new URLSearchParams('status=A&status=B')).filters).toEqual({
        status: 'A',
      })
    })
  })

  describe('writing (state → URL, push semantics)', () => {
    it('setPage writes the new page to the URL and preserves sort and filters (Q23)', () => {
      const captured = renderHookAt('/travel-requests?page=2&size=10&sort=name,asc&status=PENDING')
      act(() => {
        captured.result!.setPage(3)
      })
      expect(captured.result!.queryState).toEqual({
        page: 3,
        size: 10,
        sort: { field: 'name', direction: 'asc' },
        filters: { status: 'PENDING' },
      })
      const params = lastParams(captured)
      expect(params.get('page')).toBe('3')
      expect(params.get('size')).toBe('10')
      expect(params.get('sort')).toBe('name,asc')
      expect(params.get('status')).toBe('PENDING')
    })

    it('setSort resets page to 0 (Q23) and preserves size and filters', () => {
      const captured = renderHookAt('/travel-requests?page=4&size=50&status=PENDING&duplicateFlag=true')
      act(() => {
        captured.result!.setSort({ field: 'name', direction: 'asc' })
      })
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: 50,
        sort: { field: 'name', direction: 'asc' },
        filters: { status: 'PENDING', duplicateFlag: 'true' },
      })
      const params = lastParams(captured)
      expect(params.get('page')).toBe('0')
      expect(params.get('sort')).toBe('name,asc')
      expect(params.get('status')).toBe('PENDING')
    })

    it('setFilter updates one filter and resets page to 0 (Q23)', () => {
      const captured = renderHookAt('/travel-requests?page=4&size=50&sort=name,asc&status=PENDING')
      act(() => {
        captured.result!.setFilter('status', 'APPROVED')
      })
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: 50,
        sort: { field: 'name', direction: 'asc' },
        filters: { status: 'APPROVED' },
      })
      act(() => {
        captured.result!.setFilter('duplicateFlag', 'true')
      })
      expect(captured.result!.queryState.page).toBe(0)
      expect(captured.result!.queryState.filters).toEqual({
        status: 'APPROVED',
        duplicateFlag: 'true',
      })
    })

    it('setFilters replaces every filter and resets page to 0 (Q23)', () => {
      const captured = renderHookAt('/travel-requests?page=2&status=PENDING&duplicateFlag=true')
      act(() => {
        captured.result!.setFilters({ entityType: 'EXPENSE_CLAIM', entityId: 'E1' })
      })
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: DEFAULT_PAGE_SIZE,
        sort: DEFAULT_SORT,
        filters: { entityType: 'EXPENSE_CLAIM', entityId: 'E1' },
      })
    })

    it('clearFilter removes one filter and resets page to 0 (Q23)', () => {
      const captured = renderHookAt('/travel-requests?page=4&status=PENDING&duplicateFlag=true')
      act(() => {
        captured.result!.clearFilter('status')
      })
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: DEFAULT_PAGE_SIZE,
        sort: DEFAULT_SORT,
        filters: { duplicateFlag: 'true' },
      })
    })

    it('clearAllFilters removes every filter and resets page to 0 (Q23)', () => {
      const captured = renderHookAt('/travel-requests?page=4&size=50&status=PENDING&duplicateFlag=true')
      act(() => {
        captured.result!.clearAllFilters()
      })
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: 50,
        sort: DEFAULT_SORT,
        filters: {},
      })
    })

    it('setSize writes the new size and resets page to 0 (documented Q23 extension)', () => {
      const captured = renderHookAt('/travel-requests?page=3&size=10&sort=name,asc&status=PENDING')
      act(() => {
        captured.result!.setSize(50)
      })
      expect(captured.result!.queryState).toEqual({
        page: 0,
        size: 50,
        sort: { field: 'name', direction: 'asc' },
        filters: { status: 'PENDING' },
      })
      const params = lastParams(captured)
      expect(params.get('page')).toBe('0')
      expect(params.get('size')).toBe('50')
    })

    it('setSize keeps the current size when given a value outside PAGE_SIZE_OPTIONS', () => {
      const captured = renderHookAt('/travel-requests?page=3&size=10')
      act(() => {
        captured.result!.setSize(30)
      })
      expect(captured.result!.queryState.page).toBe(0)
      expect(captured.result!.queryState.size).toBe(10)
    })

    it('pushes a new history entry per mutation and browser back restores the prior view (Q20)', () => {
      const captured = renderHookAt('/travel-requests?page=4&status=PENDING')
      expect(captured.searches).toHaveLength(1)

      act(() => {
        captured.result!.setPage(3)
      })
      expect(captured.searches).toHaveLength(2)
      expect(lastParams(captured).get('page')).toBe('3')

      act(() => {
        captured.navigate!(-1)
      })
      expect(captured.searches.at(-1)).toBe('?page=4&status=PENDING')
      expect(captured.result!.queryState.page).toBe(4)
      expect(captured.result!.queryState.filters).toEqual({ status: 'PENDING' })
    })
  })

  describe('toSearchParams (state → API params, api-contract.md §7)', () => {
    it('serializes the full state to API-facing params', () => {
      const captured = renderHookAt(
        '/travel-requests?page=2&size=50&sort=updatedAt,asc&status=PENDING&fromDate=2024-01-01&toDate=2024-02-01',
      )
      const params = captured.result!.toSearchParams()
      expect(Object.fromEntries(params.entries())).toEqual({
        page: '2',
        size: '50',
        sort: 'updatedAt,asc',
        status: 'PENDING',
        fromDate: '2024-01-01',
        toDate: '2024-02-01',
      })
    })

    it('serializes the default state when the URL is bare', () => {
      const captured = renderHookAt('/travel-requests')
      const params = captured.result!.toSearchParams()
      expect(Object.fromEntries(params.entries())).toEqual({
        page: '0',
        size: '20',
        sort: 'createdAt,desc',
      })
    })
  })

  describe('pure helpers', () => {
    it('round-trips a ListQueryState through serialize/parse', () => {
      const state: ListQueryState = {
        page: 1,
        size: 10,
        sort: { field: 'name', direction: 'asc' },
        filters: { status: 'PENDING', approverId: 'U2' },
      }
      expect(parseListQueryState(serializeListQueryState(state))).toEqual(state)
    })

    it('serializes sort in the field,direction wire format', () => {
      expect(sortToParam({ field: 'createdAt', direction: 'desc' })).toBe('createdAt,desc')
      expect(sortToParam({ field: 'name', direction: 'asc' })).toBe('name,asc')
    })
  })
})