/**
 * P-17 Admin – Employee-Manager Mappings list + form pair (I-14,
 * frontend-tasks.md; screen P-17 per frontend-spec.md §4.12 — each Admin page
 * is a List + Create/Edit form pair for its resource; Admin-only route
 * visibility comes from routeTable.ts P-17 `/admin/mappings`, so no role logic
 * is encoded in this page).
 *
 * The list is fed by `useAdminMappings` (F-06 — ServerDataState over a
 * paginated `EmployeeManagerMappingResponse`) and wired through the shared
 * DataTable exactly like UsersPage (page/size/sort from the list query state;
 * the table renders the shared LoadingSkeleton 'table' shape while loading and
 * the shared EmptyState when there are no rows).
 *
 * The create/edit host owns the service call and the submit-error mapping
 * (frontend-api-mapping.md §3/§4, mirrored from UsersPage — the small helpers
 * below are local copies, not imports): both create and edit go through the
 * single `POST /api/admin/employee-manager-mappings` upsert endpoint (upsert
 * on `employeeId`, api-contract.md §4.3/§5.13 — `adminService.mappings`
 * exposes no separate `update`). A rejected submit maps a
 * `ClassifiedError`'s `fieldErrors[]` onto the J-07
 * `EmployeeManagerMappingForm` via `serverFieldErrors` (the L-04 field keys);
 * `managerId` is nullable — top-of-hierarchy managers submit empty (Q3).
 * Anything else surfaces as a top-of-form ErrorBanner with the category-based
 * variant, cleared on the next submit. Only one form panel is visible at a
 * time (creating OR editing); edit pre-fills from the clicked row (a `null`
 * `managerId` becomes `undefined` in the form values) and the form is
 * remounted per target (key) so its defaults are mount-only.
 */

import { useCallback, useMemo, useState } from 'react'

import './MappingsPage.css'

import {
  EmployeeManagerMappingForm,
  type EmployeeManagerMappingFormValues,
} from '../../../components/admin/EmployeeManagerMappingForm'
import { useAdminMappings } from '../../../hooks/useAdminResources'
import { useListQueryState } from '../../../hooks/useListQueryState'
import type {
  EmployeeManagerMappingRequest,
  EmployeeManagerMappingResponse,
} from '../../../models/admin'
import type { ErrorCategory } from '../../../models/error'
import { adminService } from '../../../services/adminService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { DataTable } from '../../../shared/DataTable'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { ErrorBanner, type ErrorBannerVariant } from '../../../shared/ErrorBanner'
import { ResourceErrorState } from '../../../shared/ResourceErrorState'
import type { EmployeeManagerMappingFieldErrors } from '../../../validation/adminSchemas'

/** Form value keys a server `fieldErrors[]` entry can map onto (J-07 fields). */
const FORM_FIELD_KEYS = ['employeeId', 'managerId', 'effectiveFrom'] as const

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
  fieldErrors?: EmployeeManagerMappingFieldErrors
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
  const fieldErrors: EmployeeManagerMappingFieldErrors = {}
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

/** The single form-panel target: creating a new mapping or editing one row. */
type FormTarget = { mode: 'create' } | { mode: 'edit'; mapping: EmployeeManagerMappingResponse }

