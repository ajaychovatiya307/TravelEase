// Finance Approve/Reject decision form for the expense review screen (J-04,
// frontend-tasks.md; P-13, hosted by the I-12 ExpenseReviewPage).
//
// Mirrors J-02 (ApprovalDecisionForm) for the expense resource, per
// frontend-spec.md §4.10:
// - Decision control: Approve / Reject (shared SelectField chrome, C-07);
//   selecting Reject reveals the reason field, which then becomes required
//   (frontend-clarification.md Q16).
// - the shared L-02 rule-set (validation/decisionSchema.ts — VAL-12: reason
//   required and non-blank only when decision = REJECTED) plugs into
//   react-hook-form via @hookform/resolvers' zodResolver; a blank
//   (whitespace-only) reason blocks the submit when rejecting.
// - the pre-submit confirmation dialog (frontend-spec.md §4.10,
//   frontend-clarification.md Q13) belongs to the HOST PAGE, not this form:
//   I-12 wraps the submit in the shared ConfirmDialog, confirms it, and then
//   calls `expenseService.review` (POST /api/expenses/{id}/review,
//   frontend-api-mapping.md §1.2). This component stays purely presentational
//   + RHF — no service/router imports (frontend-constitution.md §3) — and
//   hands the validated payload to the page through `onSubmit()`.
// - a valid submit maps the form values to the request DTO through the
//   dedicated `toExpenseReviewRequest()` mapping (frontend-plan.md §8,
//   frontend-api-mapping.md §2 TR-06; `reason` trimmed and omitted when
//   APPROVED per api-contract.md §5.10) before invoking `onSubmit()`. A
//   rejected promise leaves the form open, mirroring ExpenseClaimForm's
//   contract (J-03).
// - server field errors (400 VALIDATION_ERROR `fieldErrors[]`) render through
//   the identical inline path via `serverFieldErrors`
//   (frontend-api-mapping.md §3/§4); server messages win over client messages
//   for the same field.

import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

import './ExpenseReviewForm.css'

import type { DecisionType } from '../../models/travelRequest'
import type { ExpenseReviewRequest } from '../../models/expenseClaim'
import { SelectField, TextareaField } from '../../shared/FormField'
import { decisionSchema } from '../../validation/decisionSchema'
import type { DecisionFieldErrors, DecisionFormValues } from '../../validation/decisionSchema'

/** Stable field ids (label/input/error association via the shared FormField chrome, C-07). */
const FIELD_IDS = {
  decision: 'expense-review-form-decision',
  reason: 'expense-review-form-reason',
} as const

/** Decision control options (frontend-spec.md §4.10: "Approve / Reject"). */
const DECISION_OPTIONS = [
  { value: 'APPROVED', label: 'Approve' },
  { value: 'REJECTED', label: 'Reject' },
]

/**
 * Dedicated form-values → request-DTO mapping (frontend-plan.md §8;
 * frontend-api-mapping.md §2 TR-06): `reason` trimmed and included only for a
 * REJECTED decision — omitted when APPROVED (api-contract.md §5.10, TR-01
 * omission convention).
 */
function toExpenseReviewRequest(values: DecisionFormValues): ExpenseReviewRequest {
  const request: ExpenseReviewRequest = { decision: values.decision }
  const reason = values.reason?.trim()
  if (values.decision === 'REJECTED' && reason && reason.length > 0) {
    request.reason = reason
  }
  return request
}

export interface ExpenseReviewFormProps {
  /**
   * Called once client-side validation (VAL-12) passes, with the mapped
   * `ExpenseReviewRequest` DTO (api-contract.md §5.10). The host page (I-12)
   * owns the surrounding flow — the pre-submit confirmation dialog (Q13,
   * shared ConfirmDialog), the `expenseService.review` call
   * (POST /api/expenses/{id}/review, frontend-api-mapping.md §1.2), and the
   * post-submit navigation (frontend-constitution.md §3). A rejected promise
   * leaves the form open for corrections, mirroring ExpenseClaimForm (J-03).
   */
  onSubmit: (payload: ExpenseReviewRequest) => Promise<void>
  /**
   * Server field errors (400 VALIDATION_ERROR `fieldErrors[]`) mapped by the
   * page onto the identical inline field-error path (frontend-api-mapping.md
   * §3/§4). Server messages win over client messages for the same field.
   */
  serverFieldErrors?: DecisionFieldErrors
}

