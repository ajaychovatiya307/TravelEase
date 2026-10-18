// I-12 Expense Review page component tests (frontend-tasks.md; screen P-13
// per frontend-spec.md §4.10). Verification method mirrors I-07/I-10:
// decision submission and post-action navigation are covered with the
// service mocked. The real `expenseService` module is replaced (the `review`
// mock drives the confirm/cancel/failure flows) and the shared `useExpenses`
// module is mocked so `useExpenseClaim` returns loaded `ServerDataState`
// fixtures — no real HTTP paths run. Auth goes through the real AuthProvider
// (setAuthState useEffect pattern, same as ExpenseDetailPage.test.tsx) with
// a FINANCE session (P-13 route roles, routeTable.ts); the page is routed at
// `/expenses/:id/review` alongside the P-14 Review Queue target, and
// post-decision navigation is asserted via the `location` test id.

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { RoleType } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import { useExpenseClaim } from '../../../hooks/useExpenses'
import type { ExpenseClaimResponse } from '../../../models/expenseClaim'
import type { ErrorCategory, FieldError } from '../../../models/error'
import * as expenseService from '../../../services/expenseService'
import type { ClassifiedErrorInstance } from '../../../services/httpClient'
import { ExpenseReviewPage } from './ExpenseReviewPage'

vi.mock('../../../hooks/useExpenses', () => ({
  // `useExpenseList` is unused by this page but the whole module is mocked,
  // mirroring the I-08/I-09/I-10 harnesses.
  useExpenseList: vi.fn(),
  useExpenseClaim: vi.fn(),
}))

vi.mock('../../../services/expenseService', () => ({
  list: vi.fn(),
  getById: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  uploadDocument: vi.fn(),
  review: vi.fn(),
  reimburse: vi.fn(),
}))

const mockedUseExpenseClaim = vi.mocked(useExpenseClaim)
const mockedReview = vi.mocked(expenseService.review)

// ---------------------------------------------------------------------------
// Shared fixture/message constants — every string asserted below is derived
// from these so the fixture and its assertions can never drift apart.
// ---------------------------------------------------------------------------

const CLAIM_ID = 'exp-1'
const EMPLOYEE_ID = 'employee-1'
const TRAVEL_REQUEST_ID = 'tr-1'
const CATEGORY = 'Lodging'
const AMOUNT = 1250
const CURRENCY = 'EUR'
const REJECTION_REASON_TEXT = 'Over the policy limit for a client visit.'
const REASON_FIELD_ERROR_MESSAGE =
  'The rejection reason must state the specific policy rule that was exceeded.'

/** Q13 dialog copy (the page's `dialogMessageFor` output) for the fixture. */
const DIALOG_CLAIM_SUMMARY = `the expense claim from ${EMPLOYEE_ID} (${AMOUNT} ${CURRENCY}, ${CATEGORY})`
const REJECT_DIALOG_MESSAGE = `This will reject ${DIALOG_CLAIM_SUMMARY}. Rejection reason: "${REJECTION_REASON_TEXT}".`
const APPROVE_DIALOG_MESSAGE = `This will approve ${DIALOG_CLAIM_SUMMARY}. The claim will move from Pending to Approved.`

const REVIEW_QUEUE_PATH = '/expenses/review-queue'
const REVIEW_PATH = `/expenses/${CLAIM_ID}/review`

/** Full api-contract.md §5.9 claim (PENDING, owned by `employee-1`). */
function makeClaim(overrides: Partial<ExpenseClaimResponse> = {}): ExpenseClaimResponse {
  return {
    id: CLAIM_ID,
    employeeId: EMPLOYEE_ID,
    travelRequestId: TRAVEL_REQUEST_ID,
    amount: AMOUNT,
    currency: CURRENCY,
    category: CATEGORY,
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
        storageReference: `blob://expenses/${CLAIM_ID}/receipt.pdf`,
        uploadStatus: 'CONFIRMED',
      },
    ],
    createdAt: '2026-11-06T10:00:00Z',
    updatedAt: '2026-11-06T10:00:00Z',
    ...overrides,
  }
}

