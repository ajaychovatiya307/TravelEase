/**
 * P-16 Admin – Users list + form pair (I-14, frontend-tasks.md; screen P-16
 * per frontend-spec.md §4.12 — each Admin page is a List + Create/Edit form
 * pair for its resource; Admin-only route visibility comes from routeTable.ts
 * P-16 `/admin/users`, so no role logic is encoded in this page).
 *
 * The list is fed by `useAdminUsers` (F-06 — ServerDataState over a paginated
 * `UserAccountResponse`) and wired through the shared DataTable exactly like
 * ExpenseListPage (page/size/sort from the list query state; the table
 * renders the shared LoadingSkeleton 'table' shape while loading and the
 * shared EmptyState when there are no rows).
 *
 * The create/edit host owns the service call and the submit-error mapping
 * (frontend-api-mapping.md §3/§4, mirrored from ExpenseFormPage — the small
 * helpers below are local copies, not imports): `POST /api/admin/users`
 * creates, `PUT /api/admin/users/{id}` updates (api-contract.md §4.3/§5.12).
 * A rejected submit maps a `ClassifiedError`'s `fieldErrors[]` onto the J-06
 * `UserAccountForm` via `serverFieldErrors` (the L-04 field keys); anything
 * else surfaces as a top-of-form ErrorBanner with the category-based variant,
 * cleared on the next submit. Only one form panel is visible at a time
 * (creating OR editing); edit pre-fills from the clicked row and the form is
 * remounted per target (key) so its defaults are mount-only (J-06).
 */

import { useCallback, useMemo, useState } from 'react'

import './UsersPage.css'

import {
  UserAccountForm,
  type UserAccountFormValues,
} from '../../../components/admin/UserAccountForm'
import { useAuth } from '../../../context/AuthContext'
import { useAdminUsers } from '../../../hooks/useAdminResources'
import { useListQueryState } from '../../../hooks/useListQueryState'
import type {
  UserAccountCreateRequest,
  UserAccountRequest,
  UserAccountResponse,
} from '../../../models/admin'
import type { ErrorCategory } from '../../../models/error'
import { adminService } from '../../../services/adminService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { DataTable } from '../../../shared/DataTable'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { ErrorBanner, type ErrorBannerVariant } from '../../../shared/ErrorBanner'
import { ResourceErrorState } from '../../../shared/ResourceErrorState'
import type { UserAccountFieldErrors } from '../../../validation/adminSchemas'

/** Form value keys a server `fieldErrors[]` entry can map onto (J-06 fields). */
const FORM_FIELD_KEYS = ['fullName', 'email', 'password', 'roles', 'active'] as const

type FormFieldKey = (typeof FORM_FIELD_KEYS)[number]

function isFormFieldKey(value: string): value is FormFieldKey {
  return (FORM_FIELD_KEYS as readonly string[]).includes(value)
}

/** Top-of-form banner state for submit failures with no visible field error. */
interface SubmitBanner {
  message: string
  variant: ErrorBannerVariant
}

function isClassifiedError(error: unknown): error is ClassifiedErrorInstance {
  return (
    typeof error === 'object' &&
    error !== null &&
    typeof (error as ClassifiedErrorInstance).category === 'string'
  )
}

/** Banner variant per normalized category (frontend-api-mapping.md §4). */
function bannerVariantFor(category: ErrorCategory): ErrorBannerVariant {
  if (category === 'CONFLICT') {
    return 'conflict'
  }
  if (category === 'POLICY_VIOLATION') {
    return 'policy'
  }
  return 'error'
}

/**
 * Maps a rejected `onSubmit` (400 VALIDATION_ERROR / 422 POLICY_VIOLATION /
 * 409 CONFLICT — frontend-api-mapping.md §3/§4): `fieldErrors[]` entries
 * whose field matches a form key go through the identical inline
 * field-error path; anything else (or a non-classified error) becomes the
 * top-of-form banner (frontend-clarification.md Q14).
 */
function classifySubmitError(error: unknown): {
  fieldErrors?: UserAccountFieldErrors
  banner?: SubmitBanner
} {
  if (!isClassifiedError(error)) {
    return {
      banner: {
        message: 'An unexpected error occurred. Please try again later.',
        variant: 'error',
      },
    }
  }
  const fieldErrors: UserAccountFieldErrors = {}
  for (const entry of error.fieldErrors) {
    if (isFormFieldKey(entry.field)) {
      fieldErrors[entry.field] = entry.message
    }
  }
  if (Object.keys(fieldErrors).length > 0) {
    return { fieldErrors }
  }
  return { banner: { message: error.message, variant: bannerVariantFor(error.category) } }
}

