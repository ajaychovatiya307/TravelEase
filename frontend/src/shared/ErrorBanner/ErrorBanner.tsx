import './ErrorBanner.css'

export type ErrorBannerVariant = 'error' | 'warning' | 'conflict' | 'policy'

interface ErrorBannerProps {
  message: string
  variant?: ErrorBannerVariant
  onDismiss?: () => void
}

/**
 * Persistent error/warning banner (frontend-plan.md §11): stays visible until the caller
 * stops rendering it (triggering condition cleared) or the user dismisses it explicitly.
 */
export function ErrorBanner({ message, variant = 'error', onDismiss }: ErrorBannerProps) {
  return (
    <div className={`error-banner error-banner--${variant}`} role="alert">
      <span className="error-banner__message">{message}</span>
      {onDismiss ? (
        <button type="button" className="error-banner__dismiss" onClick={onDismiss}>
          Dismiss
        </button>
      ) : null}
    </div>
  )
}
