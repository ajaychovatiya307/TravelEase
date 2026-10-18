// errorResponseInterceptor unit tests (H-02, frontend-tasks.md — "H.
// Interceptors"). Covers the pure interceptor factory directly (error
// classification per frontend-api-mapping.md §4, session-expiry handling
// and pass-through behavior) and the React wiring hook's registration
// lifecycle. The transport is exercised through synthetic axios errors only
// — no network calls (the httpClient register/eject hook points are mocked,
// same pattern as authRequestInterceptor.test.tsx).

import { render } from '@testing-library/react'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthState } from '../../context/AuthContext'
import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { HttpInterceptors, ResponseErrorInterceptor } from '../httpClient'
import { isClassifiedErrorInstance, toClassifiedError } from '../httpClient'
import {
  createErrorResponseInterceptor,
  sessionExpiredLoginUrl,
  useErrorResponseInterceptor,
} from './errorResponseInterceptor'
import { SessionExpiredError, isSessionExpiredError } from './sessionExpiredError'

// The pure factory needs the real `toClassifiedError` /
// `isClassifiedErrorInstance`; only the register/eject hook points are
// mocked (for the hook lifecycle tests) — everything else from httpClient
// stays real (same `vi.importActual` + override pattern as
// httpClient.test.tsx's axios mock).
const httpClient = vi.hoisted(() => ({
  registerInterceptors: vi.fn((hooks: HttpInterceptors) => {
    void hooks
    return { response: 7 }
  }),
  ejectInterceptors: vi.fn(),
}))

vi.mock('../httpClient', async () => {
  const actual = await vi.importActual<typeof import('../httpClient')>('../httpClient')
  return {
    ...actual,
    registerInterceptors: httpClient.registerInterceptors,
    ejectInterceptors: httpClient.ejectInterceptors,
  }
})

/** Builds a realistic transport error carrying a backend response. */
function makeAxiosError(status: number, data: unknown, url?: string): AxiosError {
  return new AxiosError(
    `Request failed with status code ${status}`,
    'ERR_BAD_RESPONSE',
    url === undefined ? undefined : ({ url } as InternalAxiosRequestConfig),
    undefined,
    { data, status, statusText: 'ERR', headers: {}, config: {} } as unknown as AxiosResponse,
  )
}

/** Runs the interceptor and returns the error it threw (the error path always throws). */
function invoke(interceptor: ResponseErrorInterceptor, error: unknown): unknown {
  try {
    interceptor(error)
  } catch (thrown) {
    return thrown
  }
  throw new Error('expected the interceptor to throw')
}

