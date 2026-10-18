import type { RoleType } from '../context/AuthContext'
import { describe, expect, it } from 'vitest'
import { PAGE_IDS, routeTable, type PageId, type RouteLayout } from './routeTable'

/**
 * D-01 acceptance test (frontend-tasks.md): the route table must contain exactly
 * one entry for every Page ID in frontend-spec.md §1 (P-01..P-23); public pages
 * (P-01, P-21..P-23) have empty `requiredRoles`; every other page (P-02..P-20)
 * has a non-empty `requiredRoles` and a non-empty `layout`; and all 23 entries
 * carry the exact path/role/layout values from the spec (guards against typos
 * in dynamic segments like '/travel-requests/:id/edit').
 */

const PUBLIC_PAGE_IDS: readonly PageId[] = ['P-01', 'P-21', 'P-22', 'P-23']

const ALL_ROLES: readonly RoleType[] = ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN']

/** frontend-spec.md §1 page table: path + route-level roles (api-contract.md §4). */
const EXPECTED_ROUTES: Record<
  PageId,
  { path: string; requiredRoles: RoleType[]; layout: RouteLayout; isPublic: boolean }
> = {
  'P-01': { path: '/login', requiredRoles: [], layout: 'auth', isPublic: true },
  'P-02': {
    path: '/',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-03': {
    path: '/travel-requests',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-04': {
    path: '/travel-requests/:id',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-05': {
    path: '/travel-requests/new',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-06': {
    path: '/travel-requests/:id/edit',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-07': {
    path: '/travel-requests/:id/approval',
    requiredRoles: ['MANAGER', 'FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-08': {
    path: '/approvals/travel-requests',
    requiredRoles: ['MANAGER', 'FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-09': {
    path: '/expenses',
    requiredRoles: ['EMPLOYEE', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-10': {
    path: '/expenses/:id',
    requiredRoles: ['EMPLOYEE', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-11': {
    path: '/expenses/new',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-12': {
    path: '/expenses/:id/edit',
    requiredRoles: ['EMPLOYEE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-13': {
    path: '/expenses/:id/review',
    requiredRoles: ['FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-14': {
    path: '/expenses/review-queue',
    requiredRoles: ['FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-15': {
    path: '/expenses/:id/reimburse',
    requiredRoles: ['FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-16': {
    path: '/admin/users',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-17': {
    path: '/admin/mappings',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-18': {
    path: '/admin/policy-configs',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-19': {
    path: '/admin/expense-categories',
    requiredRoles: ['ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-20': {
    path: '/audit',
    requiredRoles: ['ADMIN', 'FINANCE'],
    layout: 'authenticated',
    isPublic: false,
  },
  'P-21': { path: '/access-denied', requiredRoles: [], layout: 'authenticated', isPublic: true },
  'P-22': { path: '/404', requiredRoles: [], layout: 'auth', isPublic: true },
  'P-23': { path: '/error', requiredRoles: [], layout: 'auth', isPublic: true },
  'P-24': {
    path: '/account/password',
    requiredRoles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'],
    layout: 'authenticated',
    isPublic: false,
  },
}

describe('routeTable (D-01, frontend-spec.md §1)', () => {
  it('declares exactly one entry for every page ID P-01..P-24', () => {
    expect(routeTable).toHaveLength(PAGE_IDS.length)
    const pageIds = routeTable.map((entry) => entry.pageId)
    expect(new Set(pageIds).size).toBe(PAGE_IDS.length)
    for (const pageId of PAGE_IDS) {
      expect(pageIds).toContain(pageId)
    }
  })

  it('orders the entries P-01..P-24', () => {
    expect(routeTable.map((entry) => entry.pageId)).toEqual([...PAGE_IDS])
  })

  it('keeps public pages (P-01, P-21..P-23) unauthenticated with empty requiredRoles', () => {
    const publicEntries = routeTable.filter((entry) => PUBLIC_PAGE_IDS.includes(entry.pageId))
    expect(publicEntries).toHaveLength(PUBLIC_PAGE_IDS.length)
    for (const entry of publicEntries) {
      expect(entry.isPublic, `isPublic of ${entry.pageId}`).toBe(true)
      expect(entry.requiredRoles, `requiredRoles of ${entry.pageId}`).toEqual([])
      expect(entry.layout, `layout of ${entry.pageId}`).not.toBe('')
    }
  })

  it('requires non-empty roles and a layout on every non-public page (P-02..P-20, P-24)', () => {
    const protectedEntries = routeTable.filter((entry) => !PUBLIC_PAGE_IDS.includes(entry.pageId))
    expect(protectedEntries).toHaveLength(PAGE_IDS.length - PUBLIC_PAGE_IDS.length)
    for (const entry of protectedEntries) {
      expect(entry.isPublic, `isPublic of ${entry.pageId}`).toBe(false)
      expect(entry.requiredRoles.length, `requiredRoles of ${entry.pageId}`).toBeGreaterThan(0)
      expect(entry.layout, `layout of ${entry.pageId}`).not.toBe('')
      for (const role of entry.requiredRoles) {
        expect(ALL_ROLES).toContain(role)
      }
    }
  })

  it('matches the exact path, roles, layout, and isPublic value for all 24 pages', () => {
    for (const entry of routeTable) {
      const expected = EXPECTED_ROUTES[entry.pageId]
      expect(entry.path, `path of ${entry.pageId}`).toBe(expected.path)
      expect(entry.requiredRoles, `requiredRoles of ${entry.pageId}`).toEqual(
        expected.requiredRoles,
      )
      expect(entry.layout, `layout of ${entry.pageId}`).toBe(expected.layout)
      expect(entry.isPublic, `isPublic of ${entry.pageId}`).toBe(expected.isPublic)
    }
  })
})
