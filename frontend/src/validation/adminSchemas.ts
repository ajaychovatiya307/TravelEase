// Admin resource validation schemas (L-04).
// Client-side rule-sets for the four Admin forms (P-16–P-19), covering the
// form fields and conditional rules in ui-model.md §4 and the J-06–J-09
// acceptance criteria (frontend-tasks.md §J):
//
//   userAccountSchema             UserAccountForm (P-16) — fullName and email
//                                 required; email format validated
//                                 client-side; roles requires at least one
//                                 value (J-06)
//   employeeManagerMappingSchema  EmployeeManagerMappingForm (P-17) —
//                                 employeeId/effectiveFrom required;
//                                 managerId nullable — top-of-hierarchy
//                                 managers may be submitted empty (J-07, Q3)
//   policyConfigSchema            PolicyConfigForm (P-18) — required fields
//                                 enforced; receiptThresholdAmount validated
//                                 as non-negative numeric (J-08)
//   expenseCategorySchema         ExpenseCategoryForm (P-19) — code/label
//                                 required (J-09)
//
// Built as Zod schemas (zod installed per A-02) so Section J forms can plug
// them straight into react-hook-form via @hookform/resolvers' zodResolver.
// Each schema validates form values in the request-DTO shape
// (models/admin.ts, E-04); the form layer owns input parsing (threshold
// decimal string -> number, date -> YYYY-MM-DD, per frontend-api-mapping.md
// TR-06) before values reach the schema.
//
// Existence/uniqueness rules — email uniqueness (FR-ADM-01), employee/manager
// user references, categoryCode referencing an existing ExpenseCategory.code
// (ui-model.md §4), and code uniqueness — are Service-layer business rules;
// presence and format are the client-side checks and the server remains
// authoritative (frontend-constitution.md §8 two-tier model).
//
// Each validate*() helper returns the first error per field name — the same
// field-keyed shape FormField (`error?: string`) and the server-field-error
// mapping path (frontend-api-mapping.md §3) consume, so client and server
// errors render through the identical inline path.

import { z } from 'zod'
import type {
  EmployeeManagerMappingRequest,
  ExpenseCategoryRequest,
  PolicyConfigRequest,
  RoleType,
  UserAccountCreateRequest,
  UserAccountRequest,
} from '../models/admin'

/** The four RoleType values (api-contract.md §2; canonical union in E-04). */
const ROLE_VALUES: [RoleType, ...RoleType[]] = ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN']

/**
 * Shared named rule: a required, non-blank string (frontend-plan.md §10 —
 * presence constraints are named rules importable by any form, not
 * re-implemented per screen).
 */
function requiredNonBlankString(message: string): z.ZodString {
  return z.string({ error: message }).refine((value) => value.trim().length > 0, { error: message })
}

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** True for canonical YYYY-MM-DD strings that are real calendar dates. */
function isValidIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  )
}

/**
 * Practical client-side email shape check (local-part@domain.tld); the server
 * remains authoritative for full email validity (FR-ADM-01).
 */
function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

// ---------------------------------------------------------------------------
// User Account form (P-16) — L-04 / J-06
// ---------------------------------------------------------------------------

/**
 * Field-specific, user-facing error messages for the User Account form
 * (P-16). Exported so forms and tests can reference the exact copy per field.
 */
export const USER_ACCOUNT_MESSAGES = {
  fullName: 'Enter the full name of the user.',
  email: 'Enter the email address of the user.',
  emailFormat: 'Enter a valid email address.',
  password: 'Enter a password between 8 and 72 characters.',
  roles: 'Select at least one role for this user.',
  active: 'The active status must be true or false.',
} as const

/**
 * The named rule-set for the User Account form (P-16; J-06, ui-model.md §4):
 * fullName and email required (email format checked client-side), roles
 * requires at least one value; active optional (server defaults it to true).
 *
 * Pass to `zodResolver` (react-hook-form) or use `validateUserAccount` for
 * direct field-error mapping.
 */
