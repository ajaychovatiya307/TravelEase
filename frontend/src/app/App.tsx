import Router from '../routes/Router'
import { AppProviders } from './AppProviders'
import { ErrorBoundary } from './ErrorBoundary'

/**
 * Root component. Composition order per ui-model.md §10:
 * AppProviders -> ErrorBoundary -> Router.
 * The router region is the default `Router` export from `src/routes/Router`
 * (BrowserRouter wrapping the guarded route tree, frontend-tasks.md D-03).
 */
function App() {
  return (
    <AppProviders>
      <ErrorBoundary>
        <Router />
      </ErrorBoundary>
    </AppProviders>
  )
}

export default App
