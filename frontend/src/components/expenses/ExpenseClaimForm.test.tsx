// ExpenseClaimForm component tests (J-03, frontend-tasks.md; screens P-11/P-12).
// Mounts the real form with `useAdminResources` fully mocked: static loaded
// ServerDataState fixtures for two ACTIVE categories (MEALS, HOTEL) plus the
// per-category policy configs (MEALS threshold 100, HOTEL threshold 250), so
// no network calls happen and the VAL-10 conditional document requirement is
// deterministic per test (entered amount vs. the selected category's
// threshold). The component looks the threshold up by `categoryCode` in the
// mocked `content`, so the mock can ignore the query-state argument.
//
// Conventions:
// - the date field is driven with `fireEvent.change` (user-event date typing
//   is flaky in jsdom); every other interaction uses user-event.
// - the document-error copy is asserted through the imported
//   `EXPENSE_CLAIM_MESSAGES` constant, never hardcoded (L-03 is the single
//   source of truth).
// - `makeFile` mirrors the VAL-11 fixture helper from
//   FileUploadControl.test.tsx.

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { UploadedDocument } from '../../shared/FileUploadControl'
import { EXPENSE_CLAIM_MESSAGES } from '../../validation/expenseClaimSchema'
import type { ExpenseClaimFormValues } from '../../validation/expenseClaimSchema'
import { ExpenseClaimForm, type ExpenseClaimFormProps } from './ExpenseClaimForm'

/**
 * Static loaded fixtures for the mocked admin-resource lookups
 * (frontend-api-mapping.md §1.3 P-11/P-12): the ACTIVE category list and the
 * policy config of each category.
 */