export const userAccountSchema = z.object({
  fullName: requiredNonBlankString(USER_ACCOUNT_MESSAGES.fullName),
  email: z
    .string({ error: USER_ACCOUNT_MESSAGES.email })
    .refine((value) => value.trim().length > 0, { error: USER_ACCOUNT_MESSAGES.email })
    .refine((value) => isValidEmail(value.trim()), { error: USER_ACCOUNT_MESSAGES.emailFormat }),
  // ui-model.md §4: roles is a multi-select requiring at least one value.
  roles: z
    .array(z.enum(ROLE_VALUES, { error: USER_ACCOUNT_MESSAGES.roles }), {
      error: USER_ACCOUNT_MESSAGES.roles,
    })
    .nonempty(USER_ACCOUNT_MESSAGES.roles),
  active: z.boolean({ error: USER_ACCOUNT_MESSAGES.active }).optional(),
})

/** Password is required only when creating an account, never when editing it. */
export const userAccountCreateSchema = userAccountSchema.extend({
  password: requiredNonBlankString(USER_ACCOUNT_MESSAGES.password).refine(
    (value) => value.length >= 8 && value.length <= 72,
    { error: USER_ACCOUNT_MESSAGES.password },
  ),
})

/** Edit-mode password value is inert, keeping form values consistently typed. */
export const userAccountEditSchema = userAccountSchema.extend({
  password: z.string(),
})

/** Field names userAccountSchema can emit errors for (the form value keys). */
const USER_ACCOUNT_FIELDS = ['fullName', 'email', 'roles', 'active'] as const

/** First error per field, keyed by field name (empty object when valid). */
export type UserAccountFieldErrors = Partial<
  Record<keyof UserAccountRequest | keyof Pick<UserAccountCreateRequest, 'password'>, string>
>

/**
 * Convenience entry point mirroring the server `fieldErrors[]` contract:
 * validates `values` against userAccountSchema and returns the first error
 * message per field name, so the result can be applied to FormField `error`
 * props / the form's field-error state exactly like server field errors
 * (frontend-api-mapping.md §3/§4).
 */
export function validateUserAccount(values: unknown): UserAccountFieldErrors {
  const result = userAccountSchema.safeParse(values)
  if (result.success) return {}
  const errors: UserAccountFieldErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (
      typeof field === 'string' &&
      USER_ACCOUNT_FIELDS.includes(field as (typeof USER_ACCOUNT_FIELDS)[number]) &&
      !(field in errors)
    ) {
      errors[field as keyof UserAccountRequest] = issue.message
    }
  }
  return errors
}

// ---------------------------------------------------------------------------
// Employee-Manager Mapping form (P-17) — L-04 / J-07
// ---------------------------------------------------------------------------

/**
 * Field-specific, user-facing error messages for the Employee-Manager
 * Mapping form (P-17). Exported so forms and tests can reference the exact
 * copy per field.
 */
export const EMPLOYEE_MANAGER_MAPPING_MESSAGES = {
  employeeId: 'Select the employee for this mapping.',
  managerId: 'The manager must be a user identifier.',
  effectiveFrom: 'Enter the date the mapping takes effect.',
  effectiveFromFormat: 'Enter a valid date.',
} as const

/**
 * The named rule-set for the Employee-Manager Mapping form (P-17; J-07,
 * ui-model.md §4): employeeId and effectiveFrom required; managerId nullable
 * — top-of-hierarchy managers have no manager and their approvals fall back
 * to Finance (Q3, FR-TR-05, models/admin.ts), so an empty value is a valid
 * submission (J-07).
 *
 * Pass to `zodResolver` (react-hook-form) or use
 * `validateEmployeeManagerMapping` for direct field-error mapping.
 */
export const employeeManagerMappingSchema = z.object({
  employeeId: requiredNonBlankString(EMPLOYEE_MANAGER_MAPPING_MESSAGES.employeeId),
  // Nullable: the picker may be left empty for top-of-hierarchy managers.
  managerId: z.string({ error: EMPLOYEE_MANAGER_MAPPING_MESSAGES.managerId }).nullish(),
  // Contract `date` type (ISO 8601, api-contract.md §3.2); the form submits
  // canonical YYYY-MM-DD (frontend-api-mapping.md TR-06).
  effectiveFrom: z
    .string({ error: EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFrom })
    .refine((value) => value.trim().length > 0, {
      error: EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFrom,
    })
    .refine((value) => isValidIsoDate(value.trim()), {
      error: EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFromFormat,
    }),
})