describe('createErrorResponseInterceptor — classification (H-02, frontend-api-mapping.md §4)', () => {
  it('maps a 400 VALIDATION_ERROR envelope to VALIDATION_ERROR with envelope fields carried over', () => {
    const onSessionExpired = vi.fn()
    const interceptor = createErrorResponseInterceptor(onSessionExpired)
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 400,
      error: 'VALIDATION_ERROR',
      message: 'justification is required',
      fieldErrors: [{ field: 'justification', message: 'required; minimum length 10' }],
    }

    const thrown = invoke(interceptor, makeAxiosError(400, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'VALIDATION_ERROR',
      message: 'justification is required',
      status: 400,
      errorCode: 'VALIDATION_ERROR',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [{ field: 'justification', message: 'required; minimum length 10' }],
    })
    expect(isClassifiedErrorInstance(thrown)).toBe(true)
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('maps a 403 FORBIDDEN_ACTION envelope to FORBIDDEN_ACTION', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 403,
      error: 'FORBIDDEN_ACTION',
      message: 'You are not allowed to approve your own travel request',
    }

    const thrown = invoke(interceptor, makeAxiosError(403, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'FORBIDDEN_ACTION',
      message: 'You are not allowed to approve your own travel request',
      status: 403,
      errorCode: 'FORBIDDEN_ACTION',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
  })

  it('maps a 404 RESOURCE_NOT_FOUND envelope to RESOURCE_NOT_FOUND', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 404,
      error: 'RESOURCE_NOT_FOUND',
      message: 'Travel request tr-99 does not exist',
    }

    const thrown = invoke(interceptor, makeAxiosError(404, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'RESOURCE_NOT_FOUND',
      message: 'Travel request tr-99 does not exist',
      status: 404,
      errorCode: 'RESOURCE_NOT_FOUND',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
  })

  it('maps a 409 CONFLICT envelope to CONFLICT', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 409,
      error: 'CONFLICT',
      message: 'Action invalid for the current entity state',
    }

    const thrown = invoke(interceptor, makeAxiosError(409, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'CONFLICT',
      message: 'Action invalid for the current entity state',
      status: 409,
      errorCode: 'CONFLICT',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
  })

  it('maps a 409 DUPLICATE_RESOURCE envelope to DUPLICATE_RESOURCE', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 409,
      error: 'DUPLICATE_RESOURCE',
      message: 'An identical submission already exists',
    }

    const thrown = invoke(interceptor, makeAxiosError(409, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'DUPLICATE_RESOURCE',
      message: 'An identical submission already exists',
      status: 409,
      errorCode: 'DUPLICATE_RESOURCE',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
  })

  it('maps a 422 POLICY_VIOLATION envelope to POLICY_VIOLATION with field errors', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 422,
      error: 'POLICY_VIOLATION',
      message: 'Receipt required for expenses above threshold',
      fieldErrors: [{ field: 'documentIds', message: 'required when amount exceeds category threshold' }],
    }

    const thrown = invoke(interceptor, makeAxiosError(422, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'POLICY_VIOLATION',
      message: 'Receipt required for expenses above threshold',
      status: 422,
      errorCode: 'POLICY_VIOLATION',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [{ field: 'documentIds', message: 'required when amount exceeds category threshold' }],
    })
  })

  it('maps a 500 INTERNAL_ERROR envelope to INTERNAL_ERROR', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 500,
      error: 'INTERNAL_ERROR',
      message: 'Something went wrong on our side',
    }

    const thrown = invoke(interceptor, makeAxiosError(500, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'INTERNAL_ERROR',
      message: 'Something went wrong on our side',
      status: 500,
      errorCode: 'INTERNAL_ERROR',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
  })

  it('maps a transport failure without a backend response to NETWORK_ERROR', () => {
    const interceptor = createErrorResponseInterceptor(() => {})

    const thrown = invoke(interceptor, new AxiosError('Network Error', 'ERR_NETWORK'))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'NETWORK_ERROR',
      message: 'Unable to reach the server. Please check your connection and try again.',
      status: null,
      errorCode: null,
      timestamp: null,
      fieldErrors: [],
    })
  })

  it('falls back to VALIDATION_ERROR for a 400 whose envelope code is not one of the 8', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 400,
      error: 'SOMETHING_ELSE',
      message: 'Invalid request',
    }

    const thrown = invoke(interceptor, makeAxiosError(400, envelope))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'VALIDATION_ERROR',
      message: 'Invalid request',
      status: 400,
      errorCode: 'SOMETHING_ELSE',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
  })

  it('maps an envelope-less 500 response to INTERNAL_ERROR without leaking the detail', () => {
    const interceptor = createErrorResponseInterceptor(() => {})

    const thrown = invoke(interceptor, makeAxiosError(500, { detail: 'boom' }))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'INTERNAL_ERROR',
      message: 'An unexpected error occurred. Please try again later.',
      status: 500,
      errorCode: null,
      timestamp: null,
      fieldErrors: [],
    })
    // no internal detail leaked into the user-facing message
    expect((thrown as { message: string }).message).not.toContain('boom')
  })

  it('maps a non-axios error to INTERNAL_ERROR with null envelope fields', () => {
    const interceptor = createErrorResponseInterceptor(() => {})

    const thrown = invoke(interceptor, new Error('boom'))

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'INTERNAL_ERROR',
      message: 'An unexpected error occurred. Please try again later.',
      status: null,
      errorCode: null,
      timestamp: null,
      fieldErrors: [],
    })
  })

  it('drops invalid fieldErrors entries, keeping only {field, message} pairs', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 400,
      error: 'VALIDATION_ERROR',
      message: 'Multiple invalid fields',
      fieldErrors: [
        { field: 'amount', message: 'must be greater than 0' },
        { field: 'currency' }, // no message
        { field: 42, message: 'field must be a string' },
        'not-an-object',
        null,
      ],
    }

    const thrown = invoke(interceptor, makeAxiosError(400, envelope))

    expect(thrown).toMatchObject({
      category: 'VALIDATION_ERROR',
      fieldErrors: [{ field: 'amount', message: 'must be greater than 0' }],
    })
  })

  it('rethrows an already-classified ClassifiedError untouched', () => {
    const onSessionExpired = vi.fn()
    const interceptor = createErrorResponseInterceptor(onSessionExpired)
    const classified = toClassifiedError({
      category: 'CONFLICT',
      message: 'Action invalid for the current entity state',
      status: 409,
      errorCode: 'CONFLICT',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })

    const thrown = invoke(interceptor, classified)

    expect(thrown).toBe(classified)
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('rethrows a SessionExpiredError sentinel untouched', () => {
    const interceptor = createErrorResponseInterceptor(() => {})
    const sentinel = new SessionExpiredError()

    const thrown = invoke(interceptor, sentinel)

    expect(thrown).toBe(sentinel)
  })
})

