// Travel Request create/edit/resubmit page (I-06, frontend-tasks.md —
// "I. Pages"): hosts the P-05 New Travel Request and P-06 Edit/Resubmit
// screens (frontend-spec.md §4.5; frontend-api-mapping.md §1.1) on top of
// `TravelRequestForm` (J-01). The page owns the service calls (F-02,
// travelRequestService.ts) and the `ClassifiedError` → UI mapping
// (frontend-api-mapping.md §3/§4): `fieldErrors[].field` entries attributed
// to a request field become inline `serverFieldErrors`, the remaining
// messages (or the envelope message when none apply) become the form-level
// `formError` banner, and `409 CONFLICT` surfaces the conflict text (P-06
// additionally refetches to sync the displayed status after a conflict).
//
// `useParams` splits the two routes into internal `CreateView`/`EditView`
// components so `useTravelRequest` is never called conditionally and the
// form mounts only when the record exists — `TravelRequestForm` reads
// `initialValues` once at mount (as `defaultValues`), so it is mounted a
// single time with stable values keyed by `data.id`.
//
// Q10 constraint (create mode): there is no request id before the first
// save and the API has no pre-create upload endpoint, so `CreateView`
// rejects `onUpload` — documents can be uploaded after the travel request
// is saved.
//
// The G-02 unsaved-changes guard is out of scope here: `TravelRequestForm`
// exposes no dirty-state signal for the page to guard on.

import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { TravelRequestForm } from '../../../components/travel-requests/TravelRequestForm'
import { useTravelRequest } from '../../../hooks/useTravelRequests'
import type { TravelRequestCreateRequest } from '../../../models/travelRequest'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import { create, update, uploadDocument } from '../../../services/travelRequestService'
import { ErrorBanner } from '../../../shared/ErrorBanner'
import { LoadingSkeleton } from '../../../shared/LoadingSkeleton'
import { NotFoundPage } from '../../../shared/NotFoundPage'
import type { TravelRequestFormValues } from '../../../validation/travelRequestSchema'

/** Request fields a `fieldErrors[].field` entry can be attributed to (api-mapping §3). */
const ATTRIBUTABLE_FIELDS = ['destination', 'startDate', 'endDate', 'justification', 'documentIds']

/** Fallback form-level message when a rejected create/update carries no usable text. */
const SAVE_FAILED_MESSAGE = 'The request could not be saved. Please try again.'

/**
 * Maps a rejected `create`/`update` call to the form's UI state
 * (frontend-api-mapping.md §3/§4):
 * - `VALIDATION_ERROR`/`POLICY_VIOLATION` — `fieldErrors[].field` entries
 *   attributed to a request field become `serverFieldErrors` (keyed by
 *   field name); the remaining messages — or the envelope message when none
 *   apply — become the form-level `formError`.
 * - `CONFLICT` — the conflict text becomes `formError`.
 * - anything else / non-classified — `formError` is the error's message, or
 *   a generic save-failure fallback when there is none.
 */
function mapSubmitError(error: unknown): {
  fieldErrors: Record<string, string>
  formError: string
} {
  const fieldErrors: Record<string, string> = {}
  let formError = SAVE_FAILED_MESSAGE

  if (isClassifiedErrorInstance(error)) {
    if (error.category === 'VALIDATION_ERROR' || error.category === 'POLICY_VIOLATION') {
      const unattributed: string[] = []
      for (const fieldError of error.fieldErrors) {
        if (ATTRIBUTABLE_FIELDS.includes(fieldError.field)) {
          fieldErrors[fieldError.field] = fieldError.message
        } else {
          unattributed.push(fieldError.message)
        }
      }
      formError = unattributed.length > 0 ? unattributed.join(' ') : error.message
    } else {
      formError = error.message
    }
  } else if (error instanceof Error && error.message.length > 0) {
    formError = error.message
  }

  return { fieldErrors, formError }
}

/**
 * Shared shell for both views (frontend-spec.md §4.5): heading, the back
 * link to the travel request list (P-03), and the content container.
 */
function PageShell({ heading, children }: { heading: string; children: ReactNode }) {
  return (
    <section className="screen" data-screen="travel-request-new" data-section="travel">
      <Link to="/travel-requests" className="back-link">
        <i className="icon icon-arrow-left" />
        Back to Travel Requests
      </Link>
      <header className="page-header">
        <div>
          <h1>{heading}</h1>
        </div>
      </header>
      {children}
    </section>
  )
}

/**
 * P-05 New Travel Request (`/travel-requests/new`): blank form, submits via
 * `create` and navigates to the new request's detail page (P-04).
 */
