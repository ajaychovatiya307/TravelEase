import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'

/**
 * Mode switch for the mocked `src/routes/Router` (frontend-tasks.md D-03,
 * part 2). `vi.hoisted` keeps the reference reachable from the hoisted
 * `vi.mock` factory:
 * - 'marker' renders a marker node that consumes AuthContext, proving the
 *   Router region is mounted inside `AppProviders` (`useAuth` throws outside
 *   an `AuthProvider`),
 * - 'crash' throws during render, proving the Router region is wrapped by
 *   the `ErrorBoundary` (a crash there must produce the GenericErrorPage
 *   fallback, not a blank screen or an uncaught render error).
 */
const mockRouterState = vi.hoisted(() => ({ mode: 'marker' as 'marker' | 'crash' }))

vi.mock('../routes/Router', async () => {
  const { useAuth } = await import('../context/AuthContext')

  function RouterMarker() {
    const { authState } = useAuth()
    return (
      <div data-testid="app-router">
        <span data-testid="app-router-auth">{String(authState.isAuthenticated)}</span>
      </div>
    )
  }

  function CrashingRouter(): never {
    throw new Error('Simulated Router crash')
  }

  return {
    default: function Router() {
      return mockRouterState.mode === 'crash' ? <CrashingRouter /> : <RouterMarker />
    },
  }
})

describe('App (D-03 composition: AppProviders -> ErrorBoundary -> Router)', () => {
  beforeEach(() => {
    mockRouterState.mode = 'marker'
    // React logs caught rendering errors to console.error (ErrorBoundary);
    // suppress the expected noise in test output (same pattern as
    // ErrorBoundary.test.tsx).
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders without crashing and mounts the Router region inside the providers', () => {
    render(<App />)
    expect(screen.getByTestId('app-router')).toBeInTheDocument()
    // The marker reads AuthContext, which only resolves inside AppProviders,
    // and observes the default unauthenticated session state.
    expect(screen.getByTestId('app-router-auth')).toHaveTextContent('false')
  })

  it('catches a Router render crash with the ErrorBoundary instead of a blank screen', () => {
    mockRouterState.mode = 'crash'
    render(<App />)
    expect(screen.getByRole('alert')).toHaveTextContent('Something Went Wrong')
    expect(screen.queryByTestId('app-router')).not.toBeInTheDocument()
  })
})
