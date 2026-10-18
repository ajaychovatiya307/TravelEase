// useExpenses unit tests (F-06, frontend-tasks.md — "F. Services").
// `expenseService` (F-03) is fully mocked — no network calls — and the tests
// assert the service-call params derived from `ListQueryState` (including the
// `duplicateFlag` boolean-filter parsing) and the `ServerDataState`
// passthrough, per frontend-plan.md §14 test matrix "Hooks" row.

import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ExpenseClaimResponse } from '../models/expenseClaim'
import type { PaginatedResult } from '../models/pagination'
import type { ListQueryState } from './useListQueryState'
import { useExpenseClaim, useExpenseList } from './useExpenses'

const expenseService = vi.hoisted(() => ({
  list: vi.fn(),
  getById: vi.fn(),
}))

vi.mock('../services/expenseService', () => expenseService)

// Call counts must not leak between tests (e.g. the refetch test asserts exact counts).
beforeEach(() => {
  vi.clearAllMocks()
})

const expenseClaim: ExpenseClaimResponse = {
  id: 'ec-1',
  employeeId: 'emp-1',
  travelRequestId: 'tr-1',
  amount: 2500,
  currency: 'INR',
  category: 'MEALS',
  expenseDate: '2026-11-04',
  status: 'PENDING',
  duplicateFlag: false,
  rejectionReason: null,
  reimbursedAt: null,
} as ExpenseClaimResponse

const page: PaginatedResult<ExpenseClaimResponse> = {
  content: [expenseClaim],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

const queryState: ListQueryState = {
  page: 2,
  size: 20,
  sort: { field: 'createdAt', direction: 'desc' },
  filters: { status: 'PENDING', duplicateFlag: 'true' },
}

describe('useExpenseList (F-06)', () => {
  it('calls expenseService.list with params mapped from ListQueryState, parsing duplicateFlag', async () => {
    expenseService.list.mockResolvedValue(page)
    const { result } = renderHook(() => useExpenseList(queryState))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(expenseService.list).toHaveBeenCalledWith({
      page: 2,
      size: 20,
      sort: 'createdAt,desc',
      status: 'PENDING',
      employeeId: undefined,
      duplicateFlag: true,
    })
    expect(result.current.data).toEqual(page)
  })

  it('leaves duplicateFlag undefined for a non-boolean filter value', async () => {
    expenseService.list.mockResolvedValue(page)
    const { result } = renderHook(() =>
      useExpenseList({ ...queryState, filters: { status: 'PENDING' } }),
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(expenseService.list).toHaveBeenCalledWith(
      expect.objectContaining({ duplicateFlag: undefined }),
    )
  })
})

describe('useExpenseClaim (F-06)', () => {
  it('calls expenseService.getById with the given id', async () => {
    expenseService.getById.mockResolvedValue(expenseClaim)
    const { result } = renderHook(() => useExpenseClaim('ec-1'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(expenseService.getById).toHaveBeenCalledWith('ec-1')
    expect(result.current.data).toEqual(expenseClaim)
  })

  it('refetch() re-invokes getById', async () => {
    expenseService.getById.mockResolvedValue(expenseClaim)
    const { result } = renderHook(() => useExpenseClaim('ec-1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => result.current.refetch())
    await waitFor(() => expect(expenseService.getById).toHaveBeenCalledTimes(2))
  })
})
