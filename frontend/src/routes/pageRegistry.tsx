import type { ReactNode } from 'react'

import { ChangePasswordPage } from '../pages/account/change-password/ChangePasswordPage'
import { UsersPage } from '../pages/admin/users/UsersPage'
import { MappingsPage } from '../pages/admin/mappings/MappingsPage'
import { PolicyConfigsPage } from '../pages/admin/policy-configs/PolicyConfigsPage'
import { ExpenseCategoriesPage } from '../pages/admin/expense-categories/ExpenseCategoriesPage'
import { DashboardPage } from '../pages/dashboard/DashboardPage'
import { ExpenseFormPage } from '../pages/expenses/create-edit/ExpenseFormPage'
import { ExpenseDetailPage } from '../pages/expenses/detail/ExpenseDetailPage'
import { ExpenseListPage } from '../pages/expenses/list/ExpenseListPage'
import { ReviewQueuePage } from '../pages/expenses/review-queue/ReviewQueuePage'
import { ExpenseReviewPage } from '../pages/expenses/review/ExpenseReviewPage'
import { LoginPage } from '../pages/login/LoginPage'
import { TravelRequestFormPage } from '../pages/travel-requests/create-edit/TravelRequestFormPage'
import { ApprovalPage } from '../pages/travel-requests/approval/ApprovalPage'
import { ApprovalQueuePage } from '../pages/travel-requests/approval-queue/ApprovalQueuePage'
import { TravelRequestListPage } from '../pages/travel-requests/list/TravelRequestListPage'
import { TravelRequestDetailPage } from '../pages/travel-requests/detail/TravelRequestDetailPage'
import { ReimbursePage } from '../pages/expenses/reimburse/ReimbursePage'
import { AuditLogPage } from '../pages/audit/AuditLogPage'
import { AccessDeniedPage } from '../shared/AccessDeniedPage'
import { GenericErrorPage } from '../shared/GenericErrorPage'
import { NotFoundPage } from '../shared/NotFoundPage'
import { PlaceholderPage } from './PlaceholderPage'
import type { PageId, RouteEntry } from './routeTable'

/**
 * Page-resolution registry — the extension point for Section I (frontend-tasks.md).
 *
 * `pageRegistry` maps a pageId to the ReactNode that should be rendered for
 * that page. It starts pre-filled with ONLY the three real fallback screens
 * (P-21..P-23, which already exist in `src/shared`); every other pageId is
 * intentionally absent so the route wiring renders a `PlaceholderPage` until
 * its feature screen lands.
 *
 * Section I tasks add entries incrementally, one pageId at a time, e.g.:
 *
 *   import { pageRegistry } from '../routes'
 *   import { TravelRequestFormPage } from '../pages/travel-requests/TravelRequestFormPage'
 *   pageRegistry['P-05'] = <TravelRequestFormPage />
 *
 * Once a pageId has an entry, `resolvePage` returns it instead of the
 * placeholder — no change to the route wiring (Router.tsx) is needed.
 */
export const pageRegistry: Partial<Record<PageId, ReactNode>> = {
  // P-01 Login (frontend-spec.md §4.1; I-01) — real page, src/pages/login.
  'P-01': <LoginPage />,
  // P-02 Dashboard (frontend-spec.md §4.2; I-02 part 2) — real page,
  // src/pages/dashboard; role-conditional RoleSection stack (Q1).
  'P-02': <DashboardPage />,
  // P-03 Travel Request List (frontend-spec.md §4.3; I-03).
  'P-03': <TravelRequestListPage />,
  // P-04 Travel Request Detail (frontend-spec.md §4.4; I-05).
  'P-04': <TravelRequestDetailPage />,
  // P-05 New Travel Request (frontend-spec.md §4.5; I-06) — real page,
  // src/pages/travel-requests/create-edit.
  'P-05': <TravelRequestFormPage />,
  // P-06 Edit / Resubmit Travel Request (frontend-spec.md §4.5; I-06) — real page,
  // hosting TravelRequestForm in edit/resubmit mode with prefill.
  'P-06': <TravelRequestFormPage />,
  // P-07 Travel Request Approval (frontend-spec.md §4.6; I-07) — real page,
  // src/pages/travel-requests/approval; decision screen with ConfirmDialog.
  'P-07': <ApprovalPage />,
  // P-08 Travel Request Approval Queue (frontend-spec.md §4.3; I-04).
  'P-08': <ApprovalQueuePage />,
  // P-09 Expense Claim List (frontend-spec.md §4.7; I-08).
  'P-09': <ExpenseListPage />,
  // P-10 Expense Claim Detail (frontend-spec.md §4.8; I-10).
  'P-10': <ExpenseDetailPage />,
  // P-11/P-12 New and Edit Expense Claim (frontend-spec.md §4.9; I-11).
  'P-11': <ExpenseFormPage />,
  'P-12': <ExpenseFormPage />,
  // P-13 Expense Review (frontend-spec.md §4.10; I-12).
  'P-13': <ExpenseReviewPage />,
  // P-14 Expense Review Queue (frontend-spec.md §4.7; I-09).
  'P-14': <ReviewQueuePage />,
  // P-15 Reimbursement Action (frontend-spec.md §4.11; I-13) — real page,
  // src/pages/expenses/reimburse; Finance confirm-and-reimburse screen.
  'P-15': <ReimbursePage />,
  // P-16 through P-19 Admin resource pages (frontend-spec.md §4.12; I-14).
  'P-16': <UsersPage />,
  'P-17': <MappingsPage />,
  'P-18': <PolicyConfigsPage />,
  'P-19': <ExpenseCategoriesPage />,
  // P-20 Audit Log Viewer (frontend-spec.md §4.13; I-15) — real page,
  // src/pages/audit; read-only filterable audit trail table.
  'P-20': <AuditLogPage />,
  // P-21 Access Denied (frontend-spec.md §1) — real fallback, src/shared.
  'P-21': <AccessDeniedPage />,
  // P-22 Not Found (frontend-spec.md §1) — real fallback, src/shared. Also the
  // element of the `*` catch-all route in Router.tsx (frontend-plan.md §3).
  'P-22': <NotFoundPage />,
  // P-23 Generic Error (frontend-spec.md §1) — real fallback, src/shared.
  'P-23': <GenericErrorPage />,
  // P-24 Change password (#14).
  'P-24': <ChangePasswordPage />,
}

/**
 * Resolve a route table entry to the ReactNode rendered inside its layout
 * (and, for non-public entries, its `RequireRole` guard) by Router.tsx.
 *
 * Resolution order:
 * 1. `pageRegistry[entry.pageId]`, when present — the real page (Section I
 *    extension point; P-21..P-23 are pre-filled with the fallback screens).
 * 2. Otherwise a `<PlaceholderPage>` stand-in carrying the pageId and path.
 */
export function resolvePage(entry: RouteEntry): ReactNode {
  const registered = pageRegistry[entry.pageId]
  return registered ?? <PlaceholderPage pageId={entry.pageId} path={entry.path} />
}
