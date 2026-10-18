// Reimburse form (J-05, frontend-tasks.md — "J. Forms") for the P-15
// Reimbursement Action screen (ui-model.md §4): a single optional
// `reimbursedAt` timestamp field. Blank is valid — the server defaults the
// timestamp to the current time (api-contract.md §5.11: `reimbursedAt` is
// optional and defaults to server time).
//
// Input control note: the shared `DateField` variant (C-01) is date-only
// (`type="date"`, `YYYY-MM-DD`) and cannot represent a contract `timestamp`,
// so the field uses the shared `TextField` variant with
// `type="datetime-local"` (value format `YYYY-MM-DDTHH:mm`); C-01's
// `TextField` type union was extended additively with `'datetime-local'`
// for this.
//
// Validation is schema-driven (frontend-constitution.md §7) with a small
// local zod schema — no L-xx task exists for this form, so the schema and
// submit mapper live inline with the component rather than in
// src/validation/. `reimbursedAt` is invalid only when non-blank but not a
// well-formed, in-range datetime (the server is always the final outcome —
// frontend-constitution.md §8). `toReimburseRequest` maps the validated
// values to `ExpenseReimburseRequest` (api-contract.md §5.11): blank → `{}`
// with no `reimbursedAt` key (server default); a value →
// `{ reimbursedAt: '<YYYY-MM-DDTHH:mm>:00Z' }` — the entered wall-clock
// value formatted deterministically as UTC ISO 8601, independent of the
// runtime time zone.
//
// Server errors (frontend-api-mapping.md §3/§4): the page maps
// `fieldErrors[].field` ('reimbursedAt') into `serverFieldErrors` — rendered
// inline on the field whenever the field has no client error — and
// non-attributable errors as `formError` in the form-level `ErrorBanner`.
// `409 CONFLICT` (claim no longer `Approved`, FR-FIN-03) is handled by the
// page, not here.
//
// This component never touches the network: the page (P-15, a later task)
// supplies the `submitting` flag and owns the ConfirmDialog gating
// (ui-model.md §6) and post-submit navigation.

import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import type { ExpenseReimburseRequest } from '../../models/expenseClaim'
import { ErrorBanner } from '../../shared/ErrorBanner'
import { TextField } from '../../shared/FormField'

import './ReimburseForm.css'

/**
 * Client pre-submit message raised when `reimbursedAt` is non-blank but not a
 * well-formed datetime.
 */
export const REIMBURSED_AT_INVALID_MESSAGE = 'Enter a valid date and time'

/** Native `datetime-local` input value shape: `YYYY-MM-DDTHH:mm`. */
const DATETIME_LOCAL_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/

/**
 * Optional-field rule: blank (or absent) is valid — the server defaults the
 * timestamp (api-contract.md §5.11); a non-blank value must be a
 * well-formed, in-range `YYYY-MM-DDTHH:mm` datetime.
 */
function isValidReimbursedAt(value: string | undefined): boolean {
  const trimmed = (value ?? '').trim()
  if (trimmed === '') {
    return true
  }
  if (!DATETIME_LOCAL_PATTERN.test(trimmed)) {
    return false
  }
  // Out-of-range values (e.g. month `13`) still match the shape pattern; the
  // explicit `Z` keeps the parse deterministic (UTC, not local time).
  return !Number.isNaN(Date.parse(`${trimmed}:00Z`))
}

/**
 * Local schema for the form values (J-05) — schema-driven validation per
 * frontend-constitution.md §7; no L-xx task exists for this form, so it is
 * kept inline with the component.
 */
const reimburseFormSchema = z.object({
  reimbursedAt: z.string().optional().refine(isValidReimbursedAt, {
    message: REIMBURSED_AT_INVALID_MESSAGE,
  }),
})

/** `ReimburseForm` values — the shape the form binds. */
type ReimburseFormValues = z.infer<typeof reimburseFormSchema>

/**
 * Maps validated form values to `ExpenseReimburseRequest`
 * (api-contract.md §5.11): blank → `{}` with no `reimbursedAt` key (server
 * defaults the timestamp); a value → `{ reimbursedAt: 'YYYY-MM-DDTHH:mm:00Z' }`
 * — the entered wall-clock time formatted deterministically as UTC.
 */
function toReimburseRequest(values: ReimburseFormValues): ExpenseReimburseRequest {
  const reimbursedAt = (values.reimbursedAt ?? '').trim()
  if (reimbursedAt === '') {
    return {}
  }
  return { reimbursedAt: `${reimbursedAt}:00Z` }
}

export interface ReimburseFormProps {
  /**
   * Server field errors keyed by contract field name
   * (`fieldErrors[].field` — frontend-api-mapping.md §3): 'reimbursedAt'.
   * Shown inline on the field when it has no client error; the page clears them.
   */
  serverFieldErrors?: Record<string, string>
  /** Form-level error that cannot be attributed to a field — shown in the
   *  form-level `ErrorBanner`. */
  formError?: string | null
  /** True while the page's reimbursement request is in flight. */
  submitting?: boolean
  /** Invoked with the `ExpenseReimburseRequest` DTO (api-contract.md §5.11)
   *  once client validation passes. */
  onSubmit: (payload: ExpenseReimburseRequest) => Promise<void> | void
}

/** Field `id`s — must stay unique on the page (shared FormField contract). */
const FIELD_IDS = {
  reimbursedAt: 'reimburse-form-reimbursedAt',
} as const

/**
 * The field starts blank: the server defaults the timestamp to the current
 * time (ui-model.md §4).
 */
const DEFAULT_VALUES: ReimburseFormValues = {}

/**
 * Optional reimbursed-at form for the P-15 Reimbursement Action. A blank
 * submit calls `onSubmit` with `{}` (server-time default); a value submits
 * the explicit ISO 8601 UTC timestamp.
 */
export function ReimburseForm({
  serverFieldErrors,
  formError,
  submitting = false,
  onSubmit,
}: ReimburseFormProps) {
  const {
    control,
    handleSubmit,
    formState: { isValid },
  } = useForm<ReimburseFormValues>({
    resolver: zodResolver(reimburseFormSchema),
    defaultValues: DEFAULT_VALUES,
    // Inline errors surface after the first blur; the field is optional so a
    // blank value keeps the form valid and submittable.
    mode: 'onTouched',
  })

  const onSubmitRequest = handleSubmit((values) => {
    return onSubmit(toReimburseRequest(values))
  })

  return (
    <form className="reimburse-form" onSubmit={onSubmitRequest}>
      {formError ? <ErrorBanner message={formError} variant="error" /> : null}

      <Controller
        control={control}
        name="reimbursedAt"
        render={({ field, fieldState }) => (
          <TextField
            id={FIELD_IDS.reimbursedAt}
            label="Reimbursed at"
            type="datetime-local"
            value={field.value ?? ''}
            onChange={field.onChange}
            onBlur={field.onBlur}
            error={fieldState.error?.message ?? serverFieldErrors?.['reimbursedAt']}
          />
        )}
      />

      <div className="reimburse-form__actions">
        <button type="submit" className="reimburse-form__submit" disabled={!isValid || submitting}>
          Mark as Reimbursed
        </button>
      </div>
    </form>
  )
}
