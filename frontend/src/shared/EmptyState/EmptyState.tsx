import type { ReactNode } from 'react'
import './EmptyState.css'

interface EmptyStateProps {
  message: string
  icon?: ReactNode
}

/** No-results indicator shown when a list's data.content is empty (ui-model.md §10). */
export function EmptyState({ message, icon }: EmptyStateProps) {
  return (
    <div className="empty-state">
      {icon ? (
        <span className="empty-state__icon" aria-hidden="true">
          {icon}
        </span>
      ) : null}
      <p className="empty-state__message">{message}</p>
    </div>
  )
}
