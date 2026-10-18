import { RoleSection } from '../../components/dashboard/RoleSection'
import { useAuth } from '../../context/AuthContext'
import { useDashboardSummary } from '../../hooks/useDashboardSummary'
import type { SessionView } from '../../models/viewModels'

/**
 * Dashboard / Home (P-02, frontend-spec.md §4.2; frontend-tasks.md I-02 part 2).
 *
 * Renders the role-conditional sections of frontend-spec.md §4.2 /
 * frontend-clarification.md Q1 as `RoleSection` cards in a fixed stack order:
 *
 * 1. "My Travel Requests & Expenses" — always (every role): the caller's own
 *    pending travel requests + pending expenses. The header count is the sum
 *    of both; a per-type breakdown line shows each number. Links into P-03
 *    (`/travel-requests`) and P-09 (`/expenses`).
 * 2. "Pending My Approval" — MANAGER only: `pendingApprovals`. Link into the
 *    P-08 approval queue (`/approvals/travel-requests`).
 * 3. "Finance Review Queue" — FINANCE only: `pendingExpenseReviews`. Link
 *    into the P-14 review queue (`/expenses/review-queue`).
 * 4. "Admin Shortcuts" — ADMIN only: a static link set into the four admin
 *    pages (P-16..P-19), no count (ui-model.md §7).
 *
 * A multi-role user sees ALL applicable sections stacked; a single-role user
 * sees only their applicable section(s) (frontend-clarification.md Q1).
 * Visibility is computed directly from the session's normalized role set.
 *
 * The route is `RequireRole`-guarded for all four roles (routeTable.ts P-02),
 * so an authenticated session exists at render time; the `SessionView` is
 * built null-safe anyway. Counts/loading/error come from `useDashboardSummary`
 * (I-02 part 1); on error the count-bearing sections degrade to the
 * "Count unavailable" note — the page itself never blanks out and all links
 * stay available.
 */
export function DashboardPage() {
  const { authState } = useAuth()
  const session: SessionView = {
    userId: authState.userId ?? '',
    roles: authState.roles,
  }

  const { data, isLoading, error } = useDashboardSummary(session)

  const isManager = session.roles.includes('MANAGER')
  const isFinance = session.roles.includes('FINANCE')
  const isAdmin = session.roles.includes('ADMIN')

  // Per-section error note: the hook's classified error (if any) is surfaced
  // through each count-bearing RoleSection's error display instead of
  // replacing the page (the page must never blank out on error).
  const errorNote = error === null ? null : error.message

  return (
    <section className="screen" data-screen="dashboard" data-section="dashboard">
      <header className="page-header">
        <div>
          <h1>Dashboard</h1>
        </div>
      </header>

      {/* Always rendered for every role (frontend-clarification.md Q1). The
          header count is the combined pending total; the breakdown line shows
          each of the two numbers. */}
      <RoleSection
        title="My Travel Requests & Expenses"
        count={data === null ? null : data.pendingTravelRequests + data.pendingExpenses}
        isLoading={isLoading}
        error={errorNote}
        summary={
          data === null ? null : (
            <p className="dashboard-page__breakdown">
              {data.pendingTravelRequests} pending travel requests, {data.pendingExpenses} pending
              expenses
            </p>
          )
        }
        links={[
          { label: 'My Travel Requests', to: '/travel-requests' },
          { label: 'My Expenses', to: '/expenses' },
        ]}
      />

      {isManager ? (
        <RoleSection
          title="Pending My Approval"
          count={data === null ? null : data.pendingApprovals}
          isLoading={isLoading}
          error={errorNote}
          links={[{ label: 'Approval Queue', to: '/approvals/travel-requests' }]}
        />
      ) : null}

      {isFinance ? (
        <RoleSection
          title="Finance Review Queue"
          count={data === null ? null : data.pendingExpenseReviews}
          isLoading={isLoading}
          error={errorNote}
          links={[{ label: 'Review Queue', to: '/expenses/review-queue' }]}
        />
      ) : null}

      {isAdmin ? (
        // Static link set into P-16..P-19 — no count (ui-model.md §7).
        <RoleSection
          title="Admin Shortcuts"
          links={[
            { label: 'Users', to: '/admin/users' },
            { label: 'Employee-Manager Mappings', to: '/admin/mappings' },
            { label: 'Policy Configuration', to: '/admin/policy-configs' },
            { label: 'Expense Categories', to: '/admin/expense-categories' },
          ]}
        />
      ) : null}
    </section>
  )
}
