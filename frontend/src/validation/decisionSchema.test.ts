// decisionSchema unit tests (L-02).
// Covers VAL-12's pass/fail cases for both decision branches: APPROVED
// (no reason requirement — reason optional) and REJECTED (reason required
// and non-blank — missing / empty / whitespace-only reasons rejected, a
// valid reason accepted), plus missing and invalid decision values.
//
// Assertions go through validateDecision (the field-errors adapter the
// forms consume, per frontend-api-mapping.md §3), plus direct
// decisionSchema.safeParse checks for the valid shapes.

import { describe, expect, it } from 'vitest'

import { DECISION_MESSAGES, decisionSchema, validateDecision } from './decisionSchema'
import type { DecisionFieldErrors, DecisionFormValues } from './decisionSchema'

/** A fully valid approval decision (reason omitted). */
const validValues: DecisionFormValues = { decision: 'APPROVED' }

function makeValues(overrides: Partial<DecisionFormValues> = {}): DecisionFormValues {
  return { ...validValues, ...overrides }
}

function fieldErrors(values: unknown): DecisionFieldErrors {
  return validateDecision(values)
}

describe('decisionSchema (L-02)', () => {
  it('accepts a valid decision without a reason (no field errors, successful parse)', () => {
    expect(fieldErrors(validValues)).toEqual({})
    expect(decisionSchema.safeParse(validValues).success).toBe(true)
  })

  describe('APPROVED branch (no reason requirement)', () => {
    it('accepts APPROVED with no reason', () => {
      const errors = fieldErrors(makeValues({ decision: 'APPROVED' }))
      expect(errors).toEqual({})
    })

    it('accepts APPROVED with a reason (reason stays optional)', () => {
      const values = makeValues({ decision: 'APPROVED', reason: 'Within budget.' })
      expect(fieldErrors(values)).toEqual({})
      expect(decisionSchema.safeParse(values).success).toBe(true)
    })
  })

  describe('REJECTED branch (VAL-12: reason required, non-blank)', () => {
    it('rejects REJECTED with a missing reason', () => {
      const errors = fieldErrors(makeValues({ decision: 'REJECTED' }))
      expect(errors.reason).toBe(DECISION_MESSAGES.reason)
      expect(errors).not.toHaveProperty('decision')
    })

    it('rejects REJECTED with an empty reason', () => {
      const errors = fieldErrors(makeValues({ decision: 'REJECTED', reason: '' }))
      expect(errors.reason).toBe(DECISION_MESSAGES.reason)
    })

    it('rejects REJECTED with a whitespace-only reason', () => {
      const errors = fieldErrors(makeValues({ decision: 'REJECTED', reason: '   ' }))
      expect(errors.reason).toBe(DECISION_MESSAGES.reason)
    })

    it('accepts REJECTED with a valid reason', () => {
      const values = makeValues({
        decision: 'REJECTED',
        reason: 'Exceeds the quarterly travel budget.',
      })
      expect(fieldErrors(values)).toEqual({})
      expect(decisionSchema.safeParse(values).success).toBe(true)
    })
  })

  describe('decision value', () => {
    it('rejects a missing decision with the decision message', () => {
      const errors = fieldErrors(makeValues({ decision: undefined }))
      expect(errors.decision).toBe(DECISION_MESSAGES.decision)
      expect(errors).not.toHaveProperty('reason')
    })

    it('rejects an invalid decision value without cascading to reason', () => {
      const errors = fieldErrors({ decision: 'PENDING', reason: 'Some reason' })
      expect(errors.decision).toBe(DECISION_MESSAGES.decision)
      expect(errors).not.toHaveProperty('reason')
    })

    it('rejects an invalid decision with a missing reason as decision-only', () => {
      const errors = fieldErrors({ decision: 'PENDING' })
      expect(errors).toEqual({ decision: DECISION_MESSAGES.decision })
    })
  })
})
