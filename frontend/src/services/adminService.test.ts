// adminService unit tests (F-04, frontend-tasks.md — "F. Services").
// The shared HTTP client (./httpClient — F-01) is fully mocked — no network
// calls — and the tests assert the endpoint, method (which client verb is
// called), params/body construction, and the typed passthrough of the mocked
// response for every function of every resource group (frontend-api-mapping.md
// §1.3; api-contract.md §4.3/§5.12–§5.15/§7; frontend-plan.md §14 test matrix
// "Services" row).

import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  EmployeeManagerMappingRequest,
  EmployeeManagerMappingResponse,
  ExpenseCategoryRequest,
  ExpenseCategoryResponse,
  PolicyConfigRequest,
  PolicyConfigResponse,
  UserAccountCreateRequest,
  UserAccountRequest,
  UserAccountResponse,
} from '../models/admin'
import type { PaginatedResult } from '../models/pagination'
import { adminService } from './adminService'

/**
 * Shared spies standing in for the shared HTTP client's verbs — the client
 * itself is the seam (frontend-plan.md §6: every service reuses it).
 * `vi.hoisted` keeps the references reachable from the hoisted `vi.mock`
 * factory (same pattern as httpClient.test.ts).
 */
const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}))

vi.mock('./httpClient', () => ({
  get: http.get,
  post: http.post,
  put: http.put,
}))

/** Typed create payload (api-contract.md §5.12). */
const userCreatePayload: UserAccountCreateRequest = {
  fullName: 'Asha Verma',
  email: 'asha.verma@company.com',
  roles: ['EMPLOYEE', 'MANAGER'],
  password: 'InitialPassword123',
  active: true,
}

/** Typed `UserAccountResponse` read model (api-contract.md §5.12). */
const userAccount: UserAccountResponse = {
  id: 'ua-1',
  fullName: 'Asha Verma',
  email: 'asha.verma@company.com',
  roles: ['EMPLOYEE', 'MANAGER'],
  active: true,
}

