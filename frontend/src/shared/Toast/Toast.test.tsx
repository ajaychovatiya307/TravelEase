import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Toast, TOAST_AUTO_DISMISS_MS } from './Toast'

describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the toast message', () => {
    render(<Toast toast={{ id: '1', message: 'Saved', variant: 'success' }} onDismiss={vi.fn()} />)
    expect(screen.getByText('Saved')).toBeInTheDocument()
  })

  it('auto-dismisses after the fixed duration', () => {
    const onDismiss = vi.fn()
    render(
      <Toast toast={{ id: '1', message: 'Saved', variant: 'success' }} onDismiss={onDismiss} />,
    )

    vi.advanceTimersByTime(TOAST_AUTO_DISMISS_MS - 1)
    expect(onDismiss).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(onDismiss).toHaveBeenCalledWith('1')
  })
})
