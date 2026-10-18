// Shared HTTP client (F-01, frontend-tasks.md — "F. Services").
// The single centralized transport every service module (F-02+,
// frontend-plan.md §6 "Service Layer Design") reuses: one axios instance
// configured once with the API base URL, the request/response pipeline hook
// points for the Section H interceptors (frontend-plan.md §13 "Interceptor
// Strategy"), and standardized error normalization to `ClassifiedError`
// (models/error.ts — E-06; frontend-plan.md §11, frontend-api-mapping.md §4).
//
// Responsibility split:
// - This module: base URL (env var `VITE_API_BASE_URL`, relative by default so
//   the Vite development proxy forwards `/api` to the gateway),
//   method/URL/body construction (JSON bodies are
//   serialized here; `FormData` is passed through untouched so the transport
//   sets the `multipart/form-data` content type with its boundary —
//   frontend-plan.md §13, api-contract.md §1/§5.5), and a minimal built-in
//   error translator used as the last-resort normalization when no interceptor
//   has classified the failure yet.
// - Section H (H-01/H-02) plugs in via the hook points below — bearer-token
//   injection from `AuthContext`, full error classification (the `errors/`
//   module), and the centralized session-expiry redirect
//   (frontend-clarification.md Q30) — without modifying this file
//   (frontend-plan.md §13: that logic "lives exclusively in these
//   interceptors").

import axios from 'axios'
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ClassifiedError, ErrorCategory, FieldError } from '../models/error'
import { isSessionExpiredError } from './interceptors/sessionExpiredError'

/** Use same-origin requests by default; Vite proxies `/api` to the local gateway. */
export const DEFAULT_BASE_URL = ''

const envBaseUrl = import.meta.env.VITE_API_BASE_URL

/**
 * The single centrally configured API origin (frontend-plan.md §6). Service
 * modules build endpoint paths relative to it (e.g. `/api/travel-requests`,
 * api-contract.md §1); none of them configures a base URL of their own.
 * Overridable via the `VITE_API_BASE_URL` env var.
 */
export const apiBaseUrl: string =
  typeof envBaseUrl === 'string' && envBaseUrl.length > 0 ? envBaseUrl : DEFAULT_BASE_URL

/** Query parameter values accepted by list endpoints (api-contract.md §7). */
export type QueryParams = Record<string, string | number | boolean | string[] | null | undefined>

/**
 * A JSON-serializable object, or `FormData` for multipart document uploads
 * (api-contract.md §5.5).
 */
export type RequestBody = object | FormData

/** A `ClassifiedError` shape carried on a real `Error` instance (see `toClassifiedError`). */
export type ClassifiedErrorInstance = Error & ClassifiedError

/** Maps any transport failure to the normalized `ClassifiedError` shape. */
export type ErrorTranslator = (error: unknown) => ClassifiedError

/** Hook point: request interceptor (frontend-plan.md §13) — e.g. bearer-token attachment. */
export type RequestInterceptor = (
  config: InternalAxiosRequestConfig,
) => InternalAxiosRequestConfig | Promise<InternalAxiosRequestConfig>

/** Hook point: response interceptor, success path (frontend-plan.md §13) — e.g. envelope unwrapping. */
export type ResponseInterceptor = (
  response: AxiosResponse,
) => AxiosResponse | Promise<AxiosResponse>

/**
 * Hook point: response interceptor, error path (frontend-plan.md §13) — e.g.
 * session-expiry detection/redirect and re-classification of the failure.
 */
export type ResponseErrorInterceptor = (error: unknown) => AxiosResponse | Promise<AxiosResponse>

/** The Section H hook points, registered once for every request (frontend-plan.md §13). */
export interface HttpInterceptors {
  request?: RequestInterceptor
  response?: ResponseInterceptor
  responseError?: ResponseErrorInterceptor
}

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
 * F-01's minimal, last-resort error translator (frontend-plan.md §13 leaves
 * the full classification to Section H — H plugs in via `setErrorTranslator`
 * or a `responseError` interceptor):
 * - transport failure without a backend response -> `NETWORK_ERROR`
 *   (`status`/`errorCode`/`timestamp` null, models/error.ts)
 * - backend response whose error envelope (api-contract.md §8) carries one of
 *   the 8 contract error codes -> that category, envelope fields carried over
 * - anything else -> `INTERNAL_ERROR` (HTTP status preserved when present)
 */
