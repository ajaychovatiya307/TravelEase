// Error response interceptor (H-02, frontend-tasks.md — "H. Interceptors").
// The error path of the shared HTTP client's response interceptor pipeline
// (frontend-plan.md §13 "Interceptor Strategy"), alongside H-01's request
// interceptor (authRequestInterceptor.ts):
// - Normalizes every failure to the `ClassifiedError` shape
//   (frontend-api-mapping.md §4 "Error Mappings"; api-contract.md §8) so
//   pages branch on the normalized `category` only — never on raw HTTP
//   status codes or raw error strings (frontend-plan.md §11).
// - Detects `401`/expired-session responses and triggers the centralized
//   redirect-to-Login flow (frontend-plan.md §13, frontend-clarification.md
//   Q30), short-circuiting further page-level error handling: the rejection
//   is the `SessionExpiredError` sentinel (sessionExpiredError.ts), never a
//   `ClassifiedError` — `401` is intentionally not an `ErrorCategory`
//   (models/error.ts, frontend-api-mapping.md §4).
//
// No service or component parses raw error shapes itself — all of that
// logic lives exclusively in these interceptors (frontend-plan.md §13).

import axios from 'axios'
import { useEffect, useRef } from 'react'

import { useAuth } from '../../context/AuthContext'
import type { ClassifiedError, ErrorCategory, FieldError } from '../../models/error'
import type { ResponseErrorInterceptor } from '../httpClient'
import {
  ejectInterceptors,
  isClassifiedErrorInstance,
  registerInterceptors,
  toClassifiedError,
} from '../httpClient'
import { SessionExpiredError, isSessionExpiredError } from './sessionExpiredError'

/**
 * The injected session-expiry side effect (frontend-plan.md §13): invoked
 * for `401`/expired-session responses before the sentinel rejection is
 * thrown. Kept injectable so `createErrorResponseInterceptor` stays
 * unit-testable with the redirect mocked (frontend-plan.md §14).
 */
export type SessionExpiryHandler = () => void

/**
 * Login (P-01, routeTable.ts) URL carrying the session-expiry signal: the
 * Login page (I-01) reads `reason=session-expired` to show the "session
 * expired" message (frontend-clarification.md Q30).
 */
const SESSION_EXPIRED_LOGIN_URL = '/login?reason=session-expired'

/**
 * Session-expired Login URL that brings the user back to the page they were on (#16). Only the
 * same-app path is carried; LoginPage ignores anything that is not a local path.
 */
export function sessionExpiredLoginUrl(location: Pick<Location, 'pathname' | 'search'>): string {
  const current = `${location.pathname}${location.search}`
  if (location.pathname === '/' || location.pathname.startsWith('/login')) {
    return SESSION_EXPIRED_LOGIN_URL
  }
  return `${SESSION_EXPIRED_LOGIN_URL}&returnTo=${encodeURIComponent(current)}`
}

/** The 8 normalized error categories (frontend-api-mapping.md §4). */
const ERROR_CATEGORIES: readonly ErrorCategory[] = [
  'VALIDATION_ERROR',
  'FORBIDDEN_ACTION',
  'RESOURCE_NOT_FOUND',
  'CONFLICT',
  'DUPLICATE_RESOURCE',
  'POLICY_VIOLATION',
  'INTERNAL_ERROR',
  'NETWORK_ERROR',
]

function isCategory(value: unknown): value is ErrorCategory {
  return typeof value === 'string' && (ERROR_CATEGORIES as readonly string[]).includes(value)
}

const GENERIC_ERROR_MESSAGE = 'An unexpected error occurred. Please try again later.'
const NETWORK_ERROR_MESSAGE =
  'Unable to reach the server. Please check your connection and try again.'

function readEnvelopeString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

/** Envelope `fieldErrors` (api-contract.md §8); entries that are not `{field: string, message: string}` are dropped. */
function readFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((entry): FieldError[] =>
    entry !== null &&
    typeof entry === 'object' &&
    typeof (entry as FieldError).field === 'string' &&
    typeof (entry as FieldError).message === 'string'
      ? [entry as FieldError]
      : [],
  )
}

/**
 * HTTP-status fallback classification (frontend-api-mapping.md §4) for
 * responses whose envelope code is missing or not one of the 8 contract
 * error codes: 400 → `VALIDATION_ERROR`, 403 → `FORBIDDEN_ACTION`,
 * 404 → `RESOURCE_NOT_FOUND`, 409 → `CONFLICT`, 422 → `POLICY_VIOLATION`;
 * 5xx and everything else → `INTERNAL_ERROR`. `DUPLICATE_RESOURCE` is only
 * ever reached via the envelope code (api-contract.md §8), never via status.
 */
function classifyHttpStatus(status: number): ErrorCategory {
  switch (status) {
    case 400:
      return 'VALIDATION_ERROR'
    case 403:
      return 'FORBIDDEN_ACTION'
    case 404:
      return 'RESOURCE_NOT_FOUND'
    case 409:
      return 'CONFLICT'
    case 422:
      return 'POLICY_VIOLATION'
    default:
      return 'INTERNAL_ERROR'
  }
}

