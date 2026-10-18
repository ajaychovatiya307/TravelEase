// actionVisibility unit tests (G-01, frontend-tasks.md — "G. Guards").
// Covers each flag across representative status/role/ownership combinations
// (frontend-spec.md §4.4/§4.8).

import { describe, expect, it } from 'vitest'

import type { ExpenseClaimResponse } from '../models/expenseClaim'
import type { TravelRequestResponse } from '../models/travelRequest'
import type { SessionView } from '../models/viewModels'
import {
  isCancellable,
  isDecidable,
  isEditable,
  isExpenseEditable,
  isExpenseSubmittable,
  isReimbursable,
  isResubmittable,
  isReviewable,
} from './actionVisibility'

const owner: SessionView = { userId: 'emp-1', roles: ['EMPLOYEE'] }
const otherEmployee: SessionView = { userId: 'emp-2', roles: ['EMPLOYEE'] }
const manager: SessionView = { userId: 'mgr-1', roles: ['MANAGER'] }
const finance: SessionView = { userId: 'fin-1', roles: ['FINANCE'] }

function travelRequest(overrides: Partial<TravelRequestResponse> = {}): TravelRequestResponse {
  return {
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
    ...overrides,
  }
}

function expenseClaim(overrides: Partial<ExpenseClaimResponse> = {}): ExpenseClaimResponse {
  return {
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
    documents: [],
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
    ...overrides,
  } as ExpenseClaimResponse
}

describe('isEditable (G-01)', () => {
  it('true for the owner when PENDING (travel request)', () => {
    expect(isEditable(travelRequest({ status: 'PENDING' }), owner)).toBe(true)
  })

  it('false for a non-owner even when PENDING', () => {
    expect(isEditable(travelRequest({ status: 'PENDING' }), otherEmployee)).toBe(false)
  })

  it('false for the owner when not PENDING', () => {
    expect(isEditable(travelRequest({ status: 'APPROVED' }), owner)).toBe(false)
  })

  it('true for the owner when PENDING (expense claim)', () => {
    expect(isEditable(expenseClaim({ status: 'PENDING' }), owner)).toBe(true)
  })

  it('false for the owner when expense claim is REIMBURSED', () => {
    expect(isEditable(expenseClaim({ status: 'REIMBURSED' }), owner)).toBe(false)
  })
})

describe('isResubmittable (G-01)', () => {
  it('true for the owner when REJECTED', () => {
    expect(isResubmittable(travelRequest({ status: 'REJECTED' }), owner)).toBe(true)
  })

  it('false for a non-owner when REJECTED', () => {
    expect(isResubmittable(travelRequest({ status: 'REJECTED' }), otherEmployee)).toBe(false)
  })

  it('false for the owner when PENDING', () => {
    expect(isResubmittable(travelRequest({ status: 'PENDING' }), owner)).toBe(false)
  })
})

describe('isDecidable (G-01)', () => {
  it('true for the assigned Manager approver when PENDING', () => {
    expect(isDecidable(travelRequest({ status: 'PENDING', approverId: 'mgr-1' }), manager)).toBe(
      true,
    )
  })

  it('true for the assigned Finance approver when PENDING', () => {
    expect(isDecidable(travelRequest({ status: 'PENDING', approverId: 'fin-1' }), finance)).toBe(
      true,
    )
  })

  it('false when PENDING but the session is not the assigned approver', () => {
    expect(isDecidable(travelRequest({ status: 'PENDING', approverId: 'mgr-1' }), finance)).toBe(
      false,
    )
  })

  it('false for the assigned approver when the request is not PENDING', () => {
    expect(isDecidable(travelRequest({ status: 'APPROVED', approverId: 'mgr-1' }), manager)).toBe(
      false,
    )
  })

  it('false when approverId is null (unresolved approver)', () => {
    expect(isDecidable(travelRequest({ status: 'PENDING', approverId: null }), manager)).toBe(
      false,
    )
  })

  it('false for the owning Employee even if userId happens to match approverId', () => {
    const employeeAsApprover: SessionView = { userId: 'emp-1', roles: ['EMPLOYEE'] }
    expect(
      isDecidable(travelRequest({ status: 'PENDING', approverId: 'emp-1' }), employeeAsApprover),
    ).toBe(false)
  })
})

