// httpClient unit tests (F-01, frontend-tasks.md — "F. Services").
// The underlying transport (the shared axios instance) is fully mocked — no
// network calls — and the tests assert the method/URL/body construction for
// each HTTP verb, multipart handling, and the `ClassifiedError` normalization
// (frontend-plan.md §6/§13; §14 test matrix "Services"/"Interceptors" rows;
// api-contract.md §1/§8).

import { AxiosError } from 'axios'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ClassifiedError } from '../models/error'
import type {
  RequestInterceptor,
  ResponseErrorInterceptor,
  ResponseInterceptor,
} from './httpClient'
import {
  apiBaseUrl,
  defaultErrorTranslator,
  ejectInterceptors,
  get,
  post,
  put,
  registerInterceptors,
  setErrorTranslator,
  toClassifiedError,
} from './httpClient'

/**
 * Shared stand-ins for the mocked axios instance: `create` records the shared
 * instance construction (base URL assertion); `request` is the transport call
 * the client under test makes; `requestUse`/`responseUse` stand in for the
 * interceptor registration hook points (frontend-plan.md §13).
 * `vi.hoisted` keeps the references reachable from the hoisted `vi.mock`
 * factory (same pattern as App.test.tsx).
 */
const transport = vi.hoisted(() => ({
  create: vi.fn(),
  // plain array (not a vi.fn): survives any mock clearing between module
  // load and the first test, unlike vi.fn() call history
  createdConfigs: [] as unknown[],
  request: vi.fn(),
  requestUse: vi.fn(),
  responseUse: vi.fn(),
  requestEject: vi.fn(),
  responseEject: vi.fn(),
}))

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios')
  return {
    ...actual,
    default: {
      ...actual.default,
      // Record the instance construction on the hoisted `transport.create`
      // spy (asserting on the mock's own inline `create` fn is unreliable:
      // the default import resolves through the real axios spread from
      // `actual.default`) and return the shared mock instance the other
      // tests rely on.
      create: (config: unknown) => {
        transport.create(config)
        transport.createdConfigs.push(config)
        return {
          request: transport.request,
          interceptors: {
            request: { use: transport.requestUse, eject: transport.requestEject },
            response: { use: transport.responseUse, eject: transport.responseEject },
          },
        }
      },
      // keep the real error-shape check (defensive fallback included) so the
      // client's minimal translator classifies realistic AxiosError instances
      isAxiosError:
        actual.isAxiosError ??
        ((value: unknown) =>
          value !== null &&
          typeof value === 'object' &&
          (value as { isAxiosError?: unknown }).isAxiosError === true),
    },
  }
})

/** Builds a realistic transport error carrying a backend response. */
function makeAxiosError(status: number, data: unknown): AxiosError {
  return new AxiosError(
    `Request failed with status code ${status}`,
    'ERR_BAD_RESPONSE',
    undefined,
    undefined,
    { data, status, statusText: 'ERR', headers: {}, config: {} } as unknown as AxiosResponse,
  )
}

beforeEach(() => {
  transport.request.mockReset()
  transport.requestUse.mockClear()
  transport.responseUse.mockClear()
  transport.requestEject.mockClear()
  transport.responseEject.mockClear()
  setErrorTranslator(defaultErrorTranslator)
})

describe('base URL configuration (frontend-plan.md §6)', () => {
  it('creates one shared instance configured with the centralized base URL', () => {
    expect(transport.createdConfigs).toEqual([{ baseURL: apiBaseUrl }])
    // no VITE_API_BASE_URL in the test environment -> same-origin Vite proxy
    expect(apiBaseUrl).toBe('')
  })
})

describe('get() — method/URL/params construction', () => {
  it('sends GET with the endpoint path and query params and returns typed data', async () => {
    const payload = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    transport.request.mockResolvedValueOnce({ data: payload })

    const result = await get('/api/travel-requests', { page: 0, size: 20, status: 'PENDING' })

    expect(transport.request).toHaveBeenCalledTimes(1)
    expect(transport.request).toHaveBeenCalledWith({
      method: 'GET',
      url: '/api/travel-requests',
      params: { page: 0, size: 20, status: 'PENDING' },
    })
    expect(result).toBe(payload)
  })

  it('sends GET without params on detail fetches', async () => {
    const payload = { id: 'tr-1', status: 'APPROVED' }
    transport.request.mockResolvedValueOnce({ data: payload })

    const result = await get('/api/travel-requests/tr-1')

    expect(transport.request).toHaveBeenCalledWith({
      method: 'GET',
      url: '/api/travel-requests/tr-1',
      params: undefined,
    })
    expect(result).toBe(payload)
  })
})

describe('post()/put() — JSON body construction', () => {
  it('post() sends the JSON-serialized body with the JSON content type', async () => {
    const body = { destination: 'Berlin', startDate: '2026-11-02', endDate: '2026-11-05' }
    const created = { id: 'tr-9', status: 'PENDING' }
    transport.request.mockResolvedValueOnce({ data: created })

    const result = await post('/api/travel-requests', body)

    expect(transport.request).toHaveBeenCalledWith({
      method: 'POST',
      url: '/api/travel-requests',
      data: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    })
    expect(JSON.parse(String(transport.request.mock.calls[0][0].data))).toEqual(body)
    expect(result).toBe(created)
  })

  it('put() sends the JSON-serialized body with the JSON content type', async () => {
    const body = { destination: 'Munich' }
    const updated = { id: 'tr-1', status: 'PENDING' }
    transport.request.mockResolvedValueOnce({ data: updated })

    const result = await put('/api/travel-requests/tr-1', body)

    expect(transport.request).toHaveBeenCalledWith({
      method: 'PUT',
      url: '/api/travel-requests/tr-1',
      data: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    })
    expect(result).toBe(updated)
  })
})

