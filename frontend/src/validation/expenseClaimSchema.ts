// Expense claim validation schema (L-03).
// Named client-side rule-set for the New/Edit Expense Claim form (P-11/P-12),
// covering frontend-spec.md §7 rules VAL-05 through VAL-10:
//
//   VAL-05  amount            required; must be > 0
//   VAL-06  currency          required (non-blank)
//   VAL-07  category          required; must be an active category (checked
//                             against the picker's codes when supplied)
//   VAL-08  expenseDate       required; valid calendar date; must fall within
//                             the linked travel request's date range (checked
//                             when the range is supplied)
//   VAL-09  travelRequestId   required (non-blank); the form restricts the
//                             picker to the employee's own Approved trips
//                             (BR-02), so presence is the client-side check
//   VAL-10  documentIds       required when amount exceeds the category
//                             receipt threshold (checked when the threshold
//                             is supplied); optional otherwise
//
// Built as a Zod schema (zod installed per A-02) so Section J forms can plug
// it straight into react-hook-form via @hookform/resolvers' zodResolver. The
// schema validates form values in the request-DTO shape
// (ExpenseClaimCreateRequest / ExpenseClaimUpdateRequest, E-03); the form
// layer owns input parsing (amount string -> number, date -> YYYY-MM-DD,
// per frontend-api-mapping.md TR-06) before values reach the schema.
//
// Conditional inputs (trip date range, receipt threshold, active category
// codes) arrive via ExpenseClaimValidationOptions — the form fetches them
// from the linked travel request and the active PolicyConfig
// (frontend-spec.md §4.9). When an option is absent the corresponding
// conditional check is skipped; the server remains authoritative
// (frontend-constitution.md §8 two-tier model).
//
// validateExpenseClaim() returns the first error per field name — the same
// field-keyed shape FormField (`error?: string`) and the server-field-error
// mapping path (frontend-api-mapping.md §3) consume, so client and server
// errors render through the identical inline path.

import { z } from 'zod'

/**
 * Form values for the Expense Claim Create/Edit form — the exact
 * ExpenseClaimCreateRequest / ExpenseClaimUpdateRequest shape (E-03), with
 * `documentIds` always present because the upload field tracks an array that
 * starts empty (frontend-plan.md §9).
 */
export interface ExpenseClaimFormValues {
  /** VAL-09: id of the employee's own Approved travel request. */
  travelRequestId: string
  /** VAL-05: claim amount in the base currency; must be > 0. */
  amount: number
  /** VAL-06: ISO 4217 currency code. */
  currency: string
  /** VAL-07: active ExpenseCategory code. */
  category: string
  /** VAL-08: ISO 8601 calendar date (YYYY-MM-DD) the expense occurred. */
  expenseDate: string
  /** VAL-10: uploaded receipt/supporting document ids. */
  documentIds: string[]
}

/**
 * Conditional validation inputs the form supplies from fetched data
 * (frontend-spec.md §4.9, frontend-api-mapping.md §3). Every option is
 * optional: when absent the corresponding rule degrades to a presence-only
 * check and the server enforces the full rule.
 */
export interface ExpenseClaimValidationOptions {
  /**
   * The linked travel request's trip range (ISO 8601 YYYY-MM-DD dates).
   * When supplied, expenseDate must fall within it, boundaries inclusive
   * (VAL-08; VAL-T-11 boundary dates accepted). Skipped when the trip is
   * not (yet) selected/fetched or its dates are not valid ISO dates.
   */
  travelRequestDateRange?: {
    startDate: string
    endDate: string
  }
  /**
   * The selected category's active receipt threshold
   * (PolicyConfig.receiptThresholdAmount). When supplied and finite,
   * documentIds must be non-empty when amount strictly exceeds it
   * (VAL-10; VAL-T-14: amount exactly at the threshold needs no document;
   * VAL-T-15: one unit above does). Documents are optional without it.
   */
  receiptThresholdAmount?: number
  /**
   * The active category codes currently offered by the category picker
   * (BR-04). When supplied, category must be one of them (VAL-07);
   * otherwise only presence is checked client-side.
   */
  activeCategoryCodes?: readonly string[]
}

/**
 * Field-specific, user-facing error messages (no raw/technical text).
 * Exported so forms and tests can reference the exact copy per field.
 */
export const EXPENSE_CLAIM_MESSAGES = {
  travelRequestId: 'Select the approved travel request this expense is for.',
  amountRequired: 'Enter the expense amount.',
  amountPositive: 'The expense amount must be greater than zero.',
  currency: 'Enter the currency for this expense.',
  categoryRequired: 'Select an expense category.',
  categoryActive: 'Select a valid, active expense category.',
  expenseDateRequired: 'Enter the date the expense occurred.',
  expenseDateInvalid: 'Enter a valid date.',
  expenseDateInRange: 'The expense date must fall within the travel request trip dates.',
  documentIds:
    'Attach at least one document — amounts above the receipt threshold require a receipt.',
} as const

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** True for canonical YYYY-MM-DD strings that are real calendar dates. */
export function isValidIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  )
}

/**
 * Shared named rule: a required, non-blank string (frontend-plan.md §10 —
 * presence constraints are named rules importable by any form, not
 * re-implemented per screen).
 */
