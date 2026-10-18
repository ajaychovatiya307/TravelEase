// expenseClaimSchema unit tests (L-03).
// Covers each VAL-05 through VAL-10 rule's pass/fail cases, including the
// threshold-conditional document rule (VAL-10) with varying threshold
// inputs (no threshold / threshold below amount / threshold at amount /
// threshold above amount) and the trip-range rule (VAL-08) with boundary
// dates (VAL-T-11) and one-day-outside dates (VAL-T-12).
//
// Assertions go through validateExpenseClaim (the field-errors adapter the
// forms consume, per frontend-api-mapping.md §3), plus direct
// schema.safeParse checks for the valid shape.

import { describe, expect, it } from 'vitest'

import {
  EXPENSE_CLAIM_MESSAGES,
  expenseClaimSchema,
  isValidIsoDate,
  validateExpenseClaim,
} from './expenseClaimSchema'
import type {
  ExpenseClaimFieldErrors,
  ExpenseClaimFormValues,
  ExpenseClaimValidationOptions,
} from './expenseClaimSchema'

const TRIP_RANGE = { startDate: '2026-03-09', endDate: '2026-03-15' }

/** A fully valid claim inside the trip range. */
const validValues: ExpenseClaimFormValues = {
  travelRequestId: 'tr-1',
  amount: 120.5,
  currency: 'USD',
  category: 'HOTEL',
  expenseDate: '2026-03-10',
  documentIds: ['doc-1'],
}

function makeValues(overrides: Partial<ExpenseClaimFormValues> = {}): ExpenseClaimFormValues {
  return { ...validValues, ...overrides }
}

/** Full options (trip range + threshold 100 + active codes); tests override per case. */
function makeOptions(
  overrides: Partial<ExpenseClaimValidationOptions> = {},
): ExpenseClaimValidationOptions {
  return {
    travelRequestDateRange: TRIP_RANGE,
    receiptThresholdAmount: 100,
    activeCategoryCodes: ['HOTEL', 'MEALS', 'TRANSPORT'],
    ...overrides,
  }
}

function fieldErrors(
  values: ExpenseClaimFormValues,
  options?: ExpenseClaimValidationOptions,
): ExpenseClaimFieldErrors {
  return validateExpenseClaim(values, options)
}

