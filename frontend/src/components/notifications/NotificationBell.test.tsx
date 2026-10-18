import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationResponse } from '../../services/notificationService'
import { NotificationBell } from './NotificationBell'

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

function page(content: NotificationResponse[], totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: totalElements > 0 ? 1 : 0 }
}

const notifications: NotificationResponse[] = [
  {
    id: 101,
    entityType: 'TRAVEL_REQUEST',
    entityId: 25,
    notificationType: 'APPROVAL',
    recipientId: 7,
    message: 'Your travel request has been approved.',
    read: false,
    createdAt: '2026-10-10T10:30:00Z',
  },
  {
    id: 102,
    entityType: 'EXPENSE_CLAIM',
    entityId: 31,
    notificationType: 'REVIEW',
    recipientId: 7,
    message: 'Your expense claim needs attention.',
    read: true,
    createdAt: '2026-10-11T10:30:00Z',
  },
]

function CurrentPath() {
  const location = useLocation()
  return <span data-testid="current-path">{location.pathname}</span>
}

function renderBell() {
  return render(
    <MemoryRouter>
      <NotificationBell />
      <CurrentPath />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  notificationApi.list.mockReset()
  notificationApi.unreadCount.mockReset()
  notificationApi.markRead.mockReset()
  notificationApi.list.mockResolvedValue(page(notifications))
  notificationApi.unreadCount.mockResolvedValue(1)
  notificationApi.markRead.mockImplementation(async (id: number) => {
    const notification = notifications.find((item) => item.id === id)
    if (!notification) throw new Error('Notification not found')
    return { ...notification, read: true }
  })
})

describe('NotificationBell', () => {
  it('loads notifications, shows unread count, sorts newest first, and closes on outside click', async () => {
    const user = userEvent.setup()
    renderBell()

    const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' })
    expect(within(bell).getByText('1')).toBeVisible()
    await user.click(bell)

    const panel = screen.getByRole('region', { name: 'Notifications' })
    const items = within(panel).getAllByRole('button')
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Your expense claim needs attention.'),
      expect.stringContaining('Your travel request has been approved.'),
    ])
    expect(notificationApi.list).toHaveBeenCalledTimes(1)
    expect(notificationApi.unreadCount).toHaveBeenCalledTimes(2)

    fireEvent.pointerDown(document.body)
    expect(screen.queryByRole('region', { name: 'Notifications' })).not.toBeInTheDocument()
  })

  it('marks unread notifications through the API before navigating and updates the count', async () => {
    const user = userEvent.setup()
    renderBell()

    await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
    await user.click(
      await screen.findByRole('button', {
        name: 'Unread: Your travel request has been approved.',
      }),
    )

    await waitFor(() => {
      expect(notificationApi.markRead).toHaveBeenCalledWith(101)
      expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument()
      expect(screen.queryByText('1')).not.toBeInTheDocument()
      expect(screen.getByTestId('current-path')).toHaveTextContent('/travel-requests/25')
    })
    expect(screen.queryByRole('region', { name: 'Notifications' })).not.toBeInTheDocument()
  })

  it('does not call the read endpoint for an already-read notification', async () => {
    const user = userEvent.setup()
    renderBell()

    await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
    await user.click(
      await screen.findByRole('button', {
        name: 'Read: Your expense claim needs attention.',
      }),
    )

    expect(notificationApi.markRead).not.toHaveBeenCalled()
    expect(screen.getByTestId('current-path')).toHaveTextContent('/expenses/31')
  })

  it('shows an empty state when no notifications are returned', async () => {
    const user = userEvent.setup()
    notificationApi.list.mockResolvedValue(page([]))
    notificationApi.unreadCount.mockResolvedValue(0)
    renderBell()
    await user.click(await screen.findByRole('button', { name: 'Notifications' }))
    expect(await screen.findByText('No notifications yet')).toBeInTheDocument()
  })

  it('shows a loading state while notifications are fetched', async () => {
    const user = userEvent.setup()
    notificationApi.list.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(page(notifications)), 20)),
    )
    renderBell()
    await user.click(screen.getByRole('button', { name: 'Notifications' }))
    expect(screen.getByRole('status')).toHaveTextContent('Loading notifications…')
    expect(await screen.findByText('Your travel request has been approved.')).toBeInTheDocument()
  })

  it('shows an error state when the notification API fails', async () => {
    const user = userEvent.setup()
    notificationApi.list.mockRejectedValue(new Error('offline'))
    renderBell()
    await user.click(screen.getByRole('button', { name: 'Notifications' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load notifications.')
  })

  it('reports a failed mark-as-read action and leaves the notification unread', async () => {
    const user = userEvent.setup()
    notificationApi.list.mockResolvedValue(page(notifications))
    notificationApi.markRead.mockRejectedValue(new Error('offline'))
    renderBell()
    await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
    await user.click(
      await screen.findByRole('button', {
        name: 'Unread: Your travel request has been approved.',
      }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not mark this notification as read.',
    )
    expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  })

  it('shows the server unread count even when unread items are beyond the loaded page', async () => {
    notificationApi.unreadCount.mockResolvedValue(37)
    renderBell()

    expect(await screen.findByRole('button', { name: 'Notifications, 37 unread' })).toBeInTheDocument()
    expect(notificationApi.list).not.toHaveBeenCalled()
  })

  it('says when only the most recent page is shown', async () => {
    const user = userEvent.setup()
    notificationApi.list.mockResolvedValue(page(notifications, 120))
    renderBell()

    await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
    expect(await screen.findByText('Showing the 2 most recent.')).toBeInTheDocument()
  })
})
