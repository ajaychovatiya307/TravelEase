import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { useAuth } from '../../context/AuthContext'
import { authService, TOO_MANY_REQUESTS } from '../../services/authService'
import { isClassifiedErrorInstance } from '../../services/httpClient'
import { sessionExpiredLoginUrl } from '../../services/interceptors/errorResponseInterceptor'
import { decodeTokenClaims } from '../../utils/jwt'
import './SessionExpiryWarning.css'

/** The warning appears this long before the token's `exp` (#16). */
export const SESSION_WARNING_LEAD_MS = 5 * 60 * 1000
const TICK_MS = 1000

function minutesLabel(msLeft: number): string {
  const minutes = Math.max(1, Math.ceil(msLeft / 60_000))
  return minutes === 1 ? '1 minute' : `${minutes} minutes`
}

/**
 * Warns before the session token expires and lets the user sign in again without leaving the page,
 * so whatever they were typing is kept (#16). There are no refresh tokens: re-authentication is a
 * normal login, and the new token is only accepted when it belongs to the same user. Once the token
 * has expired the dialog blocks the page (requests would fail) until the user signs in again or
 * chooses to go to the sign-in page.
 */
export function SessionExpiryWarning() {
  const { authState, setAuthState, clearAuthState } = useAuth()
  const [now, setNow] = useState(() => Date.now())
  const [isReauthOpen, setIsReauthOpen] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const expiresAt = authState.sessionExpiresAt === null ? NaN : Date.parse(authState.sessionExpiresAt)
  const msLeft = expiresAt - now
  const isExpired = Number.isFinite(msLeft) && msLeft <= 0
  const isWarning = Number.isFinite(msLeft) && msLeft > 0 && msLeft <= SESSION_WARNING_LEAD_MS

  useEffect(() => {
    if (!authState.isAuthenticated) return
    const timer = window.setInterval(() => setNow(Date.now()), TICK_MS)
    return () => window.clearInterval(timer)
  }, [authState.isAuthenticated])

  if (!authState.isAuthenticated || (!isWarning && !isExpired)) return null

  async function handleReauthenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (username.trim().length === 0 || password.length === 0) {
      setError('Enter your email and password.')
      return
    }
    setIsSubmitting(true)
    setError(null)
    try {
      const { token } = await authService.login({ username: username.trim(), password })
      const claims = decodeTokenClaims(token)
      if (claims === null || claims.userId !== authState.userId) {
        setError('Sign in with the account that is currently in use.')
        return
      }
      setAuthState({
        userId: claims.userId,
        roles: claims.roles,
        isAuthenticated: true,
        sessionExpiresAt: claims.expiresAt,
        token,
      })
      setNow(Date.now())
      setIsReauthOpen(false)
      setPassword('')
    } catch (caught) {
      setError(
        isClassifiedErrorInstance(caught) && caught.status === 401
          ? 'Invalid username or password.'
          : isClassifiedErrorInstance(caught) && caught.errorCode === TOO_MANY_REQUESTS
            ? 'Too many sign-in attempts. Please wait a few minutes and try again.'
            : 'Unable to sign in. Please try again.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  function goToSignIn() {
    const target = sessionExpiredLoginUrl(window.location)
    clearAuthState()
    window.location.assign(target)
  }

  const showDialog = isExpired || isReauthOpen

  return (
    <>
      {isWarning && !isReauthOpen ? (
        <div className="session-expiry-banner" role="alert">
          <span>
            Your session expires in {minutesLabel(msLeft)}. Sign in again to keep working — this page
            and anything you have typed stay as they are.
          </span>
          <button className="button button-primary" type="button" onClick={() => setIsReauthOpen(true)}>
            Stay signed in
          </button>
        </div>
      ) : null}
      {showDialog ? (
        <div className="session-expiry-overlay">
          <div
            className="card session-expiry-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="session-expiry-title"
          >
            <h2 id="session-expiry-title">{isExpired ? 'Your session has expired' : 'Stay signed in'}</h2>
            <p>
              {isExpired
                ? 'Sign in again to continue. Your unsaved changes on this page are kept.'
                : 'Confirm your password to extend your session.'}
            </p>
            {error ? (
              <p className="required" role="alert" style={{ fontSize: 13 }}>
                {error}
              </p>
            ) : null}
            <form className="session-expiry-dialog__form" noValidate onSubmit={handleReauthenticate}>
              <div className="field">
                <label htmlFor="reauth-username">Email</label>
                <input
                  className="input"
                  id="reauth-username"
                  type="email"
                  autoComplete="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="reauth-password">Password</label>
                <input
                  className="input"
                  id="reauth-password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </div>
              <div className="session-expiry-dialog__actions">
                <button className="button button-primary" type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Signing in…' : 'Sign in'}
                </button>
                {isExpired ? (
                  <button className="button button-secondary" type="button" onClick={goToSignIn}>
                    Go to sign-in page
                  </button>
                ) : (
                  <button
                    className="button button-secondary"
                    type="button"
                    onClick={() => {
                      setIsReauthOpen(false)
                      setError(null)
                    }}
                  >
                    Not now
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </>
  )
}
