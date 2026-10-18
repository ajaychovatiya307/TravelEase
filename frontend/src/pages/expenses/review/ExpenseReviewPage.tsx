// P-13 Expense Review (I-12, frontend-tasks.md; frontend-spec.md §4.10).
// The Finance decision screen for a single expense claim, fetched via
// `useExpenseClaim` (F-06) with the standard three-way `ClassifiedError`
// branch (frontend-api-mapping.md §4, mirrored from the P-10 detail page):
// `RESOURCE_NOT_FOUND` renders the P-22 not-found fallback in place,
// `FORBIDDEN_ACTION` the P-21 access-denied fallback; every other category
// degrades to the `ErrorBanner` + retry (refetch) convention.
//
// Once the claim loads (frontend-spec.md §4.10):
// - the read-only claim summary (`ExpenseSummary`, C-04) — every
//   `ExpenseClaimResponse` field (api-contract.md §5.9), including the
//   duplicate flag via `DuplicateIndicator` ("if set");
// - the `ExpenseReviewForm` (J-04) decision control: Approve / Reject;
//   Reject reveals the required reason field (VAL-12 via the shared L-02
//   rule-set, validation/decisionSchema.ts).
//
// The pre-submit confirmation dialog (frontend-spec.md §4.10,
// frontend-clarification.md Q13) is owned by THIS page (C-05 shared
// `ConfirmDialog`), not the form (J-04 boundary): the form's validated
// submit stashes the pending `ExpenseReviewRequest` payload and opens the
// dialog, which summarizes the action — for a rejection the already-entered
// reason is included (Q13: it must be entered before the dialog can be
// confirmed). Confirm → `expenseService.review` (POST
// `/api/expenses/{id}/review`, frontend-api-mapping.md §1.2) → success
// navigates back to the Review Queue (P-14) per the Q2 mirror; cancel closes
// the dialog and leaves the form as-is; a failed submit closes the dialog,
// maps the `ClassifiedError` (decision/reason field errors inline via
// `serverFieldErrors`, everything else as the top-of-form `ErrorBanner` —
// mirroring `ExpenseFormPage`, frontend-api-mapping.md §3/§4, Q14), and
// rejects the form's awaited submit promise so the form stays open for
// corrections (J-04 contract).
//
// Guard: the review action only makes sense for a `PENDING` claim — a
// fetched claim in any other status renders the summary plus a non-
// interactive notice with a link back to the queue instead of the decision
// form (the server enforces the same guard with a 409 CONFLICT,
// api-contract.md §5.10/§8).
//
// Route access (FINANCE only) is enforced by the `RequireRole` guard
// (routeTable.ts P-13); no roles are encoded in this page.

