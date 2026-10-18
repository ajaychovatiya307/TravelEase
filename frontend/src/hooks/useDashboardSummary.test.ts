// useDashboardSummary unit tests (I-02, frontend-tasks.md — "F. Services"
// data source for P-02 Dashboard). `travelRequestService` (F-02) and
// `expenseService` (F-03) are fully mocked — no network calls — and the tests
// assert the composed `DashboardSummaryView`, the `ServerDataState`
// composition (loading/success/error) and the role-scoped wire-level params,
// including the documented quirk that the travel service's `toListParams`
// recognizes only `status`/`employeeId` and drops `approverId` — so the
// MANAGER approval query arrives as an unscoped (`employeeId: undefined`)
// list call (server-side scoping applies, api-contract.md §7).

import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ExpenseClaimResponse } from '../models/expenseClaim'
import type { TravelRequestResponse } from '../models/travelRequest'
import type { PaginatedResult } from '../models/pagination'
import type { SessionView } from '../models/viewModels'
import { useDashboardSummary } from './useDashboardSummary'

const travelRequestService = vi.hoisted(() => ({
  list: vi.fn(),
}))

const expenseService = vi.hoisted(() => ({
  list: vi.fn(),
}))

vi.mock('../services/travelRequestService', () => travelRequestService)
vi.mock('../services/expenseService', () => expenseService)

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

/** Count-only page (size 1 — `content` is discarded); one element per total. */
function travelPage(totalElements: number): PaginatedResult<TravelRequestResponse> {
  return { content: [travelRequest], page: 0, size: 1, totalElements, totalPages: totalElements }
}

function expensePage(totalElements: number): PaginatedResult<ExpenseClaimResponse> {
  return { content: [expenseClaim], page: 0, size: 1, totalElements, totalPages: totalElements }
}

// Distinct totals per logical query so a crossed wiring is caught.
const TRAVEL_OWN_TOTAL = 2
const EXPENSE_OWN_TOTAL = 1
const TRAVEL_APPROVAL_TOTAL = 3
const EXPENSE_REVIEW_TOTAL = 4

const managerSession: SessionView = { userId: 'u1', roles: ['MANAGER'] }
const managerFinanceSession: SessionView = { userId: 'u1', roles: ['MANAGER', 'FINANCE'] }
const employeeSession: SessionView = { userId: 'u1', roles: ['EMPLOYEE'] }
const financeSession: SessionView = { userId: 'u1', roles: ['FINANCE'] }
const multiRoleSession: SessionView = { userId: 'u1', roles: ['EMPLOYEE', 'MANAGER'] }

/**
 * The two travel queries are distinguished on the wire by their explicit
 * `scope` (`MINE` = own requests, `APPROVALS` = routed to the caller), so the
 * mock branches on it to give each logical query its own `totalElements`.
 */
function mockTravelList(own: number, approver: number): void {
  travelRequestService.list.mockImplementation((params: { scope?: string }) =>
    Promise.resolve(travelPage(params.scope === 'APPROVALS' ? approver : own)),
  )
}

function mockExpenseList(own: number, review: number): void {
  expenseService.list.mockImplementation((params: { employeeId?: string }) =>
    Promise.resolve(expensePage(params.employeeId === undefined ? review : own)),
  )
}

