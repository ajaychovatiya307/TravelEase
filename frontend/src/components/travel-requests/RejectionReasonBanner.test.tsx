// RejectionReasonBanner component tests (J-01, frontend-tasks.md): read-only
// banner for the prior rejection reason (frontend-clarification.md Q12,
// frontend-spec.md §4.5) — renders the reason with a title and renders
// nothing for a null/blank reason.

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { RejectionReasonBanner } from './RejectionReasonBanner'

describe('RejectionReasonBanner (J-01)', () => {
  it('renders the prior rejection reason with the default title', () => {
    render(<RejectionReasonBanner reason="Budget cap exceeded for this quarter." />)
    expect(screen.getByText('Prior rejection reason')).toBeInTheDocument()
    expect(screen.getByText('Budget cap exceeded for this quarter.')).toBeInTheDocument()
  })

  it('uses a custom title when provided', () => {
    render(
      <RejectionReasonBanner
        reason="Dates overlap an existing trip"
        title="Why this was rejected"
      />,
    )
    expect(screen.getByText('Why this was rejected')).toBeInTheDocument()
    expect(screen.queryByText('Prior rejection reason')).not.toBeInTheDocument()
  })

  it('trims surrounding whitespace from the reason', () => {
    render(<RejectionReasonBanner reason="  Policy limit exceeded  " />)
    expect(screen.getByText('Policy limit exceeded')).toBeInTheDocument()
  })

  it('renders nothing when the reason is null, undefined, or blank', () => {
    const nullReason = render(<RejectionReasonBanner reason={null} />)
    expect(nullReason.container.innerHTML).toBe('')

    const undefinedReason = render(<RejectionReasonBanner />)
    expect(undefinedReason.container.innerHTML).toBe('')

    const blankReason = render(<RejectionReasonBanner reason="   " />)
    expect(blankReason.container.innerHTML).toBe('')
  })
})
