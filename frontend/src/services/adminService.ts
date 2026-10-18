// Admin service (F-04, frontend-tasks.md — "F. Services").
// Implements the four `adminService` resource groups of the Admin Service
// area (frontend-api-mapping.md §1.3) on top of the shared HTTP client
// (httpClient.ts — F-01; frontend-plan.md §6 "Service Layer Design"):
// endpoint paths, methods, params/bodies, and response types follow
// frontend-api-mapping.md §1.3 and api-contract.md §4.3 (Endpoint Catalog),
// §5.12–§5.15 (DTOs), and §7 (Pagination & Filtering).
//
// Update flows: `users` is updated via the documented
// `PUT /api/admin/users/{id}` (api-contract.md §4.3); mappings, policy
// configs, and expense categories are created and updated through their
// single `POST` endpoint (api-contract.md §4.3 "Create/update ..."; the
// mapping POST is an upsert on `employeeId` — Q5), so their groups expose
// only `list`/`create`.
//
// Error handling: every function resolves with the documented typed response
// (models/admin.ts, models/pagination.ts) or rejects with a
// `ClassifiedError`-shaped Error produced by the shared client
// (frontend-plan.md §11/§13) — the page-level mapping in
// frontend-api-mapping.md §1.3 (e.g. 403 FORBIDDEN_ACTION → Access Denied,
// 404 RESOURCE_NOT_FOUND → Not Found state, 400 VALIDATION_ERROR → field
// errors) is handled by the UI layer, not here.

import type {
  EmployeeManagerMappingRequest,
  EmployeeManagerMappingResponse,
  ExpenseCategoryRequest,
  ExpenseCategoryResponse,
  PolicyConfigRequest,
  PolicyConfigResponse,
  RoleType,
  UserAccountCreateRequest,
  UserAccountRequest,
  UserAccountResponse,
} from '../models/admin'
import type { PaginatedResult } from '../models/pagination'
import { get, post, put } from './httpClient'

/**
 * Query parameters for `GET /api/admin/users` (frontend-api-mapping.md
 * §1.3, api-contract.md §7): zero-based `page` (default 0), `size` (default
 * 20, max enforced server-side), repeatable `sort` (`field,asc|desc`), plus
 * the optional `active`/`role` filters (client-side or server-side per
 * availability, §1.3 P-16). Admin role only (api-contract.md §4.3).
 */
export type UserListParams = {
  page?: number
  size?: number
  sort?: string | string[]
  active?: boolean
  role?: RoleType
}

/**
 * Query parameters for `GET /api/admin/employee-manager-mappings`
 * (frontend-api-mapping.md §1.3, api-contract.md §7): zero-based `page`
 * (default 0), `size` (default 20, max enforced server-side), repeatable
 * `sort` (`field,asc|desc`). Admin role only (api-contract.md §4.3).
 */
export type EmployeeManagerMappingListParams = {
  page?: number
  size?: number
  sort?: string | string[]
}

/**
 * Query parameters for `GET /api/admin/policy-configs` (frontend-api-mapping.md
 * §1.3, api-contract.md §7): zero-based `page` (default 0), `size` (default
 * 20, max enforced server-side), repeatable `sort` (`field,asc|desc`), plus
 * the `categoryCode` filter backing the expense-form threshold lookup
 * (§1.3 P-11/P-12). Readable by Employee/Finance/Admin; write Admin-only
 * (api-contract.md §4.3).
 */
export type PolicyConfigListParams = {
  page?: number
  size?: number
  sort?: string | string[]
  categoryCode?: string
}

/**
 * Query parameters for `GET /api/admin/expense-categories`
 * (frontend-api-mapping.md §1.3, api-contract.md §7): zero-based `page`
 * (default 0), `size` (default 20, max enforced server-side), repeatable
 * `sort` (`field,asc|desc`), plus the `active` filter backing the
 * expense-form category lookup (§1.3 P-11/P-12, `{active: true}`). Readable
 * by Employee/Finance/Admin; write Admin-only (api-contract.md §4.3).
 */
export type ExpenseCategoryListParams = {
  page?: number
  size?: number
  sort?: string | string[]
  active?: boolean
}

/**
 * P-16 Admin – Users (frontend-api-mapping.md §1.3, api-contract.md §4.3):
 * user account administration with initial role assignments (FR-ADM-01).
 * Admin role only.
 */
const users = {
  /**
   * Lists user accounts (`GET /api/admin/users` — frontend-api-mapping.md
   * §1.3, P-16 Users list). Resolves with the shared paginated envelope
   * (api-contract.md §7).
   */
  list(params?: UserListParams): Promise<PaginatedResult<UserAccountResponse>> {
    return get<PaginatedResult<UserAccountResponse>>('/api/admin/users', params)
  },

  /**
   * Retrieves a single user account (`GET /api/admin/users/{id}` —
   * frontend-api-mapping.md §1.3, P-16 Users detail; read model
   * api-contract.md §5.12, no internal-only fields).
   */
  getById(id: string): Promise<UserAccountResponse> {
    return get<UserAccountResponse>(`/api/admin/users/${id}`)
  },

  /**
   * Creates a new user account with initial roles (`POST /api/admin/users`
   * — frontend-api-mapping.md §1.3, P-16 Users create; body:
   * `UserAccountRequest`, api-contract.md §5.12 — email uniqueness is a
   * Service-layer business rule, FR-ADM-01).
   */
  create(payload: UserAccountCreateRequest): Promise<UserAccountResponse> {
    return post<UserAccountResponse>('/api/admin/users', payload)
  },

  /**
   * Updates a user account's fields and role assignments (`PUT
   * /api/admin/users/{id}` — frontend-api-mapping.md §1.3, P-16 Users
   * update; body: `UserAccountRequest`, api-contract.md §5.12).
   */
  update(id: string, payload: UserAccountRequest): Promise<UserAccountResponse> {
    return put<UserAccountResponse>(`/api/admin/users/${id}`, payload)
  },

  /**
   * Issues a temporary password (`POST /api/admin/users/{id}/reset-password`, #14). The value is
   * returned exactly once; the user must replace it at next sign-in and existing sessions end.
   */
  resetPassword(id: string): Promise<{ temporaryPassword: string }> {
    return post<{ temporaryPassword: string }>(`/api/admin/users/${id}/reset-password`)
  },
}

