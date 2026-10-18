import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusBadge } from './StatusBadge'
import type { StatusValue } from './StatusBadge'

const cases: Array<{ status: StatusValue; label: string }> = [
  { status: 'DRAFT', label: 'Draft' },
  { status: 'PENDING', label: 'Pending' },
  { status: 'APPROVED', label: 'Approved' },
  { status: 'REJECTED', label: 'Rejected' },
  { status: 'REIMBURSED', label: 'Reimbursed' },
]

describe('StatusBadge', () => {
  it.each(cases)('renders a distinct label for $status', ({ status, label }) => {
    render(<StatusBadge status={status} />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('pairs each status with its own icon, not color alone', () => {
    const { container: pendingContainer } = render(<StatusBadge status="PENDING" />)
    const { container: approvedContainer } = render(<StatusBadge status="APPROVED" />)
    const pendingIcon = pendingContainer.querySelector('.icon')?.className
    const approvedIcon = approvedContainer.querySelector('.icon')?.className
    expect(pendingIcon).not.toEqual(approvedIcon)
  })
})
