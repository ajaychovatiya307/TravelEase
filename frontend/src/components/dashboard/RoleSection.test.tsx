// RoleSection component tests (I-02 part 1, P-02). Covers the pure
// presentation block of frontend-spec.md §4.2 / ui-model.md §7: the h2
// heading, the pending-count summary node ("N pending" / loading placeholder
// / "Count unavailable" note / nothing when no count is expected), the
// optional summary body, and the react-router links.

import type { ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { RoleSection } from './RoleSection'
import type { RoleSectionLink } from './RoleSection'

interface SectionHarnessProps {
  title?: string
  count?: number | null
  isLoading?: boolean
  error?: string | null
  summary?: ReactNode | null
  links?: RoleSectionLink[]
}

/** Mounts one `RoleSection` inside a MemoryRouter with the given props. */
function renderSection({
  title = 'Pending My Approval',
  count,
  isLoading,
  error,
  summary,
  links = [{ label: 'Approval Queue', to: '/approvals/travel-requests' }],
}: SectionHarnessProps = {}) {
  return render(
    <MemoryRouter>
      <RoleSection
        title={title}
        count={count}
        isLoading={isLoading}
        error={error}
        summary={summary}
        links={links}
      />
    </MemoryRouter>,
  )
}

describe('RoleSection (I-02 part 1, P-02)', () => {
  it('renders the section title as an h2 heading', () => {
    renderSection({ title: 'Finance Review Queue' })
    const heading = screen.getByRole('heading', { level: 2, name: 'Finance Review Queue' })
    expect(heading).toBeInTheDocument()
  })

  it('shows the resolved count as "N pending" in the section header', () => {
    renderSection({ count: 3 })
    const countNode = screen.getByText('3 pending')
    expect(countNode).toBeInTheDocument()
    expect(countNode.closest('.role-section__header')).not.toBeNull()
  })

  it('renders no count node at all when count is omitted', () => {
    const { container } = renderSection({ title: 'Admin Shortcuts' })
    expect(screen.queryByText(/pending/i)).not.toBeInTheDocument()
    expect(container.querySelector('.role-section__count')).toBeNull()
  })

  it('shows a status placeholder instead of the count while loading', () => {
    renderSection({ count: 3, isLoading: true })
    const status = screen.getByRole('status')
    expect(status.textContent).toBe('…')
    expect(screen.queryByText('3 pending')).not.toBeInTheDocument()
  })

  it('shows the "Count unavailable" note when an error message is set', () => {
    renderSection({ count: 3, error: 'Something failed' })
    const note = screen.getByText('Count unavailable')
    expect(note).toBeInTheDocument()
    expect(note.closest('.role-section__count--error')).not.toBeNull()
    expect(screen.queryByText('3 pending')).not.toBeInTheDocument()
  })

  it('renders each link with its label and target route', () => {
    renderSection({
      title: 'My Travel Requests & Expenses',
      links: [
        { label: 'My Travel Requests', to: '/travel-requests' },
        { label: 'My Expenses', to: '/expenses' },
      ],
    })
    const travelLink = screen.getByRole('link', { name: 'My Travel Requests' })
    expect(travelLink).toHaveAttribute('href', '/travel-requests')
    const expensesLink = screen.getByRole('link', { name: 'My Expenses' })
    expect(expensesLink).toHaveAttribute('href', '/expenses')
  })

  it('renders the optional summary body between header and links when provided', () => {
    renderSection({
      title: 'My Travel Requests & Expenses',
      summary: <p>2 pending travel requests, 1 pending expenses</p>,
    })
    const breakdown = screen.getByText('2 pending travel requests, 1 pending expenses')
    expect(breakdown.closest('.role-section__summary')).not.toBeNull()
  })
})