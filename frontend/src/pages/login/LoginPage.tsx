import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { useAuth } from '../../context/AuthContext'
import type { RoleType } from '../../models/admin'
import { RequiredPasswordChangeForm } from '../../components/account/RequiredPasswordChangeForm'
import { authService, PASSWORD_CHANGE_REQUIRED, TOO_MANY_REQUESTS } from '../../services/authService'
import { isClassifiedErrorInstance } from '../../services/httpClient'
import { decodeTokenClaims } from '../../utils/jwt'

const ROLE_LANDING_PATHS: Partial<Record<RoleType, string>> = {
  ADMIN: '/admin/users',
  FINANCE: '/expenses/review-queue',
  MANAGER: '/approvals/travel-requests',
  EMPLOYEE: '/travel-requests',
}

function landingPathFor(roles: RoleType[]): string {
  for (const role of ['ADMIN', 'FINANCE', 'MANAGER', 'EMPLOYEE'] as const) {
    const path = ROLE_LANDING_PATHS[role]
    if (roles.includes(role) && path !== undefined) return path
  }
  return '/'
}

/**
 * Only same-app paths are honoured as a post-login destination (#16 re-authentication), never an
 * absolute or protocol-relative URL, so the parameter cannot redirect off the application.
 */
function returnPathFor(value: string | null): string | null {
  if (value === null || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/login')) {
    return null
  }
  return value
}

/**
 * Login (P-01): authenticates against admin-service with the account's email
 * and password, then decodes the issued JWT claims to establish the same
 * in-memory session consumed by the route guards and auth interceptor.
 */
