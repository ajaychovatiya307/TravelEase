// expenseService unit tests (F-03, frontend-tasks.md — "F. Services").
// The shared HTTP client (./httpClient — F-01) is fully mocked — no network
// calls — and the tests assert the endpoint, method (which client verb is
// called), params/body construction, and the typed passthrough of the mocked
// response for each function (frontend-api-mapping.md §1.2; api-contract.md
// §4.2/§5/§7; frontend-plan.md §14 test matrix "Services" row).

import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  ExpenseClaimCreateRequest,
  ExpenseClaimResponse,
  ExpenseClaimUpdateRequest,
  ExpenseReimburseRequest,
  ExpenseReviewRequest,
} from '../models/expenseClaim'
import type { DocumentSummary } from '../models/document'
import type { PaginatedResult } from '../models/pagination'
import * as expenseService from './expenseService'

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

/** Typed `ExpenseClaimCreateRequest` payload (api-contract.md §5.7). */
const createPayload: ExpenseClaimCreateRequest = {
  travelRequestId: 'tr-1',
  amount: 2500,
  currency: 'INR',
  category: 'TRANSPORT',
  expenseDate: '2026-11-04',
  documentIds: ['doc-1'],
}

/** Typed `ExpenseClaimResponse` read model (api-contract.md §5.9). */
const expenseClaim: ExpenseClaimResponse = {
  id: 'ex-1',
  employeeId: 'emp-1',
  travelRequestId: 'tr-1',
  amount: 2500,
  currency: 'INR',
  category: 'TRANSPORT',
  expenseDate: '2026-11-04',
  status: 'PENDING',
  duplicateFlag: true,
  rejectionReason: null,
  reimbursedAt: null,
  documents: [],
  createdAt: '2026-10-06T08:15:00Z',
  updatedAt: '2026-10-06T08:15:00Z',
}

/** Typed paginated envelope (api-contract.md §7). */
const page: PaginatedResult<ExpenseClaimResponse> = {
  content: [expenseClaim],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

/** Typed `DocumentSummary` read model (api-contract.md §5.6). */
const documentSummary: DocumentSummary = {
  id: 'doc-1',
  fileName: 'taxi-receipt.jpg',
  fileType: 'JPG',
  fileSizeBytes: 204800,
  storageReference: 'expenses/ex-1/taxi-receipt.jpg',
  uploadStatus: 'CONFIRMED',
}

beforeEach(() => {
  http.get.mockReset()
  http.post.mockReset()
  http.put.mockReset()
})

describe('list (GET /api/expenses — frontend-api-mapping.md §1.2 P-09/P-14)', () => {
  it('builds the full filter query params and returns the typed envelope', async () => {
    http.get.mockResolvedValueOnce(page)

    const result = await expenseService.list({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: 'PENDING',
      employeeId: 'emp-1',
      duplicateFlag: true,
    })

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/expenses', {
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: 'PENDING',
      employeeId: 'emp-1',
      duplicateFlag: true,
    })
    expect(http.post).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(page)
  })

  it('passes a repeatable sort param through (api-contract.md §7)', async () => {
    http.get.mockResolvedValueOnce(page)

    const result = await expenseService.list({ sort: ['expenseDate,asc', 'createdAt,desc'] })

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/expenses', {
      sort: ['expenseDate,asc', 'createdAt,desc'],
    })
    expect(result).toBe(page)
  })

  it('omits the params argument when called without filters', async () => {
    http.get.mockResolvedValueOnce(page)

    const result = await expenseService.list()

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/expenses', undefined)
    expect(result).toBe(page)
  })
})

describe('getById (GET /api/expenses/{id} — frontend-api-mapping.md §1.2 P-10)', () => {
  it('fetches the claim by id and returns the typed response', async () => {
    http.get.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.getById('ex-1')

    expect(http.get).toHaveBeenCalledTimes(1)
    expect(http.get).toHaveBeenCalledWith('/api/expenses/ex-1')
    expect(http.post).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(expenseClaim)
  })
})

