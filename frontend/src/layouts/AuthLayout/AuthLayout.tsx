import type { ReactNode } from 'react'
import { ThemeToggle } from '../../components/theme/ThemeToggle'

interface AuthLayoutProps {
  children: ReactNode
}

/**
 * Minimal shell used by the Login page only — no sidebar/top bar (frontend-clarification.md Q6).
 * The theme toggle floats in the top-right corner since there is no header to host it.
 */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <>
      <ThemeToggle className="theme-toggle--floating" />
      <main id="main-content" className="auth-layout">
        {children}
      </main>
    </>
  )
}