/**
 * Field names employeeManagerMappingSchema can emit errors for (the form
 * value keys).
 */
const EMPLOYEE_MANAGER_MAPPING_FIELDS = ['employeeId', 'managerId', 'effectiveFrom'] as const

/** First error per field, keyed by field name (empty object when valid). */
export type EmployeeManagerMappingFieldErrors = Partial<
  Record<keyof EmployeeManagerMappingRequest, string>
>

/**
 * Convenience entry point mirroring the server `fieldErrors[]` contract:
 * validates `values` against employeeManagerMappingSchema and returns the
 * first error message per field name, so the result can be applied to
 * FormField `error` props / the form's field-error state exactly like server
 * field errors (frontend-api-mapping.md §3/§4).
 */
export function validateEmployeeManagerMapping(values: unknown): EmployeeManagerMappingFieldErrors {
  const result = employeeManagerMappingSchema.safeParse(values)
  if (result.success) return {}
  const errors: EmployeeManagerMappingFieldErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (
      typeof field === 'string' &&
      EMPLOYEE_MANAGER_MAPPING_FIELDS.includes(
        field as (typeof EMPLOYEE_MANAGER_MAPPING_FIELDS)[number],
      ) &&
      !(field in errors)
    ) {
      errors[field as keyof EmployeeManagerMappingRequest] = issue.message
    }
  }
  return errors
}

// ---------------------------------------------------------------------------
// Policy Config form (P-18) — L-04 / J-08
// ---------------------------------------------------------------------------

/**
 * Field-specific, user-facing error messages for the Policy Config form
 * (P-18). Exported so forms and tests can reference the exact copy per field.
 */
export const POLICY_CONFIG_MESSAGES = {
  categoryCode: 'Enter the expense category code this policy applies to.',
  categoryCodeUnknown: 'Select an existing expense category.',
  receiptThresholdAmount: 'Enter the receipt threshold amount.',
  receiptThresholdNonNegative: 'The receipt threshold amount must be zero or greater.',
  currency: 'Enter the currency for this policy.',
  active: 'The active status must be true or false.',
} as const

/**
 * The named rule-set for the Policy Config form (P-18; J-08, ui-model.md
 * §4): all fields required; receiptThresholdAmount must be a non-negative
 * number (VAL-10 source of truth per models/admin.ts). Whether categoryCode
 * references an existing ExpenseCategory.code and whether currency matches
 * the base currency (NFR-05) are Service-layer rules — presence is the
 * client-side check.
 *
 * Pass to `zodResolver` (react-hook-form) or use `validatePolicyConfig` for
 * direct field-error mapping.
 */
export function createPolicyConfigSchema(existingCategoryCodes?: readonly string[]) {
  const categoryCodes =
    existingCategoryCodes === undefined ? undefined : new Set(existingCategoryCodes.map((code) => code.trim()))

  return z
    .object({
      categoryCode: requiredNonBlankString(POLICY_CONFIG_MESSAGES.categoryCode),
      receiptThresholdAmount: z
        .number({ error: POLICY_CONFIG_MESSAGES.receiptThresholdAmount })
        .nonnegative(POLICY_CONFIG_MESSAGES.receiptThresholdNonNegative),
      currency: requiredNonBlankString(POLICY_CONFIG_MESSAGES.currency),
      active: z.boolean({ error: POLICY_CONFIG_MESSAGES.active }).optional(),
    })
    .superRefine((values, ctx) => {
      const categoryCode = values.categoryCode.trim()
      if (categoryCode && categoryCodes !== undefined && !categoryCodes.has(categoryCode)) {
        ctx.addIssue({
          code: 'custom',
          path: ['categoryCode'],
          message: POLICY_CONFIG_MESSAGES.categoryCodeUnknown,
        })
      }
    })
}

export const policyConfigSchema = createPolicyConfigSchema()

/** Field names policyConfigSchema can emit errors for (the form value keys). */
const POLICY_CONFIG_FIELDS = [
  'categoryCode',
  'receiptThresholdAmount',
  'currency',
  'active',
] as const

/** First error per field, keyed by field name (empty object when valid). */
export type PolicyConfigFieldErrors = Partial<Record<keyof PolicyConfigRequest, string>>