describe('create (POST /api/expenses — frontend-api-mapping.md §1.2 P-11)', () => {
  it('posts the create request and returns the typed response', async () => {
    http.post.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.create(createPayload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/expenses', createPayload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(expenseClaim)
  })
})

describe('update (PUT /api/expenses/{id} — frontend-api-mapping.md §1.2 P-12)', () => {
  it('puts the update request against the id and returns the typed response', async () => {
    const updatePayload: ExpenseClaimUpdateRequest = {
      ...createPayload,
      amount: 2350,
    }
    http.put.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.update('ex-1', updatePayload)

    expect(http.put).toHaveBeenCalledTimes(1)
    expect(http.put).toHaveBeenCalledWith('/api/expenses/ex-1', updatePayload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.post).not.toHaveBeenCalled()
    expect(result).toBe(expenseClaim)
  })
})

describe('uploadDocument (POST /api/expenses/{id}/documents — frontend-api-mapping.md §1.2 P-11/P-12)', () => {
  it('posts a multipart body with file and fileName fields and returns the typed DocumentSummary', async () => {
    const file = new Blob(['jpg-bytes'], { type: 'image/jpeg' })
    http.post.mockResolvedValueOnce(documentSummary)

    const result = await expenseService.uploadDocument('ex-1', {
      file,
      fileName: 'taxi-receipt.jpg',
    })

    expect(http.post).toHaveBeenCalledTimes(1)
    const [path, body] = http.post.mock.calls[0]
    expect(path).toBe('/api/expenses/ex-1/documents')
    expect(body).toBeInstanceOf(FormData)
    const formData = body as FormData
    // jsdom normalizes the appended Blob into its own part — assert the part's
    // identity by content rather than reference
    const filePart = formData.get('file') as Blob
    expect(filePart).toBeInstanceOf(Blob)
    expect(filePart.type).toBe('image/jpeg')
    expect(filePart.size).toBe(file.size)
    expect(await filePart.text()).toBe(await file.text())
    expect(formData.get('fileName')).toBe('taxi-receipt.jpg')
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(documentSummary)
  })
})

describe('review (POST /api/expenses/{id}/review — frontend-api-mapping.md §1.2 P-13)', () => {
  it('posts an approval decision and returns the typed response', async () => {
    const payload: ExpenseReviewRequest = { decision: 'APPROVED' }
    http.post.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.review('ex-1', payload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/expenses/ex-1/review', payload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(expenseClaim)
  })

  it('posts a rejection with the mandatory reason (api-contract.md §5.10, VAL-12)', async () => {
    const payload: ExpenseReviewRequest = {
      decision: 'REJECTED',
      reason: 'Duplicate of an already-reimbursed receipt',
    }
    http.post.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.review('ex-1', payload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/expenses/ex-1/review', {
      decision: 'REJECTED',
      reason: 'Duplicate of an already-reimbursed receipt',
    })
    expect(result).toBe(expenseClaim)
  })
})

describe('reimburse (POST /api/expenses/{id}/reimburse — frontend-api-mapping.md §1.2 P-15)', () => {
  it('posts a reimbursement with an explicit reimbursedAt and returns the typed response', async () => {
    const payload: ExpenseReimburseRequest = { reimbursedAt: '2026-10-07T09:00:00Z' }
    http.post.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.reimburse('ex-1', payload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/expenses/ex-1/reimburse', payload)
    expect(http.get).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(result).toBe(expenseClaim)
  })

  it('posts an empty reimbursement body when reimbursedAt is omitted (server defaults to current time)', async () => {
    const payload: ExpenseReimburseRequest = {}
    http.post.mockResolvedValueOnce(expenseClaim)

    const result = await expenseService.reimburse('ex-1', payload)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/expenses/ex-1/reimburse', {})
    expect(result).toBe(expenseClaim)
  })
})
