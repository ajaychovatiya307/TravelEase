/**
 * J-06 — Unit tests for the Admin user account form (P-16).
 *
 * Mirrors the sibling admin form tests: a renderUserForm helper, a mocked
 * `onSubmit` promise, the shared USER_ACCOUNT_MESSAGES for all error copy,
 * shared literals defined once, and label-based field queries.
 */

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'

import { UserAccountForm, type UserAccountFormValues } from './UserAccountForm'

import type { UserAccountCreateRequest, UserAccountRequest } from '../../models/admin'
import { USER_ACCOUNT_MESSAGES } from '../../validation/adminSchemas'
import type { UserAccountFieldErrors } from '../../validation/adminSchemas'

const FULL_NAME = 'Ada Lovelace'
const EMAIL = 'ada@example.com'
const PASSWORD = 'InitialPassword123'
const INVALID_EMAIL = 'not-an-email'
const ROLE_EMPLOYEE = 'EMPLOYEE'
const ROLE_FINANCE = 'FINANCE'
const EDIT_FULL_NAME = 'Grace Hopper'
const EDIT_EMAIL = 'grace@example.com'

const fullNameInput = () => screen.getByLabelText(/^Full name/) as HTMLInputElement
const emailInput = () => screen.getByLabelText(/^Email/) as HTMLInputElement
const passwordInput = () => screen.getByLabelText(/^Password/) as HTMLInputElement
const rolesSelect = () => screen.getByLabelText(/^Roles/) as HTMLSelectElement
const activeSelect = () => screen.getByLabelText(/^Active/) as HTMLSelectElement
const submitButton = () => screen.getByTestId('user-account-form-submit')

function renderUserForm(
  overrides: {
    mode?: 'create' | 'edit'
    initialValues?: Omit<UserAccountFormValues, 'password'>
    onSubmit?: Mock<
      (payload: UserAccountCreateRequest | UserAccountRequest) => Promise<void>
    >
    serverFieldErrors?: UserAccountFieldErrors
  } = {},
) {
  const onSubmit =
    overrides.onSubmit ??
    vi.fn(async (payload: UserAccountCreateRequest | UserAccountRequest) => {
      void payload
    })
  const user = userEvent.setup()
  const view =
    overrides.mode === 'edit'
      ? render(
          <UserAccountForm
            mode="edit"
            initialValues={overrides.initialValues}
            onSubmit={onSubmit}
            serverFieldErrors={overrides.serverFieldErrors}
          />,
        )
      : render(
          <UserAccountForm
            mode="create"
            onSubmit={onSubmit}
            serverFieldErrors={overrides.serverFieldErrors}
          />,
        )
  return { ...view, user, onSubmit }
}

async function fillValidCreateForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(fullNameInput(), FULL_NAME)
  await user.type(emailInput(), EMAIL)
  await user.type(passwordInput(), PASSWORD)
  await user.selectOptions(rolesSelect(), [ROLE_EMPLOYEE])
}

