// Travel request approval decision page (I-07, frontend-tasks.md —
// "I. Pages"): hosts the P-07 Travel Request Approval screen
// (frontend-spec.md §4.6; frontend-api-mapping.md §1.1 P-07 row) on top of
// `ApprovalDecisionForm` (J-02). The page owns the service call (F-02,
// travelRequestService.ts `decide`) and the `ClassifiedError` → UI mapping
// (frontend-api-mapping.md §3/§4): `fieldErrors[].field` entries
// ('decision' | 'reason') become inline `serverFieldErrors`, the remaining
// messages (or the envelope message when none apply) become the form-level
// `formError` banner, and `409 CONFLICT` surfaces the conflict text AND
// refetches to sync the displayed status (the request was already decided
// elsewhere — the TR-02 gate then hides the decision form).
//
// Decision flow (frontend-clarification.md Q13 + Q2): the form's `onSubmit`
// does NOT call the service — it holds the payload in state and opens the
// shared `ConfirmDialog` (confirmation is required before Approve/Reject;
// for Reject the reason is already entered and valid because the form
// validated it). Confirming the dialog calls `decide(id, payload)`: success
// enqueues a success toast and navigates to the Approval Queue (P-08,
// `/approvals/travel-requests`) — NOT the Detail page (Q2). Any failure
// closes the dialog and re-renders the form with the mapped errors (the
// form stays mounted); `FORBIDDEN_ACTION` additionally redirects to
// `/access-denied`.
//
// Decidability gate (TR-02, page-level): the decision form renders only
// while `status = PENDING`; otherwise an inline warning explains that the
// request is no longer pending a decision and no decision controls are
// shown.
//
// Fetch errors (frontend-api-mapping.md §1.1 P-07 row):
// `RESOURCE_NOT_FOUND` → not-found fallback, `FORBIDDEN_ACTION` → redirect
// to `/access-denied`, anything else → error banner.

import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ApprovalDecisionForm } from '../../../components/travel-requests/ApprovalDecisionForm'
import { useNotifications } from '../../../context/NotificationContext'
import { useTravelRequest } from '../../../hooks/useTravelRequests'
import type { ApprovalDecisionRequest } from '../../../models/travelRequest'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import { decide, downloadDocument } from '../../../services/travelRequestService'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { ErrorBanner } from '../../../shared/ErrorBanner'
import { LoadingSkeleton } from '../../../shared/LoadingSkeleton'
import { DocumentDownloadButton } from '../../../shared/DocumentDownload'
import { NotFoundPage } from '../../../shared/NotFoundPage'
import { applyServerFieldErrors } from '../../../validation/applyServerFieldErrors'
import { StatusBadge } from '../../../shared/StatusBadge'
import './ApprovalPage.css'

/** Decision-form fields a `fieldErrors[].field` entry can be attributed to (api-mapping §3). */
const ATTRIBUTABLE_FIELDS = ['decision', 'reason'] as const

/** Fallback form-level message when a rejected decision carries no usable text. */
const DECISION_FAILED_MESSAGE = 'The decision could not be submitted. Please try again.'

/** Approval Queue (P-08) — the destination after a successful decision (frontend-clarification.md Q2). */
const APPROVAL_QUEUE_PATH = '/approvals/travel-requests'

/**
 * Maps a rejected `decide` call to the form's UI state
 * (frontend-api-mapping.md §3/§4):
 * - `VALIDATION_ERROR` — `fieldErrors[].field` entries ('decision' |
 *   'reason') become `serverFieldErrors` (keyed by field name); the
 *   remaining messages — or the envelope message when none apply — become
 *   the form-level `formError`.
 * - `CONFLICT` — the conflict text becomes `formError` (the page refetches
 *   to sync the displayed status).
 * - anything else / non-classified — `formError` is the error's message, or
 *   a generic decision-failure fallback when there is none.
 */
function mapDecisionError(error: unknown): {
  fieldErrors: Record<string, string>
  formError: string
} {
  const fieldErrors: Record<string, string> = {}
  let formError = DECISION_FAILED_MESSAGE

  if (isClassifiedErrorInstance(error)) {
    if (error.category === 'VALIDATION_ERROR') {
      const mapped = applyServerFieldErrors(error.fieldErrors, ATTRIBUTABLE_FIELDS)
      Object.assign(fieldErrors, mapped.fieldErrors)
      formError =
        mapped.formErrors.length > 0 ? mapped.formErrors.join(' ') : error.message
    } else {
      formError = error.message
    }
  } else if (error instanceof Error && error.message.length > 0) {
    formError = error.message
  }

  return { fieldErrors, formError }
}

/** Renders an ISO 8601 contract date (models/travelRequest.ts) as `YYYY-MM-DD`. */
function formatDate(isoDate: string): string {
  const datePart = /^(\d{4}-\d{2}-\d{2})/.exec(isoDate)
  return datePart !== null ? datePart[1] : isoDate
}

/**
 * P-07 Travel Request Approval (`/travel-requests/:id/approval`): a
 * read-only summary of the request (frontend-spec.md §4.6) plus the
 * `ApprovalDecisionForm` behind the TR-02 decidability gate. Fetch errors
 * follow the P-07 mapping (frontend-api-mapping.md §1.1):
 * `RESOURCE_NOT_FOUND` → not-found fallback, `FORBIDDEN_ACTION` → redirect
 * to `/access-denied`, anything else → error banner.
 */
