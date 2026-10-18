import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { RoleType } from '../models/admin'
import { decodeTokenClaims } from '../utils/jwt'

// RoleType is canonical in models/admin.ts (E-04, api-contract.md §2 token
// claims); re-exported here so existing consumers of this context keep working.
export type { RoleType }

export interface AuthState {
  userId: string | null
  roles: RoleType[]
  isAuthenticated: boolean
  sessionExpiresAt: string | null
  /** Bearer token attached to every request by the HTTP client's auth request interceptor (H-01, frontend-plan.md §13). */
  token: string | null
}

const UNAUTHENTICATED_STATE: AuthState = {
  userId: null,
  roles: [],
  isAuthenticated: false,
  sessionExpiresAt: null,
  token: null,
}

const AUTH_TOKEN_STORAGE_KEY = 'travelease.auth.token'

function restoreAuthState(): AuthState {
  const token = sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY)
  if (token === null) {
    return UNAUTHENTICATED_STATE
  }

  const claims = decodeTokenClaims(token)
  if (claims === null) {
    sessionStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
    return UNAUTHENTICATED_STATE
  }

  return {
    userId: claims.userId,
    roles: claims.roles,
    isAuthenticated: true,
    sessionExpiresAt: claims.expiresAt,
    token,
  }
}

interface AuthContextValue {
  authState: AuthState
  /** Replaces the full session state, e.g. on successful login (ui-model.md §9). */
  setAuthState: (state: AuthState) => void
  /** Resets to the default unauthenticated state, e.g. on logout/session expiry. */
  clearAuthState: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [authState, setAuthStateInternal] = useState<AuthState>(restoreAuthState)

  const setAuthState = useCallback((state: AuthState) => {
    const claims = state.token === null ? null : decodeTokenClaims(state.token)
    if (state.isAuthenticated && state.token !== null && claims !== null) {
      sessionStorage.setItem(AUTH_TOKEN_STORAGE_KEY, state.token)
    } else {
      sessionStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
    }
    setAuthStateInternal(state)
  }, [])

  const clearAuthState = useCallback(() => {
    sessionStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
    setAuthStateInternal(UNAUTHENTICATED_STATE)
  }, [])

  const value = useMemo(
    () => ({ authState, setAuthState, clearAuthState }),
    [authState, setAuthState, clearAuthState],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- context hook co-located by convention
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

/**
 * Like {@link useAuth} but returns `undefined` outside an AuthProvider, for components that only
 * consult the session opportunistically (e.g. background polling that must stop once it expires).
 */
// eslint-disable-next-line react-refresh/only-export-components -- context hook co-located by convention
export function useOptionalAuth(): AuthContextValue | undefined {
  return useContext(AuthContext)
}
