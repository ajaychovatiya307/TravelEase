// ExpenseCategoryForm component tests (J-09, frontend-tasks.md; screen P-19).
// Mirrors the sibling Section-J form verification (J-04
// ExpenseReviewForm.test.tsx harness; J-03 ExpenseClaimForm.test.tsx field
// queries): `code`/`label` are required (L-04 expenseCategorySchema) and a
// valid submit maps to the exact `ExpenseCategoryRequest` payload
// (api-contract.md §5.15). `onSubmit` is the form/host boundary and is
// mocked here — the POST /api/admin/expense-categories call belongs to the
// host page (I-14, frontend-constitution.md §3).
//
// Conventions (mirroring ExpenseReviewForm.test.tsx):
// - the error copy is asserted through the imported `EXPENSE_CATEGORY_MESSAGES`
//   constant, never hardcoded (L-04 is the single source of truth).
// - the fields render through the shared FormField primitives (no
//   data-testid passthrough), so they are driven by label — mirroring
//   ExpenseClaimForm.test.tsx's `getByLabelText` queries.
// - repeated literals live in the shared constants below so inputs and
//   assertions can never drift apart.

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { EXPENSE_CATEGORY_MESSAGES } from '../../validation/adminSchemas'
import {
  ExpenseCategoryForm,
  type ExpenseCategoryFormProps,
  type ExpenseCategoryFormValues,
} from './ExpenseCategoryForm'

// Shared literals (defined once — no duplicate strings across assertions).
const CODE = 'MEALS'
const LABEL = 'Meals'
const EDIT_CODE = 'HOTEL'
const EDIT_LABEL = 'Hotel stay'
const EDITED_LABEL = 'Hotel stay (edited)'
const EDIT_VALUES: ExpenseCategoryFormValues = { code: EDIT_CODE, label: EDIT_LABEL, active: true }
const SERVER_CODE_ERROR = 'The category code already exists (server).'

/** Mounts the form with a mocked `onSubmit` (the host-page boundary). */
function renderCategoryForm(overrides: Partial<ExpenseCategoryFormProps> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const user = userEvent.setup()
  const renderResult = render(
    <ExpenseCategoryForm mode="create" onSubmit={onSubmit} {...overrides} />,
  )
  return { user, onSubmit, ...renderResult }
}

describe('ExpenseCategoryForm', () => {
  it('blocks an empty submit with inline required errors for code and label', async () => {
    const { user, onSubmit } = renderCategoryForm()

    await user.click(screen.getByTestId('expense-category-form-submit'))

    await waitFor(() => {
      const codeError = screen.getByText(EXPENSE_CATEGORY_MESSAGES.code)
      expect(codeError).toHaveAttribute('role', 'alert')
    })
    expect(screen.getByText(EXPENSE_CATEGORY_MESSAGES.label)).toHaveAttribute('role', 'alert')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits the exact ExpenseCategoryRequest payload with the entered values', async () => {
    const { user, onSubmit } = renderCategoryForm()

    await user.type(screen.getByLabelText(/^Code/), CODE)
    await user.type(screen.getByLabelText(/^Label/), LABEL)
    await user.selectOptions(screen.getByLabelText(/^Active/), 'true')

    await user.click(screen.getByTestId('expense-category-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({ code: CODE, label: LABEL, active: true })
  })

  it('omits active from the payload when the flag is left unset (server defaults it to true)', async () => {
    const { user, onSubmit } = renderCategoryForm()

    await user.type(screen.getByLabelText(/^Code/), CODE)
    await user.type(screen.getByLabelText(/^Label/), LABEL)

    await user.click(screen.getByTestId('expense-category-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({ code: CODE, label: LABEL })
  })

  it('prefills initialValues in edit mode and maps the edited values on submit', async () => {
    const { user, onSubmit } = renderCategoryForm({
      mode: 'edit',
      initialValues: EDIT_VALUES,
    })

    expect(screen.getByLabelText(/^Code/)).toHaveValue(EDIT_CODE)
    expect(screen.getByLabelText(/^Label/)).toHaveValue(EDIT_LABEL)
    expect(screen.getByLabelText(/^Active/)).toHaveValue('true')
    expect(screen.getByTestId('expense-category-form-submit')).toHaveTextContent('Save changes')

    await user.clear(screen.getByLabelText(/^Label/))
    await user.type(screen.getByLabelText(/^Label/), EDITED_LABEL)

    await user.click(screen.getByTestId('expense-category-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({ code: EDIT_CODE, label: EDITED_LABEL, active: true })
  })

  it('renders server field errors through the same inline path', () => {
    renderCategoryForm({
      serverFieldErrors: { code: SERVER_CODE_ERROR },
    })

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent(SERVER_CODE_ERROR)
  })

  it('leaves the form open when the host page rejects the submit', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'))
    const user = userEvent.setup()
    render(<ExpenseCategoryForm mode="create" onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText(/^Code/), CODE)
    await user.type(screen.getByLabelText(/^Label/), LABEL)

    await user.click(screen.getByTestId('expense-category-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(screen.getByTestId('expense-category-form')).toBeInTheDocument()
  })
})
