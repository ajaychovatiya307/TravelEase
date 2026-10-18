// TravelRequestForm component tests (J-01, frontend-tasks.md). Cover the
// single-step create/edit/resubmit form (frontend-spec.md §4.5,
// frontend-clarification.md Q9–Q12): blank create, required-field validation
// blocking (frontend-spec.md §7 VAL-01–04), the TR-01 submit-payload mapping
// (frontend-api-mapping.md §2), server field error display (api-mapping §3),
// and the submit-button gating (required fields / in-flight upload /
// submitting flag). Service is mocked via the `onSubmit`/`onUpload` props —
// no network; FileUploadControl is driven through its real API (C-07).

import type { ComponentProps } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { TravelRequestCreateRequest } from '../../models/travelRequest'
import type { UploadedDocument } from '../../shared/FileUploadControl'
import type { TravelRequestFormValues } from '../../validation/travelRequestSchema'
import { TravelRequestForm } from './TravelRequestForm'

type FormProps = ComponentProps<typeof TravelRequestForm>

/** Local `YYYY-MM-DD` for today + offset days (keeps VAL-02 pre-checks valid). */
function isoDate(offsetDays = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function makeFile(name: string): File {
  return new File(['x'], name, { type: 'application/pdf' })
}

/** Upload mock resolving immediately to a CONFIRMED document (C-07 API). */
function mockUpload(documentId = 'DOC-NEW') {
  const confirmed: UploadedDocument = {
    documentId,
    fileName: 'receipt.pdf',
    uploadStatus: 'CONFIRMED',
  }
  return vi.fn().mockResolvedValue(confirmed)
}

/** Submit mock typed against the TR-01 request DTO. */
function mockOnSubmit() {
  return vi
    .fn<(payload: TravelRequestCreateRequest) => Promise<void>>()
    .mockResolvedValue(undefined)
}

/** Valid pre-filled values for edit/resubmit tests (dates satisfy VAL-02). */
function validValues(overrides: Partial<TravelRequestFormValues> = {}): TravelRequestFormValues {
  return {
    destination: 'Mumbai',
    startDate: isoDate(0),
    endDate: isoDate(3),
    justification: 'Quarterly business review offsite',
    ...overrides,
  }
}

function renderForm(props: Partial<FormProps> = {}) {
  const { mode = 'create', onSubmit = mockOnSubmit(), onUpload = mockUpload(), ...rest } = props
  return render(<TravelRequestForm mode={mode} onSubmit={onSubmit} onUpload={onUpload} {...rest} />)
}

describe('TravelRequestForm (J-01)', () => {
  it('renders a blank single-step form in create mode with submit disabled', () => {
    const { container } = renderForm()

    // Regex matchers: the shared label chrome appends the required marker,
    // so the accessible text is e.g. "Destination *".
    expect(screen.getByLabelText(/destination/i)).toHaveValue('')
    expect(screen.getByLabelText(/start date/i)).toHaveValue('')
    expect(screen.getByLabelText(/end date/i)).toHaveValue('')
    expect(screen.getByLabelText(/justification/i)).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Create Travel Request' })).toBeDisabled()
    // No rejection banner outside resubmit mode (Q12).
    expect(container.querySelector('.rejection-reason-banner')).toBeNull()
  })

  it('blocks submit on empty required fields and shows inline errors', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    // Blur the destination field → VAL-01 inline error (frontend-spec.md §7).
    await user.click(screen.getByLabelText(/destination/i))
    await user.tab()
    const error = screen.getByText('Destination is required')
    expect(error).toHaveAttribute('role', 'alert')

    const submit = screen.getByRole('button', { name: 'Create Travel Request' })
    expect(submit).toBeDisabled()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits a valid create form with the TR-01 payload (trimmed strings, no documentIds)', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    renderForm({ onSubmit })

    await user.type(screen.getByLabelText(/destination/i), '  Pune  ')
    fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: isoDate(0) } })
    fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: isoDate(3) } })
    await user.type(
      screen.getByLabelText(/justification/i),
      'Client site visit for contract renewal',
    )

    const submit = screen.getByRole('button', { name: 'Create Travel Request' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    // TR-01 (frontend-api-mapping.md §2): strings trimmed, `YYYY-MM-DD`
    // dates unchanged, `documentIds` omitted entirely when no documents.
    expect(onSubmit).toHaveBeenCalledWith({
      destination: 'Pune',
      startDate: isoDate(0),
      endDate: isoDate(3),
      justification: 'Client site visit for contract renewal',
    })
  })

  it('flows document references from FileUploadControl into documentIds', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    const onUpload = mockUpload('DOC-NEW')
    renderForm({ onSubmit, onUpload })

    await user.type(screen.getByLabelText(/destination/i), 'Hyderabad')
    fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: isoDate(1) } })
    fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: isoDate(2) } })
    await user.type(screen.getByLabelText(/justification/i), 'Customer onboarding engagement')

    const file = makeFile('receipt.pdf')
    await user.upload(screen.getByLabelText('Documents'), file)
    expect(onUpload).toHaveBeenCalledWith(file)
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Create Travel Request' }))
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ documentIds: ['DOC-NEW'] }))
  })

  it('requires a confirmed upload when the active policy requires a document', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    const initialValues = validValues({ documents: [] })
    renderForm({ mode: 'edit', initialValues, documentsRequired: true, onSubmit })

    const submit = screen.getByRole('button', { name: 'Save Changes' })
    expect(await screen.findByText('A document is required by the active policy')).toHaveAttribute(
      'role',
      'alert',
    )
    expect(submit).toBeDisabled()

    await user.upload(screen.getByLabelText('Documents'), makeFile('receipt.pdf'))
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ documentIds: ['DOC-NEW'] }))
  })

  it('prefills in edit mode, submits changed values, and keeps existing documents', async () => {
    const user = userEvent.setup()
    const onSubmit = mockOnSubmit()
    const initialValues = validValues({ documents: [{ documentId: 'DOC-EXISTING' }] })
    renderForm({ mode: 'edit', initialValues, onSubmit })

    // Q11: form is pre-filled with the current values.
    expect(screen.getByLabelText(/destination/i)).toHaveValue('Mumbai')
    expect(screen.getByLabelText(/start date/i)).toHaveValue(isoDate(0))
    expect(screen.getByLabelText(/end date/i)).toHaveValue(isoDate(3))
    expect(screen.getByLabelText(/justification/i)).toHaveValue('Quarterly business review offsite')

    const submit = screen.getByRole('button', { name: 'Save Changes' })
    // Flush the mount-time resolver validation so it settles inside act.
    await waitFor(() => expect(submit).toBeEnabled())

    await user.clear(screen.getByLabelText(/destination/i))
    await user.type(screen.getByLabelText(/destination/i), 'Pune')
    await user.click(submit)

    // Same DTO shape as create (TravelRequestUpdateRequest — api-contract.md
    // §5.2); the previously uploaded document stays referenced (TR-01).
    expect(onSubmit).toHaveBeenCalledWith({
      destination: 'Pune',
      startDate: isoDate(0),
      endDate: isoDate(3),
      justification: 'Quarterly business review offsite',
      documentIds: ['DOC-EXISTING'],
    })
  })

  it('shows the prior rejection reason banner in resubmit mode', async () => {
    renderForm({
      mode: 'resubmit',
      initialValues: validValues(),
      rejectionReason: 'The requested dates exceed the approved travel budget.',
    })

    expect(screen.getByText('Prior rejection reason')).toBeInTheDocument()
    expect(
      screen.getByText('The requested dates exceed the approved travel budget.'),
    ).toBeInTheDocument()
    const submit = screen.getByRole('button', { name: 'Resubmit Travel Request' })
    // Flush the mount-time resolver validation so it settles inside act.
    await waitFor(() => expect(submit).toBeEnabled())
  })

  it('disables the submit button while the page request is in flight', async () => {
    const onSubmit = mockOnSubmit()
    const initialValues = validValues()
    const { rerender } = renderForm({ mode: 'edit', initialValues, submitting: true, onSubmit })

    const submit = screen.getByRole('button', { name: 'Save Changes' })
    await waitFor(() => expect(submit).toBeDisabled())

    rerender(
      <TravelRequestForm
        mode="edit"
        initialValues={initialValues}
        submitting={false}
        onUpload={mockUpload()}
        onSubmit={onSubmit}
      />,
    )
    await waitFor(() => expect(submit).toBeEnabled())
  })

  it('disables the submit button while a document upload is in progress', async () => {
    const user = userEvent.setup()
    const initialValues = validValues()
    // Upload that never settles → the file stays PENDING (frontend-spec.md §4.5).
    const onUpload = vi.fn().mockImplementation(() => new Promise<UploadedDocument>(() => {}))
    renderForm({ mode: 'edit', initialValues, onUpload })

    const submit = screen.getByRole('button', { name: 'Save Changes' })
    await waitFor(() => expect(submit).toBeEnabled())

    await user.upload(screen.getByLabelText('Documents'), makeFile('receipt.pdf'))
    await waitFor(() => expect(screen.getByText('PENDING')).toBeInTheDocument())
    await waitFor(() => expect(submit).toBeDisabled())
  })

  it('shows server field errors inline and non-attributable errors as a form banner', async () => {
    renderForm({
      mode: 'edit',
      initialValues: validValues(),
      serverFieldErrors: {
        destination: 'Destination is not in the approved city list.',
        documentIds: 'A receipt is required for this trip.',
      },
      formError: 'The request could not be saved. Please try again.',
    })

    // frontend-api-mapping.md §3: `destination` maps onto its FormField,
    // `documentIds` onto the upload section, everything else to the banner.
    expect(screen.getByText('Destination is not in the approved city list.')).toBeInTheDocument()
    expect(screen.getByText('A receipt is required for this trip.')).toBeInTheDocument()
    await screen.findByText('The request could not be saved. Please try again.')
  })
})
