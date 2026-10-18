import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ActionBar } from '../../../components/travel-requests/ActionBar'
import { ApprovalHistoryList } from '../../../components/travel-requests/ApprovalHistoryList'
import { ReassignApproverPanel } from '../../../components/travel-requests/ReassignApproverPanel'
import { TravelRequestSummary } from '../../../components/travel-requests/TravelRequestSummary'
import { useAuth } from '../../../context/AuthContext'
import { useTravelRequest } from '../../../hooks/useTravelRequests'
import type { DocumentSummary, UploadStatus } from '../../../models/document'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import type {
  ApprovalHistoryEntry,
  DocumentView,
  SessionView,
  TravelRequestDetailView,
} from '../../../models/viewModels'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import { NotFoundPage } from '../../../shared/NotFoundPage'
import * as travelRequestService from '../../../services/travelRequestService'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { DocumentDownloadButton } from '../../../shared/DocumentDownload'
import { isCancellable, isDecidable, isEditable, isResubmittable } from '../../../utils/actionVisibility'

/**
 * Travel Request Detail (P-04, frontend-spec.md §4.4; frontend-tasks.md I-05).
 *
 * A thin composition of the three P-04 blocks: `TravelRequestSummary`
 * (read-only overview + documents), `ApprovalHistoryList` (chronological
 * decisions, empty state when none) and `ActionBar` (Edit / Resubmit /
 * Approve-Reject driven by the computed visibility flags).
 *
 * Fetches the single record via `useTravelRequest(id)` (F-06); the route is
 * `RequireRole`-guarded (routeTable.ts P-04), so an authenticated session
 * exists at render time — the `SessionView` is built null-safe anyway,
 * exactly like the dashboard (I-02).
 *
 * Error handling branches on the normalized `error.category` only
 * (frontend-api-mapping.md §4 — never on raw HTTP codes or raw error text):
 * - `RESOURCE_NOT_FOUND` → shared `NotFoundPage` (P-22 screen, link home);
 * - `FORBIDDEN_ACTION`   → shared `AccessDeniedPage` (P-21);
 * - `NETWORK_ERROR`      → `ErrorBanner` with a fixed connectivity message;
 * - anything else        → `ErrorBanner` with a fixed generic message.
 *
 * The canonical DTO is never mutated or re-typed: the `TravelRequestDetailView`
 * (E-07) is derived per render by spreading the response and adding the
 * display-only fields (ui-model.md §7/§8 note).
 */

/** Fixed user-facing messages per normalized error category (M-01). */
const NETWORK_ERROR_MESSAGE = 'Unable to reach the server. Check your connection and try again.'
const GENERIC_ERROR_MESSAGE = 'Unable to load this travel request. Please try again.'

/**
 * `uploadStatus` display mapping (ui-model.md §7 `DocumentView`): the status
 * is always conveyed by visible text; the icon is a supporting hint only,
 * never the sole carrier of meaning (frontend-constitution.md §13).
 */
const UPLOAD_STATUS_DISPLAY: Record<UploadStatus, { label: string; icon: string }> = {
  PENDING: { label: 'Pending', icon: '⏳' },
  CONFIRMED: { label: 'Confirmed', icon: '✓' },
  FAILED: { label: 'Failed', icon: '✕' },
}

/** Strips a trailing ".0" from a one-decimal number (e.g. "1.0" → "1"). */
function trimTrailingZero(value: number): string {
  return Number(value.toFixed(1)).toString()
}

/**
 * Human-readable file size from `fileSizeBytes` (B → KB → MB), as derived by
 * `DocumentView.fileSizeLabel` (ui-model.md §7).
 */
function toFileSizeLabel(fileSizeBytes: number): string {
  if (fileSizeBytes < 1024) {
    return `${fileSizeBytes} B`
  }
  if (fileSizeBytes < 1024 * 1024) {
    return `${trimTrailingZero(fileSizeBytes / 1024)} KB`
  }
  return `${trimTrailingZero(fileSizeBytes / (1024 * 1024))} MB`
}

/**
 * Reshapes one `DocumentSummary` (DTO) into a display-ready `DocumentView`
 * (view model): adds `fileSizeLabel`, `uploadStatusLabel` and
 * `uploadStatusIcon`; the DTO fields are passed through unmodified.
 */
function toDocumentView(doc: DocumentSummary): DocumentView {
  const statusDisplay = UPLOAD_STATUS_DISPLAY[doc.uploadStatus]
  return {
    ...doc,
    fileSizeLabel: toFileSizeLabel(doc.fileSizeBytes),
    uploadStatusLabel: statusDisplay.label,
    uploadStatusIcon: statusDisplay.icon,
  }
}

