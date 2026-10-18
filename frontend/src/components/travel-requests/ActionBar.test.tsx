// ActionBar component tests (I-05, P-04). The component is pure presentation
// — no router or providers are needed. Every test mounts it with an explicit
// visibility-flag combination and fresh `vi.fn()` callbacks, covering each
// combination required by the I-05 acceptance criteria: none shown otherwise
// (read-only view), one button per single flag, all three when every flag is
// true, and each click invoking only its own callback (frontend-spec.md §4.4).

import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ActionBar } from './ActionBar'
import type { ActionBarProps } from './ActionBar'

/** The flag/callback prop set a test renders, defaulting to the read-only view. */
function makeProps(overrides: Partial<ActionBarProps> = {}): ActionBarProps {
  return {
    isEditable: false,
    isResubmittable: false,
    isDecidable: false,
    onEdit: vi.fn(),
    onResubmit: vi.fn(),
    onDecide: vi.fn(),
    ...overrides,
  }
}

/** Renders `<ActionBar {...props} />` and returns the props (with the spies). */
function renderActionBar(props: ActionBarProps) {
  render(<ActionBar {...props} />)
  return props
}

describe('ActionBar (I-05, P-04)', () => {
  it('renders nothing — no container, no buttons — when no flag is true (read-only view)', () => {
    const { container } = render(<ActionBar {...makeProps()} />)

    expect(container.querySelector('.action-bar')).toBeNull()
    expect(screen.queryByRole('group', { name: 'Actions' })).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('renders the group container with only "Edit" when isEditable is the true flag', () => {
    renderActionBar(makeProps({ isEditable: true }))

    expect(screen.getByRole('group', { name: 'Actions' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Resubmit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve/Reject' })).not.toBeInTheDocument()
  })

  it('renders the group container with only "Resubmit" when isResubmittable is the true flag', () => {
    renderActionBar(makeProps({ isResubmittable: true }))

    expect(screen.getByRole('group', { name: 'Actions' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Resubmit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve/Reject' })).not.toBeInTheDocument()
  })

  it('renders the group container with only "Approve/Reject" when isDecidable is the true flag', () => {
    renderActionBar(makeProps({ isDecidable: true }))

    expect(screen.getByRole('group', { name: 'Actions' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve/Reject' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Resubmit' })).not.toBeInTheDocument()
  })

  it('renders all three buttons in Edit → Resubmit → Approve/Reject order when every flag is true', () => {
    renderActionBar(makeProps({ isEditable: true, isResubmittable: true, isDecidable: true }))

    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(3)
    expect(buttons[0]).toHaveTextContent('Edit')
    expect(buttons[1]).toHaveTextContent('Resubmit')
    expect(buttons[2]).toHaveTextContent('Approve/Reject')
    // The buttons are real <button type="button"> elements with visible text.
    for (const button of buttons) {
      expect(button).toHaveAttribute('type', 'button')
      expect(button.textContent).not.toHaveLength(0)
    }
  })

  it('calls only onEdit when "Edit" is clicked', () => {
    const { onEdit, onResubmit, onDecide } = renderActionBar(makeProps({ isEditable: true }))

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))

    expect(onEdit).toHaveBeenCalledTimes(1)
    expect(onResubmit).not.toHaveBeenCalled()
    expect(onDecide).not.toHaveBeenCalled()
  })

  it('calls only onResubmit when "Resubmit" is clicked', () => {
    const { onEdit, onResubmit, onDecide } = renderActionBar(makeProps({ isResubmittable: true }))

    fireEvent.click(screen.getByRole('button', { name: 'Resubmit' }))

    expect(onResubmit).toHaveBeenCalledTimes(1)
    expect(onEdit).not.toHaveBeenCalled()
    expect(onDecide).not.toHaveBeenCalled()
  })

  it('calls only onDecide when "Approve/Reject" is clicked', () => {
    const { onEdit, onResubmit, onDecide } = renderActionBar(makeProps({ isDecidable: true }))

    fireEvent.click(screen.getByRole('button', { name: 'Approve/Reject' }))

    expect(onDecide).toHaveBeenCalledTimes(1)
    expect(onEdit).not.toHaveBeenCalled()
    expect(onResubmit).not.toHaveBeenCalled()
  })

  it('routes each click to its matching callback when all three buttons are rendered', () => {
    const { onEdit, onResubmit, onDecide } = renderActionBar(
      makeProps({ isEditable: true, isResubmittable: true, isDecidable: true }),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Resubmit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Approve/Reject' }))

    expect(onEdit).toHaveBeenCalledTimes(1)
    expect(onResubmit).toHaveBeenCalledTimes(1)
    expect(onDecide).toHaveBeenCalledTimes(1)
  })
})