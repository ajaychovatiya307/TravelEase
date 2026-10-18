import { useEffect } from 'react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { RoleType } from '../../context/AuthContext'
import { RoleGate } from './RoleGate'

function Harness({ roles, allowedRoles }: { roles: RoleType[]; allowedRoles: RoleType[] }) {
  const { setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({ userId: 'U1', roles, isAuthenticated: true, sessionExpiresAt: null, token: null })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <RoleGate allowedRoles={allowedRoles}>
      <div>Secret content</div>
    </RoleGate>
  )
}

describe('RoleGate', () => {
  it('renders children when the session holds an allowed role', () => {
    render(
      <AuthProvider>
        <Harness roles={['MANAGER']} allowedRoles={['MANAGER', 'FINANCE']} />
      </AuthProvider>,
    )
    expect(screen.getByText('Secret content')).toBeInTheDocument()
  })

  it('renders nothing when the session holds no allowed role', () => {
    const { container } = render(
      <AuthProvider>
        <Harness roles={['EMPLOYEE']} allowedRoles={['MANAGER', 'FINANCE']} />
      </AuthProvider>,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
