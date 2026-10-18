import type { PaginatedResult } from '../models/pagination'
import { get, post } from './httpClient'

export type NotificationEntityType = 'TRAVEL_REQUEST' | 'EXPENSE_CLAIM'

export interface NotificationResponse {
  id: number
  entityType: NotificationEntityType
  entityId: number
  notificationType: string
  recipientId: number
  message: string
  read: boolean
  createdAt: string
}

/** Default page shown in the bell; the server caps `size` at 100 and sorts newest first. */
export const NOTIFICATION_PAGE_SIZE = 20

export function list(
  page = 0,
  size = NOTIFICATION_PAGE_SIZE,
): Promise<PaginatedResult<NotificationResponse>> {
  return get<PaginatedResult<NotificationResponse>>('/api/notifications', { page, size })
}

/** Number of unread notifications across all pages (not just the loaded one). */
export async function unreadCount(): Promise<number> {
  const result = await get<{ unreadCount: number }>('/api/notifications/unread-count')
  return result.unreadCount
}

export function markRead(id: number): Promise<NotificationResponse> {
  return post<NotificationResponse>(`/api/notifications/${id}/read`)
}
