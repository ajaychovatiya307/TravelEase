import type { RoleType } from '../context/AuthContext'

/**
 * Central route table (frontend-plan.md §3): every page from frontend-spec.md §1
 * (P-01..P-23) declared as pure data — path, route-level roles, and layout.
 *
 * This module intentionally has NO component imports, guard logic, or Router
 * wiring:
 * - D-02 (`RequireAuth`/`RequireRole`) consumes `isPublic` and `requiredRoles`.
 * - D-03 (App Router wiring) consumes `path` and resolves `layout` to the
 *   `src/layouts/AuthLayout` / `src/layouts/AuthenticatedLayout` components.
 *
 * Design notes:
 * - Record-ownership rules from api-contract.md §4 (e.g. P-06 owner-only edit,
 *   P-07 assigned-approver-only) are enforced at PAGE level (Sections G/I), NOT
 *   at route-role level. Do not encode ownership in this table.
 * - P-18 (Admin – Policy Configuration) is ADMIN-only here per frontend-spec.md
 *   §1, even though api-contract.md §4.3 also grants FINANCE read access to
 *   `GET /api/admin/policy-configs`: Finance consumes policy thresholds via
 *   hooks/forms (P-11/P-12), not via this admin page.
 * - Fallback screens (frontend-plan.md §3: only Login (P-01) and the error
 *   pages are reachable without authentication):
 *   - P-21 renders in place inside the app shell when a `RequireRole` guard
 *     fails (frontend-plan.md §3), so it uses the 'authenticated' layout.
 *   - P-22 (route-level 404) and P-23 (top-level generic error) must also
 *     render with no session present, so they use the minimal 'auth' layout.
 */

/** Every page ID from frontend-spec.md §1, in P-01..P-23 order. */
export const PAGE_IDS = [
  'P-01',
  'P-02',
  'P-03',
  'P-04',
  'P-05',
  'P-06',
  'P-07',
  'P-08',
  'P-09',
  'P-10',
  'P-11',
  'P-12',
  'P-13',
  'P-14',
  'P-15',
  'P-16',
  'P-17',
  'P-18',
  'P-19',
  'P-20',
  'P-21',
  'P-22',
  'P-23',
  'P-24',
] as const

export type PageId = (typeof PAGE_IDS)[number]

/**
 * Layout discriminator, resolved to a layout component by D-03:
 * - 'auth'          → AuthLayout (minimal, unauthenticated shell)
 * - 'authenticated' → AuthenticatedLayout (sidebar + topbar)
 */
export type RouteLayout = 'auth' | 'authenticated'

export interface RouteEntry {
  /** Page ID from frontend-spec.md §1 (P-01..P-23). */
  pageId: PageId
  /** Route path registered with react-router-dom, e.g. '/travel-requests/:id/edit'. */
  path: string
  /** Label for authenticated primary navigation; omitted for page-level routes. */
  navigationLabel?: string
  /**
   * Route-level roles allowed to open this page; empty for public pages
   * (P-01, P-21..P-23). Ownership rules (api-contract.md §4) are enforced at
   * page level, not here.
   */
  requiredRoles: RoleType[]
  /** Layout discriminator; bound to a component by D-03. */
  layout: RouteLayout
  /** True when the page is reachable without authentication. */
  isPublic: boolean
}

/**
 * The 23 pages of frontend-spec.md §1 in P-01..P-23 order. Roles match the role
 * column of frontend-spec.md §1 / api-contract.md §4.
 */
