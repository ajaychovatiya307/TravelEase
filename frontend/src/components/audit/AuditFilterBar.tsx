// Audit log filter bar (I-15, frontend-tasks.md — "I. Pages") for the read-only
// Audit Log Viewer screen (P-20, frontend-spec.md §4.13): a paginated audit
// table filterable by entity type, entity id, actor, and date range. No
// create/edit/delete controls exist here or on the page (audit records are
// immutable — data-model.md §6.1).
//
// Filter-key contract with `useAudit.ts` `toListParams` (F-06,
// frontend-api-mapping.md §1.4): the committed filters are exactly
// `entityType` / `entityId` / `actorId` / `from` / `to` — the only keys the
// list hook recognizes — and an empty string means "no filter on this key".
// The PAGE (P-20) owns the URL query state via `useListQueryState` (D-04):
// `onApply` should feed `setFilters` (replaces every filter, resets `page`
// to 0) and `onClear` should feed `clearAllFilters`. This component never
// imports the hook and never touches the network — it is fully controlled
// via props.
//
// Draft/commit model (deterministic for tests): the inputs edit local draft
// state, initialized from the applied `filters` prop and re-synced whenever
// the applied filter VALUES change (a render-time value comparison against
// the previous render's prop — React's documented "adjust state when a prop
// changes" pattern — so a page re-render with an equal-valued object literal
// never clobbers in-progress edits). "Apply Filters" trims the values and commits via
// `onApply` — button click only, never on keystroke. "Clear Filters" resets
// the draft to all-empty and calls `onClear`. `disabled` (e.g. while the
// page's table is loading) disables every control via the wrapping
// `<fieldset>`.

import { useState } from 'react'

import { DateField, SelectField, TextField } from '../../shared/FormField'
import type { SelectOption } from '../../shared/FormField'

import './AuditFilterBar.css'

/**
 * The five applied filter values. The keys are the exact `useAudit.ts`
 * `toListParams` contract (frontend-api-mapping.md §1.4); an empty string
 * means "not filtered on this key".
 */
export interface AuditFilterValues {
  /** `AuditEntityType` (models/audit.ts — E-05) or '' for "all". */
  entityType: string
  entityId: string
  actorId: string
  /** Inclusive lower bound on `occurredAt` (YYYY-MM-DD). */
  from: string
  /** Inclusive upper bound on `occurredAt` (YYYY-MM-DD). */
  to: string
}

export interface AuditFilterBarProps {
  /** Currently applied filter values (empty string = not filtered). */
  filters: AuditFilterValues
  /** Commit the (trimmed) draft — the page feeds this to `setFilters` (D-04). */
  onApply: (filters: AuditFilterValues) => void
  /**
   * Called after the draft is reset to all-empty — the page feeds this to
   * `clearAllFilters` (D-04).
   */
  onClear: () => void
  /** Disable every control (e.g. while the page's table is loading). */
  disabled?: boolean
  /** Offer the administrative entity types (#13); only administrators may query them. */
  includeAdministrative?: boolean
}

/** All-empty draft — the unfiltered default P-20 view. */
const EMPTY_FILTERS: AuditFilterValues = {
  entityType: '',
  entityId: '',
  actorId: '',
  from: '',
  to: '',
}

/** Field `id`s — must stay unique on the page (shared FormField contract). */
const FIELD_IDS = {
  entityType: 'audit-filter-entity-type',
  entityId: 'audit-filter-entity-id',
  actorId: 'audit-filter-actor-id',
  from: 'audit-filter-from',
  to: 'audit-filter-to',
} as const

/** Workflow `AuditEntityType` values (models/audit.ts — E-05); "All" is the empty value. */
const ENTITY_TYPE_OPTIONS: SelectOption[] = [
  { value: 'TRAVEL_REQUEST', label: 'Travel Request' },
  { value: 'EXPENSE_CLAIM', label: 'Expense Claim' },
]

