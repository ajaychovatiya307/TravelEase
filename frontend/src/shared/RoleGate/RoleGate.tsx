import type { ReactNode } from 'react'
import { useAuth } from '../../context/AuthContext'
import type { RoleType } from '../../context/AuthContext'

interface RoleGateProps {
  allowedRoles: RoleType[]
  children: ReactNode
}

/**
 * Conditionally renders children only if the current session holds any of the allowed roles
 * (frontend-plan.md §5/§12). Renders nothing otherwise — gating is additive, never a fallback UI.
 */
export function RoleGate({ allowedRoles, children }: RoleGateProps) {
  const { authState } = useAuth()
  const hasAllowedRole = authState.roles.some((role) => allowedRoles.includes(role))

  if (!hasAllowedRole) {
    return null
  }

  return <>{children}</>
}
