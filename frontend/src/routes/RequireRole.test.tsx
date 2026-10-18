import { useEffect } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AuthProvider, useAuth } from '../context/AuthContext'
import type { RoleType } from '../context/AuthContext'
import { RequireRole } from './RequireRole'

const PROTECTED_PATH = '/dashboard'

/** Route stub standing in for P-01 (Login) so the redirect target renders a marker. */
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
 * Role-guarded route element with an established session. Seeds the state via
 * the real `AuthProvider` (same pattern as RequireAuth.test.tsx) and holds the
 * render until `isAuthenticated` is true, so `RequireRole` is only ever
 * mounted once the session exists.
 */
function RoleGuardedPage({ roles, requiredRoles }: { roles: RoleType[]; requiredRoles: RoleType[] }) {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId: 'U1',
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: null,
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <RequireRole requiredRoles={requiredRoles}>
      <div>protected-content</div>
    </RequireRole>
  )
}

/** Role-guarded route element with no session (the AuthProvider default state). */
function UnauthenticatedGuardedPage() {
  return (
    <RequireRole requiredRoles={['ADMIN', 'FINANCE']}>
      <div>protected-content</div>
    </RequireRole>
  )
}

/** MemoryRouter harness mirroring the D-03 wiring: public P-01 + one role-guarded page. */
function Harness({ roles, requiredRoles = ['ADMIN', 'FINANCE'] }: { roles?: RoleType[]; requiredRoles?: RoleType[] }) {
  return (
    <MemoryRouter initialEntries={[PROTECTED_PATH]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginMarker />} />
          <Route
            path={PROTECTED_PATH}
            element={
              roles !== undefined ? (
                <RoleGuardedPage roles={roles} requiredRoles={requiredRoles} />
              ) : (
                <UnauthenticatedGuardedPage />
              )
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('RequireRole', () => {
  it('redirects unauthenticated users to /login — no Access Denied without a session', () => {
    render(<Harness />)
    expect(screen.getByText('login-page')).toBeInTheDocument()
    // state.from carries the attempted location for post-login return (I-01).
    expect(screen.getByTestId('from-pathname')).toHaveTextContent(PROTECTED_PATH)
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument()
    expect(screen.queryByText('protected-content')).not.toBeInTheDocument()
  })

  it('renders AccessDeniedPage in place for a session holding none of the required roles', () => {
    render(<Harness roles={['EMPLOYEE']} requiredRoles={['ADMIN', 'FINANCE']} />)
    // Distinctive P-21 content: message + Dashboard link.
    expect(screen.getByText('Access Denied')).toBeInTheDocument()
    expect(screen.getByText('You do not have permission to view this page.')).toBeInTheDocument()
    expect(screen.getByText('Back to Dashboard')).toBeInTheDocument()
    expect(screen.queryByText('protected-content')).not.toBeInTheDocument()
    // In-place denial, no navigation: the /login stub never renders, and the
    // protected route (where P-21 is mounted) is still the active route.
    expect(screen.queryByText('login-page')).not.toBeInTheDocument()
  })

  it('renders children when the session holds one of the required roles (multi-role intersection)', () => {
    render(<Harness roles={['EMPLOYEE', 'MANAGER']} requiredRoles={['MANAGER', 'FINANCE']} />)
    expect(screen.getByText('protected-content')).toBeInTheDocument()
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument()
    expect(screen.queryByText('login-page')).not.toBeInTheDocument()
  })

  it('renders children when the session holds a single exactly matching role', () => {
    render(<Harness roles={['FINANCE']} requiredRoles={['ADMIN', 'FINANCE']} />)
    expect(screen.getByText('protected-content')).toBeInTheDocument()
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument()
  })
})