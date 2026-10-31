import type { ReactNode } from 'react'

interface AuthLayoutProps {
  children: ReactNode
}

/**
 * Minimal shell used by the Login page only — no sidebar/top bar (frontend-clarification.md Q6).
 * The application is light mode only, so no theme toggle is rendered.
 */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <main id="main-content" className="auth-layout">
      {children}
    </main>
  )
}
