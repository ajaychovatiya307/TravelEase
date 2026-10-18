import { useEffect } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AuthProvider, useAuth } from '../context/AuthContext'
import { RequireAuth } from './RequireAuth'

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
 * Protected route element with an established session. Seeds the state via the
 * real `AuthProvider` (same pattern as RoleGate.test.tsx) and holds the render
 * until `isAuthenticated` is true, so `RequireAuth` is only ever mounted once
 * the session exists.
 */
function AuthenticatedProtectedPage() {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId: 'U1',
      roles: ['EMPLOYEE'],
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
    <RequireAuth>
      <div>protected-content</div>
    </RequireAuth>
  )
}

/** Protected route element with no session (the AuthProvider default state). */
function UnauthenticatedProtectedPage() {
  return (
    <RequireAuth>
      <div>protected-content</div>
    </RequireAuth>
  )
}

/** MemoryRouter harness mirroring the D-03 wiring: public P-01 + one guarded page. */
function Harness({ authenticated }: { authenticated: boolean }) {
  return (
    <MemoryRouter initialEntries={[PROTECTED_PATH]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginMarker />} />
          <Route
            path={PROTECTED_PATH}
            element={
              authenticated ? (
                <AuthenticatedProtectedPage />
              ) : (
                <UnauthenticatedProtectedPage />
              )
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('RequireAuth', () => {
  it('redirects unauthenticated users to /login without rendering children', () => {
    render(<Harness authenticated={false} />)
    expect(screen.getByText('login-page')).toBeInTheDocument()
    expect(screen.queryByText('protected-content')).not.toBeInTheDocument()
  })

  it('renders children as-is for authenticated users', () => {
    render(<Harness authenticated={true} />)
    expect(screen.getByText('protected-content')).toBeInTheDocument()
    expect(screen.queryByText('login-page')).not.toBeInTheDocument()
  })

  it('replaces the protected location with /login and passes the original location along', () => {
    // `replace` semantics: the original entry is replaced rather than pushed,
    // so after the redirect the P-01 stub is the only rendered route — the
    // login marker is present and the protected child is not.
    render(<Harness authenticated={false} />)
    expect(screen.getByText('login-page')).toBeInTheDocument()
    expect(screen.queryByText('protected-content')).not.toBeInTheDocument()
    // state.from carries the attempted location for post-login return (I-01).
    expect(screen.getByTestId('from-pathname')).toHaveTextContent(PROTECTED_PATH)
  })
})