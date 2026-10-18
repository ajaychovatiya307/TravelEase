// Travel request form validation schema (L-01, frontend-tasks.md — "L. Validation").
// Client pre-submit rules for `TravelRequestForm` values, per frontend-spec.md
// §7 (VAL-01–04) and the client rule column of frontend-api-mapping.md §3
// (Validation Mappings):
//   VAL-01 `destination`     — required, non-blank
//   VAL-02 `startDate`/`endDate` — required, `YYYY-MM-DD`, `startDate <= endDate`,
//              `startDate` not in the past (client pre-check)
//   VAL-03 `justification`   — required, minimum length
//   VAL-04 `documents`       — optional client-side: required only if policy
//              mandates, which the client cannot always know pre-fetch, so the
//              server remains authoritative
//
// The server is always the final outcome (400/422 — frontend-constitution.md
// §8); these checks only give fast, field-specific feedback before submit.
// `toTravelRequestCreateRequest` applies the TR-01 transformation
// (frontend-api-mapping.md §2) to map validated values to
// `TravelRequestCreateRequest` (models/travelRequest.ts — E-01).

import { z } from 'zod'

import type { TravelRequestCreateRequest } from '../models/travelRequest'

/**
 * VAL-03: minimum justification length (characters). Exposed as a named
 * constant because the spec describes the minimum as configurable; the
 * default mirrors the server `@Size(min = 10, max = 2000)` constraint
 * (api-contract.md §6, VAL-03) so the pre-check matches the authoritative
 * rule.
 */
export const JUSTIFICATION_MIN_LENGTH = 10

/** VAL-03: maximum justification length (characters). */
export const JUSTIFICATION_MAX_LENGTH = 2000

/** Contract `date` values are `YYYY-MM-DD` ISO date strings (api-contract.md). */
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/**
 * Formats a `Date` as a local `YYYY-MM-DD` string. Used for the VAL-02
 * "not in the past" boundary and the form's "start date = today" default
 * (frontend-test.md FRM-03).
 */
export function formatIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** Today's local date as `YYYY-MM-DD` (VAL-02 client pre-check boundary). */
export function todayIsoDate(): string {
  return formatIsoDate(new Date())
}

/** `true` when the `YYYY-MM-DD` string is a real calendar date. */
function isRealCalendarDate(value: string): boolean {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
}

/**
 * One uploaded-document reference held by `TravelRequestForm` (TR-01:
 * `uploadedDocuments[].documentId` → `documentIds: string[]`). `looseObject`
 * so the form can keep extra per-file state (name, upload status) next to
 * the `documentId`.
 */
export const uploadedDocumentReferenceSchema = z.looseObject({
  documentId: z.string().min(1, { error: 'A document reference requires a documentId' }),
})

export type UploadedDocumentReference = z.infer<typeof uploadedDocumentReferenceSchema>

/** VAL-02: a required `YYYY-MM-DD` calendar date (cross-field rules below). */
const requiredIsoDate = z
  .string()
  .min(1, { error: 'Date is required' })
  .regex(ISO_DATE_PATTERN, { error: 'Date must be in YYYY-MM-DD format' })
  .refine(isRealCalendarDate, { error: 'Date must be a valid calendar date' })

export interface TravelRequestValidationOptions {
  today?: string
  documentsRequired?: boolean
}

export function createTravelRequestFormSchema(
  options: TravelRequestValidationOptions = {},
) {
  return z
    .object({
      /** VAL-01: required, non-blank. */
      destination: z
        .string()
        .refine((value) => value.trim().length > 0, { error: 'Destination is required' }),
      /** VAL-02: required, `YYYY-MM-DD`; order/past checks applied via `superRefine`. */
      startDate: requiredIsoDate,
      /** VAL-02: required, `YYYY-MM-DD`; order/past checks applied via `superRefine`. */
      endDate: requiredIsoDate,
      /** VAL-03: required; length bounded per api-contract.md §6 (VAL-03). */
      justification: z
        .string()
        .min(JUSTIFICATION_MIN_LENGTH, {
          error: `Justification must be at least ${JUSTIFICATION_MIN_LENGTH} characters`,
        })
        .max(JUSTIFICATION_MAX_LENGTH, {
          error: `Justification must be at most ${JUSTIFICATION_MAX_LENGTH} characters`,
        }),
      /** VAL-04: optional unless the active policy explicitly requires a document. */
      documents: z.array(uploadedDocumentReferenceSchema).optional(),
    })
    .superRefine((values, ctx) => {
      if (values.startDate > values.endDate) {
        ctx.addIssue({
          code: 'custom',
          path: ['endDate'],
          message: 'End date must be on or after the start date',
        })
      }
      if (values.startDate < (options.today ?? todayIsoDate())) {
        ctx.addIssue({
          code: 'custom',
          path: ['startDate'],
          message: 'Start date cannot be in the past',
        })
      }
      if (
        options.documentsRequired === true &&
        !(values.documents ?? []).some((document) => document.documentId.trim().length > 0)
      ) {
        ctx.addIssue({
          code: 'custom',
          path: ['documents'],
          message: 'A document is required by the active policy',
        })
      }
    })
}

export const travelRequestFormSchema = createTravelRequestFormSchema()

/** `TravelRequestForm` values (L-01) — the shape the form (a later task) binds. */
export type TravelRequestFormValues = z.infer<typeof travelRequestFormSchema>

/**
 * TR-01 (frontend-api-mapping.md §2): maps validated `TravelRequestForm`
 * values to `TravelRequestCreateRequest`/`TravelRequestUpdateRequest` — trims
 * string fields, keeps the schema-validated `YYYY-MM-DD` dates unchanged (the
 * form binds native date inputs, which emit `YYYY-MM-DD`), maps
 * `documents[].documentId` → `documentIds`, and omits `documentIds` entirely
 * when empty.
 */
export function toTravelRequestCreateRequest(
  values: TravelRequestFormValues,
): TravelRequestCreateRequest {
  const documentIds = (values.documents ?? [])
    .map((document) => document.documentId)
    .filter((documentId) => documentId.trim().length > 0)

  const request: TravelRequestCreateRequest = {
    destination: values.destination.trim(),
    startDate: values.startDate,
    endDate: values.endDate,
    justification: values.justification.trim(),
  }

  if (documentIds.length > 0) {
    request.documentIds = documentIds
  }

  return request
}
