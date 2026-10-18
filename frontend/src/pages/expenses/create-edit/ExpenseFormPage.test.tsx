// I-11 Expense Form page component tests (frontend-tasks.md; screens
// P-11/P-12 per frontend-spec.md §4.9). Verification method per I-11:
// picker filtering and post-submit navigation are covered with the service
// mocked. The real `expenseService` module is replaced (create/update/
// uploadDocument mocks), and the lookup hooks the page and its children
// consume (`useExpenseClaim`, `useTravelRequest`, `useTravelRequestList`,
// plus the form-owned `useExpenseCategories`/`usePolicyConfigs`) are mocked
// with loaded `ServerDataState` fixtures so no real HTTP paths run. Auth
// goes through the real AuthProvider (setAuthState useEffect pattern, same
// as TravelRequestPicker.test.tsx), the page is routed at `/expenses/new`
// and `/expenses/:id/edit`, and post-submit navigation is asserted via the
// `location` test id (same harness as ExpenseDetailPage.test.tsx).

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import { useTravelRequest, useTravelRequestList } from '../../../hooks/useTravelRequests'
import type { ExpenseClaimCreateRequest, ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { ClassifiedError } from '../../../models/error'
import type { PaginatedResult } from '../../../models/pagination'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import * as expenseService from '../../../services/expenseService'
import { ExpenseFormPage } from './ExpenseFormPage'

/**
 * Static loaded fixtures for the form-owned admin-resource lookups
 * (frontend-api-mapping.md §1.3 P-11/P-12): the ACTIVE category list
 * (MEALS/HOTEL) and each category's policy config (MEALS receipt threshold
 * 100, HOTEL 250) — same shape as ExpenseClaimForm.test.tsx.
 */
const adminResourceFixtures = vi.hoisted(() => ({
  expenseCategories: {
    data: {
      content: [
        { id: 'cat-1', code: 'MEALS', label: 'Meals', active: true },
        { id: 'cat-2', code: 'HOTEL', label: 'Hotel', active: true },
      ],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch: () => undefined,
  },
  policyConfigs: {
    data: {
      content: [
        {
          id: 'policy-1',
          categoryCode: 'MEALS',
          receiptThresholdAmount: 100,
          currency: 'USD',
          active: true,
        },
        {
          id: 'policy-2',
          categoryCode: 'HOTEL',
          receiptThresholdAmount: 250,
          currency: 'USD',
          active: true,
        },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch: () => undefined,
  },
}))

vi.mock('../../../hooks/useAdminResources', () => ({
  useExpenseCategories: () => adminResourceFixtures.expenseCategories,
  usePolicyConfigs: () => adminResourceFixtures.policyConfigs,
}))

vi.mock('../../../hooks/useExpenses', () => ({
  // `useExpenseList` is unused by this page but the whole module is mocked,
  // mirroring the I-10 harness.
  useExpenseList: vi.fn(),
  useExpenseClaim: vi.fn(),
}))

vi.mock('../../../hooks/useTravelRequests', () => ({
  useTravelRequestList: vi.fn(),
  useTravelRequest: vi.fn(),
}))

vi.mock('../../../services/expenseService', () => ({
  list: vi.fn(),
  getById: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  uploadDocument: vi.fn(),
  submit: vi.fn(),
  review: vi.fn(),
  reimburse: vi.fn(),
}))

const mockedCreate = vi.mocked(expenseService.create)
const mockedUpdate = vi.mocked(expenseService.update)
const mockedUploadDocument = vi.mocked(expenseService.uploadDocument)
const mockedSubmit = vi.mocked(expenseService.submit)
const mockedUseExpenseClaim = vi.mocked(useExpenseClaim)
const mockedUseTravelRequest = vi.mocked(useTravelRequest)
const mockedUseTravelRequestList = vi.mocked(useTravelRequestList)

/** The caller's own APPROVED travel requests (VAL-09 / BR-02). */
const approvedRequests: TravelRequestResponse[] = [
  {
    id: 'tr-approved-1',
    employeeId: 'employee-1',
    destination: 'Bengaluru',
    startDate: '2026-11-03',
    endDate: '2026-11-07',
    justification: 'Client site visit',
    status: 'APPROVED',
    approverId: 'manager-1',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-05T10:30:00Z',
  },
  {
    id: 'tr-approved-2',
    employeeId: 'employee-1',
    destination: 'Mumbai',
    startDate: '2026-12-10',
    endDate: '2026-12-12',
    justification: 'Regional offsite',
    status: 'APPROVED',
    approverId: 'manager-2',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-10-20T09:00:00Z',
    updatedAt: '2026-10-24T10:30:00Z',
  },
]

/** The claim `POST /api/expenses` resolves with in the create tests. */
const createdClaim: ExpenseClaimResponse = {
  id: 'exp-new-1',
  employeeId: 'employee-1',
  travelRequestId: 'tr-approved-1',
  amount: 150,
  currency: 'USD',
  category: 'MEALS',
  expenseDate: '2026-11-05',
  status: 'PENDING',
  duplicateFlag: false,
  rejectionReason: null,
  reimbursedAt: null,
  documents: [],
  createdAt: '2026-11-05T12:00:00Z',
  updatedAt: '2026-11-05T12:00:00Z',
}

/** The P-12 claim under edit (PENDING, owned by `employee-1`). */
function makeEditClaim(overrides: Partial<ExpenseClaimResponse> = {}): ExpenseClaimResponse {
  return {
    id: 'exp-1',
    employeeId: 'employee-1',
    travelRequestId: 'tr-approved-1',
    amount: 150,
    currency: 'USD',
    category: 'MEALS',
    expenseDate: '2026-11-05',
    status: 'PENDING',
    duplicateFlag: false,
    rejectionReason: null,
    reimbursedAt: null,
    documents: [
      {
        id: 'doc-1',
        fileName: 'receipt.pdf',
        fileType: 'PDF',
        fileSizeBytes: 1024,
        storageReference: 'blob://expenses/exp-1/receipt.pdf',
        uploadStatus: 'CONFIRMED',
      },
    ],
    createdAt: '2026-11-05T12:00:00Z',
    updatedAt: '2026-11-05T12:00:00Z',
    ...overrides,
  }
}

/** A minimal normalized `ClassifiedError` for the page's error mapping. */
function classifiedError(
  category: ClassifiedError['category'],
  status: number,
  fieldErrors: ClassifiedError['fieldErrors'] = [],
): ClassifiedError & Error {
  const error = new Error('classified') as ClassifiedError & Error
  error.name = 'ClassifiedError'
  error.category = category
  error.status = status
  error.errorCode = category
  error.timestamp = '2026-11-05T12:00:00Z'
  error.fieldErrors = fieldErrors
  return error
}

type PickerDataState = ServerDataState<PaginatedResult<TravelRequestResponse>>

/** Loaded picker fixture (same convention as TravelRequestPicker.test.tsx). */
function mockPickerRequests(
  content: TravelRequestResponse[],
  overrides?: Partial<PickerDataState>,
) {
  mockedUseTravelRequestList.mockReturnValue({
    data: {
      content,
      page: 0,
      size: 50,
      totalElements: content.length,
      totalPages: content.length === 0 ? 0 : 1,
    },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  })
}

function mockClaimState(
  claim: ExpenseClaimResponse,
  overrides: Partial<ServerDataState<ExpenseClaimResponse>> = {},
) {
  mockedUseExpenseClaim.mockReturnValue({
    data: claim,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  })
}

function mockTravelRequestState(request: TravelRequestResponse) {
  mockedUseTravelRequest.mockReturnValue({
    data: request,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  })
}

/** VAL-11 fixture helper — mirrors ExpenseClaimForm.test.tsx `makeFile`. */
function makeFile(name: string, type: string, sizeBytes: number): File {
  const file = new File(['x'.repeat(Math.min(sizeBytes, 10))], name, { type })
  Object.defineProperty(file, 'size', { value: sizeBytes })
  return file
}

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

function AuthenticatedPage() {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'employee-1',
      roles: ['EMPLOYEE'],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'token',
    })
  }, [setAuthState])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <>
      <Routes>
        <Route path="/expenses/new" element={<ExpenseFormPage />} />
        <Route path="/expenses/:id/edit" element={<ExpenseFormPage />} />
      </Routes>
      {/* Sibling of `Routes` so it survives post-submit navigation to the
          claim detail (P-10), which has no route in this harness. */}
      <LocationDisplay />
    </>
  )
}

