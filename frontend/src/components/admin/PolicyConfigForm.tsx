/**
 * J-08 — Admin policy config create/edit form (screen P-18), hosted by the
 * I-14 PolicyConfigsPage.
 *
 * Mirrors the sibling Section-J admin forms (J-09 ExpenseCategoryForm, J-03
 * ExpenseClaimForm) for the Admin resource pair:
 * - the named L-04 rule-set (validation/adminSchemas.ts —
 *   policyConfigSchema: `categoryCode`/`currency` required and non-blank,
 *   `receiptThresholdAmount` required and non-negative, `active` optional)
 *   plugs into react-hook-form via @hookform/resolvers' zodResolver.
 * - the fields render through the shared FormField chrome (C-07): TextField
 *   for `categoryCode`/`currency`, a raw numeric input for the threshold
 *   (mirroring ExpenseClaimForm's amount field) and SelectField for the
 *   `active` flag (the shared primitives have no checkbox variant).
 * - create and edit share one schema/component: `mode` + `initialValues`
 *   control prefill and the submit label; the page mounts a fresh form per
 *   create/edit target, so the defaults are mount-only.
 * - a valid submit maps the form values to the request DTO through the
 *   dedicated `toPolicyConfigRequest()` mapping (`categoryCode`/`currency`
 *   trimmed, `receiptThresholdAmount` as a number, `active` omitted when
 *   unset per the TR-01 omission convention) before invoking `onSubmit()`.
 *   A rejected promise leaves the form open, mirroring the sibling admin
 *   forms.
 * - server field errors render through the identical inline path via
 *   `serverFieldErrors`; server messages win over client messages for the
 *   same field.
 * - the host page (I-14 PolicyConfigsPage) owns the POST/PUT
 *   /api/admin/policy-configs call (api-contract.md §5.14) and navigation —
 *   the request arrives through `onSubmit`. This component stays purely
 *   presentational + RHF: no service/router imports.
 */

import { useMemo } from 'react'
import { useForm, type DefaultValues } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

import './PolicyConfigForm.css'

import type { PolicyConfigRequest } from '../../models/admin'
import { FormFieldWrapper, SelectField, TextField } from '../../shared/FormField'
import { createPolicyConfigSchema } from '../../validation/adminSchemas'
import type { PolicyConfigFieldErrors } from '../../validation/adminSchemas'

/**
 * Form values for the policy config form (P-18; ui-model.md §4) — the
 * `PolicyConfigRequest` shape (api-contract.md §5.14): `categoryCode`,
 * `receiptThresholdAmount` and `currency` required; `active` optional
 * (omitted from the payload when unset).
 */
export interface PolicyConfigFormValues {
  /** Policy category code the threshold applies to (e.g. `MEALS`). */
  categoryCode: string
  /** Minimum receipt value (non-negative) that requires an attached receipt. */
  receiptThresholdAmount: number
  /** ISO-4217 currency code of the threshold (e.g. `USD`). */
  currency: string
  /** Whether the policy is active; omitted from the payload when unset. */
  active?: boolean
}

/** Stable field ids (label/input/error association via the shared FormField chrome, C-07). */
const FIELD_IDS = {
  categoryCode: 'policy-config-form-category-code',
  receiptThresholdAmount: 'policy-config-form-receipt-threshold-amount',
  currency: 'policy-config-form-currency',
  active: 'policy-config-form-active',
} as const

/** Active flag options (ui-model.md §4 — the shared primitives have no checkbox variant). */
const ACTIVE_OPTIONS = [
  { value: 'true', label: 'Active' },
  { value: 'false', label: 'Inactive' },
]

/**
 * Maps the form values to the admin request DTO (frontend-api-mapping.md
 * §2): `categoryCode`/`currency` trimmed, `receiptThresholdAmount` as a
 * number, `active` omitted when unset (TR-01 omission convention,
 * api-contract.md §5.14).
 */
function toPolicyConfigRequest(values: PolicyConfigFormValues): PolicyConfigRequest {
  const request: PolicyConfigRequest = {
    categoryCode: values.categoryCode.trim(),
    receiptThresholdAmount: Number(values.receiptThresholdAmount),
    currency: values.currency.trim(),
  }
  if (typeof values.active === 'boolean') {
    request.active = values.active
  }
  return request
}

/**
 * Props for the policy config form. `onSubmit` is host-provided: the page
 * performs the POST/PUT /api/admin/policy-configs call (api-contract.md
 * §5.14). A rejected promise leaves the form open, mirroring the sibling
 * admin forms (J-09).
 */
