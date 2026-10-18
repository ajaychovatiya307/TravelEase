import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import type { PaginatedResult } from '../../../models/pagination'
import { useTravelRequestList } from '../../../hooks/useTravelRequests'
import { ApprovalQueuePage } from './ApprovalQueuePage'

vi.mock('../../../hooks/useTravelRequests', () => ({
  useTravelRequestList: vi.fn(),
}))

const mockedUseTravelRequestList = vi.mocked(useTravelRequestList)

const requests: TravelRequestResponse[] = [
  {
    id: 'tr-1',
    employeeId: 'employee-1',
    destination: 'Bengaluru',
    startDate: '2026-11-03',
    endDate: '2026-11-07',
    justification: 'Team workshop',
    status: 'PENDING',
    approverId: 'manager-1',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
  },
]

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

function AuthenticatedPage() {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'manager-1',
      roles: ['MANAGER'],
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
      <ApprovalQueuePage />
      <LocationDisplay />
    </>
  )
}

function renderPage(
  path = '/approvals/travel-requests',
  overrides?: Partial<ServerDataState<PaginatedResult<TravelRequestResponse>>>,
) {
  const state: ServerDataState<PaginatedResult<TravelRequestResponse>> = {
    data: { content: requests, page: 0, size: 10, totalElements: 11, totalPages: 2 },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  }
  mockedUseTravelRequestList.mockReturnValue(state)
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AuthenticatedPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ApprovalQueuePage (I-04)', () => {
  it('applies the default status=PENDING filter on load, scoped to the authenticated approver', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Approval Queue' })).toBeInTheDocument()
    expect(screen.getByText('Bengaluru')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    // Default filter + approver scoping (I-04 verification): the list hook is
    // called with status=PENDING and the explicit APPROVALS scope (requests
    // routed to the signed-in approver, resolved server-side from the token).
    expect(mockedUseTravelRequestList).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
        filters: expect.objectContaining({ status: 'PENDING' }),
      }),
      'APPROVALS',
    )
    // The default is reflected in the URL query string on load (D-04).
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('status=PENDING'))
  })

  it('keeps an explicit status filter from the URL instead of the default', async () => {
    renderPage('/approvals/travel-requests?status=APPROVED')

    expect(await screen.findByRole('heading', { name: 'Approval Queue' })).toBeInTheDocument()
    expect(mockedUseTravelRequestList).toHaveBeenCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ status: 'APPROVED' }),
      }),
      'APPROVALS',
    )
    expect(screen.getByTestId('location')).toHaveTextContent('status=APPROVED')
  })

  it('navigates to the P-07 approval screen on row click', async () => {
    renderPage()

    fireEvent.click(screen.getByText('Bengaluru'))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/travel-requests/tr-1/approval'),
    )
  })

  it('renders the LoadingSkeleton while the queue is loading', () => {
    renderPage(undefined, { data: null, isLoading: true })

    const skeleton = screen.getByRole('status', { name: 'Loading' })
    expect(skeleton).toHaveClass('loading-skeleton--table')
  })

  it('renders the EmptyState when there are no pending requests', async () => {
    renderPage(undefined, {
      data: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 },
    })

    expect(
      await screen.findByText('No travel requests pending your approval.'),
    ).toBeInTheDocument()
  })

  it('shows a retryable error when loading the approval queue fails', async () => {
    const refetch = vi.fn()
    renderPage('/approvals/travel-requests', {
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
      'Travel requests could not be loaded. Please try again.',
    )
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })
})