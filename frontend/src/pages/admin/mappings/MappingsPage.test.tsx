/**
 * I-14 — MappingsPage component tests (P-17, frontend-spec.md §4.12;
 * frontend-tasks.md I-14). Verification method per I-14: list rendering and
 * create/edit form interaction with the service mocked. The real
 * `adminService` module is replaced (namespace shape of the real export —
 * the `mappings` group only exposes `list`/`create`), and the page's data
 * hook (`useAdminMappings`) is mocked with a loaded `ServerDataState`
 * fixture. Auth goes through the real AuthProvider (setAuthState useEffect
 * pattern, same as ReviewQueuePage.test.tsx) with an ADMIN session; the page
 * uses no navigation, but `useListQueryState` (useSearchParams) requires a
 * MemoryRouter.
 *
 * Upsert note (api-contract.md §5.13): create AND edit both submit through
 * the single `POST /api/admin/employee-manager-mappings` endpoint (upsert on
 * `employeeId`), so both flows assert `adminService.mappings.create`.
 */

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useAdminMappings } from '../../../hooks/useAdminResources'
import type {
  EmployeeManagerMappingRequest,
  EmployeeManagerMappingResponse,
} from '../../../models/admin'
import type { PaginatedResult } from '../../../models/pagination'
import { adminService } from '../../../services/adminService'
import { MappingsPage } from './MappingsPage'

vi.mock('../../../hooks/useAdminResources', () => ({
  useAdminMappings: vi.fn(),
}))

vi.mock('../../../services/adminService', () => ({
  // Full namespace shape of the real module (frontend/src/services/
  // adminService.ts) — only the `mappings` group is consumed by this page,
  // and it exposes no `update` (single POST upsert, api-contract.md §5.13).
  adminService: {
    users: { list: vi.fn(), create: vi.fn(), update: vi.fn() },
    mappings: { list: vi.fn(), create: vi.fn() },
    policyConfigs: { list: vi.fn(), create: vi.fn() },
    expenseCategories: { list: vi.fn(), create: vi.fn() },
  },
}))

const mockedUseAdminMappings = vi.mocked(useAdminMappings)
const mockedMappingsCreate = vi.mocked(adminService.mappings.create)

// Shared literals (defined once, reused by every case).
const mappings: EmployeeManagerMappingResponse[] = [
  {
    id: 'mapping-1',
    employeeId: 'user-1',
    managerId: 'user-9',
    effectiveFrom: '2026-04-01',
  },
  // Top-of-hierarchy manager — `managerId` is null (Q3) and renders as
  // 'No manager' in the list.
  {
    id: 'mapping-2',
    employeeId: 'user-2',
    managerId: null,
    effectiveFrom: '2026-05-01',
  },
]

const CREATE_EMPLOYEE_ID = 'user-3'
const CREATE_MANAGER_ID = 'user-9'
const CREATE_EFFECTIVE_FROM = '2026-06-01'
const createPayload: EmployeeManagerMappingRequest = {
  employeeId: CREATE_EMPLOYEE_ID,
  managerId: CREATE_MANAGER_ID,
  effectiveFrom: CREATE_EFFECTIVE_FROM,
}

const EDIT_TARGET = mappings[1]
const EDIT_MANAGER_ID = 'user-9'
const editPayload: EmployeeManagerMappingRequest = {
  employeeId: EDIT_TARGET.employeeId,
  managerId: EDIT_MANAGER_ID,
  effectiveFrom: EDIT_TARGET.effectiveFrom,
}

const employeeIdInput = () => screen.getByLabelText(/^Employee ID/) as HTMLInputElement
const managerIdInput = () => screen.getByLabelText(/^Manager ID/) as HTMLInputElement
const effectiveFromInput = () => screen.getByLabelText(/^Effective from/) as HTMLInputElement

function AuthenticatedPage() {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'admin-1',
      roles: ['ADMIN'],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'token',
    })
  }, [setAuthState])

  if (!authState.isAuthenticated) {
    return null
  }

  return <MappingsPage />
}

function renderPage(
  path = '/admin/mappings',
  overrides: Partial<ServerDataState<PaginatedResult<EmployeeManagerMappingResponse>>> = {},
) {
  const refetch = vi.fn()
  mockedUseAdminMappings.mockReturnValue({
    data: {
      content: mappings,
      page: 0,
      size: 20,
      totalElements: mappings.length,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch,
    ...overrides,
  } satisfies ServerDataState<PaginatedResult<EmployeeManagerMappingResponse>>)
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AuthenticatedPage />
      </AuthProvider>
    </MemoryRouter>,
  )
  return { refetch, ...view }
}

