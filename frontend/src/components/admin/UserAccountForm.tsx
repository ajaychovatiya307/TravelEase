/**
 * J-06 — Admin user account create/edit form (screen P-16), hosted by the
 * I-14 UsersPage.
 *
 * Mirrors the sibling Section-J admin forms (J-08 PolicyConfigForm, J-09
 * ExpenseCategoryForm) for the Admin user resource pair:
 * - the named L-04 rule-set (validation/adminSchemas.ts —
 *   userAccountSchema: `fullName`/`email` required and non-blank (with an
 *   email-format refine on `email`), `roles` a non-empty array of valid
 *   RoleType values, `active` optional) plugs into react-hook-form via
 *   @hookform/resolvers' zodResolver.
 * - the fields render through the shared FormField chrome (C-07): TextField
 *   for `fullName`/`email` and create-only `password`, MultiSelectField for
 *   the required `roles` pick and SelectField for the optional `active` flag.
 * - create and edit share one schema/component: `mode` + `initialValues`
 *   control prefill and the submit label; the page mounts a fresh form per
 *   create/edit target, so the defaults are mount-only.
 * - a valid submit maps the form values to the request DTO through the
 *   dedicated request mappings (`fullName`/`email` trimmed, `roles` as-is,
 *   `active` omitted when unset; raw password only on create) before invoking
 *   `onSubmit()`. A rejected promise leaves the form open.
 * - server field errors render through the identical inline path via
 *   `serverFieldErrors`; server messages win over client messages for the
 *   same field.
 * - the host page (I-14 UsersPage) owns the POST/PUT /api/admin/users call
 *   and navigation — the request arrives through `onSubmit`. This component
 *   stays purely presentational + RHF: no service/router imports.
 */

import { useMemo } from 'react'
import { useForm, type DefaultValues } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

import './UserAccountForm.css'

import type {
  RoleType,
  UserAccountCreateRequest,
  UserAccountRequest,
} from '../../models/admin'
import { MultiSelectField, SelectField, TextField } from '../../shared/FormField'
import {
  userAccountCreateSchema,
  userAccountEditSchema,
} from '../../validation/adminSchemas'
import type { UserAccountFieldErrors } from '../../validation/adminSchemas'

/**
 * Form values for the user account form (P-16; ui-model.md §4): account
 * fields plus a create-only password; `active` is omitted when unset.
 */
export interface UserAccountFormValues {
  /** Full display name of the user. */
  fullName: string
  /** Login email address (must be a valid email format). */
  email: string
  /** Initial credential; submitted only in create mode. */
  password: string
  /** Roles granted to the user — at least one required. */
  roles: RoleType[]
  /** Whether the account is active; omitted from the payload when unset. */
  active?: boolean
}

interface UserAccountFormCommonProps {
  /** Server-returned field errors; server messages win over client messages. */
  serverFieldErrors?: UserAccountFieldErrors
}

/** Keep credential-bearing creation separate from profile-only updates. */
export type UserAccountFormProps = UserAccountFormCommonProps &
  (
    | {
        mode: 'create'
        initialValues?: never
        onSubmit: (payload: UserAccountCreateRequest) => Promise<void>
      }
    | {
        mode: 'edit'
        initialValues?: Omit<UserAccountFormValues, 'password'>
        onSubmit: (payload: UserAccountRequest) => Promise<void>
      }
  )

/** Stable field ids (label/input/error association via the shared FormField chrome, C-07). */
const FIELD_IDS = {
  fullName: 'user-account-form-full-name',
  email: 'user-account-form-email',
  password: 'user-account-form-password',
  roles: 'user-account-form-roles',
  active: 'user-account-form-active',
} as const

/** Role options (the RoleType values from models/admin.ts). */
const ROLE_OPTIONS: { value: RoleType; label: string }[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'FINANCE', label: 'Finance' },
  { value: 'ADMIN', label: 'Admin' },
]

/** Active flag options (the shared primitives have no checkbox variant). */
const ACTIVE_OPTIONS = [
  { value: 'true', label: 'Active' },
  { value: 'false', label: 'Inactive' },
]

/**
 * Map the form values to the `UserAccountRequest` DTO: `fullName`/`email`
 * trimmed, `roles` as-is (the control only ever yields RoleType values),
 * `active` omitted when unset (TR-01 omission convention).
 */
// eslint-disable-next-line react-refresh/only-export-components -- pure DTO mapping, not a component
export function toUserAccountRequest(values: UserAccountFormValues): UserAccountRequest {
  const request: UserAccountRequest = {
    fullName: values.fullName.trim(),
    email: values.email.trim(),
    roles: values.roles,
  }
  if (typeof values.active === 'boolean') {
    request.active = values.active
  }
  return request
}

