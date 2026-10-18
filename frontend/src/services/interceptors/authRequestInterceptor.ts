// Auth request interceptor (H-01, frontend-tasks.md — "H. Interceptors").
// Attaches `Authorization: Bearer <token>` to every outgoing request from
// `AuthContext` (api-contract.md §2; frontend-plan.md §13), without ever
// touching `Content-Type` — the shared HTTP client (httpClient.ts, F-01)
// already sets `application/json` for JSON bodies and omits it for
// `FormData` uploads so the browser can set the multipart boundary;
// attaching only the `Authorization` header here leaves that untouched.

import { useEffect, useRef } from 'react'

import { useAuth } from '../../context/AuthContext'
import type { RequestInterceptor } from '../httpClient'
import { ejectInterceptors, registerInterceptors } from '../httpClient'

/** Returns the current bearer token, or `null` when unauthenticated. */
export type TokenProvider = () => string | null

/**
 * Builds the request interceptor: when `getToken()` returns a token, sets
 * the `Authorization` header; when `null` (no session), leaves the request
 * unauthenticated — route guards (D-02), not this interceptor, are
 * responsible for keeping unauthenticated requests from being made at all.
 */
export function createAuthRequestInterceptor(getToken: TokenProvider): RequestInterceptor {
  return (config) => {
    const token = getToken()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  }
}

/**
 * Registers the auth request interceptor once for the app's lifetime,
 * reading the latest `AuthState.token` on every request via a ref — so the
 * interceptor itself never needs re-registering when the session changes
 * (login/logout/session-expiry). Mount once near the root (`AppProviders`,
 * inside `AuthProvider`); the interceptor is ejected on unmount.
 */
export function useAuthRequestInterceptor(): void {
  const { authState } = useAuth()
  const tokenRef = useRef(authState.token)
  useEffect(() => {
    tokenRef.current = authState.token
  }, [authState.token])

  useEffect(() => {
    const ids = registerInterceptors({
      request: createAuthRequestInterceptor(() => tokenRef.current),
    })
    return () => ejectInterceptors(ids)
  }, [])
}
