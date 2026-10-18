import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ThemeProvider } from '../../context/ThemeContext'
import { AuthLayout } from './AuthLayout'

describe('AuthLayout', () => {
  it('renders only a content outlet containing the passed-in child, plus the theme toggle', () => {
    render(
      <ThemeProvider>
        <AuthLayout>
          <div>Login form</div>
        </AuthLayout>
      </ThemeProvider>,
    )

    expect(screen.getByRole('main')).toHaveTextContent('Login form')
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    expect(screen.queryByRole('banner')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /switch to (light|dark) mode/i })).toBeInTheDocument()
  })
})
