import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ThemeProvider } from '../../context/ThemeContext'
import { AuthLayout } from './AuthLayout'

describe('AuthLayout', () => {
  it('renders only a content outlet containing the passed-in child, with no theme toggle (light mode only)', () => {
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
    expect(screen.queryByRole('button', { name: /switch to (light|dark) mode/i })).not.toBeInTheDocument()
  })
})
