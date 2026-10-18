import { describe, expect, it } from 'vitest'

import type { FieldError } from '../models/error'
import { applyServerFieldErrors } from './applyServerFieldErrors'

const VISIBLE_FIELDS = ['destination', 'startDate', 'documentIds'] as const

describe('applyServerFieldErrors (L-06)', () => {
  it('maps errors for visible fields and returns unmatched messages for the form banner', () => {
    const serverErrors: FieldError[] = [
      { field: 'destination', message: 'Destination is unavailable.' },
      { field: 'policy', message: 'The active policy rejected this request.' },
      { field: 'documentIds', message: 'A supporting document is required.' },
    ]

    expect(applyServerFieldErrors(serverErrors, VISIBLE_FIELDS)).toEqual({
      fieldErrors: {
        destination: 'Destination is unavailable.',
        documentIds: 'A supporting document is required.',
      },
      formErrors: ['The active policy rejected this request.'],
    })
  })

  it('puts every message in the form-level summary when there are no visible fields', () => {
    const serverErrors: FieldError[] = [
      { field: 'travelRequestId', message: 'The linked trip is no longer available.' },
    ]

    expect(applyServerFieldErrors(serverErrors, [])).toEqual({
      fieldErrors: {},
      formErrors: ['The linked trip is no longer available.'],
    })
  })

  it('returns empty results when errors are absent', () => {
    expect(applyServerFieldErrors(undefined, VISIBLE_FIELDS)).toEqual({
      fieldErrors: {},
      formErrors: [],
    })
    expect(applyServerFieldErrors(null, VISIBLE_FIELDS)).toEqual({
      fieldErrors: {},
      formErrors: [],
    })
  })

  it('keeps the first message when the server reports a field more than once', () => {
    const serverErrors: FieldError[] = [
      { field: 'startDate', message: 'Start date is invalid.' },
      { field: 'startDate', message: 'Start date is outside the allowed range.' },
    ]

    expect(applyServerFieldErrors(serverErrors, VISIBLE_FIELDS)).toEqual({
      fieldErrors: { startDate: 'Start date is invalid.' },
      formErrors: [],
    })
  })
})