/**
 * P-17 Admin – Employee-Manager Mappings (frontend-api-mapping.md §1.3,
 * api-contract.md §4.3): Employee→Manager mapping administration
 * (FR-ADM-01, Q3/Q5). Created and updated through the single `POST`
 * endpoint (upsert on `employeeId`), so no separate `update` is exposed.
 * Admin role only.
 */
const mappings = {
  /**
   * Lists employee-to-manager mappings (`GET
   * /api/admin/employee-manager-mappings` — frontend-api-mapping.md §1.3,
   * P-17 Mappings list). Resolves with the shared paginated envelope
   * (api-contract.md §7).
   */
  list(
    params?: EmployeeManagerMappingListParams,
  ): Promise<PaginatedResult<EmployeeManagerMappingResponse>> {
    return get<PaginatedResult<EmployeeManagerMappingResponse>>(
      '/api/admin/employee-manager-mappings',
      params,
    )
  },

  /**
   * Creates or updates an Employee→Manager mapping (`POST
   * /api/admin/employee-manager-mappings` — frontend-api-mapping.md §1.3,
   * P-17 Mappings create/update; body: `EmployeeManagerMappingRequest`,
   * api-contract.md §5.13 — `managerId` is nullable for top-of-hierarchy
   * managers, Q3).
   */
  create(
    payload: EmployeeManagerMappingRequest,
  ): Promise<EmployeeManagerMappingResponse> {
    return post<EmployeeManagerMappingResponse>(
      '/api/admin/employee-manager-mappings',
      payload,
    )
  },
}

/**
 * P-18 Admin – Policy Configuration (frontend-api-mapping.md §1.3,
 * api-contract.md §4.3): expense policy threshold administration
 * (FR-ADM-02, Q7). Created and updated through the single `POST` endpoint,
 * so no separate `update` is exposed. Readable by Employee/Finance/Admin;
 * write Admin-only.
 */
const policyConfigs = {
  /**
   * Lists policy configurations (`GET /api/admin/policy-configs` —
   * frontend-api-mapping.md §1.3, P-18 Policy Config list; `categoryCode`
   * also backs the expense-form threshold lookup, P-11/P-12). Resolves
   * with the shared paginated envelope (api-contract.md §7).
   */
  list(params?: PolicyConfigListParams): Promise<PaginatedResult<PolicyConfigResponse>> {
    return get<PaginatedResult<PolicyConfigResponse>>('/api/admin/policy-configs', params)
  },

  /**
   * Creates an expense policy threshold (`POST /api/admin/policy-configs` —
   * frontend-api-mapping.md §1.3, P-18 Policy Config create; body: `PolicyConfigRequest`,
   * api-contract.md §5.14 — `categoryCode` must reference an existing
   * `ExpenseCategory.code`, `receiptThresholdAmount` is `>= 0`).
   */
  create(payload: PolicyConfigRequest): Promise<PolicyConfigResponse> {
    return post<PolicyConfigResponse>('/api/admin/policy-configs', payload)
  },

  /**
   * Updates a specific policy configuration (`PUT
   * /api/admin/policy-configs/{id}`).
   */
  update(id: string, payload: PolicyConfigRequest): Promise<PolicyConfigResponse> {
    return put<PolicyConfigResponse>(`/api/admin/policy-configs/${id}`, payload)
  },
}

/**
 * P-19 Admin – Expense Categories (frontend-api-mapping.md §1.3,
 * api-contract.md §4.3): predefined expense category administration
 * (FR-ADM-02, FR-EXP-04). Created and updated through the single `POST`
 * endpoint, so no separate `update` is exposed. Readable by
 * Employee/Finance/Admin; write Admin-only.
 */
const expenseCategories = {
  /**
   * Lists expense categories (`GET /api/admin/expense-categories` —
   * frontend-api-mapping.md §1.3, P-19 Expense Categories list; the
   * `active` filter backs the expense-form category lookup, P-11/P-12).
   * Resolves with the shared paginated envelope (api-contract.md §7).
   */
  list(params?: ExpenseCategoryListParams): Promise<PaginatedResult<ExpenseCategoryResponse>> {
    return get<PaginatedResult<ExpenseCategoryResponse>>('/api/admin/expense-categories', params)
  },

  /**
   * Creates or updates an expense category (`POST
   * /api/admin/expense-categories` — frontend-api-mapping.md §1.3, P-19
   * Expense Categories create/update; body: `ExpenseCategoryRequest`,
   * api-contract.md §5.15 — `code` is required and unique, enforced at
   * the Service layer).
   */
  create(payload: ExpenseCategoryRequest): Promise<ExpenseCategoryResponse> {
    return post<ExpenseCategoryResponse>('/api/admin/expense-categories', payload)
  },
}

/**
 * The `adminService` object consumed by the Admin pages (P-16–P-19) and
 * the F-06 data-fetching hooks — one group per admin resource, one
 * function per documented endpoint (frontend-plan.md §6/§7).
 */
export const adminService = {
  users,
  mappings,
  policyConfigs,
  expenseCategories,
}