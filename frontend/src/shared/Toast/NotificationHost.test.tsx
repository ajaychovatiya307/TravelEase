import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { NotificationProvider, useNotifications } from '../../context/NotificationContext'
import { NotificationHost } from './NotificationHost'

function Harness() {
  const { enqueueToast } = useNotifications()
  const [count, setCount] = useState(0)
  return (
    <div>
      <button
        onClick={() => {
          enqueueToast(`Saved ${count}`, 'success')
          setCount((c) => c + 1)
        }}
      >
        trigger
      </button>
      <NotificationHost />
    </div>
  )
}

describe('NotificationHost', () => {
  it('renders nothing when the queue is empty', () => {
    const { container } = render(
      <NotificationProvider>
        <NotificationHost />
      </NotificationProvider>,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('renders a toast enqueued via NotificationContext', async () => {
    const user = userEvent.setup()
    render(
      <NotificationProvider>
        <Harness />
      </NotificationProvider>,
    )
    await user.click(screen.getByText('trigger'))
    expect(screen.getByText('Saved 0')).toBeInTheDocument()
  })
})
