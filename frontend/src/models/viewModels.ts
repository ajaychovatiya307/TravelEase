// View-model types (E-07).
// Page/component-facing display shapes per ui-model.md §7 (frontend-plan.md
// §8): additive, display-only derivations computed from the response DTOs in
// this folder — never replacing or merged into them (ui-model.md §8 note).
// Each DTO-derived view model references its source DTO type by extending it;
// DTO fields are never re-declared here.
//
// Types only: the DTO → view-model mapping is owned by the page-level
// data-fetching hooks (ui-model.md §7/§9) in later tasks. No mappers,
// validation schemas, or services belong in this file.
//
// Contract `date`/`timestamp` fields on the source DTOs are `string`
// (ISO 8601); the formatted display variants added here are `string` too.

import type { RoleType } from './admin'
import type { AuditRecordResponse } from './audit'
import type { DocumentSummary } from './document'
import type { ExpenseClaimResponse } from './expenseClaim'
import type { DecisionType, TravelRequestResponse } from './travelRequest'

/**
 * ui-model.md §7: Travel Request Table row (P-03/P-08). Derived from
 * `TravelRequestResponse` plus flattened display fields; the remaining table
 * columns (Destination, Approver, Created At) are read directly from the DTO.
 */
export interface TravelRequestRowView extends TravelRequestResponse {
  /** Flattened display field: "Start–End" date range formatted from `startDate`/`endDate`. */
  dateRange: string
  /** Flattened display field: display label for `status` (rendered by `StatusBadge`). */
  statusLabel: string
}

/**
 * ui-model.md §7: one entry of `TravelRequestDetailView.approvalHistory`.
 * Mirrors backend/data-model.md §3.3 (`ApprovalDecision`) with internal-only
 * fields (`id`, `travelRequestId`) omitted per Constitution §6. The group is
 * chronological (`decidedAt` ascending) per data-model.md §3.3.
 */
export interface ApprovalHistoryEntry {
  /** Identifier of the user who made the decision (entity field `decidedBy`). */
  decidedBy: string
  decision: DecisionType
  /** Required when `decision` is `REJECTED` (VAL-12); null when `APPROVED`. */
  reason: string | null
  /** ISO 8601 timestamp of the decision (entity field `decidedAt`). */
  decidedAt: string
}

/**
 * ui-model.md §7: P-04 Travel Request Detail view. Derived from
 * `TravelRequestResponse` plus:
 * - "grouped `documents`" — the DTO's `DocumentSummary[]` reshaped into
 *   display-ready `DocumentView` items (the detail page's `DocumentList`
 *   composes one `DocumentItem` per `DocumentView`, ui-model.md §10);
 * - "grouped `approvalHistory` (if exposed)" — chronological
 *   `ApprovalDecision` entries for `ApprovalHistoryList` (ui-model.md §2/§10,
 *   frontend-spec.md §4.4), optional because `TravelRequestResponse`
 *   (api-contract.md §5.3) does not expose approval history yet;
 * - computed `isEditable`/`isResubmittable`/`isDecidable` flags (derived from
 *   status + session role/ownership) driving the P-04 `ActionBar`.
 */
export interface TravelRequestDetailView extends TravelRequestResponse {
  /** Reshaped `documents`: one display-ready `DocumentView` per uploaded document. */
  documents: DocumentView[]
  /** Chronological approval history; present only when exposed by the API ("if exposed"). */
  approvalHistory?: ApprovalHistoryEntry[]
  /** Computed flag: owner + `PENDING` (frontend-spec.md §4.4). */
  isEditable: boolean
  /** Computed flag: owner + `REJECTED` (frontend-spec.md §4.4). */
  isResubmittable: boolean
  /** Computed flag: assigned approver + `PENDING` (frontend-spec.md §4.4). */
  isDecidable: boolean
}

/**
 * ui-model.md §7: Expense Claim Table row (P-09/P-14). Derived from
 * `ExpenseClaimResponse`; the remaining table columns (Travel Request ref,
 * Amount, Currency, Category, Expense Date, Status) are read directly from
 * the DTO, so the only flattened display field is the `isDuplicate`
 * passthrough (rendered by `DuplicateIndicator`).
 */
export interface ExpenseRowView extends ExpenseClaimResponse {
  /** Computed boolean passthrough of `duplicateFlag` (potential-duplicate indicator). */
  isDuplicate: boolean
}

/**
 * ui-model.md §7: P-10 Expense Claim Detail view. Derived from
 * `ExpenseClaimResponse` plus the computed action flags driving the P-10
 * `ActionBar` (ui-model.md §2/§10), each derived from status + session
 * role/ownership: Edit (owner + `PENDING`), Review (Finance + `PENDING`),
 * Reimburse (Finance + `APPROVED`, FR-FIN-03).
 */
export interface ExpenseDetailView extends ExpenseClaimResponse {
  /** Computed flag: owner + `PENDING`. */
  isEditable: boolean
  /** Computed flag: Finance + `PENDING`. */
  isReviewable: boolean
  /** Computed flag: Finance + `APPROVED`. */
  isReimbursable: boolean
}

/**
 * ui-model.md §7: display-ready document metadata for `DocumentList`/
 * `DocumentItem` (P-04, P-10) and `FileUploadControl` (P-05, P-06, P-11,
 * P-12). Derived from `DocumentSummary` plus the human-readable file size and
 * the status label/icon mapping for `uploadStatus`.
 */
export interface DocumentView extends DocumentSummary {
  /** Human-readable file size formatted from `fileSizeBytes`. */
  fileSizeLabel: string
  /** Display label for `uploadStatus`. */
  uploadStatusLabel: string
  /** Display icon for `uploadStatus`. */
  uploadStatusIcon: string
}

/**
 * ui-model.md §7: Audit Log Table row (P-20). Derived from
 * `AuditRecordResponse`; the remaining columns (Entity Type, Entity Id,
 * Actor, Before → After Status) are read directly from the DTO, with the
 * formatted timestamp and human-readable action label added for rendering.
 */
export interface AuditRowView extends AuditRecordResponse {
  /** Formatted timestamp for display, derived from `occurredAt` (ISO 8601 string). */
  occurredAtFormatted: string
  /** Human-readable label for `action` (e.g. "Created", "Approved"). */
  actionLabel: string
}

/**
 * ui-model.md §7: P-02 Dashboard summary. Not derived from a single DTO —
 * computed in the dashboard hook from aggregated counts of `Pending` records
 * returned by the Travel Request/Expense list endpoints, one count per role
 * section (frontend-spec.md §4.2). Sections render conditionally via
 * `RoleGate`; the "Admin Shortcuts" section (Admin) is a static link set and
 * carries no counts.
 */
export interface DashboardSummaryView {
  /** "My Travel Requests & Expenses" (always): caller's own `PENDING` travel requests. */
  pendingTravelRequests: number
  /** "My Travel Requests & Expenses" (always): caller's own `PENDING` expense claims. */
  pendingExpenses: number
  /** "Pending My Approval" (MANAGER): `PENDING` travel requests assigned to the caller. */
  pendingApprovals: number
  /** "Finance Review Queue" (FINANCE): `PENDING` expense claims awaiting review. */
  pendingExpenseReviews: number
}

/**
 * ui-model.md §7: session shape for `AuthContext` consumers. Derived from
 * the decoded token claims (`userId`, `roles`) with the normalized
 * (deduplicated) role set used by `RoleGate`/guards (ui-model.md §9/§10).
 */
export interface SessionView {
  /** Decoded token claim `userId`. */
  userId: string
  /** Normalized role set from the decoded token claim `roles`. */
  roles: RoleType[]
}
