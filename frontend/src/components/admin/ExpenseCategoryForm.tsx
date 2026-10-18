// Admin expense category create/edit form (J-09, frontend-tasks.md; screen
// P-19, hosted by the I-14 ExpenseCategoriesPage).
//
// Mirrors the sibling Section-J forms (J-03 ExpenseClaimForm, J-04
// ExpenseReviewForm) for the Admin resource pair per frontend-spec.md §4.12
// ("Each is a List + Create/Edit form pair") and ui-model.md §4:
// - the named L-04 rule-set (validation/adminSchemas.ts —
//   expenseCategorySchema: `code`/`label` required and non-blank, `active`
//   optional with the server defaulting it to true) plugs into react-hook-form
//   via @hookform/resolvers' zodResolver. Code uniqueness is a Service-layer
//   business rule — presence is the client-side check (the server remains
//   authoritative, frontend-constitution.md §8 two-tier model).
// - the fields render through the shared FormField chrome (C-07): TextField
//   for `code`/`label` and SelectField for the `active` flag (the shared
//   primitives have no checkbox variant).
// - create and edit share one schema/component: `mode` + `initialValues`
//   control prefill and the submit label (frontend-plan.md §9, mirroring
//   ExpenseClaimForm J-03); the page mounts a fresh form per create/edit
//   target, so the defaults are mount-only.
// - a valid submit maps the form values to the request DTO through the
//   dedicated `toExpenseCategoryRequest()` mapping (frontend-plan.md §8,
//   frontend-api-mapping.md §2 TR-06; `code`/`label` trimmed, `active`
//   omitted when unset per the TR-01 omission convention, api-contract.md
//   §5.15) before invoking `onSubmit()`. A rejected promise leaves the form
//   open, mirroring ExpenseClaimForm's contract (J-03).
// - server field errors (400 VALIDATION_ERROR / 409 CONFLICT `fieldErrors[]`
//   — duplicate code) render through the identical inline path via
//   `serverFieldErrors` (frontend-api-mapping.md §3/§4); server messages win
//   over client messages for the same field.
// - the host page (I-14) owns service calls and navigation
//   (frontend-constitution.md §3): the POST /api/admin/expense-categories
//   create/update call and the post-submit navigation arrive through
//   `onSubmit`. This component stays purely presentational + RHF — no
//   service/router imports.

import { useMemo } from 'react'
import { useForm, type DefaultValues } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

import './ExpenseCategoryForm.css'

import type { ExpenseCategoryRequest } from '../../models/admin'
import { SelectField, TextField } from '../../shared/FormField'
import { createExpenseCategorySchema } from '../../validation/adminSchemas'
import type { ExpenseCategoryFieldErrors } from '../../validation/adminSchemas'

/**
 * Form values for the expense category form (P-19; ui-model.md §4) — the
 * exact `ExpenseCategoryRequest` shape (E-04, api-contract.md §5.15):
 * `code` and `label` required; `active` optional (the server defaults it to
 * true when omitted).
 */
export interface ExpenseCategoryFormValues {
  /** Unique category code; it backs the predefined category list (VAL-07, FR-EXP-04). */
  code: string
  /** Human-readable label rendered in the category pickers. */
  label: string
  /** Optional; defaults to `true` when omitted (api-contract.md §5.15). */
  active?: boolean
}

/** Stable field ids (label/input/error association via the shared FormField chrome, C-07). */
const FIELD_IDS = {
  code: 'expense-category-form-code',
  label: 'expense-category-form-label',
  active: 'expense-category-form-active',
} as const

/** Active flag options (ui-model.md §4: `active: boolean`). */
const ACTIVE_OPTIONS = [
  { value: 'true', label: 'Active' },
  { value: 'false', label: 'Inactive' },
]

/**
 * Dedicated form-values → request-DTO mapping (frontend-plan.md §8;
 * frontend-api-mapping.md §2 TR-06): `code`/`label` trimmed; `active`
 * included only when set — omitted otherwise (TR-01 omission convention; the
 * server defaults it to true, api-contract.md §5.15).
 */
function toExpenseCategoryRequest(values: ExpenseCategoryFormValues): ExpenseCategoryRequest {
  const request: ExpenseCategoryRequest = {
    code: values.code.trim(),
    label: values.label.trim(),
  }
  if (typeof values.active === 'boolean') {
    request.active = values.active
  }
  return request
}

export interface ExpenseCategoryFormProps {
  /** 'create' or 'edit' — controls prefill and the submit label (frontend-plan.md §9, mirroring J-03). */
  mode: 'create' | 'edit'
  /**
   * Edit-mode prefill (frontend-spec.md §4.12: selecting a row populates the
   * form in edit mode) — the host page maps the `ExpenseCategoryResponse`
   * onto the form values. Ignored in create mode.
   */
  initialValues?: ExpenseCategoryFormValues
  /** Existing category codes used to enforce ui-model §4 uniqueness. */
  existingCategoryCodes?: readonly string[]
  /**
   * Called once client-side validation (L-04) passes, with the mapped
   * `ExpenseCategoryRequest` DTO (api-contract.md §5.15). The host page
   * (I-14) owns the `expenseService` create/update call
   * (POST /api/admin/expense-categories, frontend-api-mapping.md §1.3) and
   * the post-submit navigation (frontend-constitution.md §3). A rejected
   * promise leaves the form open for corrections, mirroring ExpenseClaimForm
   * (J-03).
   */
  onSubmit: (payload: ExpenseCategoryRequest) => Promise<void>
  /**
   * Server field errors (400 VALIDATION_ERROR / 409 CONFLICT duplicate code
   * `fieldErrors[]`) mapped by the page onto the identical inline
   * field-error path (frontend-api-mapping.md §3/§4). Server messages win
   * over client messages for the same field.
   */
  serverFieldErrors?: ExpenseCategoryFieldErrors
}

