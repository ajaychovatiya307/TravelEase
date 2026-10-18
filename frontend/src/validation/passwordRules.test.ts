import { describe, expect, it } from 'vitest'
import { validateNewPassword } from './passwordRules'

describe('validateNewPassword (#14)', () => {
  it('accepts a policy-compliant password that matches its confirmation', () => {
    expect(validateNewPassword('Harbour-2026', 'Harbour-2026', 'old-pass-1')).toEqual({})
  })

  it('rejects passwords outside 10–72 characters', () => {
    expect(validateNewPassword('Short1', 'Short1').newPassword).toMatch(/between 10 and 72/)
    const long = `a1${'x'.repeat(71)}`
    expect(validateNewPassword(long, long).newPassword).toMatch(/between 10 and 72/)
  })

  it('requires a letter and a digit', () => {
    expect(validateNewPassword('onlyletters', 'onlyletters').newPassword).toMatch(/letter and one digit/)
    expect(validateNewPassword('1234567890', '1234567890').newPassword).toMatch(/letter and one digit/)
  })

  it('rejects reusing the current password', () => {
    expect(validateNewPassword('Harbour-2026', 'Harbour-2026', 'Harbour-2026').newPassword).toMatch(
      /different/,
    )
  })

  it('flags a mismatched confirmation', () => {
    expect(validateNewPassword('Harbour-2026', 'Harbour-2027').confirmPassword).toBe(
      'Passwords do not match.',
    )
  })
})
