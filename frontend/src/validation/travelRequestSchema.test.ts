// travelRequestSchema unit tests (L-01, frontend-tasks.md — "L. Validation").
// Covers each client pre-submit rule's pass/fail cases (VAL-01–04,
// frontend-spec.md §7; frontend-api-mapping.md §3) plus the TR-01 form →
// create-request transformation (frontend-api-mapping.md §2).

import { describe, expect, it } from 'vitest'

import {
  createTravelRequestFormSchema,
  JUSTIFICATION_MAX_LENGTH,
  JUSTIFICATION_MIN_LENGTH,
  formatIsoDate,
  toTravelRequestCreateRequest,
  todayIsoDate,
  travelRequestFormSchema,
} from './travelRequestSchema'
import type { TravelRequestFormValues } from './travelRequestSchema'

/** A `YYYY-MM-DD` string offset from today by `days` (local time). */
function isoDateOffsetBy(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return formatIsoDate(date)
}

const validValues: TravelRequestFormValues = {
  destination: 'Bengaluru',
  startDate: isoDateOffsetBy(1),
  endDate: isoDateOffsetBy(3),
  justification: 'Onsite workshop with the payments team',
  documents: [{ documentId: 'doc-1' }],
}

/** `true` when the schema accepts `values` as-is. */
function isValid(values: TravelRequestFormValues): boolean {
  return travelRequestFormSchema.safeParse(values).success
}

/** Top-level field paths of the issues raised for `values` (empty when valid). */
function issuePaths(values: TravelRequestFormValues): string[] {
  const result = travelRequestFormSchema.safeParse(values)
  if (result.success) {
    return []
  }
  return result.error.issues.map((issue) => String(issue.path[0]))
}

describe('travelRequestFormSchema (L-01)', () => {
  it('accepts a fully valid payload with documents', () => {
    expect(isValid(validValues)).toBe(true)
  })

  it('accepts extra per-document state next to documentId', () => {
    expect(
      isValid({
        ...validValues,
        documents: [{ documentId: 'doc-1', fileName: 'report.pdf' }],
      }),
    ).toBe(true)
  })

  describe('VAL-01: destination', () => {
    it.each(['', '   '])('rejects a blank destination (%s)', (destination) => {
      const values = { ...validValues, destination }
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('destination')
    })
  })

  describe('VAL-02: travel dates', () => {
    it('rejects a startDate after endDate', () => {
      const values = {
        ...validValues,
        startDate: isoDateOffsetBy(3),
        endDate: isoDateOffsetBy(1),
      }
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('endDate')
    })

    it('accepts a startDate equal to endDate', () => {
      const values = {
        ...validValues,
        startDate: isoDateOffsetBy(2),
        endDate: isoDateOffsetBy(2),
      }
      expect(isValid(values)).toBe(true)
    })

    it('rejects a past startDate', () => {
      const values = {
        ...validValues,
        startDate: isoDateOffsetBy(-1),
        endDate: isoDateOffsetBy(1),
      }
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('startDate')
    })

    it('accepts a startDate of today (not considered past)', () => {
      const values = {
        ...validValues,
        startDate: todayIsoDate(),
        endDate: todayIsoDate(),
      }
      expect(isValid(values)).toBe(true)
    })

    it.each(['2026-01-01', '', '01/02/2026', '2026-13-01'])(
      'rejects a malformed or impossible date (%s)',
      (startDate) => {
        const values = { ...validValues, startDate, endDate: startDate }
        expect(isValid(values)).toBe(false)
        expect(issuePaths(values)).toContain('startDate')
      },
    )
  })

  describe('VAL-03: justification', () => {
    it('rejects an empty justification', () => {
      const values = { ...validValues, justification: '' }
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('justification')
    })

    it('rejects a justification below the minimum length', () => {
      const values = {
        ...validValues,
        justification: 'x'.repeat(JUSTIFICATION_MIN_LENGTH - 1),
      }
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('justification')
    })

    it('accepts a justification at exactly the minimum length', () => {
      const values = {
        ...validValues,
        justification: 'x'.repeat(JUSTIFICATION_MIN_LENGTH),
      }
      expect(isValid(values)).toBe(true)
    })

    it('rejects a justification above the maximum length', () => {
      const values = {
        ...validValues,
        justification: 'x'.repeat(JUSTIFICATION_MAX_LENGTH + 1),
      }
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('justification')
    })
  })

  describe('VAL-04: documents', () => {
    it('accepts an omitted documents field', () => {
      expect(isValid({ ...validValues, documents: undefined })).toBe(true)
    })

    it('accepts an empty documents array', () => {
      expect(isValid({ ...validValues, documents: [] })).toBe(true)
    })

    it('rejects a document reference without a documentId', () => {
      // Intentionally invalid input (missing `documentId`), so the cast
      // bypasses the compile-time shape the form will uphold.
      const values = {
        ...validValues,
        documents: [{}],
      } as TravelRequestFormValues
      expect(isValid(values)).toBe(false)
      expect(issuePaths(values)).toContain('documents')
    })
  })

  describe('policy and date options', () => {
    const futureValues = {
      ...validValues,
      startDate: '2099-01-01',
      endDate: '2099-01-03',
    }

    it('uses the supplied today boundary for the past-date check', () => {
      const schema = createTravelRequestFormSchema({ today: '2099-01-02' })
      const result = schema.safeParse(futureValues)

      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.map((issue) => String(issue.path[0]))).toContain('startDate')
      }
    })

    it('requires a non-blank document reference only when policy requires one', () => {
      const requiredSchema = createTravelRequestFormSchema({
        today: '2099-01-01',
        documentsRequired: true,
      })
      const optionalSchema = createTravelRequestFormSchema({
        today: '2099-01-01',
        documentsRequired: false,
      })

      expect(requiredSchema.safeParse({ ...futureValues, documents: [] }).success).toBe(false)
      expect(
        requiredSchema.safeParse({ ...futureValues, documents: [{ documentId: 'doc-1' }] })
          .success,
      ).toBe(true)
      expect(optionalSchema.safeParse({ ...futureValues, documents: [] }).success).toBe(true)
    })
  })
})

describe('toTravelRequestCreateRequest (TR-01)', () => {
  it('trims string fields, keeps YYYY-MM-DD dates, and maps documents to documentIds', () => {
    const request = toTravelRequestCreateRequest({
      destination: '  Bengaluru  ',
      startDate: isoDateOffsetBy(1),
      endDate: isoDateOffsetBy(3),
      justification: '  Onsite workshop with the payments team  ',
      documents: [{ documentId: 'doc-1', fileName: 'a.pdf' }, { documentId: 'doc-2' }],
    })

    expect(request).toEqual({
      destination: 'Bengaluru',
      startDate: isoDateOffsetBy(1),
      endDate: isoDateOffsetBy(3),
      justification: 'Onsite workshop with the payments team',
      documentIds: ['doc-1', 'doc-2'],
    })
  })

  it('omits documentIds entirely when no documents are selected', () => {
    const request = toTravelRequestCreateRequest({ ...validValues, documents: [] })
    expect('documentIds' in request).toBe(false)
  })

  it('omits documentIds when the documents field is absent', () => {
    const request = toTravelRequestCreateRequest({ ...validValues, documents: undefined })
    expect('documentIds' in request).toBe(false)
  })
})