describe('useDashboardSummary (I-02)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('composes the four pending counts into DashboardSummaryView once all resolve', async () => {
    // A MANAGER + FINANCE multi-role session is the only shape where all four
    // logical queries are distinguishable on the wire (approver-scoped travel
    // and unscoped expense review both arrive with `employeeId: undefined`).
    mockTravelList(TRAVEL_OWN_TOTAL, TRAVEL_APPROVAL_TOTAL)
    mockExpenseList(EXPENSE_OWN_TOTAL, EXPENSE_REVIEW_TOTAL)
    const { result } = renderHook(() => useDashboardSummary(managerFinanceSession))

    // Before any query settles: still loading, no data yet.
    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeNull()

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.error).toBeNull()
    expect(result.current.data).toEqual({
      pendingTravelRequests: TRAVEL_OWN_TOTAL,
      pendingExpenses: EXPENSE_OWN_TOTAL,
      pendingApprovals: TRAVEL_APPROVAL_TOTAL,
      pendingExpenseReviews: EXPENSE_REVIEW_TOTAL,
    })
  })

  it('stays in the loading state while a query is still in flight', () => {
    // Intentionally never settled — keeps both queries in flight so the
    // pre-resolution loading state can be asserted.
    travelRequestService.list.mockReturnValue(
      new Promise<PaginatedResult<TravelRequestResponse>>(() => {
        // never resolved
      }),
    )
    expenseService.list.mockReturnValue(
      new Promise<PaginatedResult<ExpenseClaimResponse>>(() => {
        // never resolved
      }),
    )
    const { result } = renderHook(() => useDashboardSummary(managerSession))

    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('surfaces the first sub-query error once the in-flight work settles', async () => {
    const error = Object.assign(new Error('boom'), { category: 'INTERNAL_ERROR' })
    travelRequestService.list.mockRejectedValue(error)
    mockExpenseList(EXPENSE_OWN_TOTAL, EXPENSE_REVIEW_TOTAL)
    const { result } = renderHook(() => useDashboardSummary(managerSession))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.error).toBe(error)
    expect(result.current.data).toBeNull()
  })

  it('MANAGER: issues independent MINE and APPROVALS travel queries (dual-role users see both views)', async () => {
    mockTravelList(TRAVEL_OWN_TOTAL, TRAVEL_APPROVAL_TOTAL)
    mockExpenseList(EXPENSE_OWN_TOTAL, EXPENSE_REVIEW_TOTAL)
    const { result } = renderHook(() => useDashboardSummary(managerSession))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(travelRequestService.list).toHaveBeenCalledTimes(2)
    expect(travelRequestService.list).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      status: 'PENDING',
      scope: 'MINE',
    })
    // The approval count is explicitly scoped to the caller's queue; no user
    // id is sent (ownership is the authenticated user server-side).
    expect(travelRequestService.list).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      status: 'PENDING',
      scope: 'APPROVALS',
    })
  })

  it('EMPLOYEE: both travel queries stay MINE-scoped (never an approval-queue query)', async () => {
    travelRequestService.list.mockResolvedValue(travelPage(TRAVEL_OWN_TOTAL))
    expenseService.list.mockResolvedValue(expensePage(EXPENSE_OWN_TOTAL))
    const { result } = renderHook(() => useDashboardSummary(employeeSession))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    const ownTravelParams = {
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      status: 'PENDING',
      scope: 'MINE',
    }
    expect(travelRequestService.list).toHaveBeenCalledTimes(2)
    expect(travelRequestService.list).toHaveBeenCalledWith(ownTravelParams)
    for (const [params] of travelRequestService.list.mock.calls) {
      expect(params).toMatchObject({ scope: 'MINE' })
    }
  })

  it('FINANCE: issues the unscoped review query plus the employeeId-scoped own expense query', async () => {
    travelRequestService.list.mockResolvedValue(travelPage(TRAVEL_OWN_TOTAL))
    mockExpenseList(EXPENSE_OWN_TOTAL, EXPENSE_REVIEW_TOTAL)
    const { result } = renderHook(() => useDashboardSummary(financeSession))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(expenseService.list).toHaveBeenCalledTimes(2)
    expect(expenseService.list).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      status: 'PENDING',
      employeeId: undefined,
      duplicateFlag: undefined,
    })
    expect(expenseService.list).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      status: 'PENDING',
      employeeId: 'u1',
      duplicateFlag: undefined,
    })
  })

  it('non-finance multi-role: both expense queries stay employeeId-scoped', async () => {
    travelRequestService.list.mockResolvedValue(travelPage(TRAVEL_OWN_TOTAL))
    expenseService.list.mockResolvedValue(expensePage(EXPENSE_OWN_TOTAL))
    const { result } = renderHook(() => useDashboardSummary(multiRoleSession))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(expenseService.list).toHaveBeenCalledTimes(2)
    expect(expenseService.list).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      status: 'PENDING',
      employeeId: 'u1',
      duplicateFlag: undefined,
    })
    for (const [params] of expenseService.list.mock.calls) {
      expect(params).toMatchObject({ employeeId: 'u1' })
    }
  })
})