describe('expenseClaimSchema (L-03)', () => {
  it('accepts a fully valid claim (no field errors, successful parse)', () => {
    expect(fieldErrors(validValues, makeOptions())).toEqual({})
    expect(expenseClaimSchema(makeOptions()).safeParse(validValues).success).toBe(true)
  })

  it('works with no options supplied (all conditional checks skipped)', () => {
    // No trip range, no threshold, no category codes: only presence/format
    // rules apply, so a claim with no documents and a date far outside any
    // trip still passes.
    const values = makeValues({ documentIds: [], expenseDate: '2025-01-05' })
    expect(fieldErrors(values)).toEqual({})
  })

  describe('VAL-09 travelRequestId (linked travel request)', () => {
    it('passes when a travel request is selected', () => {
      expect(fieldErrors(makeValues(), makeOptions())).not.toHaveProperty('travelRequestId')
    })

    it('rejects a missing travel request', () => {
      const errors = fieldErrors(makeValues({ travelRequestId: undefined }), makeOptions())
      expect(errors.travelRequestId).toBe(EXPENSE_CLAIM_MESSAGES.travelRequestId)
    })

    it('rejects a blank travel request', () => {
      const errors = fieldErrors(makeValues({ travelRequestId: '   ' }), makeOptions())
      expect(errors.travelRequestId).toBe(EXPENSE_CLAIM_MESSAGES.travelRequestId)
    })
  })

  describe('VAL-05 amount', () => {
    it('accepts the smallest positive unit (VAL-T-08)', () => {
      expect(fieldErrors(makeValues({ amount: 0.01, documentIds: [] }))).not.toHaveProperty(
        'amount',
      )
    })

    it('rejects a zero amount (VAL-T-07)', () => {
      const errors = fieldErrors(makeValues({ amount: 0, documentIds: [] }))
      expect(errors.amount).toBe(EXPENSE_CLAIM_MESSAGES.amountPositive)
    })

    it('rejects a negative amount', () => {
      const errors = fieldErrors(makeValues({ amount: -42, documentIds: [] }))
      expect(errors.amount).toBe(EXPENSE_CLAIM_MESSAGES.amountPositive)
    })

    it('rejects a missing amount with the required message', () => {
      const errors = fieldErrors(makeValues({ amount: undefined, documentIds: [] }))
      expect(errors.amount).toBe(EXPENSE_CLAIM_MESSAGES.amountRequired)
    })

    it('rejects a non-numeric (NaN) amount with the required message', () => {
      const errors = fieldErrors(makeValues({ amount: NaN, documentIds: [] }), makeOptions())
      expect(errors.amount).toBe(EXPENSE_CLAIM_MESSAGES.amountRequired)
    })

    it('does not cascade a failed amount into the document rule (VAL-10)', () => {
      const errors = fieldErrors(makeValues({ amount: NaN, documentIds: [] }), makeOptions())
      expect(errors).not.toHaveProperty('documentIds')
    })
  })

  describe('VAL-06 currency', () => {
    it('passes when a currency is present', () => {
      expect(fieldErrors(makeValues(), makeOptions())).not.toHaveProperty('currency')
    })

    it('rejects a blank currency (VAL-T-09)', () => {
      const errors = fieldErrors(makeValues({ currency: '' }), makeOptions())
      expect(errors.currency).toBe(EXPENSE_CLAIM_MESSAGES.currency)
    })

    it('rejects a whitespace-only currency', () => {
      const errors = fieldErrors(makeValues({ currency: '   ' }), makeOptions())
      expect(errors.currency).toBe(EXPENSE_CLAIM_MESSAGES.currency)
    })

    it('rejects a missing currency', () => {
      const errors = fieldErrors(makeValues({ currency: undefined }), makeOptions())
      expect(errors.currency).toBe(EXPENSE_CLAIM_MESSAGES.currency)
    })
  })

  describe('VAL-07 category', () => {
    it('passes when an active category is selected', () => {
      expect(fieldErrors(makeValues(), makeOptions())).not.toHaveProperty('category')
    })

    it('rejects a blank category', () => {
      const errors = fieldErrors(makeValues({ category: '' }), makeOptions())
      expect(errors.category).toBe(EXPENSE_CLAIM_MESSAGES.categoryRequired)
    })

    it('rejects a missing category', () => {
      const errors = fieldErrors(makeValues({ category: undefined }), makeOptions())
      expect(errors.category).toBe(EXPENSE_CLAIM_MESSAGES.categoryRequired)
    })

    it('rejects an inactive/unknown category when active codes are supplied (VAL-T-10)', () => {
      const errors = fieldErrors(makeValues({ category: 'UNKNOWN' }), makeOptions())
      expect(errors.category).toBe(EXPENSE_CLAIM_MESSAGES.categoryActive)
    })

    it('only checks presence when no active codes are supplied', () => {
      const errors = fieldErrors(
        makeValues({ category: 'UNKNOWN' }),
        makeOptions({ activeCategoryCodes: undefined }),
      )
      expect(errors).not.toHaveProperty('category')
    })
  })

  describe('VAL-08 expenseDate (trip range)', () => {
    it('passes for a date inside the trip range', () => {
      expect(fieldErrors(makeValues(), makeOptions())).not.toHaveProperty('expenseDate')
    })

    it('accepts a date exactly at the trip start boundary (VAL-T-11)', () => {
      const errors = fieldErrors(makeValues({ expenseDate: TRIP_RANGE.startDate }), makeOptions())
      expect(errors).not.toHaveProperty('expenseDate')
    })

    it('accepts a date exactly at the trip end boundary (VAL-T-11)', () => {
      const errors = fieldErrors(makeValues({ expenseDate: TRIP_RANGE.endDate }), makeOptions())
      expect(errors).not.toHaveProperty('expenseDate')
    })

    it('rejects a date one day before the trip range (VAL-T-12)', () => {
      const errors = fieldErrors(makeValues({ expenseDate: '2026-03-08' }), makeOptions())
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateInRange)
    })

    it('rejects a date one day after the trip range (VAL-T-12)', () => {
      const errors = fieldErrors(makeValues({ expenseDate: '2026-03-16' }), makeOptions())
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateInRange)
    })

    it('skips the range check when no trip range is supplied', () => {
      const errors = fieldErrors(
        makeValues({ expenseDate: '2025-01-05' }),
        makeOptions({ travelRequestDateRange: undefined }),
      )
      expect(errors).not.toHaveProperty('expenseDate')
    })

    it('rejects a blank expense date', () => {
      const errors = fieldErrors(makeValues({ expenseDate: '' }), makeOptions())
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateRequired)
    })

    it('rejects a missing expense date', () => {
      const errors = fieldErrors(makeValues({ expenseDate: undefined }), makeOptions())
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateRequired)
    })

    it('rejects an unparseable expense date', () => {
      const errors = fieldErrors(makeValues({ expenseDate: 'not-a-date' }), makeOptions())
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateInvalid)
    })

    it('rejects a calendar-impossible date (Feb 30)', () => {
      const errors = fieldErrors(makeValues({ expenseDate: '2026-02-30' }), makeOptions())
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateInvalid)
    })

    it('skips the range check when the supplied range itself is invalid', () => {
      const errors = fieldErrors(
        makeValues({ expenseDate: '2025-01-05' }),
        makeOptions({ travelRequestDateRange: { startDate: 'garbage', endDate: 'junk' } }),
      )
      expect(errors).not.toHaveProperty('expenseDate')
    })
  })

  describe('VAL-10 documentIds (threshold-conditional)', () => {
    it('passes when no threshold is supplied (documents optional)', () => {
      const errors = fieldErrors(
        makeValues({ amount: 999, documentIds: [] }),
        makeOptions({ receiptThresholdAmount: undefined }),
      )
      expect(errors).not.toHaveProperty('documentIds')
    })

    it('passes when amount is below the threshold, with no documents', () => {
      const errors = fieldErrors(
        makeValues({ amount: 50, documentIds: [] }),
        makeOptions({ receiptThresholdAmount: 100 }),
      )
      expect(errors).not.toHaveProperty('documentIds')
    })

    it('passes when amount is exactly at the threshold (VAL-T-14)', () => {
      const errors = fieldErrors(
        makeValues({ amount: 100, documentIds: [] }),
        makeOptions({ receiptThresholdAmount: 100 }),
      )
      expect(errors).not.toHaveProperty('documentIds')
    })

    it('rejects missing documents when amount is one unit above the threshold (VAL-T-15)', () => {
      const errors = fieldErrors(
        makeValues({ amount: 100.01, documentIds: [] }),
        makeOptions({ receiptThresholdAmount: 100 }),
      )
      expect(errors.documentIds).toBe(EXPENSE_CLAIM_MESSAGES.documentIds)
    })

    it('rejects missing documents when the threshold is below the amount', () => {
      const errors = fieldErrors(
        makeValues({ amount: 150, documentIds: [] }),
        makeOptions({ receiptThresholdAmount: 100 }),
      )
      expect(errors.documentIds).toBe(EXPENSE_CLAIM_MESSAGES.documentIds)
    })

    it('passes when the threshold is above the amount, with no documents', () => {
      const errors = fieldErrors(
        makeValues({ amount: 150, documentIds: [] }),
        makeOptions({ receiptThresholdAmount: 200 }),
      )
      expect(errors).not.toHaveProperty('documentIds')
    })

    it('passes when amount is above the threshold and a document is attached', () => {
      const errors = fieldErrors(
        makeValues({ amount: 500, documentIds: ['doc-1'] }),
        makeOptions({ receiptThresholdAmount: 100 }),
      )
      expect(errors).not.toHaveProperty('documentIds')
    })
  })

  describe('validateExpenseClaim (field-error adapter)', () => {
    it('returns an empty object for valid values', () => {
      expect(validateExpenseClaim(validValues, makeOptions())).toEqual({})
    })

    it('maps every failing field to its own key with a field-specific message', () => {
      const errors = fieldErrors(
        makeValues({
          travelRequestId: '',
          amount: 0,
          currency: '',
          category: 'UNKNOWN',
          expenseDate: '2026-01-01',
          documentIds: [],
        }),
        makeOptions(),
      )
      expect(Object.keys(errors).sort()).toEqual([
        'amount',
        'category',
        'currency',
        'expenseDate',
        'travelRequestId',
      ])
      expect(errors.travelRequestId).toBe(EXPENSE_CLAIM_MESSAGES.travelRequestId)
      expect(errors.amount).toBe(EXPENSE_CLAIM_MESSAGES.amountPositive)
      expect(errors.currency).toBe(EXPENSE_CLAIM_MESSAGES.currency)
      expect(errors.category).toBe(EXPENSE_CLAIM_MESSAGES.categoryActive)
      expect(errors.expenseDate).toBe(EXPENSE_CLAIM_MESSAGES.expenseDateInRange)
      // amount 0 is not above the threshold, so no documentIds error cascades
      expect(errors).not.toHaveProperty('documentIds')
    })

    it('ignores root-level issues (no field path) and extra unknown fields', () => {
      // Non-object input produces a root-level invalid_type issue with an
      // empty path — the adapter drops it rather than attributing it to a
      // field. Extra unknown fields are stripped by the object schema.
      expect(validateExpenseClaim(null)).toEqual({})
      expect(validateExpenseClaim({ ...validValues, bogusField: 'x' })).toEqual({})
    })
  })

  describe('isValidIsoDate', () => {
    it('accepts real calendar dates', () => {
      expect(isValidIsoDate('2026-03-10')).toBe(true)
      expect(isValidIsoDate('2024-02-29')).toBe(true) // leap year
    })

    it('rejects non-dates and malformed values', () => {
      expect(isValidIsoDate('')).toBe(false)
      expect(isValidIsoDate('2026-02-30')).toBe(false)
      expect(isValidIsoDate('2026-13-01')).toBe(false)
      expect(isValidIsoDate('2026-3-10')).toBe(false)
      expect(isValidIsoDate('10/03/2026')).toBe(false)
      expect(isValidIsoDate('2026-03-10T00:00:00Z')).toBe(false)
    })
  })
})
