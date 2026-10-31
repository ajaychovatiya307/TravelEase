// Approval decision form (J-02, frontend-tasks.md — "J. Forms") for the
// P-07 Travel Request Approval screen (frontend-spec.md §4.6): an Approve /
// Reject `decision` choice plus a `reason` textarea that is hidden until
// "Reject" is selected (frontend-clarification.md Q16).
//
// Validation is schema-driven (frontend-constitution.md §7): react-hook-form
// + `zodResolver(decisionFormSchema)` (validation/decisionSchema.ts — L-02;
// frontend-spec.md §7 VAL-12) — `reason` must be non-blank (after trim) only
// when `decision = REJECTED` ("Reason is required when rejecting"). The
// submit payload is produced by the TR-03 transformation (frontend-api-mapping.md
// §2) via `toApprovalDecisionRequest` — `reason` is omitted entirely when
// `APPROVED` and trimmed when `REJECTED`.
//
// Server errors (frontend-api-mapping.md §3/§4): the page maps
// `fieldErrors[].field` ('decision' | 'reason') into `serverFieldErrors` —
// rendered inline on the matching field whenever the field has no client
// error — and non-attributable errors as `formError` in the form-level
// `ErrorBanner`. `409 CONFLICT` is handled by the page (P-07), not here.
//
// This component never touches the network: the page (P-07, a later task)
// supplies the `submitting` flag and owns the ConfirmDialog routing (Q13) and
// post-submit navigation.

import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'

import { ErrorBanner } from '../../shared/ErrorBanner'
import { SelectField, TextareaField } from '../../shared/FormField'
import type { SelectOption } from '../../shared/FormField'
import type { ApprovalDecisionRequest } from '../../models/travelRequest'
import { decisionFormSchema, toApprovalDecisionRequest } from '../../validation/decisionSchema'
import type { ApprovalDecisionFormValues } from '../../validation/decisionSchema'

import './ApprovalDecisionForm.css'

export interface ApprovalDecisionFormProps {
  /**
   * Server field errors keyed by contract field name
   * (`fieldErrors[].field` — frontend-api-mapping.md §3): 'decision' or
   * 'reason'. Shown inline on the matching field when it has no client
   * error; the page clears them.
   */
  serverFieldErrors?: Record<string, string>
  /** Form-level error that cannot be attributed to a field — shown in the
   *  form-level `ErrorBanner`. */
  formError?: string | null
  /** True while the page's decision request is in flight. */
  submitting?: boolean
  /** Invoked with the TR-03 request DTO once client validation passes. */
  onSubmit: (payload: ApprovalDecisionRequest) => Promise<void> | void
}

/** Field `id`s — must stay unique on the page (shared FormField contract). */
const FIELD_IDS = {
  decision: 'approval-decision-decision',
  reason: 'approval-decision-reason',
} as const

/** The two `DecisionType` values (models/travelRequest.ts — E-01). */
const DECISION_OPTIONS: SelectOption[] = [
  { value: 'APPROVED', label: 'Approve' },
  { value: 'REJECTED', label: 'Reject' },
]

/**
 * Q16: start on Approve so the form is immediately valid and submittable;
 * the `reason` field stays hidden until the user picks Reject.
 */
const DEFAULT_VALUES: ApprovalDecisionFormValues = { decision: 'APPROVED' }

/**
 * Approve/Reject decision form. The `reason` textarea is revealed (and
 * required — VAL-12) only once Reject is selected; a valid submit calls
 * `onSubmit` with the TR-03 request DTO.
 */
export function ApprovalDecisionForm({
  serverFieldErrors,
  formError,
  submitting = false,
  onSubmit,
}: ApprovalDecisionFormProps) {
  const {
    control,
    handleSubmit,
    formState: { isValid },
  } = useForm<ApprovalDecisionFormValues>({
    resolver: zodResolver(decisionFormSchema),
    defaultValues: DEFAULT_VALUES,
    // Inline errors surface after the first blur; `isValid` stays live so the
    // submit button tracks the conditional VAL-12 rule.
    mode: 'onTouched',
  })

  const onSubmitRequest = handleSubmit((values) => {
    return onSubmit(toApprovalDecisionRequest(values))
  })

  return (
    <form className="approval-decision-form" onSubmit={onSubmitRequest}>
      {formError ? <ErrorBanner message={formError} variant="error" /> : null}

      {/* Q16: the reason Controller is nested inside the decision Controller
          and rendered only while `field.value` is REJECTED, so the reason
          field's visibility always derives from the actual form value (the
          Controller re-renders on every decision change). */}
      <Controller
        control={control}
        name="decision"
        render={({ field, fieldState }) => (
          <>
            <SelectField
              id={FIELD_IDS.decision}
              label="Decision"
              required
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              options={DECISION_OPTIONS}
              error={fieldState.error?.message ?? serverFieldErrors?.['decision']}
            />
            {field.value === 'REJECTED' ? (
              <Controller
                control={control}
                name="reason"
                render={({ field: reasonField, fieldState: reasonFieldState }) => (
                  <TextareaField
                    id={FIELD_IDS.reason}
                    label="Reason"
                    required
                    value={reasonField.value ?? ''}
                    onChange={reasonField.onChange}
                    onBlur={reasonField.onBlur}
                    error={reasonFieldState.error?.message ?? serverFieldErrors?.['reason']}
                  />
                )}
              />
            ) : null}
          </>
        )}
      />

      <div className="approval-decision-form__actions">
        <button
          type="submit"
          className="button button-primary approval-decision-form__submit"
          disabled={!isValid || submitting}
        >
          Submit Decision
        </button>
      </div>
    </form>
  )
}