describe('createErrorResponseInterceptor — session expiry (H-02)', () => {
  it('runs the handler exactly once and rejects a 401 with the SessionExpiredError sentinel', () => {
    const onSessionExpired = vi.fn()
    const interceptor = createErrorResponseInterceptor(onSessionExpired)
    const error = makeAxiosError(401, { error: 'UNAUTHORIZED', message: 'Session expired' })

    const thrown = invoke(interceptor, error)

    expect(onSessionExpired).toHaveBeenCalledTimes(1)
    expect(thrown).toBeInstanceOf(SessionExpiredError)
    expect(isSessionExpiredError(thrown)).toBe(true)
    // `401` never produces a ClassifiedError — it short-circuits to expiry
    expect(isClassifiedErrorInstance(thrown)).toBe(false)
  })

  it('invokes the handler once per invocation, never twice for the same 401', () => {
    const onSessionExpired = vi.fn()
    const interceptor = createErrorResponseInterceptor(onSessionExpired)

    invoke(interceptor, makeAxiosError(401, { error: 'UNAUTHORIZED' }))
    invoke(interceptor, makeAxiosError(401, { error: 'UNAUTHORIZED' }))

    expect(onSessionExpired).toHaveBeenCalledTimes(2)
  })

  it('preserves 401 login failures for the login form instead of treating them as expired sessions', () => {
    const onSessionExpired = vi.fn()
    const interceptor = createErrorResponseInterceptor(onSessionExpired)
    const thrown = invoke(
      interceptor,
      makeAxiosError(401, { error: 'UNAUTHORIZED', message: 'Invalid credentials' }, '/api/auth/login'),
    )

    expect(thrown).toMatchObject({
      name: 'ClassifiedError',
      category: 'INTERNAL_ERROR',
      status: 401,
    })
    expect(onSessionExpired).not.toHaveBeenCalled()
  })
})

describe('useErrorResponseInterceptor (H-02)', () => {
  // jsdom throws on real navigation — stub `window.location.assign`. This
  // jsdom's `location` members are non-configurable, so `vi.spyOn` cannot
  // wrap them; replace the whole `location` property with a spread copy
  // plus a spy `assign`, restoring the original afterwards.
  const originalLocation = window.location
  let assignSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    assignSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, assign: assignSpy },
      configurable: true,
      writable: true,
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      configurable: true,
      writable: true,
    })
  })

  function Probe() {
    const { authState, setAuthState } = useAuth()
    useErrorResponseInterceptor()
    const state: AuthState = {
      userId: 'emp-1',
      roles: [],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    }
    return (
      <>
        <span data-testid="auth-state">{String(authState.isAuthenticated)}</span>
        <button onClick={() => setAuthState(state)} data-testid="set-token">
          set
        </button>
      </>
    )
  }

  it('registers exactly one responseError interceptor on mount', () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(httpClient.registerInterceptors).toHaveBeenCalledTimes(1)
    const hooks = httpClient.registerInterceptors.mock.calls[0][0]
    expect(typeof hooks.responseError).toBe('function')
    expect(hooks.request).toBeUndefined()
    expect(hooks.response).toBeUndefined()
  })

  it('ejects the interceptor on unmount', () => {
    const { unmount } = render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    unmount()

    expect(httpClient.ejectInterceptors).toHaveBeenCalledWith({ response: 7 })
  })

  it('clears the auth state and redirects to Login with the session-expired reason on a 401', () => {
    let registeredResponseError: ResponseErrorInterceptor | undefined
    httpClient.registerInterceptors.mockImplementationOnce((hooks) => {
      registeredResponseError = hooks.responseError
      return { response: 7 }
    })
    const { getByTestId } = render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    act(() => {
      getByTestId('set-token').click()
    })
    expect(getByTestId('auth-state').textContent).toBe('true')

    let thrown: unknown
    act(() => {
      const error = makeAxiosError(401, { error: 'UNAUTHORIZED', message: 'Session expired' })
      try {
        registeredResponseError!(error)
      } catch (e) {
        thrown = e
      }
    })

    expect(thrown).toBeInstanceOf(SessionExpiredError)
    // the default handler ran: clearAuthState reset the session...
    expect(getByTestId('auth-state').textContent).toBe('false')
    // ...and the full-page redirect to Login carries the expiry reason
    expect(assignSpy).toHaveBeenCalledTimes(1)
    expect(assignSpy).toHaveBeenCalledWith('/login?reason=session-expired')
  })

  it('builds a login URL that returns to the current in-app page (#16)', () => {
    expect(sessionExpiredLoginUrl({ pathname: '/expenses/5/edit', search: '?tab=docs' })).toBe(
      '/login?reason=session-expired&returnTo=%2Fexpenses%2F5%2Fedit%3Ftab%3Ddocs',
    )
    expect(sessionExpiredLoginUrl({ pathname: '/', search: '' })).toBe('/login?reason=session-expired')
    expect(sessionExpiredLoginUrl({ pathname: '/login', search: '?x=1' })).toBe(
      '/login?reason=session-expired',
    )
  })
})
