/**
 * I-14 — PolicyConfigsPage component tests (P-18, frontend-spec.md §4.12;
 * frontend-tasks.md I-14). Verification method per I-14: list rendering and
 * create/edit form interaction with the service mocked. The real
 * `adminService` module is replaced (namespace shape of the real export —
 * The `policyConfigs` group exposes `list`/`create`/`update`), and the page's
 * data hook (`usePolicyConfigs`) is mocked with a loaded `ServerDataState`
 * fixture. Auth goes through the real AuthProvider (setAuthState useEffect
 * pattern, same as ReviewQueuePage.test.tsx) with an ADMIN session; the page
 * uses no navigation, but `useListQueryState` (useSearchParams) requires a
 * MemoryRouter.
 *
 * Creates use POST and edits use PUT with the edited policy id.
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useExpenseCategories, usePolicyConfigs } from '../../../hooks/useAdminResources'
import type {
  ExpenseCategoryResponse,
  PolicyConfigRequest,
  PolicyConfigResponse,
} from '../../../models/admin'
import type { PaginatedResult } from '../../../models/pagination'
import { adminService } from '../../../services/adminService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import type { ErrorCategory } from '../../../models/error'
import { POLICY_CONFIG_MESSAGES } from '../../../validation/adminSchemas'
import { PolicyConfigsPage } from './PolicyConfigsPage'

vi.mock('../../../hooks/useAdminResources', () => ({
  useExpenseCategories: vi.fn(),
  usePolicyConfigs: vi.fn(),
}))

vi.mock('../../../services/adminService', () => ({
  // Full namespace shape of the real module (frontend/src/services/
  // adminService.ts) — only the `policyConfigs` group is consumed by this
  // page.
  adminService: {
    users: { list: vi.fn(), create: vi.fn(), update: vi.fn() },
    mappings: { list: vi.fn(), create: vi.fn() },
    policyConfigs: { list: vi.fn(), create: vi.fn(), update: vi.fn() },
    expenseCategories: { list: vi.fn(), create: vi.fn() },
  },
}))

const mockedUsePolicyConfigs = vi.mocked(usePolicyConfigs)
const mockedUseExpenseCategories = vi.mocked(useExpenseCategories)
const mockedPolicyConfigsCreate = vi.mocked(adminService.policyConfigs.create)
const mockedPolicyConfigsUpdate = vi.mocked(adminService.policyConfigs.update)

// Shared literals (defined once, reused by every case).
const policyConfigs: PolicyConfigResponse[] = [
  {
    id: 'policy-1',
    categoryCode: 'MEALS',
    receiptThresholdAmount: 50,
    currency: 'USD',
    active: true,
  },
  {
    id: 'policy-2',
    categoryCode: 'TRAVEL',
    receiptThresholdAmount: 100,
    currency: 'USD',
    active: false,
  },
]

const expenseCategories: ExpenseCategoryResponse[] = [
  { id: 'category-meals', code: 'MEALS', label: 'Meals', active: true },
  { id: 'category-travel', code: 'TRAVEL', label: 'Travel', active: false },
  { id: 'category-training', code: 'TRAINING', label: 'Training', active: true },
]

const CREATE_CATEGORY_CODE = 'TRAINING'
const CREATE_THRESHOLD = 50
const CREATE_CURRENCY = 'USD'
const createPayload: PolicyConfigRequest = {
  categoryCode: CREATE_CATEGORY_CODE,
  receiptThresholdAmount: CREATE_THRESHOLD,
  currency: CREATE_CURRENCY,
}

const EDIT_TARGET = policyConfigs[1]
const EDIT_THRESHOLD = 150
const editPayload: PolicyConfigRequest = {
  categoryCode: EDIT_TARGET.categoryCode,
  receiptThresholdAmount: EDIT_THRESHOLD,
  currency: EDIT_TARGET.currency,
  active: EDIT_TARGET.active,
}

const CREATE_FIELD_ERROR_MESSAGE = 'Category code must reference an existing expense category.'
const createValidationError: ClassifiedErrorInstance = Object.assign(
  new Error(CREATE_FIELD_ERROR_MESSAGE),
  {
    category: 'VALIDATION_ERROR' as ErrorCategory,
    status: 400,
    errorCode: 'VALIDATION_FAILED',
    timestamp: '2026-01-15T10:00:00Z',
    fieldErrors: [{ field: 'categoryCode', message: CREATE_FIELD_ERROR_MESSAGE }],
  },
)

const categoryCodeInput = () => screen.getByLabelText(/^Category code/) as HTMLInputElement
const thresholdInput = () => screen.getByLabelText(/^Receipt threshold amount/) as HTMLInputElement
const currencyInput = () => screen.getByLabelText(/^Currency/) as HTMLInputElement
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

  return <PolicyConfigsPage />
}

function renderPage(
  path = '/admin/policy-configs',
  overrides: Partial<ServerDataState<PaginatedResult<PolicyConfigResponse>>> = {},
) {
  const refetch = vi.fn()
  mockedUseExpenseCategories.mockReturnValue({
    data: {
      content: expenseCategories,
      page: 0,
      size: 50,
      totalElements: expenseCategories.length,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  } satisfies ServerDataState<PaginatedResult<ExpenseCategoryResponse>>)
  mockedUsePolicyConfigs.mockReturnValue({
    data: {
      content: policyConfigs,
      page: 0,
      size: 20,
      totalElements: policyConfigs.length,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch,
    ...overrides,
  } satisfies ServerDataState<PaginatedResult<PolicyConfigResponse>>)
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
  categoryCode = CREATE_CATEGORY_CODE,
) {
  await user.click(screen.getByRole('button', { name: 'New policy' }))
  await user.type(categoryCodeInput(), categoryCode)
  await user.type(thresholdInput(), String(CREATE_THRESHOLD))
  await user.type(currencyInput(), CREATE_CURRENCY)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PolicyConfigsPage (I-14)', () => {
  it('renders the loading state while policy configs are being fetched', () => {
    renderPage('/admin/policy-configs', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('renders the fixture rows with category codes, thresholds, and active state', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Policy Configs' })).toBeInTheDocument()
    expect(screen.getByText('policy-1')).toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
    // The threshold cell renders as `<amount> <currency>` (e.g. '50 USD').
    expect(screen.getByText('50 USD')).toBeInTheDocument()
    expect(screen.getByText('Yes')).toBeInTheDocument()
    expect(screen.getByText('policy-2')).toBeInTheDocument()
    expect(screen.getByText('TRAVEL')).toBeInTheDocument()
    expect(screen.getByText('100 USD')).toBeInTheDocument()
    expect(screen.getByText('No')).toBeInTheDocument()
  })

  it('renders an empty state when no policy configs exist', () => {
    renderPage('/admin/policy-configs', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No policy configs found.')).toBeInTheDocument()
  })

  it('shows a retryable error when policy configs fail to load', async () => {
    const refetch = vi.fn()
    renderPage('/admin/policy-configs', {
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
      'Policy configs could not be loaded. Please try again.',
    )
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('opens the create form panel on "New policy" and closes it on Cancel', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Policy Configs' })
    expect(screen.queryByTestId('policy-configs-page-form-panel')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New policy' }))
    // Create mode: the form mounts empty with the create submit label (J-08).
    expect(screen.getByTestId('policy-configs-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'New policy', level: 2 })).toBeInTheDocument()
    expect(screen.queryByTestId('policy-configs-page-new-action')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create policy' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByTestId('policy-configs-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByTestId('policy-configs-page-new-action')).toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
  })

  it('creates a policy config: submits the payload once via the upsert create, hides the panel, and refetches', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Policy Configs' })
    await openCreatePanel(user)
    await user.click(screen.getByTestId('policy-config-form-submit'))

    // `POST /api/admin/policy-configs` (api-contract.md §5.14) — the single
    // create/update (upsert) endpoint; the `receiptThresholdAmount` arrives as
    // a number and `active` is omitted when the flag is unset (TR-01).
    await waitFor(() => expect(mockedPolicyConfigsCreate).toHaveBeenCalledTimes(1))
    expect(mockedPolicyConfigsCreate).toHaveBeenCalledWith(createPayload)
    expect(screen.queryByTestId('policy-configs-page-form-panel')).not.toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('rejects a category code that is not in the loaded category list', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { name: 'Policy Configs' })
    await openCreatePanel(user, 'UNKNOWN')
    await user.click(screen.getByTestId('policy-config-form-submit'))

    expect(await screen.findByText(POLICY_CONFIG_MESSAGES.categoryCodeUnknown)).toBeInTheDocument()
    expect(mockedPolicyConfigsCreate).not.toHaveBeenCalled()
  })

  it('edits a policy config: prefills the row and submits the edited payload by id', async () => {
    const user = userEvent.setup()
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Policy Configs' })
    await user.click(screen.getByTestId(`policy-configs-page-edit-${EDIT_TARGET.id}`))

    // Edit mode pre-fills from the clicked row (J-08 mount-only defaults) and
    // relabels the submit button.
    expect(screen.getByTestId('policy-configs-page-form-panel')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(categoryCodeInput().value).toBe(EDIT_TARGET.categoryCode)
    expect(thresholdInput().value).toBe(String(EDIT_TARGET.receiptThresholdAmount))
    expect(currencyInput().value).toBe(EDIT_TARGET.currency)
    expect(activeSelect().value).toBe('false')

    await user.clear(thresholdInput())
    await user.type(thresholdInput(), String(EDIT_THRESHOLD))
    await user.click(screen.getByTestId('policy-config-form-submit'))

    await waitFor(() => expect(mockedPolicyConfigsUpdate).toHaveBeenCalledTimes(1))
    expect(mockedPolicyConfigsUpdate).toHaveBeenCalledWith(EDIT_TARGET.id, editPayload)
    expect(mockedPolicyConfigsCreate).not.toHaveBeenCalled()
    expect(screen.queryByTestId('policy-configs-page-form-panel')).not.toBeInTheDocument()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('keeps the panel open with an inline field error when create is rejected with a field error', async () => {
    const user = userEvent.setup()
    mockedPolicyConfigsCreate.mockRejectedValueOnce(createValidationError)
    const { refetch } = renderPage()

    await screen.findByRole('heading', { name: 'Policy Configs' })
    await openCreatePanel(user)
    await user.click(screen.getByTestId('policy-config-form-submit'))

    await waitFor(() => expect(mockedPolicyConfigsCreate).toHaveBeenCalledTimes(1))
    // The ClassifiedError's fieldError maps onto the category code field
    // inline (frontend-api-mapping.md §3/§4) and the form stays open for
    // corrections.
    expect(await screen.findByText(CREATE_FIELD_ERROR_MESSAGE)).toBeInTheDocument()
    expect(screen.getByTestId('policy-configs-page-form-panel')).toBeInTheDocument()
    expect(refetch).not.toHaveBeenCalled()
  })
})
