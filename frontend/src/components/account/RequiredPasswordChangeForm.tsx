import { useState } from 'react'
import type { FormEvent } from 'react'

import { authService, TOO_MANY_REQUESTS } from '../../services/authService'
import { isClassifiedErrorInstance } from '../../services/httpClient'
import { PASSWORD_RULES_HINT, validateNewPassword } from '../../validation/passwordRules'
import { PasswordField } from './PasswordField'

interface RequiredPasswordChangeFormProps {
  username: string
  temporaryPassword: string
  /** Called with the new password once the server accepted it. */
  onChanged: (newPassword: string) => void | Promise<void>
  onCancel: () => void
}

/**
 * Shown at sign-in when the account holds an administrator-issued temporary password (#14):
 * the user must choose their own password before a session is issued.
 */
export function RequiredPasswordChangeForm({
  username,
  temporaryPassword,
  onChanged,
  onCancel,
}: RequiredPasswordChangeFormProps) {
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errors, setErrors] = useState<{ newPassword?: string; confirmPassword?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = validateNewPassword(newPassword, confirmPassword, temporaryPassword)
    setErrors(nextErrors)
    setFormError(null)
    if (nextErrors.newPassword !== undefined || nextErrors.confirmPassword !== undefined) return

    setIsSubmitting(true)
    try {
      await authService.completePasswordChange({ username, temporaryPassword, newPassword })
      await onChanged(newPassword)
    } catch (error) {
      if (isClassifiedErrorInstance(error) && error.errorCode === TOO_MANY_REQUESTS) {
        setFormError('Too many attempts. Please wait a few minutes and try again.')
      } else if (isClassifiedErrorInstance(error) && error.category === 'VALIDATION_ERROR') {
        const fieldError = error.fieldErrors.find((item) => item.field === 'newPassword')
        if (fieldError) {
          setErrors({ newPassword: fieldError.message })
        } else {
          setFormError('Your temporary password is no longer valid. Sign in again or ask an administrator for a new one.')
        }
      } else {
        setFormError('Your password could not be changed. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <div>
        <h1>Set a new password</h1>
        <p className="login-intro">
          Your password was reset by an administrator. Choose your own password to continue.
        </p>
      </div>
      {formError ? (
        <p className="required" role="alert" style={{ fontSize: 13 }}>
          {formError}
        </p>
      ) : null}
      <form className="login-form" noValidate onSubmit={handleSubmit}>
        <PasswordField
          id="required-new-password"
          label="New password"
          autoComplete="new-password"
          hint={PASSWORD_RULES_HINT}
          value={newPassword}
          error={errors.newPassword}
          onChange={(value) => {
            setNewPassword(value)
            setErrors((current) => ({ ...current, newPassword: undefined }))
          }}
        />
        <PasswordField
          id="required-confirm-password"
          label="Confirm new password"
          autoComplete="new-password"
          value={confirmPassword}
          error={errors.confirmPassword}
          onChange={(value) => {
            setConfirmPassword(value)
            setErrors((current) => ({ ...current, confirmPassword: undefined }))
          }}
        />
        <button className="button button-primary button-block" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : 'Save password and sign in'}
        </button>
        <button className="button button-secondary button-block" type="button" onClick={onCancel}>
          Back to sign in
        </button>
      </form>
    </>
  )
}
