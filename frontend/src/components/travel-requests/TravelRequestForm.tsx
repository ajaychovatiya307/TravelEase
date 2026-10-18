// Travel Request create/edit/resubmit form (J-01, frontend-tasks.md —
// "J. Forms"). Single-step form (frontend-clarification.md Q9) for the
// P-05/P-06 screens (frontend-spec.md §4.5, §5): destination, start date,
// end date, justification, and the document upload section.
//
// Validation is schema-driven (frontend-constitution.md §7): react-hook-form
// + `zodResolver(travelRequestFormSchema)` (validation/travelRequestSchema.ts
// — L-01; frontend-spec.md §7 VAL-01–04). The submit payload is produced by
// the TR-01 transformation (frontend-api-mapping.md §2) via
// `toTravelRequestCreateRequest` — create and update share the same
// `TravelRequestCreateRequest`/`TravelRequestUpdateRequest` shape
// (api-contract.md §5.1/§5.2).
//
// Modes (frontend-clarification.md Q11/Q12): `create` starts blank (the page
// passes no `initialValues`), `edit` pre-fills from `initialValues`, and
// `resubmit` additionally shows the prior rejection reason via
// `RejectionReasonBanner`.
//
// Server errors (frontend-api-mapping.md §3/§4): the page maps
// `fieldErrors[].field` ('destination' | 'startDate' | 'endDate' |
// 'justification' | 'documentIds') into `serverFieldErrors` — rendered inline
// on the matching field (under the upload section for 'documentIds') whenever
// the field has no client error — and non-attributable errors as `formError`
// in the form-level `ErrorBanner`. `409 CONFLICT` (edit of a record that is
// no longer `PENDING`/`REJECTED`) is handled by the page
// (frontend-api-mapping.md §1.1, P-06), not here.
//
// The page owns the service calls (F-02): it supplies `onUpload` (immediate
// upload on selection — Q10, shared `FileUploadControl` — C-07) and the
// `submitting` flag; this component never touches the network.

import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'

import { FileUploadControl } from '../../shared/FileUploadControl'
import type { UploadedDocument } from '../../shared/FileUploadControl'
import { DateField, TextField, TextareaField } from '../../shared/FormField'
import type { TravelRequestCreateRequest } from '../../models/travelRequest'
import {
  createTravelRequestFormSchema,
  toTravelRequestCreateRequest,
  todayIsoDate,
} from '../../validation/travelRequestSchema'
import type {
  TravelRequestFormValues,
  UploadedDocumentReference,
} from '../../validation/travelRequestSchema'

import { RejectionReasonBanner } from './RejectionReasonBanner'

/** P-05/P-06 screen mode (frontend-spec.md §4.5). */
export type TravelRequestFormMode = 'create' | 'edit' | 'resubmit'

export interface TravelRequestFormProps {
  /**
   * `create` — blank form; `edit` — pre-filled from `initialValues` (Q11);
   * `resubmit` — pre-filled plus the prior rejection banner (Q12).
   */
  mode: TravelRequestFormMode
  /** Existing values for edit/resubmit pre-fill; omit for a blank create. */
  initialValues?: TravelRequestFormValues
  /** Prior rejection reason for the resubmit banner (Q12); ignored otherwise. */
  rejectionReason?: string | null
  /** Require a confirmed document when the active travel policy says so. */
  documentsRequired?: boolean
  /**
   * Server field errors keyed by contract field name
   * (`fieldErrors[].field` — frontend-api-mapping.md §3): 'destination',
   * 'startDate', 'endDate', 'justification', or 'documentIds'. Shown inline
   * on the matching field when it has no client error; the page clears them.
   */
  serverFieldErrors?: Record<string, string>
  /** Form-level message for non-attributable server errors (api-mapping §3/§4). */
  formError?: string | null
  /** True while the page's create/update request is in flight. */
  submitting?: boolean
  /** Immediate-on-selection upload (Q10); wired to the service by the page. */
  onUpload: (file: File) => Promise<UploadedDocument>
  /** Invoked with the TR-01 request DTO once client validation passes. */
  onSubmit: (payload: TravelRequestCreateRequest) => Promise<void> | void
}

/** Field `id`s — must stay unique on the page (shared FormField contract). */
const FIELD_IDS = {
  destination: 'travel-request-destination',
  startDate: 'travel-request-start-date',
  endDate: 'travel-request-end-date',
  justification: 'travel-request-justification',
  documents: 'travel-request-documents',
} as const

/** Blank values for create mode (frontend-spec.md §4.5: create starts empty). */
const EMPTY_VALUES: TravelRequestFormValues = {
  destination: '',
  startDate: '',
  endDate: '',
  justification: '',
}

const SUBMIT_LABELS: Record<TravelRequestFormMode, string> = {
  create: 'Create Travel Request',
  edit: 'Save Changes',
  resubmit: 'Resubmit Travel Request',
}

