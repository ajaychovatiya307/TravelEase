// New / Edit Expense Claim host pages (I-11, frontend-tasks.md; screens
// P-11/P-12 per frontend-spec.md §4.9; endpoints per api-contract.md §4.2,
// §5.7–§5.8).
//
// Both screens share one host page component — the route param decides the
// mode (`useParams<{id: string}>`: `id` present ⇒ edit; routeTable P-11
// `/expenses/new` / P-12 `/expenses/:id/edit`, both EMPLOYEE-only):
// - P-11 (create) hosts the controlled `TravelRequestPicker` (I-11) +
//   `ExpenseClaimForm` (J-03) in 'create' mode. Create-then-upload
//   (frontend-spec.md §4.9, frontend-clarification.md Q10 amendment):
//   `POST /api/expenses` no longer requires documents above the category
//   threshold, so selected files are BUFFERED by this page (generated temp
//   ids, reported to the form as CONFIRMED session uploads so the
//   client-side VAL-10 gate keeps working) and uploaded one-by-one against
//   the created claim via `POST /api/expenses/{id}/documents` after creation.
// - P-12 (edit) fetches the claim (`useExpenseClaim`, F-06) with the standard
//   three-way error branch (frontend-api-mapping.md §4, mirrored from the
//   P-10 detail page), then hosts the form in 'edit' mode pre-filled from the
//   claim; uploads go straight to the existing claim id and
//   `PUT /api/expenses/{id}` (api-contract.md §5.8) enforces VAL-10 against
//   the claim's ATTACHED documents — a 422 POLICY_VIOLATION `documentIds`
//   field error surfaces inline via `serverFieldErrors` (api-contract.md §8).
//
// Service calls and post-submit navigation belong to the page
// (frontend-constitution.md §3): the form receives them as props and a
// rejected `onSubmit` promise leaves the form open. Server errors not
// attributable to a visible field surface through the top-of-form
// `ErrorBanner` (frontend-clarification.md Q14), cleared on the next submit.
//
// No preemptive ownership check in edit mode: the P-12 route allows any
// employee — owner-only access is enforced at page level via the server's 403
// (routeTable.ts P-12 note; api-contract.md §4) → FORBIDDEN_ACTION →
// AccessDeniedPage.

