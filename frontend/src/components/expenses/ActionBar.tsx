// P-10 Expense Claim Detail action bar (I-10, frontend-tasks.md).
// Presentational render of the P-10 conditional action buttons per
// frontend-spec.md §4.8 / ui-model.md §2 (`ActionBar` (Expense):
// "Conditionally renders Edit/Review/Reimburse buttons based on status +
// role + ownership"):
// - "Edit" (owner + `Pending`) — visible only when `isEditable` is true;
// - "Review" (Finance + `Pending`) — visible only when `isReviewable` is
//   true;
// - "Reimburse" (Finance + `Approved`) — visible only when `isReimbursable`
//   is true (FR-FIN-03 — the Reimburse action gate).
// The flags arrive pre-computed by the page from `ExpenseDetailView`
// (viewModels.ts §7; derived by the G-01 helpers in actionVisibility.ts),
// so this component never inspects status, role, or ownership itself. When
// no flag is true nothing is rendered — the detail view is read-only
// (frontend-spec.md §4.8: "none shown otherwise"). Each visible button
// navigates to its dedicated action page with the claim id taken from the
// `claimId` prop — P-12 `/expenses/{id}/edit`, P-13 `/expenses/{id}/review`,
// P-15 `/expenses/{id}/reimburse` (routeTable.ts) — where the actual edit,
// review decision, and reimbursement confirmation happen.

import { useNavigate } from 'react-router-dom'

import './ActionBar.css'

interface ActionBarProps {
  /** P-10 route parameter: the id of the claim being displayed; used only to build the action routes. */
  claimId: string
  /** Computed flag (frontend-spec.md §4.8): owner + `Pending` → show "Edit". */
  isEditable: boolean
  /** Computed flag (frontend-spec.md §4.8): Finance + `Pending` → show "Review". */
  isReviewable: boolean
  /** Computed flag (frontend-spec.md §4.8, FR-FIN-03): Finance + `Approved` → show "Reimburse". */
  isReimbursable: boolean
}

/**
 * Renders the P-10 action buttons for an expense claim from the computed
 * visibility flags. Each flag independently controls its button; when every
 * flag is false the component renders nothing (read-only view,
 * frontend-spec.md §4.8). The claim id is consumed verbatim to navigate to
 * the action pages (P-12/P-13/P-15) — no status/role/ownership logic lives
 * here.
 */
export function ActionBar({ claimId, isEditable, isReviewable, isReimbursable }: ActionBarProps) {
  const navigate = useNavigate()

  if (!isEditable && !isReviewable && !isReimbursable) {
    return null
  }

  return (
    <nav className="action-bar" aria-label="Expense claim actions">
      {isEditable ? (
        <button
          type="button"
          className="action-bar__button"
          onClick={() => navigate(`/expenses/${claimId}/edit`)}
        >
          Edit
        </button>
      ) : null}
      {isReviewable ? (
        <button
          type="button"
          className="action-bar__button"
          onClick={() => navigate(`/expenses/${claimId}/review`)}
        >
          Review
        </button>
      ) : null}
      {isReimbursable ? (
        <button
          type="button"
          className="action-bar__button"
          onClick={() => navigate(`/expenses/${claimId}/reimburse`)}
        >
          Reimburse
        </button>
      ) : null}
    </nav>
  )
}
