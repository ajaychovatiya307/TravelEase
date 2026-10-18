// Travel request picker for the New / Edit Expense Claim pages (I-11,
// frontend-tasks.md; screens P-11/P-12 per frontend-spec.md §4.9).
//
// Links the expense claim to an approved trip: lists only the caller's own
// `Approved` travel requests (VAL-09 — travel request selection is restricted
// to the Employee's own `Approved` requests):
// - fetched via `useTravelRequestList` (F-06) with a static lookup query
//   state (same pattern as the form's P-11/P-12 lookups,
//   frontend-api-mapping.md §1.3): one fixed 50-row page filtered
//   server-side by `status=APPROVED` (BR-02), scoped to the session user
//   (`TravelRequestResponse.employeeId`);
// - controlled component — the selection state belongs to the host page
//   (I-11 ExpenseFormPage), which feeds the chosen id and the trip date range
//   into `ExpenseClaimForm` (J-03); this component never imports the form;
// - each option shows the request reference (id), the destination, and the
//   date range — the same `TravelRequestResponse` fields the P-03 table
//   renders through `TravelRequestRow`; contract dates are ISO 8601 strings,
//   so the display keeps the YYYY-MM-DD portion only (same convention as
//   `TravelRequestRow`);
// - loading / error / empty follow the ui-model.md §10 conventions
//   (`LoadingSkeleton`, `ErrorBanner` + `refetch` retry, `EmptyState`).

import { useTravelRequestList } from '../../hooks/useTravelRequests'
import type { ListQueryState } from '../../hooks/useListQueryState'
import type { TravelRequestResponse } from '../../models/travelRequest'

/**
 * Static lookup query state (frontend-api-mapping.md §1.3 P-11/P-12): one
 * fixed page of the caller's `APPROVED` travel requests (VAL-09). The picker
 * is not a user-paginated list view, so the state is a constant — no URL
 * sync (D-04 applies to list/table pages only) and no pagination controls.
 */
const PICKER_QUERY_STATE: ListQueryState = {
  page: 0,
  size: 50,
  sort: { field: 'createdAt', direction: 'desc' },
  filters: { status: 'APPROVED' },
}

/** Shared name of the single-select radio group. */
const RADIO_GROUP_NAME = 'travel-request-picker'

/** Contract dates are ISO 8601 strings; the display keeps the YYYY-MM-DD portion (same convention as TravelRequestRow). */
function displayDate(value: string): string {
  return value.slice(0, 10)
}

export interface TravelRequestPickerProps {
  /** Id of the currently linked travel request; owned by the host page (controlled component). */
  selectedId?: string
  /**
   * Called when the user picks an option, with the full `TravelRequestResponse`.
   * `null` is accepted so the host page can clear a selection programmatically;
   * the picker itself only ever emits a picked request.
   */
  onSelect: (request: TravelRequestResponse | null) => void
  /** Field label above the options (default "Travel request"). */
  label?: string
  /** Shows the required marker — travelRequestId is a required claim field (frontend-spec.md §5). */
  required?: boolean
  /** Host-owned inline field error (e.g. a VAL-09 "required" failure), rendered below the options. */
  error?: string | null
}

export function TravelRequestPicker({
  selectedId,
  onSelect,
  label = 'Travel request',
  required = true,
  error = null,
}: TravelRequestPickerProps) {
  // `MINE` scopes the lookup to the signed-in user's own trips (BR-02) even
  // when they are also a manager, on top of the fixed APPROVED filter in
  // PICKER_QUERY_STATE (VAL-09).
  const {
    data,
    isLoading,
    error: fetchError,
    refetch,
  } = useTravelRequestList(PICKER_QUERY_STATE, 'MINE')

  const requests = data?.content ?? []

  return (
    <div className="travel-request-picker" data-testid="travel-request-picker">
      {isLoading && !data ? (
        <p className="empty-state" role="status" data-testid="travel-request-picker-loading">
          Loading approved travel requests…
        </p>
      ) : fetchError ? (
        <div>
          <p
            className="empty-state required"
            role="alert"
            data-testid="travel-request-picker-error"
            style={{ fontSize: 13 }}
          >
            Your approved travel requests could not be loaded. Please try again.
          </p>
          <button className="button button-secondary button-small" type="button" onClick={refetch}>
            Try again
          </button>
          <button className="button button-secondary button-small" type="button" onClick={refetch}>
            Dismiss
          </button>
        </div>
      ) : requests.length === 0 ? (
        <p className="empty-state">No approved travel requests.</p>
      ) : (
        <fieldset
          className="field field-wide"
          aria-required={required || undefined}
          aria-describedby={error ? 'travel-request-picker-field-error' : undefined}
        >
          <legend className="field-label">
            {label}
            {required ? (
              <span className="required" aria-hidden="true">
                *
              </span>
            ) : null}
          </legend>
          <div className="picker">
            {requests.map((request) => (
              <label key={request.id} className="picker-option">
                <input
                  type="radio"
                  name={RADIO_GROUP_NAME}
                  value={request.id}
                  checked={request.id === selectedId}
                  onChange={() => onSelect(request)}
                  data-testid={`travel-request-picker-option-${request.id}`}
                />
                <span className="picker-id numeric">{request.id}</span>
                <span className="picker-destination">{request.destination}</span>
                <span className="picker-dates numeric">
                  {displayDate(request.startDate)} – {displayDate(request.endDate)}
                </span>
              </label>
            ))}
          </div>
        </fieldset>
      )}
      {error ? (
        <p
          id="travel-request-picker-field-error"
          role="alert"
          className="travel-request-picker__field-error"
          data-testid="travel-request-picker-field-error"
        >
          {error}
        </p>
      ) : null}
    </div>
  )
}
