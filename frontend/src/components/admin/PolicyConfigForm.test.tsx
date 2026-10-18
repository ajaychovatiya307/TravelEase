// Unit tests for the admin policy config form (J-08, screen P-18) — mirrors
// the ExpenseCategoryForm test harness: a host-provided `onSubmit` stand-in,
// userEvent-driven interactions, and error copy asserted only through the
// named L-04 message table (POLICY_CONFIG_MESSAGES, adminSchemas.ts).

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import {
  PolicyConfigForm,
  type PolicyConfigFormProps,
  type PolicyConfigFormValues,
} from './PolicyConfigForm'
import { POLICY_CONFIG_MESSAGES } from '../../validation/adminSchemas'

const CATEGORY_CODE = 'MEALS'
const THRESHOLD = 50
const CURRENCY = 'USD'
const NEGATIVE = '-5'
const EDIT_VALUES: PolicyConfigFormValues = {
  categoryCode: 'HOTEL',
  receiptThresholdAmount: 250,
  currency: 'EUR',
  active: true,
}
const EDIT_THRESHOLD = 300
const SERVER_CATEGORY_CODE_ERROR = 'Category code is already in use'

function renderPolicyForm(overrides: Partial<PolicyConfigFormProps> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const user = userEvent.setup()
  const view = render(<PolicyConfigForm mode="create" onSubmit={onSubmit} {...overrides} />)
  return { onSubmit, user, ...view }
}

const thresholdInput = () => screen.getByLabelText(/^Receipt threshold amount/)

describe('PolicyConfigForm', () => {
  it('blocks an empty submit with the schema messages and never calls onSubmit', async () => {
    const { onSubmit, user } = renderPolicyForm()

    await user.click(screen.getByRole('button', { name: /create policy/i }))

    await waitFor(() => {
      expect(
        screen
          .getAllByRole('alert')
          .some((node) => node.textContent?.includes(POLICY_CONFIG_MESSAGES.categoryCode)),
      ).toBe(true)
    })
    const alertTexts = screen.getAllByRole('alert').map((node) => node.textContent ?? '')
    expect(
      alertTexts.some((text) => text.includes(POLICY_CONFIG_MESSAGES.receiptThresholdAmount)),
    ).toBe(true)
    expect(alertTexts.some((text) => text.includes(POLICY_CONFIG_MESSAGES.currency))).toBe(true)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('blocks a negative receipt threshold amount', async () => {
    const { onSubmit, user } = renderPolicyForm()

    await user.type(screen.getByLabelText(/^Category code/), CATEGORY_CODE)
    await user.type(screen.getByLabelText(/^Currency/), CURRENCY)
    await user.type(thresholdInput(), NEGATIVE)
    await user.click(screen.getByRole('button', { name: /create policy/i }))

    await waitFor(() => {
      expect(
        screen
          .getAllByRole('alert')
          .some((node) =>
            node.textContent?.includes(POLICY_CONFIG_MESSAGES.receiptThresholdNonNegative),
          ),
      ).toBe(true)
    })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits the trimmed payload with active=true when Active is selected', async () => {
    const { onSubmit, user } = renderPolicyForm()

    await user.type(screen.getByLabelText(/^Category code/), CATEGORY_CODE)
    await user.type(thresholdInput(), String(THRESHOLD))
    await user.type(screen.getByLabelText(/^Currency/), CURRENCY)
    await user.selectOptions(screen.getByLabelText(/^Active/), 'true')
    await user.click(screen.getByRole('button', { name: /create policy/i }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      categoryCode: 'MEALS',
      receiptThresholdAmount: 50,
      currency: 'USD',
      active: true,
    })
  })

  it('omits active from the payload when the select is left unset', async () => {
    const { onSubmit, user } = renderPolicyForm()

    await user.type(screen.getByLabelText(/^Category code/), CATEGORY_CODE)
    await user.type(thresholdInput(), String(THRESHOLD))
    await user.type(screen.getByLabelText(/^Currency/), CURRENCY)
    await user.click(screen.getByRole('button', { name: /create policy/i }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      categoryCode: 'MEALS',
      receiptThresholdAmount: 50,
      currency: 'USD',
    })
  })

  it('prefills edit mode from initialValues and submits the edited payload', async () => {
    const { onSubmit, user } = renderPolicyForm({ mode: 'edit', initialValues: EDIT_VALUES })

    expect(screen.getByLabelText(/^Category code/)).toHaveValue('HOTEL')
    expect(thresholdInput()).toHaveValue(250)
    expect(screen.getByLabelText(/^Currency/)).toHaveValue('EUR')
    expect(screen.getByLabelText(/^Active/)).toHaveValue('true')
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument()

    await user.clear(thresholdInput())
    await user.type(thresholdInput(), String(EDIT_THRESHOLD))
    await user.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      categoryCode: 'HOTEL',
      receiptThresholdAmount: 300,
      currency: 'EUR',
      active: true,
    })
  })

  it('renders the server field error for categoryCode', () => {
    renderPolicyForm({ serverFieldErrors: { categoryCode: SERVER_CATEGORY_CODE_ERROR } })

    expect(screen.getByRole('alert')).toHaveTextContent(SERVER_CATEGORY_CODE_ERROR)
  })

  it('leaves the form open when the host submit rejects', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'))
    const { user } = renderPolicyForm({ onSubmit })

    await user.type(screen.getByLabelText(/^Category code/), CATEGORY_CODE)
    await user.type(thresholdInput(), String(THRESHOLD))
    await user.type(screen.getByLabelText(/^Currency/), CURRENCY)
    await user.click(screen.getByRole('button', { name: /create policy/i }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(screen.getByTestId('policy-config-form')).toBeInTheDocument()
  })
})
