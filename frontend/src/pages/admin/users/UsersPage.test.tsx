/**
 * I-14 — UsersPage component tests (P-16, frontend-spec.md §4.12;
 * frontend-tasks.md I-14). Verification method per I-14: list rendering and
 * create/edit form interaction with the service mocked. The real
 * `adminService` module is replaced (namespace shape of the real export —
 * the `users` group's `create`/`update` are the methods the page calls), and
 * the page's data hook (`useAdminUsers`) is mocked with a loaded
 * `ServerDataState` fixture. Auth goes through the real AuthProvider
 * (setAuthState useEffect pattern, same as ReviewQueuePage.test.tsx) with an
 * ADMIN session; the page uses no navigation, but `useListQueryState`
 * (useSearchParams) requires a MemoryRouter.
 */

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useAdminUsers } from '../../../hooks/useAdminResources'
import type {
  RoleType,
  UserAccountCreateRequest,
  UserAccountRequest,
  UserAccountResponse,
} from '../../../models/admin'
import type { PaginatedResult } from '../../../models/pagination'
import { adminService } from '../../../services/adminService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import type { ErrorCategory } from '../../../models/error'
import { UsersPage } from './UsersPage'

vi.mock('../../../hooks/useAdminResources', () => ({
  useAdminUsers: vi.fn(),
}))

vi.mock('../../../services/adminService', () => ({
  // Full namespace shape of the real module (frontend/src/services/
  // adminService.ts) — only the `users` group is consumed by this page.
  adminService: {
    users: { list: vi.fn(), create: vi.fn(), update: vi.fn(), resetPassword: vi.fn() },
    mappings: { list: vi.fn(), create: vi.fn() },
    policyConfigs: { list: vi.fn(), create: vi.fn() },
    expenseCategories: { list: vi.fn(), create: vi.fn() },
  },
}))

const mockedUseAdminUsers = vi.mocked(useAdminUsers)
const mockedUsersCreate = vi.mocked(adminService.users.create)
const mockedUsersUpdate = vi.mocked(adminService.users.update)
const mockedResetPassword = vi.mocked(adminService.users.resetPassword)

// Shared literals (defined once, reused by every case).
const users: UserAccountResponse[] = [
  {
    id: 'user-1',
    fullName: 'Alice Anderson',
    email: 'alice@example.com',
    roles: ['EMPLOYEE'],
    active: true,
  },
  {
    id: 'user-2',
    fullName: 'Bob Brown',
    email: 'bob@example.com',
    roles: ['MANAGER', 'FINANCE'],
    active: false,
  },
]

const CREATE_FULL_NAME = 'Carol Clarke'
const CREATE_EMAIL = 'carol@example.com'
const CREATE_PASSWORD = 'InitialPassword123'
const CREATE_ROLES: RoleType[] = ['EMPLOYEE']
const createPayload: UserAccountCreateRequest = {
  fullName: CREATE_FULL_NAME,
  email: CREATE_EMAIL,
  roles: CREATE_ROLES,
  password: CREATE_PASSWORD,
}

const EDIT_TARGET = users[1]
const EDIT_EMAIL = 'bob.brown@example.com'
const editPayload: UserAccountRequest = {
  fullName: EDIT_TARGET.fullName,
  email: EDIT_EMAIL,
  roles: EDIT_TARGET.roles,
  active: EDIT_TARGET.active,
}

const EMAIL_FIELD_ERROR_MESSAGE = 'Email is already registered.'
const createConflictError: ClassifiedErrorInstance = Object.assign(
  new Error(EMAIL_FIELD_ERROR_MESSAGE),
  {
    category: 'CONFLICT' as ErrorCategory,
    status: 409,
    errorCode: 'DUPLICATE_RESOURCE',
    timestamp: '2026-01-15T10:00:00Z',
    fieldErrors: [{ field: 'email', message: EMAIL_FIELD_ERROR_MESSAGE }],
  },
)

const fullNameInput = () => screen.getByLabelText(/^Full name/) as HTMLInputElement
const emailInput = () => screen.getByLabelText(/^Email/) as HTMLInputElement
const passwordInput = () => screen.getByLabelText(/^Password/) as HTMLInputElement
const rolesSelect = () => screen.getByLabelText(/^Roles/) as HTMLSelectElement

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

  return <UsersPage />
}

