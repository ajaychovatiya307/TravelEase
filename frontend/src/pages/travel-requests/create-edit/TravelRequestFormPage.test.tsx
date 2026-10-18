// TravelRequestFormPage component tests (I-06 verification method). Cover the
// P-05/P-06 screens (frontend-spec.md §4.5) through the real page — service
// calls are mocked (travelRequestService module), while the form (J-01), the
// `useTravelRequest` hook (F-06), and the `ClassifiedError` → UI mapping
// (frontend-api-mapping.md §3/§4) run for real: blank create + post-submit
// navigation, edit prefill, resubmit banner, update success, 409 conflict +
// refetch, 404 not-found fallback, 403 access-denied redirect, and 400
// field-error attribution.

import { useEffect } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { ClassifiedError } from '../../../models/error'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { create, getById, update } from '../../../services/travelRequestService'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import { TravelRequestFormPage } from './TravelRequestFormPage'

vi.mock('../../../services/travelRequestService', () => ({
  create: vi.fn(),
  update: vi.fn(),
  getById: vi.fn(),
  uploadDocument: vi.fn(),
}))

const createMock = vi.mocked(create)
const updateMock = vi.mocked(update)
const getByIdMock = vi.mocked(getById)

/** Current `useLocation().pathname` inside the harness MemoryRouter. */
const probe = vi.hoisted(() => ({ pathname: '' }))

/** Local `YYYY-MM-DD` for today + offset days (keeps VAL-02 pre-checks valid). */
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
 * TravelRequestFormPage inside a MemoryRouter starting at `path` + real
 * AuthProvider with the session seeded for an EMPLOYEE (same pattern as
 * Router.test.tsx): the page is held back until `isAuthenticated` is true, so
 * it only ever mounts once the session exists. A `LocationProbe` sibling
 * records the current pathname in the hoisted `probe` state object.
 */
function Harness({ path }: { path: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <SeededPage />
        <LocationProbe />
      </AuthProvider>
    </MemoryRouter>
  )
}

