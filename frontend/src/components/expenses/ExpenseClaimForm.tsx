// Expense claim create/edit form (J-03, frontend-tasks.md; screens P-11/P-12,
// hosted by the I-11 ExpenseFormPage).
//
// Schema-driven single-step form per frontend-spec.md §4.9/§5 and
// frontend-plan.md §9/§10:
// - the named L-03 rule-set (validation/expenseClaimSchema.ts, VAL-05..VAL-10)
//   plugs into react-hook-form via @hookform/resolvers' zodResolver; the
//   schema factory is re-run — and the resolver rebuilt — whenever the
//   conditional inputs change (trip date range, the selected category's
//   fetched threshold, the active category codes). Absent inputs degrade the
//   corresponding check to presence-only; the server remains authoritative
//   (frontend-constitution.md §8 two-tier model).
// - fields render through the C-07 FormField chrome (FormFieldWrapper + the
//   `form-field` input classes, mirroring TextField/SelectField/DateField);
//   the shared FileUploadControl owns the document upload UX (VAL-11).
// - the conditional document requirement (VAL-10) is a schema rule keyed off
//   the computed amount-vs-threshold (frontend-plan.md §9), surfaced on the
//   upload section as a required marker + hint; while the entered amount
//   strictly exceeds the fetched category threshold, submit is blocked
//   without at least one CONFIRMED document (field-specific error).
// - the host page owns service calls and navigation (frontend-constitution.md
//   §3): the travel request link (VAL-09 — the TravelRequestPicker lives on
//   the page, I-11), the document upload
//   (POST /api/expenses/{id}/documents, frontend-api-mapping.md §1.2), and
//   the create/update call arrive as props. The form calls the category/policy
//   lookup hooks itself (F-06; BR-04 + the VAL-10 threshold source).
// - a valid submit maps form values to the request DTO through the dedicated
//   `toExpenseClaimRequest()` mapping (frontend-plan.md §8,
//   frontend-api-mapping.md §2 TR-06) before invoking `onSubmit()`.
// - server field errors (400 VALIDATION_ERROR / 422 POLICY_VIOLATION
//   `fieldErrors[]`) render through the identical inline path via
//   `serverFieldErrors` (frontend-api-mapping.md §3/§4).

import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useForm, type DefaultValues } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

import { useExpenseCategories, usePolicyConfigs } from '../../hooks/useAdminResources'
import type { ListQueryState } from '../../hooks/useListQueryState'
import type { ExpenseClaimCreateRequest } from '../../models/expenseClaim'
import { FileUploadControl } from '../../shared/FileUploadControl'
import type { UploadedDocument } from '../../shared/FileUploadControl'
import { FormFieldWrapper } from '../../shared/FormField'
import { expenseClaimSchema } from '../../validation/expenseClaimSchema'
import type {
  ExpenseClaimFieldErrors,
  ExpenseClaimFormValues,
  ExpenseClaimValidationOptions,
} from '../../validation/expenseClaimSchema'

/** Stable field ids (label/input/error association + test hooks). */
const FIELD_IDS = {
  travelRequestId: 'expense-claim-form-travel-request',
  amount: 'expense-claim-form-amount',
  currency: 'expense-claim-form-currency',
  category: 'expense-claim-form-category',
  expenseDate: 'expense-claim-form-expense-date',
  documents: 'expense-claim-form-documents',
} as const

/**
 * Static lookup query states (frontend-api-mapping.md §1.3 P-11/P-12 lookups):
 * one fixed page of ACTIVE categories (BR-04: only active categories are
 * offered) plus a small page of policy configs filtered by the selected
 * category (VAL-10 threshold source, `PolicyConfig.receiptThresholdAmount`).
 * Form lookups are not user-paginated list views, so the state is a constant
 * instead of URL-synchronized (D-04 applies to list/table pages only).
 */
const CATEGORY_QUERY_STATE: ListQueryState = {
  page: 0,
  size: 50,
  sort: { field: 'code', direction: 'asc' },
  filters: { active: 'true' },
}

