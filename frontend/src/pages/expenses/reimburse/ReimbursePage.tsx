// Expense reimbursement confirmation page (I-13, frontend-tasks.md —
// "I. Pages"): hosts the P-15 Expense Reimbursement screen
// (frontend-spec.md §4.11; frontend-api-mapping.md §1.2 P-15 row) on top of
// `ReimburseForm` (J-04). The page owns the service call
// (expenseService.ts `reimburse`) and the `ClassifiedError` → UI mapping
// (frontend-api-mapping.md §3/§4): the single `fieldErrors[].field` entry
// ('reimbursedAt') becomes an inline `serverFieldErrors`, and the remaining
// message (or the envelope message when none apply) becomes the form-level
// `formError` banner. `409 CONFLICT` (the claim is no longer `APPROVED`,
// FR-FIN-03) surfaces the conflict text AND refetches to sync the displayed
// status, which then unmounts the form via the approval gate.
//
// Confirmation flow (frontend-clarification.md Q13): the form's `onSubmit`
// does NOT call the service — it holds the payload in state and opens the
// shared `ConfirmDialog`, summarizing the reimbursement action and the
// explicit timestamp when one was provided (a blank submit records the
// server's current time). Confirming the dialog calls `reimburse(id,
// payload)`: success enqueues a success toast and navigates to the Review
// Queue (P-14, `/expenses/review-queue` — spec flow `ExpReimburse -->
// ExpQueue`). Any failure closes the dialog and re-renders the form with
// the mapped errors (the form stays mounted); `FORBIDDEN_ACTION`
// additionally redirects to `/access-denied`.
//
// Eligibility gate (page-level, FR-FIN-03): the reimbursement form renders
// only while `status = APPROVED`; otherwise an inline warning explains that
// the claim is not approved for reimbursement and no submit controls are
// shown.
//
// Fetch errors (frontend-api-mapping.md §1.2 P-15 row):
// `RESOURCE_NOT_FOUND` → not-found fallback, `FORBIDDEN_ACTION` → redirect
// to `/access-denied`, anything else → error banner.

import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ReimburseForm } from '../../../components/expenses/ReimburseForm'
import { useNotifications } from '../../../context/NotificationContext'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import type { ExpenseReimburseRequest } from '../../../models/expenseClaim'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import { reimburse } from '../../../services/expenseService'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { ErrorBanner } from '../../../shared/ErrorBanner'
import { LoadingSkeleton } from '../../../shared/LoadingSkeleton'
import { NotFoundPage } from '../../../shared/NotFoundPage'
import { StatusBadge } from '../../../shared/StatusBadge'
import './ReimbursePage.css'

/** Reimburse-form fields a `fieldErrors[].field` entry can be attributed to (api-mapping §3). */
const ATTRIBUTABLE_FIELDS = ['reimbursedAt']

/** Fallback form-level message when the reimbursement call fails without usable text. */
const REIMBURSE_FAILED_MESSAGE = 'The reimbursement could not be recorded. Please try again.'

/** Review Queue (P-14) — the destination after a successful reimbursement (frontend-spec.md §4.11 flow). */
const REVIEW_QUEUE_PATH = '/expenses/review-queue'

/**
 * Maps a rejected `reimburse` call to the form's UI state
 * (frontend-api-mapping.md §3/§4):
 * - `VALIDATION_ERROR` — the `fieldErrors[].field` entry ('reimbursedAt')
 *   becomes `serverFieldErrors` (keyed by field name); the remaining
 *   messages — or the envelope message when none apply — become the
 *   form-level `formError`.
 * - `CONFLICT` — the conflict text becomes `formError` (the page refetches
 *   to sync the displayed status).
 * - anything else / non-classified — `formError` is the error's message, or
 *   a generic reimbursement-failure fallback when there is none.
 */