function renderPage(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <AuthenticatedPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

/** Enters the plain form fields (date via `fireEvent.change` — jsdom). */
async function enterClaimFields(
  user: ReturnType<typeof userEvent.setup>,
  fields: { amount: string; category: string; currency?: string; expenseDate?: string },
) {
  await user.type(screen.getByTestId('expense-claim-form-amount'), fields.amount)
  await user.type(screen.getByTestId('expense-claim-form-currency'), fields.currency ?? 'USD')
  await user.selectOptions(screen.getByTestId('expense-claim-form-category'), fields.category)
  fireEvent.change(screen.getByTestId('expense-claim-form-expense-date'), {
    target: { value: fields.expenseDate ?? '2026-11-05' },
  })
}

/** Selects the first picker option and fills the remaining create fields. */
async function selectFirstRequestAndEnterFields(
  user: ReturnType<typeof userEvent.setup>,
  fields: { amount: string; category: string; currency?: string; expenseDate?: string },
) {
  await user.click(await screen.findByTestId('travel-request-picker-option-tr-approved-1'))
  await enterClaimFields(user, fields)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ExpenseFormPage create (P-11)', () => {
  it('shows a loading state while approved travel requests are being fetched', () => {
    mockPickerRequests([], { data: null, isLoading: true })
    renderPage('/expenses/new')

    expect(screen.getByTestId('travel-request-picker-loading')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'New Expense Claim' })).toBeInTheDocument()
  })

  it('shows an empty state when the employee has no approved travel requests', () => {
    mockPickerRequests([])
    renderPage('/expenses/new')

    expect(screen.getByText('No approved travel requests.')).toBeInTheDocument()
  })

  it('shows a retryable message when approved travel requests cannot be loaded', async () => {
    const refetch = vi.fn()
    mockPickerRequests([], {
      data: null,
      error: classifiedError('INTERNAL_ERROR', 500),
      refetch,
    })
    renderPage('/expenses/new')

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Your approved travel requests could not be loaded. Please try again.',
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('renders "New Expense Claim" and lists only the caller\'s own approved travel requests', async () => {
    mockPickerRequests(approvedRequests)
    renderPage('/expenses/new')

    expect((await screen.findByTestId('expense-form-page-title')).textContent).toBe(
      'New Expense Claim',
    )
    expect(screen.getByTestId('travel-request-picker-option-tr-approved-1')).toBeInTheDocument()
    expect(screen.getByTestId('travel-request-picker-option-tr-approved-2')).toBeInTheDocument()
    expect(screen.getByText('Bengaluru')).toBeInTheDocument()
    expect(screen.getByText('Mumbai')).toBeInTheDocument()
    // VAL-09/BR-02: server-side status=APPROVED filter scoped to the session
    // user — the picker lists only the caller's own approved requests.
    expect(mockedUseTravelRequestList).toHaveBeenCalledWith(
      expect.objectContaining({ filters: { status: 'APPROVED' } }),
      'MINE',
    )
  })

  it('creates the claim, uploads the buffered document, and navigates to the new claim', async () => {
    const user = userEvent.setup()
    mockPickerRequests(approvedRequests)
    mockedCreate.mockResolvedValue(createdClaim)
    mockedUploadDocument.mockResolvedValue({
      id: 'doc-new-1',
      fileName: 'receipt.pdf',
      fileType: 'PDF',
      fileSizeBytes: 1024,
      storageReference: 'blob://expenses/exp-new-1/receipt.pdf',
      uploadStatus: 'CONFIRMED',
    })
    renderPage('/expenses/new')

    await selectFirstRequestAndEnterFields(user, { amount: '150', category: 'MEALS' })

    // 150 > MEALS threshold 100 → the upload is required (VAL-10); the page
    // buffers the file and reports it as CONFIRMED (Q10 amendment).
    const file = makeFile('receipt.pdf', 'application/pdf', 1024)
    await user.upload(screen.getByLabelText('Documents (required)'), file)
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-new-1'),
    )
    // Create-then-upload (frontend-clarification.md Q10): the create payload
    // never carries the temp documentIds.
    const createPayload = mockedCreate.mock.calls[0][0] as ExpenseClaimCreateRequest
    expect(mockedCreate).toHaveBeenCalledTimes(1)
    expect(createPayload).toEqual(
      expect.objectContaining({
        travelRequestId: 'tr-approved-1',
        amount: 150,
        currency: 'USD',
        category: 'MEALS',
        expenseDate: '2026-11-05',
      }),
    )
    expect(createPayload.documentIds).toBeUndefined()
    // The buffered file is uploaded against the created claim id.
    expect(mockedUploadDocument).toHaveBeenCalledTimes(1)
    expect(mockedUploadDocument).toHaveBeenCalledWith('exp-new-1', {
      file,
      fileName: 'receipt.pdf',
    })
    // The draft is sent to Finance only after the receipt is stored.
    expect(mockedSubmit).toHaveBeenCalledWith('exp-new-1')
    expect(mockedUploadDocument.mock.invocationCallOrder[0]).toBeLessThan(
      mockedSubmit.mock.invocationCallOrder[0],
    )
  })

  it('keeps the claim as a draft and does not submit when a receipt upload fails', async () => {
    const user = userEvent.setup()
    mockPickerRequests(approvedRequests)
    mockedCreate.mockResolvedValue(createdClaim)
    mockedUploadDocument.mockRejectedValue(
      Object.assign(new Error('storage down'), { category: 'INTERNAL_ERROR', fieldErrors: [] }),
    )
    renderPage('/expenses/new')

    await selectFirstRequestAndEnterFields(user, { amount: '150', category: 'MEALS' })
    await user.upload(
      screen.getByLabelText('Documents (required)'),
      makeFile('receipt.pdf', 'application/pdf', 1024),
    )
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())
    await user.click(screen.getByTestId('expense-claim-form-submit'))

    // The claim exists (as a DRAFT) so the user lands on it to retry, but it is
    // never sent to Finance without the intended receipt.
    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-new-1'),
    )
    expect(mockedUploadDocument).toHaveBeenCalledTimes(1)
    expect(mockedSubmit).not.toHaveBeenCalled()
  })

  it('creates below the threshold without a document and navigates', async () => {
    const user = userEvent.setup()
    mockPickerRequests(approvedRequests)
    mockedCreate.mockResolvedValue(createdClaim)
    renderPage('/expenses/new')

    await selectFirstRequestAndEnterFields(user, { amount: '80', category: 'MEALS' })

    // 80 ≤ MEALS threshold 100 → the document section stays optional.
    expect(
      screen.queryByTestId('expense-claim-form-documents-required-hint'),
    ).not.toBeInTheDocument()

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-new-1'),
    )
    expect(mockedCreate).toHaveBeenCalledTimes(1)
    expect(mockedUploadDocument).not.toHaveBeenCalled()
  })

  it('shows the server amount field error inline when create is rejected with fieldErrors', async () => {
    const user = userEvent.setup()
    mockPickerRequests(approvedRequests)
    mockedCreate.mockRejectedValue(
      classifiedError('VALIDATION_ERROR', 400, [
        { field: 'amount', message: 'The amount exceeds the policy limit for MEALS.' },
      ]),
    )
    renderPage('/expenses/new')

    await selectFirstRequestAndEnterFields(user, { amount: '150', category: 'MEALS' })

    // The client-side VAL-10 gate needs a CONFIRMED document to let the
    // submit through — attach one via the page's buffered upload path.
    await user.upload(
      screen.getByLabelText('Documents (required)'),
      makeFile('receipt.pdf', 'application/pdf', 1024),
    )
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() =>
      expect(
        screen.getByText('The amount exceeds the policy limit for MEALS.'),
      ).toBeInTheDocument(),
    )
    // The form stays open: no navigation, no post-create upload.
    expect(screen.getByTestId('location')).toHaveTextContent('/expenses/new')
    expect(mockedUploadDocument).not.toHaveBeenCalled()
  })
})

