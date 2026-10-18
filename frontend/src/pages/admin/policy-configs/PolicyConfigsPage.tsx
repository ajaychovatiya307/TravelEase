/**
 * P-18 Admin – Policy Configs list + form pair (I-14, frontend-tasks.md;
 * screen P-18 per frontend-spec.md §4.12 — each Admin page is a List +
 * Create/Edit form pair for its resource; Admin-only route visibility comes
 * from routeTable.ts P-18 `/admin/policy-configs`, so no role logic is
 * encoded in this page).
 *
 * The list is fed by `usePolicyConfigs` (F-06 — ServerDataState over a
 * paginated `PolicyConfigResponse`) and wired through the shared DataTable
 * exactly like UsersPage (page/size/sort from the list query state; the
 * table renders the shared LoadingSkeleton 'table' shape while loading and
 * the shared EmptyState when there are no rows).
 *
 * The create/edit host owns the service call and the submit-error mapping
 * (frontend-api-mapping.md §3/§4, mirrored from UsersPage — the small
 * helpers below are local copies, not imports): creates use POST and edits
 * use PUT /api/admin/policy-configs/{id}, matching PolicyConfigController.
 * A rejected submit maps a `ClassifiedError`'s `fieldErrors[]` onto the J-08
 * `PolicyConfigForm` via `serverFieldErrors` (the L-04 field keys); anything
 * else surfaces as a top-of-form ErrorBanner with the category-based variant,
 * cleared on the next submit. Only one form panel is visible at a time
 * (creating OR editing); edit pre-fills from the clicked row and the form is
 * remounted per target (key) so its defaults are mount-only (J-08).
 */

import { useCallback, useMemo, useState } from 'react'

import './PolicyConfigsPage.css'

import {
  PolicyConfigForm,
  type PolicyConfigFormValues,
} from '../../../components/admin/PolicyConfigForm'
import { useExpenseCategories, usePolicyConfigs } from '../../../hooks/useAdminResources'
import { useListQueryState } from '../../../hooks/useListQueryState'
import type { ListQueryState } from '../../../hooks/useListQueryState'
import type { PolicyConfigRequest, PolicyConfigResponse } from '../../../models/admin'
import type { ErrorCategory } from '../../../models/error'
import { adminService } from '../../../services/adminService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { DataTable } from '../../../shared/DataTable'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { ErrorBanner, type ErrorBannerVariant } from '../../../shared/ErrorBanner'
import { ResourceErrorState } from '../../../shared/ResourceErrorState'
import type { PolicyConfigFieldErrors } from '../../../validation/adminSchemas'

/** Form value keys a server `fieldErrors[]` entry can map onto (J-08 fields). */
const FORM_FIELD_KEYS = ['categoryCode', 'receiptThresholdAmount', 'currency', 'active'] as const

const CATEGORY_LOOKUP_QUERY_STATE: ListQueryState = {
  page: 0,
  size: 50,
  sort: { field: 'code', direction: 'asc' },
  filters: {},
}

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
 * whose field is a known J-08 form key map onto `PolicyConfigFieldErrors`
 * (L-04); anything else (or a non-classified error) becomes the top-of-form
 * banner (frontend-clarification.md Q14).
 */