/**
 * H-02's full error classification (frontend-plan.md §11/§13,
 * frontend-api-mapping.md §4) — the superset of F-01's minimal
 * `defaultErrorTranslator` (httpClient.ts, left untouched as the last-resort
 * translator for failures that bypass the interceptor pipeline):
 * - transport failure without a backend response → `NETWORK_ERROR`
 *   (connectivity/timeout: `status`/`errorCode`/`timestamp` null)
 * - backend response whose error envelope (api-contract.md §8) carries one
 *   of the 8 contract error codes → that category, with `message`, `status`,
 *   `errorCode`, `timestamp`, and `fieldErrors` carried over (invalid
 *   `fieldErrors` entries dropped)
 * - any other response → the HTTP-status fallback above; the envelope
 *   `message` is used when present, else the generic message
 * - non-axios error → `INTERNAL_ERROR` (no status/errorCode/timestamp)
 *
 * Login-endpoint `401` responses are classified for the Login page;
 * protected-request `401` responses short-circuit to session-expiry handling.
 */
function classifyError(error: unknown): ClassifiedError {
  if (axios.isAxiosError(error)) {
    const response = error.response
    if (!response) {
      return {
        category: 'NETWORK_ERROR',
        message: NETWORK_ERROR_MESSAGE,
        status: null,
        errorCode: null,
        timestamp: null,
        fieldErrors: [],
      }
    }
    const data: Record<string, unknown> =
      typeof response.data === 'object' && response.data !== null
        ? (response.data as Record<string, unknown>)
        : {}
    const errorCode = readEnvelopeString(data.error)
    return {
      category: isCategory(errorCode) ? errorCode : classifyHttpStatus(response.status),
      message: readEnvelopeString(data.message) ?? GENERIC_ERROR_MESSAGE,
      status: response.status,
      errorCode,
      timestamp: readEnvelopeString(data.timestamp),
      fieldErrors: readFieldErrors(data.fieldErrors),
    }
  }
  return {
    category: 'INTERNAL_ERROR',
    message: GENERIC_ERROR_MESSAGE,
    status: null,
    errorCode: null,
    timestamp: null,
    fieldErrors: [],
  }
}

/**
 * Builds the error-path response interceptor (frontend-plan.md §13). For
 * every rejected request:
 * 1. `SessionExpiredError` sentinels and already-classified `ClassifiedError`
 *    rejections (from an earlier interceptor pass) are rethrown untouched —
 *    never re-translated.
 * 2. A `401` from the public login endpoint is classified and returned to
 *    LoginPage; all other `401` responses invoke the session-expired handler
 *    exactly once and throw a `SessionExpiredError`.
 * 3. Everything else is normalized to a `ClassifiedError` and thrown via
 *    `toClassifiedError`, so `httpClient`'s `isClassifiedErrorInstance`
 *    pass-through in `request()` applies (frontend-plan.md §13).
 */
export function createErrorResponseInterceptor(
  onSessionExpired: SessionExpiryHandler,
): ResponseErrorInterceptor {
  return (error: unknown) => {
    if (isSessionExpiredError(error) || isClassifiedErrorInstance(error)) {
      throw error
    }
    const response = axios.isAxiosError(error) ? error.response : undefined
    if (response !== undefined && response.status === 401) {
      if (axios.isAxiosError(error) && error.config?.url?.split('?')[0] === '/api/auth/login') {
        throw toClassifiedError(classifyError(error))
      }
      onSessionExpired()
      throw new SessionExpiredError()
    }
    throw toClassifiedError(classifyError(error))
  }
}

/**
 * Registers the error response interceptor once for the app's lifetime,
 * mirroring H-01's `useAuthRequestInterceptor`: the latest `clearAuthState`
 * callback is kept in a ref, so the interceptor never needs re-registering
 * when the session changes (login/logout/session-expiry). Mount once near
 * the root (`AppProviders`, inside `AuthProvider`); the interceptor is
 * ejected on unmount.
 *
 * The default session-expiry handler (frontend-plan.md §13, Q30):
 * 1. Clears the auth state (`clearAuthState`, via the ref) so no dead token
 *    or stale session survives the redirect, then
 * 2. Navigates to Login (P-01) with `?reason=session-expired` via
 *    `window.location.assign` — a full page reload, so the message survives
 *    regardless of which page the `401` originated from; the Login page
 *    (I-01) reads the query param (frontend-clarification.md Q30).
 */
export function useErrorResponseInterceptor(): void {
  const { clearAuthState } = useAuth()
  const clearAuthStateRef = useRef(clearAuthState)
  useEffect(() => {
    clearAuthStateRef.current = clearAuthState
  }, [clearAuthState])

  useEffect(() => {
    const ids = registerInterceptors({
      responseError: createErrorResponseInterceptor(() => {
        clearAuthStateRef.current()
        window.location.assign(sessionExpiredLoginUrl(window.location))
      }),
    })
    return () => ejectInterceptors(ids)
  }, [])
}