export function LoginPage() {
  const { setAuthState } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [usernameError, setUsernameError] = useState<string | undefined>()
  const [passwordError, setPasswordError] = useState<string | undefined>()
  const [loginError, setLoginError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [sessionExpired, setSessionExpired] = useState(
    () => searchParams.get('reason') === 'session-expired',
  )
  const [passwordChanged, setPasswordChanged] = useState(
    () => searchParams.get('reason') === 'password-changed',
  )
  /** Set while an administrator-issued temporary password must be replaced (#14). */
  const [pendingChange, setPendingChange] = useState<{ username: string; password: string } | null>(
    null,
  )

  useEffect(() => {
    const reason = searchParams.get('reason')
    if (reason === 'session-expired' || reason === 'password-changed') {
      // drop only the one-time notice; keep returnTo for the post-login redirect
      const next = new URLSearchParams(searchParams)
      next.delete('reason')
      setSearchParams(next, { replace: true })
    }
  }, [searchParams, setSearchParams])

  async function signIn(normalizedUsername: string, secret: string) {
    const { token } = await authService.login({ username: normalizedUsername, password: secret })
    const claims = decodeTokenClaims(token)
    if (claims === null) {
      setLoginError('Unable to sign in. Please try again.')
      return
    }

    setSessionExpired(false)
    setAuthState({
      userId: claims.userId,
      roles: claims.roles,
      isAuthenticated: true,
      sessionExpiresAt: claims.expiresAt,
      token,
    })
    const from = (location.state as { from?: { pathname?: string; search?: string } } | null)?.from
    const returnTo =
      returnPathFor(searchParams.get('returnTo')) ??
      returnPathFor(from?.pathname ? `${from.pathname}${from.search ?? ''}` : null)
    navigate(returnTo ?? landingPathFor(claims.roles), {
      replace: true,
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedUsername = username.trim()
    const nextUsernameError = normalizedUsername.length === 0 ? 'Username is required.' : undefined
    const nextPasswordError = password.length === 0 ? 'Password is required.' : undefined
    setUsernameError(nextUsernameError)
    setPasswordError(nextPasswordError)
    setLoginError(null)

    if (nextUsernameError !== undefined || nextPasswordError !== undefined) return

    setIsSubmitting(true)
    setPasswordChanged(false)
    try {
      await signIn(normalizedUsername, password)
    } catch (error) {
      if (isClassifiedErrorInstance(error) && error.errorCode === PASSWORD_CHANGE_REQUIRED) {
        setSessionExpired(false)
        setPendingChange({ username: normalizedUsername, password })
        return
      }
      setLoginError(
        isClassifiedErrorInstance(error) && error.status === 401
          ? 'Invalid username or password.'
          : isClassifiedErrorInstance(error) && error.errorCode === TOO_MANY_REQUESTS
            ? 'Too many sign-in attempts. Please wait a few minutes and try again.'
            : 'Unable to sign in. Please try again.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="login" id="login">
      <div className="login-box">
        <div className="login-brand">
          <span className="brand-mark">
            <i className="icon icon-plane" />
          </span>
          TravelEase
        </div>
        <div className="login-card">
          {pendingChange !== null ? (
            <RequiredPasswordChangeForm
              username={pendingChange.username}
              temporaryPassword={pendingChange.password}
              onChanged={async (newPassword) => {
                setPendingChange(null)
                setPassword('')
                try {
                  await signIn(pendingChange.username, newPassword)
                } catch {
                  setPasswordChanged(true)
                }
              }}
              onCancel={() => {
                setPendingChange(null)
                setPassword('')
              }}
            />
          ) : (
            <>
              <div>
                <h1>Login</h1>
                <p className="login-intro">Sign in with your TravelEase account email and password.</p>
              </div>
              {sessionExpired ? (
                <p className="required" role="alert" style={{ fontSize: 13 }}>
                  Your session has expired, please log in again.
                </p>
              ) : null}
              {passwordChanged ? (
                <p role="status" style={{ fontSize: 13 }}>
                  Your password was changed. Sign in with your new password.
                </p>
              ) : null}
              {loginError ? (
                <p className="required" role="alert" style={{ fontSize: 13 }}>
                  {loginError}
                </p>
              ) : null}
              <form className="login-form" noValidate onSubmit={handleSubmit}>
                <div className="field">
                  <label htmlFor="login-username">
                    Username{' '}
                    <span className="required" aria-hidden="true">
                      *
                    </span>
                  </label>
                  <div className="input-with-icon">
                    <i className="icon icon-mail" />
                    <input
                      className="input"
                      id="login-username"
                      type="email"
                      autoComplete="username"
                      required
                      value={username}
                      aria-invalid={usernameError ? true : undefined}
                      aria-describedby={usernameError ? 'login-username-error' : undefined}
                      onChange={(event) => {
                        setUsername(event.target.value)
                        setUsernameError(undefined)
                        setLoginError(null)
                      }}
                    />
                  </div>
                  {usernameError ? (
                    <p
                      id="login-username-error"
                      className="required"
                      role="alert"
                      style={{ fontSize: 13 }}
                    >
                      {usernameError}
                    </p>
                  ) : null}
                </div>
                <div className="field">
                  <label htmlFor="login-password">
                    Password{' '}
                    <span className="required" aria-hidden="true">
                      *
                    </span>
                  </label>
                  <div className="input-with-icon">
                    <i className="icon icon-lock" />
                    <input
                      className="input"
                      id="login-password"
                      type="password"
                      autoComplete="current-password"
                      required
                      value={password}
                      aria-invalid={passwordError ? true : undefined}
                      aria-describedby={passwordError ? 'login-password-error' : undefined}
                      onChange={(event) => {
                        setPassword(event.target.value)
                        setPasswordError(undefined)
                        setLoginError(null)
                      }}
                    />
                  </div>
                  {passwordError ? (
                    <p
                      id="login-password-error"
                      className="required"
                      role="alert"
                      style={{ fontSize: 13 }}
                    >
                      {passwordError}
                    </p>
                  ) : null}
                </div>
                <button
                  className="button button-primary button-block"
                  type="submit"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Signing in…' : 'Sign in'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
