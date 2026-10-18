import './FallbackPage.css'

/** Static fallback for authenticated-but-unauthorized access (ui-model.md §2, frontend-clarification.md Q5). */
export function AccessDeniedPage() {
  return (
    <div className="fallback-page">
      <h1>Access Denied</h1>
      <p>You do not have permission to view this page.</p>
      <a className="fallback-page__link" href="/">
        Back to Dashboard
      </a>
    </div>
  )
}
