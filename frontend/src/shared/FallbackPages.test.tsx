import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AccessDeniedPage } from './AccessDeniedPage'
import { NotFoundPage } from './NotFoundPage'
import { GenericErrorPage } from './GenericErrorPage'

describe('AccessDeniedPage', () => {
  it('renders an informational message and a link back to the Dashboard', () => {
    render(<AccessDeniedPage />)
    expect(screen.getByText('You do not have permission to view this page.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Dashboard' })).toHaveAttribute('href', '/')
  })
})

describe('NotFoundPage', () => {
  it('renders an informational message and a link back to the Dashboard', () => {
    render(<NotFoundPage />)
    expect(
      screen.getByText('The page or record you are looking for could not be found.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Dashboard' })).toHaveAttribute('href', '/')
  })
})

describe('GenericErrorPage', () => {
  it('renders an informational message and a link back to the Dashboard', () => {
    render(<GenericErrorPage />)
    expect(
      screen.getByText('An unexpected error occurred. Please try again later.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Dashboard' })).toHaveAttribute('href', '/')
  })
})
