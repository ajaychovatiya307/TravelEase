// Audit DTO and enums (E-05).
// Mirrors api-contract.md §5.16 and backend/data-model.md §6.1: field names,
// types, nullability, and enum string values must stay in sync with the
// contract. The enum string values also match the backend sources in
// common-lib (com.travelease.common.event.AuditEntityType /
// AuditActionType).
//
// `AuditRecordResponse` is a read-only shape (GET /api/audit) for the
// append-only audit trail (FR-AUD-01); no internal-only fields per
// Constitution §6. Contract `timestamp` (`occurredAt`) is `string` (ISO 8601).

/** api-contract.md §5.16 / data-model.md §6.1: AuditEntityType. */
export type AuditEntityType =
  | 'TRAVEL_REQUEST'
  | 'EXPENSE_CLAIM'
  // administrative records (#13) — visible to ADMIN only
  | 'USER_ACCOUNT'
  | 'EMPLOYEE_MANAGER_MAPPING'
  | 'POLICY_CONFIG'
  | 'EXPENSE_CATEGORY'
  | 'TRAVEL_REQUEST_POLICY'

/** api-contract.md §5.16 / data-model.md §6.1: AuditActionType. */
export type AuditActionType =
  | 'CREATED'
  | 'UPDATED'
  | 'APPROVED'
  | 'REJECTED'
  | 'RESUBMITTED'
  | 'REIMBURSED'
  | 'SUBMITTED'
  | 'CANCELLED'
  | 'REASSIGNED'
  | 'DEACTIVATED'
  | 'ACTIVATED'
  | 'ROLE_GRANTED'
  | 'ROLE_REVOKED'
  | 'PASSWORD_RESET'
  | 'PASSWORD_CHANGED'

/**
 * api-contract.md §5.16: read-only audit record (GET /api/audit). `beforeStatus`
 * is deliberately nullable — a `CREATED` action has no before-status;
 * `afterStatus` and `occurredAt` are always present.
 */
export interface AuditRecordResponse {
  id: string
  entityType: AuditEntityType
  entityId: string
  actorId: string
  action: AuditActionType
  beforeStatus: string | null
  /** Null for administrative records, which have no workflow status (#13). */
  afterStatus: string | null
  occurredAt: string
  /** Change summary for administrative records (e.g. "role ADMIN granted"); absent otherwise. */
  details?: string | null
}