/** The single form-panel target: creating a new user or editing one row. */
type FormTarget = { mode: 'create' } | { mode: 'edit'; user: UserAccountResponse }

export function UsersPage() {
  const { queryState, setPage, setSize, setSort } = useListQueryState()
  const { data, isLoading, error, refetch } = useAdminUsers(queryState)
  const [formTarget, setFormTarget] = useState<FormTarget | null>(null)
  const [submitBanner, setSubmitBanner] = useState<SubmitBanner | null>(null)
  const [serverFieldErrors, setServerFieldErrors] = useState<UserAccountFieldErrors | undefined>(
    undefined,
  )
  const { authState } = useAuth()
  // #14: administrator-issued temporary password, shown once and never stored
  const [resetTarget, setResetTarget] = useState<UserAccountResponse | null>(null)
  const [issuedPassword, setIssuedPassword] = useState<{ user: UserAccountResponse; value: string } | null>(
    null,
  )
  const [resetError, setResetError] = useState<string | null>(null)

  const handleResetPassword = useCallback(async () => {
    const user = resetTarget
    setResetTarget(null)
    if (user === null) return
    setResetError(null)
    try {
      const { temporaryPassword } = await adminService.users.resetPassword(user.id)
      setIssuedPassword({ user, value: temporaryPassword })
    } catch (caught) {
      setResetError(
        isClassifiedErrorInstance(caught) && caught.category !== 'INTERNAL_ERROR'
          ? caught.message
          : 'The password could not be reset. Please try again.',
      )
    }
  }, [resetTarget])

  const pagination = {
    page: data?.page ?? queryState.page,
    size: data?.size ?? queryState.size,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
  }

  // Opening a target (new user / row edit) or cancelling starts from clean
  // error state; submit failures additionally clear on the next submit.
  const openForm = useCallback((target: FormTarget) => {
    setFormTarget(target)
    setSubmitBanner(null)
    setServerFieldErrors(undefined)
  }, [])

  const handleCancel = useCallback(() => {
    setFormTarget(null)
    setSubmitBanner(null)
    setServerFieldErrors(undefined)
  }, [])

  const handleSortChange = (sort: SortState) => {
    setSort(sort)
  }

  const handleCreateSubmit = useCallback(
    async (payload: UserAccountCreateRequest): Promise<void> => {
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // `POST /api/admin/users` (api-contract.md §5.12) — email uniqueness
        // is a Service-layer business rule and rejects as a classified error.
        await adminService.users.create(payload)
        setFormTarget(null)
        void refetch()
      } catch (error) {
        // Keep the form open (rejected promise, J-06) and map the normalized
        // error: field errors inline, everything else as a top-of-form banner.
        const classified = classifySubmitError(error)
        if (classified.fieldErrors !== undefined) {
          setServerFieldErrors(classified.fieldErrors)
        }
        setSubmitBanner(classified.banner ?? null)
        throw error
      }
    },
    [refetch],
  )

  const handleEditSubmit = useCallback(
    async (payload: UserAccountRequest): Promise<void> => {
      if (formTarget === null || formTarget.mode !== 'edit') {
        return
      }
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // `PUT /api/admin/users/{id}` (api-contract.md §5.12) updates the
        // account's fields and role assignments.
        await adminService.users.update(formTarget.user.id, payload)
        setFormTarget(null)
        void refetch()
      } catch (error) {
        // Same mapping as create — the form stays open for corrections.
        const classified = classifySubmitError(error)
        if (classified.fieldErrors !== undefined) {
          setServerFieldErrors(classified.fieldErrors)
        }
        setSubmitBanner(classified.banner ?? null)
        throw error
      }
    },
    [formTarget, refetch],
  )

  // Edit mode pre-fills from the clicked row (J-06); the form is remounted
  // per target (key below) so the prefilled values are mount-only.
  const initialValues = useMemo<Omit<UserAccountFormValues, 'password'> | undefined>(() => {
    if (formTarget === null || formTarget.mode !== 'edit') {
      return undefined
    }
    const user = formTarget.user
    return {
      fullName: user.fullName,
      email: user.email,
      roles: user.roles,
      active: user.active,
    }
  }, [formTarget])

  const columns: DataTableColumn<UserAccountResponse>[] = [
    {
      key: 'id',
      header: 'ID',
      render: (user) => <span>{user.id}</span>,
    },
    {
      key: 'fullName',
      header: 'Full name',
      sortable: true,
      render: (user) => <span>{user.fullName}</span>,
    },
    {
      key: 'email',
      header: 'Email',
      sortable: true,
      render: (user) => <span>{user.email}</span>,
    },
    {
      key: 'roles',
      header: 'Roles',
      render: (user) => <span>{user.roles.join(', ')}</span>,
    },
    {
      key: 'active',
      header: 'Active',
      sortable: true,
      render: (user) => <span>{user.active ? 'Yes' : 'No'}</span>,
    },
    {
      key: 'actions',
      header: '',
      render: (user) => (
        <span style={{ display: 'inline-flex', gap: 8 }}>
          <button
            type="button"
            className="button button-secondary button-small"
            data-testid={`users-page-edit-${user.id}`}
            onClick={() => openForm({ mode: 'edit', user })}
          >
            Edit
          </button>
          {user.id !== authState.userId ? (
            <button
              type="button"
              className="button button-secondary button-small"
              data-testid={`users-page-reset-${user.id}`}
              onClick={() => setResetTarget(user)}
            >
              Reset password
            </button>
          ) : null}
        </span>
      ),
    },
  ]

  return (
    <section className="screen users-page" data-testid="users-page">
      <header className="page-header users-page__header">
        <div>
          <h1>Users</h1>
          <p>Manage user accounts and their role assignments.</p>
        </div>
        {formTarget === null ? (
          <button
            type="button"
            className="button button-primary"
            data-testid="users-page-new-action"
            onClick={() => openForm({ mode: 'create' })}
          >
            New user
          </button>
        ) : null}
      </header>

      {resetError !== null ? <ErrorBanner message={resetError} variant="error" /> : null}
      {issuedPassword !== null ? (
        <div className="card" role="status" data-testid="users-page-temporary-password" style={{ padding: 16 }}>
          <p>
            Temporary password for <strong>{issuedPassword.user.email}</strong>. It is shown only
            once: give it to the user through a trusted channel. They must choose a new password at
            next sign-in, and their current sessions have ended.
          </p>
          <p>
            <code className="numeric" style={{ fontSize: 16 }}>
              {issuedPassword.value}
            </code>
          </p>
          <button
            type="button"
            className="button button-secondary button-small"
            onClick={() => setIssuedPassword(null)}
          >
            Done
          </button>
        </div>
      ) : null}
      <ConfirmDialog
        isOpen={resetTarget !== null}
        title="Reset this user's password?"
        message={`${resetTarget?.email ?? 'The user'} will be signed out and must set a new password with the temporary one you are given.`}
        confirmLabel="Reset password"
        cancelLabel="Keep current password"
        onConfirm={handleResetPassword}
        onCancel={() => setResetTarget(null)}
      />

      {error !== null ? (
        <ResourceErrorState error={error} onRetry={refetch} resourceName="Users" />
      ) : null}

      {formTarget !== null ? (
        <div className="card card-medium users-page__form-panel" data-testid="users-page-form-panel">
          <div className="card-header users-page__form-header">
            <h2>{formTarget.mode === 'create' ? 'New user' : 'Edit user'}</h2>
            <button
              type="button"
              className="icon-button"
              data-testid="users-page-cancel"
              aria-label="Cancel"
              onClick={handleCancel}
            >
              <i className="icon icon-x" />
            </button>
          </div>
          {submitBanner !== null ? (
            <ErrorBanner message={submitBanner.message} variant={submitBanner.variant} />
          ) : null}
          {formTarget.mode === 'create' ? (
            <UserAccountForm
              key="create"
              mode="create"
              onSubmit={handleCreateSubmit}
              serverFieldErrors={serverFieldErrors}
            />
          ) : (
            <UserAccountForm
              key={`edit-${formTarget.user.id}`}
              mode="edit"
              initialValues={initialValues}
              onSubmit={handleEditSubmit}
              serverFieldErrors={serverFieldErrors}
            />
          )}
        </div>
      ) : (
        <div className="card">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          getRowId={(user) => user.id}
          isLoading={isLoading}
          emptyMessage="No users found."
          pagination={pagination}
          onPageChange={setPage}
          onSizeChange={setSize}
          sort={queryState.sort}
          onSortChange={handleSortChange}
        />
        </div>
      )}
    </section>
  )
}