async function openCreatePanel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'New mapping' }))
  await user.type(employeeIdInput(), CREATE_EMPLOYEE_ID)
  await user.type(managerIdInput(), CREATE_MANAGER_ID)
  // The date field is a raw text input (J-07) — set it directly, the same
  // way the form's own unit test does.
  fireEvent.change(effectiveFromInput(), { target: { value: CREATE_EFFECTIVE_FROM } })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('MappingsPage (I-14)', () => {
  it('renders the loading state while mappings are being fetched', () => {
    renderPage('/admin/mappings', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('renders the fixture rows, including the null-manager row as "No manager"', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Employee-Manager Mappings' }),
    ).toBeInTheDocument()
    expect(screen.getByText('mapping-1')).toBeInTheDocument()
    expect(screen.getByText('user-1')).toBeInTheDocument()
    expect(screen.getByText('user-9')).toBeInTheDocument()
    expect(screen.getByText('2026-04-01')).toBeInTheDocument()
    expect(screen.getByText('mapping-2')).toBeInTheDocument()
    expect(screen.getByText('user-2')).toBeInTheDocument()
    // A `null` managerId renders as 'No manager' (Q3).
    expect(screen.getByText('No manager')).toBeInTheDocument()
    expect(screen.getByText('2026-05-01')).toBeInTheDocument()
  })

  it('renders an empty state when no mappings exist', () => {
    renderPage('/admin/mappings', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No mappings found.')).toBeInTheDocument()
  })

  it('shows a retryable error when mappings fail to load', async () => {
    const refetch = vi.fn()
    renderPage('/admin/mappings', {
      data: null,
      error: Object.assign(new Error('transport details'), {
        category: 'NETWORK_ERROR' as const,
        status: null,
        errorCode: null,
        timestamp: null,
        fieldErrors: [],
      }),
      refetch,
    })

    expect(screen.getByRole('alert')).toHaveTextContent('Mappings could not be loaded. Please try again.')
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('opens the create form panel on "New mapping" and closes it on Cancel', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Employee-Manager Mappings' })
    expect(screen.queryByTestId('mappings-page-form-panel')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New mapping' }))
    // Create mode: the form mounts empty with the create submit label (J-07).
    expect(screen.getByTestId('mappings-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'New mapping', level: 2 })).toBeInTheDocument()
    expect(screen.queryByTestId('mappings-page-new-action')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create mapping' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByTestId('mappings-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByTestId('mappings-page-new-action')).toBeInTheDocument()
    expect(screen.getByText('No manager')).toBeInTheDocument()
  })

  it('creates a mapping: submits the payload once via the upsert create, hides the panel, and refetches', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Employee-Manager Mappings' })
    await openCreatePanel(user)
    await user.click(screen.getByTestId('employee-manager-mapping-form-submit'))

    // `POST /api/admin/employee-manager-mappings` (api-contract.md §5.13) —
    // the single upsert endpoint keyed on `employeeId`.
    await waitFor(() => expect(mockedMappingsCreate).toHaveBeenCalledTimes(1))
    expect(mockedMappingsCreate).toHaveBeenCalledWith(createPayload)
    expect(screen.queryByTestId('mappings-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByText('No manager')).toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('edits a mapping: prefills from the row and submits the edited payload via the same upsert create', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Employee-Manager Mappings' })
    await user.click(screen.getByTestId(`mappings-page-edit-${EDIT_TARGET.id}`))

    // Edit mode pre-fills from the clicked row (J-07 mount-only defaults); a
    // `null` managerId becomes an empty manager field (Q3).
    expect(screen.getByTestId('mappings-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(employeeIdInput().value).toBe(EDIT_TARGET.employeeId)
    expect(managerIdInput().value).toBe('')
    expect(effectiveFromInput().value).toBe(EDIT_TARGET.effectiveFrom)

    await user.type(managerIdInput(), EDIT_MANAGER_ID)
    await user.click(screen.getByTestId('employee-manager-mapping-form-submit'))

    // Same single `POST` upsert as create — `adminService.mappings` exposes
    // no separate update method (api-contract.md §5.13).
    await waitFor(() => expect(mockedMappingsCreate).toHaveBeenCalledTimes(1))
    expect(mockedMappingsCreate).toHaveBeenCalledWith(editPayload)
    expect(screen.queryByTestId('mappings-page-form-panel')).not.toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })
})
