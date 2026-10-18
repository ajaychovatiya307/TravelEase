// P-02 Dashboard section block (I-02 part 1, frontend-tasks.md). One card per
// role section (frontend-spec.md §4.2): heading, optional pending-count
// summary, and links into the relevant List/Queue pages. Sections are gated
// per role by the DashboardPage (RoleGate, C-08; frontend-clarification.md Q1
// — a multi-role user sees all applicable sections stacked); "Admin
// Shortcuts" passes no `count` because it is a static link set (ui-model.md §7).
// Pure presentation — counts come from `useDashboardSummary` (I-02 part 1).

import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** One entry link of a section, rendered with react-router `Link`. */
export interface RoleSectionLink {
  /** Accessible label of the link (e.g. "Open approval queue"). */
  label: string
  /** Route of the target List/Queue page (e.g. `/travel-requests?status=PENDING`). */
  to: string
}

interface RoleSectionProps {
  /** Section heading, rendered as an accessible `h2` (e.g. "Pending My Approval"). */
  title: string
  /**
   * Pending-item count; `null`/`undefined` = no count is shown at all (e.g.
   * the Admin Shortcuts section, which is a static link set).
   */
  count?: number | null
  /**
   * `true` while the count is being fetched — a placeholder is shown instead
   * of the number. Pass this only for sections that display a count.
   */
  isLoading?: boolean
  /**
   * Non-empty display message (e.g. a classified error's message) — when set,
   * a small inline "Count unavailable" note is shown instead of the count.
   * The section never crashes on error; links stay available.
   */
  error?: string | null
  /**
   * Optional body content rendered between the header and the link list —
   * e.g. the "My Travel Requests & Expenses" section's per-type breakdown
   * line (I-02 part 2). `null`/`undefined` = nothing rendered.
   */
  summary?: ReactNode | null
  /** Links into the relevant List/Queue pages for this section. */
  links: RoleSectionLink[]
}

/**
 * Summary text for the section header:
 * - resolved count (and no error/loading) → "N pending"
 * - error (non-empty `error` string) → "Count unavailable" note
 * - still loading → "…" placeholder (`role="status"` for screen readers)
 * - no count expected (Admin Shortcuts) → nothing
 */
function summaryNode(
  count: number | null | undefined,
  isLoading: boolean,
  error: string | null,
): ReactNode {
  const hasError = typeof error === 'string' && error.length > 0

  if (typeof count === 'number' && !hasError && !isLoading) {
    return <span className="role-section__count">{count} pending</span>
  }
  if (hasError) {
    return <span className="role-section__count role-section__count--error">Count unavailable</span>
  }
  if (isLoading) {
    return (
      <span className="role-section__count role-section__count--loading" role="status">
        …
      </span>
    )
  }
  return null
}

/**
 * Renders one dashboard role section: a `<section>` with an `h2` heading, an
 * optional pending-count summary ("N pending", with loading placeholder and
 * inline error note), and the section's link list.
 */
export function RoleSection({
  title,
  count,
  isLoading = false,
  error = null,
  summary = null,
  links,
}: RoleSectionProps) {
  return (
    <section className="card card-narrow role-section">
      <div className="card-header role-section__header">
        <h2>{title}</h2>
        {count !== undefined ? (
          <span className="status status-pending">
            <i className="icon icon-hourglass" />
            {summaryNode(count, isLoading, error)}
          </span>
        ) : null}
      </div>
      {summary !== null ? (
        <div className="dashboard-note role-section__summary">{summary}</div>
      ) : null}
      <ul className="link-list">
        {links.map((link) => (
          <li key={link.to}>
            <Link className="link-row" to={link.to}>
              <span className="link-row-icon">
                <i
                  className={`icon icon-${link.to.includes('travel-requests') ? 'plane' : 'receipt'}`}
                />
              </span>
              <span className="link-row-label">{link.label}</span>
              <i className="icon icon-chevron-right" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
