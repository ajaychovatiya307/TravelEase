// useTravelRequests unit tests (F-06, frontend-tasks.md — "F. Services").
// `travelRequestService` (F-02) is fully mocked — no network calls — and the
// tests assert the service-call params derived from `ListQueryState` and the
// `ServerDataState` passthrough (loading/success/error), per frontend-plan.md
// §14 test matrix "Hooks" row.

import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { TravelRequestResponse } from '../models/travelRequest'
import type { PaginatedResult } from '../models/pagination'
import type { ListQueryState } from './useListQueryState'
import { useTravelRequest, useTravelRequestList } from './useTravelRequests'

const travelRequestService = vi.hoisted(() => ({
  list: vi.fn(),
  getById: vi.fn(),
}))

vi.mock('../services/travelRequestService', () => travelRequestService)

// Call counts must not leak between tests (e.g. the refetch test asserts exact counts).
beforeEach(() => {
  vi.clearAllMocks()
})

const travelRequest: TravelRequestResponse = {
  id: 'tr-1',
  employeeId: 'emp-1',
  destination: 'Bengaluru',
  startDate: '2026-11-03',
  endDate: '2026-11-07',
  justification: 'Onsite workshop with the payments team',
  status: 'PENDING',
  approverId: 'mgr-1',
  rejectionReason: null,
  resubmissionOfId: null,
  documents: [],
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-01T09:00:00Z',
}

const page: PaginatedResult<TravelRequestResponse> = {
  content: [travelRequest],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

const queryState: ListQueryState = {
  page: 1,
  size: 10,
  sort: { field: 'createdAt', direction: 'desc' },
  filters: { status: 'PENDING', employeeId: 'emp-1' },
}

describe('useTravelRequestList (F-06)', () => {
  it('calls travelRequestService.list with params mapped from ListQueryState', async () => {
    travelRequestService.list.mockResolvedValue(page)
    const { result } = renderHook(() => useTravelRequestList(queryState))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(travelRequestService.list).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      sort: 'createdAt,desc',
      status: 'PENDING',
      // No user id goes on the wire: ownership is the authenticated user server-side.
      scope: undefined,
    })
    expect(result.current.data).toEqual(page)
    expect(result.current.error).toBeNull()
  })

  it('passes an explicit list scope (own requests vs. approval queue)', async () => {
    travelRequestService.list.mockResolvedValue(page)
    const { result } = renderHook(() => useTravelRequestList(queryState, 'APPROVALS'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(travelRequestService.list).toHaveBeenCalledWith(expect.objectContaining({ scope: 'APPROVALS' }))
  })

  it('surfaces a rejected fetch as error', async () => {
    const error = Object.assign(new Error('boom'), { category: 'INTERNAL_ERROR' })
    travelRequestService.list.mockRejectedValue(error)
    const { result } = renderHook(() => useTravelRequestList(queryState))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.error).toBe(error)
    expect(result.current.data).toBeNull()
  })
})

describe('useTravelRequest (F-06)', () => {
  it('calls travelRequestService.getById with the given id', async () => {
    travelRequestService.getById.mockResolvedValue(travelRequest)
    const { result } = renderHook(() => useTravelRequest('tr-1'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(travelRequestService.getById).toHaveBeenCalledWith('tr-1')
    expect(result.current.data).toEqual(travelRequest)
  })

  it('refetch() re-invokes getById', async () => {
    travelRequestService.getById.mockResolvedValue(travelRequest)
    const { result } = renderHook(() => useTravelRequest('tr-1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(travelRequestService.getById).toHaveBeenCalledTimes(1)

    act(() => result.current.refetch())
    await waitFor(() => expect(travelRequestService.getById).toHaveBeenCalledTimes(2))
  })
})
