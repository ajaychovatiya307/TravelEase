import { AccessDeniedPage } from './AccessDeniedPage'
import { ErrorBanner } from './ErrorBanner'
import { GenericErrorPage } from './GenericErrorPage'
import { NotFoundPage } from './NotFoundPage'
import type { ClassifiedErrorInstance } from '../services/httpClient'

interface ResourceErrorStateProps {
  error: ClassifiedErrorInstance
  onRetry?: () => void
  resourceName?: string
}

/**
 * Maps a normalized fetch error to the standard page treatment from
 * frontend-api-mapping.md §4. Raw server messages are intentionally not
 * rendered because they are not a stable user-facing contract.
 */
export function ResourceErrorState({ error, onRetry, resourceName = 'Data' }: ResourceErrorStateProps) {
  if (error.category === 'RESOURCE_NOT_FOUND') {
    return <NotFoundPage />
  }

  if (error.category === 'FORBIDDEN_ACTION') {
    return <AccessDeniedPage />
  }

  if (error.category === 'NETWORK_ERROR') {
    return (
      <ErrorBanner
        message={`${resourceName} could not be loaded. Please try again.`}
        onDismiss={onRetry}
      />
    )
  }

  if (error.category === 'INTERNAL_ERROR') {
    return <GenericErrorPage />
  }

  const messageByCategory: Record<string, string> = {
    CONFLICT: 'The requested data changed. Please try again.',
    DUPLICATE_RESOURCE: 'This resource already exists.',
    POLICY_VIOLATION: 'This request violates a policy and could not be completed.',
    VALIDATION_ERROR: 'The data could not be loaded because it is invalid.',
  }

  return (
    <ErrorBanner
      message={messageByCategory[error.category] ?? `${resourceName} could not be loaded. Please try again.`}
      onDismiss={onRetry}
    />
  )
}
