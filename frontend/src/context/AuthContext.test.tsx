import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'

function makeToken(): string {
  const header = btoa('{"alg":"HS256","typ":"JWT"}')
  const payload = btoa(
    JSON.stringify({
      sub: 'U1',
      roles: ['EMPLOYEE'],
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `${header}.${payload}.signature`
}

function AuthProbe() {
  const { authState, setAuthState, clearAuthState } = useAuth()
  return (
    <div>
      <span data-testid="is-authenticated">{String(authState.isAuthenticated)}</span>
      <span data-testid="user-id">{authState.userId ?? 'none'}</span>
      <span data-testid="roles">{authState.roles.join(',')}</span>
      <button
        onClick={() =>
          setAuthState({
            userId: 'U1',
            roles: ['EMPLOYEE'],
            isAuthenticated: true,
            sessionExpiresAt: null,
            token: makeToken(),
          })
        }
      >
        login
      </button>
      <button onClick={clearAuthState}>logout</button>
    </div>
  )
}

beforeEach(() => {
  sessionStorage.clear()
})

describe('AuthContext', () => {
  it('provides a default unauthenticated state', () => {
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('user-id')).toHaveTextContent('none')
    expect(screen.getByTestId('roles')).toHaveTextContent('')
  })

  it('setAuthState establishes an authenticated session', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )
    await user.click(screen.getByText('login'))
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('user-id')).toHaveTextContent('U1')
    expect(screen.getByTestId('roles')).toHaveTextContent('EMPLOYEE')
  })

  it('restores an authenticated session after the provider is remounted', async () => {
    const user = userEvent.setup()
    const firstRender = render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )

    await user.click(screen.getByText('login'))
    firstRender.unmount()

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('user-id')).toHaveTextContent('U1')
    expect(screen.getByTestId('roles')).toHaveTextContent('EMPLOYEE')
  })

  it('clearAuthState resets to the default unauthenticated state', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )
    await user.click(screen.getByText('login'))
    await user.click(screen.getByText('logout'))
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('user-id')).toHaveTextContent('none')
    expect(sessionStorage.length).toBe(0)
  })

  it('discards an expired stored token', () => {
    const header = btoa('{"alg":"HS256","typ":"JWT"}')
    const payload = btoa(
      JSON.stringify({ sub: 'U1', roles: ['EMPLOYEE'], exp: Math.floor(Date.now() / 1000) - 60 }),
    )
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
    sessionStorage.setItem('travelease.auth.token', `${header}.${payload}.signature`)

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(sessionStorage.length).toBe(0)
  })
})