describe('isReviewable (G-01)', () => {
  it('true for Finance when PENDING', () => {
    expect(isReviewable(expenseClaim({ status: 'PENDING' }), finance)).toBe(true)
  })

  it('false for a non-Finance role when PENDING', () => {
    expect(isReviewable(expenseClaim({ status: 'PENDING' }), manager)).toBe(false)
  })

  it('false for Finance when not PENDING', () => {
    expect(isReviewable(expenseClaim({ status: 'APPROVED' }), finance)).toBe(false)
  })
})

describe('isReimbursable (G-01)', () => {
  it('true for Finance when APPROVED', () => {
    expect(isReimbursable(expenseClaim({ status: 'APPROVED' }), finance)).toBe(true)
  })

  it('false for a non-Finance role when APPROVED', () => {
    expect(isReimbursable(expenseClaim({ status: 'APPROVED' }), owner)).toBe(false)
  })

  it('false for Finance when already REIMBURSED', () => {
    expect(isReimbursable(expenseClaim({ status: 'REIMBURSED' }), finance)).toBe(false)
  })
})

describe('Phase 1 workflow actions', () => {
  it('isExpenseEditable: owner while DRAFT, PENDING or REJECTED only', () => {
    for (const status of ['DRAFT', 'PENDING', 'REJECTED'] as const) {
      expect(isExpenseEditable(expenseClaim({ status }), owner)).toBe(true)
      expect(isExpenseEditable(expenseClaim({ status }), otherEmployee)).toBe(false)
    }
    for (const status of ['APPROVED', 'REIMBURSED'] as const) {
      expect(isExpenseEditable(expenseClaim({ status }), owner)).toBe(false)
    }
  })

  it('isExpenseSubmittable: owner with a DRAFT or REJECTED claim only (no double submission)', () => {
    expect(isExpenseSubmittable(expenseClaim({ status: 'DRAFT' }), owner)).toBe(true)
    expect(isExpenseSubmittable(expenseClaim({ status: 'REJECTED' }), owner)).toBe(true)
    expect(isExpenseSubmittable(expenseClaim({ status: 'PENDING' }), owner)).toBe(false)
    expect(isExpenseSubmittable(expenseClaim({ status: 'DRAFT' }), finance)).toBe(false)
  })

  it('isCancellable: owner; PENDING any time, APPROVED only before the start date', () => {
    const today = '2026-11-01'
    expect(isCancellable(travelRequest({ status: 'PENDING' }), owner, today)).toBe(true)
    expect(isCancellable(travelRequest({ status: 'APPROVED', startDate: '2026-11-03' }), owner, today)).toBe(true)
    expect(isCancellable(travelRequest({ status: 'APPROVED', startDate: '2026-11-01' }), owner, today)).toBe(false)
    expect(isCancellable(travelRequest({ status: 'REJECTED' }), owner, today)).toBe(false)
    expect(isCancellable(travelRequest({ status: 'CANCELLED' }), owner, today)).toBe(false)
    expect(isCancellable(travelRequest({ status: 'PENDING' }), manager, today)).toBe(false)
  })

  it('Finance never sees Review/Reimburse on their own claim', () => {
    const ownClaim = { employeeId: finance.userId }
    expect(isReviewable(expenseClaim({ ...ownClaim, status: 'PENDING' }), finance)).toBe(false)
    expect(isReimbursable(expenseClaim({ ...ownClaim, status: 'APPROVED' }), finance)).toBe(false)
    expect(isReviewable(expenseClaim({ status: 'PENDING' }), finance)).toBe(true)
  })
})
