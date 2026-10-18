// travelRequestService unit tests (F-02, frontend-tasks.md — "F. Services").
// The shared HTTP client (./httpClient — F-01) is fully mocked — no network
// calls — and the tests assert the endpoint, method (which client verb is
// called), params/body construction, and the typed passthrough of the mocked
// response for each function (frontend-api-mapping.md §1.1; api-contract.md
// §4.1/§5/§7; frontend-plan.md §14 test matrix "Services" row).

import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  ApprovalDecisionRequest,
  TravelRequestCreateRequest,
  TravelRequestResponse,
  TravelRequestUpdateRequest,
} from '../models/travelRequest'
import type { DocumentSummary } from '../models/document'
import type { PaginatedResult } from '../models/pagination'
import * as travelRequestService from './travelRequestService'

/**
 * Shared spies standing in for the shared HTTP client's verbs — the client
 * itself is the seam (frontend-plan.md §6: every service reuses it).
 * `vi.hoisted` keeps the references reachable from the hoisted `vi.mock`
 * factory (same pattern as httpClient.test.ts).
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

/** Typed `TravelRequestCreateRequest` payload (api-contract.md §5.1). */
const createPayload: TravelRequestCreateRequest = {
  destination: 'Bengaluru',
  startDate: '2026-11-03',
  endDate: '2026-11-07',
  justification: 'Onsite workshop with the payments team',
  documentIds: ['doc-1'],
}

/** Typed `TravelRequestResponse` read model (api-contract.md §5.3). */
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

/** Typed paginated envelope (api-contract.md §7). */
const page: PaginatedResult<TravelRequestResponse> = {
  content: [travelRequest],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

/** Typed `DocumentSummary` read model (api-contract.md §5.6). */
const documentSummary: DocumentSummary = {
  id: 'doc-1',
  fileName: 'flight-itinerary.pdf',
  fileType: 'PDF',
  fileSizeBytes: 123456,
  storageReference: 'travel-requests/tr-1/flight-itinerary.pdf',
  uploadStatus: 'CONFIRMED',
}

beforeEach(() => {
  http.get.mockReset()
  http.post.mockReset()
  http.put.mockReset()
})

describe('list (GET /api/travel-requests — frontend-api-mapping.md §1.1 P-03/P-08)', () => {
  it('builds page/size/sort/status/scope query params and returns the typed envelope', async () => {
    http.get.mockResolvedValueOnce(page)

    const result = await travelRequestService.list({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: 'PENDING',
      scope: 'MINE',
    })

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/travel-requests', {
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: 'PENDING',
      scope: 'MINE',
    })
    expect(http.post).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(page)
  })

  it('passes a repeatable sort param through (api-contract.md §7)', async () => {
    http.get.mockResolvedValueOnce(page)

    const result = await travelRequestService.list({ sort: ['startDate,asc', 'createdAt,desc'] })

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/travel-requests', {
      sort: ['startDate,asc', 'createdAt,desc'],
    })
    expect(result).toBe(page)
  })

  it('omits the params argument when called without filters', async () => {
    http.get.mockResolvedValueOnce(page)

    const result = await travelRequestService.list()

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/travel-requests', undefined)
    expect(result).toBe(page)
  })
})

describe('getById (GET /api/travel-requests/{id} — frontend-api-mapping.md §1.1 P-04)', () => {
  it('fetches the request by id and returns the typed response', async () => {
    http.get.mockResolvedValueOnce(travelRequest)

    const result = await travelRequestService.getById('tr-1')

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/travel-requests/tr-1')
    expect(http.post).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(travelRequest)
  })
})

describe('create (POST /api/travel-requests — frontend-api-mapping.md §1.1 P-05)', () => {
  it('posts the create request and returns the typed response', async () => {
    http.post.mockResolvedValueOnce(travelRequest)

    const result = await travelRequestService.create(createPayload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/travel-requests', createPayload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(travelRequest)
  })
})

describe('update (PUT /api/travel-requests/{id} — frontend-api-mapping.md §1.1 P-06)', () => {
  it('puts the update request against the id and returns the typed response', async () => {
    const updatePayload: TravelRequestUpdateRequest = {
      ...createPayload,
      justification: 'Updated scope: payments workshop plus vendor audit',
    }
    http.put.mockResolvedValueOnce(travelRequest)

    const result = await travelRequestService.update('tr-1', updatePayload)

    expect(http.put).toHaveBeenCalledTimes(1)
    expect(http.put).toHaveBeenCalledWith('/api/travel-requests/tr-1', updatePayload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.post).not.toHaveBeenCalled()
    expect(result).toBe(travelRequest)
  })
})

describe('uploadDocument (POST /api/travel-requests/{id}/documents — frontend-api-mapping.md §1.1 P-05/P-06)', () => {
  it('posts a multipart body with file and fileName fields and returns the typed DocumentSummary', async () => {
    const file = new Blob(['pdf-bytes'], { type: 'application/pdf' })
    http.post.mockResolvedValueOnce(documentSummary)

    const result = await travelRequestService.uploadDocument('tr-1', {
      file,
      fileName: 'flight-itinerary.pdf',
    })

    expect(http.post).toHaveBeenCalledTimes(1)
    const [path, body] = http.post.mock.calls[0]
    expect(path).toBe('/api/travel-requests/tr-1/documents')
    expect(body).toBeInstanceOf(FormData)
    const formData = body as FormData
    // jsdom normalizes the appended Blob into its own part — assert the part's
    // identity by content rather than reference
    const filePart = formData.get('file') as Blob
    expect(filePart).toBeInstanceOf(Blob)
    expect(filePart.type).toBe('application/pdf')
    expect(filePart.size).toBe(file.size)
    expect(await filePart.text()).toBe(await file.text())
    expect(formData.get('fileName')).toBe('flight-itinerary.pdf')
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(documentSummary)
  })
})

describe('decide (POST /api/travel-requests/{id}/approval — frontend-api-mapping.md §1.1 P-07)', () => {
  it('posts an approval decision and returns the typed response', async () => {
    const payload: ApprovalDecisionRequest = { decision: 'APPROVED' }
    http.post.mockResolvedValueOnce(travelRequest)

    const result = await travelRequestService.decide('tr-1', payload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/travel-requests/tr-1/approval', payload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(travelRequest)
  })

  it('posts a rejection with the mandatory reason (api-contract.md §5.4, VAL-12)', async () => {
    const payload: ApprovalDecisionRequest = {
      decision: 'REJECTED',
      reason: 'Start date is in the past',
    }
    http.post.mockResolvedValueOnce(travelRequest)

    const result = await travelRequestService.decide('tr-1', payload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/travel-requests/tr-1/approval', {
      decision: 'REJECTED',
      reason: 'Start date is in the past',
    })
    expect(result).toBe(travelRequest)
  })
})
