/**
 * J-07 — Admin employee-manager mapping create/edit form (screen P-17),
 * hosted by the I-14 MappingsPage.
 *
 * Mirrors the sibling Section-J admin forms (J-08 PolicyConfigForm, J-09
 * ExpenseCategoryForm) for the Admin resource pair:
 * - the named L-04 rule-set (validation/adminSchemas.ts —
 *   employeeManagerMappingSchema: `employeeId` and `effectiveFrom` required
 *   and non-blank, `effectiveFrom` must be a valid ISO calendar date,
 *   `managerId` optional — blank/`null` allowed) plugs into react-hook-form
 *   via @hookform/resolvers' zodResolver.
 * - the fields render through the shared FormField chrome (C-07): TextField
 *   for `employeeId`/`managerId` (the manager is optional — no required
 *   marker) and a raw text input for `effectiveFrom` (mirroring
 *   PolicyConfigForm's raw numeric-input pattern — the L-04 calendar-date
 *   check must run on the entered string; a native date input silently
 *   drops out-of-range dates like 2026-02-30 before RHF sees them).
 * - create and edit share one schema/component: `mode` + `initialValues`
 *   control prefill and the submit label; the page mounts a fresh form per
 *   create/edit target, so the defaults are mount-only.
 * - a valid submit maps the form values to the request DTO through the
 *   dedicated `toEmployeeManagerMappingRequest()` mapping (`employeeId`
 *   trimmed, `managerId` trimmed with blank/whitespace/`null` mapped to
 *   `null` — no manager — and `effectiveFrom` as-is) before invoking
 *   `onSubmit()`. A rejected promise leaves the form open, mirroring the
 *   sibling admin forms.
 * - server field errors render through the identical inline path via
 *   `serverFieldErrors`; server messages win over client messages for the
 *   same field.
 * - the host page (I-14 MappingsPage) owns the admin mapping create/update
 *   service call and navigation — the request arrives through `onSubmit`.
 *   This component stays purely presentational + RHF: no service/router
 *   imports.
 */

import { useMemo } from 'react'
import { useForm, type DefaultValues } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

import './EmployeeManagerMappingForm.css'

import type { EmployeeManagerMappingRequest } from '../../models/admin'
import { FormFieldWrapper, TextField } from '../../shared/FormField'
import { employeeManagerMappingSchema } from '../../validation/adminSchemas'
import type { EmployeeManagerMappingFieldErrors } from '../../validation/adminSchemas'

/**
 * Form values for the employee-manager mapping form (P-17; ui-model.md §4) —
 * the `EmployeeManagerMappingRequest` shape (api-contract.md): `employeeId`
 * and `effectiveFrom` required; `managerId` optional and nullable (blank or
 * `null` = no manager).
 */
export interface EmployeeManagerMappingFormValues {
  /** Employee the mapping applies to (e.g. `user-42`). */
  employeeId: string
  /** Manager assigned to the employee; blank or `null` means no manager. */
  managerId?: string | null
  /** ISO date (YYYY-MM-DD) from which the mapping applies. */
  effectiveFrom: string
}

/** Stable field ids (label/input/error association via the shared FormField chrome, C-07). */
const FIELD_IDS = {
  employeeId: 'employee-manager-mapping-form-employee-id',
  managerId: 'employee-manager-mapping-form-manager-id',
  effectiveFrom: 'employee-manager-mapping-form-effective-from',
} as const

/**
 * Maps the form values to the admin request DTO (frontend-api-mapping.md
 * §2): `employeeId` trimmed, `managerId` trimmed — blank/whitespace/`null`
 * maps to `null` (no manager) — and `effectiveFrom` as-is (a valid ISO
 * calendar date per the schema).
 */
function toEmployeeManagerMappingRequest(
  values: EmployeeManagerMappingFormValues,
): EmployeeManagerMappingRequest {
  const managerId = values.managerId?.trim()
  return {
    employeeId: values.employeeId.trim(),
    managerId: managerId ? managerId : null,
    effectiveFrom: values.effectiveFrom,
  }
}

/**
 * Props for the employee-manager mapping form. `onSubmit` is host-provided:
 * the page performs the admin mapping create/update call (api-contract.md).
 * A rejected promise leaves the form open, mirroring the sibling admin
 * forms (J-08).
 */
