import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { useAuth } from '../context/AuthContext'

/**
 * Authentication route guard (frontend-plan.md §12, frontend-tasks.md D-02).
 *
 * Wraps the children of a protected route (wired in D-03) and checks the
 * `isAuthenticated` flag of the `AuthState` before rendering anything:
 * - Unauthenticated: replace-navigates to the login page (P-01, `/login`)
 *   via `<Navigate to="/login" replace />`. The attempted location is
 *   attached as `state.from` so the LoginPage (I-01, future task) can
 *   redirect the user back there after a successful login; LoginPage may
 *   consume `state.from` but must not require it (a direct visit to
 *   `/login` carries no state).
 * - Authenticated: renders `children` as-is.
 *
 * The public API is deliberately minimal: a single `children` prop. The
 * login target is the fixed P-01 page, so no `redirectTo` override is
 * offered (frontend-plan.md §3 declares a single login page).
 *
 * This guard checks session presence only. Role enforcement is NOT done
 * here — it belongs to the sibling `RequireRole` guard (D-02), which D-03
 * composes with this guard per route table entry.
 *
 * No redirect loops: `/login` itself is public (routeTable P-01) and must
 * never be wrapped in `RequireAuth`. That invariant is enforced by the
 * route wiring (D-03), not inside this guard.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { authState } = useAuth()
  const location = useLocation()

  if (!authState.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <>{children}</>
}