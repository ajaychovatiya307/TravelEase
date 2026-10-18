// JWT payload (claims) decoding for the username/password Login page (P-01).
//
// Client-side claim decoding does not verify the signature. The backend
// authenticates credentials, issues the HS256 JWT, and verifies the signature
// on every protected request. The Login page reads `sub`, `roles`, and `exp`
// from that response to initialize the client AuthState.

import type { RoleType } from '../models/admin'

export interface DecodedTokenClaims {
  /** The token's `sub` claim — the opaque user identifier (api-contract.md §3.1). */
  userId: string
  /** The token's `roles` claim — valid `RoleType` values, deduped, first-seen order. */
  roles: RoleType[]
  /** The token's `exp` claim as an ISO 8601 string (api-contract.md §3.2). */
  expiresAt: string
}

const VALID_ROLES: readonly RoleType[] = ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN']

function isRoleType(value: unknown): value is RoleType {
  return typeof value === 'string' && (VALID_ROLES as readonly string[]).includes(value)
}

/**
 * base64url string → UTF-8 string: swap the base64url alphabet back to base64,
 * re-apply padding, decode with `atob`, then interpret the byte values as UTF-8
 * via `TextDecoder` (no `decodeURIComponent(escape(...))`). Returns null when
 * the input is not decodable.
 */
function base64UrlDecode(input: string): string | null {
  try {
    const base64 = input.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    return null
  }
}

/**
 * Decode the claims of a JWT (header.payload.signature) client-side.
 *
 * Returns `null` for anything invalid — wrong segment count or empty segment,
 * undecodable/non-object payload, missing or empty-string `sub`, an empty
 * `roles` array or one containing a non-`RoleType` entry, or a non-finite /
 * not-in-the-future `exp` — so callers can surface a single generic error
 * without leaking which check failed (frontend-spec.md §4.1, security).
 */
export function decodeTokenClaims(token: string): DecodedTokenClaims | null {
  try {
    const parts = token.trim().split('.')
    if (parts.length !== 3 || parts.some((part) => part.length === 0)) {
      return null
    }

    const payloadText = base64UrlDecode(parts[1])
    if (payloadText === null) {
      return null
    }

    const payload: unknown = JSON.parse(payloadText)
    if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
      return null
    }

    const { sub, roles, exp } = payload as Record<string, unknown>

    if (typeof sub !== 'string' || sub.length === 0) {
      return null
    }

    // Validate + dedupe in one pass, preserving first-seen order (a user may
    // hold multiple roles; api-contract.md §2, Q2).
    const uniqueRoles: RoleType[] = []
    if (Array.isArray(roles)) {
      for (const role of roles) {
        if (isRoleType(role) && !uniqueRoles.includes(role)) {
          uniqueRoles.push(role)
        }
      }
    }
    if (uniqueRoles.length === 0) {
      return null
    }

    if (typeof exp !== 'number' || !Number.isFinite(exp) || exp <= Date.now() / 1000) {
      return null
    }

    return {
      userId: sub,
      roles: uniqueRoles,
      expiresAt: new Date(exp * 1000).toISOString(),
    }
  } catch {
    return null
  }
}