export interface EmployeeManagerMappingFormProps {
  /** `create` shows 'Create mapping'; `edit` prefills and shows 'Save changes'. */
  mode: 'create' | 'edit'
  /** Edit-mode seed values; the defaults are mount-only. */
  initialValues?: EmployeeManagerMappingFormValues
  /** Host submit handler; the page owns the service call and navigation. */
  onSubmit: (payload: EmployeeManagerMappingRequest) => Promise<void>
  /** Server-returned field errors (frontend-api-mapping.md §3/§4); win over client messages. */
  serverFieldErrors?: EmployeeManagerMappingFieldErrors
}

/**
 * Create/edit form for an employee-manager mapping (P-17). Presentational +
 * RHF only — see the file header for the host/service split (I-14
 * MappingsPage).
 */
export function EmployeeManagerMappingForm({
  mode,
  initialValues,
  onSubmit,
  serverFieldErrors,
}: EmployeeManagerMappingFormProps) {
  /* eslint-disable react-hooks/exhaustive-deps -- mount-only defaults */
  const defaultValues = useMemo<DefaultValues<EmployeeManagerMappingFormValues>>(
    () => ({
      employeeId: initialValues?.employeeId,
      managerId: initialValues?.managerId,
      effectiveFrom: initialValues?.effectiveFrom,
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
  } = useForm<EmployeeManagerMappingFormValues>({
    resolver: zodResolver(employeeManagerMappingSchema),
    defaultValues,
    mode: 'onBlur',
    reValidateMode: 'onBlur',
  })

  /** Server field errors (page-mapped, frontend-api-mapping.md §3/§4) win over client errors. */
  const displayError = (field: keyof EmployeeManagerMappingFormValues): string | undefined => {
    const serverMessage = serverFieldErrors?.[field]
    if (serverMessage) return serverMessage
    return (errors[field] as { message?: string } | undefined)?.message
  }

  const employeeIdError = displayError('employeeId')
  const managerIdError = displayError('managerId')
  const effectiveFromError = displayError('effectiveFrom')

  const watchedEmployeeId = watch('employeeId')
  const watchedManagerId = watch('managerId')

  const onValidSubmit = handleSubmit(async (values) => {
    try {
      await onSubmit(toEmployeeManagerMappingRequest(values))
    } catch {
      // The host page surfaces the service error (banner/field errors —
      // frontend-api-mapping.md §1.3/§4); the form stays open for corrections.
    }
  })

  return (
    <section
      className="employee-manager-mapping-form"
      data-testid="employee-manager-mapping-form"
      aria-label={
        mode === 'create' ? 'New employee-manager mapping' : 'Edit employee-manager mapping'
      }
    >
      <form noValidate onSubmit={onValidSubmit}>
        <TextField
          id={FIELD_IDS.employeeId}
          label="Employee ID"
          required
          error={employeeIdError}
          value={watchedEmployeeId ?? ''}
          placeholder="user-42"
          onChange={(value) => setValue('employeeId', value)}
          onBlur={() => {
            void trigger('employeeId')
          }}
        />
        <TextField
          id={FIELD_IDS.managerId}
          label="Manager ID"
          error={managerIdError}
          value={watchedManagerId ?? ''}
          placeholder="Leave empty for no manager"
          onChange={(value) => setValue('managerId', value)}
          onBlur={() => {
            void trigger('managerId')
          }}
        />
        <FormFieldWrapper
          fieldId={FIELD_IDS.effectiveFrom}
          label="Effective from"
          required
          error={effectiveFromError}
        >
          <input
            type="text"
            placeholder="YYYY-MM-DD"
            id={FIELD_IDS.effectiveFrom}
            className="form-field__input"
            defaultValue={initialValues?.effectiveFrom}
            aria-required="true"
            aria-invalid={Boolean(effectiveFromError)}
            aria-describedby={effectiveFromError ? `${FIELD_IDS.effectiveFrom}-error` : undefined}
            {...register('effectiveFrom')}
          />
        </FormFieldWrapper>
        <div className="employee-manager-mapping-form__actions">
          <button
            type="submit"
            className="button button-primary employee-manager-mapping-form__submit"
            data-testid="employee-manager-mapping-form-submit"
            disabled={isSubmitting}
          >
            {mode === 'create' ? 'Create mapping' : 'Save changes'}
          </button>
        </div>
      </form>
    </section>
  )
}