const adminResourceFixtures = vi.hoisted(() => ({
  expenseCategories: {
    data: {
      content: [
        { id: 'cat-1', code: 'MEALS', label: 'Meals', active: true },
        { id: 'cat-2', code: 'HOTEL', label: 'Hotel', active: true },
      ],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch: () => undefined,
  },
  policyConfigs: {
    data: {
      content: [
        {
          id: 'policy-1',
          categoryCode: 'MEALS',
          receiptThresholdAmount: 100,
          currency: 'USD',
          active: true,
        },
        {
          id: 'policy-2',
          categoryCode: 'HOTEL',
          receiptThresholdAmount: 250,
          currency: 'USD',
          active: true,
        },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
    },
    isLoading: false,
    error: null,
    refetch: () => undefined,
  },
}))

vi.mock('../../hooks/useAdminResources', () => ({
  useExpenseCategories: () => adminResourceFixtures.expenseCategories,
  usePolicyConfigs: () => adminResourceFixtures.policyConfigs,
}))

/** The CONFIRMED document every mocked `onUploadDocument` resolves with. */
const confirmedDocument: UploadedDocument = {
  documentId: 'doc-1',
  fileName: 'receipt.pdf',
  uploadStatus: 'CONFIRMED',
}

/** VAL-11 fixture helper — mirrors FileUploadControl.test.tsx `makeFile`. */
function makeFile(name: string, type: string, sizeBytes: number): File {
  const file = new File(['x'.repeat(Math.min(sizeBytes, 10))], name, { type })
  Object.defineProperty(file, 'size', { value: sizeBytes })
  return file
}

/**
 * Mounts the form in create mode with controllable host-page mocks; callers
 * override any prop (e.g. `mode: 'edit'` + `initialValues`). `onUploadDocument`
 * always resolves the CONFIRMED `doc-1` fixture.
 */
function renderClaimForm(overrides: Partial<ExpenseClaimFormProps> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const onUploadDocument = vi.fn().mockResolvedValue(confirmedDocument)
  const user = userEvent.setup()
  const renderResult = render(
    <ExpenseClaimForm
      mode="create"
      travelRequestId="tr-100"
      onUploadDocument={onUploadDocument}
      onSubmit={onSubmit}
      {...overrides}
    />,
  )
  return { user, onSubmit, onUploadDocument, ...renderResult }
}

/** Enters the plain form fields (date via `fireEvent.change` — see header note). */
async function enterClaimFields(
  user: ReturnType<typeof userEvent.setup>,
  fields: { amount: string; category: string; currency?: string; expenseDate?: string },
) {
  await user.type(screen.getByTestId('expense-claim-form-amount'), fields.amount)
  await user.type(screen.getByTestId('expense-claim-form-currency'), fields.currency ?? 'USD')
  await user.selectOptions(screen.getByTestId('expense-claim-form-category'), fields.category)
  fireEvent.change(screen.getByTestId('expense-claim-form-expense-date'), {
    target: { value: fields.expenseDate ?? '2026-06-10' },
  })
}

describe('ExpenseClaimForm', () => {
  it('blocks submit when required claim fields are missing', async () => {
    const { user, onSubmit } = renderClaimForm({ travelRequestId: '' })

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => {
      expect(screen.getByText(EXPENSE_CLAIM_MESSAGES.travelRequestId)).toBeInTheDocument()
      expect(screen.getByText(EXPENSE_CLAIM_MESSAGES.amountRequired)).toBeInTheDocument()
      expect(screen.getByText(EXPENSE_CLAIM_MESSAGES.currency)).toBeInTheDocument()
      expect(screen.getByText(EXPENSE_CLAIM_MESSAGES.categoryRequired)).toBeInTheDocument()
      expect(screen.getByText(EXPENSE_CLAIM_MESSAGES.expenseDateRequired)).toBeInTheDocument()
    })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('blocks submit with a document error when the amount exceeds the category threshold', async () => {
    const { user, onSubmit } = renderClaimForm()
    await enterClaimFields(user, { amount: '150', category: 'MEALS' })

    expect(screen.getByTestId('expense-claim-form-documents-required-hint')).toBeInTheDocument()

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => {
      const documentError = screen.getByTestId('expense-claim-form-document-error')
      expect(documentError).toHaveAttribute('role', 'alert')
      expect(documentError).toHaveTextContent(EXPENSE_CLAIM_MESSAGES.documentIds)
    })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits with documentIds after the required document upload is CONFIRMED', async () => {
    const { user, onSubmit, onUploadDocument } = renderClaimForm()
    await enterClaimFields(user, { amount: '150', category: 'MEALS' })

    const file = makeFile('receipt.pdf', 'application/pdf', 1024)
    await user.upload(screen.getByLabelText('Documents (required)'), file)
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onUploadDocument).toHaveBeenCalledWith(file)
    expect(onSubmit).toHaveBeenCalledWith({
      travelRequestId: 'tr-100',
      amount: 150,
      currency: 'USD',
      category: 'MEALS',
      expenseDate: '2026-06-10',
      documentIds: ['doc-1'],
    })
  })

  it('submits below the threshold without a documentIds key', async () => {
    const { user, onSubmit } = renderClaimForm()
    await enterClaimFields(user, { amount: '80', category: 'MEALS' })

    expect(
      screen.queryByTestId('expense-claim-form-documents-required-hint'),
    ).not.toBeInTheDocument()

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      travelRequestId: 'tr-100',
      amount: 80,
      currency: 'USD',
      category: 'MEALS',
      expenseDate: '2026-06-10',
    })
  })

  it('treats the exact threshold amount as not requiring a document', async () => {
    const { user, onSubmit } = renderClaimForm()
    await enterClaimFields(user, { amount: '100', category: 'MEALS' })

    expect(
      screen.queryByTestId('expense-claim-form-documents-required-hint'),
    ).not.toBeInTheDocument()

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      travelRequestId: 'tr-100',
      amount: 100,
      currency: 'USD',
      category: 'MEALS',
      expenseDate: '2026-06-10',
    })
  })

  it('maps the amount to 4 decimals and upper-cases the currency (TR-06)', async () => {
    const { user, onSubmit } = renderClaimForm()
    await enterClaimFields(user, { amount: '123.45678', category: 'HOTEL', currency: 'usd ' })

    expect(
      screen.queryByTestId('expense-claim-form-documents-required-hint'),
    ).not.toBeInTheDocument()

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      travelRequestId: 'tr-100',
      amount: 123.4568,
      currency: 'USD',
      category: 'HOTEL',
      expenseDate: '2026-06-10',
    })
  })

  it('toggles the required hint as the amount crosses the threshold', async () => {
    const { user } = renderClaimForm()
    const amountInput = screen.getByTestId('expense-claim-form-amount')
    const requiredHint = () => screen.queryByTestId('expense-claim-form-documents-required-hint')
    await user.selectOptions(screen.getByTestId('expense-claim-form-category'), 'MEALS')

    await user.type(amountInput, '90')
    expect(requiredHint()).not.toBeInTheDocument()

    await user.clear(amountInput)
    await user.type(amountInput, '150')
    expect(requiredHint()).toBeInTheDocument()

    await user.clear(amountInput)
    await user.type(amountInput, '50')
    expect(requiredHint()).not.toBeInTheDocument()
  })

  it('prefills every field in edit mode and submits with the prefilled documentIds', async () => {
    const initialValues: ExpenseClaimFormValues = {
      travelRequestId: 'tr-200',
      amount: 150.5,
      currency: 'EUR',
      category: 'HOTEL',
      expenseDate: '2026-06-15',
      documentIds: ['doc-existing'],
    }
    const { user, onSubmit } = renderClaimForm({
      mode: 'edit',
      travelRequestId: 'tr-200',
      initialValues,
    })

    expect(screen.getByTestId('expense-claim-form-travel-request')).toHaveValue('tr-200')
    expect(screen.getByTestId('expense-claim-form-amount')).toHaveValue(150.5)
    expect(screen.getByTestId('expense-claim-form-currency')).toHaveValue('EUR')
    expect(screen.getByTestId('expense-claim-form-category')).toHaveValue('HOTEL')
    expect(screen.getByTestId('expense-claim-form-expense-date')).toHaveValue('2026-06-15')

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      travelRequestId: 'tr-200',
      amount: 150.5,
      currency: 'EUR',
      category: 'HOTEL',
      expenseDate: '2026-06-15',
      documentIds: ['doc-existing'],
    })
  })

  it('keeps the prefilled documentIds and appends newly confirmed uploads in edit mode', async () => {
    const initialValues: ExpenseClaimFormValues = {
      travelRequestId: 'tr-200',
      amount: 150.5,
      currency: 'EUR',
      category: 'HOTEL',
      expenseDate: '2026-06-15',
      documentIds: ['doc-existing'],
    }
    const { user, onSubmit } = renderClaimForm({
      mode: 'edit',
      travelRequestId: 'tr-200',
      initialValues,
    })

    const file = makeFile('extra-receipt.pdf', 'application/pdf', 512)
    await user.upload(screen.getByLabelText('Documents'), file)
    await waitFor(() => expect(screen.getByText('CONFIRMED')).toBeInTheDocument())

    await user.click(screen.getByTestId('expense-claim-form-submit'))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      travelRequestId: 'tr-200',
      amount: 150.5,
      currency: 'EUR',
      category: 'HOTEL',
      expenseDate: '2026-06-15',
      documentIds: ['doc-existing', 'doc-1'],
    })
  })

  it('renders server field errors inline for the matching field', () => {
    renderClaimForm({ serverFieldErrors: { amount: 'The amount exceeds the allowed limit.' } })

    expect(screen.getByText('The amount exceeds the allowed limit.')).toHaveAttribute(
      'role',
      'alert',
    )
  })
})
