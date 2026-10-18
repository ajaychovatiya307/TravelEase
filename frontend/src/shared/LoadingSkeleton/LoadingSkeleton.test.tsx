import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LoadingSkeleton } from './LoadingSkeleton'
import type { LoadingSkeletonShape } from './LoadingSkeleton'

const shapes: LoadingSkeletonShape[] = ['table', 'detail', 'form']

describe('LoadingSkeleton', () => {
  it.each(shapes)('renders the %s shape variant', (shape) => {
    render(<LoadingSkeleton shape={shape} />)
    const skeleton = screen.getByRole('status', { name: 'Loading' })
    expect(skeleton).toHaveClass(`loading-skeleton--${shape}`)
  })

  it('renders the requested number of placeholder lines', () => {
    const { container } = render(<LoadingSkeleton shape="table" rows={3} />)
    expect(container.querySelectorAll('.loading-skeleton__line')).toHaveLength(3)
  })
})
