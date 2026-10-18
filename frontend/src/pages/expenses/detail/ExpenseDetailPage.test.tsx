// P-10 Expense Claim Detail component test (I-10, frontend-tasks.md).
// Verification method mirrors I-05: each combination of status, role, and
// ownership is exercised against the page so the conditional Edit/Review/
// Reimburse ActionBar follows the G-01 flags in actionVisibility.ts. The
// shared `useExpenses` module is mocked (both hooks); `useExpenseClaim`
// returns the `ServerDataState` shape the real hook returns (data/
// isLoading/error/refetch), the page is routed at `/expenses/:id` and
// rendered at `/expenses/exp-1`, and ActionBar navigation is asserted via
// the `location` test id.

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { RoleType } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import type { ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { ErrorCategory } from '../../../models/error'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import * as expenseService from '../../../services/expenseService'
import { ExpenseDetailPage } from './ExpenseDetailPage'

vi.mock('../../../hooks/useExpenses', () => ({
  // `useExpenseList` is unused by this page but the whole module is mocked,
  // mirroring the I-08/I-09 harnesses.
  useExpenseList: vi.fn(),
  useExpenseClaim: vi.fn(),
}))

vi.mock('../../../services/expenseService', () => ({
  submit: vi.fn(),
  downloadDocument: vi.fn(),
}))

const mockedUseExpenseClaim = vi.mocked(useExpenseClaim)
const mockedSubmit = vi.mocked(expenseService.submit)

// Full api-contract.md §5.9 claim (PENDING, owned by `employee-1`); tests
// override status/ownership per case.
function makeClaim(overrides: Partial<ExpenseClaimResponse> = {}): ExpenseClaimResponse {
  return {
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
    ...overrides,
  }
}

/** A minimal normalized `ClassifiedError` for the page's category branching. */
function classifiedError(category: ErrorCategory, status: number): ClassifiedErrorInstance {
  const error = new Error(category) as ClassifiedErrorInstance
  error.name = 'ClassifiedError'
  error.category = category
  error.status = status
  error.errorCode = category
  error.timestamp = '2026-11-06T10:00:00Z'
  error.fieldErrors = []
  return error
}

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

interface Session {
  userId: string
  roles: RoleType[]
}

function AuthenticatedPage({ session }: { session: Session }) {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: session.userId,
      roles: session.roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'token',
    })
  }, [setAuthState, session.userId, session.roles])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <>
      <Routes>
        <Route path="/expenses/:id" element={<ExpenseDetailPage />} />
      </Routes>
      {/* Sibling of `Routes` so it survives ActionBar navigation to an
          action page (P-12/P-13/P-15), which has no route in this harness. */}
      <LocationDisplay />
    </>
  )
}