describe('post() — multipart upload (frontend-plan.md §13, api-contract.md §5.5)', () => {
  it('passes a FormData body through untouched and sets no JSON content type', async () => {
    const formData = new FormData()
    formData.append('file', new File(['receipt-bytes'], 'receipt.pdf', { type: 'application/pdf' }))
    const summary = { documentId: 'doc-1', fileName: 'receipt.pdf' }
    transport.request.mockResolvedValueOnce({ data: summary })

    const result = await post('/api/travel-requests/tr-1/documents', formData)

    const config = transport.request.mock.calls[0][0]
    expect(config.method).toBe('POST')
    expect(config.url).toBe('/api/travel-requests/tr-1/documents')
    // same reference: the transport sets `multipart/form-data` + boundary
    expect(config.data).toBe(formData)
    // no content-type override (in particular no application/json)
    expect(config.headers).toBeUndefined()
    expect(result).toBe(summary)
  })
})

describe('error normalization (F-01 minimal classifier; Section H extends it)', () => {
  it('rejects non-2xx responses with a ClassifiedError carrying the envelope', async () => {
    const envelope = {
      timestamp: '2026-10-06T08:30:00Z',
      status: 422,
      error: 'POLICY_VIOLATION',
      message: 'Receipt required for expenses above threshold',
      fieldErrors: [
        { field: 'documentIds', message: 'required when amount exceeds category threshold' },
      ],
    }
    transport.request.mockRejectedValueOnce(makeAxiosError(422, envelope))

    const error = await post('/api/expenses', { amount: 500 }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(Error)
    expect(error).toMatchObject({
      name: 'ClassifiedError',
      category: 'POLICY_VIOLATION',
      message: 'Receipt required for expenses above threshold',
      status: 422,
      errorCode: 'POLICY_VIOLATION',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [
        { field: 'documentIds', message: 'required when amount exceeds category threshold' },
      ],
    })
  })

  it('maps an envelope-less non-2xx response to INTERNAL_ERROR (status kept)', async () => {
    transport.request.mockRejectedValueOnce(makeAxiosError(500, { detail: 'boom' }))

    const error = await get('/api/expenses').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(Error)
    expect(error).toMatchObject({
      name: 'ClassifiedError',
      category: 'INTERNAL_ERROR',
      status: 500,
      errorCode: null,
      timestamp: null,
      fieldErrors: [],
    })
    // no internal detail leaked into the user-facing message
    expect((error as { message: string }).message).not.toContain('boom')
  })

  it('maps a transport failure without a backend response to NETWORK_ERROR', async () => {
    transport.request.mockRejectedValueOnce(new AxiosError('Network Error', 'ERR_NETWORK'))

    const error = await get('/api/expenses').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(Error)
    expect(error).toMatchObject({
      name: 'ClassifiedError',
      category: 'NETWORK_ERROR',
      status: null,
      errorCode: null,
      timestamp: null,
      fieldErrors: [],
    })
  })

  it('rethrows interceptor-classified errors untouched (no double translation)', async () => {
    const classified = toClassifiedError({
      category: 'CONFLICT',
      message: 'Action invalid for the current entity state',
      status: 409,
      errorCode: 'CONFLICT',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    })
    transport.request.mockRejectedValueOnce(classified)

    await expect(get('/api/travel-requests/tr-1')).rejects.toBe(classified)
  })
})

describe('Section H hook points (frontend-plan.md §13)', () => {
  it('registerInterceptors() registers request and response interceptors', () => {
    const onRequest: RequestInterceptor = (config) => config
    const onResponse: ResponseInterceptor = (response) => response
    const onResponseError: ResponseErrorInterceptor = (error) => Promise.reject(error)

    registerInterceptors({
      request: onRequest,
      response: onResponse,
      responseError: onResponseError,
    })

    expect(transport.requestUse).toHaveBeenCalledTimes(1)
    expect(transport.requestUse).toHaveBeenCalledWith(onRequest)
    expect(transport.responseUse).toHaveBeenCalledTimes(1)
    expect(transport.responseUse).toHaveBeenCalledWith(onResponse, onResponseError)
  })

  it('ejectInterceptors() de-registers the interceptors by id', () => {
    transport.requestUse.mockReturnValueOnce(11)
    transport.responseUse.mockReturnValueOnce(22)
    const ids = registerInterceptors({
      request: (config) => config,
      response: (response) => response,
    })

    ejectInterceptors(ids)

    expect(transport.requestEject).toHaveBeenCalledWith(11)
    expect(transport.responseEject).toHaveBeenCalledWith(22)
  })

  it('ejectInterceptors() is a no-op for ids that were never registered', () => {
    ejectInterceptors({})
    expect(transport.requestEject).not.toHaveBeenCalled()
    expect(transport.responseEject).not.toHaveBeenCalled()
  })

  it('setErrorTranslator() lets Section H plug in the full classifier', async () => {
    const custom: ClassifiedError = {
      category: 'DUPLICATE_RESOURCE',
      message: 'An identical submission already exists',
      status: 409,
      errorCode: 'DUPLICATE_RESOURCE',
      timestamp: '2026-10-06T08:30:00Z',
      fieldErrors: [],
    }
    const translator = vi.fn(() => custom)
    setErrorTranslator(translator)
    transport.request.mockRejectedValueOnce(new Error('boom'))

    const error = await post('/api/expenses', { amount: 500 }).catch((e: unknown) => e)

    expect(translator).toHaveBeenCalledTimes(1)
    expect(error).toBeInstanceOf(Error)
    expect(error).toMatchObject(custom)
  })
})
