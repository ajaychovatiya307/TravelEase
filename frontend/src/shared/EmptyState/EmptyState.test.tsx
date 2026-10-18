import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('renders the provided message', () => {
    render(<EmptyState message="No travel requests found" />)
    expect(screen.getByText('No travel requests found')).toBeInTheDocument()
  })

  it('renders an optional icon when provided', () => {
    render(<EmptyState message="No results" icon="📭" />)
    expect(screen.getByText('📭')).toBeInTheDocument()
  })

  it('renders without an icon when none is provided', () => {
    const { container } = render(<EmptyState message="No results" />)
    expect(container.querySelector('.empty-state__icon')).not.toBeInTheDocument()
  })
})
