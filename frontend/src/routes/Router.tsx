import type { ReactNode } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'

import { AuthenticatedLayout } from '../layouts/AuthenticatedLayout/AuthenticatedLayout'
import { AuthLayout } from '../layouts/AuthLayout/AuthLayout'
import { resolvePage } from './pageRegistry'
import { RequireRole } from './RequireRole'
import { routeTable } from './routeTable'
import type { RouteEntry } from './routeTable'

/**
 * Build the route element for one routeTable entry (frontend-plan.md §3,
 * frontend-tasks.md D-03, part 1):
 *
 *   layout( [ RequireRole( page ) ] )
 *
 * - `layout` resolves `entry.layout` to the real layout component:
 *   'auth' → AuthLayout (minimal unauthenticated shell, P-01/P-22/P-23),
 *   'authenticated' → AuthenticatedLayout (sidebar + topbar, P-02..P-21).
 *   Layouts take a plain `children` prop (no Outlet), so each entry wraps its
 *   page per-entry instead of using nested layout routes.
 * - Non-public entries (P-02..P-20) wrap the page in `RequireRole` with the
 *   entry's `requiredRoles`. Public entries get NO guard at all — in
 *   particular `/login` (P-01) is never wrapped in `RequireAuth`, which is
 *   the no-redirect-loop invariant established in D-02 (a guarded login page
 *   would bounce unauthenticated users to /login forever).
 * - The page itself comes from `resolvePage` (pageRegistry): the real page
 *   once Section I registers it, the real fallback screen for P-21..P-23, or
 *   a PlaceholderPage until then.
 */
function entryElement(entry: RouteEntry): ReactNode {
  const page = resolvePage(entry)
  const guarded = entry.isPublic
    ? page
    : (
        <RequireRole requiredRoles={entry.requiredRoles}>
          {page}
        </RequireRole>
      )
  return entry.layout === 'authenticated' ? (
    <AuthenticatedLayout>{guarded}</AuthenticatedLayout>
  ) : (
    <AuthLayout>{guarded}</AuthLayout>
  )
}

/** Route-level 404 entry (frontend-plan.md §3): unmatched routes render P-22's
 * real NotFoundPage with the same public/no-guard treatment as P-22's own
 * routeTable entry. The path is irrelevant to the resolved element (the real
 * NotFoundPage does not render it); '*' documents the catch-all purpose. */
const CATCH_ALL_ENTRY: RouteEntry = {
  pageId: 'P-22',
  path: '*',
  requiredRoles: [],
  layout: 'auth',
  isPublic: true,
}

/**
 * The full route tree for the app (frontend-plan.md §3): every routeTable
 * entry mapped programmatically to `<Route path element>` plus the `*`
 * catch-all. Exported WITHOUT any history router so tests can wrap it in a
 * `MemoryRouter` with `initialEntries`; the real App mount (D-03 part 2)
 * uses the default `Router` export, which provides the `BrowserRouter`.
 */
export function AppRoutes() {
  return (
    <Routes>
      {routeTable.map((entry) => (
        <Route key={entry.pageId} path={entry.path} element={entryElement(entry)} />
      ))}
      <Route path="*" element={entryElement(CATCH_ALL_ENTRY)} />
    </Routes>
  )
}

/**
 * Default router region for `App` (D-03 part 2 mounts this): the `AppRoutes`
 * tree inside a `BrowserRouter` for real browser navigation.
 */
export default function Router() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}