export function MappingsPage() {
  const { queryState, setPage, setSize, setSort } = useListQueryState()
  const { data, isLoading, error, refetch } = useAdminMappings(queryState)
  const [formTarget, setFormTarget] = useState<FormTarget | null>(null)
  const [submitBanner, setSubmitBanner] = useState<SubmitBanner | null>(null)
  const [serverFieldErrors, setServerFieldErrors] = useState<
    EmployeeManagerMappingFieldErrors | undefined
  >(undefined)

  const pagination = {
    page: data?.page ?? queryState.page,
    size: data?.size ?? queryState.size,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
  }

  // Opening a target (new mapping / row edit) or cancelling starts from clean
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
    async (payload: EmployeeManagerMappingRequest): Promise<void> => {
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // `POST /api/admin/employee-manager-mappings` (api-contract.md
        // §5.13) — the single upsert endpoint keyed on `employeeId`; an
        // unknown user id rejects as a classified error.
        await adminService.mappings.create(payload)
        setFormTarget(null)
        void refetch()
      } catch (error) {
        // Keep the form open (rejected promise) and map the normalized
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
    async (payload: EmployeeManagerMappingRequest): Promise<void> => {
      if (formTarget === null || formTarget.mode !== 'edit') {
        return
      }
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // Same single `POST` upsert as create (upsert on `employeeId`) —
        // `adminService.mappings` exposes no separate update method.
        await adminService.mappings.create(payload)
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

  // Edit mode pre-fills from the clicked row; a `null` `managerId` becomes
  // `undefined` in the form values (blank = no manager, Q3). The form is
  // remounted per target (key below) so the prefilled values are mount-only.
  const initialValues = useMemo<EmployeeManagerMappingFormValues | undefined>(() => {
    if (formTarget === null || formTarget.mode !== 'edit') {
      return undefined
    }
    const mapping = formTarget.mapping
    return {
      employeeId: mapping.employeeId,
      managerId: mapping.managerId ?? undefined,
      effectiveFrom: mapping.effectiveFrom,
    }
  }, [formTarget])

  const columns: DataTableColumn<EmployeeManagerMappingResponse>[] = [
    {
      key: 'id',
      header: 'ID',
      render: (mapping) => <span>{mapping.id}</span>,
    },
    {
      key: 'employeeId',
      header: 'Employee ID',
      sortable: true,
      render: (mapping) => <span>{mapping.employeeId}</span>,
    },
    {
      key: 'managerId',
      header: 'Manager ID',
      render: (mapping) => <span>{mapping.managerId ?? 'No manager'}</span>,
    },
    {
      key: 'effectiveFrom',
      header: 'Effective from',
      sortable: true,
      render: (mapping) => <span>{mapping.effectiveFrom}</span>,
    },
    {
      key: 'actions',
      header: '',
      render: (mapping) => (
        <button
          type="button"
          className="button button-secondary button-small"
          data-testid={`mappings-page-edit-${mapping.id}`}
          onClick={() => openForm({ mode: 'edit', mapping })}
        >
          Edit
        </button>
      ),
    },
  ]

  return (
    <section className="screen mappings-page" data-testid="mappings-page">
      <header className="page-header mappings-page__header">
        <div>
          <h1>Employee-Manager Mappings</h1>
          <p>Manage employee-to-manager reporting mappings.</p>
        </div>
        {formTarget === null ? (
          <button
            type="button"
            className="button button-primary"
            data-testid="mappings-page-new-action"
            onClick={() => openForm({ mode: 'create' })}
          >
            New mapping
          </button>
        ) : null}
      </header>

      {error !== null ? (
        <ResourceErrorState error={error} onRetry={refetch} resourceName="Mappings" />
      ) : null}

      {formTarget !== null ? (
        <div className="card card-medium mappings-page__form-panel" data-testid="mappings-page-form-panel">
          <div className="card-header mappings-page__form-header">
            <h2>{formTarget.mode === 'create' ? 'New mapping' : 'Edit mapping'}</h2>
            <button
              type="button"
              className="icon-button"
              data-testid="mappings-page-cancel"
              aria-label="Cancel"
              onClick={handleCancel}
            >
              <i className="icon icon-x" />
            </button>
          </div>
          {submitBanner !== null ? (
            <ErrorBanner message={submitBanner.message} variant={submitBanner.variant} />
          ) : null}
          <EmployeeManagerMappingForm
            key={formTarget.mode === 'create' ? 'create' : `edit-${formTarget.mapping.id}`}
            mode={formTarget.mode}
            initialValues={initialValues}
            onSubmit={formTarget.mode === 'create' ? handleCreateSubmit : handleEditSubmit}
            serverFieldErrors={serverFieldErrors}
          />
        </div>
      ) : (
        <div className="card">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          getRowId={(mapping) => mapping.id}
          isLoading={isLoading}
          emptyMessage="No mappings found."
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
