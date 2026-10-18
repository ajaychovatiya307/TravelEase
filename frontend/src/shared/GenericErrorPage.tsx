import './FallbackPage.css'

/** Static fallback rendered by the top-level ErrorBoundary on unexpected rendering exceptions (ui-model.md §2). */
export function GenericErrorPage() {
  return (
    <div className="fallback-page" role="alert">
      <h1>Something Went Wrong</h1>
      <p>An unexpected error occurred. Please try again later.</p>
      <a className="fallback-page__link" href="/">
        Back to Dashboard
      </a>
    </div>
  )
}