function renderPage(
  path = '/admin/users',
  overrides: Partial<ServerDataState<PaginatedResult<UserAccountResponse>>> = {},
) {
  const refetch = vi.fn()
  mockedUseAdminUsers.mockReturnValue({
    data: { content: users, page: 0, size: 20, totalElements: users.length, totalPages: 1 },
    isLoading: false,
    error: null,
    refetch,
    ...overrides,
  } satisfies ServerDataState<PaginatedResult<UserAccountResponse>>)
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
  await user.click(screen.getByRole('button', { name: 'New user' }))
  await user.type(fullNameInput(), CREATE_FULL_NAME)
  await user.type(emailInput(), CREATE_EMAIL)
  await user.type(passwordInput(), CREATE_PASSWORD)
  await user.selectOptions(rolesSelect(), CREATE_ROLES)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('UsersPage (I-14)', () => {
  it('renders the loading state while accounts are being fetched', () => {
    renderPage('/admin/users', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('renders the fixture rows with names, emails, roles, and active state', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument()
    expect(screen.getByText('user-1')).toBeInTheDocument()
    expect(screen.getByText('Alice Anderson')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('EMPLOYEE')).toBeInTheDocument()
    expect(screen.getByText('user-2')).toBeInTheDocument()
    expect(screen.getByText('Bob Brown')).toBeInTheDocument()
    expect(screen.getByText('bob@example.com')).toBeInTheDocument()
    expect(screen.getByText('MANAGER, FINANCE')).toBeInTheDocument()
  })

  it('renders an empty state when no accounts exist', () => {
    renderPage('/admin/users', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No users found.')).toBeInTheDocument()
  })

  it('shows a retryable error when the users list fails to load', async () => {
    const refetch = vi.fn()
    renderPage('/admin/users', {
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

    expect(screen.getByRole('alert')).toHaveTextContent('Users could not be loaded. Please try again.')
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('opens the create form panel on "New user" and closes it on Cancel', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Users' })
    expect(screen.queryByTestId('users-page-form-panel')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New user' }))
    // Create mode: the form mounts empty with the create submit label (J-06).
    expect(screen.getByTestId('users-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'New user', level: 2 })).toBeInTheDocument()
    expect(screen.queryByTestId('users-page-new-action')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create user' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByTestId('users-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByTestId('users-page-new-action')).toBeInTheDocument()
    expect(screen.getByText('Alice Anderson')).toBeInTheDocument()
  })

  it('creates a user: submits the payload once, hides the panel, and refetches', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Users' })
    await openCreatePanel(user)
    await user.click(screen.getByTestId('user-account-form-submit'))

    // `POST /api/admin/users` (api-contract.md §5.12); `active` is omitted
    // when the flag is unset (TR-01 omission convention).
    await waitFor(() => expect(mockedUsersCreate).toHaveBeenCalledTimes(1))
    expect(mockedUsersCreate).toHaveBeenCalledWith(createPayload)
    expect(screen.queryByTestId('users-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByText('Alice Anderson')).toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('edits a user: prefills from the row, submits (id, payload) via update, and hides the panel', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Users' })
    await user.click(screen.getByTestId(`users-page-edit-${EDIT_TARGET.id}`))

    // Edit mode pre-fills from the clicked row (J-06 mount-only defaults) and
    // relabels the submit button.
    expect(screen.getByTestId('users-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(fullNameInput().value).toBe(EDIT_TARGET.fullName)
    expect(emailInput().value).toBe(EDIT_TARGET.email)
    expect(Array.from(rolesSelect().selectedOptions).map((option) => option.value)).toEqual(
      EDIT_TARGET.roles,
    )

    await user.clear(emailInput())
    await user.type(emailInput(), EDIT_EMAIL)
    await user.click(screen.getByTestId('user-account-form-submit'))

    // `PUT /api/admin/users/{id}` (api-contract.md §5.12) — unlike the
    // mappings upsert, users has a dedicated update endpoint.
    await waitFor(() => expect(mockedUsersUpdate).toHaveBeenCalledTimes(1))
    expect(mockedUsersUpdate).toHaveBeenCalledWith(EDIT_TARGET.id, editPayload)
    expect(screen.queryByTestId('users-page-form-panel')).not.toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('keeps the panel open with an inline field error when create is rejected with a field error', async () => {
    const user = userEvent.setup()
    mockedUsersCreate.mockRejectedValueOnce(createConflictError)
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Users' })
    await openCreatePanel(user)
    await user.click(screen.getByTestId('user-account-form-submit'))

    await waitFor(() => expect(mockedUsersCreate).toHaveBeenCalledTimes(1))
    // The ClassifiedError's fieldError maps onto the email field inline
    // (frontend-api-mapping.md §3/§4) and the form stays open for corrections.
    expect(await screen.findByText(EMAIL_FIELD_ERROR_MESSAGE)).toBeInTheDocument()
    expect(screen.getByTestId('users-page-form-panel')).toBeInTheDocument()
    expect(refetch).not.toHaveBeenCalled()
  })

  describe('password reset (#14)', () => {
    it('confirms, then shows the temporary password exactly once', async () => {
      const user = userEvent.setup()
      mockedResetPassword.mockResolvedValueOnce({ temporaryPassword: 'Tmp-7h3k-9QzX-21' })
      renderPage()

      await user.click(screen.getByTestId('users-page-reset-user-1'))
      expect(mockedResetPassword).not.toHaveBeenCalled()
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reset password' }))

      const panel = await screen.findByTestId('users-page-temporary-password')
      expect(panel).toHaveTextContent('Tmp-7h3k-9QzX-21')
      expect(panel).toHaveTextContent('alice@example.com')
      expect(mockedResetPassword).toHaveBeenCalledWith('user-1')

      await user.click(screen.getByRole('button', { name: 'Done' }))
      expect(screen.queryByText('Tmp-7h3k-9QzX-21')).not.toBeInTheDocument()
    })

    it('does nothing when the administrator keeps the current password', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByTestId('users-page-reset-user-1'))
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Keep current password' }))
      expect(mockedResetPassword).not.toHaveBeenCalled()
    })
  })
})