function mapReimburseError(error: unknown): {
  fieldErrors: Record<string, string>
  formError: string
} {
  const fieldErrors: Record<string, string> = {}
  let formError = REIMBURSE_FAILED_MESSAGE

  if (isClassifiedErrorInstance(error)) {
    if (error.category === 'VALIDATION_ERROR') {
      const unattributed: string[] = []
      for (const fieldError of error.fieldErrors) {
        if (ATTRIBUTABLE_FIELDS.includes(fieldError.field)) {
          fieldErrors[fieldError.field] = fieldError.message
        } else {
          unattributed.push(fieldError.message)
        }
      }
      formError = unattributed.length > 0 ? unattributed.join(' ') : error.message
    } else {
      formError = error.message
    }
  } else if (error instanceof Error && error.message.length > 0) {
    formError = error.message
  }

  return { fieldErrors, formError }
}

/** Renders an ISO 8601 contract date (models/expenseClaim.ts) as `YYYY-MM-DD`. */
function formatDate(isoDate: string): string {
  const datePart = /^(\d{4}-\d{2}-\d{2})/.exec(isoDate)
  return datePart !== null ? datePart[1] : isoDate
}

/** Renders an ISO 8601 timestamp (e.g. `reimbursedAt`) as `YYYY-MM-DD HH:mm`. */
function formatDateTime(isoDateTime: string): string {
  const dateTimePart = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(isoDateTime)
  return dateTimePart !== null ? `${dateTimePart[1]} ${dateTimePart[2]}` : isoDateTime
}

/**
 * P-15 Expense Reimbursement (`/expenses/:id/reimburse`): a read-only
 * summary of the claim (frontend-spec.md §4.11) plus the `ReimburseForm`
 * behind the FR-FIN-03 approval gate. Fetch errors follow the P-15 mapping
 * (frontend-api-mapping.md §1.2): `RESOURCE_NOT_FOUND` → not-found fallback,
 * `FORBIDDEN_ACTION` → redirect to `/access-denied`, anything else → error
 * banner.
 */
