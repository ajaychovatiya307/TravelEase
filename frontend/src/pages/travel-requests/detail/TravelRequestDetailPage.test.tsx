// TravelRequestDetailPage component tests (I-05, P-04, frontend-spec.md §4.4).
// Mounts the real page inside a MemoryRouter + real AuthProvider with a seeded
// session (same pattern as DashboardPage.test.tsx / Router.test.tsx);
// `useTravelRequest` is fully mocked so no network calls happen and the
// resolved/loading/error states are controllable per test. A `LocationProbe`
// wildcard route captures navigation triggered by the ActionBar buttons.

import { useEffect } from 'react'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { RoleType } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import type { ErrorCategory } from '../../../models/error'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import type { ApprovalHistoryEntry } from '../../../models/viewModels'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import * as travelRequestService from '../../../services/travelRequestService'
import { TravelRequestDetailPage } from './TravelRequestDetailPage'

/** The controllable state the mocked `useTravelRequest` returns. */
const requestState = vi.hoisted(
  (): ServerDataState<TravelRequestResponse> => ({
    data: null,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
)

vi.mock('../../../hooks/useTravelRequests', () => ({
  useTravelRequest: () => requestState,
}))

vi.mock('../../../services/travelRequestService', () => ({
  cancel: vi.fn(),
  downloadDocument: vi.fn(),
}))

/** The request id used by the MemoryRouter entry (`/travel-requests/:id`). */
const REQUEST_ID = 'tr-1'
/** The owner of the fixture record. */
const OWNER_ID = 'U1'
/** The assigned approver of the fixture record. */
const APPROVER_ID = 'M1'

/** A `ClassifiedError`-shaped error carried on a real `Error` instance. */
function makeClassifiedError(category: ErrorCategory): ClassifiedErrorInstance {
  return Object.assign(new Error('Something went wrong'), {
    category,
    status: category === 'NETWORK_ERROR' ? null : 500,
    errorCode: category,
    timestamp: null,
    fieldErrors: [],
  }) as ClassifiedErrorInstance
}

/** Canonical `TravelRequestResponse` fixture (owner U1, approver M1, PENDING). */
function makeRequest(overrides: Partial<TravelRequestResponse> = {}): TravelRequestResponse {
  return {
    id: REQUEST_ID,
    employeeId: OWNER_ID,
    destination: 'Mumbai',
    startDate: '2026-12-01',
    endDate: '2026-12-05',
    justification: 'Client visit',
    status: 'PENDING',
    approverId: APPROVER_ID,
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [
      {
        id: 'doc-1',
        fileName: 'itinerary.pdf',
        fileType: 'PDF',
        fileSizeBytes: 2048,
        storageReference: 'blob://doc-1',
        uploadStatus: 'CONFIRMED',
      },
    ],
    createdAt: '2026-11-20T09:00:00Z',
    updatedAt: '2026-11-20T09:00:00Z',
    ...overrides,
  }
}

/** Records the current location so ActionBar navigation can be asserted. */
function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location-probe">{location.pathname}</div>
}

/** The real page held back until the seeded session exists. */
function SeededTravelRequestDetailPage({ userId, roles }: { userId: string; roles: RoleType[] }) {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId,
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!authState.isAuthenticated) {
    return null
  }

  return <TravelRequestDetailPage />
}

/**
 * TravelRequestDetailPage at `/travel-requests/:id` inside a MemoryRouter +
 * real AuthProvider, with a `*` probe route capturing ActionBar navigation.
 */
