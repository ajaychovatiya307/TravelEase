import type { PageId } from './routeTable'

interface PlaceholderPageProps {
  pageId: PageId
  path: string
}

/**
 * Minimal stand-in for a feature page that Section I has not implemented yet
 * (frontend-tasks.md D-03, part 1).
 *
 * Presentational only: renders the page ID, the route path, and a
 * "placeholder" marker so routing tests and manual smoke checks can tell the
 * stand-in apart from a real screen. No styling, no data access, no hooks.
 *
 * Section I (feature screens) replaces placeholder usage for each pageId by
 * registering the real page element in `pageRegistry` (pageRegistry.tsx) —
 * `resolvePage` then returns the real page instead of this component, so
 * placeholder usage is replaced page by page without touching the route
 * wiring in Router.tsx.
 */
export function PlaceholderPage({ pageId, path }: PlaceholderPageProps) {
  return (
    <div data-page-id={pageId}>
      <h1>{pageId}</h1>
      <p>Placeholder for {path} — replaced by the real feature screen in Section I.</p>
    </div>
  )
}