const POLICY_QUERY_BASE: Omit<ListQueryState, 'filters'> = {
  page: 0,
  size: 10,
  sort: { field: 'categoryCode', direction: 'asc' },
}

/** api-contract.md §3.3: contract decimals carry at most 4 decimal places. */
function roundAmount(value: number): number {
  return Math.round(value * 10000) / 10000
}

/**
 * Dedicated form-values → request-DTO mapping (frontend-plan.md §8;
 * frontend-api-mapping.md §2 TR-06): numeric amount rounded to 4 decimal
 * places, upper-cased ISO 4217 currency, the selected category code, the
 * YYYY-MM-DD expense date, and `documentIds` only when non-empty (TR-01
 * omission convention — omitted when empty and not required).
 */
function toExpenseClaimRequest(values: ExpenseClaimFormValues): ExpenseClaimCreateRequest {
  const request: ExpenseClaimCreateRequest = {
    travelRequestId: values.travelRequestId.trim(),
    amount: roundAmount(values.amount),
    currency: values.currency.trim().toUpperCase(),
    category: values.category.trim(),
    expenseDate: values.expenseDate.trim(),
  }
  if (values.documentIds.length > 0) {
    request.documentIds = [...values.documentIds]
  }
  return request
}

export interface ExpenseClaimFormProps {
  /** 'create' (P-11) or 'edit' (P-12) — controls prefill and the submit label (frontend-plan.md §9). */
  mode: 'create' | 'edit'
  /**
   * Edit-mode prefill (frontend-spec.md §4.9: "Edit mode pre-fills existing
   * values"); the host page maps the `ExpenseClaimResponse` to form values
   * (`documents[].id` → `documentIds`). Ignored in create mode.
   */
  initialValues?: ExpenseClaimFormValues
  /**
   * The travel request the host page's TravelRequestPicker currently links
   * (VAL-09; BR-02 — own `Approved` trips only). Rendered read-only here;
   * the picker itself belongs to the page (I-11).
   */
  travelRequestId: string
  /**
   * The linked trip's date range (VAL-08) supplied by the page from the
   * fetched travel request. Absent while unknown → the in-range check
   * degrades to presence-only and the server stays authoritative.
   */
  travelRequestDateRange?: { startDate: string; endDate: string } | null
  /**
   * Host-page document upload (POST /api/expenses/{id}/documents —
   * frontend-api-mapping.md §1.2); service calls belong to the page
   * (frontend-constitution.md §3).
   */
  onUploadDocument: (file: File) => Promise<UploadedDocument>
  /**
   * Called once client-side validation (VAL-05..VAL-10) passes, with the
   * mapped request DTO (TR-06); the page owns the service call and the
   * post-submit navigation. A rejected promise leaves the form open.
   */
  onSubmit: (payload: ExpenseClaimCreateRequest) => Promise<void>
  /**
   * Server field errors (400 VALIDATION_ERROR / 422 POLICY_VIOLATION
   * `fieldErrors[]`) mapped by the page onto the identical inline
   * field-error path (frontend-api-mapping.md §3/§4). Server messages win
   * over client messages for the same field.
   */
  serverFieldErrors?: ExpenseClaimFieldErrors
  travelRequestPicker?: ReactNode
}

/**
 * Renders the P-11/P-12 single-step expense claim form (frontend-spec.md
 * §4.9). Creates/updates share one schema/component; `mode` + `initialValues`
 * control prefill and the submit label (frontend-plan.md §9). The category
 * and receipt-threshold lookups are owned here (F-06); the travel-request
 * link, the document upload, and the create/update service call arrive from
 * the host page (frontend-constitution.md §3).
 */