function ApprovalView({ id }: { id: string }) {
  const navigate = useNavigate()
  const { enqueueToast } = useNotifications()
  const { data, isLoading, error, refetch } = useTravelRequest(id)
  const [submitting, setSubmitting] = useState(false)
  const [serverFieldErrors, setServerFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  /** Non-null while the Q13 confirmation dialog is open; the pending payload. */
  const [pendingDecision, setPendingDecision] = useState<ApprovalDecisionRequest | null>(null)

  // FORBIDDEN_ACTION on load (e.g. the record was not assigned to this
  // approver): redirect to the access-denied screen instead of the decision UI.
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

  const isRejection = pendingDecision !== null && pendingDecision.decision === 'REJECTED'
  const confirmMessage = isRejection
    ? `Reject this travel request to ${data.destination}? The reason you entered will be recorded with the decision.`
    : `Approve this travel request to ${data.destination}?`

  // Q13: the form's validated payload does not hit the service yet — it
  // opens the confirmation dialog; confirming is what actually calls `decide`.
  const handleSubmit = (payload: ApprovalDecisionRequest): void => {
    // Clear stale server errors at the start of a new submit attempt.
    setServerFieldErrors({})
    setFormError(null)
    setPendingDecision(payload)
  }

  const handleConfirm = async (): Promise<void> => {
    if (pendingDecision === null) return
    setSubmitting(true)
    try {
      await decide(id, pendingDecision)
      // Q2: back to the Approval Queue (P-08), not the Detail page — the
      // success toast is hosted app-wide (NotificationHost), so it survives
      // the navigation.
      enqueueToast(
        pendingDecision.decision === 'APPROVED'
          ? 'Travel request approved.'
          : 'Travel request rejected.',
        'success',
      )
      navigate(APPROVAL_QUEUE_PATH)
    } catch (error) {
      if (isClassifiedErrorInstance(error) && error.category === 'FORBIDDEN_ACTION') {
        navigate('/access-denied')
        return
      }
      const mapped = mapDecisionError(error)
      setServerFieldErrors(mapped.fieldErrors)
      setFormError(mapped.formError)
      // 409: the request was already decided — sync the displayed status so
      // the TR-02 gate reflects it (the form unmounts once status ≠ PENDING).
      if (isClassifiedErrorInstance(error) && error.category === 'CONFLICT') {
        refetch()
      }
    } finally {
      setSubmitting(false)
      setPendingDecision(null)
    }
  }

  return (
    <div className="approval-page">
      <h1 className="approval-page__heading">Travel Request Approval</h1>
      <Link to={APPROVAL_QUEUE_PATH} className="approval-page__back-link">
        Back to Approval Queue
      </Link>

      <dl className="approval-page__summary">
        <div className="approval-page__summary-item">
          <dt>Status</dt>
          <dd>
            <StatusBadge status={data.status} />
          </dd>
        </div>
        <div className="approval-page__summary-item">
          <dt>Destination</dt>
          <dd>{data.destination}</dd>
        </div>
        <div className="approval-page__summary-item">
          <dt>Start date</dt>
          <dd>{formatDate(data.startDate)}</dd>
        </div>
        <div className="approval-page__summary-item">
          <dt>End date</dt>
          <dd>{formatDate(data.endDate)}</dd>
        </div>
        <div className="approval-page__summary-item">
          <dt>Justification</dt>
          <dd>{data.justification}</dd>
        </div>
        {data.rejectionReason !== null ? (
          <div className="approval-page__summary-item">
            <dt>Rejection reason</dt>
            <dd>{data.rejectionReason}</dd>
          </div>
        ) : null}
      </dl>

      {data.documents.length === 0 ? (
        <p>No documents attached.</p>
      ) : (
        <ul className="approval-page__documents">
          {data.documents.map((document) => (
            <li key={document.id}>
              {document.fileName} ({document.fileType}, {document.fileSizeBytes} bytes,{' '}
              {document.uploadStatus}){' '}
              {document.uploadStatus === 'CONFIRMED' ? (
                <DocumentDownloadButton
                  fileName={document.fileName}
                  download={() => downloadDocument(data.id, document.id)}
                />
              ) : null}
            </li>
          ))}
        </ul>
      )}

      <section className="approval-page__decision" aria-label="Decision">
        {data.status === 'PENDING' ? (
          <ApprovalDecisionForm
            serverFieldErrors={serverFieldErrors}
            formError={formError}
            submitting={submitting}
            onSubmit={handleSubmit}
          />
        ) : (
          <ErrorBanner
            variant="warning"
            message="This travel request is no longer pending a decision, so no decision controls are available."
          />
        )}
      </section>

      <ConfirmDialog
        isOpen={pendingDecision !== null}
        title={isRejection ? 'Confirm Rejection' : 'Confirm Approval'}
        message={confirmMessage}
        confirmLabel={isRejection ? 'Reject' : 'Approve'}
        onConfirm={handleConfirm}
        onCancel={() => setPendingDecision(null)}
      />
    </div>
  )
}

/**
 * P-07 page entry (frontend-api-mapping.md §1.1): `/travel-requests/:id/approval`.
 * The route param is always present per the route, but `useParams` types it
 * as optional — render `ApprovalView` only for the valid case so
 * `useTravelRequest` is never called with a non-string id.
 */
export function ApprovalPage() {
  const { id } = useParams<{ id: string }>()
  return id === undefined ? null : <ApprovalView id={id} />
}