function SeededPage() {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId: 'U1',
      roles: ['EMPLOYEE'],
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
      <Route path="/travel-requests/new" element={<TravelRequestFormPage />} />
      <Route path="/travel-requests/:id/edit" element={<TravelRequestFormPage />} />
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

describe('TravelRequestFormPage (I-06)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    probe.pathname = ''
  })

  it('create: blank form, submit calls create with the trimmed TR-01 payload and navigates to the detail page', async () => {
    const user = userEvent.setup()
    createMock.mockResolvedValue(makeTravelRequest())
    render(<Harness path="/travel-requests/new" />)

    expect(screen.getByRole('heading', { name: 'New Travel Request' })).toBeInTheDocument()
    // Create starts blank (frontend-spec.md §4.5).
    expect(screen.getByLabelText(/destination/i)).toHaveValue('')
    expect(screen.getByLabelText(/start date/i)).toHaveValue('')
    expect(screen.getByLabelText(/end date/i)).toHaveValue('')
    expect(screen.getByLabelText(/justification/i)).toHaveValue('')

    const submit = screen.getByRole('button', { name: 'Create Travel Request' })
    expect(submit).toBeDisabled()

    await user.type(screen.getByLabelText(/destination/i), '  Mumbai  ')
    fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: isoDate(0) } })
    fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: isoDate(3) } })
    await user.type(screen.getByLabelText(/justification/i), 'Quarterly business review offsite')

    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1))
    // TR-01 (frontend-api-mapping.md §2): trimmed strings, `YYYY-MM-DD`
    // dates unchanged, `documentIds` omitted entirely when no documents.
    expect(createMock).toHaveBeenCalledWith({
      destination: 'Mumbai',
      startDate: isoDate(0),
      endDate: isoDate(3),
      justification: 'Quarterly business review offsite',
    })
    expect(createMock.mock.calls[0][0]).not.toHaveProperty('documentIds')
    await waitFor(() => expect(probe.pathname).toBe('/travel-requests/TR-1'))
  })

  it('edit: renders a loading state while the request is being fetched', async () => {
    getByIdMock.mockReturnValue(new Promise(() => {}))
    render(<Harness path="/travel-requests/TR-1/edit" />)

    expect(await screen.findByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Edit Travel Request' })).not.toBeInTheDocument()
  })

  it('edit: shows an error banner for a non-404 fetch failure', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        category: 'INTERNAL_ERROR',
        message: 'Request service is unavailable.',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/edit" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Request service is unavailable.')
  })

  it('edit: pre-fills the form from the fetched record under the Edit heading', async () => {
    getByIdMock.mockResolvedValue(makeTravelRequest())
    render(<Harness path="/travel-requests/TR-1/edit" />)

    // Q11: form is pre-filled with the current values once the fetch lands.
    const destination = await screen.findByLabelText(/destination/i)
    expect(destination).toHaveValue('Mumbai')
    expect(screen.getByLabelText(/start date/i)).toHaveValue(isoDate(0))
    expect(screen.getByLabelText(/end date/i)).toHaveValue(isoDate(3))
    expect(screen.getByLabelText(/justification/i)).toHaveValue('Quarterly business review offsite')
    expect(screen.getByRole('heading', { name: 'Edit Travel Request' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save Changes' })).toBeInTheDocument()
    // No rejection banner for non-rejected records (Q12).
    expect(screen.queryByText('Prior rejection reason')).not.toBeInTheDocument()
  })

  it('resubmit: shows the prior rejection reason banner and the resubmit action for REJECTED records', async () => {
    getByIdMock.mockResolvedValue(makeTravelRequest({ status: 'REJECTED' }))
    render(<Harness path="/travel-requests/TR-1/edit" />)

    expect(await screen.findByText('Prior rejection reason')).toBeInTheDocument()
    expect(screen.getByText('Budget exceeded')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Resubmit Travel Request' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Edit Travel Request' })).not.toBeInTheDocument()
  })

  it('edit: Save Changes calls update with the request id and existing documents, then navigates to the detail page', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    updateMock.mockResolvedValue(makeTravelRequest())
    render(<Harness path="/travel-requests/TR-1/edit" />)

    const submit = await screen.findByRole('button', { name: 'Save Changes' })
    // Flush the mount-time resolver validation so it settles inside act.
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    await waitFor(() => expect(updateMock).toHaveBeenCalledTimes(1))
    // TR-01: existing documents stay referenced as `documentIds`.
    expect(updateMock).toHaveBeenCalledWith('TR-1', {
      destination: 'Mumbai',
      startDate: isoDate(0),
      endDate: isoDate(3),
      justification: 'Quarterly business review offsite',
      documentIds: ['DOC-1'],
    })
    await waitFor(() => expect(probe.pathname).toBe('/travel-requests/TR-1'))
  })

  it('edit: a 409 CONFLICT on update shows the conflict text and refetches the record', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    updateMock.mockRejectedValue(
      makeClassifiedError({
        message: 'The request is no longer editable.',
        category: 'CONFLICT',
        status: 409,
        errorCode: 'CONFLICT',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/edit" />)

    const submit = await screen.findByRole('button', { name: 'Save Changes' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // api-mapping §1.1 P-06: the conflict message surfaces as the form error,
    // and the record is refetched to sync the displayed status.
    expect(await screen.findByText('The request is no longer editable.')).toBeInTheDocument()
    await waitFor(() => expect(getByIdMock.mock.calls.length).toBeGreaterThanOrEqual(2))
  })

  it('edit: a RESOURCE_NOT_FOUND fetch renders the shared NotFoundPage instead of the form', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Not found',
        category: 'RESOURCE_NOT_FOUND',
        status: 404,
        errorCode: 'RESOURCE_NOT_FOUND',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/edit" />)

    expect(await screen.findByText('Not Found')).toBeInTheDocument()
    expect(screen.queryByLabelText(/destination/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save Changes' })).not.toBeInTheDocument()
  })

  it('edit: a FORBIDDEN_ACTION fetch redirects to the access-denied screen', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Forbidden',
        category: 'FORBIDDEN_ACTION',
        status: 403,
        errorCode: 'FORBIDDEN_ACTION',
      }),
    )
    render(<Harness path="/travel-requests/TR-1/edit" />)

    await waitFor(() => expect(probe.pathname).toBe('/access-denied'))
    expect(await screen.findByText('Access Denied')).toBeInTheDocument()
    expect(screen.queryByLabelText(/destination/i)).not.toBeInTheDocument()
  })

  it('edit: a 400 VALIDATION_ERROR attributes field errors inline and lifts the rest to the form banner', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeTravelRequest())
    updateMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Validation failed',
        category: 'VALIDATION_ERROR',
        status: 400,
        errorCode: 'VALIDATION_ERROR',
        fieldErrors: [
          { field: 'destination', message: 'Destination is not in the approved city list.' },
          { field: 'unknownField', message: 'Unexpected issue.' },
        ],
      }),
    )
    render(<Harness path="/travel-requests/TR-1/edit" />)

    const submit = await screen.findByRole('button', { name: 'Save Changes' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // api-mapping §3: `destination` is attributed to its field; the
    // non-attributable message becomes the form-level banner.
    expect(
      await screen.findByText('Destination is not in the approved city list.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Unexpected issue.')).toBeInTheDocument()
  })
})
