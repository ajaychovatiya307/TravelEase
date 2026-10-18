// P-10 Expense Claim Detail (I-10, frontend-tasks.md; frontend-spec.md §4.8).
// Single claim record by the `:id` route param, fetched via `useExpenseClaim`
// (F-06). Renders, per frontend-spec.md §4.8:
// - the claim summary — `ExpenseSummary` (C-04): every `ExpenseClaimResponse`
//   field (api-contract.md §5.9), including status via `StatusBadge` and the
//   duplicate flag via `DuplicateIndicator`;
// - the claim's `documents` as a semantic list (file name, type, size);
// - the conditional `ActionBar` (Edit/Review/Reimburse) — the G-01
//   visibility flags are computed here (actionVisibility.ts) and passed with
//   the claim id: "Edit" (owner + `DRAFT`/`PENDING`/`REJECTED`), "Review" (Finance + `PENDING`),
//   "Reimburse" (Finance + `APPROVED`, FR-FIN-03); the bar renders nothing
//   when no flag is true (read-only view).
// Error states branch on the normalized `ClassifiedError` category only
// (frontend-api-mapping.md §4): `RESOURCE_NOT_FOUND` renders the P-22
// not-found fallback in place, `FORBIDDEN_ACTION` the P-21 access-denied
// fallback; every other category degrades to the P-09/P-14 `ErrorBanner` +
// retry convention.

import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'

import { useAuth } from '../../../context/AuthContext'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import type { SessionView } from '../../../models/viewModels'
import * as expenseService from '../../../services/expenseService'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import {
  isExpenseEditable,
  isExpenseSubmittable,
  isReviewable,
  isReimbursable,
} from '../../../utils/actionVisibility'
import { ActionBar } from '../../../components/expenses/ActionBar'
import { ExpenseDocumentList } from '../../../components/expenses/ExpenseDocumentList'
import { ExpenseSummary } from '../../../components/expenses/ExpenseSummary'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import { NotFoundPage } from '../../../shared/NotFoundPage'

/**
 * Expense Claim Detail (P-10, frontend-spec.md §1/§4.8;
 * frontend-tasks.md I-10).
 *
 * Route access (EMPLOYEE/FINANCE/ADMIN) is enforced by the `RequireRole`
 * guard (routeTable.ts P-10), so an authenticated session exists at render
 * time; the `SessionView` is still built null-safe (same convention as
 * `DashboardPage`) because the G-01 flag helpers need a non-null session.
 * The route param `:id` is passed verbatim to `useExpenseClaim`; the fetch
 * re-runs whenever the id changes (e.g. navigating between claims) and
 * `refetch` retries a failed load (F-06).
 */
export function ExpenseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { authState } = useAuth()

  const session: SessionView = {
    userId: authState.userId ?? '',
    roles: authState.roles,
  }

  const claimId = id ?? ''
  const { data, isLoading, error, refetch } = useExpenseClaim(claimId)

  // Outcome of the create flow (ExpenseFormPage): files that failed to upload,
  // or why the new draft could not be submitted. Shown until the user acts.
  const location = useLocation()
  const navigationState = (location.state ?? {}) as { failedUploads?: string[]; submitError?: string }
  const [submitError, setSubmitError] = useState<string | null>(navigationState.submitError ?? null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const failedUploads = navigationState.failedUploads ?? []

  const handleSubmitForReview = async () => {
    setIsSubmitting(true)
    setSubmitError(null)
    try {
      await expenseService.submit(claimId)
      refetch()
    } catch (caught) {
      setSubmitError(
        isClassifiedErrorInstance(caught)
          ? (caught.fieldErrors.find((entry) => entry.message)?.message ?? caught.message)
          : 'The claim could not be submitted. Please try again.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  // G-01 action flags pre-computed for the conditional `ActionBar`
  // (frontend-spec.md §4.8). False while no claim is loaded — the bar is
  // only rendered for a fetched claim anyway.
  const isEditActionVisible = data !== null && isExpenseEditable(data, session)
  const isSubmitActionVisible = data !== null && isExpenseSubmittable(data, session)
  const isReviewActionVisible = data !== null && isReviewable(data, session)
  const isReimburseActionVisible = data !== null && isReimbursable(data, session)

  // Missing record (404) — P-22 not-found fallback in place.
  if (error !== null && error.category === 'RESOURCE_NOT_FOUND') {
    return (
      <section className="screen" data-screen="expense-detail" data-section="expenses">
        <NotFoundPage />
      </section>
    )
  }

  // Forbidden read (403) — P-21 access-denied fallback in place.
  if (error !== null && error.category === 'FORBIDDEN_ACTION') {
    return (
      <section className="screen" data-screen="expense-detail" data-section="expenses">
        <AccessDeniedPage />
      </section>
    )
  }

  return (
    <section className="screen" data-screen="expense-detail" data-section="expenses">
      <Link className="back-link" to="/expenses">
        <i className="icon icon-arrow-left" />
        Back to Expense Claims
      </Link>
      <header className="page-header">
        <div>
          <h1>Expense Claim Detail</h1>
          <p>Claim summary, documents, and available actions.</p>
        </div>
      </header>

      {error !== null ? (
        <div>
          <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
            This expense claim could not be loaded. Please try again.
          </p>
          <button className="button button-secondary button-small" type="button" onClick={refetch}>
            Dismiss
          </button>
        </div>
      ) : null}

      {isLoading ? (
        <p className="empty-state loading-skeleton--detail" role="status" aria-label="Loading">
          Loading…
        </p>
      ) : data !== null ? (
        <>
          {failedUploads.length > 0 ? (
            <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
              These files could not be uploaded: {failedUploads.join(', ')}. The claim was saved as a
              draft and has not been sent to Finance. Edit the claim to attach the receipt again,
              then submit it for review.
            </p>
          ) : null}
          {submitError !== null ? (
            <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
              Not submitted: {submitError}
            </p>
          ) : null}

          <ExpenseSummary claim={data} />

          {/* frontend-spec.md §4.8: the claim's documents, each downloadable. */}
          <ExpenseDocumentList claim={data} />

          {isSubmitActionVisible ? (
            <div className="form-actions">
              <span style={{ marginRight: 'auto', alignSelf: 'center', fontSize: 13 }}>
                {data.status === 'REJECTED'
                  ? 'Corrected this claim? Resubmit it for Finance review.'
                  : 'This claim is a draft. Finance will only see it after you submit it.'}
              </span>
              <button
                className="button button-primary"
                type="button"
                onClick={handleSubmitForReview}
                disabled={isSubmitting}
              >
                {isSubmitting
                  ? 'Submitting…'
                  : data.status === 'REJECTED'
                    ? 'Resubmit for review'
                    : 'Submit for review'}
              </button>
            </div>
          ) : null}

          <ActionBar
            claimId={data.id}
            isEditable={isEditActionVisible}
            isReviewable={isReviewActionVisible}
            isReimbursable={isReimburseActionVisible}
          />
        </>
      ) : (
        <p className="empty-state">No expense claim found.</p>
      )}
    </section>
  )
}
