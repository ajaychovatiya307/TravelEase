import { useTheme } from '../../context/ThemeContext'
import './ThemeToggle.css'

interface ThemeToggleProps {
  className?: string
}

/**
 * Sun/moon button that switches the whole app between light and dark mode.
 * Shows the icon of the theme it will switch TO, with an accessible label to match.
 */
export function ThemeToggle({ className }: ThemeToggleProps) {
  const { theme, toggleTheme } = useTheme()
  const nextTheme = theme === 'dark' ? 'light' : 'dark'
  const label = `Switch to ${nextTheme} mode`

  return (
    <button
      type="button"
      className={`icon-button theme-toggle${className ? ` ${className}` : ''}`}
      onClick={toggleTheme}
      aria-label={label}
      title={label}
      data-theme-current={theme}
    >
      <i className={`icon icon-${nextTheme === 'dark' ? 'moon' : 'sun'}`} aria-hidden="true" />
    </button>
  )
}
