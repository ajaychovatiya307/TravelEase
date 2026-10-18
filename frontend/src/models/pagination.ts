// Shared pagination envelope type (E-06).
// Mirrors api-contract.md §7 ("Pagination & Filtering"): every paginated `GET`
// list endpoint (`/api/travel-requests`, `/api/expenses`, `/api/admin/users`,
// `/api/admin/employee-manager-mappings`, `/api/admin/policy-configs`,
// `/api/admin/expense-categories`, `/api/audit`) returns this same envelope —
// field names and types must stay in sync with the contract.
//
// Query-parameter semantics (`page` zero-based, `size` default 20 with a
// server-side max, repeatable `sort` as `field,asc|desc`, plus the
// service-specific filters) are built by the service layer; this model covers
// the response envelope only.

/**
 * api-contract.md §7: response envelope shared by all paginated `GET` list
 * endpoints. `page` is the zero-based page index; `size` is the page size;
 * `totalElements`/`totalPages` describe the full result set under the active
 * filters (including ownership-based implicit filters applied server-side).
 */
export interface PaginatedResult<T> {
  /** The page of resource DTOs (the contract's `content` array). */
  content: T[]
  /** Zero-based index of the returned page. */
  page: number
  /** Page size (items requested per page). */
  size: number
  /** Total number of elements matching the query across all pages. */
  totalElements: number
  /** Total number of pages at the current `size`. */
  totalPages: number
}
