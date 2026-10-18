// auditService unit tests (F-05, frontend-tasks.md — "F. Services").
// The shared HTTP client (./httpClient — F-01) is fully mocked — no network
// calls — and the tests assert the endpoint, method (which client verb is
// called), query-parameter construction, and the typed passthrough of the
// mocked response for the single `list` function of the read-only,
// append-only Audit Service (frontend-api-mapping.md §1.4; api-contract.md
// §4.4/§5.16/§7; frontend-plan.md §14 test matrix "Services" row).

import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuditRecordResponse } from '../models/audit'
import type { PaginatedResult } from '../models/pagination'
import { auditService } from './auditService'

/**
 * Shared spies standing in for the shared HTTP client's verbs — the client
 * itself is the seam (frontend-plan.md §6: every service reuses it).
 * `vi.hoisted` keeps the references reachable from the hoisted `vi.mock`
 * factory (same pattern as httpClient.test.ts / adminService.test.ts).
 */
const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}))

vi.mock('./httpClient', () => ({
  get: http.get,
  post: http.post,
  put: http.put,
}))

/** Typed `AuditRecordResponse` read model (api-contract.md §5.16). */
const auditRecord: AuditRecordResponse = {
  id: 'aud-1',
  entityType: 'TRAVEL_REQUEST',
  entityId: 'tr-1',
  actorId: 'ua-1',
  action: 'APPROVED',
  beforeStatus: 'PENDING',
  afterStatus: 'APPROVED',
  occurredAt: '2026-11-04T09:30:00Z',
}

/** Typed paginated envelope (api-contract.md §7). */
const auditPage: PaginatedResult<AuditRecordResponse> = {
  content: [auditRecord],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

beforeEach(() => {
  http.get.mockReset()
  http.post.mockReset()
  http.put.mockReset()
})

describe('auditService (P-20, frontend-api-mapping.md §1.4)', () => {
  describe('list (GET /api/audit)', () => {
    it('builds the page/size/sort query params and returns the typed envelope', async () => {
      http.get.mockResolvedValueOnce(auditPage)

      const result = await auditService.list({
        page: 1,
        size: 10,
        sort: 'occurredAt,desc',
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/audit', {
        page: 1,
        size: 10,
        sort: 'occurredAt,desc',
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(auditPage)
    })

    it('passes a repeatable sort param through (api-contract.md §7)', async () => {
      http.get.mockResolvedValueOnce(auditPage)

      const result = await auditService.list({
        sort: ['occurredAt,desc', 'entityType,asc'],
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/audit', {
        sort: ['occurredAt,desc', 'entityType,asc'],
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(auditPage)
    })

    it('builds the entityType/entityId/actorId/from/to filter query params and returns the typed envelope', async () => {
      http.get.mockResolvedValueOnce(auditPage)

      const result = await auditService.list({
        entityType: 'EXPENSE_CLAIM',
        entityId: 'ec-1',
        actorId: 'ua-2',
        from: '2026-11-01T00:00:00Z',
        to: '2026-11-30T23:59:59Z',
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/audit', {
        entityType: 'EXPENSE_CLAIM',
        entityId: 'ec-1',
        actorId: 'ua-2',
        from: '2026-11-01T00:00:00Z',
        to: '2026-11-30T23:59:59Z',
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(auditPage)
    })

    it('omits the params argument when called without filters', async () => {
      http.get.mockResolvedValueOnce(auditPage)

      const result = await auditService.list()

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/audit', undefined)
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(auditPage)
    })
  })
})