// adminSchemas unit tests (L-04).
// Covers the J-06–J-09 acceptance criteria for the four Admin form rule-sets
// (P-16 userAccountSchema, P-17 employeeManagerMappingSchema, P-18
// policyConfigSchema, P-19 expenseCategorySchema): required non-blank fields,
// client-side email format, the roles multi-select minimum (J-06), the
// nullable managerId for top-of-hierarchy managers (J-07), the non-negative
// receipt threshold (J-08), and the required code/label (J-09).
//
// Assertions go through the validate*() field-errors adapters the forms
// consume (per frontend-api-mapping.md §3), plus direct schema.safeParse
// checks for the valid shapes. Error copy is asserted via the exported
// *_MESSAGES constants, never hardcoded.

import { describe, expect, it } from 'vitest'

import {
  EMPLOYEE_MANAGER_MAPPING_MESSAGES,
  EXPENSE_CATEGORY_MESSAGES,
  POLICY_CONFIG_MESSAGES,
  USER_ACCOUNT_MESSAGES,
  createExpenseCategorySchema,
  createPolicyConfigSchema,
  employeeManagerMappingSchema,
  expenseCategorySchema,
  policyConfigSchema,
  userAccountSchema,
  validateEmployeeManagerMapping,
  validateExpenseCategory,
  validatePolicyConfig,
  validateUserAccount,
} from './adminSchemas'
import type {
  EmployeeManagerMappingRequest,
  ExpenseCategoryRequest,
  PolicyConfigRequest,
  UserAccountRequest,
} from '../models/admin'

/** A fully valid user account submission (P-16). */
const validUser: UserAccountRequest = {
  fullName: 'Ada Lovelace',
  email: 'ada.lovelace@example.com',
  roles: ['EMPLOYEE', 'MANAGER'],
  active: true,
}

/** A fully valid employee-to-manager mapping (P-17). */
const validMapping: EmployeeManagerMappingRequest = {
  employeeId: 'user-1',
  managerId: 'user-2',
  effectiveFrom: '2026-04-01',
}

/** A fully valid policy configuration (P-18). */
const validPolicy: PolicyConfigRequest = {
  categoryCode: 'MEALS',
  receiptThresholdAmount: 50,
  currency: 'USD',
  active: true,
}

/** A fully valid expense category (P-19). */
const validCategory: ExpenseCategoryRequest = {
  code: 'MEALS',
  label: 'Meals',
  active: true,
}

function makeUser(overrides: Partial<UserAccountRequest> = {}): UserAccountRequest {
  return { ...validUser, ...overrides }
}

function makeMapping(
  overrides: Partial<EmployeeManagerMappingRequest> = {},
): EmployeeManagerMappingRequest {
  return { ...validMapping, ...overrides }
}

function makePolicy(overrides: Partial<PolicyConfigRequest> = {}): PolicyConfigRequest {
  return { ...validPolicy, ...overrides }
}

function makeCategory(overrides: Partial<ExpenseCategoryRequest> = {}): ExpenseCategoryRequest {
  return { ...validCategory, ...overrides }
}

