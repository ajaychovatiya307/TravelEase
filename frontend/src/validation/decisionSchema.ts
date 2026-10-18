// Decision validation schema (L-02).
// Shared conditional client-side rule-set for the decision + reason fields,
// covering frontend-spec.md §7 rule VAL-12:
//
//   VAL-12  reason  required and non-blank only when decision = REJECTED;
//                   no reason requirement when decision = APPROVED
//
// Shared by both review screens, which send the same { decision, reason? }
// shape: the travel ApprovalDecisionForm (J-02, `ApprovalDecisionRequest`,
// api-contract.md §5.4, E-01) and the expense ExpenseReviewForm (J-04,
// `ExpenseReviewRequest`, api-contract.md §5.10, E-03), each carrying
// `decision: DecisionType` and an optional `reason`.
//
// Built as a Zod schema (zod installed per A-02) so Section J forms can plug
// it straight into react-hook-form via @hookform/resolvers' zodResolver.
// The VAL-12 condition is internal to the values (decision + reason), so the
// schema is a single exported const — no conditional inputs to thread
// through. The server remains authoritative (frontend-constitution.md §8
// two-tier model).
//
// validateDecision() returns the first error per field name — the same
// field-keyed shape FormField (`error?: string`) and the server-field-error
// mapping path (frontend-api-mapping.md §3) consume, so client and server
// errors render through the identical inline path.

import { z } from 'zod'
import type { ApprovalDecisionRequest, DecisionType } from '../models/travelRequest'

/**
 * Form values for the decision + reason fields — the exact shared shape of
 * both `ApprovalDecisionRequest` (api-contract.md §5.4) and
 * `ExpenseReviewRequest` (api-contract.md §5.10).
 */
export interface DecisionFormValues {
  /** VAL-12 context: the approver's decision. */
  decision: DecisionType
  /** VAL-12: required and non-blank only when decision is REJECTED. */
  reason?: string
}

/**
 * Field-specific, user-facing error messages (no raw/technical text).
 * Exported so forms and tests can reference the exact copy per field.
 */
export const DECISION_MESSAGES = {
  decision: 'Select a decision.',
  reason: 'Enter a reason for the rejection.',
} as const

export const REJECT_REASON_REQUIRED_MESSAGE = DECISION_MESSAGES.reason

/**
 * The named shared decision rule-set (VAL-12).
 *
 * Pass to `zodResolver` (react-hook-form) or use `validateDecision` for
 * direct field-error mapping.
 */
export const decisionSchema = z
  .object({
    // VAL-12 context: decision must be one of the DecisionType values
    // (missing or any other value rejected with the field message).
    decision: z.enum(['APPROVED', 'REJECTED'], { error: DECISION_MESSAGES.decision }),
    // VAL-12: always optional at the schema level — the requirement is
    // decision-conditional and applied in the refine below.
    reason: z.string({ error: DECISION_MESSAGES.reason }).optional(),
  })

  .superRefine((value, ctx) => {
    // VAL-12: reason required and non-blank only when REJECTED. Skipped for
    // APPROVED (no reason requirement) and when decision failed its own
    // rule (no cascading errors).
    if (value.decision !== 'REJECTED') return
    if (value.reason === undefined || value.reason.trim().length === 0) {
      ctx.addIssue({
        code: 'custom',
        path: ['reason'],
        message: DECISION_MESSAGES.reason,
      })
    }
  })

export const decisionFormSchema = decisionSchema

export type ApprovalDecisionFormValues = DecisionFormValues

export function toApprovalDecisionRequest(
  values: ApprovalDecisionFormValues,
): ApprovalDecisionRequest {
  const request: ApprovalDecisionRequest = { decision: values.decision }
  const reason = values.reason?.trim()
  if (values.decision === 'REJECTED' && reason) {
    request.reason = reason
  }
  return request
}

/** Field names the schema can emit errors for (the form value keys). */
const DECISION_FIELDS = ['decision', 'reason'] as const

/** First error per field, keyed by field name (empty object when valid). */
export type DecisionFieldErrors = Partial<Record<keyof DecisionFormValues, string>>

/**
 * Convenience entry point mirroring the server `fieldErrors[]` contract:
 * validates `values` against the schema and returns the first error message
 * per field name, so the result can be applied to FormField `error` props /
 * the form's field-error state exactly like server field errors
 * (frontend-api-mapping.md §3/§4).
 */
export function validateDecision(values: unknown): DecisionFieldErrors {
  const result = decisionSchema.safeParse(values)
  if (result.success) return {}
  const errors: DecisionFieldErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (
      typeof field === 'string' &&
      DECISION_FIELDS.includes(field as (typeof DECISION_FIELDS)[number]) &&
      !(field in errors)
    ) {
      errors[field as keyof DecisionFormValues] = issue.message
    }
  }
  return errors
}