function ReimburseView({ id }: { id: string }) {
  const navigate = useNavigate()
  const { enqueueToast } = useNotifications()
  const { data, isLoading, error, refetch } = useExpenseClaim(id)
  const [submitting, setSubmitting] = useState(false)
  const [serverFieldErrors, setServerFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  /** Non-null while the Q13 confirmation dialog is open; the pending payload. */
  const [pendingPayload, setPendingPayload] = useState<ExpenseReimburseRequest | null>(null)

  // FORBIDDEN_ACTION on load (e.g. the caller is not a Finance user):
  // redirect to the access-denied screen instead of the reimbursement UI.
  useEffect(() => {
    if (
      error !== null &&
      isClassifiedErrorInstance(error) &&
      error.category === 'FORBIDDEN_ACTION'
    ) {
      navigate('/access-denied')
    }
  }, [error, navigate])

  if (error !== null) {
    if (isClassifiedErrorInstance(error)) {
      if (error.category === 'RESOURCE_NOT_FOUND') return <NotFoundPage />
      if (error.category === 'FORBIDDEN_ACTION') return null
    }
    return <ErrorBanner message={error.message} />
  }

  // Initial load: `data` is null until the first successful fetch (failed
  // fetches set `error`, handled above), so this branch is the `isLoading`
  // state — a post-conflict `refetch` keeps `data` non-null, so the page
  // body stays mounted instead of flashing back to the skeleton.
  if (data === null) {
    return isLoading ? <LoadingSkeleton shape="form" /> : null
  }

  const confirmMessage =
    pendingPayload?.reimbursedAt !== undefined
      ? `Mark this claim as reimbursed? The reimbursement timestamp you entered (${formatDateTime(pendingPayload.reimbursedAt)}) will be recorded.`
      : 'Mark this claim as reimbursed? The server’s current time will be recorded as the reimbursement timestamp.'

  // Q13: the form's validated payload does not hit the service yet — it
  // opens the confirmation dialog; confirming is what actually calls
  // `reimburse`.
  const handleSubmit = (payload: ExpenseReimburseRequest): void => {
    // Clear stale server errors at the start of a new submit attempt.
    setServerFieldErrors({})
    setFormError(null)
    setPendingPayload(payload)
  }

  const handleConfirm = async (): Promise<void> => {
    if (pendingPayload === null) return
    setSubmitting(true)
    try {
      await reimburse(id, pendingPayload)
      // P-14: back to the Review Queue (spec flow `ExpReimburse --> ExpQueue`)
      // — the success toast is hosted app-wide (NotificationHost), so it
      // survives the navigation.
      enqueueToast('Expense claim marked as reimbursed.', 'success')
      navigate(REVIEW_QUEUE_PATH)
    } catch (error) {
      if (isClassifiedErrorInstance(error) && error.category === 'FORBIDDEN_ACTION') {
        navigate('/access-denied')
        return
      }
      const mapped = mapReimburseError(error)
      setServerFieldErrors(mapped.fieldErrors)
      setFormError(mapped.formError)
      // 409: the claim is no longer Approved — sync the displayed status so
      // the FR-FIN-03 gate reflects it (the form unmounts once status ≠ APPROVED).
      if (isClassifiedErrorInstance(error) && error.category === 'CONFLICT') {
        refetch()
      }
    } finally {
      setSubmitting(false)
      setPendingPayload(null)
    }
  }

  return (
    <div className="reimburse-page">
      <h1 className="reimburse-page__heading">Expense Reimbursement</h1>
      <Link to={REVIEW_QUEUE_PATH} className="reimburse-page__back-link">
        Back to Review Queue
      </Link>

      <dl className="reimburse-page__summary">
        <div className="reimburse-page__summary-item">
          <dt>Status</dt>
          <dd>
            <StatusBadge status={data.status} />
          </dd>
        </div>
        <div className="reimburse-page__summary-item">
          <dt>Amount</dt>
          <dd>
            {data.amount.toFixed(2)} {data.currency}
          </dd>
        </div>
        <div className="reimburse-page__summary-item">
          <dt>Category</dt>
          <dd>{data.category}</dd>
        </div>
        <div className="reimburse-page__summary-item">
          <dt>Expense date</dt>
          <dd>{formatDate(data.expenseDate)}</dd>
        </div>
        <div className="reimburse-page__summary-item">
          <dt>Travel request</dt>
          <dd>{data.travelRequestId}</dd>
        </div>
        {data.duplicateFlag ? (
          <div className="reimburse-page__summary-item">
            <dt>Duplicate flag</dt>
            <dd>Flagged as a potential duplicate — verify before paying out.</dd>
          </div>
        ) : null}
        {data.reimbursedAt !== null ? (
          <div className="reimburse-page__summary-item">
            <dt>Reimbursed at</dt>
            <dd>{formatDateTime(data.reimbursedAt)}</dd>
          </div>
        ) : null}
      </dl>

      <section className="reimburse-page__form" aria-label="Reimbursement">
        {data.status === 'APPROVED' ? (
          <ReimburseForm
            serverFieldErrors={serverFieldErrors}
            formError={formError}
            submitting={submitting}
            onSubmit={handleSubmit}
          />
        ) : (
          <ErrorBanner
            variant="warning"
            message="This claim is not approved for reimbursement, so no reimbursement controls are available."
          />
        )}
      </section>

      <ConfirmDialog
        isOpen={pendingPayload !== null}
        title="Confirm Reimbursement"
        message={confirmMessage}
        confirmLabel="Reimburse"
        onConfirm={handleConfirm}
        onCancel={() => setPendingPayload(null)}
      />
    </div>
  )
}

/**
 * P-15 page entry (frontend-api-mapping.md §1.2): `/expenses/:id/reimburse`.
 * The route param is always present per the route, but `useParams` types it
 * as optional — render `ReimburseView` only for the valid case so
 * `useExpenseClaim` is never called with a non-string id.
 */
export function ReimbursePage() {
  const { id } = useParams<{ id: string }>()
  return id === undefined ? null : <ReimburseView id={id} />
}