describe('userAccountSchema (L-04 / J-06)', () => {
  it('accepts a valid user (no field errors, successful parse)', () => {
    expect(validateUserAccount(validUser)).toEqual({})
    expect(userAccountSchema.safeParse(validUser).success).toBe(true)
  })

  it('accepts a user with a single role (one value satisfies the multi-select)', () => {
    const values = makeUser({ roles: ['MANAGER'] })
    expect(validateUserAccount(values)).toEqual({})
    expect(userAccountSchema.safeParse(values).success).toBe(true)
  })

  it('accepts a user with active omitted (server defaults it to true)', () => {
    const values = {
      fullName: validUser.fullName,
      email: validUser.email,
      roles: validUser.roles,
    }
    expect(validateUserAccount(values)).toEqual({})
    expect(userAccountSchema.safeParse(values).success).toBe(true)
  })

  it('rejects an empty roles array with the roles message', () => {
    const errors = validateUserAccount(makeUser({ roles: [] }))
    expect(errors.roles).toBe(USER_ACCOUNT_MESSAGES.roles)
    expect(errors).not.toHaveProperty('fullName')
    expect(errors).not.toHaveProperty('email')
  })

  it('rejects a missing roles array with the roles message', () => {
    const errors = validateUserAccount({
      fullName: validUser.fullName,
      email: validUser.email,
      active: validUser.active,
    })
    expect(errors.roles).toBe(USER_ACCOUNT_MESSAGES.roles)
    expect(errors).not.toHaveProperty('fullName')
    expect(errors).not.toHaveProperty('email')
  })

  it('rejects an unrecognized role value with the roles message', () => {
    const errors = validateUserAccount({ ...validUser, roles: ['SUPERUSER'] })
    expect(errors.roles).toBe(USER_ACCOUNT_MESSAGES.roles)
    expect(errors).not.toHaveProperty('fullName')
    expect(errors).not.toHaveProperty('email')
  })

  it('rejects a missing fullName with the fullName message', () => {
    const errors = validateUserAccount({
      email: validUser.email,
      roles: validUser.roles,
      active: validUser.active,
    })
    expect(errors.fullName).toBe(USER_ACCOUNT_MESSAGES.fullName)
    expect(errors).not.toHaveProperty('email')
  })

  it('rejects a whitespace-only fullName with the fullName message', () => {
    const errors = validateUserAccount(makeUser({ fullName: '   ' }))
    expect(errors.fullName).toBe(USER_ACCOUNT_MESSAGES.fullName)
    expect(errors).not.toHaveProperty('email')
  })

  it('rejects a missing email with the email message', () => {
    const errors = validateUserAccount({
      fullName: validUser.fullName,
      roles: validUser.roles,
      active: validUser.active,
    })
    expect(errors.email).toBe(USER_ACCOUNT_MESSAGES.email)
    expect(errors).not.toHaveProperty('fullName')
  })

  it('rejects a malformed email with the emailFormat message', () => {
    const errors = validateUserAccount(makeUser({ email: 'not-an-email' }))
    expect(errors.email).toBe(USER_ACCOUNT_MESSAGES.emailFormat)
    expect(errors).not.toHaveProperty('fullName')
  })

  it('reports every failing field once, keyed by field name (sorted keys)', () => {
    const errors = validateUserAccount({ fullName: '', email: 'bad', roles: [] })
    expect(Object.keys(errors).sort()).toEqual(['email', 'fullName', 'roles'])
    expect(errors.email).toBe(USER_ACCOUNT_MESSAGES.emailFormat)
    expect(errors.fullName).toBe(USER_ACCOUNT_MESSAGES.fullName)
    expect(errors.roles).toBe(USER_ACCOUNT_MESSAGES.roles)
  })
})

describe('employeeManagerMappingSchema (L-04 / J-07)', () => {
  it('accepts a valid mapping (no field errors, successful parse)', () => {
    expect(validateEmployeeManagerMapping(validMapping)).toEqual({})
    expect(employeeManagerMappingSchema.safeParse(validMapping).success).toBe(true)
  })

  it('accepts an empty managerId (top-of-hierarchy manager, J-07)', () => {
    const values = makeMapping({ managerId: '' })
    expect(validateEmployeeManagerMapping(values)).toEqual({})
    expect(employeeManagerMappingSchema.safeParse(values).success).toBe(true)
  })

  it('accepts a null managerId (top-of-hierarchy manager, J-07)', () => {
    const values = makeMapping({ managerId: null })
    expect(validateEmployeeManagerMapping(values)).toEqual({})
    expect(employeeManagerMappingSchema.safeParse(values).success).toBe(true)
  })

  it('accepts an omitted managerId (top-of-hierarchy manager, J-07)', () => {
    const values = {
      employeeId: validMapping.employeeId,
      effectiveFrom: validMapping.effectiveFrom,
    }
    expect(validateEmployeeManagerMapping(values)).toEqual({})
    expect(employeeManagerMappingSchema.safeParse(values).success).toBe(true)
  })

  it('rejects a missing employeeId with the employeeId message', () => {
    const errors = validateEmployeeManagerMapping({
      managerId: validMapping.managerId,
      effectiveFrom: validMapping.effectiveFrom,
    })
    expect(errors.employeeId).toBe(EMPLOYEE_MANAGER_MAPPING_MESSAGES.employeeId)
    expect(errors).not.toHaveProperty('managerId')
  })

  it('rejects an empty employeeId with the employeeId message', () => {
    const errors = validateEmployeeManagerMapping(makeMapping({ employeeId: '' }))
    expect(errors.employeeId).toBe(EMPLOYEE_MANAGER_MAPPING_MESSAGES.employeeId)
    expect(errors).not.toHaveProperty('effectiveFrom')
  })

  it('rejects a missing effectiveFrom with the effectiveFrom message', () => {
    const errors = validateEmployeeManagerMapping({
      employeeId: validMapping.employeeId,
      managerId: validMapping.managerId,
    })
    expect(errors.effectiveFrom).toBe(EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFrom)
    expect(errors).not.toHaveProperty('employeeId')
  })

  it('rejects a non-existent calendar date with the effectiveFromFormat message', () => {
    const errors = validateEmployeeManagerMapping(makeMapping({ effectiveFrom: '2026-02-30' }))
    expect(errors.effectiveFrom).toBe(EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFromFormat)
    expect(errors).not.toHaveProperty('employeeId')
  })

  it('reports every failing field once, keyed by field name (sorted keys)', () => {
    const errors = validateEmployeeManagerMapping({ employeeId: '', effectiveFrom: 'nope' })
    expect(Object.keys(errors).sort()).toEqual(['effectiveFrom', 'employeeId'])
    expect(errors.effectiveFrom).toBe(EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFromFormat)
    expect(errors.employeeId).toBe(EMPLOYEE_MANAGER_MAPPING_MESSAGES.employeeId)
  })
})

