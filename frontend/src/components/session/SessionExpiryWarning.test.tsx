import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../context/AuthContext'
import { authService } from '../../services/authService'
import { SessionExpiryWarning } from './SessionExpiryWarning'

vi.mock('../../services/authService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/authService')>()),
  authService: { login: vi.fn() },
}))

const mockedLogin = vi.mocked(authService.login)

function makeToken(sub: string, secondsLeft: number) {
  const encode = (text: string) => btoa(text).replace(/=+$/, '')
  const exp = Math.floor(Date.now() / 1000) + secondsLeft
  return `${encode('{"alg":"HS256"}')}.${encode(JSON.stringify({ sub, roles: ['EMPLOYEE'], exp }))}.sig`
}

/** Stands in for a page with an unsaved form: its state must survive re-authentication. */
function DraftForm() {
  const [draft, setDraft] = useState('')
  const { authState } = useAuth()
  return (
    <>
      <label>
        Draft
        <input value={draft} onChange={(event) => setDraft(event.target.value)} />
      </label>
      <span data-testid="token">{authState.token}</span>
    </>
  )
}

function renderWithSession(secondsLeft: number, sub = '7') {
  sessionStorage.setItem('travelease.auth.token', makeToken(sub, secondsLeft))
  return render(
    <AuthProvider>
      <SessionExpiryWarning />
      <DraftForm />
    </AuthProvider>,
  )
}

beforeEach(() => {
  mockedLogin.mockReset()
  sessionStorage.clear()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('SessionExpiryWarning (#16)', () => {
  it('stays hidden while the session has more than five minutes left', () => {
    renderWithSession(30 * 60)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('warns shortly before expiry and re-authenticates in place, keeping unsaved input', async () => {
    const user = userEvent.setup()
    const renewed = makeToken('7', 3600)
    mockedLogin.mockResolvedValueOnce({ token: renewed })
    renderWithSession(4 * 60)

    await user.type(screen.getByLabelText('Draft'), 'half-written justification')
    expect(screen.getByRole('alert')).toHaveTextContent('Your session expires in 4 minutes')

    await user.click(screen.getByRole('button', { name: 'Stay signed in' }))
    await user.type(screen.getByLabelText('Email'), 'emp@example.com')
    await user.type(screen.getByLabelText('Password'), 'Emp-pass-2026')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByTestId('token')).toHaveTextContent(renewed)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Draft')).toHaveValue('half-written justification')
  })

  it('refuses a token issued to a different user', async () => {
    const user = userEvent.setup()
    mockedLogin.mockResolvedValueOnce({ token: makeToken('99', 3600) })
    renderWithSession(4 * 60)

    await user.click(screen.getByRole('button', { name: 'Stay signed in' }))
    await user.type(screen.getByLabelText('Email'), 'other@example.com')
    await user.type(screen.getByLabelText('Password'), 'Other-pass-2026')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Sign in with the account that is currently in use.')).toBeInTheDocument()
    expect(sessionStorage.getItem('travelease.auth.token')).not.toContain(makeToken('99', 3600).split('.')[1])
  })

  it('blocks the page with a sign-in dialog once the session has expired', () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    renderWithSession(2)

    act(() => {
      vi.advanceTimersByTime(3000)
    })

    expect(screen.getByRole('dialog', { name: 'Your session has expired' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Go to sign-in page' })).toBeInTheDocument()
  })
})
