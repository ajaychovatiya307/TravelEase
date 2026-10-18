// P-04 Travel Request Detail summary block (I-05, frontend-tasks.md). Read-only
// overview of the travel request (frontend-spec.md §4.4): destination,
// start–end date range, justification, status (shared `StatusBadge`), approver
// (a "Not yet assigned" fallback while `approverId` is null), the rejection
// reason and the resubmission lineage (each rendered only when set), the
// created/updated timestamps, and the attached documents. Pure presentation —
// every field is read directly from the `TravelRequestDetailView` (E-07); no
// fetching, no navigation, no mutation of the DTO.

import { StatusBadge } from '../../shared/StatusBadge/StatusBadge'
import type { ReactNode } from 'react'
import type { DocumentView, TravelRequestDetailView } from '../../models/viewModels'

export interface TravelRequestSummaryProps {
  request: TravelRequestDetailView
  /** Optional per-document action (e.g. a download button) rendered after its metadata. */
  renderDocumentAction?: (document: DocumentView) => ReactNode
}

/**
 * Locale-formatted display of an ISO 8601 `date` value (`YYYY-MM-DD`,
 * optionally with a time suffix). The date parts are parsed manually so the
 * displayed day is never shifted by the local timezone. Falls back to the raw
 * string when the value does not start with an ISO date. No date library (I-05).
 */
function formatIsoDate(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (match === null) {
    return value
  }
  const [, year, month, day] = match
  const date = new Date(Number(year), Number(month) - 1, Number(day))
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

/** The "Start–End" date range displayed for the request dates (ui-model.md §7). */
function formatDateRange(startDate: string, endDate: string): string {
  return `${formatIsoDate(startDate)} – ${formatIsoDate(endDate)}`
}

/**
 * Renders one travel request as a read-only summary: a `<section>` with an
 * `h2` heading, a definition list of labelled field/value pairs (all labels
 * are visible text — status is never conveyed by color alone), and the
 * documents section listing each `DocumentView`'s display metadata inline
 * (the shared `DocumentList` is a later task, ui-model.md §10).
 */
export function TravelRequestSummary({ request, renderDocumentAction }: TravelRequestSummaryProps) {
  const {
    destination,
    startDate,
    endDate,
    justification,
    status,
    approverId,
    rejectionReason,
    resubmissionOfId,
    documents,
    createdAt,
    updatedAt,
  } = request

  return (
    <div className="card card-medium">
      <div className="card-header">
        <h2>Request summary</h2>
      </div>
      <div className="card-body">
        <dl className="summary-grid">
          <div>
            <dt>Destination</dt>
            <dd>{destination}</dd>
          </div>
          <div>
            <dt>Travel dates</dt>
            <dd className="numeric">{formatDateRange(startDate, endDate)}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>
              <StatusBadge status={status} />
            </dd>
          </div>
          <div>
            <dt>Approver</dt>
            <dd className="numeric">{approverId ?? 'Not yet assigned'}</dd>
          </div>
          {rejectionReason !== null ? (
            <div className="summary-wide">
              <dt>Rejection reason</dt>
              <dd className="required">{rejectionReason}</dd>
            </div>
          ) : null}
          {resubmissionOfId !== null ? (
            <div>
              <dt>Resubmission of</dt>
              <dd>{resubmissionOfId}</dd>
            </div>
          ) : null}
          <div className="summary-wide">
            <dt>Justification</dt>
            <dd>{justification}</dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd className="mono">{createdAt}</dd>
          </div>
          <div>
            <dt>Updated</dt>
            <dd className="mono">{updatedAt}</dd>
          </div>
        </dl>
      </div>
      <div className="card-section">
        <h2>
          <i className="icon icon-file" />
          Documents
        </h2>
        {documents.length === 0 ? (
          <p className="empty-state">No documents</p>
        ) : (
          <dl className="detail-list">
            {documents.map((doc) => (
              <div key={doc.id} className="detail-row travel-request-summary__document">
                <dt>{doc.fileName}</dt>
                <dd>
                  <span>{doc.fileType}</span> · <span>{doc.fileSizeLabel}</span> ·{' '}
                  <span>{doc.uploadStatusLabel}</span>
                  {renderDocumentAction ? <> {renderDocumentAction(doc)}</> : null}
                </dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </div>
  )
}