function classifySubmitError(error: unknown): {
  fieldErrors?: PolicyConfigFieldErrors
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
  const fieldErrors: PolicyConfigFieldErrors = {}
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

/** The single form-panel target: creating a new policy or editing one row. */
type FormTarget = { mode: 'create' } | { mode: 'edit'; config: PolicyConfigResponse }

export function PolicyConfigsPage() {
  const { queryState, setPage, setSize, setSort } = useListQueryState()
  const { data, isLoading, error, refetch } = usePolicyConfigs(queryState)
  const { data: categoryData } = useExpenseCategories(CATEGORY_LOOKUP_QUERY_STATE)
  const existingCategoryCodes = useMemo(
    () => categoryData?.content.map((category) => category.code),
    [categoryData?.content],
  )
  const [formTarget, setFormTarget] = useState<FormTarget | null>(null)
  const [submitBanner, setSubmitBanner] = useState<SubmitBanner | null>(null)
  const [serverFieldErrors, setServerFieldErrors] = useState<PolicyConfigFieldErrors | undefined>(
    undefined,
  )

  const pagination = {
    page: data?.page ?? queryState.page,
    size: data?.size ?? queryState.size,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
  }

  // Opening a target (new policy / row edit) or cancelling starts from clean
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
    async (payload: PolicyConfigRequest): Promise<void> => {
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // `POST /api/admin/policy-configs` (api-contract.md §5.14) creates the
        // threshold; a `categoryCode` that is missing, already active, or not
        // in the base currency rejects as a classified error.
        await adminService.policyConfigs.create(payload)
        setFormTarget(null)
        void refetch()
      } catch (error) {
        // Keep the form open (rejected promise, J-08) and map the normalized
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
    async (payload: PolicyConfigRequest): Promise<void> => {
      if (formTarget === null || formTarget.mode !== 'edit') {
        return
      }
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        await adminService.policyConfigs.update(formTarget.config.id, payload)
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

  // Edit mode pre-fills from the clicked row (J-08); the form is remounted
  // per target (key below) so the prefilled values are mount-only.
  const initialValues = useMemo<PolicyConfigFormValues | undefined>(() => {
    if (formTarget === null || formTarget.mode !== 'edit') {
      return undefined
    }
    const config = formTarget.config
    return {
      categoryCode: config.categoryCode,
      receiptThresholdAmount: config.receiptThresholdAmount,
      currency: config.currency,
      active: config.active,
    }
  }, [formTarget])

  const columns: DataTableColumn<PolicyConfigResponse>[] = [
    {
      key: 'id',
      header: 'ID',
      render: (config) => <span>{config.id}</span>,
    },
    {
      key: 'categoryCode',
      header: 'Category code',
      sortable: true,
      render: (config) => <span>{config.categoryCode}</span>,
    },
    {
      key: 'receiptThresholdAmount',
      header: 'Receipt threshold',
      sortable: true,
      render: (config) => <span>{`${config.receiptThresholdAmount} ${config.currency}`}</span>,
    },
    {
      key: 'active',
      header: 'Active',
      sortable: true,
      render: (config) => <span>{config.active ? 'Yes' : 'No'}</span>,
    },
    {
      key: 'actions',
      header: '',
      render: (config) => (
        <button
          type="button"
          className="button button-secondary button-small"
          data-testid={`policy-configs-page-edit-${config.id}`}
          onClick={() => openForm({ mode: 'edit', config })}
        >
          Edit
        </button>
      ),
    },
  ]

  return (
    <section className="screen policy-configs-page" data-testid="policy-configs-page">
      <header className="page-header policy-configs-page__header">
        <div>
          <h1>Policy Configs</h1>
          <p>Manage expense category policy thresholds.</p>
        </div>
        {formTarget === null ? (
          <button
            type="button"
            className="button button-primary"
            data-testid="policy-configs-page-new-action"
            onClick={() => openForm({ mode: 'create' })}
          >
            New policy
          </button>
        ) : null}
      </header>

      {error !== null ? (
        <ResourceErrorState error={error} onRetry={refetch} resourceName="Policy configs" />
      ) : null}

      {formTarget !== null ? (
        <div
          className="card card-medium policy-configs-page__form-panel"
          data-testid="policy-configs-page-form-panel"
        >
          <div className="card-header policy-configs-page__form-header">
            <h2>{formTarget.mode === 'create' ? 'New policy' : 'Edit policy'}</h2>
            <button
              type="button"
              className="icon-button"
              data-testid="policy-configs-page-cancel"
              aria-label="Cancel"
              onClick={handleCancel}
            >
              <i className="icon icon-x" />
            </button>
          </div>
          {submitBanner !== null ? (
            <ErrorBanner message={submitBanner.message} variant={submitBanner.variant} />
          ) : null}
          <PolicyConfigForm
            key={formTarget.mode === 'create' ? 'create' : `edit-${formTarget.config.id}`}
            mode={formTarget.mode}
            initialValues={initialValues}
            existingCategoryCodes={existingCategoryCodes}
            onSubmit={formTarget.mode === 'create' ? handleCreateSubmit : handleEditSubmit}
            serverFieldErrors={serverFieldErrors}
          />
        </div>
      ) : (
        <div className="card">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          getRowId={(config) => config.id}
          isLoading={isLoading}
          emptyMessage="No policy configs found."
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