describe('policyConfigSchema (L-04 / J-08)', () => {
  it('accepts a valid policy (no field errors, successful parse)', () => {
    expect(validatePolicyConfig(validPolicy)).toEqual({})
    expect(policyConfigSchema.safeParse(validPolicy).success).toBe(true)
  })

  it('accepts a zero threshold (zero is non-negative)', () => {
    const values = makePolicy({ receiptThresholdAmount: 0 })
    expect(validatePolicyConfig(values)).toEqual({})
    expect(policyConfigSchema.safeParse(values).success).toBe(true)
  })

  it('rejects a negative threshold with the non-negative message', () => {
    const errors = validatePolicyConfig(makePolicy({ receiptThresholdAmount: -0.01 }))
    expect(errors.receiptThresholdAmount).toBe(POLICY_CONFIG_MESSAGES.receiptThresholdNonNegative)
    expect(errors).not.toHaveProperty('categoryCode')
  })

  it('rejects a missing threshold with the receiptThresholdAmount message', () => {
    const errors = validatePolicyConfig({
      categoryCode: validPolicy.categoryCode,
      currency: validPolicy.currency,
      active: validPolicy.active,
    })
    expect(errors.receiptThresholdAmount).toBe(POLICY_CONFIG_MESSAGES.receiptThresholdAmount)
    expect(errors).not.toHaveProperty('categoryCode')
  })

  it('rejects a string threshold with the receiptThresholdAmount message', () => {
    const errors = validatePolicyConfig({ ...validPolicy, receiptThresholdAmount: '50' })
    expect(errors.receiptThresholdAmount).toBe(POLICY_CONFIG_MESSAGES.receiptThresholdAmount)
    expect(errors).not.toHaveProperty('categoryCode')
  })

  it('rejects a missing categoryCode with the categoryCode message', () => {
    const errors = validatePolicyConfig({
      receiptThresholdAmount: validPolicy.receiptThresholdAmount,
      currency: validPolicy.currency,
      active: validPolicy.active,
    })
    expect(errors.categoryCode).toBe(POLICY_CONFIG_MESSAGES.categoryCode)
    expect(errors).not.toHaveProperty('currency')
  })

  it('rejects a whitespace-only categoryCode with the categoryCode message', () => {
    const errors = validatePolicyConfig(makePolicy({ categoryCode: '  ' }))
    expect(errors.categoryCode).toBe(POLICY_CONFIG_MESSAGES.categoryCode)
    expect(errors).not.toHaveProperty('currency')
  })

  it('rejects a categoryCode that is not in the supplied existing categories', () => {
    const errors = validatePolicyConfig(makePolicy({ categoryCode: 'TRAINING' }), ['MEALS'])
    expect(errors.categoryCode).toBe(POLICY_CONFIG_MESSAGES.categoryCodeUnknown)
  })

  it('accepts any non-blank categoryCode when the category list is not available', () => {
    expect(createPolicyConfigSchema().safeParse(validPolicy).success).toBe(true)
  })

  it('rejects a missing currency with the currency message', () => {
    const errors = validatePolicyConfig({
      categoryCode: validPolicy.categoryCode,
      receiptThresholdAmount: validPolicy.receiptThresholdAmount,
      active: validPolicy.active,
    })
    expect(errors.currency).toBe(POLICY_CONFIG_MESSAGES.currency)
    expect(errors).not.toHaveProperty('categoryCode')
  })

  it('reports every failing field once, keyed by field name (sorted keys)', () => {
    const errors = validatePolicyConfig({
      categoryCode: '',
      receiptThresholdAmount: -1,
      currency: '',
    })
    expect(Object.keys(errors).sort()).toEqual([
      'categoryCode',
      'currency',
      'receiptThresholdAmount',
    ])
    expect(errors.categoryCode).toBe(POLICY_CONFIG_MESSAGES.categoryCode)
    expect(errors.receiptThresholdAmount).toBe(POLICY_CONFIG_MESSAGES.receiptThresholdNonNegative)
    expect(errors.currency).toBe(POLICY_CONFIG_MESSAGES.currency)
  })
})

