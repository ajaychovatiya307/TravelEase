// useAdminResources unit tests (F-06, frontend-tasks.md — "F. Services").
// `adminService` (F-04) is fully mocked — no network calls — and the tests
// assert the service-call params derived from `ListQueryState` for each of
// the four resource groups, per frontend-plan.md §14 test matrix "Hooks" row.

import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type {
  EmployeeManagerMappingResponse,
  ExpenseCategoryResponse,
  PolicyConfigResponse,
  UserAccountResponse,
} from '../models/admin'
import type { PaginatedResult } from '../models/pagination'
import type { ListQueryState } from './useListQueryState'
import {
  useAdminMappings,
  useAdminUsers,
  useExpenseCategories,
  usePolicyConfigs,
} from './useAdminResources'

const adminService = vi.hoisted(() => ({
  users: { list: vi.fn() },
  mappings: { list: vi.fn() },
  policyConfigs: { list: vi.fn() },
  expenseCategories: { list: vi.fn() },
}))

vi.mock('../services/adminService', () => ({ adminService }))

const baseQueryState: ListQueryState = {
  page: 0,
  size: 20,
  sort: { field: 'createdAt', direction: 'desc' },
  filters: {},
}

function emptyPage<T>(): PaginatedResult<T> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

describe('useAdminUsers (F-06)', () => {
  it('calls adminService.users.list with active/role filters parsed from ListQueryState', async () => {
    adminService.users.list.mockResolvedValue(emptyPage<UserAccountResponse>())
    const { result } = renderHook(() =>
      useAdminUsers({ ...baseQueryState, filters: { active: 'true', role: 'ADMIN' } }),
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(adminService.users.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      active: true,
      role: 'ADMIN',
    })
  })
})

describe('useAdminMappings (F-06)', () => {
  it('calls adminService.mappings.list with only page/size/sort', async () => {
    adminService.mappings.list.mockResolvedValue(emptyPage<EmployeeManagerMappingResponse>())
    const { result } = renderHook(() => useAdminMappings(baseQueryState))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(adminService.mappings.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })
  })
})

describe('usePolicyConfigs (F-06)', () => {
  it('calls adminService.policyConfigs.list with the categoryCode filter', async () => {
    adminService.policyConfigs.list.mockResolvedValue(emptyPage<PolicyConfigResponse>())
    const { result } = renderHook(() =>
      usePolicyConfigs({ ...baseQueryState, filters: { categoryCode: 'TRAVEL' } }),
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(adminService.policyConfigs.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      categoryCode: 'TRAVEL',
    })
  })
})

describe('useExpenseCategories (F-06)', () => {
  it('calls adminService.expenseCategories.list with the active filter', async () => {
    adminService.expenseCategories.list.mockResolvedValue(emptyPage<ExpenseCategoryResponse>())
    const { result } = renderHook(() =>
      useExpenseCategories({ ...baseQueryState, filters: { active: 'false' } }),
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(adminService.expenseCategories.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      active: false,
    })
  })
})