export interface PolicyConfigFormProps {
  /** `create` shows 'Create policy'; `edit` prefills and shows 'Save changes'. */
  mode: 'create' | 'edit'
  /** Edit-mode seed values; the defaults are mount-only. */
  initialValues?: PolicyConfigFormValues
  /** Existing expense-category codes for the ui-model §4 reference rule. */
  existingCategoryCodes?: readonly string[]
  /** Host submit handler; the page owns the service call and navigation. */
  onSubmit: (payload: PolicyConfigRequest) => Promise<void>
  /** Server-returned field errors (frontend-api-mapping.md §3/§4); win over client messages. */
  serverFieldErrors?: PolicyConfigFieldErrors
}

/**
 * Create/edit form for a policy config (P-18). Presentational + RHF only —
 * see the file header for the host/service split (I-14 PolicyConfigsPage).
 */
export function PolicyConfigForm({
  mode,
  initialValues,
  existingCategoryCodes,
  onSubmit,
  serverFieldErrors,
}: PolicyConfigFormProps) {
  /* eslint-disable react-hooks/exhaustive-deps -- mount-only defaults */
  const defaultValues = useMemo<DefaultValues<PolicyConfigFormValues>>(
    () => ({
      categoryCode: initialValues?.categoryCode,
      receiptThresholdAmount: initialValues?.receiptThresholdAmount,
      currency: initialValues?.currency,
      active: initialValues?.active,
    }),
    [],
  )
  /* eslint-enable react-hooks/exhaustive-deps */

  const {
    handleSubmit,
    watch,
    setValue,
    trigger,
    register,
    formState: { errors, isSubmitting },
  } = useForm<PolicyConfigFormValues>({
    resolver: zodResolver(createPolicyConfigSchema(existingCategoryCodes)),
    defaultValues,
    mode: 'onBlur',
    reValidateMode: 'onBlur',
  })

  /** Server field errors (page-mapped, frontend-api-mapping.md §3/§4) win over client errors. */
  const displayError = (field: keyof PolicyConfigFormValues): string | undefined => {
    const serverMessage = serverFieldErrors?.[field]
    if (serverMessage) return serverMessage
    return (errors[field] as { message?: string } | undefined)?.message
  }

  const categoryCodeError = displayError('categoryCode')
  const thresholdError = displayError('receiptThresholdAmount')
  const currencyError = displayError('currency')
  const activeError = displayError('active')

  const watchedCategoryCode = watch('categoryCode')
  const watchedCurrency = watch('currency')
  const watchedActive = watch('active')

  function handleActiveChange(value: string) {
    // The select can only hold the empty placeholder or 'true'/'false';
    // mirror the boolean (or undefined) into the RHF field (submit-DTO
    // source) and re-validate on a real pick (on blur/submit, not every
    // keystroke) — mirroring ExpenseCategoryForm's flag handling (J-09).
    setValue('active', value === '' ? undefined : value === 'true', {
      shouldValidate: value !== '',
    })
  }

  const onValidSubmit = handleSubmit(async (values) => {
    try {
      await onSubmit(toPolicyConfigRequest(values))
    } catch {
      // The host page surfaces the service error (banner/field errors —
      // frontend-api-mapping.md §1.3/§4); the form stays open for corrections.
    }
  })

  return (
    <section
      className="policy-config-form"
      data-testid="policy-config-form"
      aria-label={mode === 'create' ? 'New policy config' : 'Edit policy config'}
    >
      <form noValidate onSubmit={onValidSubmit}>
        <TextField
          id={FIELD_IDS.categoryCode}
          label="Category code"
          required
          error={categoryCodeError}
          value={watchedCategoryCode ?? ''}
          placeholder="MEALS"
          onChange={(value) => setValue('categoryCode', value)}
          onBlur={() => {
            void trigger('categoryCode')
          }}
        />
        <FormFieldWrapper
          fieldId={FIELD_IDS.receiptThresholdAmount}
          label="Receipt threshold amount"
          required
          error={thresholdError}
        >
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="0.0001"
            id={FIELD_IDS.receiptThresholdAmount}
            className="form-field__input"
            defaultValue={
              typeof initialValues?.receiptThresholdAmount === 'number'
                ? initialValues.receiptThresholdAmount
                : undefined
            }
            aria-required="true"
            aria-invalid={Boolean(thresholdError)}
            aria-describedby={
              thresholdError ? `${FIELD_IDS.receiptThresholdAmount}-error` : undefined
            }
            {...register('receiptThresholdAmount', { valueAsNumber: true })}
          />
        </FormFieldWrapper>
        <TextField
          id={FIELD_IDS.currency}
          label="Currency"
          required
          error={currencyError}
          value={watchedCurrency ?? ''}
          placeholder="USD"
          onChange={(value) => setValue('currency', value)}
          onBlur={() => {
            void trigger('currency')
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
        <div className="policy-config-form__actions">
          <button
            type="submit"
            className="button button-primary policy-config-form__submit"
            data-testid="policy-config-form-submit"
            disabled={isSubmitting}
          >
            {mode === 'create' ? 'Create policy' : 'Save changes'}
          </button>
        </div>
      </form>
    </section>
  )
}
