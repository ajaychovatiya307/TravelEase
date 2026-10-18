import { useEffect } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AuthProvider, useAuth } from '../context/AuthContext'
import { ThemeProvider } from '../context/ThemeContext'
import type { RoleType } from '../context/AuthContext'
import { AppRoutes } from './Router'

/**
 * AppRoutes mounted inside a MemoryRouter starting at `path` (frontend-tasks.md
 * D-03, part 1). With `roles` omitted, the default unauthenticated AuthProvider
 * state is used; otherwise the session is seeded via the real AuthProvider and
 * AppRoutes is held back until `isAuthenticated` is true (same pattern as
 * RequireRole.test.tsx), so the route tree is only ever mounted once the
 * session exists.
 */
function Harness({ roles, path }: { roles?: RoleType[]; path: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <ThemeProvider>
        <AuthProvider>
          {roles === undefined ? <AppRoutes /> : <AuthenticatedAppRoutes roles={roles} />}
        </AuthProvider>
      </ThemeProvider>
    </MemoryRouter>
  )
}

function AuthenticatedAppRoutes({ roles }: { roles: RoleType[] }) {
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

  return <AppRoutes />
}

describe('AppRoutes (D-03 route wiring)', () => {
  it('redirects an unauthenticated visit to a protected path to the P-01 login page inside AuthLayout', () => {
    render(<Harness path="/travel-requests" />)
    // Real P-01 login page: credential fields and submit action, inside the minimal auth layout.
    const heading = screen.getByRole('heading', { name: 'Login' })
    expect(heading.closest('.auth-layout')).not.toBeNull()
    expect(screen.getByRole('textbox', { name: 'Username' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Password/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    // The protected page (P-03) never renders, and the authenticated shell
    // does not either.
    expect(screen.queryByText('P-03')).not.toBeInTheDocument()
    expect(screen.queryByRole('complementary', { name: 'Primary navigation' })).not.toBeInTheDocument()
  })

  it('renders the P-03 placeholder inside AuthenticatedLayout for an EMPLOYEE on /travel-requests', () => {
    render(<Harness roles={['EMPLOYEE']} path="/travel-requests" />)
    // Real layout markers: sidebar region, top bar, logout action.
    expect(screen.getByRole('complementary', { name: 'Primary navigation' })).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Logout' })).toBeInTheDocument()
    // P-03 real page in the layout content outlet.
    const heading = screen.getByRole('heading', { name: 'My Travel Requests' })
    expect(heading.closest('.authenticated-layout__content')).not.toBeNull()
    expect(screen.getByRole('link', { name: 'New Travel Request' })).toBeInTheDocument()
  })

  it('renders AccessDeniedPage in place for an EMPLOYEE on the ADMIN-only /admin/users (P-16)', () => {
    render(<Harness roles={['EMPLOYEE']} path="/admin/users" />)
    // Distinctive P-21 content, rendered in place inside the app shell.
    expect(screen.getByText('Access Denied')).toBeInTheDocument()
    expect(screen.getByText('You do not have permission to view this page.')).toBeInTheDocument()
    expect(screen.getByText('Back to Dashboard')).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBeInTheDocument()
    // Neither the protected page nor a redirect to login.
    expect(screen.queryByText('P-16')).not.toBeInTheDocument()
    expect(screen.queryByText('P-01')).not.toBeInTheDocument()
  })

  it('renders the registered Users page for an ADMIN on /admin/users', () => {
    render(<Harness roles={['ADMIN']} path="/admin/users" />)
    expect(screen.getByTestId('users-page')).toBeInTheDocument()
    expect(screen.queryByText('Access Denied')).not.toBeInTheDocument()
  })

  it('renders the real NotFoundPage (P-22) for an unmatched path, without authentication', () => {
    render(<Harness path="/nope" />)
    expect(screen.getByText('Not Found')).toBeInTheDocument()
    expect(screen.getByText('The page or record you are looking for could not be found.')).toBeInTheDocument()
    // The catch-all is public: no login redirect, no placeholder stand-in.
    expect(screen.queryByText('P-01')).not.toBeInTheDocument()
    expect(screen.queryByText(/Placeholder/)).not.toBeInTheDocument()
  })

  it('renders the real GenericErrorPage (P-23) on /error without authentication', () => {
    render(<Harness path="/error" />)
    expect(screen.getByText('Something Went Wrong')).toBeInTheDocument()
    expect(screen.getByText('An unexpected error occurred. Please try again later.')).toBeInTheDocument()
    expect(screen.queryByText('P-01')).not.toBeInTheDocument()
  })
})