/**
 * Single-step travel request form. Required-field errors (and any server
 * field errors) block the submit button / inline-report on the field; a
 * valid submit calls `onSubmit` with the TR-01 request DTO.
 */
export function TravelRequestForm({
  mode,
  initialValues,
  rejectionReason,
  documentsRequired = false,
  serverFieldErrors,
  formError,
  submitting = false,
  onUpload,
  onSubmit,
}: TravelRequestFormProps) {
  const {
    control,
    handleSubmit,
    setValue,
    formState: { errors, isValid },
  } = useForm<TravelRequestFormValues>({
    resolver: zodResolver(createTravelRequestFormSchema({ documentsRequired })),
    defaultValues: initialValues ?? EMPTY_VALUES,
    // Inline errors surface after the first blur; `isValid` stays live so the
    // submit button can track required-field validity (frontend-spec.md §4.5).
    mode: 'onTouched',
  })

  // Documents selected in this session, as reported by FileUploadControl
  // (each entry carries its per-file upload status — C-07).
  const [sessionDocuments, setSessionDocuments] = useState<UploadedDocument[]>([])

  const isUploading = sessionDocuments.some((document) => document.uploadStatus === 'PENDING')

  // TR-01 document references: previously uploaded (edit/resubmit) references
  // are kept, plus this session's uploads that reached CONFIRMED status.
  // PENDING uploads disable the submit button, and FAILED ones have no
  // confirmed `documentId` yet (the user retries them in the control).
  const documentReferences: UploadedDocumentReference[] = [
    ...(initialValues?.documents ?? []),
    ...sessionDocuments.filter((document) => document.uploadStatus === 'CONFIRMED'),
  ].map((document) => ({ documentId: document.documentId }))

  const documentIdsError = serverFieldErrors?.['documentIds']
  const policyDocumentMissing =
    documentsRequired &&
    !documentReferences.some((document) => document.documentId.trim().length > 0)
  const documentsError =
    errors.documents?.message ??
    documentIdsError ??
    (policyDocumentMissing ? 'A document is required by the active policy' : undefined)

  const handleDocumentsChange = (documents: UploadedDocument[]) => {
    setSessionDocuments(documents)
    const references = [
      ...(initialValues?.documents ?? []),
      ...documents.filter((document) => document.uploadStatus === 'CONFIRMED'),
    ].map((document) => ({ documentId: document.documentId }))
    setValue('documents', references, { shouldValidate: true, shouldDirty: true })
  }

  const onSubmitRequest = handleSubmit((values) => {
    return onSubmit(toTravelRequestCreateRequest({ ...values, documents: documentReferences }))
  })

  return (
    <form className="card card-narrow" onSubmit={onSubmitRequest}>
      {mode === 'resubmit' ? <RejectionReasonBanner reason={rejectionReason} /> : null}
      {formError ? (
        <p className="empty-state required" role="alert" style={{ fontSize: 13 }}>
          {formError}
        </p>
      ) : null}
      <div className="card-body form-grid">
        <Controller
          control={control}
          name="destination"
          render={({ field, fieldState }) => (
            <TextField
              id={FIELD_IDS.destination}
              label="Destination"
              wide
              icon="map-pin"
              required
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              error={fieldState.error?.message ?? serverFieldErrors?.['destination']}
            />
          )}
        />

        <Controller
          control={control}
          name="startDate"
          render={({ field, fieldState }) => (
            <DateField
              id={FIELD_IDS.startDate}
              label="Start date"
              required
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              min={todayIsoDate()}
              error={fieldState.error?.message ?? serverFieldErrors?.['startDate']}
            />
          )}
        />

        <Controller
          control={control}
          name="endDate"
          render={({ field, fieldState }) => (
            <DateField
              id={FIELD_IDS.endDate}
              label="End date"
              required
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              error={fieldState.error?.message ?? serverFieldErrors?.['endDate']}
            />
          )}
        />

        <Controller
          control={control}
          name="justification"
          render={({ field, fieldState }) => (
            <TextareaField
              id={FIELD_IDS.justification}
              label="Justification"
              wide
              required
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              error={fieldState.error?.message ?? serverFieldErrors?.['justification']}
            />
          )}
        />

        <div className="field field-wide">
          <FileUploadControl
            id={FIELD_IDS.documents}
            label="Documents"
            onUpload={onUpload}
            onChange={handleDocumentsChange}
          />
          {documentsError ? (
            <p
              id={`${FIELD_IDS.documents}-server-error`}
              role="alert"
              className="required"
              style={{ fontSize: 13 }}
            >
              {documentsError}
            </p>
          ) : null}
        </div>
      </div>

      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={!isValid || isUploading || submitting}
        >
          {SUBMIT_LABELS[mode]}
        </button>
      </div>
    </form>
  )
}
