import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { RoleType } from '../../context/AuthContext'
import { ThemeProvider } from '../../context/ThemeContext'
import { AuthenticatedLayout } from './AuthenticatedLayout'

const notificationApi = vi.hoisted(() => ({
  list: vi.fn(),
  unreadCount: vi.fn(),
  markRead: vi.fn(),
}))

vi.mock('../../services/notificationService', () => ({
  list: notificationApi.list,
  unreadCount: notificationApi.unreadCount,
  markRead: notificationApi.markRead,
}))

beforeEach(() => {
  notificationApi.list.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  notificationApi.unreadCount.mockResolvedValue(0)
})

function AuthenticatedLayoutHarness({
  children,
  initialPath = '/',
  roles = [],
}: {
  children: ReactNode
  initialPath?: string
  roles?: RoleType[]
}) {
  return (
    <MemoryRouter initialEntries={[initialPath]}>
      <ThemeProvider>
        <AuthProvider>
          <SeededSession roles={roles}>
            <AuthenticatedLayout>{children}</AuthenticatedLayout>
          </SeededSession>
        </AuthProvider>
      </ThemeProvider>
    </MemoryRouter>
  )
}

function SeededSession({ children, roles }: { children: ReactNode; roles: RoleType[] }) {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'U1',
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: null,
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return authState.isAuthenticated ? children : null
}

describe('AuthenticatedLayout', () => {
  it('renders a sidebar region, a top bar region, and the child in the content outlet', () => {
    render(
      <AuthenticatedLayoutHarness>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    expect(screen.getByRole('complementary', { name: 'Primary navigation' })).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveTextContent('Page content')
  })

  it('renders a logout action that clears the session', () => {
    render(
      <AuthenticatedLayoutHarness>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    expect(screen.getByRole('button', { name: 'Logout' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument()
  })

  it.each([
    { roles: ['EMPLOYEE'] as RoleType[] },
    { roles: ['MANAGER'] as RoleType[] },
    { roles: ['FINANCE'] as RoleType[] },
    { roles: ['EMPLOYEE', 'MANAGER'] as RoleType[] },
  ])('shows notifications to authenticated roles $roles', ({ roles }) => {
    render(
      <AuthenticatedLayoutHarness roles={roles}>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument()
  })

  it.each([
    {
      role: 'EMPLOYEE' as const,
      labels: ['Dashboard', 'Travel Requests', 'Expenses'],
    },
    {
      role: 'MANAGER' as const,
      labels: ['Dashboard', 'Travel Requests', 'Travel Approvals'],
    },
    {
      role: 'FINANCE' as const,
      labels: [
        'Dashboard',
        'Travel Requests',
        'Travel Approvals',
        'Expenses',
        'Expense Reviews',
        'Audit Log',
      ],
    },
    {
      role: 'ADMIN' as const,
      labels: [
        'Dashboard',
        'Travel Requests',
        'Expenses',
        'Users',
        'Employee-Manager Mappings',
        'Policy Configuration',
        'Expense Categories',
        'Audit Log',
      ],
    },
  ])('shows only the routes allowed for $role', ({ role, labels }) => {
    render(
      <AuthenticatedLayoutHarness roles={[role]}>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    const navigation = screen.getByRole('navigation', { name: 'Dashboard sections' })
    expect(Array.from(navigation.querySelectorAll('a'), (link) => link.textContent)).toEqual(labels)
  })

  it('combines navigation routes for users with multiple roles', () => {
    render(
      <AuthenticatedLayoutHarness roles={['EMPLOYEE', 'MANAGER']}>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    const navigation = screen.getByRole('navigation', { name: 'Dashboard sections' })
    expect(Array.from(navigation.querySelectorAll('a'), (link) => link.textContent)).toEqual([
      'Dashboard',
      'Travel Requests',
      'Travel Approvals',
      'Expenses',
    ])
  })

  it('marks only the exact current route as active', () => {
    render(
      <AuthenticatedLayoutHarness initialPath="/expenses" roles={['EMPLOYEE']}>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    expect(screen.getByRole('link', { name: 'Expenses' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current')
  })

  it('activates Expense Reviews without also activating Expenses for Finance', () => {
    render(
      <AuthenticatedLayoutHarness initialPath="/expenses/review-queue" roles={['FINANCE']}>
        <div>Page content</div>
      </AuthenticatedLayoutHarness>,
    )

    expect(screen.getByRole('link', { name: 'Expense Reviews' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(screen.getByRole('link', { name: 'Expenses' })).not.toHaveAttribute('aria-current')
  })
})