/**
 * Renders the P-13 Finance decision form (frontend-spec.md §4.10): a
 * single-step Approve/Reject control with the Q16 conditional reason field.
 * The selected decision lives in component state (it drives the reason
 * field's visibility) and is mirrored into the RHF field as the submit-DTO
 * source — mirroring ExpenseClaimForm's category pattern (J-03).
 */
export function ExpenseReviewForm({ onSubmit, serverFieldErrors }: ExpenseReviewFormProps) {
  const [decision, setDecision] = useState<'' | DecisionType>('')

  // The static L-02 rule-set needs no conditional inputs to rebuild —
  // VAL-12's condition is internal to the values (decision + reason).
  const {
    handleSubmit,
    watch,
    setValue,
    clearErrors,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<DecisionFormValues>({
    resolver: zodResolver(decisionSchema),
    defaultValues: { decision: undefined, reason: undefined },
    mode: 'onBlur',
    reValidateMode: 'onBlur',
  })

  const watchedReason = watch('reason')
  // Q16: the reason field renders only while REJECTED is selected —
  // visibility keyed off the decision state, not imperative DOM toggling.
  const reasonVisible = decision === 'REJECTED'

  // When the decision leaves REJECTED the conditional reason requirement no
  // longer applies — drop any stale field error (the schema re-checks on the
  // next submit), mirroring ExpenseClaimForm's threshold handling (J-03).
  useEffect(() => {
    if (decision !== 'REJECTED' && errors.reason) {
      clearErrors('reason')
    }
  }, [decision, errors.reason, clearErrors])

  /** Server field errors (page-mapped, frontend-api-mapping.md §3/§4) win over client errors. */
  const displayError = (field: keyof DecisionFormValues): string | undefined => {
    const serverMessage = serverFieldErrors?.[field]
    if (serverMessage) return serverMessage
    return (errors[field] as { message?: string } | undefined)?.message
  }

  const decisionError = displayError('decision')
  const reasonError = displayError('reason')

  function handleDecisionChange(value: string) {
    // The select can only hold the empty placeholder or a DecisionType value;
    // mirror into the RHF field (submit-DTO source) and re-validate on a real
    // pick (Q15: on blur/submit, not every keystroke).
    setDecision(value === '' ? '' : (value as DecisionType))
    setValue('decision', value as DecisionType, { shouldValidate: value !== '' })
  }

  const onValidSubmit = handleSubmit(async (values) => {
    try {
      await onSubmit(toExpenseReviewRequest(values))
    } catch {
      // The host page surfaces the service error (banner/field errors —
      // frontend-api-mapping.md §1.2/§4); the form stays open for corrections.
    }
  })

  return (
    <section
      className="expense-review-form"
      data-testid="expense-review-form"
      aria-label="Expense review decision"
    >
      <form noValidate onSubmit={onValidSubmit}>
        <SelectField
          id={FIELD_IDS.decision}
          label="Decision"
          required
          error={decisionError}
          value={decision}
          options={DECISION_OPTIONS}
          placeholder="Select a decision"
          onChange={handleDecisionChange}
          onBlur={() => {
            void trigger('decision')
          }}
        />
        {reasonVisible ? (
          <TextareaField
            id={FIELD_IDS.reason}
            label="Reason"
            required
            error={reasonError}
            value={watchedReason ?? ''}
            placeholder="Explain why this expense claim is being rejected"
            onChange={(value) => setValue('reason', value)}
            onBlur={() => {
              void trigger('reason')
            }}
          />
        ) : null}
        <div className="expense-review-form__actions">
          <button
            type="submit"
            className="button button-primary expense-review-form__submit"
            data-testid="expense-review-form-submit"
            disabled={isSubmitting}
          >
            Submit decision
          </button>
        </div>
      </form>
    </section>
  )
}