/** A minimal normalized `ClassifiedError` for the page's error mapping. */
function classifiedError(
  category: ErrorCategory,
  status: number,
  fieldErrors: FieldError[] = [],
): ClassifiedErrorInstance {
  const error = new Error(category) as ClassifiedErrorInstance
  error.name = 'ClassifiedError'
  error.category = category
  error.status = status
  error.errorCode = category
  error.timestamp = '2026-11-06T10:00:00Z'
  error.fieldErrors = fieldErrors
  return error
}

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

/** FINANCE session (P-13 route roles, routeTable.ts) via the real AuthProvider. */
function AuthenticatedPage() {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    const roles: RoleType[] = ['FINANCE']
    setAuthState({
      userId: 'finance-1',
      roles,
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
        <Route path="/expenses/:id/review" element={<ExpenseReviewPage />} />
        {/* P-14 has no real screen in this harness — it is the post-decision
            navigation target, asserted via `location`. */}
        <Route
          path={REVIEW_QUEUE_PATH}
          element={<div data-testid="review-queue-stub">Review Queue</div>}
        />
      </Routes>
      {/* Sibling of `Routes` so it survives navigation, same convention as
          ExpenseDetailPage.test.tsx. */}
      <LocationDisplay />
    </>
  )
}

function renderPage(
  claim: ExpenseClaimResponse = makeClaim(),
  overrides: Partial<ServerDataState<ExpenseClaimResponse>> = {},
) {
  const state: ServerDataState<ExpenseClaimResponse> = {
    data: claim,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  }
  mockedUseExpenseClaim.mockReturnValue(state)
  return render(
    <MemoryRouter initialEntries={[REVIEW_PATH]}>
      <AuthProvider>
        <AuthenticatedPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

/** Selects the decision, types the reason when rejecting, and submits. */
async function submitDecision(
  user: ReturnType<typeof userEvent.setup>,
  decision: 'APPROVED' | 'REJECTED',
  reason?: string,
) {
  await user.selectOptions(screen.getByRole('combobox', { name: 'Decision' }), decision)
  if (decision === 'REJECTED' && reason !== undefined) {
    // Q16: the reason field appears only while Reject is selected.
    await user.type(screen.getByRole('textbox', { name: 'Reason' }), reason)
  }
  await user.click(screen.getByTestId('expense-review-form-submit'))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ExpenseReviewPage (I-12)', () => {
  it('renders a loading state while the claim is being fetched', () => {
    renderPage(makeClaim(), { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit decision' })).not.toBeInTheDocument()
  })

  it('renders the not-found fallback when the claim does not exist', () => {
    renderPage(makeClaim(), {
      data: null,
      error: classifiedError('RESOURCE_NOT_FOUND', 404),
    })

    expect(screen.getByRole('heading', { name: 'Not Found' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Review Expense Claim' })).not.toBeInTheDocument()
  })

  it('renders the access-denied fallback when the claim is not readable', () => {
    renderPage(makeClaim(), {
      data: null,
      error: classifiedError('FORBIDDEN_ACTION', 403),
    })

    expect(screen.getByRole('heading', { name: 'Access Denied' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Review Expense Claim' })).not.toBeInTheDocument()
  })

  it('shows a retryable banner for other fetch errors', async () => {
    const refetch = vi.fn()
    renderPage(makeClaim(), {
      data: null,
      error: classifiedError('INTERNAL_ERROR', 500),
      refetch,
    })

    expect(screen.getByRole('alert')).toHaveTextContent(
      'This expense claim could not be loaded. Please try again.',
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('renders the claim summary and the decision form for a PENDING claim', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Review Expense Claim' })).toBeInTheDocument()
    expect(screen.getByTestId('expense-review-page')).toBeInTheDocument()
    expect(mockedUseExpenseClaim).toHaveBeenCalledWith(CLAIM_ID)
    // ExpenseSummary (C-04) — response fields in display form.
    expect(screen.getByText(TRAVEL_REQUEST_ID)).toBeInTheDocument()
    expect(screen.getByText(`${AMOUNT} ${CURRENCY}`)).toBeInTheDocument()
    expect(screen.getByText(CATEGORY)).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    // Decision form (J-04) — decision select + submit; the reason field is
    // hidden until Reject is selected (Q16).
    expect(screen.getByRole('combobox', { name: 'Decision' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit decision' })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Reason' })).not.toBeInTheDocument()
  })

  it('rejects the claim through the Q13 dialog and navigates back to the queue', async () => {
    const user = userEvent.setup()
    mockedReview.mockResolvedValue(
      makeClaim({ status: 'REJECTED', rejectionReason: REJECTION_REASON_TEXT }),
    )
    renderPage()

    await screen.findByTestId('expense-review-form-submit')
    await submitDecision(user, 'REJECTED', REJECTION_REASON_TEXT)

    // Q13: the dialog summarizes the rejection and includes the reason.
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent(REJECT_DIALOG_MESSAGE)
    expect(screen.getByRole('heading', { name: 'Reject Expense Claim' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    // Q2 mirror: a successful decision lands back on the Review Queue (P-14).
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(REVIEW_QUEUE_PATH))
    expect(mockedReview).toHaveBeenCalledTimes(1)
    expect(mockedReview).toHaveBeenCalledWith(CLAIM_ID, {
      decision: 'REJECTED',
      reason: REJECTION_REASON_TEXT,
    })
  })

  it('approves the claim through the Q13 dialog and navigates back to the queue', async () => {
    const user = userEvent.setup()
    mockedReview.mockResolvedValue(makeClaim({ status: 'APPROVED' }))
    renderPage()

    await screen.findByTestId('expense-review-form-submit')
    await submitDecision(user, 'APPROVED')

    // Q13: the approval summary carries no reason (VAL-12: not required).
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent(APPROVE_DIALOG_MESSAGE)
    expect(screen.getByRole('heading', { name: 'Approve Expense Claim' })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Reason' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Confirm approval' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(REVIEW_QUEUE_PATH))
    expect(mockedReview).toHaveBeenCalledTimes(1)
    // `reason` is omitted from the APPROVED payload (api-contract.md §5.10).
    expect(mockedReview).toHaveBeenCalledWith(CLAIM_ID, { decision: 'APPROVED' })
  })

  it('closes the dialog on cancel without submitting a decision', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByTestId('expense-review-form-submit')
    await submitDecision(user, 'REJECTED', REJECTION_REASON_TEXT)

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent(REJECT_DIALOG_MESSAGE)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    // Q13: cancel closes the dialog; the form stays as-is on the review route.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mockedReview).not.toHaveBeenCalled()
    expect(screen.getByTestId('location')).toHaveTextContent(REVIEW_PATH)
    expect(screen.getByRole('textbox', { name: 'Reason' })).toBeInTheDocument()
  })

  it('shows the server reason field error inline when the review call fails', async () => {
    const user = userEvent.setup()
    mockedReview.mockRejectedValue(
      classifiedError('POLICY_VIOLATION', 422, [
        { field: 'reason', message: REASON_FIELD_ERROR_MESSAGE },
      ]),
    )
    renderPage()

    await screen.findByTestId('expense-review-form-submit')
    await submitDecision(user, 'REJECTED', REJECTION_REASON_TEXT)
    await screen.findByRole('dialog')
    await user.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    // Q14: the fieldErrors entry maps onto the reason field's inline error.
    await waitFor(() => expect(screen.getByText(REASON_FIELD_ERROR_MESSAGE)).toBeInTheDocument())
    // Dialog closed, form left open on the review route — no navigation.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(REVIEW_PATH)
    expect(mockedReview).toHaveBeenCalledTimes(1)
  })

  it('renders the not-pending notice instead of the form for a non-PENDING claim', async () => {
    renderPage(makeClaim({ status: 'APPROVED' }))

    expect(await screen.findByTestId('expense-review-page-not-pending')).toBeInTheDocument()
    // Summary still renders for the non-PENDING claim.
    expect(screen.getByText(TRAVEL_REQUEST_ID)).toBeInTheDocument()
    expect(screen.getByText('Approved')).toBeInTheDocument()
    // The decision control is unavailable, so no review call is possible.
    expect(screen.queryByTestId('expense-review-form')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit decision' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Decision' })).not.toBeInTheDocument()
  })
})
