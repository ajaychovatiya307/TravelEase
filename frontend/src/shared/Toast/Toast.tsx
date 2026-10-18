import { useEffect } from 'react'
import type { Toast as ToastData } from '../../context/NotificationContext'
import './Toast.css'

// "A few seconds" per frontend-clarification.md Q31.
export const TOAST_AUTO_DISMISS_MS = 5000

interface ToastProps {
  toast: ToastData
  onDismiss: (id: string) => void
}

/** A single transient success/info confirmation; auto-dismisses after a fixed duration (Q31). */
export function Toast({ toast, onDismiss }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(toast.id), TOAST_AUTO_DISMISS_MS)
    return () => clearTimeout(timer)
  }, [toast.id, onDismiss])

  return (
    <div className={`toast toast--${toast.variant}`} role="status">
      {toast.message}
    </div>
  )
}
