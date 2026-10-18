// authRequestInterceptor unit tests (H-01, frontend-tasks.md — "H.
// Interceptors"). Covers the pure interceptor factory directly (header
// presence/absence) and the React wiring hook's registration lifecycle.

import { render } from '@testing-library/react'
import { act } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { AuthState } from '../../context/AuthContext'
import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { RequestInterceptor } from '../httpClient'
import { createAuthRequestInterceptor, useAuthRequestInterceptor } from './authRequestInterceptor'

function fakeConfig() {
  return { headers: { set: vi.fn() } } as unknown as Parameters<
    ReturnType<typeof createAuthRequestInterceptor>
  >[0]
}

describe('createAuthRequestInterceptor (H-01)', () => {
  it('sets the Authorization header when a token is present', () => {
    const interceptor = createAuthRequestInterceptor(() => 'jwt-token')
    const config = fakeConfig()

    const result = interceptor(config)

    expect(config.headers.set).toHaveBeenCalledWith('Authorization', 'Bearer jwt-token')
    expect(result).toBe(config)
  })

  it('does not set the Authorization header when there is no token', () => {
    const interceptor = createAuthRequestInterceptor(() => null)
    const config = fakeConfig()

    interceptor(config)

    expect(config.headers.set).not.toHaveBeenCalled()
  })

  it('never touches Content-Type, leaving multipart Content-Type negotiation to the transport', () => {
    const interceptor = createAuthRequestInterceptor(() => 'jwt-token')
    const config = fakeConfig()

    interceptor(config)

    expect(config.headers.set).toHaveBeenCalledTimes(1)
    expect(config.headers.set).not.toHaveBeenCalledWith('Content-Type', expect.anything())
  })
})

const httpClient = vi.hoisted(() => ({
  registerInterceptors: vi.fn((hooks: { request: RequestInterceptor }) => {
    void hooks
    return { request: 1 }
  }),
  ejectInterceptors: vi.fn(),
}))

vi.mock('../httpClient', () => httpClient)

describe('useAuthRequestInterceptor (H-01)', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  function Probe() {
    const { setAuthState } = useAuth()
    useAuthRequestInterceptor()
    const state: AuthState = {
      userId: 'emp-1',
      roles: [],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    }
    return (
      <button onClick={() => setAuthState(state)} data-testid="set-token">
        set
      </button>
    )
  }

  it('registers exactly one request interceptor on mount', () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(httpClient.registerInterceptors).toHaveBeenCalledTimes(1)
    const requestInterceptor = httpClient.registerInterceptors.mock.calls[0]?.[0]?.request
    expect(typeof requestInterceptor).toBe('function')
  })

  it('ejects the interceptor on unmount', () => {
    const { unmount } = render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    unmount()

    expect(httpClient.ejectInterceptors).toHaveBeenCalledWith({ request: 1 })
  })

  it('reads the latest token without re-registering the interceptor', () => {
    let capturedInterceptor: ReturnType<typeof createAuthRequestInterceptor> | undefined
    httpClient.registerInterceptors.mockImplementationOnce((hooks) => {
      capturedInterceptor = hooks.request
      return { request: 1 }
    })

    const { getByTestId } = render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    const beforeConfig = fakeConfig()
    capturedInterceptor!(beforeConfig)
    expect(beforeConfig.headers.set).not.toHaveBeenCalled()

    act(() => {
      getByTestId('set-token').click()
    })

    const afterConfig = fakeConfig()
    capturedInterceptor!(afterConfig)
    expect(afterConfig.headers.set).toHaveBeenCalledWith('Authorization', 'Bearer jwt-token')

    // Same registered interceptor instance throughout — no re-registration.
    expect(httpClient.registerInterceptors).toHaveBeenCalledTimes(1)
  })
})
