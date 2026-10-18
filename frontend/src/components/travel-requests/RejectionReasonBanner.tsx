// Prior rejection reason banner (J-01, frontend-tasks.md — "J. Forms").
// Read-only banner shown at the top of `TravelRequestForm` in Resubmit mode
// (frontend-clarification.md Q12, frontend-spec.md §4.5): it surfaces the
// prior rejection reason of the previously `REJECTED` travel request so the
// employee can address it before resubmitting (frontend-spec.md §5 form row,
// "Resubmit mode additionally shows the prior rejection reason").
//
// Pure presentation — the reason text arrives from the hosting page, which
// reads `TravelRequestResponse.rejectionReason` (api-contract.md §5.3).
// Rendering is consistent with the shared `ErrorBanner` (uses the warning
// palette, as this is prior-state information, not a current submit error).

import './RejectionReasonBanner.css'

interface RejectionReasonBannerProps {
  /**
   * Prior rejection reason text. `null`/`undefined`/blank → nothing rendered
   * (a rejected record always carries a non-blank reason server-side per
   * VAL-12, so the banner simply stays hidden when the page has none).
   */
  reason?: string | null
  /** Heading rendered above the reason text. */
  title?: string
}

/**
 * Persistent read-only banner with the prior rejection reason
 * (frontend-clarification.md Q12: "displayed as a persistent read-only
 * banner at the top of the form").
 */
export function RejectionReasonBanner({
  reason,
  title = 'Prior rejection reason',
}: RejectionReasonBannerProps) {
  const trimmedReason = (reason ?? '').trim()
  if (trimmedReason.length === 0) {
    return null
  }

  return (
    <div className="rejection-reason-banner" role="alert">
      <span className="rejection-reason-banner__title">{title}</span>
      <p className="rejection-reason-banner__reason">{trimmedReason}</p>
    </div>
  )
}