import { useCallback, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import './ExpenseReviewPage.css'

import { ExpenseReviewForm } from '../../../components/expenses/ExpenseReviewForm'
import { ExpenseDocumentList } from '../../../components/expenses/ExpenseDocumentList'
import { ExpenseSummary } from '../../../components/expenses/ExpenseSummary'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import type { ExpenseClaimResponse, ExpenseReviewRequest } from '../../../models/expenseClaim'
import type { ErrorCategory } from '../../../models/error'
import * as expenseService from '../../../services/expenseService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import { ConfirmDialog } from '../../../shared/ConfirmDialog/ConfirmDialog'
import { EmptyState } from '../../../shared/EmptyState'
import { ErrorBanner, type ErrorBannerVariant } from '../../../shared/ErrorBanner'
import { LoadingSkeleton } from '../../../shared/LoadingSkeleton'
import { NotFoundPage } from '../../../shared/NotFoundPage'
import type { DecisionFieldErrors } from '../../../validation/decisionSchema'

/** Form value keys a server `fieldErrors[]` entry can map onto (J-04 fields). */
const FORM_FIELD_KEYS = ['decision', 'reason'] as const

type FormFieldKey = (typeof FORM_FIELD_KEYS)[number]

function isFormFieldKey(value: string): value is FormFieldKey {
  return (FORM_FIELD_KEYS as readonly string[]).includes(value)
}

/** Top-of-form banner state for submit failures with no visible field error. */
interface SubmitBanner {
  message: string
  variant: ErrorBannerVariant
}

function isClassifiedError(error: unknown): error is ClassifiedErrorInstance {
  return (
    typeof error === 'object' &&
    error !== null &&
    typeof (error as ClassifiedErrorInstance).category === 'string'
  )
}

/** Banner variant per normalized category (frontend-api-mapping.md §4). */
function bannerVariantFor(category: ErrorCategory): ErrorBannerVariant {
  if (category === 'CONFLICT') {
    return 'conflict'
  }
  if (category === 'POLICY_VIOLATION') {
    return 'policy'
  }
  return 'error'
}

/**
 * Maps a failed `expenseService.review` (400 VALIDATION_ERROR / 422
 * POLICY_VIOLATION / 409 CONFLICT — frontend-api-mapping.md §3/§4),
 * mirroring `ExpenseFormPage`: `fieldErrors[]` entries whose field matches a
 * form key go through the identical inline field-error path
 * (`DecisionFieldErrors`); anything else (or a non-classified error) becomes
 * the top-of-form banner (frontend-clarification.md Q14).
 */
function classifySubmitError(error: unknown): {
  fieldErrors?: DecisionFieldErrors
  banner?: SubmitBanner
} {
  if (!isClassifiedError(error)) {
    return {
      banner: {
        message: 'An unexpected error occurred. Please try again later.',
        variant: 'error',
      },
    }
  }
  const fieldErrors: DecisionFieldErrors = {}
  for (const entry of error.fieldErrors) {
    if (isFormFieldKey(entry.field)) {
      fieldErrors[entry.field] = entry.message
    }
  }
  if (Object.keys(fieldErrors).length > 0) {
    return { fieldErrors }
  }
  return { banner: { message: error.message, variant: bannerVariantFor(error.category) } }
}

/**
 * Q13 confirmation-dialog copy: the action is summarized and, for a
 * rejection, the already-entered reason is included — the dialog can only be
 * opened after VAL-12 (L-02) has guaranteed the reason is present.
 */
function dialogMessageFor(claim: ExpenseClaimResponse, payload: ExpenseReviewRequest): string {
  const summary = `the expense claim from ${claim.employeeId} (${claim.amount} ${claim.currency}, ${claim.category})`
  if (payload.decision === 'REJECTED') {
    return `This will reject ${summary}. Rejection reason: "${payload.reason ?? ''}".`
  }
  return `This will approve ${summary}. The claim will move from Pending to Approved.`
}

/**
 * Expense Review (P-13, frontend-spec.md §4.10; frontend-tasks.md I-12).
 *
 * Route access (FINANCE only) is enforced by the `RequireRole` guard
 * (routeTable.ts P-13), so an authenticated session exists at render time.
 * The route param `:id` is passed verbatim to `useExpenseClaim`; the fetch
 * re-runs whenever the id changes and `refetch` retries a failed load (F-06).
 */
export function ExpenseReviewPage() {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()

  const claimId = id ?? ''
  const { data, isLoading, error, refetch } = useExpenseClaim(claimId)

  // Q13 confirmation-dialog state: the pending validated payload (null =
  // dialog closed) plus the settle functions for the submit promise the
  // form awaits — resolved on confirm-success or cancel, rejected on a
  // failed service call so the form stays open (J-04).
  const [pendingDecision, setPendingDecision] = useState<ExpenseReviewRequest | null>(null)
  const resolveSubmitRef = useRef<(() => void) | null>(null)
  const rejectSubmitRef = useRef<((error: unknown) => void) | null>(null)
  const [submitBanner, setSubmitBanner] = useState<SubmitBanner | null>(null)
  const [serverFieldErrors, setServerFieldErrors] = useState<DecisionFieldErrors | undefined>(
    undefined,
  )

  /**
   * J-04 boundary: called only after the form's client-side validation
   * (VAL-12, L-02) passes, with the mapped `ExpenseReviewRequest` DTO
   * (api-contract.md §5.10). Stashes the payload, opens the Q13
   * confirmation dialog, and returns a promise that settles only after the
   * dialog flow completes (see `handleConfirm`/`handleCancel`).
   */
  const handleSubmit = useCallback((payload: ExpenseReviewRequest): Promise<void> => {
    // Clear the previous attempt's error state before a new one (Q14).
    setSubmitBanner(null)
    setServerFieldErrors(undefined)
    setPendingDecision(payload)
    return new Promise<void>((resolve, reject) => {
      resolveSubmitRef.current = resolve
      rejectSubmitRef.current = reject
    })
  }, [])

  /** Closes the dialog and settles the form's awaited submit promise. */
  const settleSubmit = useCallback((error?: unknown) => {
    setPendingDecision(null)
    if (error === undefined) {
      resolveSubmitRef.current?.()
    } else {
      rejectSubmitRef.current?.(error)
    }
    resolveSubmitRef.current = null
    rejectSubmitRef.current = null
  }, [])

  /** Confirm (Q13): submit the decision, then back to the Review Queue (Q2). */
  const handleConfirm = useCallback(async () => {
    const payload = pendingDecision
    if (payload === null) return
    try {
      // POST /api/expenses/{id}/review (frontend-api-mapping.md §1.2).
      await expenseService.review(claimId, payload)
      settleSubmit()
      // Q2 mirror: a successful action lands back on the Review Queue (P-14).
      navigate('/expenses/review-queue')
    } catch (submitError) {
      // Close the dialog, map the normalized error (inline field errors win,
      // else the top-of-form banner — Q14), and reject so the form stays open
      // for corrections (J-04).
      const classified = classifySubmitError(submitError)
      if (classified.fieldErrors !== undefined) {
        setServerFieldErrors(classified.fieldErrors)
      }
      setSubmitBanner(classified.banner ?? null)
      settleSubmit(submitError)
    }
  }, [claimId, navigate, pendingDecision, settleSubmit])

  /** Cancel (Q13): close the dialog; the form stays as-is. */
  const handleCancel = useCallback(() => {
    settleSubmit()
  }, [settleSubmit])

  // Missing record (404) — P-22 not-found fallback in place.
  if (error !== null && error.category === 'RESOURCE_NOT_FOUND') {
    return (
      <div className="expense-review-page" data-testid="expense-review-page">
        <NotFoundPage />
      </div>
    )
  }

  // Forbidden read (403) — P-21 access-denied fallback in place.
  if (error !== null && error.category === 'FORBIDDEN_ACTION') {
    return (
      <div className="expense-review-page" data-testid="expense-review-page">
        <AccessDeniedPage />
      </div>
    )
  }

  const claim = data
  const isRejecting = pendingDecision?.decision === 'REJECTED'

  return (
    <div className="expense-review-page" data-testid="expense-review-page">
      <div className="expense-review-page__header" data-testid="expense-review-page-header">
        <div>
          <h1>Review Expense Claim</h1>
          <p>Approve or reject this expense claim.</p>
        </div>
        <Link
          className="expense-review-page__back"
          data-testid="expense-review-page-back"
          to="/expenses/review-queue"
        >
          Back to Review Queue
        </Link>
      </div>

      {error !== null ? (
        <ErrorBanner
          message="This expense claim could not be loaded. Please try again."
          onDismiss={refetch}
        />
      ) : null}

      {isLoading ? (
        <LoadingSkeleton shape="detail" />
      ) : claim !== null ? (
        <>
          {/* frontend-spec.md §4.10: read-only claim summary, duplicate-flag
              indicator included (C-04 renders it via `DuplicateIndicator`). */}
          <ExpenseSummary claim={claim} />

          {/* Finance reviews against the actual receipts (download per document). */}
          <ExpenseDocumentList claim={claim} />

          {claim.status !== 'PENDING' ? (
            // Guard: the decision control is only meaningful while the claim
            // is `PENDING` (api-contract.md §5.10); the server enforces the
            // same guard with a 409 CONFLICT if a decision is attempted
            // anyway — so no form is rendered for any other status.
            <div
              className="expense-review-page__notice"
              data-testid="expense-review-page-not-pending"
              role="note"
            >
              <p>
                Review decisions are only available while the claim is pending. This claim is no
                longer pending, so no decision can be submitted.
              </p>
              <Link className="expense-review-page__notice-link" to="/expenses/review-queue">
                Back to Review Queue
              </Link>
            </div>
          ) : (
            <>
              {submitBanner !== null ? (
                <ErrorBanner message={submitBanner.message} variant={submitBanner.variant} />
              ) : null}
              <ExpenseReviewForm onSubmit={handleSubmit} serverFieldErrors={serverFieldErrors} />
            </>
          )}
        </>
      ) : (
        <EmptyState message="No expense claim found." />
      )}

      {claim !== null && pendingDecision !== null ? (
        <ConfirmDialog
          isOpen
          title={`${isRejecting ? 'Reject' : 'Approve'} Expense Claim`}
          message={dialogMessageFor(claim, pendingDecision)}
          confirmLabel={isRejecting ? 'Confirm rejection' : 'Confirm approval'}
          onConfirm={() => {
            void handleConfirm()
          }}
          onCancel={handleCancel}
        />
      ) : null}
    </div>
  )
}