// eslint-disable-next-line react-refresh/only-export-components -- pure DTO mapping, not a component
export function toUserAccountCreateRequest(
  values: UserAccountFormValues,
): UserAccountCreateRequest {
  return { ...toUserAccountRequest(values), password: values.password }
}

export function UserAccountForm({
  mode,
  initialValues,
  onSubmit,
  serverFieldErrors,
}: UserAccountFormProps) {
  // Mount-only defaults: the host page mounts a fresh form per create/edit
  // target, so the values must not track `initialValues` after mount.
  const defaultValues = useMemo<DefaultValues<UserAccountFormValues>>(
    () => ({
      fullName: initialValues?.fullName ?? '',
      email: initialValues?.email ?? '',
      password: '',
      roles: initialValues?.roles ?? [],
      active: initialValues?.active,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only defaults (see above)
    [],
  )

  const {
    handleSubmit,
    setValue,
    watch,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<UserAccountFormValues>({
    resolver: zodResolver(mode === 'create' ? userAccountCreateSchema : userAccountEditSchema),
    mode: 'onBlur',
    defaultValues,
  })

  const watchedFullName = watch('fullName')
  const watchedEmail = watch('email')
  const watchedPassword = watch('password')
  const watchedRoles = watch('roles')
  const watchedActive = watch('active')

  // Server field errors win over client (zod) messages for the same field.
  const displayError = (field: 'fullName' | 'email' | 'password' | 'roles' | 'active') =>
    serverFieldErrors?.[field] ?? errors[field]?.message

  const fullNameError = displayError('fullName')
  const emailError = displayError('email')
  const passwordError = displayError('password')
  const rolesError = displayError('roles')
  const activeError = displayError('active')

  const handleRolesChange = (roles: string[]) => {
    // The control only yields the known RoleType values; mirror the pick
    // into the RHF field (submit-DTO source) and re-validate on a real pick
    // (on blur/submit, not every change) — mirroring the sibling flag
    // handling (J-08).
    setValue('roles', roles as RoleType[], {
      shouldValidate: roles.length > 0,
    })
  }

  const handleActiveChange = (value: string) => {
    // The select can only hold the empty placeholder or 'true'/'false';
    // mirror the boolean (or undefined) into the RHF field (submit-DTO
    // source) and re-validate on a real pick (on blur/submit, not every
    // keystroke) — mirroring PolicyConfigForm's flag handling (J-08).
    setValue('active', value === '' ? undefined : value === 'true', {
      shouldValidate: value !== '',
    })
  }

  const onValidSubmit = handleSubmit(async (values) => {
    try {
      if (mode === 'create') {
        await onSubmit(toUserAccountCreateRequest(values))
      } else {
        await onSubmit(toUserAccountRequest(values))
      }
    } catch {
      // The host page surfaces the service error (banner/field errors —
      // frontend-api-mapping.md §1.3/§4); the form stays open for corrections.
    }
  })

  return (
    <section
      className="user-account-form"
      data-testid="user-account-form"
      aria-label={mode === 'create' ? 'New user account' : 'Edit user account'}
    >
      <form noValidate onSubmit={onValidSubmit}>
        <TextField
          id={FIELD_IDS.fullName}
          label="Full name"
          required
          error={fullNameError}
          value={watchedFullName ?? ''}
          placeholder="Ada Lovelace"
          onChange={(value) => setValue('fullName', value)}
          onBlur={() => {
            void trigger('fullName')
          }}
        />
        <TextField
          id={FIELD_IDS.email}
          label="Email"
          required
          error={emailError}
          value={watchedEmail ?? ''}
          placeholder="name@example.com"
          onChange={(value) => setValue('email', value)}
          onBlur={() => {
            void trigger('email')
          }}
        />
        {mode === 'create' ? (
          <TextField
            id={FIELD_IDS.password}
            label="Password"
            required
            type="password"
            autoComplete="new-password"
            error={passwordError}
            value={watchedPassword ?? ''}
            placeholder="At least 8 characters"
            onChange={(value) => setValue('password', value)}
            onBlur={() => {
              void trigger('password')
            }}
          />
        ) : null}
        <MultiSelectField
          id={FIELD_IDS.roles}
          label="Roles"
          required
          error={rolesError}
          value={watchedRoles ?? []}
          options={ROLE_OPTIONS}
          onChange={handleRolesChange}
          onBlur={() => {
            void trigger('roles')
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
        <div className="user-account-form__actions">
          <button
            type="submit"
            className="user-account-form__submit"
            data-testid="user-account-form-submit"
            disabled={isSubmitting}
          >
            {mode === 'create' ? 'Create user' : 'Save changes'}
          </button>
        </div>
      </form>
    </section>
  )
}
