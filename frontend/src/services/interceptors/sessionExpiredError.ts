// Session-expired sentinel error (H-02, frontend-tasks.md — "H. Interceptors").
// Rejected by the shared HTTP client in place of a `ClassifiedError` when a
// `401`/expired-session response is handled by the centralized session-expiry
// flow (frontend-plan.md §13, frontend-clarification.md Q30) — callers of a
// service function never receive a normal classified error for `401`, which
// is intentionally not an `ErrorCategory` (models/error.ts,
// frontend-api-mapping.md §4: session expiry is handled before
// classification reaches the page).
//
// This module is deliberately a leaf: it imports nothing from `httpClient.ts`
// or `errorResponseInterceptor.ts`, even though `httpClient.ts` value-imports
// `isSessionExpiredError` from here and `errorResponseInterceptor.ts` imports
// both — keeping this file dependency-free avoids a runtime circular import.

/**
 * Sentinel thrown (rejected) by the error response interceptor after the
 * session-expiry handler has run (H-02). Carries no `ClassifiedError`
 * fields: the page the request originated from is being replaced by the
 * redirect to Login, so there is no error state left for it to render.
 */
export class SessionExpiredError extends Error {
  constructor() {
    super('The session has expired. Please sign in again.')
    this.name = 'SessionExpiredError'
  }
}

/** Type guard for `SessionExpiredError` (H-02). */
export function isSessionExpiredError(error: unknown): error is SessionExpiredError {
  return error instanceof SessionExpiredError
}