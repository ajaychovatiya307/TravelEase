import type { ReactNode } from 'react'
import { AuthProvider } from '../context/AuthContext'
import { NotificationProvider } from '../context/NotificationContext'
import { ThemeProvider } from '../context/ThemeContext'
import { useAuthRequestInterceptor } from '../services/interceptors/authRequestInterceptor'
import { useErrorResponseInterceptor } from '../services/interceptors/errorResponseInterceptor'
import { NotificationHost } from '../shared/Toast'

interface AppProvidersProps {
  children: ReactNode
}

/**
 * Registers BOTH Section H interceptors for the app's lifetime: the auth
 * request interceptor (H-01, bearer-token injection) and the error response
 * interceptor (H-02, error classification + centralized session-expiry
 * redirect). Rendered inside `AuthProvider` (both hooks need `useAuth`);
 * renders nothing itself.
 */
function HttpInterceptorsRegistrar(): null {
  useAuthRequestInterceptor()
  useErrorResponseInterceptor()
  return null
}

/**
 * Composes global context providers around the application (ui-model.md §10).
 * Providers are added here incrementally as each context is implemented
 * (AuthContext in B-02, NotificationContext in B-03). ThemeProvider is outermost
 * so every surface, including error and login screens, follows the active theme.
 */
export function AppProviders({ children }: AppProvidersProps) {
  return (
    <ThemeProvider>
      <AuthProvider>
        <HttpInterceptorsRegistrar />
        <NotificationProvider>
          {children}
          <NotificationHost />
        </NotificationProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}

