import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  THEME_STORAGE_KEY,
  applyTheme,
  getSystemThemeQuery,
  readStoredTheme,
  resolveInitialTheme,
  writeStoredTheme,
} from '../theme/theme'
import type { Theme } from '../theme/theme'

interface ThemeContextValue {
  theme: Theme
  /** Sets and persists an explicit theme choice. */
  setTheme: (theme: Theme) => void
  toggleTheme: () => void
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined)

interface ThemeProviderProps {
  children: ReactNode
}

/**
 * Owns the active light/dark theme. The initial value matches what the index.html boot
 * script already applied, so there is no flash or re-render on load. Until the user makes
 * an explicit choice the theme follows OS changes; an explicit choice is persisted to
 * localStorage and synced across open tabs.
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(resolveInitialTheme)

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  // Follow the OS preference only while no explicit choice has been saved.
  useEffect(() => {
    const query = getSystemThemeQuery()
    if (!query) return undefined
    const handleChange = (event: MediaQueryListEvent) => {
      if (readStoredTheme() === null) setThemeState(event.matches ? 'dark' : 'light')
    }
    query.addEventListener('change', handleChange)
    return () => query.removeEventListener('change', handleChange)
  }, [])

  // Keep other open tabs in sync with a choice made here.
  useEffect(() => {
    const handleStorage = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY) setThemeState(resolveInitialTheme())
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  const setTheme = useCallback((next: Theme) => {
    writeStoredTheme(next)
    setThemeState(next)
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark')
  }, [theme, setTheme])

  const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [theme, setTheme, toggleTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- context hook co-located by convention
export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }
  return context
}
