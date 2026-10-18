import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import type { ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { PaginatedResult } from '../../../models/pagination'
import { useExpenseList } from '../../../hooks/useExpenses'
import { ReviewQueuePage } from './ReviewQueuePage'

vi.mock('../../../hooks/useExpenses', () => ({
  useExpenseList: vi.fn(),
}))

const mockedUseExpenseList = vi.mocked(useExpenseList)

const claims: ExpenseClaimResponse[] = [
  {
    id: 'exp-1',
    employeeId: 'employee-1',
    travelRequestId: 'tr-1',
    amount: 1250,
    currency: 'EUR',
    category: 'Lodging',
    expenseDate: '2026-11-05',
    status: 'PENDING',
    duplicateFlag: true,
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
    createdAt: '2026-11-06T10:00:00Z',
    updatedAt: '2026-11-06T10:00:00Z',
  },
  {
    id: 'exp-2',
    employeeId: 'employee-2',
    travelRequestId: 'tr-2',
    amount: 340,
    currency: 'EUR',
    category: 'Meals',
    expenseDate: '2026-11-07',
    status: 'PENDING',
    duplicateFlag: false,
    rejectionReason: null,
    reimbursedAt: null,
    documents: [],
    createdAt: '2026-11-07T12:00:00Z',
    updatedAt: '2026-11-07T12:00:00Z',
  },
]

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

function AuthenticatedPage() {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'finance-1',
      roles: ['FINANCE'],
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
      <ReviewQueuePage />
      <LocationDisplay />
    </>
  )
}

function renderPage(
  path = '/expenses/review-queue',
  overrides: Partial<ServerDataState<PaginatedResult<ExpenseClaimResponse>>> = {},
) {
  const state: ServerDataState<PaginatedResult<ExpenseClaimResponse>> = {
    data: { content: claims, page: 0, size: 10, totalElements: 11, totalPages: 2 },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  }
  mockedUseExpenseList.mockReturnValue(state)
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AuthenticatedPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ReviewQueuePage (I-09)', () => {
  it('applies the default status=PENDING filter on load, without a caller scope', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Expense Review Queue' })).toBeInTheDocument()
    expect(screen.getByText('tr-1')).toBeInTheDocument()
    expect(screen.getByText('Lodging')).toBeInTheDocument()
    // Default filter (I-09 verification): the list hook is called with
    // status=PENDING and, unlike the P-08 travel queue, WITHOUT a caller-id
    // argument — Finance reviews claims from all employees (frontend-spec.md
    // §4.7, "Finance view unscoped (reviewable queue)").
    expect(mockedUseExpenseList).toHaveBeenCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ status: 'PENDING' }),
      }),
    )
    // The default is reflected in the URL query string on load (D-04).
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('status=PENDING'))
  })

  it('keeps an explicit status filter from the URL instead of the default', async () => {
    renderPage('/expenses/review-queue?status=APPROVED')

    expect(await screen.findByRole('heading', { name: 'Expense Review Queue' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('APPROVED')
    expect(mockedUseExpenseList).toHaveBeenCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ status: 'APPROVED' }),
      }),
    )
    expect(screen.getByTestId('location')).toHaveTextContent('status=APPROVED')
  })

  it.each([
    ['PENDING', 'Pending'],
    ['APPROVED', 'Approved'],
    ['REJECTED', 'Rejected'],
    ['REIMBURSED', 'Reimbursed'],
  ] as const)('allows Finance to filter the queue by %s status', async (status, label) => {
    renderPage()

    const statusFilter = await screen.findByRole('combobox', { name: 'Status' })
    fireEvent.change(statusFilter, { target: { value: status } })

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent(`status=${status}`)
    })
    expect(statusFilter).toHaveValue(status)
    expect(mockedUseExpenseList).toHaveBeenLastCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ status }),
      }),
    )
    expect(screen.getByRole('option', { name: label })).toBeInTheDocument()
  })

  it('updates the duplicateFlag filter and URL when the Duplicates select changes', async () => {
    renderPage()

    // FR-FIN-02: the tri-state Duplicates filter is present (Q19).
    const duplicateFilter = screen.getByRole('combobox', { name: 'Duplicates' })
    expect(duplicateFilter).toBeInTheDocument()

    fireEvent.change(duplicateFilter, { target: { value: 'true' } })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('duplicateFlag=true'))
    expect(mockedUseExpenseList).toHaveBeenCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ duplicateFlag: 'true' }),
      }),
    )

    fireEvent.change(duplicateFilter, { target: { value: 'false' } })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('duplicateFlag=false'))
    expect(mockedUseExpenseList).toHaveBeenCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ duplicateFlag: 'false' }),
      }),
    )
  })

  it('flags duplicate-flagged claims with the shared duplicate indicator', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Expense Review Queue' })).toBeInTheDocument()
    // frontend-spec.md §4.7 / frontend-clarification.md Q8: only exp-1 has
    // duplicateFlag=true, so exactly one shared DuplicateIndicator renders.
    expect(screen.getByText('Possible duplicate')).toBeInTheDocument()
  })

  it('navigates to the selected expense detail on row click', async () => {
    renderPage()

    fireEvent.click(screen.getByText('Meals'))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-2'))
  })

  it('shows the table loading state', () => {
    renderPage('/expenses/review-queue', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('shows the empty state when no claims await review', () => {
    renderPage('/expenses/review-queue', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No expense claims match the current filters.')).toBeInTheDocument()
  })

  it('shows a retryable error when loading the review queue fails', async () => {
    const refetch = vi.fn()
    renderPage('/expenses/review-queue', {
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
      'Expense claims could not be loaded. Please try again.',
    )
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })
})