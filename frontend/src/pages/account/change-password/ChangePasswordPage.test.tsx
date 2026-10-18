import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import { authService } from '../../../services/authService'
import { toClassifiedError } from '../../../services/httpClient'
import { ChangePasswordPage } from './ChangePasswordPage'

vi.mock('../../../services/authService', () => ({
  authService: { changePassword: vi.fn() },
}))

const mockedChange = vi.mocked(authService.changePassword)

function token() {
  const encode = (text: string) => btoa(text).replace(/=+$/, '')
  const exp = Math.floor(Date.now() / 1000) + 3600
  return `${encode('{"alg":"HS256"}')}.${encode(JSON.stringify({ sub: '7', roles: ['EMPLOYEE'], exp }))}.sig`
}

function LoginProbe() {
  const { authState } = useAuth()
  const location = useLocation()
  return (
    <div>
      <span data-testid="location">{`${location.pathname}${location.search}`}</span>
      <span data-testid="authenticated">{String(authState.isAuthenticated)}</span>
    </div>
  )
}

function renderPage() {
  sessionStorage.setItem('travelease.auth.token', token())
  return render(
    <MemoryRouter initialEntries={['/account/password']}>
      <AuthProvider>
        <Routes>
          <Route path="/account/password" element={<ChangePasswordPage />} />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function fill(current: string, next: string, confirm: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/^Current password/), current)
  await user.type(screen.getByLabelText(/^New password/), next)
  await user.type(screen.getByLabelText(/^Confirm new password/), confirm)
  await user.click(screen.getByRole('button', { name: 'Change password' }))
}

beforeEach(() => {
  mockedChange.mockReset()
  sessionStorage.clear()
})

describe('ChangePasswordPage (P-24, #14)', () => {
  it('validates locally before calling the API', async () => {
    renderPage()
    await fill('Old-pass-2025', 'short', 'different')

    expect(await screen.findByText(/between 10 and 72 characters/)).toBeInTheDocument()
    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
    expect(mockedChange).not.toHaveBeenCalled()
  })

  it('signs the user out and returns to login after a successful change', async () => {
    mockedChange.mockResolvedValueOnce(undefined)
    renderPage()
    await fill('Old-pass-2025', 'New-pass-2026', 'New-pass-2026')

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/login?reason=password-changed'),
    )
    expect(mockedChange).toHaveBeenCalledWith({
      currentPassword: 'Old-pass-2025',
      newPassword: 'New-pass-2026',
    })
    expect(screen.getByTestId('authenticated')).toHaveTextContent('false')
    expect(sessionStorage.getItem('travelease.auth.token')).toBeNull()
  })

  it('shows a wrong current password next to that field', async () => {
    mockedChange.mockRejectedValueOnce(
      toClassifiedError({
        category: 'VALIDATION_ERROR',
        message: 'Validation failed',
        status: 400,
        errorCode: 'VALIDATION_ERROR',
        timestamp: null,
        fieldErrors: [{ field: 'currentPassword', message: 'The current password is incorrect' }],
      }),
    )
    renderPage()
    await fill('Wrong-pass-1', 'New-pass-2026', 'New-pass-2026')

    expect(await screen.findByText('The current password is incorrect')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Current password/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('reports an unexpected failure without signing out', async () => {
    mockedChange.mockRejectedValueOnce(new Error('offline'))
    renderPage()
    await fill('Old-pass-2025', 'New-pass-2026', 'New-pass-2026')

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be changed')
    expect(sessionStorage.getItem('travelease.auth.token')).not.toBeNull()
  })
})
