// ExpenseReviewForm component tests (J-04, frontend-tasks.md; screen P-13).
// Mirrors J-02's verification method (frontend-test.md FRM-17 mirrors
// FRM-09/FRM-10): the reason field is hidden until Reject is selected, and a
// blank (whitespace-only) reason blocks the submit when rejecting
// (frontend-clarification.md Q16, VAL-12 via L-02). The pre-submit
// ConfirmDialog (Q13) belongs to the host page (I-12), so `onSubmit` is the
// form/host boundary and is mocked here — service mocked.
//
// Conventions:
// - the error copy is asserted through the imported `DECISION_MESSAGES`
//   constant, never hardcoded (L-02 is the single source of truth).
// - the decision/reason fields render through the shared FormField primitives
//   (no data-testid passthrough), so they are driven by label — mirroring
//   ExpenseClaimForm.test.tsx's `getByLabelText` queries for
//   FileUploadControl-owned fields.

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { DECISION_MESSAGES } from '../../validation/decisionSchema'
import { ExpenseReviewForm, type ExpenseReviewFormProps } from './ExpenseReviewForm'

/** Mounts the form with a mocked `onSubmit` (the host-page boundary). */
function renderReviewForm(overrides: Partial<ExpenseReviewFormProps> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const user = userEvent.setup()
  const renderResult = render(<ExpenseReviewForm onSubmit={onSubmit} {...overrides} />)
  return { user, onSubmit, ...renderResult }
}

describe('ExpenseReviewForm', () => {
  it('hides the reason field until Reject is selected and approves without a reason', async () => {
    const { user, onSubmit } = renderReviewForm()

    expect(screen.queryByLabelText(/^Reason/)).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText(/^Decision/), 'APPROVED')

    expect(screen.queryByLabelText(/^Reason/)).not.toBeInTheDocument()

    await user.click(screen.getByTestId('expense-review-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({ decision: 'APPROVED' })
  })

  it('reveals the reason field when Reject is selected and blocks a blank reason', async () => {
    const { user, onSubmit } = renderReviewForm()

    await user.selectOptions(screen.getByLabelText(/^Decision/), 'REJECTED')

    expect(screen.getByLabelText(/^Reason/)).toBeInTheDocument()

    await user.click(screen.getByTestId('expense-review-form-submit'))

    await waitFor(() => {
      const reasonError = screen.getByText(DECISION_MESSAGES.reason)
      expect(reasonError).toHaveAttribute('role', 'alert')
    })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('blocks a whitespace-only reason (VAL-12: required and non-blank)', async () => {
    const { user, onSubmit } = renderReviewForm()

    await user.selectOptions(screen.getByLabelText(/^Decision/), 'REJECTED')
    await user.type(screen.getByLabelText(/^Reason/), '   ')

    await user.click(screen.getByTestId('expense-review-form-submit'))

    await waitFor(() => {
      const reasonError = screen.getByText(DECISION_MESSAGES.reason)
      expect(reasonError).toHaveAttribute('role', 'alert')
    })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits the mapped request DTO with the entered (trimmed) reason when rejecting', async () => {
    const { user, onSubmit } = renderReviewForm()

    await user.selectOptions(screen.getByLabelText(/^Decision/), 'REJECTED')
    await user.type(screen.getByLabelText(/^Reason/), '  Duplicate of an earlier claim  ')

    await user.click(screen.getByTestId('expense-review-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      decision: 'REJECTED',
      reason: 'Duplicate of an earlier claim',
    })
  })

  it('renders server field errors through the same inline path', async () => {
    const serverReasonError = 'Reason must not be blank (server).'
    const { user } = renderReviewForm({
      serverFieldErrors: { reason: serverReasonError },
    })

    await user.selectOptions(screen.getByLabelText(/^Decision/), 'REJECTED')

    await waitFor(() => {
      const alert = screen.getByRole('alert')
      expect(alert).toHaveTextContent(serverReasonError)
    })
  })

  it('leaves the form open when the host page rejects the submit', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'))
    const user = userEvent.setup()
    render(<ExpenseReviewForm onSubmit={onSubmit} />)

    await user.selectOptions(screen.getByLabelText(/^Decision/), 'APPROVED')
    await user.click(screen.getByTestId('expense-review-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(screen.getByTestId('expense-review-form')).toBeInTheDocument()
  })
})
