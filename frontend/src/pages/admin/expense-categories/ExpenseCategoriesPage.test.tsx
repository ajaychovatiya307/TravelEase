/**
 * I-14 — ExpenseCategoriesPage component tests (P-19, frontend-spec.md
 * §4.12; frontend-tasks.md I-14). Verification method per I-14: list
 * rendering and create/edit form interaction with the service mocked. The
 * real `adminService` module is replaced (namespace shape of the real export
 * — the `expenseCategories` group only exposes `list`/`create`), and the
 * page's data hook (`useExpenseCategories`) is mocked with a loaded
 * `ServerDataState` fixture. Auth goes through the real AuthProvider
 * (setAuthState useEffect pattern, same as ReviewQueuePage.test.tsx) with an
 * ADMIN session; the page uses no navigation, but `useListQueryState`
 * (useSearchParams) requires a MemoryRouter.
 *
 * Upsert note (api-contract.md §5.15): create AND edit both submit through
 * the single `POST /api/admin/expense-categories` endpoint (upsert on
 * `code`), so both flows assert
 * `adminService.expenseCategories.create`.
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useExpenseCategories } from '../../../hooks/useAdminResources'
import type { ExpenseCategoryRequest, ExpenseCategoryResponse } from '../../../models/admin'
import type { PaginatedResult } from '../../../models/pagination'
import { adminService } from '../../../services/adminService'
import { EXPENSE_CATEGORY_MESSAGES } from '../../../validation/adminSchemas'
import { ExpenseCategoriesPage } from './ExpenseCategoriesPage'

vi.mock('../../../hooks/useAdminResources', () => ({
  useExpenseCategories: vi.fn(),
}))

vi.mock('../../../services/adminService', () => ({
  // Full namespace shape of the real module (frontend/src/services/
  // adminService.ts) — only the `expenseCategories` group is consumed by
  // this page, and it exposes no `update` (single POST upsert,
  // api-contract.md §5.15).
  adminService: {
    users: { list: vi.fn(), create: vi.fn(), update: vi.fn() },
    mappings: { list: vi.fn(), create: vi.fn() },
    policyConfigs: { list: vi.fn(), create: vi.fn() },
    expenseCategories: { list: vi.fn(), create: vi.fn() },
  },
}))

const mockedUseExpenseCategories = vi.mocked(useExpenseCategories)
const mockedExpenseCategoriesCreate = vi.mocked(adminService.expenseCategories.create)

// Shared literals (defined once, reused by every case).
const expenseCategories: ExpenseCategoryResponse[] = [
  {
    id: 'category-1',
    code: 'MEALS',
    label: 'Meals',
    active: true,
  },
  {
    id: 'category-2',
    code: 'TRAVEL',
    label: 'Travel',
    active: false,
  },
]

const CREATE_CODE = 'TRAINING'
const CREATE_LABEL = 'Training'
const createPayload: ExpenseCategoryRequest = {
  code: CREATE_CODE,
  label: CREATE_LABEL,
}

const EDIT_TARGET = expenseCategories[1]
const EDIT_LABEL = 'Travel & Accommodation'
const editPayload: ExpenseCategoryRequest = {
  code: EDIT_TARGET.code,
  label: EDIT_LABEL,
  active: EDIT_TARGET.active,
}

const codeInput = () => screen.getByLabelText(/^Code/) as HTMLInputElement
const labelInput = () => screen.getByLabelText(/^Label/) as HTMLInputElement
const activeSelect = () => screen.getByLabelText(/^Active/) as HTMLSelectElement

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

  return <ExpenseCategoriesPage />
}

function renderPage(
  path = '/admin/expense-categories',
  overrides: Partial<ServerDataState<PaginatedResult<ExpenseCategoryResponse>>> = {},
) {
  const refetch = vi.fn()
  mockedUseExpenseCategories.mockReturnValue({
    data: {
      content: expenseCategories,
      page: 0,
      size: 20,
      totalElements: expenseCategories.length,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch,
    ...overrides,
  } satisfies ServerDataState<PaginatedResult<ExpenseCategoryResponse>>)
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AuthenticatedPage />
      </AuthProvider>
    </MemoryRouter>,
  )
  return { refetch, ...view }
}

async function openCreatePanel(
  user: ReturnType<typeof userEvent.setup>,
  code = CREATE_CODE,
) {
  await user.click(screen.getByRole('button', { name: 'New category' }))
  await user.type(codeInput(), code)
  await user.type(labelInput(), CREATE_LABEL)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ExpenseCategoriesPage (I-14)', () => {
  it('renders the loading state while categories are being fetched', () => {
    renderPage('/admin/expense-categories', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('renders the fixture rows with codes, labels, and active state', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Expense Categories' })).toBeInTheDocument()
    expect(screen.getByText('category-1')).toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
    expect(screen.getByText('Meals')).toBeInTheDocument()
    expect(screen.getByText('Yes')).toBeInTheDocument()
    expect(screen.getByText('category-2')).toBeInTheDocument()
    expect(screen.getByText('TRAVEL')).toBeInTheDocument()
    expect(screen.getByText('Travel')).toBeInTheDocument()
    expect(screen.getByText('No')).toBeInTheDocument()
  })

  it('renders an empty state when no categories exist', () => {
    renderPage('/admin/expense-categories', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No expense categories found.')).toBeInTheDocument()
  })

  it('shows a retryable error when categories fail to load', async () => {
    const refetch = vi.fn()
    renderPage('/admin/expense-categories', {
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

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Expense categories could not be loaded. Please try again.',
    )
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('opens the create form panel on "New category" and closes it on Cancel', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Expense Categories' })
    expect(screen.queryByTestId('expense-categories-page-form-panel')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New category' }))
    // Create mode: the form mounts empty with the create submit label (J-09).
    expect(screen.getByTestId('expense-categories-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create category' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByTestId('expense-categories-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
  })

  it('creates a category: submits the payload once via the upsert create, hides the panel, and refetches', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Expense Categories' })
    await openCreatePanel(user)
    await user.click(screen.getByTestId('expense-category-form-submit'))

    // `POST /api/admin/expense-categories` (api-contract.md §5.15) — the
    // single create/update (upsert) endpoint; `active` is omitted when the
    // flag is unset (TR-01 omission convention — the server defaults it to
    // true).
    await waitFor(() => expect(mockedExpenseCategoriesCreate).toHaveBeenCalledTimes(1))
    expect(mockedExpenseCategoriesCreate).toHaveBeenCalledWith(createPayload)
    expect(screen.queryByTestId('expense-categories-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('rejects a duplicate category code on create', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Expense Categories' })
    await openCreatePanel(user, 'MEALS')
    await user.click(screen.getByTestId('expense-category-form-submit'))

    expect(await screen.findByText(EXPENSE_CATEGORY_MESSAGES.codeUnique)).toBeInTheDocument()
    expect(mockedExpenseCategoriesCreate).not.toHaveBeenCalled()
  })

  it('edits a category: prefills from the row and submits the edited payload via the same upsert create', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Expense Categories' })
    await user.click(screen.getByTestId(`expense-categories-page-edit-${EDIT_TARGET.id}`))

    // Edit mode pre-fills from the clicked row (J-09 mount-only defaults) and
    // relabels the submit button.
    expect(screen.getByTestId('expense-categories-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(codeInput().value).toBe(EDIT_TARGET.code)
    expect(labelInput().value).toBe(EDIT_TARGET.label)
    expect(activeSelect().value).toBe('false')

    await user.clear(labelInput())
    await user.type(labelInput(), EDIT_LABEL)
    await user.click(screen.getByTestId('expense-category-form-submit'))

    // Same single `POST /api/admin/expense-categories` upsert as create —
    // `adminService.expenseCategories` exposes no separate update method
    // (api-contract.md §5.15).
    await waitFor(() => expect(mockedExpenseCategoriesCreate).toHaveBeenCalledTimes(1))
    expect(mockedExpenseCategoriesCreate).toHaveBeenCalledWith(editPayload)
    expect(screen.queryByTestId('expense-categories-page-form-panel')).not.toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })
})