/** Administrative entity types (#13), offered to administrators only. */
const ADMIN_ENTITY_TYPE_OPTIONS: SelectOption[] = [
  { value: 'USER_ACCOUNT', label: 'User Account' },
  { value: 'EMPLOYEE_MANAGER_MAPPING', label: 'Employee–Manager Mapping' },
  { value: 'POLICY_CONFIG', label: 'Policy Configuration' },
  { value: 'EXPENSE_CATEGORY', label: 'Expense Category' },
  { value: 'TRAVEL_REQUEST_POLICY', label: 'Travel Request Policy' },
]

function hasSameValues(a: AuditFilterValues, b: AuditFilterValues): boolean {
  return (
    a.entityType === b.entityType &&
    a.entityId === b.entityId &&
    a.actorId === b.actorId &&
    a.from === b.from &&
    a.to === b.to
  )
}

/**
 * P-20 audit log filters (frontend-spec.md §4.13): Entity Type, Entity Id,
 * Actor Id, and a From/To date range — all optional. Edits stay in a local
 * draft until "Apply Filters" commits them via `onApply`.
 */
export function AuditFilterBar({
  filters,
  onApply,
  onClear,
  disabled = false,
  includeAdministrative = false,
}: AuditFilterBarProps) {
  const [draft, setDraft] = useState<AuditFilterValues>(filters)
  // The `filters` value as of the previous render — used to detect an actual
  // change of the APPLIED filter values (page navigation, an external clear,
  // or a post-apply normalization) and re-sync the draft on the very next
  // render (React's documented "adjust state when a prop changes" pattern —
  // no effect, so in-progress edits survive equal-valued re-renders).
  const [prevFilters, setPrevFilters] = useState<AuditFilterValues>(filters)

  if (!hasSameValues(prevFilters, filters)) {
    setPrevFilters(filters)
    setDraft(filters)
  }

  const updateDraft = (key: keyof AuditFilterValues) => (value: string) => {
    setDraft((prev) => ({ ...prev, [key]: value }))
  }

  const handleApply = () => {
    // Trim the values; an empty string means "remove this filter".
    onApply({
      entityType: draft.entityType.trim(),
      entityId: draft.entityId.trim(),
      actorId: draft.actorId.trim(),
      from: draft.from.trim(),
      to: draft.to.trim(),
    })
  }

  const handleClear = () => {
    setDraft({ ...EMPTY_FILTERS })
    onClear()
  }

  return (
    <fieldset className="audit-filter-bar" disabled={disabled}>
      <legend className="audit-filter-bar__legend">Filters</legend>

      <div className="audit-filter-bar__fields">
        <SelectField
          id={FIELD_IDS.entityType}
          label="Entity Type"
          value={draft.entityType}
          onChange={updateDraft('entityType')}
          options={
            includeAdministrative
              ? [...ENTITY_TYPE_OPTIONS, ...ADMIN_ENTITY_TYPE_OPTIONS]
              : ENTITY_TYPE_OPTIONS
          }
          placeholder="All"
        />
        <TextField
          id={FIELD_IDS.entityId}
          label="Entity Id"
          value={draft.entityId}
          onChange={updateDraft('entityId')}
          placeholder="e.g. TR-101"
        />
        <TextField
          id={FIELD_IDS.actorId}
          label="Actor Id"
          value={draft.actorId}
          onChange={updateDraft('actorId')}
          placeholder="e.g. U-42"
        />
        <DateField
          id={FIELD_IDS.from}
          label="From"
          value={draft.from}
          onChange={updateDraft('from')}
        />
        <DateField id={FIELD_IDS.to} label="To" value={draft.to} onChange={updateDraft('to')} />
      </div>

      <div className="audit-filter-bar__actions">
        <button
          type="button"
          className="button button-primary audit-filter-bar__apply"
          onClick={handleApply}
          disabled={disabled}
        >
          Apply Filters
        </button>
        <button
          type="button"
          className="button button-secondary audit-filter-bar__clear"
          onClick={handleClear}
          disabled={disabled}
        >
          Clear Filters
        </button>
      </div>
    </fieldset>
  )
}
