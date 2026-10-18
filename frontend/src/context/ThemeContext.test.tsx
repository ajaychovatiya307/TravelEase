import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ThemeToggle } from '../components/theme/ThemeToggle'
import { THEME_STORAGE_KEY } from '../theme/theme'
import { ThemeProvider } from './ThemeContext'

type ChangeListener = (event: MediaQueryListEvent) => void

/** Installs a controllable `prefers-color-scheme: dark` media query. */
function mockSystemTheme(prefersDark: boolean) {
  const listeners = new Set<ChangeListener>()
  const query = {
    matches: prefersDark,
    media: '(prefers-color-scheme: dark)',
    addEventListener: (_type: string, listener: ChangeListener) => listeners.add(listener),
    removeEventListener: (_type: string, listener: ChangeListener) => listeners.delete(listener),
  }
  vi.stubGlobal('matchMedia', vi.fn(() => query))
  return {
    change(nextDark: boolean) {
      query.matches = nextDark
      listeners.forEach((listener) => listener({ matches: nextDark } as MediaQueryListEvent))
    },
  }
}

function renderToggle() {
  return render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  )
}

const currentTheme = () => document.documentElement.dataset.theme

beforeEach(() => {
  window.localStorage.clear()
  delete document.documentElement.dataset.theme
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ThemeProvider / ThemeToggle', () => {
  it('follows the OS preference on first visit when nothing is saved', () => {
    mockSystemTheme(true)
    renderToggle()

    expect(currentTheme()).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBeNull()
  })

  it('uses a saved preference over the OS preference', () => {
    mockSystemTheme(true)
    window.localStorage.setItem(THEME_STORAGE_KEY, 'light')
    renderToggle()

    expect(currentTheme()).toBe('light')
    expect(screen.getByRole('button', { name: 'Switch to dark mode' })).toBeInTheDocument()
  })

  it('toggles the whole document theme and persists the choice', async () => {
    mockSystemTheme(false)
    const user = userEvent.setup()
    renderToggle()
    expect(currentTheme()).toBe('light')

    await user.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    expect(currentTheme()).toBe('dark')
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')

    await user.click(screen.getByRole('button', { name: 'Switch to light mode' }))
    expect(currentTheme()).toBe('light')
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
  })

  it('restores the persisted choice after a remount (page refresh)', async () => {
    mockSystemTheme(false)
    const user = userEvent.setup()
    const { unmount } = renderToggle()
    await user.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    unmount()
    delete document.documentElement.dataset.theme

    renderToggle()
    expect(currentTheme()).toBe('dark')
  })

  it('tracks OS changes only until the user makes an explicit choice', async () => {
    const system = mockSystemTheme(false)
    const user = userEvent.setup()
    renderToggle()

    act(() => system.change(true))
    expect(currentTheme()).toBe('dark')

    await user.click(screen.getByRole('button', { name: 'Switch to light mode' }))
    act(() => system.change(true))
    expect(currentTheme()).toBe('light')
  })
})