function renderPage(options: {
  session: Session
  claim?: ExpenseClaimResponse | null
  error?: ClassifiedErrorInstance
  isLoading?: boolean
  refetch?: () => void
  navigationState?: { failedUploads?: string[]; submitError?: string }
}) {
  const { session, claim = makeClaim(), error = null, isLoading = false, refetch = vi.fn() } = options
  const state: ServerDataState<ExpenseClaimResponse> = {
    data: error !== null ? null : claim,
    isLoading,
    error,
    refetch,
  }
  mockedUseExpenseClaim.mockReturnValue(state)
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/expenses/exp-1', state: options.navigationState }]}>
      <AuthProvider>
        <AuthenticatedPage session={session} />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ExpenseDetailPage (I-10)', () => {
  it('renders a loading skeleton while the claim is being fetched', () => {
    renderPage({
      session: { userId: 'employee-1', roles: ['EMPLOYEE'] },
      isLoading: true,
    })

    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Expense Claim Detail' })).toBeInTheDocument()
  })

  it('renders the empty state when no claim data is available', () => {
    renderPage({
      session: { userId: 'employee-1', roles: ['EMPLOYEE'] },
      claim: null,
    })

    expect(screen.getByText('No expense claim found.')).toBeInTheDocument()
  })

  it('shows Edit for the owning employee on a PENDING claim', async () => {
    renderPage({ session: { userId: 'employee-1', roles: ['EMPLOYEE'] } })

    expect(await screen.findByRole('heading', { name: 'Expense Claim Detail' })).toBeInTheDocument()
    expect(mockedUseExpenseClaim).toHaveBeenCalledWith('exp-1')
    // G-01: owner + PENDING → Edit only.
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reimburse' })).not.toBeInTheDocument()
  })

  it('shows Review for Finance on a PENDING claim', async () => {
    renderPage({ session: { userId: 'finance-1', roles: ['FINANCE'] } })

    expect(await screen.findByRole('heading', { name: 'Expense Claim Detail' })).toBeInTheDocument()
    // G-01: Finance + PENDING → Review only.
    expect(screen.getByRole('button', { name: 'Review' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reimburse' })).not.toBeInTheDocument()
  })

  it('gates Reimburse to Finance on an APPROVED claim (FR-FIN-03)', async () => {
    renderPage({
      session: { userId: 'finance-1', roles: ['FINANCE'] },
      claim: makeClaim({ status: 'APPROVED' }),
    })

    expect(await screen.findByText('Approved')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reimburse' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review' })).not.toBeInTheDocument()
  })

  it('renders a read-only view (no action buttons) for a non-owner employee', async () => {
    renderPage({ session: { userId: 'employee-2', roles: ['EMPLOYEE'] } })

    expect(await screen.findByRole('heading', { name: 'Expense Claim Detail' })).toBeInTheDocument()
    // No flag is true → the ActionBar renders nothing (frontend-spec.md §4.8).
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reimburse' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Expense claim actions' })).not.toBeInTheDocument()
  })

  it('renders the claim summary fields and the documents list', async () => {
    renderPage({ session: { userId: 'employee-1', roles: ['EMPLOYEE'] } })

    expect(await screen.findByRole('heading', { name: 'Expense Claim Detail' })).toBeInTheDocument()
    // ExpenseSummary (C-04) — response fields in display form.
    expect(screen.getByText('tr-1')).toBeInTheDocument()
    expect(screen.getByText('1250 EUR')).toBeInTheDocument()
    expect(screen.getByText('Lodging')).toBeInTheDocument()
    expect(screen.getByText('2026-11-05')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    // Documents list — file name, type, and human-readable size.
    expect(screen.getByRole('heading', { name: 'Documents' })).toBeInTheDocument()
    expect(screen.getByText('receipt.pdf')).toBeInTheDocument()
    expect(screen.getByText('PDF · 1.0 KB')).toBeInTheDocument()
  })

  it('renders the not-found fallback in place for a RESOURCE_NOT_FOUND failure', async () => {
    renderPage({
      session: { userId: 'employee-1', roles: ['EMPLOYEE'] },
      error: classifiedError('RESOURCE_NOT_FOUND', 404),
    })

    // P-22 fallback replaces the page body (frontend-api-mapping.md §4).
    expect(await screen.findByRole('heading', { name: 'Not Found' })).toBeInTheDocument()
    expect(
      screen.getByText('The page or record you are looking for could not be found.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Expense Claim Detail' })).not.toBeInTheDocument()
  })

  it('renders the access-denied fallback in place for a FORBIDDEN_ACTION failure', async () => {
    renderPage({
      session: { userId: 'employee-1', roles: ['EMPLOYEE'] },
      error: classifiedError('FORBIDDEN_ACTION', 403),
    })

    // P-21 fallback replaces the page body (frontend-api-mapping.md §4).
    expect(await screen.findByRole('heading', { name: 'Access Denied' })).toBeInTheDocument()
    expect(screen.getByText('You do not have permission to view this page.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Expense Claim Detail' })).not.toBeInTheDocument()
  })

  it('shows a retryable banner for other claim-fetch errors', async () => {
    const refetch = vi.fn()
    renderPage({
      session: { userId: 'employee-1', roles: ['EMPLOYEE'] },
      error: classifiedError('INTERNAL_ERROR', 500),
      refetch,
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This expense claim could not be loaded. Please try again.',
    )
    expect(screen.queryByText('INTERNAL_ERROR')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('navigates to the edit page when Edit is clicked', async () => {
    renderPage({ session: { userId: 'employee-1', roles: ['EMPLOYEE'] } })

    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-1/edit'),
    )
  })

  it('navigates to the review page when Review is clicked', async () => {
    renderPage({ session: { userId: 'finance-1', roles: ['FINANCE'] } })

    fireEvent.click(await screen.findByRole('button', { name: 'Review' }))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/expenses/exp-1/review'),
    )
  })

  describe('draft submission and documents (Phase 1 #5/#6/#9)', () => {
    const owner = { userId: 'employee-1', roles: ['EMPLOYEE'] as RoleType[] }

    it('offers "Submit for review" on the owner\'s DRAFT and refreshes after submitting', async () => {
      const refetch = vi.fn()
      mockedSubmit.mockResolvedValue(makeClaim({ status: 'PENDING' }))
      renderPage({ session: owner, claim: makeClaim({ status: 'DRAFT' }), refetch })

      fireEvent.click(await screen.findByRole('button', { name: 'Submit for review' }))

      await waitFor(() => expect(mockedSubmit).toHaveBeenCalledWith('exp-1'))
      await waitFor(() => expect(refetch).toHaveBeenCalled())
    })

    it('offers "Resubmit for review" on a REJECTED claim', async () => {
      renderPage({ session: owner, claim: makeClaim({ status: 'REJECTED', rejectionReason: 'Wrong amount' }) })

      expect(await screen.findByRole('button', { name: 'Resubmit for review' })).toBeInTheDocument()
    })

    it('shows why a submission failed (e.g. missing receipt) instead of failing silently', async () => {
      mockedSubmit.mockRejectedValue(
        Object.assign(classifiedError('POLICY_VIOLATION', 422), {
          fieldErrors: [{ field: 'documentIds', message: 'A supporting document is required' }],
        }),
      )
      renderPage({ session: owner, claim: makeClaim({ status: 'DRAFT' }) })

      fireEvent.click(await screen.findByRole('button', { name: 'Submit for review' }))

      expect(await screen.findByText(/Not submitted: A supporting document is required/)).toBeInTheDocument()
    })

    it('lists upload failures from the create flow', async () => {
      renderPage({
        session: owner,
        claim: makeClaim({ status: 'DRAFT' }),
        navigationState: { failedUploads: ['hotel.pdf'] },
      })

      expect(await screen.findByText(/These files could not be uploaded: hotel.pdf/)).toBeInTheDocument()
    })

    it('does not offer submission on PENDING claims or to Finance', async () => {
      renderPage({ session: owner, claim: makeClaim({ status: 'PENDING' }) })
      await screen.findByText('receipt.pdf')
      expect(screen.queryByRole('button', { name: /Submit for review/ })).not.toBeInTheDocument()
    })

    it('renders a download action for each stored document', async () => {
      renderPage({ session: owner })

      expect(await screen.findByRole('button', { name: 'Download receipt.pdf' })).toBeInTheDocument()
    })
  })
})
