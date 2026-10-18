import './LoadingSkeleton.css'

export type LoadingSkeletonShape = 'table' | 'detail' | 'form'

interface LoadingSkeletonProps {
  shape: LoadingSkeletonShape
  /** Number of placeholder lines; sensible defaults are applied per shape when omitted. */
  rows?: number
}

const DEFAULT_ROWS: Record<LoadingSkeletonShape, number> = {
  table: 5,
  detail: 4,
  form: 3,
}

/** Initial-load placeholder shown while a page's ServerDataState.isLoading is true (ui-model.md §10). */
export function LoadingSkeleton({ shape, rows }: LoadingSkeletonProps) {
  const lineCount = rows ?? DEFAULT_ROWS[shape]
  return (
    <div
      className={`loading-skeleton loading-skeleton--${shape}`}
      role="status"
      aria-label="Loading"
    >
      {Array.from({ length: lineCount }, (_, index) => (
        <div key={index} className="loading-skeleton__line" />
      ))}
    </div>
  )
}
