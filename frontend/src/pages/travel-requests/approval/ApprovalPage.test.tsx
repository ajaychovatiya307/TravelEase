// ApprovalPage component tests (I-07 verification method). Cover the P-07
// screen (frontend-spec.md §4.6) through the real page — service calls are
// mocked (travelRequestService module), while the `useTravelRequest` hook
// (F-06), the `ApprovalDecisionForm` (J-02), and the `ClassifiedError` → UI
// mapping (frontend-api-mapping.md §3/§4) run for real: the read-only
// summary, the Q13 confirm-then-decide flow with Q2 queue navigation, the
// TR-03 payload shape (approve omits `reason`, reject trims it), dialog
// cancellation, the TR-02 gate on non-pending status, and the decide/fetch
// error mappings (409 + refetch, 400 field attribution, 404, 403).

import { useEffect } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import { NotificationProvider } from '../../../context/NotificationContext'
import type { ClassifiedError } from '../../../models/error'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { decide, getById } from '../../../services/travelRequestService'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import { ApprovalPage } from './ApprovalPage'

vi.mock('../../../services/travelRequestService', () => ({
  create: vi.fn(),
  update: vi.fn(),
  getById: vi.fn(),
  uploadDocument: vi.fn(),
  decide: vi.fn(),
}))

const getByIdMock = vi.mocked(getById)
const decideMock = vi.mocked(decide)

/** Current `useLocation().pathname` inside the harness MemoryRouter. */
const probe = vi.hoisted(() => ({ pathname: '' }))