function requiredNonBlankString(message: string): z.ZodString {
  return z.string({ error: message }).refine((value) => value.trim().length > 0, { error: message })
}

/** Lexicographic range test — exact for canonical YYYY-MM-DD strings. */
function isIsoDateWithinRange(
  value: string,
  range: { startDate: string; endDate: string },
): boolean {
  if (!isValidIsoDate(range.startDate) || !isValidIsoDate(range.endDate)) return true
  return value >= range.startDate && value <= range.endDate
}

/**
 * The named expense-claim rule-set (VAL-05 through VAL-10).
 *
 * Re-create per form instance with the current fetched options; pass the
 * result to `zodResolver` (react-hook-form) or use `validateExpenseClaim`
 * for direct field-error mapping.
 */
export function expenseClaimSchema(options: ExpenseClaimValidationOptions = {}) {
  const { travelRequestDateRange, receiptThresholdAmount, activeCategoryCodes } = options

  return z
    .object({
      // VAL-09: link required; the picker only lists the employee's own
      // Approved trips, so presence is the client-side check.
      travelRequestId: requiredNonBlankString(EXPENSE_CLAIM_MESSAGES.travelRequestId),
      // VAL-05: required; must be > 0 (VAL-T-07: zero rejected; VAL-T-08:
      // smallest positive unit accepted).
      amount: z
        .number({ error: EXPENSE_CLAIM_MESSAGES.amountRequired })
        .positive(EXPENSE_CLAIM_MESSAGES.amountPositive),
      // VAL-06: required (VAL-T-09: blank currency rejected).
      currency: requiredNonBlankString(EXPENSE_CLAIM_MESSAGES.currency),
      // VAL-07: required; must be an active category when codes supplied
      // (VAL-T-10: inactive/unknown category rejected).
      category: z
        .string({ error: EXPENSE_CLAIM_MESSAGES.categoryRequired })
        .refine((value) => value.trim().length > 0, {
          error: EXPENSE_CLAIM_MESSAGES.categoryRequired,
        })
        .refine(
          (value) =>
            activeCategoryCodes === undefined || activeCategoryCodes.includes(value.trim()),
          { error: EXPENSE_CLAIM_MESSAGES.categoryActive },
        ),
      // VAL-08: required; valid date; within the trip range when supplied
      // (VAL-T-11: boundaries accepted; VAL-T-12: one day outside rejected).
      expenseDate: z
        .string({ error: EXPENSE_CLAIM_MESSAGES.expenseDateRequired })
        .refine((value) => value.trim().length > 0, {
          error: EXPENSE_CLAIM_MESSAGES.expenseDateRequired,
        })
        .refine((value) => isValidIsoDate(value.trim()), {
          error: EXPENSE_CLAIM_MESSAGES.expenseDateInvalid,
        })
        .refine(
          (value) =>
            travelRequestDateRange === undefined ||
            isIsoDateWithinRange(value.trim(), travelRequestDateRange),
          { error: EXPENSE_CLAIM_MESSAGES.expenseDateInRange },
        ),
      // VAL-10: threshold-conditional — applied cross-field in superRefine.
      documentIds: z.array(z.string({ error: 'Document entries must be document identifiers.' })),
    })
    .superRefine((value, ctx) => {
      // VAL-10: document required when amount exceeds the category
      // threshold. Skipped when no finite threshold is supplied, when
      // amount failed VAL-05 (no cascading errors), or when amount does not
      // strictly exceed the threshold (VAL-T-14).
      const threshold = receiptThresholdAmount
      if (typeof threshold !== 'number' || !Number.isFinite(threshold)) return
      if (!Number.isFinite(value.amount)) return
      if (value.amount <= threshold) return
      const documentIds = Array.isArray(value.documentIds) ? value.documentIds : []
      if (documentIds.length === 0) {
        ctx.addIssue({
          code: 'custom',
          path: ['documentIds'],
          message: EXPENSE_CLAIM_MESSAGES.documentIds,
        })
      }
    })
}

/** Field names the schema can emit errors for (the form value keys). */
const EXPENSE_CLAIM_FIELDS = [
  'travelRequestId',
  'amount',
  'currency',
  'category',
  'expenseDate',
  'documentIds',
] as const

/** First error per field, keyed by field name (empty object when valid). */
export type ExpenseClaimFieldErrors = Partial<Record<keyof ExpenseClaimFormValues, string>>

/**
 * Convenience entry point mirroring the server `fieldErrors[]` contract:
 * validates `values` against the schema and returns the first error message
 * per field name, so the result can be applied to FormField `error` props /
 * the form's field-error state exactly like server field errors
 * (frontend-api-mapping.md §3/§4).
 */
export function validateExpenseClaim(
  values: unknown,
  options?: ExpenseClaimValidationOptions,
): ExpenseClaimFieldErrors {
  const result = expenseClaimSchema(options).safeParse(values)
  if (result.success) return {}
  const errors: ExpenseClaimFieldErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (
      typeof field === 'string' &&
      EXPENSE_CLAIM_FIELDS.includes(field as (typeof EXPENSE_CLAIM_FIELDS)[number]) &&
      !(field in errors)
    ) {
      errors[field as keyof ExpenseClaimFormValues] = issue.message
    }
  }
  return errors
}
