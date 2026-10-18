import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { RoleType } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import type { ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { PaginatedResult } from '../../../models/pagination'
import { useExpenseList } from '../../../hooks/useExpenses'
import * as expenseService from '../../../services/expenseService'
import { downloadCsv } from '../../../utils/csvExport'
import { ExpenseListPage } from './ExpenseListPage'

vi.mock('../../../hooks/useExpenses', () => ({
  useExpenseList: vi.fn(),
}))

vi.mock('../../../services/expenseService', () => ({
  list: vi.fn(),
}))

vi.mock('../../../utils/csvExport', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../utils/csvExport')>()
  return { ...actual, downloadCsv: vi.fn() }
})

const mockedUseExpenseList = vi.mocked(useExpenseList)
const mockedServiceList = vi.mocked(expenseService.list)
const mockedDownloadCsv = vi.mocked(downloadCsv)

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
    createdAt: '2026-11-06T10:00:00Z',
    updatedAt: '2026-11-06T10:00:00Z',
  },
]

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

function AuthenticatedPage({ roles }: { roles: RoleType[] }) {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'employee-1',
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'token',
    })
  }, [roles, setAuthState])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <>
      <ExpenseListPage />
      <LocationDisplay />
    </>
  )
}

function renderPage(
  path = '/expenses',
  overrides: Partial<ServerDataState<PaginatedResult<ExpenseClaimResponse>>> = {},
  roles: RoleType[] = ['EMPLOYEE'],
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
        <AuthenticatedPage roles={roles} />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ExpenseListPage (I-08)', () => {
  it('renders the employee list and new-expense action', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'My Expense Claims' })).toBeInTheDocument()
    expect(screen.getByText('tr-1')).toBeInTheDocument()
    expect(screen.getByText('1250')).toBeInTheDocument()
    expect(screen.getByText('EUR')).toBeInTheDocument()
    expect(screen.getByText('Lodging')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'New Expense Claim' })).toHaveAttribute(
      'href',
      '/expenses/new',
    )
    expect(screen.queryByRole('button', { name: 'Export CSV' })).not.toBeInTheDocument()
    expect(mockedUseExpenseList).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
    )
  })

  it('shows the export action to admins', () => {
    renderPage('/expenses', {}, ['ADMIN'])

    expect(screen.getByRole('heading', { name: 'Expenses' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeInTheDocument()
    const actions = screen.getByRole('button', { name: 'Export CSV' }).parentElement
    expect(actions).toHaveClass('expense-list-page__actions')
    expect(screen.getByRole('link', { name: 'New Expense Claim' }).parentElement).toBe(actions)
  })

  it('exports every expense page for admins', async () => {
    mockedServiceList.mockResolvedValue({
      content: claims,
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderPage('/expenses', {}, ['ADMIN'])

    fireEvent.click(screen.getByRole('button', { name: 'Export CSV' }))

    await waitFor(() => expect(mockedDownloadCsv).toHaveBeenCalledOnce())
    expect(mockedServiceList).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: undefined,
      employeeId: undefined,
      duplicateFlag: undefined,
    })
    expect(mockedDownloadCsv).toHaveBeenCalledWith(
      expect.stringMatching(/^expenses-\d{4}-\d{2}-\d{2}\.csv$/),
      claims,
      expect.any(Array),
    )
  })

  it('updates the URL when pagination changes', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('page=1'))
  })

  it('navigates to the selected expense detail', async () => {
    renderPage()

    fireEvent.click(screen.getByText('Lodging'))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-1'))
  })

  it('shows the table loading state', () => {
    renderPage('/expenses', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('shows the empty state when there are no claims', () => {
    renderPage('/expenses', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No expense claims found.')).toBeInTheDocument()
  })

  it('shows a retryable error when loading claims fails', async () => {
    const refetch = vi.fn()
    renderPage('/expenses', {
      data: null,
      error: new Error('transport details') as ServerDataState<
        PaginatedResult<ExpenseClaimResponse>
      >['error'],
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