/** Local `YYYY-MM-DD` for today + offset days. */
function isoDate(offsetDays = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** `TravelRequestResponse` factory (api-contract.md §5.3), today-based dates. */
function makeTravelRequest(overrides: Partial<TravelRequestResponse> = {}): TravelRequestResponse {
  return {
    id: 'TR-1',
    employeeId: 'U1',
    destination: 'Mumbai',
    startDate: isoDate(0),
    endDate: isoDate(3),
    justification: 'Quarterly business review offsite',
    status: 'PENDING',
    approverId: null,
    rejectionReason: 'Budget exceeded',
    resubmissionOfId: null,
    documents: [
      {
        id: 'DOC-1',
        fileName: 'a.pdf',
        fileType: 'PDF',
        fileSizeBytes: 1,
        storageReference: 's',
        uploadStatus: 'CONFIRMED',
      },
    ],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

/** Classified error carried on a real `Error` (see DashboardPage.test.tsx). */
function makeClassifiedError(
  error: Pick<ClassifiedError, 'category' | 'message'> &
    Partial<Omit<ClassifiedError, 'category' | 'message'>>,
): ClassifiedErrorInstance {
  return Object.assign(
    new Error(error.message),
    { status: null, errorCode: null, timestamp: null, fieldErrors: [] },
    error,
  ) as ClassifiedErrorInstance
}

/**
 * ApprovalPage inside a MemoryRouter starting at `path` + real
 * NotificationProvider (the page's success toast goes through
 * `useNotifications`, which throws without the provider; the fire-and-forget
 * `enqueueToast` needs no `NotificationHost` rendering) + real AuthProvider
 * with the session seeded for a MANAGER (the approver's role, same pattern as
 * Router.test.tsx): the page is held back until `isAuthenticated` is true, so
 * it only ever mounts once the session exists. A `LocationProbe` sibling
 * records the current pathname in the hoisted `probe` state object.
 */
function Harness({ path }: { path: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <NotificationProvider>
        <AuthProvider>
          <SeededPage />
          <LocationProbe />
        </AuthProvider>
      </NotificationProvider>
    </MemoryRouter>
  )
}

function SeededPage() {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId: 'U2',
      roles: ['MANAGER'],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <Routes>
      <Route path="/travel-requests/:id/approval" element={<ApprovalPage />} />
      <Route path="/approvals/travel-requests" element={<div>Approval Queue</div>} />
      <Route path="/access-denied" element={<AccessDeniedPage />} />
    </Routes>
  )
}

function LocationProbe() {
  const { pathname } = useLocation()
  useEffect(() => {
    probe.pathname = pathname
  }, [pathname])
  return null
}

describe('ApprovalPage (I-07)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    probe.pathname = ''
  })

  it('pending: renders the read-only summary and the decision form', async () => {
    getByIdMock.mockResolvedValue(makeTravelRequest())
    render(<Harness path="/travel-requests/TR-1/approval" />)

    expect(await screen.findByText('Mumbai')).toBeInTheDocument()
    // StatusBadge renders the human-readable status label (PENDING → 'Pending').
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByText('Quarterly business review offsite')).toBeInTheDocument()
    expect(screen.getByText(/a\.pdf/)).toBeInTheDocument()
    // TR-02 gate: PENDING records get the decision form. The decision
    // control is the labelled select (`selector: 'select'` excludes the
    // `aria-label="Decision"` section wrapping the form).
    expect(screen.getByLabelText(/decision/i, { selector: 'select' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit Decision' })).toBeInTheDocument()
  })

  it('renders the loading state while the approval request is being fetched', async () => {
    getByIdMock.mockReturnValue(new Promise(() => {}))
    render(<Harness path="/travel-requests/TR-1/approval" />)

    expect(await screen.findByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit Decision' })).not.toBeInTheDocument()
  })

  it('shows a retryable error banner when another fetch error occurs', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        category: 'INTERNAL_ERROR',
        message: 'Approval request could not be loaded.',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/approval" />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Approval request could not be loaded.',
    )
  })

  it('approve: submits open the ConfirmDialog, confirming calls decide once and navigates to the Approval Queue (Q2)', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    decideMock.mockResolvedValue(makeTravelRequest({ status: 'APPROVED' }))
    render(<Harness path="/travel-requests/TR-1/approval" />)

    // Q16: the decision select starts on Approve, so the form is valid as-is.
    const submit = await screen.findByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // Q13: submitting only opens the ConfirmDialog — the service is not hit yet.
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(decideMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Approve' }))

    await waitFor(() => expect(decideMock).toHaveBeenCalledTimes(1))
    // TR-03: the approve payload carries no `reason` key at all.
    expect(decideMock).toHaveBeenCalledWith('TR-1', { decision: 'APPROVED' })
    expect(decideMock.mock.calls[0][1]).not.toHaveProperty('reason')
    // Q2: back to the Approval Queue (P-08), not the Detail page.
    await waitFor(() => expect(probe.pathname).toBe('/approvals/travel-requests'))
  })

  it('reject: submits the trimmed reason with the REJECTED decision and navigates to the Approval Queue', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    decideMock.mockResolvedValue(makeTravelRequest({ status: 'REJECTED' }))
    render(<Harness path="/travel-requests/TR-1/approval" />)

    const submit = await screen.findByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.selectOptions(screen.getByLabelText(/decision/i, { selector: 'select' }), 'REJECTED')
    // VAL-12: the reason textarea is revealed (and required) only for Reject.
    await user.type(await screen.findByLabelText(/reason/i), '  Over budget  ')
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(decideMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Reject' }))

    await waitFor(() => expect(decideMock).toHaveBeenCalledTimes(1))
    // TR-03: the rejection reason is trimmed.
    expect(decideMock).toHaveBeenCalledWith('TR-1', { decision: 'REJECTED', reason: 'Over budget' })
    await waitFor(() => expect(probe.pathname).toBe('/approvals/travel-requests'))
  })

  it('cancelling the ConfirmDialog does not call decide and keeps the form mounted', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    decideMock.mockResolvedValue(makeTravelRequest({ status: 'APPROVED' }))
    render(<Harness path="/travel-requests/TR-1/approval" />)

    const submit = await screen.findByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(decideMock).not.toHaveBeenCalled()
    // The form stays mounted so the approver can still decide.
    expect(screen.getByLabelText(/decision/i, { selector: 'select' })).toBeInTheDocument()
  })

  it('non-pending: the TR-02 gate hides the decision controls and shows the warning instead', async () => {
    getByIdMock.mockResolvedValue(makeTravelRequest({ status: 'APPROVED' }))
    render(<Harness path="/travel-requests/TR-1/approval" />)

    expect(await screen.findByText('Approved')).toBeInTheDocument()
    expect(screen.queryByLabelText(/decision/i, { selector: 'select' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit Decision' })).not.toBeInTheDocument()
    expect(
      screen.getByText(
        'This travel request is no longer pending a decision, so no decision controls are available.',
      ),
    ).toBeInTheDocument()
  })

  it('decide: a 409 CONFLICT surfaces the conflict text and refetches the record', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    decideMock.mockRejectedValue(
      makeClassifiedError({
        message: 'The request has already been decided.',
        category: 'CONFLICT',
        status: 409,
        errorCode: 'CONFLICT',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/approval" />)

    const submit = await screen.findByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Approve' }))

    // api-mapping §1.1 P-07: the conflict message becomes the form error, the
    // record is refetched to sync the displayed status, and the dialog closes.
    expect(await screen.findByText('The request has already been decided.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(getByIdMock.mock.calls.length).toBeGreaterThanOrEqual(2))
    // Still on the approval page — no navigation on failure.
    expect(probe.pathname).toBe('/travel-requests/TR-1/approval')
  })

  it('decide: a 400 VALIDATION_ERROR attributes the reason field error inline', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    decideMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Validation failed',
        category: 'VALIDATION_ERROR',
        status: 400,
        errorCode: 'VALIDATION_ERROR',
        fieldErrors: [{ field: 'reason', message: 'Reason is required when rejecting.' }],
      }),
    )
    render(<Harness path="/travel-requests/TR-1/approval" />)

    const submit = await screen.findByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.selectOptions(screen.getByLabelText(/decision/i, { selector: 'select' }), 'REJECTED')
    await user.type(await screen.findByLabelText(/reason/i), 'Over budget')
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reject' }))

    // api-mapping §3: 'reason' is attributed to the field — inline error,
    // dialog closed, form still mounted.
    expect(await screen.findByText('Reason is required when rejecting.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/reason/i)).toBeInTheDocument()
  })

  it('fetch: a RESOURCE_NOT_FOUND render the shared NotFoundPage', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Not found',
        category: 'RESOURCE_NOT_FOUND',
        status: 404,
        errorCode: 'RESOURCE_NOT_FOUND',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/approval" />)

    expect(await screen.findByText('Not Found')).toBeInTheDocument()
    expect(screen.queryByLabelText(/decision/i, { selector: 'select' })).not.toBeInTheDocument()
  })

  it('fetch: a FORBIDDEN_ACTION fetch redirects to the access-denied screen', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Forbidden',
        category: 'FORBIDDEN_ACTION',
        status: 403,
        errorCode: 'FORBIDDEN_ACTION',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/approval" />)

    await waitFor(() => expect(probe.pathname).toBe('/access-denied'))
    expect(screen.queryByLabelText(/decision/i, { selector: 'select' })).not.toBeInTheDocument()
  })
})
