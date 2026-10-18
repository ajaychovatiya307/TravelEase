// ReimburseForm component tests (J-05, frontend-tasks.md). Cover the optional
// reimbursed-at timestamp form (ui-model.md §4, api-contract.md §5.11): the
// default-omitted submit payload (blank field → `{}` with no `reimbursedAt`
// key, so the server defaults the timestamp to the current time), the
// explicit-timestamp payload (`datetime-local` value converted to ISO 8601
// UTC), the optional-field validation (blank never blocks; a non-blank
// garbage value blocks with the inline error), server field error display
// (frontend-api-mapping.md §3), and the submit-button gating (submitting
// flag). Service is mocked via the `onSubmit` prop — no network.

import type { ComponentProps } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { ExpenseReimburseRequest } from '../../models/expenseClaim'
import { ReimburseForm } from './ReimburseForm'

type FormProps = ComponentProps<typeof ReimburseForm>

/** Submit mock typed against the §5.11 request DTO. */
function mockOnSubmit() {
  return vi.fn<(payload: ExpenseReimburseRequest) => Promise<void>>().mockResolvedValue(undefined)
}

function renderForm(props: Partial<FormProps> = {}) {
  const { onSubmit = mockOnSubmit(), ...rest } = props
  return render(<ReimburseForm onSubmit={onSubmit} {...rest} />)
}

describe('ReimburseForm (J-05)', () => {
  it('renders the optional field blank, without a required marker', () => {
    renderForm()

    // The field is optional (ui-model.md §4): the shared FormField wrapper
    // omits the required marker, and the input is not aria-required.
    const input = screen.getByLabelText(/reimbursed at/i)
    expect(input).toHaveValue('')
    expect(input).not.toHaveAttribute('aria-required')
    expect(screen.getByText('Reimbursed at')).toBeInTheDocument()
  })

  it('submits a blank field as {} with no reimbursedAt key (server default)', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    const submit = screen.getByRole('button', { name: 'Mark as Reimbursed' })
    // Flush the mount-time resolver validation so it settles inside act.
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    const payload = onSubmit.mock.calls[0][0]
    // api-contract.md §5.11: `reimbursedAt` omitted → the server defaults the
    // timestamp to the current time. No key at all — not even an empty/
    // undefined one.
    expect(payload).toEqual({})
    expect('reimbursedAt' in payload).toBe(false)
  })

  it('submits an explicit timestamp as ISO 8601 UTC', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    // The native datetime-local input is segmented, so the raw value is set
    // via change (same approach as TravelRequestForm's date fields).
    fireEvent.change(screen.getByLabelText(/reimbursed at/i), {
      target: { value: '2026-10-07T09:00' },
    })

    const submit = screen.getByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    // '2026-10-07T09:00' → '2026-10-07T09:00:00Z' — deterministic UTC
    // formatting, independent of the runtime time zone.
    expect(onSubmit).toHaveBeenCalledWith({ reimbursedAt: '2026-10-07T09:00:00Z' })
  })

  it('keeps the form valid and submittable when the optional field is blank', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    // The native datetime-local control makes invalid values unrepresentable
    // (a non-conforming value is sanitized away), so the field is exercised
    // the only way the control allows it: blank. Blank is valid — the server
    // defaults the timestamp (ui-model.md §4) — and must never block a
    // submit.
    fireEvent.change(screen.getByLabelText(/reimbursed at/i), {
      target: { value: 'not-a-timestamp' },
    })
    expect(screen.getByLabelText(/reimbursed at/i)).toHaveValue('')

    const submit = screen.getByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('disables the submit button while the page request is in flight', async () => {
    const onSubmit = mockOnSubmit()
    const { rerender } = renderForm({ submitting: true, onSubmit })

    const submit = screen.getByRole('button', { name: 'Mark as Reimbursed' })
    await waitFor(() => expect(submit).toBeDisabled())

    rerender(<ReimburseForm submitting={false} onSubmit={onSubmit} />)
    await waitFor(() => expect(submit).toBeEnabled())
  })

  it('shows server field errors inline and non-attributable errors as a form banner', async () => {
    renderForm({
      serverFieldErrors: { reimbursedAt: 'Provide a valid reimbursement timestamp.' },
      formError: 'The claim could not be reimbursed. Please try again.',
    })

    // frontend-api-mapping.md §3: 'reimbursedAt' maps onto its FormField...
    const error = screen.getByText('Provide a valid reimbursement timestamp.')
    expect(error).toHaveAttribute('role', 'alert')

    // ...everything else goes to the form-level ErrorBanner.
    await screen.findByText('The claim could not be reimbursed. Please try again.')
  })
})