function CreateView() {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [serverFieldErrors, setServerFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  // Q10: there is no request id before the first save and no pre-create
  // upload endpoint, so document upload is unavailable until the request
  // exists (the upload service requires an id).
  const handleUpload = (): Promise<never> =>
    Promise.reject(new Error('Documents can be uploaded after the travel request is saved.'))

  const handleSubmit = async (payload: TravelRequestCreateRequest): Promise<void> => {
    setSubmitting(true)
    setServerFieldErrors({})
    setFormError(null)
    try {
      const response = await create(payload)
      navigate(`/travel-requests/${response.id}`)
    } catch (error) {
      const mapped = mapSubmitError(error)
      setServerFieldErrors(mapped.fieldErrors)
      setFormError(mapped.formError)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PageShell heading="New Travel Request">
      <TravelRequestForm
        mode="create"
        submitting={submitting}
        serverFieldErrors={serverFieldErrors}
        formError={formError}
        onUpload={handleUpload}
        onSubmit={handleSubmit}
      />
    </PageShell>
  )
}

/**
 * P-06 Edit/Resubmit (`/travel-requests/:id/edit`): loads the request via
 * `useTravelRequest` and pre-fills the form — `REJECTED` requests render in
 * `resubmit` mode with the prior rejection reason (frontend-clarification.md
 * Q12). Fetch errors follow the P-06 mapping (frontend-api-mapping.md §1.1):
 * `RESOURCE_NOT_FOUND` → not-found fallback, `FORBIDDEN_ACTION` → redirect
 * to `/access-denied`, anything else → error banner.
 */
function EditView({ id }: { id: string }) {
  const navigate = useNavigate()
  const { data, isLoading, error, refetch } = useTravelRequest(id)
  const [submitting, setSubmitting] = useState(false)
  const [serverFieldErrors, setServerFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  // FORBIDDEN_ACTION (e.g. the record left PENDING/REJECTED via a decision
  // made elsewhere): redirect to the access-denied screen instead of the form.
  useEffect(() => {
    if (
      error !== null &&
      isClassifiedErrorInstance(error) &&
      error.category === 'FORBIDDEN_ACTION'
    ) {
      navigate('/access-denied')
    }
  }, [error, navigate])

  if (error !== null) {
    if (isClassifiedErrorInstance(error)) {
      if (error.category === 'RESOURCE_NOT_FOUND') return <NotFoundPage />
      if (error.category === 'FORBIDDEN_ACTION') return null
    }
    return <ErrorBanner message={error.message} />
  }

  // Initial load: `data` is null until the first successful fetch (failed
  // fetches set `error`, handled above), so this branch is the `isLoading`
  // state — a post-conflict `refetch` keeps `data` non-null, so the form
  // stays mounted instead of flashing back to the skeleton.
  if (data === null) {
    return isLoading ? <LoadingSkeleton shape="form" /> : null
  }

  const mode = data.status === 'REJECTED' ? 'resubmit' : 'edit'
  const heading = mode === 'resubmit' ? 'Resubmit Travel Request' : 'Edit Travel Request'

  // Q10: uploads require the request id — wire the immediate-on-selection
  // upload to `uploadDocument` and project the `DocumentSummary` (its id
  // field is `id`, not `documentId`) onto the control's shape.
  const handleUpload = (file: File) =>
    uploadDocument(id, { file, fileName: file.name }).then((summary) => ({
      documentId: summary.id,
      fileName: summary.fileName,
      uploadStatus: summary.uploadStatus,
    }))

  const handleSubmit = async (payload: TravelRequestCreateRequest): Promise<void> => {
    setSubmitting(true)
    setServerFieldErrors({})
    setFormError(null)
    try {
      await update(id, payload)
      navigate(`/travel-requests/${id}`)
    } catch (error) {
      const mapped = mapSubmitError(error)
      setServerFieldErrors(mapped.fieldErrors)
      setFormError(mapped.formError)
      // 409: the record is no longer editable — sync the displayed status
      // after showing the conflict message.
      if (isClassifiedErrorInstance(error) && error.category === 'CONFLICT') {
        refetch()
      }
    } finally {
      setSubmitting(false)
    }
  }

  const initialValues: TravelRequestFormValues = {
    destination: data.destination,
    startDate: data.startDate,
    endDate: data.endDate,
    justification: data.justification,
    documents: data.documents.map((document) => ({ documentId: document.id })),
  }

  return (
    <PageShell heading={heading}>
      <TravelRequestForm
        key={data.id}
        mode={mode}
        initialValues={initialValues}
        rejectionReason={data.rejectionReason}
        submitting={submitting}
        serverFieldErrors={serverFieldErrors}
        formError={formError}
        onUpload={handleUpload}
        onSubmit={handleSubmit}
      />
    </PageShell>
  )
}

/**
 * P-05/P-06 page entry (frontend-api-mapping.md §1.1): `/travel-requests/new`
 * renders the create view, `/travel-requests/:id/edit` the edit/resubmit
 * view. Splitting on the route param (instead of a conditional hook inside
 * one component) keeps `useTravelRequest` unconditional and mounts the form
 * only when the record exists.
 */
export function TravelRequestFormPage() {
  const { id } = useParams<{ id: string }>()
  return id === undefined ? <CreateView /> : <EditView id={id} />
}
