// ReimbursePage component tests (I-13 verification method). Cover the P-15
// screen (frontend-spec.md §4.11) through the real page — service calls are
// mocked (expenseService module), while the `useExpenseClaim` hook (F-06),
// the `ReimburseForm` (J-05), and the `ClassifiedError` → UI mapping
// (frontend-api-mapping.md §3/§4) run for real: the read-only summary, the
// FR-FIN-03 approval gate, the Q13 confirm-then-reimburse flow with P-14
// queue navigation, the api-contract.md §5.11 payload shape (blank → `{}`
// with no `reimbursedAt` key, explicit value → UTC ISO 8601), dialog
// cancellation, and the reimburse/fetch error mappings (409 + refetch, 400
// field attribution, 404, 403).

import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import { NotificationProvider } from '../../../context/NotificationContext'
import type { ClassifiedError } from '../../../models/error'
import type { ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { getById, reimburse } from '../../../services/expenseService'
import { AccessDeniedPage } from '../../../shared/AccessDeniedPage'
import { ReimbursePage } from './ReimbursePage'

vi.mock('../../../services/expenseService', () => ({
  list: vi.fn(),
  getById: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  uploadDocument: vi.fn(),
  review: vi.fn(),
  reimburse: vi.fn(),
}))

const getByIdMock = vi.mocked(getById)
const reimburseMock = vi.mocked(reimburse)

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

/** `ExpenseClaimResponse` factory (api-contract.md §5.9), today-based date. */
function makeExpenseClaim(overrides: Partial<ExpenseClaimResponse> = {}): ExpenseClaimResponse {
  return {
    id: 'EX-1',
    employeeId: 'U1',
    travelRequestId: 'TR-1',
    amount: 250,
    currency: 'INR',
    category: 'MEALS',
    expenseDate: isoDate(0),
    status: 'APPROVED',
    duplicateFlag: false,
    rejectionReason: null,
    reimbursedAt: null,
    documents: [],
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
 * ReimbursePage inside a MemoryRouter starting at `path` + real
 * NotificationProvider (the page's success toast goes through
 * `useNotifications`, which throws without the provider; the fire-and-forget
 * `enqueueToast` needs no `NotificationHost` rendering) + real AuthProvider
 * with the session seeded for a FINANCE user (the payer's role, same pattern
 * as Router.test.tsx): the page is held back until `isAuthenticated` is true,
 * so it only ever mounts once the session exists. A `LocationProbe` sibling
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
      userId: 'U4',
      roles: ['FINANCE'],
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
      <Route path="/expenses/:id/reimburse" element={<ReimbursePage />} />
      <Route path="/expenses/review-queue" element={<div>Expense Review Queue</div>} />
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

describe('ReimbursePage (I-13)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    probe.pathname = ''
  })

  it('approved: renders the read-only summary and the reimbursement form', async () => {
    getByIdMock.mockResolvedValue(makeExpenseClaim())
    render(<Harness path="/expenses/EX-1/reimburse" />)

    // Read-only summary (frontend-spec.md §4.11): amount + currency, the
    // human-readable status (StatusBadge), category, expense date, and the
    // travel request id.
    expect(await screen.findByText('Expense Reimbursement')).toBeInTheDocument()
    expect(screen.getByText('250.00 INR')).toBeInTheDocument()
    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByText('MEALS')).toBeInTheDocument()
    expect(screen.getByText(isoDate(0))).toBeInTheDocument()
    expect(screen.getByText('TR-1')).toBeInTheDocument()
    // FR-FIN-03 gate: APPROVED claims get the reimbursement form — the
    // optional `Reimbursed at` datetime field and the submit control.
    expect(screen.getByLabelText('Reimbursed at')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mark as Reimbursed' })).toBeInTheDocument()
  })

  it('renders a loading state while the claim is being fetched', async () => {
    getByIdMock.mockReturnValue(new Promise(() => {}))
    render(<Harness path="/expenses/EX-1/reimburse" />)

    expect(await screen.findByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark as Reimbursed' })).not.toBeInTheDocument()
  })

  it('shows an error banner for a non-404 fetch failure', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        category: 'INTERNAL_ERROR',
        message: 'Claim service is unavailable.',
      }),
    )
    render(<Harness path="/expenses/EX-1/reimburse" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Claim service is unavailable.')
  })

  it('gate: non-approved claims hide the reimbursement form and show the warning instead', async () => {
    const gateCases: Array<{ label: string; claim: ExpenseClaimResponse }> = [
      { label: 'Pending', claim: makeExpenseClaim({ status: 'PENDING' }) },
      {
        label: 'Reimbursed',
        claim: makeExpenseClaim({ status: 'REIMBURSED', reimbursedAt: '2026-01-05T10:00:00Z' }),
      },
    ]

    for (const { label, claim } of gateCases) {
      getByIdMock.mockResolvedValue(claim)
      render(<Harness path="/expenses/EX-1/reimburse" />)

      expect(await screen.findByText(label)).toBeInTheDocument()
      // FR-FIN-03 gate: no reimbursement controls for non-approved claims…
      expect(screen.queryByRole('button', { name: 'Mark as Reimbursed' })).not.toBeInTheDocument()
      // …and the warning explains why.
      expect(
        screen.getByText(
          'This claim is not approved for reimbursement, so no reimbursement controls are available.',
        ),
      ).toBeInTheDocument()
      cleanup()
    }
  })

  it('blank submit: opens the ConfirmDialog, confirming calls reimburse with {} and navigates to the Review Queue (P-14)', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeExpenseClaim())
    reimburseMock.mockResolvedValue(makeExpenseClaim({ status: 'REIMBURSED' }))
    render(<Harness path="/expenses/EX-1/reimburse" />)

    const submit = await screen.findByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // Q13: submitting only opens the ConfirmDialog — the service is not hit yet.
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(reimburseMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Reimburse' }))

    // api-contract.md §5.11: a blank timestamp submits `{}` with no
    // `reimbursedAt` key — the server defaults to its current time.
    await waitFor(() => expect(reimburseMock).toHaveBeenCalledTimes(1))
    expect(reimburseMock).toHaveBeenCalledWith('EX-1', {})
    expect(reimburseMock.mock.calls[0][1]).not.toHaveProperty('reimbursedAt')
    // P-14: back to the Review Queue on success.
    await waitFor(() => expect(probe.pathname).toBe('/expenses/review-queue'))
  })

  it('explicit timestamp: confirms reimburse with the UTC ISO 8601 reimbursedAt and navigates to the Review Queue', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeExpenseClaim())
    reimburseMock.mockResolvedValue(makeExpenseClaim({ status: 'REIMBURSED' }))
    render(<Harness path="/expenses/EX-1/reimburse" />)

    const submit = await screen.findByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    // datetime-local input: set the value directly (the repo's date-input
    // precedent) — a `YYYY-MM-DDTHH:mm` wall-clock value.
    fireEvent.change(screen.getByLabelText('Reimbursed at'), {
      target: { value: '2026-10-07T09:00' },
    })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(reimburseMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Reimburse' }))

    // J-05 mapping: the entered wall clock is formatted deterministically as
    // UTC ISO 8601, independent of the runtime time zone.
    await waitFor(() => expect(reimburseMock).toHaveBeenCalledTimes(1))
    expect(reimburseMock).toHaveBeenCalledWith('EX-1', { reimbursedAt: '2026-10-07T09:00:00Z' })
    await waitFor(() => expect(probe.pathname).toBe('/expenses/review-queue'))
  })

  it('cancelling the ConfirmDialog does not call reimburse and keeps the form mounted', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeExpenseClaim())
    render(<Harness path="/expenses/EX-1/reimburse" />)

    const submit = await screen.findByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(reimburseMock).not.toHaveBeenCalled()
    // The form stays mounted so Finance can still record the reimbursement.
    expect(screen.getByLabelText('Reimbursed at')).toBeInTheDocument()
  })

  it('reimburse: a 409 CONFLICT surfaces the conflict text, closes the dialog, and refetches the claim', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeExpenseClaim())
    reimburseMock.mockRejectedValue(
      makeClassifiedError({
        message: 'The claim is no longer approved.',
        category: 'CONFLICT',
        status: 409,
        errorCode: 'CONFLICT',
      }),
    )
    render(<Harness path="/expenses/EX-1/reimburse" />)

    const submit = await screen.findByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reimburse' }))

    // api-mapping §1.2 P-15: the conflict message becomes the form error, the
    // record is refetched to sync the displayed status (FR-FIN-03 gate), and
    // the dialog closes.
    expect(await screen.findByText('The claim is no longer approved.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(getByIdMock.mock.calls.length).toBeGreaterThanOrEqual(2))
    // Still on the reimbursement page — no navigation on failure.
    expect(probe.pathname).toBe('/expenses/EX-1/reimburse')
  })

  it('reimburse: a 400 VALIDATION_ERROR attributes the reimbursedAt field error inline', async () => {
    const user = userEvent.setup()
    getByIdMock.mockResolvedValue(makeExpenseClaim())
    reimburseMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Validation failed',
        category: 'VALIDATION_ERROR',
        status: 400,
        errorCode: 'VALIDATION_ERROR',
        fieldErrors: [{ field: 'reimbursedAt', message: 'Timestamp is in the future.' }],
      }),
    )
    render(<Harness path="/expenses/EX-1/reimburse" />)

    const submit = await screen.findByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    // A well-formed but future wall clock — client validation passes, the
    // server rejects it and attributes the error to the field.
    fireEvent.change(screen.getByLabelText('Reimbursed at'), {
      target: { value: '2099-12-31T23:59' },
    })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reimburse' }))

    // api-mapping §3: 'reimbursedAt' is attributed to the field — inline
    // error, dialog closed, form still mounted.
    expect(await screen.findByText('Timestamp is in the future.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Reimbursed at')).toBeInTheDocument()
  })

  it('fetch: a RESOURCE_NOT_FOUND renders the shared NotFoundPage', async () => {
    getByIdMock.mockRejectedValue(
      makeClassifiedError({
        message: 'Not found',
        category: 'RESOURCE_NOT_FOUND',
        status: 404,
        errorCode: 'RESOURCE_NOT_FOUND',
      }),
    )
    render(<Harness path="/expenses/EX-1/reimburse" />)

    expect(await screen.findByText('Not Found')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark as Reimbursed' })).not.toBeInTheDocument()
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
    render(<Harness path="/expenses/EX-1/reimburse" />)

    await waitFor(() => expect(probe.pathname).toBe('/access-denied'))
    expect(screen.queryByRole('button', { name: 'Mark as Reimbursed' })).not.toBeInTheDocument()
  })
})
