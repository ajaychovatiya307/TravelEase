import { useNotifications } from '../../context/NotificationContext'
import { Toast } from './Toast'
import './Toast.css'

/**
 * Single top-level renderer of the app-wide toast queue (ui-model.md §10); mounted once
 * in AppProviders so every mutation flow's toasts render through the same host.
 */
export function NotificationHost() {
  const {
    notificationState: { toasts },
    dismissToast,
  } = useNotifications()

  if (toasts.length === 0) {
    return null
  }

  return (
    <div className="toast-host" aria-live="polite">
      {toasts.map((toast) => (
        <Toast key={toast.id} toast={toast} onDismiss={dismissToast} />
      ))}
    </div>
  )
}