import { useCallback, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ExpenseClaimForm } from '../../../components/expenses/ExpenseClaimForm'
import { TravelRequestPicker } from '../../../components/expenses/TravelRequestPicker'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import { useTravelRequest } from '../../../hooks/useTravelRequests'
import type { ExpenseClaimCreateRequest, ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { ErrorCategory } from '../../../models/error'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import * as expenseService from '../../../services/expenseService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import type { ErrorBannerVariant } from '../../../shared/ErrorBanner'
import type { UploadedDocument } from '../../../shared/FileUploadControl'
import { NotFoundPage } from '../../../shared/NotFoundPage'
import type {
  ExpenseClaimFieldErrors,
  ExpenseClaimFormValues,
} from '../../../validation/expenseClaimSchema'

/** Form value keys a server `fieldErrors[]` entry can map onto (J-03 fields). */
const FORM_FIELD_KEYS = [
  'travelRequestId',
  'amount',
  'currency',
  'category',
  'expenseDate',
  'documentIds',
] as const

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
/** Human-readable reason a draft could not be sent to Finance (shown on the detail page). */
function describeSubmitFailure(error: unknown): string {
  if (isClassifiedError(error)) {
    const fieldMessage = error.fieldErrors.find((entry) => entry.message)?.message
    return fieldMessage ?? error.message
  }
  return 'The claim was saved as a draft but could not be submitted. Please try again.'
}

function classifySubmitError(error: unknown): {
  fieldErrors?: ExpenseClaimFieldErrors
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
  const fieldErrors: ExpenseClaimFieldErrors = {}
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

/**
 * New / Edit Expense Claim (P-11/P-12, frontend-spec.md §4.9;
 * frontend-tasks.md I-11).
 *
 * Route access (EMPLOYEE-only) is enforced by the `RequireRole` guard
 * (routeTable.ts P-11/P-12), so an authenticated employee session exists at
 * render time. Mode is derived from the route param: P-12
 * (`/expenses/:id/edit`) carries `:id`, P-11 (`/expenses/new`) does not.
 */
export function ExpenseFormPage() {
  const { id } = useParams<{ id: string }>()
  if (id === undefined) {
    return <CreateExpenseClaimPage />
  }
  return <EditExpenseClaimPage claimId={id} />
}

/** A create-mode buffered file awaiting its post-create upload (Q10 amendment). */
interface PendingDocument {
  tempId: string
  file: File
}

/**
 * P-11 New Expense Claim (frontend-spec.md §4.9). Hosts the controlled
 * `TravelRequestPicker` — the selection state (chosen id + the full
 * `TravelRequestResponse` for its trip date range) is owned here — and the
 * create-mode `ExpenseClaimForm`.
 */
function CreateExpenseClaimPage() {
  const navigate = useNavigate()
  const [selectedTravelRequest, setSelectedTravelRequest] = useState<TravelRequestResponse | null>(
    null,
  )
  const [submitBanner, setSubmitBanner] = useState<SubmitBanner | null>(null)
  const [serverFieldErrors, setServerFieldErrors] = useState<ExpenseClaimFieldErrors | undefined>(
    undefined,
  )
  // Create-then-upload buffer (frontend-spec.md §4.9, Q10 amendment): files
  // are keyed by a generated temp id until `POST /api/expenses` succeeds.
  const pendingDocumentsRef = useRef<PendingDocument[]>([])
  const tempIdCounterRef = useRef(0)

  const travelRequestId = selectedTravelRequest?.id ?? ''
  const travelRequestDateRange =
    selectedTravelRequest === null
      ? null
      : {
          startDate: selectedTravelRequest.startDate,
          endDate: selectedTravelRequest.endDate,
        }

  const handleSelectTravelRequest = useCallback((request: TravelRequestResponse | null): void => {
    setSelectedTravelRequest(request)
  }, [])

  /**
   * Create-then-upload wiring (frontend-spec.md §4.9,
   * frontend-clarification.md Q10 amendment): on the CREATE screen the claim
   * does not exist yet, so the file is buffered here (page-level, keyed by a
   * generated temp id) and reported to the form as an already-CONFIRMED
   * session upload. That keeps the form's client-side VAL-10 gate working
   * (submit enabled only once a document is attached) while the real
   * `POST /api/expenses/{id}/documents` upload happens after creation.
   */
  const handleUploadDocument = useCallback((file: File): Promise<UploadedDocument> => {
    tempIdCounterRef.current += 1
    const tempId = `create-temp-document-${tempIdCounterRef.current}`
    pendingDocumentsRef.current = [...pendingDocumentsRef.current, { tempId, file }]
    return Promise.resolve({ documentId: tempId, fileName: file.name, uploadStatus: 'CONFIRMED' })
  }, [])

  const handleCreateSubmit = useCallback(
    async (payload: ExpenseClaimCreateRequest): Promise<void> => {
      // Clear the previous attempt's error state before a new one (Q14).
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // The temp documentIds never reach the create payload:
        // `POST /api/expenses` no longer requires documents above the
        // category threshold (frontend-spec.md §4.9, Q10 amendment) — the
        // buffered files are attached to the created claim below.
        // 1. The claim is created as a private DRAFT.
        const created = await expenseService.create({ ...payload, documentIds: undefined })
        // 2. Post-create uploads, in selection order. Failures are collected and
        //    shown on the claim detail (P-10) — never silently dropped — so the
        //    employee knows the receipt is missing and can retry there.
        const failedUploads: string[] = []
        for (const pending of pendingDocumentsRef.current) {
          try {
            await expenseService.uploadDocument(created.id, {
              file: pending.file,
              fileName: pending.file.name,
            })
          } catch {
            failedUploads.push(pending.file.name)
          }
        }
        pendingDocumentsRef.current = []
        if (failedUploads.length > 0) {
          // Keep it a DRAFT: submitting now could fail the receipt rule or
          // send Finance a claim without the intended evidence.
          navigate(`/expenses/${created.id}`, { state: { failedUploads } })
          return
        }
        // 3. Send it to Finance review; the server re-checks the receipt rule.
        try {
          await expenseService.submit(created.id)
          navigate(`/expenses/${created.id}`)
        } catch (submitError) {
          navigate(`/expenses/${created.id}`, {
            state: { submitError: describeSubmitFailure(submitError) },
          })
        }
      } catch (error) {
        // Keep the form open (rejected promise, J-03) and map the normalized
        // error: field errors inline, everything else as a top-of-form banner.
        const classified = classifySubmitError(error)
        if (classified.fieldErrors !== undefined) {
          setServerFieldErrors(classified.fieldErrors)
        }
        setSubmitBanner(classified.banner ?? null)
        throw error
      }
    },
    [navigate],
  )

  return (
    <section
      className="screen"
      data-screen="expense-new"
      data-section="expenses"
      data-testid="expense-form-page"
    >
      <Link className="back-link" data-testid="expense-form-page-back" to="/expenses">
        <i className="icon icon-arrow-left" />
        Back to Expense Claims
      </Link>
      <header className="page-header" data-testid="expense-form-page-header">
        <div>
          <h1 data-testid="expense-form-page-title">New Expense Claim</h1>
          <p>Claim an expense against one of your approved travel requests.</p>
        </div>
      </header>

      {submitBanner !== null ? (
        <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
          {submitBanner.message}
        </p>
      ) : null}

      <ExpenseClaimForm
        mode="create"
        travelRequestId={travelRequestId}
        travelRequestDateRange={travelRequestDateRange}
        onUploadDocument={handleUploadDocument}
        onSubmit={handleCreateSubmit}
        serverFieldErrors={serverFieldErrors}
        travelRequestPicker={
          <TravelRequestPicker
            selectedId={selectedTravelRequest?.id}
            onSelect={handleSelectTravelRequest}
          />
        }
      />
    </section>
  )
}

/**
 * P-12 Edit Expense Claim (frontend-spec.md §4.9). Fetches the claim
 * (`useExpenseClaim`, F-06) and branches on the normalized error category
 * (frontend-api-mapping.md §4, same convention as the P-10 detail page).
 * No preemptive ownership check is added here: the P-12 route allows any
 * employee and owner-only access is enforced at page level via the server's
 * 403 (routeTable.ts P-12 note; api-contract.md §4) → FORBIDDEN_ACTION →
 * AccessDeniedPage below.
 */
function EditExpenseClaimPage({ claimId }: { claimId: string }) {
  const { data, isLoading, error, refetch } = useExpenseClaim(claimId)

  // Missing record (404) — P-22 not-found fallback in place.
  if (error !== null && error.category === 'RESOURCE_NOT_FOUND') {
    return (
      <section
        className="screen"
        data-screen="expense-new"
        data-section="expenses"
        data-testid="expense-form-page"
      >
        <NotFoundPage />
      </section>
    )
  }

  // Forbidden read (403) — P-21 access-denied fallback in place.
  if (error !== null && error.category === 'FORBIDDEN_ACTION') {
    return (
      <section
        className="screen"
        data-screen="expense-new"
        data-section="expenses"
        data-testid="expense-form-page"
      >
        <AccessDeniedPage />
      </section>
    )
  }

  return (
    <section
      className="screen"
      data-screen="expense-new"
      data-section="expenses"
      data-testid="expense-form-page"
    >
      <Link className="back-link" data-testid="expense-form-page-back" to={`/expenses/${claimId}`}>
        <i className="icon icon-arrow-left" />
        Back to Expense Claim
      </Link>
      <header className="page-header" data-testid="expense-form-page-header">
        <div>
          <h1 data-testid="expense-form-page-title">Edit Expense Claim</h1>
          <p>Edit the claim details and its attached documents.</p>
        </div>
      </header>

      {error !== null ? (
        <div>
          <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
            This expense claim could not be loaded. Please try again.
          </p>
          <button className="button button-secondary button-small" type="button" onClick={refetch}>
            Dismiss
          </button>
        </div>
      ) : null}

      {isLoading ? (
        <p className="empty-state loading-skeleton--form" role="status" aria-label="Loading">
          Loading…
        </p>
      ) : data !== null ? (
        <EditExpenseClaimForm claim={data} />
      ) : (
        <p className="empty-state">No expense claim found.</p>
      )}
    </section>
  )
}

/**
 * Edit-mode form host, mounted only once the claim is loaded so the linked
 * travel request lookup (trip date range for VAL-08) can use a real id.
 */
function EditExpenseClaimForm({ claim }: { claim: ExpenseClaimResponse }) {
  const navigate = useNavigate()
  const [submitBanner, setSubmitBanner] = useState<SubmitBanner | null>(null)
  const [serverFieldErrors, setServerFieldErrors] = useState<ExpenseClaimFieldErrors | undefined>(
    undefined,
  )

  // The linked trip's date range (VAL-08); null while loading — the form
  // degrades the in-range check to presence-only until it arrives.
  const { data: travelRequest } = useTravelRequest(claim.travelRequestId)

  // frontend-spec.md §4.9: edit mode pre-fills existing values; the claim's
  // attached documents (api-contract.md §5.9 → §5.6) seed `documentIds`.
  const initialValues = useMemo<ExpenseClaimFormValues>(
    () => ({
      travelRequestId: claim.travelRequestId,
      amount: claim.amount,
      currency: claim.currency,
      category: claim.category,
      expenseDate: claim.expenseDate,
      documentIds: claim.documents.map((doc) => doc.id),
    }),
    [claim],
  )

  const handleUploadDocument = useCallback(
    (file: File): Promise<UploadedDocument> =>
      expenseService.uploadDocument(claim.id, { file, fileName: file.name }).then((doc) => ({
        documentId: doc.id,
        fileName: doc.fileName,
        uploadStatus: doc.uploadStatus,
      })),
    [claim.id],
  )

  const handleUpdateSubmit = useCallback(
    async (payload: ExpenseClaimCreateRequest): Promise<void> => {
      setSubmitBanner(null)
      setServerFieldErrors(undefined)
      try {
        // `PUT /api/expenses/{id}` (api-contract.md §5.8) enforces VAL-10
        // against the claim's ATTACHED documents; a 422 POLICY_VIOLATION
        // `documentIds` field error surfaces inline via `serverFieldErrors`.
        await expenseService.update(claim.id, payload)
        navigate(`/expenses/${claim.id}`)
      } catch (error) {
        const classified = classifySubmitError(error)
        if (classified.fieldErrors !== undefined) {
          setServerFieldErrors(classified.fieldErrors)
        }
        setSubmitBanner(classified.banner ?? null)
        throw error
      }
    },
    [claim.id, navigate],
  )

  return (
    <>
      {submitBanner !== null ? (
        <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
          {submitBanner.message}
        </p>
      ) : null}
      <ExpenseClaimForm
        mode="edit"
        initialValues={initialValues}
        travelRequestId={claim.travelRequestId}
        travelRequestDateRange={
          travelRequest === null
            ? null
            : { startDate: travelRequest.startDate, endDate: travelRequest.endDate }
        }
        onUploadDocument={handleUploadDocument}
        onSubmit={handleUpdateSubmit}
        serverFieldErrors={serverFieldErrors}
      />
    </>
  )
}