/**
 * Convenience entry point mirroring the server `fieldErrors[]` contract:
 * validates `values` against policyConfigSchema and returns the first error
 * message per field name, so the result can be applied to FormField `error`
 * props / the form's field-error state exactly like server field errors
 * (frontend-api-mapping.md §3/§4).
 */
export function validatePolicyConfig(
  values: unknown,
  existingCategoryCodes?: readonly string[],
): PolicyConfigFieldErrors {
  const result = createPolicyConfigSchema(existingCategoryCodes).safeParse(values)
  if (result.success) return {}
  const errors: PolicyConfigFieldErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (
      typeof field === 'string' &&
      POLICY_CONFIG_FIELDS.includes(field as (typeof POLICY_CONFIG_FIELDS)[number]) &&
      !(field in errors)
    ) {
      errors[field as keyof PolicyConfigRequest] = issue.message
    }
  }
  return errors
}

// ---------------------------------------------------------------------------
// Expense Category form (P-19) — L-04 / J-09
// ---------------------------------------------------------------------------

/**
 * Field-specific, user-facing error messages for the Expense Category form
 * (P-19). Exported so forms and tests can reference the exact copy per field.
 */
export const EXPENSE_CATEGORY_MESSAGES = {
  code: 'Enter the category code.',
  codeUnique: 'This category code is already in use.',
  label: 'Enter the category label.',
  active: 'The active status must be true or false.',
} as const

/**
 * The named rule-set for the Expense Category form (P-19; J-09, ui-model.md
 * §4): code and label required; active optional (server defaults it to true).
 * code uniqueness is a Service-layer business rule — presence is the
 * client-side check.
 *
 * Pass to `zodResolver` (react-hook-form) or use `validateExpenseCategory`
 * for direct field-error mapping.
 */
export function createExpenseCategorySchema(
  existingCategoryCodes?: readonly string[],
  currentCategoryCode?: string,
) {
  const categoryCodes =
    existingCategoryCodes === undefined ? undefined : new Set(existingCategoryCodes.map((code) => code.trim()))
  const currentCode = currentCategoryCode?.trim()

  return z
    .object({
      code: requiredNonBlankString(EXPENSE_CATEGORY_MESSAGES.code),
      label: requiredNonBlankString(EXPENSE_CATEGORY_MESSAGES.label),
      active: z.boolean({ error: EXPENSE_CATEGORY_MESSAGES.active }).optional(),
    })
    .superRefine((values, ctx) => {
      const code = values.code.trim()
      if (code && code !== currentCode && categoryCodes?.has(code)) {
        ctx.addIssue({
          code: 'custom',
          path: ['code'],
          message: EXPENSE_CATEGORY_MESSAGES.codeUnique,
        })
      }
    })
}

export const expenseCategorySchema = createExpenseCategorySchema()

/** Field names expenseCategorySchema can emit errors for (the form value keys). */
const EXPENSE_CATEGORY_FIELDS = ['code', 'label', 'active'] as const

/** First error per field, keyed by field name (empty object when valid). */
export type ExpenseCategoryFieldErrors = Partial<Record<keyof ExpenseCategoryRequest, string>>

/**
 * Convenience entry point mirroring the server `fieldErrors[]` contract:
 * validates `values` against expenseCategorySchema and returns the first
 * error message per field name, so the result can be applied to FormField
 * `error` props / the form's field-error state exactly like server field
 * errors (frontend-api-mapping.md §3/§4).
 */
export function validateExpenseCategory(
  values: unknown,
  existingCategoryCodes?: readonly string[],
  currentCategoryCode?: string,
): ExpenseCategoryFieldErrors {
  const result = createExpenseCategorySchema(existingCategoryCodes, currentCategoryCode).safeParse(
    values,
  )
  if (result.success) return {}
  const errors: ExpenseCategoryFieldErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (
      typeof field === 'string' &&
      EXPENSE_CATEGORY_FIELDS.includes(field as (typeof EXPENSE_CATEGORY_FIELDS)[number]) &&
      !(field in errors)
    ) {
      errors[field as keyof ExpenseCategoryRequest] = issue.message
    }
  }
  return errors
}
