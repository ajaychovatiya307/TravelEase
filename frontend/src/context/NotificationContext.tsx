import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

export type ToastVariant = 'success' | 'info' | 'warning' | 'error'

export interface Toast {
  id: string
  message: string
  variant: ToastVariant
}

export interface NotificationState {
  toasts: Toast[]
}

const DEFAULT_STATE: NotificationState = { toasts: [] }

interface NotificationContextValue {
  notificationState: NotificationState
  /** Appends a toast to the queue; returns the generated toast id. */
  enqueueToast: (message: string, variant?: ToastVariant) => string
  /** Removes a toast from the queue, e.g. on auto-dismiss or manual close. */
  dismissToast: (id: string) => void
}

const NotificationContext = createContext<NotificationContextValue | undefined>(undefined)

interface NotificationProviderProps {
  children: ReactNode
}

/**
 * Holds the single, app-wide toast queue (ui-model.md §9). Mounted once via AppProviders
 * so every mutation flow enqueues onto the same state regardless of which page triggered it.
 * Visual rendering of the queue is owned by the shared Toast/NotificationHost component (C-06).
 */
export function NotificationProvider({ children }: NotificationProviderProps) {
  const [notificationState, setNotificationState] = useState<NotificationState>(DEFAULT_STATE)

  const enqueueToast = useCallback((message: string, variant: ToastVariant = 'info') => {
    const id = crypto.randomUUID()
    setNotificationState((prev) => ({ toasts: [...prev.toasts, { id, message, variant }] }))
    return id
  }, [])

  const dismissToast = useCallback((id: string) => {
    setNotificationState((prev) => ({
      toasts: prev.toasts.filter((toast) => toast.id !== id),
    }))
  }, [])

  const value = useMemo(
    () => ({ notificationState, enqueueToast, dismissToast }),
    [notificationState, enqueueToast, dismissToast],
  )

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- context hook co-located by convention
export function useNotifications(): NotificationContextValue {
  const context = useContext(NotificationContext)
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider')
  }
  return context
}
