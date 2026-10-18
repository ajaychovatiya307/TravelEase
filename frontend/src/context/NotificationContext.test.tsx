import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { NotificationProvider, useNotifications } from './NotificationContext'

function NotificationProbe() {
  const { notificationState, enqueueToast, dismissToast } = useNotifications()
  return (
    <div>
      <span data-testid="count">{notificationState.toasts.length}</span>
      <ul>
        {notificationState.toasts.map((toast) => (
          <li key={toast.id} data-testid="toast">
            {toast.variant}: {toast.message}
          </li>
        ))}
      </ul>
      <button onClick={() => enqueueToast('Saved successfully', 'success')}>enqueue</button>
      <button
        onClick={() => {
          const id = notificationState.toasts[0]?.id
          if (id) dismissToast(id)
        }}
      >
        dismiss-first
      </button>
    </div>
  )
}

describe('NotificationContext', () => {
  it('starts with an empty toast queue', () => {
    render(
      <NotificationProvider>
        <NotificationProbe />
      </NotificationProvider>,
    )
    expect(screen.getByTestId('count')).toHaveTextContent('0')
  })

  it('enqueueToast appends a toast to the queue', async () => {
    const user = userEvent.setup()
    render(
      <NotificationProvider>
        <NotificationProbe />
      </NotificationProvider>,
    )
    await user.click(screen.getByText('enqueue'))
    expect(screen.getByTestId('count')).toHaveTextContent('1')
    expect(screen.getByTestId('toast')).toHaveTextContent('success: Saved successfully')
  })

  it('dismissToast removes a toast from the queue', async () => {
    const user = userEvent.setup()
    render(
      <NotificationProvider>
        <NotificationProbe />
      </NotificationProvider>,
    )
    await user.click(screen.getByText('enqueue'))
    await user.click(screen.getByText('dismiss-first'))
    expect(screen.getByTestId('count')).toHaveTextContent('0')
  })
})
