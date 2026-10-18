// P-04 Travel Request Detail approval history block (I-05, frontend-tasks.md).
// Chronological list of the travel request's approval decisions
// (frontend-spec.md §4.4; ui-model.md §2/§7): one ordered-list entry per
// `ApprovalHistoryEntry` showing the decision as visible text + icon (never
// color alone), who decided, when, and the reason when present (required for
// `REJECTED`, null for `APPROVED`). Entries arrive already in `decidedAt`
// ascending order (ui-model.md §7) and are rendered in the given order — no
// re-sorting. Pure presentation — no fetching, no navigation, no mutation of
// the input; an empty history renders the shared `EmptyState`.

import type { DecisionType } from '../../models/travelRequest'
import type { ApprovalHistoryEntry } from '../../models/viewModels'

export interface ApprovalHistoryListProps {
  /**
   * Chronological approval decisions (`decidedAt` ascending) — rendered in
   * the given order, never re-sorted.
   */
  entries: ApprovalHistoryEntry[]
}

/**
 * Text + icon display per `DecisionType` (frontend-requirement.md §11): the
 * decision is always conveyed by visible text; the supporting color is only a
 * hint and never carries meaning on its own.
 */
const DECISION_DISPLAY: Record<DecisionType, { label: string; icon: string; modifier: string }> = {
  APPROVED: { label: 'Approved', icon: 'check', modifier: 'approved' },
  REJECTED: { label: 'Rejected', icon: 'x', modifier: 'rejected' },
}

/**
 * Renders the travel request's approval history: a `<section>` with an `h2`
 * heading and, when there are decisions, an ordered list of entries — each a
 * `<li>` with the decision (text + icon), labelled "Decided by"/"Decided at"
 * lines, and the reason when non-null. With no decisions yet, the shared
 * `EmptyState` is shown under the heading instead.
 */
export function ApprovalHistoryList({ entries }: ApprovalHistoryListProps) {
  return (
    <div className="card card-medium approval-history-list">
      <div className="card-header">
        <h2>
          <i className="icon icon-clock" />
          Approval history
        </h2>
      </div>
      <div className="card-body">
        {entries.length === 0 ? (
          <p className="empty-state">No approval decisions recorded yet.</p>
        ) : (
          <ul className="detail-list approval-history-list__entries">
            {entries.map((entry, index) => {
              const decision = DECISION_DISPLAY[entry.decision]
              return (
                <li
                  key={`${entry.decidedBy}-${entry.decidedAt}-${index}`}
                  className="approval-history-list__entry"
                >
                  <div className="detail-row">
                    <dt>Decision</dt>
                    <dd>
                      <span
                        className={`status status-${decision.modifier} approval-history-list__decision approval-history-list__decision--${decision.modifier}`}
                      >
                        <i className={`icon icon-${decision.icon}`} />
                        {decision.label}
                      </span>
                    </dd>
                  </div>
                  <div className="detail-row">
                    <dt>Decided by</dt>
                    <dd>{entry.decidedBy}</dd>
                  </div>
                  <div className="detail-row">
                    <dt>Decided at</dt>
                    <dd className="mono">
                      <time dateTime={entry.decidedAt}>{entry.decidedAt}</time>
                    </dd>
                  </div>
                  {entry.reason !== null ? (
                    <div className="detail-row approval-history-list__reason">
                      <dt>Reason</dt>
                      <dd>{entry.reason}</dd>
                    </div>
                  ) : null}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}
