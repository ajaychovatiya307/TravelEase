// Normalized API error types (E-06).
// `ClassifiedError` is the single normalized error shape every service
// function throws and every page/component consumes (frontend-api-mapping.md
// §4 "Error Mappings"; frontend-plan.md §11 "Error Handling Strategy"):
// pages branch on the normalized `category` only — never on raw HTTP status
// codes or raw error strings. The envelope fields mirror the standard
// structured error shape in api-contract.md §8 ("Error Responses").
//
// The classification itself (mapping an API error response or a network
// failure to one of the 8 categories) is implemented in the shared HTTP
// client's response interceptor (frontend-plan.md §13) — this file holds the
// types only.
//
// `NETWORK_ERROR` (connectivity/timeout) has no backend envelope: `status`,
// `errorCode`, and `timestamp` are `null` and `fieldErrors` is empty.
// `401`/expired-session responses are intercepted before classification
// reaches the page (frontend-plan.md §13, Q30) and are not part of
// `ErrorCategory`.

/**
 * frontend-api-mapping.md §4 / frontend-plan.md §11 / frontend-constitution.md
 * §9: the 8 normalized error categories every API error (or network failure)
 * maps to.
 */
export type ErrorCategory =
  | 'VALIDATION_ERROR'
  | 'FORBIDDEN_ACTION'
  | 'RESOURCE_NOT_FOUND'
  | 'CONFLICT'
  | 'DUPLICATE_RESOURCE'
  | 'POLICY_VIOLATION'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'

/**
 * api-contract.md §8: one entry of the error envelope's `fieldErrors` array.
 * Populated for `400 VALIDATION_ERROR` and, when the violation is attributable
 * to a specific field (e.g. `documentIds`), for `422 POLICY_VIOLATION`;
 * omitted (`null`/empty) otherwise.
 */
export interface FieldError {
  /** Name of the request field the error is attributable to. */
  field: string
  /** Human-readable explanation of the failure for that field. */
  message: string
}

/**
 * frontend-api-mapping.md §4 / frontend-plan.md §11: the normalized error
 * thrown by every service function. Envelope fields mirror api-contract.md
 * §8: `timestamp` is ISO 8601 (`string`), `status` is the HTTP status
 * (`number`), `error` is the backend error code. For `NETWORK_ERROR`
 * (no backend response) `status`/`errorCode`/`timestamp` are `null`.
 */
export interface ClassifiedError {
  /** Normalized category; each maps to one standard UI treatment (§4 table). */
  category: ErrorCategory
  /** User-facing message (envelope `message`; generic message for network failures). */
  message: string
  /** HTTP status code of the backend response; `null` for `NETWORK_ERROR`. */
  status: number | null
  /** Backend error code (envelope `error`, e.g. `POLICY_VIOLATION`); `null` for `NETWORK_ERROR`. */
  errorCode: string | null
  /** Envelope `timestamp` (ISO 8601); `null` for `NETWORK_ERROR`. */
  timestamp: string | null
  /** Envelope `fieldErrors`; empty when no field-level errors apply. */
  fieldErrors: FieldError[]
}
