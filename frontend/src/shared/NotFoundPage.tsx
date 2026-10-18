import './FallbackPage.css'

/** Static fallback for an unmatched route or missing record (ui-model.md §2). */
export function NotFoundPage() {
  return (
    <div className="fallback-page">
      <h1>Not Found</h1>
      <p>The page or record you are looking for could not be found.</p>
      <a className="fallback-page__link" href="/">
        Back to Dashboard
      </a>
    </div>
  )
}
