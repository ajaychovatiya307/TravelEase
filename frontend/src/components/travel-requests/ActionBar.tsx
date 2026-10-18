// P-04 Travel Request Detail action bar (I-05, frontend-tasks.md). Renders the
// detail page's action buttons conditionally (frontend-spec.md §4.4): "Edit"
// (owner + `Pending`), "Resubmit" (owner + `Rejected`), "Approve/Reject"
// (assigned approver + `Pending`) — none shown otherwise (read-only view).
// Pure presentation — the page supplies the computed visibility flags
// (src/utils/actionVisibility.ts) and the navigation callbacks; no data
// fetching, no routing, no state.

import './ActionBar.css'

export interface ActionBarProps {
  /**
   * Show the "Edit" button (frontend-spec.md §4.4: owner + `Pending`). The
   * flag is computed by `isEditable` (src/utils/actionVisibility.ts, G-01).
   */
  isEditable: boolean
  /**
   * Show the "Resubmit" button (frontend-spec.md §4.4: owner + `Rejected`).
   * The flag is computed by `isResubmittable` (G-01).
   */
  isResubmittable: boolean
  /**
   * Show the "Approve/Reject" button (frontend-spec.md §4.4: assigned
   * approver + `Pending`). The flag is computed by `isDecidable` (G-01).
   */
  isDecidable: boolean
  /** Navigates to the Edit screen (P-05). */
  onEdit: () => void
  /** Navigates to the Resubmit screen (P-06). */
  onResubmit: () => void
  /** Navigates to the Approval screen (P-07). */
  onDecide: () => void
}

/**
 * Renders the travel request's action bar: a labelled `role="group"`
 * container holding only the buttons whose visibility flag is true, in the
 * fixed order Edit → Resubmit → Approve/Reject. When no flag is true the
 * component renders `null` — no empty container (read-only view,
 * frontend-spec.md §4.4).
 */
export function ActionBar({
  isEditable,
  isResubmittable,
  isDecidable,
  onEdit,
  onResubmit,
  onDecide,
}: ActionBarProps) {
  if (!isEditable && !isResubmittable && !isDecidable) {
    return null
  }

  return (
    <div className="action-bar" role="group" aria-label="Actions">
      {isEditable ? (
        <button type="button" className="action-bar__button" onClick={onEdit}>
          Edit
        </button>
      ) : null}
      {isResubmittable ? (
        <button type="button" className="action-bar__button" onClick={onResubmit}>
          Resubmit
        </button>
      ) : null}
      {isDecidable ? (
        <button
          type="button"
          className="action-bar__button action-bar__button--primary"
          onClick={onDecide}
        >
          Approve/Reject
        </button>
      ) : null}
    </div>
  )
}