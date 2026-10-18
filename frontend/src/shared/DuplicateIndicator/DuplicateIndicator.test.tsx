import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DuplicateIndicator } from './DuplicateIndicator'

describe('DuplicateIndicator', () => {
  it('renders the duplicate indicator when the flag is true', () => {
    render(<DuplicateIndicator duplicateFlag />)
    expect(screen.getByText('Possible duplicate')).toBeInTheDocument()
  })

  it('renders nothing when the flag is false', () => {
    const { container } = render(<DuplicateIndicator duplicateFlag={false} />)
    expect(container).toBeEmptyDOMElement()
  })
})
