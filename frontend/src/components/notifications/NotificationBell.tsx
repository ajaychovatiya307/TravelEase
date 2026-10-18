import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useOptionalAuth } from '../../context/AuthContext'
import * as notificationService from '../../services/notificationService'
import type { NotificationResponse } from '../../services/notificationService'
import './NotificationBell.css'

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function notificationPath(notification: NotificationResponse): string | null {
  const id = encodeURIComponent(String(notification.entityId))
  if (notification.entityType === 'TRAVEL_REQUEST') return `/travel-requests/${id}`
  if (notification.entityType === 'EXPENSE_CLAIM') return `/expenses/${id}`
  return null
}

/** How often the unread badge is refreshed while the app is open. */
export const UNREAD_REFRESH_INTERVAL_MS = 60_000

function newestFirst(notifications: NotificationResponse[]): NotificationResponse[] {
  return [...notifications].sort(
    (left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
  )
}

function notificationIcon(notification: NotificationResponse): 'banknote' | 'check' {
  const text = `${notification.notificationType} ${notification.message}`.toLowerCase()
  return text.includes('reimbursed') ? 'banknote' : 'check'
}

export function NotificationBell() {
  const [notifications, setNotifications] = useState<NotificationResponse[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [isOpen, setIsOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [actionError, setActionError] = useState(false)
  const [markingIds, setMarkingIds] = useState<Set<number>>(() => new Set())
  const rootRef = useRef<HTMLDivElement>(null)
  const markingIdsRef = useRef(new Set<number>())
  const requestIdRef = useRef(0)
  const navigate = useNavigate()
  // Background polls stop once the session token has expired, so they cannot trigger the
  // session-expired redirect while the user is re-authenticating in place (#16).
  const sessionExpiresAt = useOptionalAuth()?.authState.sessionExpiresAt ?? null
  const sessionExpiresAtRef = useRef(sessionExpiresAt)
  useEffect(() => {
    sessionExpiresAtRef.current = sessionExpiresAt
  }, [sessionExpiresAt])

  // The badge comes from the server count, so unread items beyond the loaded page are included.
  const refreshUnreadCount = useCallback(async () => {
    try {
      setUnreadCount(await notificationService.unreadCount())
    } catch {
      // keep the last known count; the list view reports load failures
    }
  }, [])

  const loadNotifications = useCallback(async () => {
    const requestId = ++requestIdRef.current
    setIsLoading(true)
    setLoadError(false)
    try {
      const result = await notificationService.list()
      if (requestId === requestIdRef.current) {
        setNotifications(newestFirst(result.content))
        setHasMore(result.totalElements > result.content.length)
      }
    } catch {
      if (requestId === requestIdRef.current) setLoadError(true)
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    let isCurrent = true
    const refresh = () => {
      const expiresAt = sessionExpiresAtRef.current
      if (expiresAt !== null && Date.parse(expiresAt) <= Date.now()) return
      void notificationService
        .unreadCount()
        .then((count) => {
          if (isCurrent) setUnreadCount(count)
        })
        .catch(() => {
          // a transient failure leaves the badge unchanged
        })
    }
    refresh()
    const timer = window.setInterval(refresh, UNREAD_REFRESH_INTERVAL_MS)
    return () => {
      isCurrent = false
      window.clearInterval(timer)
      // drop any list load still in flight
      requestIdRef.current += 1
    }
  }, [])

  useEffect(() => {
    if (!isOpen) return

    function handlePointerDown(event: PointerEvent) {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)) {
        setIsOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  function toggleDropdown() {
    const nextOpen = !isOpen
    setIsOpen(nextOpen)
    if (nextOpen) {
      setActionError(false)
      void loadNotifications()
      void refreshUnreadCount()
    }
  }

  async function handleNotificationClick(notification: NotificationResponse) {
    if (markingIdsRef.current.has(notification.id)) return
    if (!notification.read) {
      markingIdsRef.current.add(notification.id)
      setMarkingIds(new Set(markingIdsRef.current))
      setActionError(false)
      try {
        const updated = await notificationService.markRead(notification.id)
        setNotifications((current) =>
          current.map((item) => (item.id === notification.id ? updated : item)),
        )
        setUnreadCount((count) => Math.max(0, count - 1))
      } catch {
        setActionError(true)
        return
      } finally {
        markingIdsRef.current.delete(notification.id)
        setMarkingIds(new Set(markingIdsRef.current))
      }
    }

    const path = notificationPath(notification)
    setIsOpen(false)
    if (path !== null) navigate(path)
  }

  return (
    <div className="notifications notification-bell" ref={rootRef}>
      <button
        type="button"
        className="button icon-button notification-bell__trigger"
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        aria-expanded={isOpen}
        aria-controls="notifications-panel"
        onClick={toggleDropdown}
      >
        <i className="icon icon-large icon-bell" />
        {unreadCount > 0 && (
          <span className="notification-bell__count" aria-hidden="true">
            {unreadCount}
          </span>
        )}
      </button>
      {isOpen && (
        <section
          id="notifications-panel"
          className="notifications-panel"
          aria-label="Notifications"
        >
          <h2>Notifications</h2>
          {isLoading ? (
            <p className="empty-state" role="status">
              Loading notifications…
            </p>
          ) : loadError ? (
            <div className="empty-state" role="alert">
              <p className="required">Could not load notifications.</p>
              <button
                className="button button-secondary"
                type="button"
                onClick={() => void loadNotifications()}
              >
                Try again
              </button>
            </div>
          ) : notifications.length === 0 ? (
            <p className="empty-state">No notifications yet</p>
          ) : (
            <ul>
              {notifications.map((notification) => {
                const icon = notificationIcon(notification)
                return (
                  <li
                    key={notification.id}
                    className="notification"
                    role="button"
                    tabIndex={markingIds.has(notification.id) ? -1 : 0}
                    aria-label={`${notification.read ? 'Read' : 'Unread'}: ${notification.message}`}
                    onClick={() => void handleNotificationClick(notification)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault()
                        void handleNotificationClick(notification)
                      }
                    }}
                    aria-disabled={markingIds.has(notification.id)}
                  >
                    <span
                      className={`notification-icon ${icon === 'banknote' ? 'status-reimbursed' : 'status-approved'}`}
                    >
                      <i className={`icon icon-${icon}`} />
                    </span>
                    <div>
                      <p className="notification-text">{notification.message}</p>
                      <time dateTime={notification.createdAt}>
                        {formatTimestamp(notification.createdAt)}
                      </time>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
          {hasMore && !isLoading && !loadError && (
            <p className="notification-bell__more">Showing the {notifications.length} most recent.</p>
          )}
          {actionError && (
            <p className="required" role="alert">
              Could not mark this notification as read. Please try again.
            </p>
          )}
        </section>
      )}
    </div>
  )
}
