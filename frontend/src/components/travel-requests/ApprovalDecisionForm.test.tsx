// ApprovalDecisionForm component tests (J-02, frontend-tasks.md). Cover the
// approve/reject decision form (frontend-spec.md §4.6,
// frontend-clarification.md Q16): the conditional `reason` visibility (hidden
// until Reject is selected), the VAL-12 validation block (a blank reason
// blocks a reject submit — frontend-spec.md §7), the TR-03 submit-payload
// mapping (frontend-api-mapping.md §2 — no `reason` key on APPROVED, trimmed
// reason on REJECTED), server field error display (api-mapping §3), and the
// submit-button gating (invalid form / submitting flag). Service is mocked via
// the `onSubmit` prop — no network.

import type { ComponentProps } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { ApprovalDecisionRequest } from '../../models/travelRequest'
import { REJECT_REASON_REQUIRED_MESSAGE } from '../../validation/decisionSchema'
import { ApprovalDecisionForm } from './ApprovalDecisionForm'

type FormProps = ComponentProps<typeof ApprovalDecisionForm>

/** Submit mock typed against the TR-03 request DTO. */
function mockOnSubmit() {
  return vi.fn<(payload: ApprovalDecisionRequest) => Promise<void>>().mockResolvedValue(undefined)
}

function renderForm(props: Partial<FormProps> = {}) {
  const { onSubmit = mockOnSubmit(), ...rest } = props
  return render(<ApprovalDecisionForm onSubmit={onSubmit} {...rest} />)
}

describe('ApprovalDecisionForm (J-02)', () => {
  it('renders with the Approve default and the reason field hidden (Q16)', async () => {
    renderForm()

    // Regex matchers: the shared label chrome appends the required marker,
    // so the accessible text is e.g. "Decision *".
    expect(screen.getByLabelText(/decision/i)).toHaveValue('APPROVED')
    // Q16: the reason field is hidden until "Reject" is selected.
    expect(screen.queryByLabelText(/reason/i)).toBeNull()

    const submit = screen.getByRole('button', { name: 'Submit Decision' })
    // Flush the mount-time resolver validation so it settles inside act.
    await waitFor(() => expect(submit).toBeEnabled())
  })

  it('reveals the reason field when Reject is selected (Q16)', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.selectOptions(screen.getByLabelText(/decision/i), 'REJECTED')

    expect(screen.getByLabelText(/reason/i)).toHaveValue('')
    // VAL-12: with no reason yet, the form is invalid until one is entered.
    const submit = screen.getByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeDisabled())
  })

  it('blocks submit with the VAL-12 message when rejecting with a blank reason', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    await user.selectOptions(screen.getByLabelText(/decision/i), 'REJECTED')

    // Blur the revealed reason field → VAL-12 inline error (frontend-spec.md §7).
    await user.click(screen.getByLabelText(/reason/i))
    await user.tab()
    const error = screen.getByText(REJECT_REASON_REQUIRED_MESSAGE)
    expect(error).toHaveAttribute('role', 'alert')

    const submit = screen.getByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeDisabled())
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits a valid reject with the TR-03 payload (trimmed reason)', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    await user.selectOptions(screen.getByLabelText(/decision/i), 'REJECTED')
    await user.type(screen.getByLabelText(/reason/i), '  Over budget for this cycle  ')

    const submit = screen.getByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    // TR-03 (frontend-api-mapping.md §2): `reason` is trimmed when present.
    expect(onSubmit).toHaveBeenCalledWith({
      decision: 'REJECTED',
      reason: 'Over budget for this cycle',
    })
  })

  it('submits an approve with the TR-03 payload (no reason key at all)', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    const submit = screen.getByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    const payload = onSubmit.mock.calls[0][0]
    // TR-03 (frontend-api-mapping.md §2): `reason` is omitted entirely when
    // `decision = APPROVED` — no key, not even an empty/undefined one.
    expect(payload).toEqual({ decision: 'APPROVED' })
    expect('reason' in payload).toBe(false)
  })

  it('disables the submit button while the page request is in flight', async () => {
    const onSubmit = mockOnSubmit()
    const { rerender } = renderForm({ submitting: true, onSubmit })

    const submit = screen.getByRole('button', { name: 'Submit Decision' })
    await waitFor(() => expect(submit).toBeDisabled())

    rerender(<ApprovalDecisionForm submitting={false} onSubmit={onSubmit} />)
    await waitFor(() => expect(submit).toBeEnabled())
  })

  it('shows server field errors inline and non-attributable errors as a form banner', async () => {
    const user = userEvent.setup()
    renderForm({
      serverFieldErrors: {
        decision: 'This request is no longer pending approval.',
        reason: 'Provide an actionable rejection reason.',
      },
      formError: 'The decision could not be saved. Please try again.',
    })

    // frontend-api-mapping.md §3: `decision` maps onto its FormField...
    expect(screen.getByText('This request is no longer pending approval.')).toBeInTheDocument()

    // ...and the `reason` server error appears once the field is revealed (Q16).
    await user.selectOptions(screen.getByLabelText(/decision/i), 'REJECTED')
    expect(screen.getByText('Provide an actionable rejection reason.')).toBeInTheDocument()

    // ...everything else goes to the form-level ErrorBanner.
    await screen.findByText('The decision could not be saved. Please try again.')
  })
})
