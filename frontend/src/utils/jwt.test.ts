// decodeTokenClaims unit tests (P-01, I-01). Pure unit tests — no network, no
// router. Test tokens are built by base64url-encoding an ASCII JSON payload and
// joining it with a dummy header and signature; decodeTokenClaims only ever
// inspects the payload segment, so the other two parts just need to be
// non-empty and dot-free.

import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'

import { decodeTokenClaims } from './jwt'

function base64UrlEncode(text: string): string {
  return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Builds a 3-segment token whose payload is `rawPayload` (an arbitrary string). */
function makeToken(rawPayload: string): string {
  const header = base64UrlEncode('{"alg":"HS256","typ":"JWT"}')
  return `${header}.${base64UrlEncode(rawPayload)}.signature`
}

/** Builds a token whose payload is the JSON encoding of `claims`. */
function tokenFor(claims: Record<string, unknown>): string {
  return makeToken(JSON.stringify(claims))
}

/** A valid future exp in epoch seconds (one hour from now). */
function futureExp(): number {
  return Math.floor(Date.now() / 1000) + 3600
}

describe('decodeTokenClaims (I-01)', () => {
  it('decodes a valid token into userId, roles, and expiresAt', () => {
    const exp = futureExp()
    const claims = decodeTokenClaims(tokenFor({ sub: 'user-123', roles: ['EMPLOYEE', 'FINANCE'], exp }))
    expect(claims).toEqual({
      userId: 'user-123',
      roles: ['EMPLOYEE', 'FINANCE'],
      expiresAt: new Date(exp * 1000).toISOString(),
    })
  })

  it('dedupes repeated roles, preserving first-seen order', () => {
    const claims = decodeTokenClaims(
      tokenFor({ sub: 'U1', roles: ['EMPLOYEE', 'MANAGER', 'EMPLOYEE'], exp: futureExp() }),
    )
    expect(claims).not.toBeNull()
    expect(claims?.roles).toEqual(['EMPLOYEE', 'MANAGER'])
  })

  it('returns null for an expired token (exp in the past)', () => {
    const exp = Math.floor(Date.now() / 1000) - 60
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['ADMIN'], exp }))).toBeNull()
  })

  it('decodes a valid token wrapped in surrounding whitespace (trimmed before decoding)', () => {
    const exp = futureExp()
    const claims = decodeTokenClaims(`  ${tokenFor({ sub: 'U1', roles: ['ADMIN'], exp })}\n`)
    expect(claims).toEqual({
      userId: 'U1',
      roles: ['ADMIN'],
      expiresAt: new Date(exp * 1000).toISOString(),
    })
  })
})

describe('decodeTokenClaims — malformed token structure (I-01)', () => {
  it('returns null when the token does not have exactly 3 parts', () => {
    const payload = base64UrlEncode(JSON.stringify({ sub: 'U1', roles: ['ADMIN'], exp: futureExp() }))
    expect(decodeTokenClaims(`header.${payload}`)).toBeNull() // 2 parts
    expect(decodeTokenClaims(`header.${payload}.signature.extra`)).toBeNull() // 4 parts
  })

  it('returns null when any part is empty', () => {
    const payload = base64UrlEncode(JSON.stringify({ sub: 'U1', roles: ['ADMIN'], exp: futureExp() }))
    expect(decodeTokenClaims(`.${payload}.signature`)).toBeNull()
    expect(decodeTokenClaims('header..signature')).toBeNull()
    expect(decodeTokenClaims(`header.${payload}.`)).toBeNull()
  })

  it('returns null when the payload is not valid base64url', () => {
    expect(decodeTokenClaims('header.!!!not-base64!!!.signature')).toBeNull()
  })

  it('returns null when the payload is not JSON', () => {
    expect(decodeTokenClaims(makeToken('not json at all'))).toBeNull()
  })

  it('returns null when the payload JSON is not a plain object', () => {
    expect(decodeTokenClaims(makeToken('[1, 2, 3]'))).toBeNull()
    expect(decodeTokenClaims(makeToken('"user-123"'))).toBeNull()
    expect(decodeTokenClaims(makeToken('42'))).toBeNull()
    expect(decodeTokenClaims(makeToken('null'))).toBeNull()
  })
})

describe('decodeTokenClaims — sub claim (I-01)', () => {
  it('returns null when sub is missing', () => {
    expect(decodeTokenClaims(tokenFor({ roles: ['ADMIN'], exp: futureExp() }))).toBeNull()
  })

  it('returns null when sub is an empty string', () => {
    expect(decodeTokenClaims(tokenFor({ sub: '', roles: ['ADMIN'], exp: futureExp() }))).toBeNull()
  })

  it('returns null when sub is not a string', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 42, roles: ['ADMIN'], exp: futureExp() }))).toBeNull()
    expect(decodeTokenClaims(tokenFor({ sub: null, roles: ['ADMIN'], exp: futureExp() }))).toBeNull()
  })
})

describe('decodeTokenClaims — roles claim (I-01)', () => {
  it('returns null when roles is missing', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', exp: futureExp() }))).toBeNull()
  })

  it('returns null when roles is an empty array', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: [], exp: futureExp() }))).toBeNull()
  })

  it('returns null when roles contains only invalid values', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['SUPERUSER'], exp: futureExp() }))).toBeNull()
  })

  it('returns null when roles is not an array', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: 'ADMIN', exp: futureExp() }))).toBeNull()
  })

  it('skips invalid role entries but keeps the valid ones, first-seen order', () => {
    const claims = decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['EMPLOYEE', 'SUPERUSER'], exp: futureExp() }))
    expect(claims).not.toBeNull()
    expect(claims?.roles).toEqual(['EMPLOYEE'])
  })
})

describe('decodeTokenClaims — exp claim (I-01)', () => {
  it('returns null when exp is missing', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['ADMIN'] }))).toBeNull()
  })

  it('returns null when exp is not a number', () => {
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['ADMIN'], exp: '1798761600' }))).toBeNull()
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['ADMIN'], exp: true }))).toBeNull()
  })

  it('returns null when exp is not finite', () => {
    // JSON.stringify cannot encode Infinity/NaN (both serialize to null), so
    // the raw payload is crafted by hand: JSON.parse('1e999') yields Infinity.
    expect(decodeTokenClaims(makeToken('{"sub":"U1","roles":["ADMIN"],"exp":1e999}'))).toBeNull()
  })
})

describe('decodeTokenClaims — exp boundary (I-01, fake system time)', () => {
  beforeAll(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
  })

  afterAll(() => {
    vi.useRealTimers()
  })

  it('returns null when exp equals the current second (expiry must be strictly in the future)', () => {
    const exp = Math.floor(Date.now() / 1000)
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['EMPLOYEE'], exp }))).toBeNull()
  })

  it('accepts a token whose exp is one second in the future', () => {
    const exp = Math.floor(Date.now() / 1000) + 1
    expect(decodeTokenClaims(tokenFor({ sub: 'U1', roles: ['EMPLOYEE'], exp }))).toEqual({
      userId: 'U1',
      roles: ['EMPLOYEE'],
      expiresAt: new Date(exp * 1000).toISOString(),
    })
  })
})