describe('UserAccountForm', () => {
  it('blocks an empty submit with the required-field alerts and never calls onSubmit', async () => {
    const { user, onSubmit } = renderUserForm()

    await user.click(submitButton())

    await waitFor(() => {
      expect(screen.getByText(USER_ACCOUNT_MESSAGES.fullName)).not.toBeNull()
    })
    expect(screen.getByText(USER_ACCOUNT_MESSAGES.email)).not.toBeNull()
    expect(screen.getByText(USER_ACCOUNT_MESSAGES.roles)).not.toBeNull()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('requires at least one role, then accepts a single selected role', async () => {
    const { user, onSubmit } = renderUserForm()

    await user.type(fullNameInput(), FULL_NAME)
    await user.type(emailInput(), EMAIL)
    await user.type(passwordInput(), PASSWORD)
    await user.click(submitButton())

    expect(screen.getByText(USER_ACCOUNT_MESSAGES.roles)).not.toBeNull()
    expect(onSubmit).not.toHaveBeenCalled()

    await user.selectOptions(rolesSelect(), [ROLE_EMPLOYEE])
    await user.click(submitButton())

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      fullName: FULL_NAME,
      email: EMAIL,
      roles: [ROLE_EMPLOYEE],
      password: PASSWORD,
    })
  })

  it('rejects a malformed email with the email-format alert', async () => {
    const { user, onSubmit } = renderUserForm()

    await user.type(fullNameInput(), FULL_NAME)
    await user.type(emailInput(), INVALID_EMAIL)
    await user.selectOptions(rolesSelect(), [ROLE_EMPLOYEE])
    await user.click(submitButton())

    expect(screen.getByText(USER_ACCOUNT_MESSAGES.emailFormat)).not.toBeNull()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits a valid form once with the exact mapped request', async () => {
    const { user, onSubmit } = renderUserForm()

    await fillValidCreateForm(user)
    await user.selectOptions(activeSelect(), 'true')
    await user.click(submitButton())

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      fullName: FULL_NAME,
      email: EMAIL,
      roles: [ROLE_EMPLOYEE],
      password: PASSWORD,
      active: true,
    })
  })

  it('maps multiple selected roles in control order', async () => {
    const { user, onSubmit } = renderUserForm()

    await user.type(fullNameInput(), FULL_NAME)
    await user.type(emailInput(), EMAIL)
    await user.type(passwordInput(), PASSWORD)
    await user.selectOptions(rolesSelect(), [ROLE_EMPLOYEE, ROLE_FINANCE])
    await user.click(submitButton())

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      fullName: FULL_NAME,
      email: EMAIL,
      roles: [ROLE_EMPLOYEE, ROLE_FINANCE],
      password: PASSWORD,
    })
  })

  it('omits the active key from the payload when the flag is unset', async () => {
    const { user, onSubmit } = renderUserForm()

    await fillValidCreateForm(user)
    await user.click(submitButton())

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('active')
  })

  it('requires a password of at least eight characters for account creation', async () => {
    const { user, onSubmit } = renderUserForm()
    await user.type(fullNameInput(), FULL_NAME)
    await user.type(emailInput(), EMAIL)
    await user.type(passwordInput(), 'short')
    await user.selectOptions(rolesSelect(), [ROLE_EMPLOYEE])
    await user.click(submitButton())

    expect(screen.getByText(USER_ACCOUNT_MESSAGES.password)).not.toBeNull()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('prefills the form in edit mode, relabels submit, and maps edited values', async () => {
    const { user, onSubmit } = renderUserForm({
      mode: 'edit',
      initialValues: {
        fullName: EDIT_FULL_NAME,
        email: EDIT_EMAIL,
        roles: [ROLE_EMPLOYEE],
        active: true,
      },
    })

    expect(screen.getByRole('button', { name: 'Save changes' })).not.toBeNull()
    expect(screen.queryByLabelText(/^Password/)).toBeNull()
    expect(fullNameInput().value).toBe(EDIT_FULL_NAME)
    expect(emailInput().value).toBe(EDIT_EMAIL)
    expect(Array.from(rolesSelect().selectedOptions).map((option) => option.value)).toEqual([
      ROLE_EMPLOYEE,
    ])
    expect(activeSelect().value).toBe('true')

    await user.type(fullNameInput(), ' A')
    await user.click(submitButton())

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })
    expect(onSubmit).toHaveBeenCalledWith({
      fullName: `${EDIT_FULL_NAME} A`,
      email: EDIT_EMAIL,
      roles: [ROLE_EMPLOYEE],
      active: true,
    })
  })

  it('keeps the form open when the submit promise is rejected', async () => {
    const rejectedOnSubmit = vi.fn(async (
      _payload: UserAccountCreateRequest | UserAccountRequest,
    ): Promise<void> => {
      void _payload
      throw new Error('create user failed')
    })
    const { user } = renderUserForm({ onSubmit: rejectedOnSubmit })

    await fillValidCreateForm(user)
    await user.selectOptions(activeSelect(), 'true')
    await user.click(submitButton())

    await waitFor(() => {
      expect(rejectedOnSubmit).toHaveBeenCalledTimes(1)
    })
    expect(screen.getByTestId('user-account-form')).not.toBeNull()
  })

  it('renders server field errors inline for the matching field', () => {
    renderUserForm({ serverFieldErrors: { email: 'This email is already in use.' } })

    expect(screen.getByText('This email is already in use.')).toHaveAttribute('role', 'alert')
  })
})
