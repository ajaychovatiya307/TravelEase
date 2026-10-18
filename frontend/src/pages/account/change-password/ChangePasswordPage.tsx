import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '../../../context/AuthContext'
import { authService } from '../../../services/authService'
import { isClassifiedErrorInstance } from '../../../services/httpClient'
import { PASSWORD_RULES_HINT, validateNewPassword } from '../../../validation/passwordRules'
import { PasswordField } from '../../../components/account/PasswordField'
import './ChangePasswordPage.css'

type FieldName = 'currentPassword' | 'newPassword' | 'confirmPassword'

/**
 * Change password (P-24, #14). A successful change ends every session of the account on the server
 * (the token's credentials version no longer matches), so the user is signed out and asked to sign
 * in with the new password.
 */
export function ChangePasswordPage() {
  const { clearAuthState } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState<Record<FieldName, string>>({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  })
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  function update(field: FieldName, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
    setFormError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors: Partial<Record<FieldName, string>> = validateNewPassword(
      values.newPassword,
      values.confirmPassword,
      values.currentPassword,
    )
    if (values.currentPassword.length === 0) nextErrors.currentPassword = 'Current password is required.'
    setErrors(nextErrors)
    setFormError(null)
    if (Object.values(nextErrors).some((message) => message !== undefined)) return

    setIsSubmitting(true)
    try {
      await authService.changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      clearAuthState()
      navigate('/login?reason=password-changed', { replace: true })
    } catch (error) {
      if (isClassifiedErrorInstance(error) && error.category === 'VALIDATION_ERROR') {
        const serverErrors: Partial<Record<FieldName, string>> = {}
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'currentPassword' || fieldError.field === 'newPassword') {
            serverErrors[fieldError.field] = fieldError.message
          }
        }
        setErrors(serverErrors)
        if (Object.keys(serverErrors).length === 0) setFormError(error.message)
      } else {
        setFormError('Your password could not be changed. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="page change-password-page" aria-labelledby="change-password-title">
      <header className="page-header">
        <div>
          <h1 id="change-password-title">Change password</h1>
          <p>After the change you will be signed out everywhere and asked to sign in again.</p>
        </div>
      </header>
      <div className="card change-password-page__card">
        {formError ? (
          <p className="required" role="alert">
            {formError}
          </p>
        ) : null}
        <form className="change-password-page__form" noValidate onSubmit={handleSubmit}>
          <PasswordField
            id="current-password"
            label="Current password"
            autoComplete="current-password"
            value={values.currentPassword}
            error={errors.currentPassword}
            onChange={(value) => update('currentPassword', value)}
          />
          <PasswordField
            id="new-password"
            label="New password"
            autoComplete="new-password"
            hint={PASSWORD_RULES_HINT}
            value={values.newPassword}
            error={errors.newPassword}
            onChange={(value) => update('newPassword', value)}
          />
          <PasswordField
            id="confirm-password"
            label="Confirm new password"
            autoComplete="new-password"
            value={values.confirmPassword}
            error={errors.confirmPassword}
            onChange={(value) => update('confirmPassword', value)}
          />
          <button className="button button-primary" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Changing password…' : 'Change password'}
          </button>
        </form>
      </div>
    </section>
  )
}