describe('expenseCategorySchema (L-04 / J-09)', () => {
  it('accepts a valid category (no field errors, successful parse)', () => {
    expect(validateExpenseCategory(validCategory)).toEqual({})
    expect(expenseCategorySchema.safeParse(validCategory).success).toBe(true)
  })

  it('accepts a category with active omitted (server defaults it to true)', () => {
    const values = { code: validCategory.code, label: validCategory.label }
    expect(validateExpenseCategory(values)).toEqual({})
    expect(expenseCategorySchema.safeParse(values).success).toBe(true)
  })

  it('rejects a missing code with the code message', () => {
    const errors = validateExpenseCategory({
      label: validCategory.label,
      active: validCategory.active,
    })
    expect(errors.code).toBe(EXPENSE_CATEGORY_MESSAGES.code)
    expect(errors).not.toHaveProperty('label')
  })

  it('rejects a whitespace-only code with the code message', () => {
    const errors = validateExpenseCategory(makeCategory({ code: '  ' }))
    expect(errors.code).toBe(EXPENSE_CATEGORY_MESSAGES.code)
    expect(errors).not.toHaveProperty('label')
  })

  it('rejects a duplicate code on create', () => {
    const errors = validateExpenseCategory(makeCategory(), ['MEALS', 'TRAVEL'])
    expect(errors.code).toBe(EXPENSE_CATEGORY_MESSAGES.codeUnique)
  })

  it('allows an unchanged code on edit but rejects another category code', () => {
    expect(validateExpenseCategory(makeCategory(), ['MEALS', 'TRAVEL'], 'MEALS')).toEqual({})
    expect(
      validateExpenseCategory(makeCategory({ code: 'TRAVEL' }), ['MEALS', 'TRAVEL'], 'MEALS').code,
    ).toBe(EXPENSE_CATEGORY_MESSAGES.codeUnique)
    expect(createExpenseCategorySchema(['MEALS'], 'MEALS').safeParse(validCategory).success).toBe(
      true,
    )
  })

  it('rejects a missing label with the label message', () => {
    const errors = validateExpenseCategory({
      code: validCategory.code,
      active: validCategory.active,
    })
    expect(errors.label).toBe(EXPENSE_CATEGORY_MESSAGES.label)
    expect(errors).not.toHaveProperty('code')
  })

  it('rejects a blank label; the adapter reports both blanks and maps null to no errors', () => {
    const errors = validateExpenseCategory(makeCategory({ label: '  ' }))
    expect(errors.label).toBe(EXPENSE_CATEGORY_MESSAGES.label)
    expect(errors).not.toHaveProperty('code')

    const multiErrors = validateExpenseCategory({ code: '', label: '' })
    expect(Object.keys(multiErrors).sort()).toEqual(['code', 'label'])
    expect(multiErrors.code).toBe(EXPENSE_CATEGORY_MESSAGES.code)
    expect(multiErrors.label).toBe(EXPENSE_CATEGORY_MESSAGES.label)

    expect(validateExpenseCategory(null)).toEqual({})
  })
})
