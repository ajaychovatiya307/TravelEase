import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { RoleType } from '../../context/AuthContext'
import { authService } from '../../services/authService'
import type { ClassifiedErrorInstance } from '../../services/httpClient'
import { LoginPage } from './LoginPage'

vi.mock('../../services/authService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/authService')>()),
  authService: { login: vi.fn(), completePasswordChange: vi.fn() },
}))

const mockedLogin = vi.mocked(authService.login)
const mockedComplete = vi.mocked(authService.completePasswordChange)

function apiError(status: number, errorCode: string, fieldErrors: { field: string; message: string }[] = []) {
  const error = new Error(errorCode) as ClassifiedErrorInstance
  error.name = 'ClassifiedError'
  error.category = status === 400 ? 'VALIDATION_ERROR' : 'INTERNAL_ERROR'
  error.status = status
  error.errorCode = errorCode
  error.timestamp = null
  error.fieldErrors = fieldErrors
  return error
}

function base64UrlEncode(text: string): string {
  return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function makeToken(sub: string, roles: RoleType[], exp = Math.floor(Date.now() / 1000) + 3600) {
  const header = base64UrlEncode('{"alg":"HS256","typ":"JWT"}')
  const payload = base64UrlEncode(JSON.stringify({ sub, roles, iat: Math.floor(Date.now() / 1000), exp }))
  return `${header}.${payload}.signature`
}

function SessionProbe() {
  const { authState } = useAuth()
  const location = useLocation()
  return (
    <div data-testid="session-probe">
      <span data-testid="is-authenticated">{String(authState.isAuthenticated)}</span>
      <span data-testid="user-id">{authState.userId ?? 'none'}</span>
      <span data-testid="roles">{authState.roles.join(',')}</span>
      <span data-testid="session-expires-at">{authState.sessionExpiresAt ?? 'none'}</span>
      <span data-testid="token">{authState.token ?? 'none'}</span>
      <span data-testid="location">{`${location.pathname}${location.search}`}</span>
    </div>
  )
}

function LandingPage() {
  const location = useLocation()
  return (
    <>
      <div data-testid="landing-marker">{location.pathname}</div>
      <SessionProbe />
    </>
  )
}

function LoginWithProbe() {
  return (
    <>
      <LoginPage />
      <SessionProbe />
    </>
  )
}

function Harness({ path = '/login' }: { path?: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginWithProbe />} />
          <Route path="/" element={<LandingPage />} />
          <Route path="/travel-requests" element={<LandingPage />} />
          <Route path="/approvals/travel-requests" element={<LandingPage />} />
          <Route path="/expenses/review-queue" element={<LandingPage />} />
          <Route path="/admin/users" element={<LandingPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

function unauthorizedError(): ClassifiedErrorInstance {
  const error = new Error('Unauthorized') as ClassifiedErrorInstance
  error.name = 'ClassifiedError'
  error.category = 'INTERNAL_ERROR'
  error.status = 401
  error.errorCode = 'UNAUTHORIZED'
  error.timestamp = null
  error.fieldErrors = []
  return error
}

beforeEach(() => {
  mockedLogin.mockReset()
  mockedComplete.mockReset()
  sessionStorage.clear()
})

describe('LoginPage (P-01)', () => {
  it('renders username and password fields with the Sign in button', () => {
    render(<Harness />)

    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Username' })).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText(/Password/)).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByText('Your session has expired, please log in again.')).not.toBeInTheDocument()
  })

  it('shows field-level required errors without calling the API', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByText('Username is required.')).toBeInTheDocument()
    expect(screen.getByText('Password is required.')).toBeInTheDocument()
    expect(mockedLogin).not.toHaveBeenCalled()
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
  })

  it('disables the form while the login request is pending', async () => {
    const user = userEvent.setup()
    mockedLogin.mockReturnValue(new Promise(() => {}))
    render(<Harness />)

    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'user@travelease.example')
    await user.type(screen.getByLabelText(/Password/), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('button', { name: 'Signing in…' })).toBeDisabled()
  })

  it.each([
    { role: 'EMPLOYEE', path: '/travel-requests' },
    { role: 'MANAGER', path: '/approvals/travel-requests' },
    { role: 'FINANCE', path: '/expenses/review-queue' },
    { role: 'ADMIN', path: '/admin/users' },
  ] as const)('authenticates and routes a $role account to $path', async ({ role, path }) => {
    const user = userEvent.setup()
    const exp = Math.floor(Date.now() / 1000) + 3600
    const token = makeToken('5', [role], exp)
    mockedLogin.mockResolvedValueOnce({ token })
    render(<Harness />)

    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'sam@travelease.example')
    await user.type(screen.getByLabelText(/Password/), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(mockedLogin).toHaveBeenCalledWith({
      username: 'sam@travelease.example',
      password: 'secret-password',
    })
    await waitFor(() => expect(screen.getByTestId('landing-marker')).toHaveTextContent(path))
    expect(screen.getByTestId('landing-marker')).toHaveTextContent(path)
    expect(screen.getByTestId('location')).toHaveTextContent(path)
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('user-id')).toHaveTextContent('5')
    expect(screen.getByTestId('roles')).toHaveTextContent(role)
    expect(screen.getByTestId('session-expires-at')).toHaveTextContent(new Date(exp * 1000).toISOString())
    expect(screen.getByTestId('token')).toHaveTextContent(token)
  })

  it('uses the highest-priority landing page for users with multiple roles', async () => {
    const user = userEvent.setup()
    mockedLogin.mockResolvedValueOnce({ token: makeToken('7', ['EMPLOYEE', 'ADMIN', 'MANAGER']) })
    render(<Harness />)

    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'multi@travelease.example')
    await user.type(screen.getByLabelText(/Password/), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() =>
      expect(screen.getByTestId('landing-marker')).toHaveTextContent('/admin/users'),
    )
    expect(screen.getByTestId('landing-marker')).toHaveTextContent('/admin/users')
  })

  it('shows a generic invalid-credentials message after a 401', async () => {
    const user = userEvent.setup()
    mockedLogin.mockRejectedValueOnce(unauthorizedError())
    render(<Harness />)

    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'wrong@travelease.example')
    await user.type(screen.getByLabelText(/Password/), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  it('shows a generic error when the login request fails for another reason', async () => {
    const user = userEvent.setup()
    mockedLogin.mockRejectedValueOnce(new Error('network failure'))
    render(<Harness />)

    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'user@travelease.example')
    await user.type(screen.getByLabelText(/Password/), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to sign in. Please try again.')
  })

  it('shows the session-expired warning and consumes its query parameter', () => {
    render(<Harness path="/login?reason=session-expired" />)

    expect(screen.getByText('Your session has expired, please log in again.')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  describe('administrator-issued temporary password (#14)', () => {
    async function signInWithTemporaryPassword() {
      const user = userEvent.setup()
      render(<Harness />)
      await user.type(screen.getByRole('textbox', { name: 'Username' }), 'emp@example.com')
      await user.type(screen.getByLabelText(/Password/), 'Temp-pass-0001')
      await user.click(screen.getByRole('button', { name: 'Sign in' }))
      return user
    }

    it('asks for a new password instead of signing in, then signs in with it', async () => {
      mockedLogin
        .mockRejectedValueOnce(apiError(403, 'PASSWORD_CHANGE_REQUIRED'))
        .mockResolvedValueOnce({ token: makeToken('7', ['EMPLOYEE']) })
      mockedComplete.mockResolvedValueOnce(undefined)
      const user = await signInWithTemporaryPassword()

      expect(await screen.findByRole('heading', { name: 'Set a new password' })).toBeInTheDocument()
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')

      await user.type(screen.getByLabelText(/^New password/), 'Harbour-2026')
      await user.type(screen.getByLabelText(/^Confirm new password/), 'Harbour-2026')
      await user.click(screen.getByRole('button', { name: 'Save password and sign in' }))

      await waitFor(() => expect(screen.getByTestId('landing-marker')).toHaveTextContent('/travel-requests'))
      expect(mockedComplete).toHaveBeenCalledWith({
        username: 'emp@example.com',
        temporaryPassword: 'Temp-pass-0001',
        newPassword: 'Harbour-2026',
      })
      expect(mockedLogin).toHaveBeenLastCalledWith({ username: 'emp@example.com', password: 'Harbour-2026' })
    })

    it('shows the server password-policy message on the new password field', async () => {
      mockedLogin.mockRejectedValueOnce(apiError(403, 'PASSWORD_CHANGE_REQUIRED'))
      mockedComplete.mockRejectedValueOnce(
        apiError(400, 'VALIDATION_ERROR', [
          { field: 'newPassword', message: 'Password must not contain your user name' },
        ]),
      )
      const user = await signInWithTemporaryPassword()

      await user.type(await screen.findByLabelText(/^New password/), 'emp-2026-harbour')
      await user.type(screen.getByLabelText(/^Confirm new password/), 'emp-2026-harbour')
      await user.click(screen.getByRole('button', { name: 'Save password and sign in' }))

      expect(await screen.findByText('Password must not contain your user name')).toBeInTheDocument()
    })

    it('returns to the sign-in form on request', async () => {
      mockedLogin.mockRejectedValueOnce(apiError(403, 'PASSWORD_CHANGE_REQUIRED'))
      const user = await signInWithTemporaryPassword()

      await user.click(await screen.findByRole('button', { name: 'Back to sign in' }))
      expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument()
    })
  })

  it('explains rate limiting after too many attempts', async () => {
    const user = userEvent.setup()
    mockedLogin.mockRejectedValueOnce(apiError(429, 'TOO_MANY_REQUESTS'))
    render(<Harness />)
    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'emp@example.com')
    await user.type(screen.getByLabelText(/Password/), 'whatever-1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many sign-in attempts')
  })

  it('confirms a completed password change', () => {
    render(<Harness path="/login?reason=password-changed" />)
    expect(screen.getByRole('status')).toHaveTextContent('Your password was changed.')
  })

  it('returns to a same-app path after re-authentication, ignoring external URLs (#16)', async () => {
    const user = userEvent.setup()
    mockedLogin.mockResolvedValue({ token: makeToken('7', ['ADMIN']) })

    const first = render(<Harness path="/login?reason=session-expired&returnTo=%2Ftravel-requests" />)
    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'admin@example.com')
    await user.type(screen.getByLabelText(/Password/), 'Admin-pass-1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(screen.getByTestId('landing-marker')).toHaveTextContent('/travel-requests'))
    first.unmount()
    sessionStorage.clear()

    render(<Harness path="/login?returnTo=%2F%2Fevil.example.com" />)
    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'admin@example.com')
    await user.type(screen.getByLabelText(/Password/), 'Admin-pass-1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(screen.getByTestId('landing-marker')).toHaveTextContent('/admin/users'))
  })
})
