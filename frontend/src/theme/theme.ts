// Theme preference helpers shared by ThemeProvider and its tests.
// The inline boot script in index.html mirrors THEME_STORAGE_KEY and the resolution
// order below (saved preference -> OS preference) so the correct theme is applied
// before first paint; keep the two in sync.

export type Theme = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'travelease-theme'

const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)'

function isTheme(value: unknown): value is Theme {
  return value === 'light' || value === 'dark'
}

/** Returns the explicitly saved theme, or null when the user has never chosen one. */
export function readStoredTheme(): Theme | null {
  try {
    const value = window.localStorage.getItem(THEME_STORAGE_KEY)
    return isTheme(value) ? value : null
  } catch {
    // Storage can be unavailable (private mode, blocked site data).
    return null
  }
}

export function writeStoredTheme(theme: Theme): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // Non-fatal: the theme still applies for this session.
  }
}

/** Returns a MediaQueryList for the OS dark preference, or null where matchMedia is unavailable. */
export function getSystemThemeQuery(): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia(DARK_SCHEME_QUERY) : null
}

export function getSystemTheme(): Theme {
  return getSystemThemeQuery()?.matches ? 'dark' : 'light'
}

/** Saved preference wins; otherwise follow the operating system. */
export function resolveInitialTheme(): Theme {
  return readStoredTheme() ?? getSystemTheme()
}

/** Applies the theme to <html>; all color tokens in tokens.css key off this attribute. */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
}