function Harness({ userId = OWNER_ID, roles }: { userId?: string; roles: RoleType[] }) {
  return (
    <MemoryRouter initialEntries={[`/travel-requests/${REQUEST_ID}`]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/travel-requests/:id"
            element={<SeededTravelRequestDetailPage userId={userId} roles={roles} />}
          />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('TravelRequestDetailPage (I-05, P-04)', () => {
  beforeEach(() => {
    requestState.data = null
    requestState.isLoading = false
    requestState.error = null
  })

  // (a) Loading state
  it('renders a loading skeleton while the request is in flight', () => {
    requestState.isLoading = true
    render(<Harness roles={['EMPLOYEE']} />)

    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
  })

  it('renders an empty state when no request data is returned', () => {
    render(<Harness roles={['EMPLOYEE']} />)

    expect(screen.getByText('No travel request found.')).toBeInTheDocument()
  })

  // (b) Success: summary, approval history and page heading
  it('renders the page heading, the summary and the approval history on success', () => {
    requestState.data = makeRequest()
    render(<Harness roles={['EMPLOYEE']} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Travel Request' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Request summary' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Approval history' }),
    ).toBeInTheDocument()
    // Summary content including the derived document display metadata.
    expect(screen.getByText('Mumbai')).toBeInTheDocument()
    expect(screen.getByText('itinerary.pdf')).toBeInTheDocument()
    expect(screen.getByText('2 KB')).toBeInTheDocument()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
    // No approval history exposed by the API → the empty state is shown.
    expect(screen.getByText('No approval decisions recorded yet.')).toBeInTheDocument()
  })

  it('renders the chronological approval history when the API exposes it', () => {
    const requestWithHistory: TravelRequestResponse & {
      approvalHistory: ApprovalHistoryEntry[]
    } = {
      ...makeRequest({ status: 'APPROVED' }),
      approvalHistory: [
        {
          decidedBy: APPROVER_ID,
          decision: 'REJECTED',
          reason: 'Missing receipts',
          decidedAt: '2026-11-21T10:00:00Z',
        },
        {
          decidedBy: APPROVER_ID,
          decision: 'APPROVED',
          reason: null,
          decidedAt: '2026-11-25T09:30:00Z',
        },
      ],
    }
    requestState.data = requestWithHistory
    render(<Harness roles={['EMPLOYEE']} />)

    expect(screen.getAllByText('Decided by')).toHaveLength(2)
    expect(screen.getByText('Missing receipts')).toBeInTheDocument()
  })

  // (c) ActionBar visibility per status/role/ownership
  it('shows only Edit for the owner of a PENDING request', () => {
    requestState.data = makeRequest()
    render(<Harness userId={OWNER_ID} roles={['EMPLOYEE']} />)

    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Resubmit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve/Reject' })).not.toBeInTheDocument()
  })

  it('shows only Resubmit for the owner of a REJECTED request', () => {
    requestState.data = makeRequest({ status: 'REJECTED', rejectionReason: 'Missing receipts' })
    render(<Harness userId={OWNER_ID} roles={['EMPLOYEE']} />)

    expect(screen.getByRole('button', { name: 'Resubmit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve/Reject' })).not.toBeInTheDocument()
  })

  it('shows only Approve/Reject for the assigned MANAGER approver of a PENDING request', () => {
    requestState.data = makeRequest()
    render(<Harness userId={APPROVER_ID} roles={['MANAGER']} />)

    expect(screen.getByRole('button', { name: 'Approve/Reject' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Resubmit' })).not.toBeInTheDocument()
  })

  it('shows no action buttons for a non-owner, non-approver viewing an APPROVED request', () => {
    requestState.data = makeRequest({ status: 'APPROVED' })
    render(<Harness userId="U2" roles={['EMPLOYEE']} />)

    expect(screen.queryByRole('group', { name: 'Actions' })).not.toBeInTheDocument()
  })

  // (d) ActionBar navigation
  it('navigates to the edit route when Edit is clicked', () => {
    requestState.data = makeRequest()
    render(<Harness userId={OWNER_ID} roles={['EMPLOYEE']} />)

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))

    expect(screen.getByTestId('location-probe')).toHaveTextContent(
      `/travel-requests/${REQUEST_ID}/edit`,
    )
  })

  it('navigates to the approval route when Approve/Reject is clicked', () => {
    requestState.data = makeRequest()
    render(<Harness userId={APPROVER_ID} roles={['MANAGER']} />)

    fireEvent.click(screen.getByRole('button', { name: 'Approve/Reject' }))

    expect(screen.getByTestId('location-probe')).toHaveTextContent(
      `/travel-requests/${REQUEST_ID}/approval`,
    )
  })

  // (e) Error categories — fixed messages only, never the raw error text
  it('renders the Not Found page for RESOURCE_NOT_FOUND', () => {
    requestState.error = makeClassifiedError('RESOURCE_NOT_FOUND')
    render(<Harness roles={['EMPLOYEE']} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Not Found' })).toBeInTheDocument()
  })

  it('renders the Access Denied page for FORBIDDEN_ACTION', () => {
    requestState.error = makeClassifiedError('FORBIDDEN_ACTION')
    render(<Harness roles={['EMPLOYEE']} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Access Denied' })).toBeInTheDocument()
  })

  it('renders the connectivity banner for NETWORK_ERROR', () => {
    requestState.error = makeClassifiedError('NETWORK_ERROR')
    render(<Harness roles={['EMPLOYEE']} />)

    expect(
      screen.getByRole('alert'),
    ).toHaveTextContent('Unable to reach the server. Check your connection and try again.')
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument()
  })

  it('renders the generic banner for any other category', () => {
    requestState.error = makeClassifiedError('INTERNAL_ERROR')
    render(<Harness roles={['EMPLOYEE']} />)

    expect(
      screen.getByRole('alert'),
    ).toHaveTextContent('Unable to load this travel request. Please try again.')
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument()
  })

  describe('cancellation and documents (Phase 1 #6/#10)', () => {
    it('lets the owner withdraw a PENDING request after confirming, then refreshes', async () => {
      requestState.data = makeRequest({ status: 'PENDING' })
      requestState.refetch = vi.fn()
      vi.mocked(travelRequestService.cancel).mockResolvedValue(makeRequest({ status: 'CANCELLED' }))
      render(<Harness roles={['EMPLOYEE']} />)

      fireEvent.click(await screen.findByRole('button', { name: 'Cancel request' }))
      // nothing is cancelled until the confirmation dialog is accepted
      expect(travelRequestService.cancel).not.toHaveBeenCalled()
      const dialog = await screen.findByRole('dialog')
      fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel request' }))

      await waitFor(() => expect(travelRequestService.cancel).toHaveBeenCalledWith(REQUEST_ID))
      await waitFor(() => expect(requestState.refetch).toHaveBeenCalled())
    })

    it('does not offer cancellation to the approver or for a started/closed request', async () => {
      requestState.data = makeRequest({ status: 'PENDING' })
      const { unmount } = render(<Harness userId={APPROVER_ID} roles={['MANAGER']} />)
      await screen.findByText('Mumbai')
      expect(screen.queryByRole('button', { name: 'Cancel request' })).not.toBeInTheDocument()
      unmount()

      requestState.data = makeRequest({ status: 'REJECTED' })
      render(<Harness roles={['EMPLOYEE']} />)
      await screen.findByText('Mumbai')
      expect(screen.queryByRole('button', { name: 'Cancel request' })).not.toBeInTheDocument()
    })

    it('renders a download action for stored documents', async () => {
      requestState.data = makeRequest()
      render(<Harness roles={['EMPLOYEE']} />)

      expect(await screen.findByRole('button', { name: 'Download itinerary.pdf' })).toBeInTheDocument()
    })
  })
})