export function defaultErrorTranslator(error: unknown): ClassifiedError {
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
      category: isCategory(errorCode) ? errorCode : 'INTERNAL_ERROR',
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
 * Wraps a normalized error shape in a real `Error` instance (keeps the stack
 * trace and `catch` ergonomics) while carrying every `ClassifiedError` field,
 * so callers can branch on `category` only (frontend-plan.md §11).
 */
export function toClassifiedError(error: ClassifiedError): ClassifiedErrorInstance {
  const instance = new Error(error.message)
  instance.name = 'ClassifiedError'
  return Object.assign(instance, error)
}

/**
 * Type guard for errors already normalized to a `ClassifiedError` shape
 * (Section H interceptors, e.g. H-02's `responseError` pipeline). Exported
 * so the interceptors can pass classified rejections through untouched.
 */
export function isClassifiedErrorInstance(error: unknown): error is ClassifiedErrorInstance {
  return (
    error instanceof Error &&
    'category' in error &&
    isCategory((error as unknown as ClassifiedError).category)
  )
}

const client = axios.create({ baseURL: apiBaseUrl })

let errorTranslator: ErrorTranslator = defaultErrorTranslator

/**
 * Replaces the built-in minimal translator — the Section H / `errors/` hook
 * point for full error classification (frontend-plan.md §11/§13). Errors
 * already classified by a registered `responseError` interceptor always take
 * precedence and are never re-translated.
 */
export function setErrorTranslator(translator: ErrorTranslator): void {
  errorTranslator = translator
}

/**
 * The Section H pipeline hook points (frontend-plan.md §13): registers
 * request/response interceptors on the shared instance once, so bearer-token
 * injection, envelope unwrapping, error classification, and session-expiry
 * handling apply to every request without any service or component touching
 * the transport. Returns the axios interceptor IDs (for de-registration).
 */
export function registerInterceptors(hooks: HttpInterceptors): {
  request?: number
  response?: number
} {
  const ids: { request?: number; response?: number } = {}
  if (hooks.request !== undefined) {
    ids.request = client.interceptors.request.use(hooks.request)
  }
  if (hooks.response !== undefined || hooks.responseError !== undefined) {
    ids.response = client.interceptors.response.use(hooks.response, hooks.responseError)
  }
  return ids
}

/**
 * De-registers interceptors previously returned by `registerInterceptors`
 * (e.g. on unmount of the component that registered them).
 */
export function ejectInterceptors(ids: { request?: number; response?: number }): void {
  if (ids.request !== undefined) client.interceptors.request.eject(ids.request)
  if (ids.response !== undefined) client.interceptors.response.eject(ids.response)
}

type BodyConfig = Pick<AxiosRequestConfig, 'data' | 'headers'>

function buildBodyConfig(body: RequestBody | undefined): BodyConfig {
  if (body === undefined) return { data: undefined }
  if (body instanceof FormData) {
    // multipart upload (api-contract.md §5.5): pass the `FormData` through
    // untouched and do NOT set a JSON content type — the transport sets
    // `multipart/form-data` with the correct boundary (frontend-plan.md §13).
    return { data: body }
  }
  // JSON body (api-contract.md §1): serialize here and attach the standard
  // `Content-Type: application/json` header (frontend-plan.md §13).
  return { data: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } }
}

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await client.request<T>(config)
    return response.data
  } catch (error) {
    // Interceptor-classified errors (Section H) and the session-expiry
    // sentinel (H-02) pass through untouched. A login 401 is classified so
    // LoginPage can display invalid credentials; protected-request 401s use
    // the session-expiry sentinel.
    if (isClassifiedErrorInstance(error) || isSessionExpiredError(error)) throw error
    throw toClassifiedError(errorTranslator(error))
  }
}

/**
 * `GET {apiBaseUrl}{path}` with optional query params (api-contract.md §7);
 * resolves with the typed response data (`response.data`) or rejects with a
 * `ClassifiedError` (models/error.ts — E-06).
 */
export function get<T>(path: string, params?: QueryParams): Promise<T> {
  return request<T>({ method: 'GET', url: path, params })
}

/**
 * `GET {apiBaseUrl}{path}` for a binary download (document attachments). Goes through the same
 * client, so the bearer token and the error/session-expiry interceptors still apply.
 */
export function getBlob(path: string): Promise<Blob> {
  return request<Blob>({ method: 'GET', url: path, responseType: 'blob' })
}

/**
 * `POST {apiBaseUrl}{path}` with a JSON body, or a `FormData` body for
 * multipart document uploads (frontend-api-mapping.md §1: `uploadDocument`);
 * resolves with the typed response data or rejects with a `ClassifiedError`.
 */
export function post<T>(path: string, body?: RequestBody): Promise<T> {
  return request<T>({ method: 'POST', url: path, ...buildBodyConfig(body) })
}

/**
 * `PUT {apiBaseUrl}{path}` with a JSON update payload; resolves with the
 * typed response data or rejects with a `ClassifiedError`.
 */
export function put<T>(path: string, body?: RequestBody): Promise<T> {
  return request<T>({ method: 'PUT', url: path, ...buildBodyConfig(body) })
}
