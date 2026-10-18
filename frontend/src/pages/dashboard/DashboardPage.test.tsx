// DashboardPage component tests (I-02 part 2, P-02). Mounts the real page
// inside a MemoryRouter + real AuthProvider with a seeded session (same
// pattern as Router.test.tsx); `useDashboardSummary` is fully mocked so no
// network calls happen and the resolved/loading/error states are controllable
// per test (frontend-spec.md §4.2; frontend-clarification.md Q1: a multi-role
// user sees all applicable sections stacked in the fixed order).

import { useEffect } from 'react'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { RoleType } from '../../context/AuthContext'
import type { ServerDataState } from '../../hooks/useAsyncResource'
import type { DashboardSummaryView } from '../../models/viewModels'
import type { ClassifiedErrorInstance } from '../../services/httpClient'
import { DashboardPage } from './DashboardPage'

/** The controllable state the mocked `useDashboardSummary` returns. */
const summaryState = vi.hoisted(
  (): ServerDataState<DashboardSummaryView> => ({
    data: null,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
)

vi.mock('../../hooks/useDashboardSummary', () => ({
  useDashboardSummary: () => summaryState,
}))

/** A `ClassifiedError`-shaped error carried on a real `Error` instance. */
function makeClassifiedError(): ClassifiedErrorInstance {
  return Object.assign(new Error('Something went wrong'), {
    category: 'INTERNAL_ERROR',
    status: 500,
    errorCode: 'INTERNAL_ERROR',
    timestamp: null,
    fieldErrors: [],
  }) as ClassifiedErrorInstance
}

/**
 * DashboardPage inside a MemoryRouter + real AuthProvider with the session
 * seeded for `roles`: the page is held back until `isAuthenticated` is true
 * (same pattern as Router.test.tsx), so it only ever mounts once the session
 * exists.
 */
function Harness({ roles }: { roles: RoleType[] }) {
  return (
    <MemoryRouter>
      <AuthProvider>
        <SeededDashboard roles={roles} />
      </AuthProvider>
    </MemoryRouter>
  )
}

function SeededDashboard({ roles }: { roles: RoleType[] }) {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId: 'U1',
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!authState.isAuthenticated) {
    return null
  }

  return <DashboardPage />
}

/** The `<section>` element of the RoleSection whose h2 heading is `name`. */
function sectionFor(name: string): HTMLElement {
  const section = screen.getByRole('heading', { level: 2, name: name }).closest('section')
  if (section === null) {
    throw new Error(`No section found for heading ${name}`)
  }
  return section
}

describe('DashboardPage (I-02 part 2, P-02)', () => {
  beforeEach(() => {
    summaryState.data = null
    summaryState.isLoading = false
    summaryState.error = null
  })

  it('renders only the always section for an EMPLOYEE', () => {
    render(<Harness roles={['EMPLOYEE']} />)
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'My Travel Requests & Expenses' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Pending My Approval' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Finance Review Queue' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Admin Shortcuts' }),
    ).not.toBeInTheDocument()
  })

  it('renders the always section plus "Pending My Approval" for a MANAGER', () => {
    render(<Harness roles={['MANAGER']} />)
    expect(
      screen.getByRole('heading', { level: 2, name: 'My Travel Requests & Expenses' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Pending My Approval' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Finance Review Queue' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Admin Shortcuts' }),
    ).not.toBeInTheDocument()
  })

  it('renders the always section plus "Finance Review Queue" for a FINANCE user', () => {
    render(<Harness roles={['FINANCE']} />)
    expect(
      screen.getByRole('heading', { level: 2, name: 'My Travel Requests & Expenses' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Finance Review Queue' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Pending My Approval' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Admin Shortcuts' }),
    ).not.toBeInTheDocument()
  })

  it('renders the always section plus "Admin Shortcuts" with its four links for an ADMIN', () => {
    render(<Harness roles={['ADMIN']} />)
    expect(
      screen.getByRole('heading', { level: 2, name: 'My Travel Requests & Expenses' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Admin Shortcuts' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Pending My Approval' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Finance Review Queue' }),
    ).not.toBeInTheDocument()
    // Static link set (ui-model.md §7): the four admin page links, no count.
    const shortcuts = sectionFor('Admin Shortcuts')
    expect(within(shortcuts).queryByText(/pending/i)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Users' })).toHaveAttribute('href', '/admin/users')
    expect(
      screen.getByRole('link', { name: 'Employee-Manager Mappings' }),
    ).toHaveAttribute('href', '/admin/mappings')
    expect(
      screen.getByRole('link', { name: 'Policy Configuration' }),
    ).toHaveAttribute('href', '/admin/policy-configs')
    expect(
      screen.getByRole('link', { name: 'Expense Categories' }),
    ).toHaveAttribute('href', '/admin/expense-categories')
  })

  it('renders all four sections in fixed order for a MANAGER+FINANCE+ADMIN user (Q1)', () => {
    render(<Harness roles={['MANAGER', 'FINANCE', 'ADMIN']} />)
    const headings = screen.getAllByRole('heading', { level: 2 })
    expect(headings.map((heading) => heading.textContent)).toEqual([
      'My Travel Requests & Expenses',
      'Pending My Approval',
      'Finance Review Queue',
      'Admin Shortcuts',
    ])
  })

  it('shows the resolved counts and the per-type breakdown line', () => {
    summaryState.data = {
      pendingTravelRequests: 2,
      pendingExpenses: 1,
      pendingApprovals: 3,
      pendingExpenseReviews: 4,
    }
    render(<Harness roles={['EMPLOYEE', 'MANAGER', 'FINANCE']} />)

    const mySection = sectionFor('My Travel Requests & Expenses')
    // Header count is the combined pending total (2 + 1 = 3).
    expect(within(mySection).getByText('3 pending')).toBeInTheDocument()
    expect(
      within(mySection).getByText('2 pending travel requests, 1 pending expenses'),
    ).toBeInTheDocument()
    expect(within(sectionFor('Pending My Approval')).getByText('3 pending')).toBeInTheDocument()
    expect(
      within(sectionFor('Finance Review Queue')).getByText('4 pending'),
    ).toBeInTheDocument()
  })

  it('renders zero counts when the loaded dashboard has no pending work', () => {
    summaryState.data = {
      pendingTravelRequests: 0,
      pendingExpenses: 0,
      pendingApprovals: 0,
      pendingExpenseReviews: 0,
    }

    render(<Harness roles={['EMPLOYEE', 'MANAGER', 'FINANCE']} />)

    expect(
      within(sectionFor('My Travel Requests & Expenses')).getByText('0 pending'),
    ).toBeInTheDocument()
    expect(
      within(sectionFor('My Travel Requests & Expenses')).getByText(
        '0 pending travel requests, 0 pending expenses',
      ),
    ).toBeInTheDocument()
    expect(within(sectionFor('Pending My Approval')).getByText('0 pending')).toBeInTheDocument()
    expect(within(sectionFor('Finance Review Queue')).getByText('0 pending')).toBeInTheDocument()
  })

  it('keeps every section rendered with a loading placeholder while the summary loads', () => {
    summaryState.isLoading = true
    render(<Harness roles={['MANAGER', 'FINANCE', 'ADMIN']} />)

    // The page itself never blanks out while the counts are in flight.
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'My Travel Requests & Expenses' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Pending My Approval' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Finance Review Queue' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Admin Shortcuts' }),
    ).toBeInTheDocument()
    // A status placeholder per count-bearing section; the Admin Shortcuts
    // section is a static link set and carries no count.
    expect(screen.getAllByRole('status')).toHaveLength(3)
  })

  it('degrades counts to "Count unavailable" on error, keeping sections and links', () => {
    summaryState.error = makeClassifiedError()
    const { container } = render(<Harness roles={['MANAGER', 'FINANCE', 'ADMIN']} />)

    // The page never blanks out: every section still renders.
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'My Travel Requests & Expenses' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Pending My Approval' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Finance Review Queue' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Admin Shortcuts' }),
    ).toBeInTheDocument()
    // One inline note per count-bearing section, and the raw classified
    // message is never shown to the user.
    expect(screen.getAllByText('Count unavailable')).toHaveLength(3)
    expect(container.querySelectorAll('.role-section__count--error')).toHaveLength(3)
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument()
    // Links stay available even when the counts fail.
    expect(
      screen.getByRole('link', { name: 'My Travel Requests' }),
    ).toHaveAttribute('href', '/travel-requests')
    expect(
      screen.getByRole('link', { name: 'Approval Queue' }),
    ).toHaveAttribute('href', '/approvals/travel-requests')
  })
})