describe('ExpenseFormPage edit (P-12)', () => {
  it('renders a loading state while the claim is being fetched', () => {
    mockClaimState(makeEditClaim(), { data: null, isLoading: true })
    renderPage('/expenses/exp-1/edit')

    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.getByTestId('expense-form-page-title')).toHaveTextContent('Edit Expense Claim')
  })

  it('renders the empty state when no claim is returned', () => {
    mockClaimState(makeEditClaim(), { data: null })
    renderPage('/expenses/exp-1/edit')

    expect(screen.getByText('No expense claim found.')).toBeInTheDocument()
  })

  it('renders the not-found fallback for a RESOURCE_NOT_FOUND fetch error', () => {
    mockClaimState(makeEditClaim(), {
      data: null,
      error: classifiedError('RESOURCE_NOT_FOUND', 404),
    })
    renderPage('/expenses/exp-1/edit')

    expect(screen.getByRole('heading', { name: 'Not Found' })).toBeInTheDocument()
  })

  it('renders the access-denied fallback for a FORBIDDEN_ACTION fetch error', () => {
    mockClaimState(makeEditClaim(), {
      data: null,
      error: classifiedError('FORBIDDEN_ACTION', 403),
    })
    renderPage('/expenses/exp-1/edit')

    expect(screen.getByRole('heading', { name: 'Access Denied' })).toBeInTheDocument()
  })

  it('shows a retryable banner for other claim-fetch errors', async () => {
    const refetch = vi.fn()
    mockClaimState(makeEditClaim(), {
      data: null,
      error: classifiedError('INTERNAL_ERROR', 500),
      refetch,
    })
    renderPage('/expenses/exp-1/edit')

    expect(screen.getByRole('alert')).toHaveTextContent(
      'This expense claim could not be loaded. Please try again.',
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('prefills the form from the fetched claim, updates, and navigates to the detail', async () => {
    const user = userEvent.setup()
    mockClaimState(makeEditClaim())
    mockTravelRequestState(approvedRequests[0])
    mockedUpdate.mockResolvedValue(makeEditClaim())
    renderPage('/expenses/exp-1/edit')

    expect((await screen.findByTestId('expense-form-page-title')).textContent).toBe(
      'Edit Expense Claim',
    )
    expect(mockedUseExpenseClaim).toHaveBeenCalledWith('exp-1')
    // Edit mode pre-fills existing values (frontend-spec.md §4.9).
    expect(screen.getByTestId('expense-claim-form-travel-request')).toHaveValue('tr-approved-1')
    expect(screen.getByTestId('expense-claim-form-amount')).toHaveValue(150)
    expect(screen.getByTestId('expense-claim-form-currency')).toHaveValue('USD')
    expect(screen.getByTestId('expense-claim-form-category')).toHaveValue('MEALS')
    expect(screen.getByTestId('expense-claim-form-expense-date')).toHaveValue('2026-11-05')

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-1'))
    expect(mockedUpdate).toHaveBeenCalledTimes(1)
    expect(mockedUpdate.mock.calls[0][0]).toBe('exp-1')
    const updatePayload = mockedUpdate.mock.calls[0][1] as ExpenseClaimCreateRequest
    expect(updatePayload).toEqual(
      expect.objectContaining({
        travelRequestId: 'tr-approved-1',
        amount: 150,
        currency: 'USD',
        category: 'MEALS',
        expenseDate: '2026-11-05',
      }),
    )
    // The claim's attached documents seed `documentIds` (api-contract.md §5.8).
    expect(updatePayload.documentIds).toEqual(['doc-1'])
  })

  it('shows a 422 documentIds field error inline when the update is rejected', async () => {
    const user = userEvent.setup()
    mockClaimState(makeEditClaim())
    mockTravelRequestState(approvedRequests[0])
    mockedUpdate.mockRejectedValue(
      classifiedError('POLICY_VIOLATION', 422, [
        {
          field: 'documentIds',
          message: 'A receipt is required for amounts above the MEALS threshold.',
        },
      ]),
    )
    renderPage('/expenses/exp-1/edit')

    await screen.findByTestId('expense-claim-form-submit')
    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() =>
      expect(
        screen.getByText('A receipt is required for amounts above the MEALS threshold.'),
      ).toBeInTheDocument(),
    )
    // The form stays open: no navigation to the claim detail.
    expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-1/edit')
  })
})
