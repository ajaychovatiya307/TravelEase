import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationResponse } from './notificationService'
import * as notificationService from './notificationService'

const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('./httpClient', () => ({
  get: http.get,
  post: http.post,
}))

const notification: NotificationResponse = {
  id: 101,
  entityType: 'TRAVEL_REQUEST',
  entityId: 25,
  notificationType: 'APPROVAL',
  recipientId: 7,
  message: 'Your travel request has been approved.',
  read: false,
  createdAt: '2026-10-10T10:30:00Z',
}

beforeEach(() => {
  http.get.mockReset()
  http.post.mockReset()
})

describe('notificationService', () => {
  it('requests the first page of notifications by default', async () => {
    const page = { content: [notification], page: 0, size: 20, totalElements: 1, totalPages: 1 }
    http.get.mockResolvedValueOnce(page)

    await expect(notificationService.list()).resolves.toEqual(page)
    expect(http.get).toHaveBeenCalledWith('/api/notifications', { page: 0, size: 20 })
  })

  it('passes an explicit page and size through', async () => {
    http.get.mockResolvedValueOnce({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 })

    await notificationService.list(2, 50)
    expect(http.get).toHaveBeenCalledWith('/api/notifications', { page: 2, size: 50 })
  })

  it('reads the server-side unread count', async () => {
    http.get.mockResolvedValueOnce({ unreadCount: 42 })

    await expect(notificationService.unreadCount()).resolves.toBe(42)
    expect(http.get).toHaveBeenCalledWith('/api/notifications/unread-count')
  })

  it('marks a notification as read without a request body', async () => {
    http.post.mockResolvedValueOnce({ ...notification, read: true })

    await expect(notificationService.markRead(101)).resolves.toMatchObject({ id: 101, read: true })
    expect(http.post).toHaveBeenCalledWith('/api/notifications/101/read')
  })
})