export const routeTable: RouteEntry[] = [
  // P-01 Login (frontend-spec.md §1) — public landing for unauthenticated users.
  {
    pageId: 'P-01',
    path: '/login',
    requiredRoles: [],
    layout: 'auth',
    isPublic: true,
  },
  // P-02 Dashboard (frontend-spec.md §1) — role-driven sections for all roles.
  {
    pageId: 'P-02',
    path: '/',
    navigationLabel: 'Dashboard',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-03 Travel Request List (frontend-spec.md §1).
  {
    pageId: 'P-03',
    path: '/travel-requests',
    navigationLabel: 'Travel Requests',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-04 Travel Request Detail (frontend-spec.md §1).
  {
    pageId: 'P-04',
    path: '/travel-requests/:id',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-05 New Travel Request (frontend-spec.md §1) — employee-only creation.
  {
    pageId: 'P-05',
    path: '/travel-requests/new',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-06 Edit/Resubmit Travel Request (frontend-spec.md §1) — route allows any
  // employee; owner-only access is enforced at page level (api-contract.md §4).
  {
    pageId: 'P-06',
    path: '/travel-requests/:id/edit',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-07 Travel Request Approval (frontend-spec.md §1) — route allows
  // MANAGER/FINANCE; assigned-approver-only is enforced at page level.
  {
    pageId: 'P-07',
    path: '/travel-requests/:id/approval',
    requiredRoles: ['MANAGER', 'FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-08 Travel Request Approval Queue (frontend-spec.md §1).
  {
    pageId: 'P-08',
    path: '/approvals/travel-requests',
    navigationLabel: 'Travel Approvals',
    requiredRoles: ['MANAGER', 'FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-09 Expense Claim List (frontend-spec.md §1).
  {
    pageId: 'P-09',
    path: '/expenses',
    navigationLabel: 'Expenses',
    requiredRoles: ['EMPLOYEE', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-10 Expense Claim Detail (frontend-spec.md §1).
  {
    pageId: 'P-10',
    path: '/expenses/:id',
    requiredRoles: ['EMPLOYEE', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-11 New Expense Claim (frontend-spec.md §1) — employee-only creation.
  {
    pageId: 'P-11',
    path: '/expenses/new',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-12 Edit Expense Claim (frontend-spec.md §1) — route allows any employee;
  // owner-only access is enforced at page level (api-contract.md §4).
  {
    pageId: 'P-12',
    path: '/expenses/:id/edit',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-13 Expense Review (frontend-spec.md §1).
  {
    pageId: 'P-13',
    path: '/expenses/:id/review',
    requiredRoles: ['FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-14 Expense Review Queue (frontend-spec.md §1).
  {
    pageId: 'P-14',
    path: '/expenses/review-queue',
    navigationLabel: 'Expense Reviews',
    requiredRoles: ['FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-15 Reimbursement Action (frontend-spec.md §1).
  {
    pageId: 'P-15',
    path: '/expenses/:id/reimburse',
    requiredRoles: ['FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-16 Admin – Users (frontend-spec.md §1).
  {
    pageId: 'P-16',
    path: '/admin/users',
    navigationLabel: 'Users',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-17 Admin – Employee-Manager Mapping (frontend-spec.md §1).
  {
    pageId: 'P-17',
    path: '/admin/mappings',
    navigationLabel: 'Employee-Manager Mappings',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-18 Admin – Policy Configuration (frontend-spec.md §1) — ADMIN-only at
  // route level; the FINANCE read access from api-contract.md §4.3 is consumed
  // via hooks/forms (P-11/P-12), not this page (see file-level note).
  {
    pageId: 'P-18',
    path: '/admin/policy-configs',
    navigationLabel: 'Policy Configuration',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-19 Admin – Expense Categories (frontend-spec.md §1).
  {
    pageId: 'P-19',
    path: '/admin/expense-categories',
    navigationLabel: 'Expense Categories',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-20 Audit Log Viewer (frontend-spec.md §1).
  {
    pageId: 'P-20',
    path: '/audit',
    navigationLabel: 'Audit Log',
    requiredRoles: ['ADMIN', 'FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  // P-21 Access Denied (frontend-spec.md §1) — public fallback rendered in place
  // by the RequireRole guard inside the app shell (frontend-plan.md §3).
  {
    pageId: 'P-21',
    path: '/access-denied',
    requiredRoles: [],
    layout: 'authenticated',
    isPublic: true,
  },
  // P-22 Not Found (frontend-spec.md §1) — route-level 404, reachable with no
  // session, so rendered in the minimal layout (frontend-plan.md §3).
  {
    pageId: 'P-22',
    path: '/404',
    requiredRoles: [],
    layout: 'auth',
    isPublic: true,
  },
  // P-23 Generic Error (frontend-spec.md §1) — top-level error boundary target,
  // reachable with no session, so rendered in the minimal layout.
  {
    pageId: 'P-23',
    path: '/error',
    requiredRoles: [],
    layout: 'auth',
    isPublic: true,
  },
  // P-24 Change password (#14) — every signed-in user; reached from the top bar, not the sidebar.
  {
    pageId: 'P-24',
    path: '/account/password',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
]
