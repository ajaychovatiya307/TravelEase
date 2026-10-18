import type { ReactNode } from 'react'

import { useAuth } from '../context/AuthContext'
import type { RoleType } from '../context/AuthContext'
import { AccessDeniedPage } from '../shared/AccessDeniedPage'
import { RequireAuth } from './RequireAuth'

interface RequireRoleProps {
  requiredRoles: RoleType[]
  children: ReactNode
}

/**
 * Role route guard (frontend-plan.md §3/§12, frontend-tasks.md D-02, part 2).
 *
 * The route-level equivalent of `RoleGate` — same any-of intersection semantics
 * (`authState.roles` INTERSECTS `requiredRoles`; api-contract.md §2: a user may
 * hold multiple roles simultaneously) — but instead of rendering nothing on
 * failure it renders the Access Denied fallback (P-21).
 *
 * Design (a) of frontend-tasks.md D-02: this guard internally composes
 * `RequireAuth`, so a role-guarded route is safe to wrap in `RequireRole`
 * alone (the D-03 wiring):
 * - Unauthenticated: `RequireAuth` replace-navigates to the login page (P-01,
 *   `/login`) with the attempted location carried as `state.from`. A user with
 *   no session is never shown "Access Denied" — that page is defined for
 *   authenticated-but-unauthorized access only (ui-model.md §2).
 * - Authenticated without any required role: `AccessDeniedPage` renders IN
 *   PLACE — no redirect, and therefore no redirect loop.
 * - Authenticated with at least one required role: renders `children` as-is.
 *
 * This guard enforces only the page-level `requiredRoles` list carried by each
 * protected routeTable entry (P-02..P-20). Owner-only and assigned-approver
 * rules are page-level concerns (sections G/I) and are NOT handled here.
 */
export function RequireRole({ requiredRoles, children }: RequireRoleProps) {
  const { authState } = useAuth()
  const hasRequiredRole = authState.roles.some((role) => requiredRoles.includes(role))

  return (
    <RequireAuth>
      {hasRequiredRole ? children : <AccessDeniedPage />}
    </RequireAuth>
  )
}