export function ExpenseClaimForm({
  mode,
  initialValues,
  travelRequestId,
  travelRequestDateRange,
  onUploadDocument,
  onSubmit,
  serverFieldErrors,
  travelRequestPicker,
}: ExpenseClaimFormProps) {
  // Lookup data owned by the form (F-06): active categories (BR-04) and the
  // selected category's policy config (VAL-10 threshold source).
  const categoriesState = useExpenseCategories(CATEGORY_QUERY_STATE)

  // The selected category is the watched value the conditional schema is
  // keyed off (frontend-plan.md §9) and drives the per-category policy
  // lookup, so it lives in component state; the RHF field mirrors it for
  // the submit DTO (handleCategoryChange).
  const [category, setCategory] = useState(initialValues?.category ?? '')

  const policyQueryState = useMemo<ListQueryState>(
    () => ({
      ...POLICY_QUERY_BASE,
      filters: (category ? { categoryCode: category } : {}) as Record<string, string>,
    }),
    [category],
  )
  const policyState = usePolicyConfigs(policyQueryState)

  const categoryCodes = useMemo(
    () => categoriesState.data?.content.map((item) => item.code) ?? [],
    [categoriesState.data],
  )
  const categoryOptions = useMemo(
    () =>
      (categoriesState.data?.content ?? []).map((item) => ({
        value: item.code,
        label: item.label,
      })),
    [categoriesState.data],
  )
  // VAL-10: the selected category's ACTIVE receipt threshold (PolicyConfig).
  const receiptThresholdAmount = useMemo(() => {
    if (!category) return undefined
    return policyState.data?.content.find((item) => item.active && item.categoryCode === category)
      ?.receiptThresholdAmount
  }, [policyState.data, category])

  // Rebuild the named rule-set (L-03) — and therefore the zodResolver —
  // whenever a conditional input changes (trip range, threshold, active
  // category codes); absent inputs degrade the check to presence-only.
  const validationOptions = useMemo<ExpenseClaimValidationOptions>(
    () => ({
      travelRequestDateRange: travelRequestDateRange ?? undefined,
      receiptThresholdAmount:
        typeof receiptThresholdAmount === 'number' && Number.isFinite(receiptThresholdAmount)
          ? receiptThresholdAmount
          : undefined,
      activeCategoryCodes: categoryCodes.length > 0 ? categoryCodes : undefined,
    }),
    [travelRequestDateRange, receiptThresholdAmount, categoryCodes],
  )
  const resolver = useMemo(
    () => zodResolver(expenseClaimSchema(validationOptions)),
    [validationOptions],
  )

  // Mount-only defaults (frontend-spec.md §4.9: edit mode pre-fills existing
  // values) — the page mounts a fresh form per create/edit target, so the
  // defaults must not react to later prop changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only defaults
  const defaultValues = useMemo<DefaultValues<ExpenseClaimFormValues>>(
    () => ({
      travelRequestId: travelRequestId ?? '',
      amount: initialValues?.amount,
      currency: initialValues?.currency ?? '',
      category: initialValues?.category ?? '',
      expenseDate: initialValues?.expenseDate ?? '',
      documentIds: initialValues?.documentIds ?? [],
    }),
    [],
  )

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    clearErrors,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseClaimFormValues>({
    resolver,
    defaultValues,
    mode: 'onBlur',
    reValidateMode: 'onBlur',
  })

  // Documents already attached to the claim (edit mode). FileUploadControl
  // tracks session uploads only, so the payload keeps these prefilled ids
  // alongside the newly confirmed ones; the page renders the existing
  // documents in the claim's document list (I-11).
  const prefilledDocumentIds = initialValues?.documentIds ?? []
  const [hasPendingUploads, setHasPendingUploads] = useState(false)

  const watchedAmount = watch('amount')
  // VAL-10 conditional requirement — a computed rule key, not imperative DOM
  // toggling (frontend-plan.md §9). The strictly-above boundary semantics
  // (VAL-T-14/VAL-T-15) live in the L-03 schema.
  const documentRequired =
    typeof receiptThresholdAmount === 'number' &&
    Number.isFinite(receiptThresholdAmount) &&
    typeof watchedAmount === 'number' &&
    watchedAmount > receiptThresholdAmount

  // Keep the read-only RHF field in step with the host picker (VAL-09); a
  // non-blank link clears any stale presence error.
  useEffect(() => {
    setValue('travelRequestId', travelRequestId)
    if (travelRequestId.trim().length > 0) {
      clearErrors('travelRequestId')
    }
  }, [travelRequestId, setValue, clearErrors])

  // When the amount drops back to/under the threshold the conditional
  // requirement no longer applies — drop the stale field error (the schema
  // re-checks on the next submit).
  useEffect(() => {
    if (!documentRequired && errors.documentIds) {
      clearErrors('documentIds')
    }
  }, [documentRequired, errors.documentIds, clearErrors])

  /** Server field errors (page-mapped, frontend-api-mapping.md §3/§4) win over client errors. */
  const displayError = (field: keyof ExpenseClaimFormValues): string | undefined => {
    const serverMessage = serverFieldErrors?.[field]
    if (serverMessage) return serverMessage
    return (errors[field] as { message?: string } | undefined)?.message
  }

  const travelRequestError = displayError('travelRequestId')
  const amountError = displayError('amount')
  const currencyError = displayError('currency')
  const categoryError = displayError('category')
  const expenseDateError = displayError('expenseDate')
  const documentError = displayError('documentIds')

  // FEC-09: an inactive prefilled category is still shown (historical
  // display) but no longer offered by the active-only list.
  const prefilledCategory = initialValues?.category
  const prefilledCategoryNotActive =
    mode === 'edit' &&
    categoriesState.data !== null &&
    typeof prefilledCategory === 'string' &&
    prefilledCategory.length > 0 &&
    !categoryOptions.some((option) => option.value === prefilledCategory)

  function handleDocumentsChange(documents: UploadedDocument[]) {
    // Only CONFIRMED uploads count toward the DTO (frontend-plan.md §9: the
    // payload references the resulting documentIds); PENDING entries block
    // the submit button (frontend-spec.md §4.5 submit-gating pattern).
    setHasPendingUploads(documents.some((document) => document.uploadStatus === 'PENDING'))
    const confirmedIds = documents
      .filter((document) => document.uploadStatus === 'CONFIRMED')
      .map((document) => document.documentId)
    setValue('documentIds', [...prefilledDocumentIds, ...confirmedIds], {
      shouldValidate: confirmedIds.length > 0,
    })
  }

  function handleCategoryChange(value: string) {
    setCategory(value)
    // Mirror into the RHF field (submit DTO source) and re-validate it.
    setValue('category', value, { shouldValidate: value.length > 0 })
  }

  const onValidSubmit = handleSubmit(async (values) => {
    try {
      await onSubmit(toExpenseClaimRequest(values))
    } catch {
      // The host page surfaces the service error (banner/field errors —
      // frontend-api-mapping.md §1.2/§4); the form stays open for corrections.
    }
  })

  const lookupFailed = categoriesState.error !== null || policyState.error !== null

  return (
    <form
      className="card card-medium"
      data-testid="expense-claim-form"
      aria-label={mode === 'create' ? 'New expense claim' : 'Edit expense claim'}
      noValidate
      onSubmit={onValidSubmit}
    >
      {lookupFailed ? (
        <p
          role="alert"
          className="required"
          style={{ fontSize: 13 }}
          data-testid="expense-claim-form-lookup-error"
        >
          Expense categories or receipt thresholds could not be loaded. Check your connection and
          reload the page.
        </p>
      ) : null}
      <div className="card-body form-grid">
        {travelRequestPicker}
        <FormFieldWrapper
          fieldId={FIELD_IDS.travelRequestId}
          label="Travel request"
          required
          error={travelRequestError}
        >
          <input
            id={FIELD_IDS.travelRequestId}
            type="text"
            className="input"
            data-testid="expense-claim-form-travel-request"
            value={travelRequestId}
            readOnly
            placeholder={
              travelRequestId ? undefined : 'Select a travel request in the picker above'
            }
            aria-required
            aria-invalid={travelRequestError ? true : undefined}
            aria-describedby={travelRequestError ? `${FIELD_IDS.travelRequestId}-error` : undefined}
          />
        </FormFieldWrapper>
        <FormFieldWrapper fieldId={FIELD_IDS.amount} label="Amount" required error={amountError}>
          <input
            id={FIELD_IDS.amount}
            type="number"
            inputMode="decimal"
            min="0"
            step="0.0001"
            className="input numeric"
            data-testid="expense-claim-form-amount"
            defaultValue={
              typeof initialValues?.amount === 'number' ? initialValues.amount : undefined
            }
            aria-required
            aria-invalid={amountError ? true : undefined}
            aria-describedby={amountError ? `${FIELD_IDS.amount}-error` : undefined}
            {...register('amount', { valueAsNumber: true })}
          />
        </FormFieldWrapper>
        <FormFieldWrapper
          fieldId={FIELD_IDS.currency}
          label="Currency"
          required
          error={currencyError}
        >
          <input
            id={FIELD_IDS.currency}
            type="text"
            maxLength={8}
            placeholder="USD"
            className="input"
            data-testid="expense-claim-form-currency"
            defaultValue={initialValues?.currency || undefined}
            aria-required
            aria-invalid={currencyError ? true : undefined}
            aria-describedby={currencyError ? `${FIELD_IDS.currency}-error` : undefined}
            {...register('currency')}
          />
        </FormFieldWrapper>
        <FormFieldWrapper
          fieldId={FIELD_IDS.category}
          label="Category"
          required
          error={categoryError}
        >
          <select
            id={FIELD_IDS.category}
            className="input"
            data-testid="expense-claim-form-category"
            value={category}
            aria-required
            aria-invalid={categoryError ? true : undefined}
            aria-describedby={categoryError ? `${FIELD_IDS.category}-error` : undefined}
            onChange={(event) => handleCategoryChange(event.target.value)}
            onBlur={() => {
              void trigger('category')
            }}
          >
            <option value="">Select a category</option>
            {prefilledCategoryNotActive && prefilledCategory ? (
              <option value={prefilledCategory}>{prefilledCategory} (no longer active)</option>
            ) : null}
            {categoryOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </FormFieldWrapper>
        <FormFieldWrapper
          fieldId={FIELD_IDS.expenseDate}
          label="Expense date"
          required
          error={expenseDateError}
        >
          <input
            id={FIELD_IDS.expenseDate}
            type="date"
            className="input"
            data-testid="expense-claim-form-expense-date"
            min={travelRequestDateRange?.startDate}
            max={travelRequestDateRange?.endDate}
            defaultValue={initialValues?.expenseDate || undefined}
            aria-required
            aria-invalid={expenseDateError ? true : undefined}
            aria-describedby={expenseDateError ? `${FIELD_IDS.expenseDate}-error` : undefined}
            {...register('expenseDate')}
          />
        </FormFieldWrapper>
        <div className="field field-wide" data-testid="expense-claim-form-documents">
          <FileUploadControl
            id={FIELD_IDS.documents}
            label={documentRequired ? 'Documents (required)' : 'Documents'}
            onUpload={onUploadDocument}
            onChange={handleDocumentsChange}
          />
          {documentRequired && typeof receiptThresholdAmount === 'number' ? (
            <p
              className="required"
              style={{ fontSize: 13 }}
              data-testid="expense-claim-form-documents-required-hint"
            >
              Required: the amount exceeds the {receiptThresholdAmount} receipt threshold for this
              category — attach at least one document.
            </p>
          ) : null}
          {documentError ? (
            <p
              role="alert"
              className="required"
              style={{ fontSize: 13 }}
              data-testid="expense-claim-form-document-error"
            >
              {documentError}
            </p>
          ) : null}
        </div>
      </div>
      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          data-testid="expense-claim-form-submit"
          disabled={isSubmitting || hasPendingUploads}
        >
          {mode === 'create' ? 'Submit claim' : 'Save changes'}
        </button>
      </div>
    </form>
  )
}
