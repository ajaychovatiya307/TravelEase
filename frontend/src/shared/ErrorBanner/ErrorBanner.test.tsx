import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorBanner } from './ErrorBanner'

describe('ErrorBanner', () => {
  it('renders persistently with the provided message', () => {
    render(<ErrorBanner message="Already approved" />)
    expect(screen.getByRole('alert')).toHaveTextContent('Already approved')
  })

  it('renders no dismiss action when onDismiss is not provided', () => {
    render(<ErrorBanner message="Already approved" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('invokes onDismiss when the dismiss action is clicked', async () => {
    const user = userEvent.setup()
    const onDismiss = vi.fn()
    render(<ErrorBanner message="Already approved" onDismiss={onDismiss} />)
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(onDismiss).toHaveBeenCalled()
  })
})
