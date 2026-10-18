import { useEffect } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AuthProvider, useAuth } from '../context/AuthContext'
import type { RoleType } from '../context/AuthContext'
import { RequireRole } from './RequireRole'
import { routeTable } from './routeTable'
import type { PageId } from './routeTable'

/**
 * Route guard coverage across the real route table (frontend-tasks.md N-05).
 *
 * RequireAuth.test.tsx and RequireRole.test.tsx cover the guards' behavior in
 * isolation against a synthetic path. This suite closes N-05's remaining
 * acceptance criterion — the same three scenarios (unauthenticated, wrong
 * role, authorized) exercised for a representative sample of routes — by
 * driving `RequireRole` with the `requiredRoles` each route actually declares
 * in `routeTable` (frontend-plan.md §3).
 *
 * The sample covers every distinct route-level role grouping in the table:
 * all-roles, EMPLOYEE-only, MANAGER/FINANCE, EMPLOYEE/FINANCE/ADMIN,
 * FINANCE-only, ADMIN-only and ADMIN/FINANCE, plus one path-parameter route.
 * The final test asserts that sample stays exhaustive as the table grows.
 *
 * Pages are stubbed deliberately: this verifies guard decisions per route, not
 * page rendering (covered by Section I page tests and Router.test.tsx), so no
 * page data dependency can mask an allow/deny regression.
 */

const PROTECTED_CONTENT = 'protected-content'

interface GuardCase {
  pageId: PageId
  /** Concrete URL to visit; equals the route path unless it carries params. */
  url: string
  /** A role the route grants access to. */
  authorizedRole: RoleType
  /**
   * Roles held by an authenticated user who must be denied. Empty for routes
   * open to every role, where a session holding no roles is the only denial.
   */
  deniedRoles: RoleType[]
}

/** Representative sample: one route per distinct `requiredRoles` grouping. */
const GUARD_CASES: GuardCase[] = [
  // All roles (P-03) — denial only for a session carrying no roles at all.
  { pageId: 'P-03', url: '/travel-requests', authorizedRole: 'EMPLOYEE', deniedRoles: [] },
  // EMPLOYEE-only creation route (P-05).
  { pageId: 'P-05', url: '/travel-requests/new', authorizedRole: 'EMPLOYEE', deniedRoles: ['MANAGER'] },
  // MANAGER/FINANCE approval queue (P-08).
  { pageId: 'P-08', url: '/approvals/travel-requests', authorizedRole: 'MANAGER', deniedRoles: ['EMPLOYEE'] },
  // EMPLOYEE/FINANCE/ADMIN expense list (P-09) — MANAGER is excluded.
  { pageId: 'P-09', url: '/expenses', authorizedRole: 'ADMIN', deniedRoles: ['MANAGER'] },
  // FINANCE-only review queue (P-14).
  { pageId: 'P-14', url: '/expenses/review-queue', authorizedRole: 'FINANCE', deniedRoles: ['ADMIN'] },
  // FINANCE-only review route carrying a path parameter (P-13).
  { pageId: 'P-13', url: '/expenses/42/review', authorizedRole: 'FINANCE', deniedRoles: ['EMPLOYEE'] },
  // ADMIN-only mappings page (P-17).
  { pageId: 'P-17', url: '/admin/mappings', authorizedRole: 'ADMIN', deniedRoles: ['FINANCE'] },
  // ADMIN/FINANCE audit log (P-20).
  { pageId: 'P-20', url: '/audit', authorizedRole: 'FINANCE', deniedRoles: ['EMPLOYEE'] },
]

function entryFor(pageId: PageId) {
  const entry = routeTable.find((candidate) => candidate.pageId === pageId)
  if (!entry) {
    throw new Error(`routeTable has no entry for ${pageId}`)
  }
  return entry
}

/** Route stub standing in for P-01 (Login) so the redirect target is observable. */
function LoginMarker() {
  const location = useLocation()
  const from = (location.state as { from?: { pathname?: string } } | null)?.from
  return (
    <div>
      <span>login-page</span>
      <span data-testid="from-pathname">{from?.pathname ?? 'none'}</span>
    </div>
  )
}

/**
 * Guarded route element. Seeds the session through the real `AuthProvider`
 * and holds the render until `isAuthenticated` is true (the pattern used by
 * RequireRole.test.tsx), so the guard only ever mounts once the session
 * exists. With `roles` omitted the provider's unauthenticated default stands.
 */
function GuardedPage({ requiredRoles, roles }: { requiredRoles: RoleType[]; roles?: RoleType[] }) {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    if (roles === undefined) {
      return
    }
    setAuthState({
      userId: 'U1',
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (roles !== undefined && !authState.isAuthenticated) {
    return null
  }

  return (
    <RequireRole requiredRoles={requiredRoles}>
      <div>{PROTECTED_CONTENT}</div>
    </RequireRole>
  )
}

/** Harness mirroring the D-03 wiring: public P-01 plus the guarded route under test. */
function Harness({
  path,
  url,
  requiredRoles,
  roles,
}: {
  path: string
  url: string
  requiredRoles: RoleType[]
  roles?: RoleType[]
}) {
  return (
    <MemoryRouter initialEntries={[url]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginMarker />} />
          <Route path={path} element={<GuardedPage requiredRoles={requiredRoles} roles={roles} />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('route guards across the route table (N-05)', () => {
  describe.each(GUARD_CASES)('$pageId ($url)', ({ pageId, url, authorizedRole, deniedRoles }) => {
    const entry = entryFor(pageId)

    it('redirects an unauthenticated visit to /login, carrying the attempted location', () => {
      render(<Harness path={entry.path} url={url} requiredRoles={entry.requiredRoles} />)
      expect(screen.getByText('login-page')).toBeInTheDocument()
      expect(screen.getByTestId('from-pathname')).toHaveTextContent(url)
      // A session-less user is never shown Access Denied (ui-model.md §2).
      expect(screen.queryByText('Access Denied')).not.toBeInTheDocument()
      expect(screen.queryByText(PROTECTED_CONTENT)).not.toBeInTheDocument()
    })

    it('renders Access Denied in place for an authenticated user without a required role', () => {
      render(
        <Harness path={entry.path} url={url} requiredRoles={entry.requiredRoles} roles={deniedRoles} />,
      )
      expect(screen.getByText('Access Denied')).toBeInTheDocument()
      // Denial is in place — no navigation to the login page.
      expect(screen.queryByText('login-page')).not.toBeInTheDocument()
      expect(screen.queryByText(PROTECTED_CONTENT)).not.toBeInTheDocument()
    })

    it(`renders the page for an authorized ${authorizedRole} session`, () => {
      render(
        <Harness
          path={entry.path}
          url={url}
          requiredRoles={entry.requiredRoles}
          roles={[authorizedRole]}
        />,
      )
      expect(screen.getByText(PROTECTED_CONTENT)).toBeInTheDocument()
      expect(screen.queryByText('Access Denied')).not.toBeInTheDocument()
      expect(screen.queryByText('login-page')).not.toBeInTheDocument()
    })
  })

  it('covers every distinct route-level role grouping in the route table', () => {
    const sampled = new Set(
      GUARD_CASES.map(({ pageId }) => [...entryFor(pageId).requiredRoles].sort().join('|')),
    )
    const allProtected = new Set(
      routeTable
        .filter((entry) => !entry.isPublic)
        .map((entry) => [...entry.requiredRoles].sort().join('|')),
    )
    expect([...allProtected].filter((grouping) => !sampled.has(grouping))).toEqual([])
  })
})
