// Ownership-aware action visibility helpers (G-01, frontend-tasks.md — "G.
// Guards"). Pure functions computing the `isEditable`/`isResubmittable`/
// `isDecidable`/`isReviewable`/`isReimbursable` flags consumed by the
// `TravelRequestDetailView`/`ExpenseDetailView` view models (ui-model.md §7)
// and rendered by each detail page's `ActionBar` (frontend-spec.md §4.4,
// §4.8): "Edit" (owner + `Pending`), "Resubmit" (owner + `Rejected`),
// "Approve/Reject" (assigned approver + `Pending`), "Review" (Finance +
// `Pending`), "Reimburse" (Finance + `Approved`).
//
// Each helper takes a response DTO and the current `SessionView` (E-07) and
// returns a boolean only — no side effects, no server calls. These flags are
// a UI convenience only; every mutating endpoint re-enforces the same rules
// server-side (frontend-spec.md §8 business rules).

import type { ExpenseClaimResponse } from '../models/expenseClaim'
import type { TravelRequestResponse } from '../models/travelRequest'
import type { SessionView } from '../models/viewModels'

function isOwner(session: SessionView, employeeId: string): boolean {
  return session.userId === employeeId
}

function hasRole(session: SessionView, role: SessionView['roles'][number]): boolean {
  return session.roles.includes(role)
}

/**
 * "Edit" action (frontend-spec.md §4.4/§4.8): the caller owns the record and
 * it is still `PENDING`. Shared by both Travel Request and Expense Claim
 * detail views — both DTOs expose `employeeId` and a `status` whose
 * `PENDING` member is a literal match.
 */
export function isEditable(
  record: { employeeId: string; status: string },
  session: SessionView,
): boolean {
  return isOwner(session, record.employeeId) && record.status === 'PENDING'
}

/**
 * "Resubmit" action (frontend-spec.md §4.4): the caller owns the travel
 * request and it is `REJECTED` (Q12 — the prior rejection reason is shown as
 * a read-only banner on the resubmit form).
 */
export function isResubmittable(request: TravelRequestResponse, session: SessionView): boolean {
  return isOwner(session, request.employeeId) && request.status === 'REJECTED'
}

/**
 * "Approve/Reject" action (frontend-spec.md §4.4): the request is `PENDING`
 * and the caller is its resolved `approverId` (BR-06 — routing is resolved
 * server-side; the UI only checks the resolved value) holding the Manager or
 * Finance role.
 */
export function isDecidable(request: TravelRequestResponse, session: SessionView): boolean {
  return (
    request.status === 'PENDING' &&
    request.approverId !== null &&
    request.approverId === session.userId &&
    (hasRole(session, 'MANAGER') || hasRole(session, 'FINANCE'))
  )
}

/**
 * "Review" action (frontend-spec.md §4.8): the caller holds the Finance role,
 * the expense claim is `PENDING`, and it is not the caller's own claim
 * (segregation of duties — also enforced server-side).
 */
export function isReviewable(claim: ExpenseClaimResponse, session: SessionView): boolean {
  return hasRole(session, 'FINANCE') && claim.status === 'PENDING' && !isOwner(session, claim.employeeId)
}

/**
 * "Reimburse" action (frontend-spec.md §4.8, FR-FIN-03): the caller holds
 * the Finance role, the expense claim is `APPROVED`, and it is not their own.
 */
export function isReimbursable(claim: ExpenseClaimResponse, session: SessionView): boolean {
  return hasRole(session, 'FINANCE') && claim.status === 'APPROVED' && !isOwner(session, claim.employeeId)
}

/**
 * Expense "Edit": the owner may change a claim until Finance decides
 * (`DRAFT`/`PENDING`) and again after a rejection to correct it (`REJECTED`).
 */
export function isExpenseEditable(claim: ExpenseClaimResponse, session: SessionView): boolean {
  return (
    isOwner(session, claim.employeeId) &&
    (claim.status === 'DRAFT' || claim.status === 'PENDING' || claim.status === 'REJECTED')
  )
}

/**
 * Expense "Submit for review": a `DRAFT` (first submission) or corrected
 * `REJECTED` claim can be sent to Finance by its owner.
 */
export function isExpenseSubmittable(claim: ExpenseClaimResponse, session: SessionView): boolean {
  return isOwner(session, claim.employeeId) && (claim.status === 'DRAFT' || claim.status === 'REJECTED')
}

/**
 * Travel request "Cancel": the owner may withdraw a `PENDING` request, or an
 * `APPROVED` one before its start date. `today` is an ISO date (YYYY-MM-DD).
 */
export function isCancellable(
  request: TravelRequestResponse,
  session: SessionView,
  today: string = new Date().toISOString().slice(0, 10),
): boolean {
  if (!isOwner(session, request.employeeId)) return false
  if (request.status === 'PENDING') return true
  return request.status === 'APPROVED' && request.startDate.slice(0, 10) > today
}
