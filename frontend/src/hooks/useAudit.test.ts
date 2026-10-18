// useAudit unit tests (F-06, frontend-tasks.md — "F. Services").
// `auditService` (F-05) is fully mocked — no network calls — and the tests
// assert the service-call params derived from `ListQueryState` and the
// `ServerDataState` passthrough, per frontend-plan.md §14 test matrix "Hooks"
// row.

import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { AuditRecordResponse } from '../models/audit'
import type { PaginatedResult } from '../models/pagination'
import type { ListQueryState } from './useListQueryState'
import { useAuditList } from './useAudit'

const auditService = vi.hoisted(() => ({ list: vi.fn() }))

vi.mock('../services/auditService', () => ({ auditService }))

const auditRecord: AuditRecordResponse = {
  id: 'audit-1',
  entityType: 'TRAVEL_REQUEST',
  entityId: 'tr-1',
  actorId: 'mgr-1',
  action: 'APPROVED',
  beforeStatus: 'PENDING',
  afterStatus: 'APPROVED',
  occurredAt: '2026-11-05T10:00:00Z',
}

const page: PaginatedResult<AuditRecordResponse> = {
  content: [auditRecord],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

const queryState: ListQueryState = {
  page: 0,
  size: 20,
  sort: { field: 'occurredAt', direction: 'desc' },
  filters: { entityType: 'TRAVEL_REQUEST', entityId: 'tr-1' },
}

describe('useAuditList (F-06)', () => {
  it('calls auditService.list with params mapped from ListQueryState', async () => {
    auditService.list.mockResolvedValue(page)
    const { result } = renderHook(() => useAuditList(queryState))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(auditService.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'occurredAt,desc',
      entityType: 'TRAVEL_REQUEST',
      entityId: 'tr-1',
      actorId: undefined,
      from: undefined,
      to: undefined,
    })
    expect(result.current.data).toEqual(page)
  })

  it('surfaces a rejected fetch as error', async () => {
    const error = Object.assign(new Error('forbidden'), { category: 'FORBIDDEN_ACTION' })
    auditService.list.mockRejectedValue(error)
    const { result } = renderHook(() => useAuditList(queryState))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.error).toBe(error)
  })

  it('sends inclusive UTC instant bounds for date-only filter values', async () => {
    auditService.list.mockResolvedValue(page)
    const dateRangeQuery: ListQueryState = {
      ...queryState,
      filters: { from: '2026-11-05', to: '2026-11-06' },
    }
    renderHook(() => useAuditList(dateRangeQuery))

    await waitFor(() =>
      expect(auditService.list).toHaveBeenCalledWith(
        expect.objectContaining({
          from: '2026-11-05T00:00:00Z',
          to: '2026-11-06T23:59:59.999999999Z',
        }),
      ),
    )
  })
})