/** Typed paginated envelope (api-contract.md §7). */
const userPage: PaginatedResult<UserAccountResponse> = {
  content: [userAccount],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

/** Typed `EmployeeManagerMappingRequest` payload (api-contract.md §5.13). */
const mappingCreatePayload: EmployeeManagerMappingRequest = {
  employeeId: 'emp-1',
  managerId: null,
  effectiveFrom: '2026-10-01',
}

/**
 * Typed `EmployeeManagerMappingResponse` read model (api-contract.md §5.13);
 * `managerId` is null for top-of-hierarchy managers (Q3).
 */
const mapping: EmployeeManagerMappingResponse = {
  id: 'map-1',
  employeeId: 'emp-1',
  managerId: null,
  effectiveFrom: '2026-10-01',
}

/** Typed paginated envelope (api-contract.md §7). */
const mappingPage: PaginatedResult<EmployeeManagerMappingResponse> = {
  content: [mapping],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

/** Typed `PolicyConfigRequest` payload (api-contract.md §5.14). */
const policyConfigCreatePayload: PolicyConfigRequest = {
  categoryCode: 'TRANSPORT',
  receiptThresholdAmount: 1500,
  currency: 'INR',
  active: true,
}

/** Typed `PolicyConfigResponse` read model (api-contract.md §5.14). */
const policyConfig: PolicyConfigResponse = {
  id: 'pc-1',
  categoryCode: 'TRANSPORT',
  receiptThresholdAmount: 1500,
  currency: 'INR',
  active: true,
}

/** Typed paginated envelope (api-contract.md §7). */
const policyConfigPage: PaginatedResult<PolicyConfigResponse> = {
  content: [policyConfig],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

/** Typed `ExpenseCategoryRequest` payload (api-contract.md §5.15). */
const expenseCategoryCreatePayload: ExpenseCategoryRequest = {
  code: 'TRANSPORT',
  label: 'Transport',
  active: true,
}

/** Typed `ExpenseCategoryResponse` read model (api-contract.md §5.15). */
const expenseCategory: ExpenseCategoryResponse = {
  id: 'cat-1',
  code: 'TRANSPORT',
  label: 'Transport',
  active: true,
}

/** Typed paginated envelope (api-contract.md §7). */
const expenseCategoryPage: PaginatedResult<ExpenseCategoryResponse> = {
  content: [expenseCategory],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

beforeEach(() => {
  http.get.mockReset()
  http.post.mockReset()
  http.put.mockReset()
})

describe('users (P-16, frontend-api-mapping.md §1.3)', () => {
  describe('list (GET /api/admin/users)', () => {
    it('builds the page/size/sort/active/role query params and returns the typed envelope', async () => {
      http.get.mockResolvedValueOnce(userPage)

      const result = await adminService.users.list({
        page: 0,
        size: 20,
        sort: 'fullName,asc',
        active: true,
        role: 'ADMIN',
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/users', {
        page: 0,
        size: 20,
        sort: 'fullName,asc',
        active: true,
        role: 'ADMIN',
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(userPage)
    })

    it('passes a repeatable sort param through (api-contract.md §7)', async () => {
      http.get.mockResolvedValueOnce(userPage)

      const result = await adminService.users.list({
        sort: ['fullName,asc', 'email,desc'],
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/users', {
        sort: ['fullName,asc', 'email,desc'],
      })
      expect(result).toBe(userPage)
    })

    it('omits the params argument when called without filters', async () => {
      http.get.mockResolvedValueOnce(userPage)

      const result = await adminService.users.list()

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/users', undefined)
      expect(result).toBe(userPage)
    })
  })

  describe('getById (GET /api/admin/users/{id})', () => {
    it('fetches the user by id and returns the typed response', async () => {
      http.get.mockResolvedValueOnce(userAccount)

      const result = await adminService.users.getById('ua-1')

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/users/ua-1')
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(userAccount)
    })
  })

  describe('create (POST /api/admin/users)', () => {
    it('posts the create request with initial roles and returns the typed response', async () => {
      http.post.mockResolvedValueOnce(userAccount)

      const result = await adminService.users.create(userCreatePayload)

      expect(http.post).toHaveBeenCalledTimes(1)
      expect(http.post).toHaveBeenCalledWith('/api/admin/users', userCreatePayload)
      expect(http.get).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(userAccount)
    })
  })

  describe('update (PUT /api/admin/users/{id})', () => {
    it('puts the update request against the id and returns the typed response', async () => {
      const updatePayload: UserAccountRequest = {
        fullName: userCreatePayload.fullName,
        email: userCreatePayload.email,
        roles: ['MANAGER'],
        active: false,
      }
      http.put.mockResolvedValueOnce(userAccount)

      const result = await adminService.users.update('ua-1', updatePayload)

      expect(http.put).toHaveBeenCalledTimes(1)
      expect(http.put).toHaveBeenCalledWith('/api/admin/users/ua-1', updatePayload)
      expect(http.get).not.toHaveBeenCalled()
      expect(http.post).not.toHaveBeenCalled()
      expect(result).toBe(userAccount)
    })
  })
})

describe('mappings (P-17, frontend-api-mapping.md §1.3)', () => {
  describe('list (GET /api/admin/employee-manager-mappings)', () => {
    it('builds the page/size/sort query params and returns the typed envelope', async () => {
      http.get.mockResolvedValueOnce(mappingPage)

      const result = await adminService.mappings.list({
        page: 1,
        size: 10,
        sort: 'effectiveFrom,desc',
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/employee-manager-mappings', {
        page: 1,
        size: 10,
        sort: 'effectiveFrom,desc',
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(mappingPage)
    })

    it('omits the params argument when called without filters', async () => {
      http.get.mockResolvedValueOnce(mappingPage)

      const result = await adminService.mappings.list()

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith(
        '/api/admin/employee-manager-mappings',
        undefined,
      )
      expect(result).toBe(mappingPage)
    })
  })

  describe('create (POST /api/admin/employee-manager-mappings)', () => {
    it('posts the mapping request (managerId null for top-of-hierarchy managers, Q3) and returns the typed response', async () => {
      http.post.mockResolvedValueOnce(mapping)

      const result = await adminService.mappings.create(mappingCreatePayload)

      expect(http.post).toHaveBeenCalledTimes(1)
      expect(http.post).toHaveBeenCalledWith(
        '/api/admin/employee-manager-mappings',
        mappingCreatePayload,
      )
      expect(http.get).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(mapping)
    })
  })
})

describe('policyConfigs (P-18, frontend-api-mapping.md §1.3)', () => {
  describe('list (GET /api/admin/policy-configs)', () => {
    it('builds the page/size/sort/categoryCode query params and returns the typed envelope', async () => {
      http.get.mockResolvedValueOnce(policyConfigPage)

      const result = await adminService.policyConfigs.list({
        page: 0,
        size: 20,
        sort: 'categoryCode,asc',
        categoryCode: 'TRANSPORT',
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/policy-configs', {
        page: 0,
        size: 20,
        sort: 'categoryCode,asc',
        categoryCode: 'TRANSPORT',
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(policyConfigPage)
    })

    it('omits the params argument when called without filters', async () => {
      http.get.mockResolvedValueOnce(policyConfigPage)

      const result = await adminService.policyConfigs.list()

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/policy-configs', undefined)
      expect(result).toBe(policyConfigPage)
    })
  })

  describe('create (POST /api/admin/policy-configs)', () => {
    it('posts the policy config request and returns the typed response', async () => {
      http.post.mockResolvedValueOnce(policyConfig)

      const result = await adminService.policyConfigs.create(policyConfigCreatePayload)

      expect(http.post).toHaveBeenCalledTimes(1)
      expect(http.post).toHaveBeenCalledWith(
        '/api/admin/policy-configs',
        policyConfigCreatePayload,
      )
      expect(http.get).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(policyConfig)
    })
  })

  describe('update (PUT /api/admin/policy-configs/{id})', () => {
    it('updates the selected policy by id and returns the typed response', async () => {
      http.put.mockResolvedValueOnce(policyConfig)

      const result = await adminService.policyConfigs.update('pc-1', policyConfigCreatePayload)

      expect(http.put).toHaveBeenCalledOnce()
      expect(http.put).toHaveBeenCalledWith(
        '/api/admin/policy-configs/pc-1',
        policyConfigCreatePayload,
      )
      expect(http.post).not.toHaveBeenCalled()
      expect(result).toBe(policyConfig)
    })
  })
})

describe('expenseCategories (P-19, frontend-api-mapping.md §1.3)', () => {
  describe('list (GET /api/admin/expense-categories)', () => {
    it('builds the page/size/sort/active query params and returns the typed envelope', async () => {
      http.get.mockResolvedValueOnce(expenseCategoryPage)

      const result = await adminService.expenseCategories.list({
        page: 0,
        size: 20,
        sort: 'code,asc',
        active: true,
      })

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith('/api/admin/expense-categories', {
        page: 0,
        size: 20,
        sort: 'code,asc',
        active: true,
      })
      expect(http.post).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(expenseCategoryPage)
    })

    it('omits the params argument when called without filters', async () => {
      http.get.mockResolvedValueOnce(expenseCategoryPage)

      const result = await adminService.expenseCategories.list()

      expect(http.get).toHaveBeenCalledTimes(1)
      expect(http.get).toHaveBeenCalledWith(
        '/api/admin/expense-categories',
        undefined,
      )
      expect(result).toBe(expenseCategoryPage)
    })
  })

  describe('create (POST /api/admin/expense-categories)', () => {
    it('posts the expense category request and returns the typed response', async () => {
      http.post.mockResolvedValueOnce(expenseCategory)

      const result = await adminService.expenseCategories.create(
        expenseCategoryCreatePayload,
      )

      expect(http.post).toHaveBeenCalledTimes(1)
      expect(http.post).toHaveBeenCalledWith(
        '/api/admin/expense-categories',
        expenseCategoryCreatePayload,
      )
      expect(http.get).not.toHaveBeenCalled()
      expect(http.put).not.toHaveBeenCalled()
      expect(result).toBe(expenseCategory)
    })
  })
})