export function TravelRequestDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { authState } = useAuth()

  const session: SessionView = {
    userId: authState.userId ?? '',
    roles: authState.roles,
  }

  const { data, isLoading, error, refetch } = useTravelRequest(id)
  const [isConfirmingCancel, setIsConfirmingCancel] = useState(false)
  const [isCancelling, setIsCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)

  const handleCancel = async () => {
    setIsConfirmingCancel(false)
    setIsCancelling(true)
    setCancelError(null)
    try {
      await travelRequestService.cancel(id)
      refetch()
    } catch (caught) {
      setCancelError(
        isClassifiedErrorInstance(caught) && caught.category === 'CONFLICT'
          ? 'This request was changed by someone else. Reload the page and try again.'
          : isClassifiedErrorInstance(caught)
            ? caught.message
            : 'The request could not be cancelled. Please try again.',
      )
    } finally {
      setIsCancelling(false)
    }
  }

  // Branch on the normalized category only — no raw error text, no HTTP
  // codes (frontend-api-mapping.md §4; M-01).
  if (error !== null) {
    if (error.category === 'RESOURCE_NOT_FOUND') {
      return (
        <section className="screen" data-screen="travel-request-detail" data-section="travel">
          <NotFoundPage />
        </section>
      )
    }
    if (error.category === 'FORBIDDEN_ACTION') {
      return (
        <section className="screen" data-screen="travel-request-detail" data-section="travel">
          <AccessDeniedPage />
        </section>
      )
    }
    if (error.category === 'NETWORK_ERROR') {
      return (
        <section className="screen" data-screen="travel-request-detail" data-section="travel">
          <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
            {NETWORK_ERROR_MESSAGE}
          </p>
        </section>
      )
    }
    return (
      <section className="screen" data-screen="travel-request-detail" data-section="travel">
        <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
          {GENERIC_ERROR_MESSAGE}
        </p>
      </section>
    )
  }

  // Initial-load placeholder (ui-model.md §10); a background `refetch` with
  // data already present keeps the content on screen.
  if (isLoading && data === null) {
    return (
      <section className="screen" data-screen="travel-request-detail" data-section="travel">
        <p className="empty-state loading-skeleton--detail" role="status" aria-label="Loading">
          Loading…
        </p>
      </section>
    )
  }

  if (data === null) {
    return (
      <section className="screen" data-screen="travel-request-detail" data-section="travel">
        <p className="empty-state">No travel request found.</p>
      </section>
    )
  }

  // `TravelRequestResponse` (api-contract.md §5.3) does not expose approval
  // history yet; read the optional field when a future API exposes it
  // ("if exposed", ui-model.md §7) and fall back to an empty history.
  const approvalHistory: ApprovalHistoryEntry[] =
    (data as TravelRequestResponse & { approvalHistory?: ApprovalHistoryEntry[] })
      .approvalHistory ?? []

  const view: TravelRequestDetailView = {
    ...data,
    documents: data.documents.map(toDocumentView),
    approvalHistory,
    // Computed flags (G-01, src/utils/actionVisibility.ts) driving the
    // ActionBar; the server re-enforces the same rules (BR-06).
    isEditable: isEditable(data, session),
    isResubmittable: isResubmittable(data, session),
    isDecidable: isDecidable(data, session),
  }

  return (
    <section className="screen" data-screen="travel-request-detail" data-section="travel">
      <Link className="back-link" to="/travel-requests">
        <i className="icon icon-arrow-left" />
        Back to Travel Requests
      </Link>
      <header className="page-header">
        <div>
          <h1>Travel Request</h1>
        </div>
      </header>

      {cancelError !== null ? (
        <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
          {cancelError}
        </p>
      ) : null}
      <TravelRequestSummary
        request={view}
        renderDocumentAction={(doc) =>
          doc.uploadStatus === 'CONFIRMED' ? (
            <DocumentDownloadButton
              fileName={doc.fileName}
              download={() => travelRequestService.downloadDocument(data.id, doc.id)}
            />
          ) : null
        }
      />
      <ApprovalHistoryList entries={approvalHistory} />
      <ActionBar
        isEditable={view.isEditable}
        isResubmittable={view.isResubmittable}
        isDecidable={view.isDecidable}
        onEdit={() => navigate(`/travel-requests/${id}/edit`)}
        onResubmit={() => navigate(`/travel-requests/${id}/edit`)}
        onDecide={() => navigate(`/travel-requests/${id}/approval`)}
      />
      {isCancellable(data, session) ? (
        <div className="form-actions">
          <span style={{ marginRight: 'auto', alignSelf: 'center', fontSize: 13 }}>
            {data.status === 'APPROVED'
              ? 'Trip no longer needed? You can withdraw it before the start date.'
              : 'No longer need this trip? Withdraw the request.'}
          </span>
          <button
            className="button button-secondary"
            type="button"
            onClick={() => setIsConfirmingCancel(true)}
            disabled={isCancelling}
          >
            {isCancelling ? 'Cancelling…' : 'Cancel request'}
          </button>
        </div>
      ) : null}
      {authState.roles.includes('ADMIN') && data.status === 'PENDING' ? (
        <ReassignApproverPanel request={data} onReassigned={refetch} />
      ) : null}
      <ConfirmDialog
        isOpen={isConfirmingCancel}
        title="Cancel this travel request?"
        message="The request will be withdrawn and no expense claims can be filed against it. This cannot be undone."
        confirmLabel="Cancel request"
        cancelLabel="Keep request"
        onConfirm={handleCancel}
        onCancel={() => setIsConfirmingCancel(false)}
      />
    </section>
  )
}