/**
 * Renders the P-19 Admin expense category create/edit form (frontend-spec.md
 * §4.12, ui-model.md §4): a single-step form with required `code`/`label`
 * fields and an optional `active` flag. Create/edit share one
 * schema/component; `mode` + `initialValues` control prefill and the submit
 * label (frontend-plan.md §9). The host page owns the service call and
 * navigation (frontend-constitution.md §3).
 */
export function ExpenseCategoryForm({
  mode,
  initialValues,
  existingCategoryCodes,
  onSubmit,
  serverFieldErrors,
}: ExpenseCategoryFormProps) {
  // Mount-only defaults (frontend-spec.md §4.12: edit mode pre-fills the
  // existing values) — the page mounts a fresh form per create/edit target,
  // so the defaults must not react to later prop changes (J-03 pattern).
  /* eslint-disable react-hooks/exhaustive-deps -- mount-only defaults */
  const defaultValues = useMemo<DefaultValues<ExpenseCategoryFormValues>>(
    () => ({
      code: initialValues?.code ?? '',
      label: initialValues?.label ?? '',
      active: initialValues?.active,
    }),
    [],
  )
  /* eslint-enable react-hooks/exhaustive-deps */

  // The static L-04 rule-set needs no conditional inputs to rebuild — every
  // rule is internal to the values (code/label presence, active type).
  const {
    handleSubmit,
    watch,
    setValue,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseCategoryFormValues>({
    resolver: zodResolver(
      createExpenseCategorySchema(existingCategoryCodes, initialValues?.code),
    ),
    defaultValues,
    mode: 'onBlur',
    reValidateMode: 'onBlur',
  })

  /** Server field errors (page-mapped, frontend-api-mapping.md §3/§4) win over client errors. */
  const displayError = (field: keyof ExpenseCategoryFormValues): string | undefined => {
    const serverMessage = serverFieldErrors?.[field]
    if (serverMessage) return serverMessage
    return (errors[field] as { message?: string } | undefined)?.message
  }

  const codeError = displayError('code')
  const labelError = displayError('label')
  const activeError = displayError('active')

  const watchedCode = watch('code')
  const watchedLabel = watch('label')
  const watchedActive = watch('active')

  function handleActiveChange(value: string) {
    // The select can only hold the empty placeholder or 'true'/'false';
    // mirror the boolean (or undefined) into the RHF field (submit-DTO
    // source) and re-validate on a real pick (Q15: on blur/submit, not every
    // keystroke) — mirroring ExpenseReviewForm's decision handling (J-04).
    setValue('active', value === '' ? undefined : value === 'true', {
      shouldValidate: value !== '',
    })
  }

  const onValidSubmit = handleSubmit(async (values) => {
    try {
      await onSubmit(toExpenseCategoryRequest(values))
    } catch {
      // The host page surfaces the service error (banner/field errors —
      // frontend-api-mapping.md §1.3/§4); the form stays open for corrections.
    }
  })

  return (
    <section
      className="expense-category-form"
      data-testid="expense-category-form"
      aria-label={mode === 'create' ? 'New expense category' : 'Edit expense category'}
    >
      <form noValidate onSubmit={onValidSubmit}>
        <TextField
          id={FIELD_IDS.code}
          label="Code"
          required
          error={codeError}
          value={watchedCode ?? ''}
          placeholder="MEALS"
          onChange={(value) => setValue('code', value)}
          onBlur={() => {
            void trigger('code')
          }}
        />
        <TextField
          id={FIELD_IDS.label}
          label="Label"
          required
          error={labelError}
          value={watchedLabel ?? ''}
          placeholder="Meals"
          onChange={(value) => setValue('label', value)}
          onBlur={() => {
            void trigger('label')
          }}
        />
        <SelectField
          id={FIELD_IDS.active}
          label="Active"
          error={activeError}
          value={watchedActive === undefined ? '' : String(watchedActive)}
          options={ACTIVE_OPTIONS}
          placeholder="Select the active status"
          onChange={handleActiveChange}
          onBlur={() => {
            void trigger('active')
          }}
        />
        <div className="expense-category-form__actions">
          <button
            type="submit"
            className="expense-category-form__submit"
            data-testid="expense-category-form-submit"
            disabled={isSubmitting}
          >
            {mode === 'create' ? 'Create category' : 'Save changes'}
          </button>
        </div>
      </form>
    </section>
  )
}
