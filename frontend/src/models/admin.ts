// Admin resource DTOs and RoleType enum (E-04).
// Mirrors api-contract.md §5.12–§5.15 (RoleType values per §2) and
// backend/data-model.md §5.1–§5.5: field names, types, nullability, and enum
// string values must stay in sync with the contract.
// Internal-only fields (e.g. `deleted`, `version`, audit metadata) are
// intentionally absent per Constitution §6 (see the api-contract.md §5.3 note,
// which applies to all read models).
//
// Contract `decimal` (threshold amounts) is `number` in JSON payloads
// (api-contract.md §3.3); `date` contract types are `string` (ISO 8601,
// api-contract.md §3.2); identifiers are opaque strings (§3.1).

/**
 * api-contract.md §2/§5.12 / data-model.md §5.2: RBAC roles shared across all
 * services (Q1). A user may hold multiple roles simultaneously (Q2).
 *
 * Canonical home for `RoleType` — src/context/AuthContext.tsx currently
 * declares a local mirror until it is re-pointed at this module.
 */
export type RoleType = 'EMPLOYEE' | 'MANAGER' | 'FINANCE' | 'ADMIN'

/**
 * Profile fields for PUT /api/admin/users/{id}. `email` format/validity is
 * checked at the boundary; uniqueness is a Service-layer business rule
 * (FR-ADM-01).
 */
export interface UserAccountRequest {
  fullName: string
  email: string
  roles: RoleType[]
  /** Optional; defaults to `true` when omitted. */
  active?: boolean
}

/** Create payload includes the credential used for the user's first login. */
export interface UserAccountCreateRequest extends UserAccountRequest {
  password: string
}

/**
 * api-contract.md §5.12: Admin user account read model
 * (GET /api/admin/users, GET /api/admin/users/{id}). No internal-only fields
 * (Constitution §6).
 */
export interface UserAccountResponse {
  id: string
  fullName: string
  email: string
  roles: RoleType[]
  active: boolean
}

/**
 * api-contract.md §5.13: POST /api/admin/employee-manager-mappings
 * (create/update). `employeeId` must reference an existing user; `managerId`
 * is nullable — top-of-hierarchy managers have no manager and their approvals
 * fall back to Finance (Q3, FR-TR-05).
 */
export interface EmployeeManagerMappingRequest {
  employeeId: string
  managerId?: string | null
  effectiveFrom: string
}

/**
 * api-contract.md §5.13: employee-to-manager mapping read model
 * (GET /api/admin/employee-manager-mappings). No internal-only fields
 * (Constitution §6); `managerId` is null for top-of-hierarchy managers (Q3).
 */
export interface EmployeeManagerMappingResponse {
  id: string
  employeeId: string
  managerId: string | null
  effectiveFrom: string
}

/**
 * api-contract.md §5.14: POST /api/admin/policy-configs (create) and
 * PUT /api/admin/policy-configs/{id} (update).
 * `categoryCode` must reference an existing `ExpenseCategory.code`;
 * `currency` must match the base currency (NFR-05); `receiptThresholdAmount`
 * is `>= 0` (VAL-10 source of truth).
 */
export interface PolicyConfigRequest {
  categoryCode: string
  receiptThresholdAmount: number
  currency: string
  /** Optional; defaults to `true` when omitted. */
  active?: boolean
}

/**
 * api-contract.md §5.14: expense policy configuration read model
 * (GET /api/admin/policy-configs). No internal-only fields (Constitution §6).
 */
export interface PolicyConfigResponse {
  id: string
  categoryCode: string
  receiptThresholdAmount: number
  currency: string
  active: boolean
}

/**
 * api-contract.md §5.15: POST /api/admin/expense-categories (create/update).
 * `code` is required and unique (uniqueness enforced at the Service layer);
 * it backs the predefined category list referenced by
 * `ExpenseClaim.category` (VAL-07, FR-EXP-04).
 */
export interface ExpenseCategoryRequest {
  code: string
  label: string
  /** Optional; defaults to `true` when omitted. */
  active?: boolean
}

/**
 * api-contract.md §5.15: expense category read model
 * (GET /api/admin/expense-categories). No internal-only fields
 * (Constitution §6).
 */
export interface ExpenseCategoryResponse {
  id: string
  code: string
  label: string
  active: boolean
}
