// AuditFilterBar component tests (I-15, frontend-tasks.md). Cover the
// read-only audit log filters (P-20, frontend-spec.md §4.13): all five
// optional controls render (Entity Type / Entity Id / Actor Id / From / To),
// the draft/commit model (edits do NOT commit until "Apply Filters" is
// clicked), the trimmed `onApply` payload (frontend-api-mapping.md §1.4 — the
// exact `useAudit.ts` `toListParams` keys, '' = no filter), the "Clear
// Filters" reset (`onClear`), the re-sync of the draft when the page applies
// new filter values (D-04), and the exactly-three Entity Type options
// (models/audit.ts `AuditEntityType` — E-05). Props are mocked — no network.

import type { ComponentProps } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { AuditFilterBar } from './AuditFilterBar'
import type { AuditFilterValues } from './AuditFilterBar'

type BarProps = ComponentProps<typeof AuditFilterBar>

const EMPTY_FILTERS: AuditFilterValues = {
  entityType: '',
  entityId: '',
  actorId: '',
  from: '',
  to: '',
}

/** Apply mock typed against the `toListParams` (F-06) filter contract. */
function mockOnApply() {
  return vi.fn<(filters: AuditFilterValues) => void>()
}

function mockOnClear() {
  return vi.fn<() => void>()
}

function renderBar(props: Partial<BarProps> = {}) {
  const {
    filters = EMPTY_FILTERS,
    onApply = mockOnApply(),
    onClear = mockOnClear(),
    ...rest
  } = props
  return {
    onApply,
    onClear,
    ...render(<AuditFilterBar filters={filters} onApply={onApply} onClear={onClear} {...rest} />),
  }
}

describe('AuditFilterBar (I-15)', () => {
  it('renders all five filter controls plus the Apply/Clear buttons', () => {
    renderBar()

    // Regex matchers: every filter is optional, so the shared label chrome
    // renders the plain label text (no required marker).
    expect(screen.getByLabelText(/entity type/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/entity id/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/actor id/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^from$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^to$/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apply Filters' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Clear Filters' })).toBeInTheDocument()
    // Accessible filter group named by its legend (frontend-spec.md §4.13).
    expect(screen.getByRole('group', { name: /filters/i })).toBeInTheDocument()
  })

  it('does not call onApply while editing the draft — only on Apply click', async () => {
    const user = userEvent.setup()
    const { onApply } = renderBar()

    await user.type(screen.getByLabelText(/entity id/i), 'TR-101')
    await user.selectOptions(screen.getByLabelText(/entity type/i), 'TRAVEL_REQUEST')

    // Draft edits never commit — no keystroke-driven apply.
    expect(onApply).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Apply Filters' }))
    expect(onApply).toHaveBeenCalledTimes(1)
  })

  it('applies trimmed values for filled fields and empty strings for untouched ones', async () => {
    const user = userEvent.setup()
    const { onApply } = renderBar()

    await user.type(screen.getByLabelText(/entity id/i), '  TR-101  ')
    await user.type(screen.getByLabelText(/actor id/i), 'U-42')
    await user.selectOptions(screen.getByLabelText(/entity type/i), 'EXPENSE_CLAIM')

    await user.click(screen.getByRole('button', { name: 'Apply Filters' }))

    expect(onApply).toHaveBeenCalledTimes(1)
    // Trimmed text values; untouched filters stay '' ("no filter").
    expect(onApply).toHaveBeenCalledWith({
      entityType: 'EXPENSE_CLAIM',
      entityId: 'TR-101',
      actorId: 'U-42',
      from: '',
      to: '',
    })
  })

  it('clears the visible draft inputs and calls onClear', async () => {
    const user = userEvent.setup()
    const { onClear } = renderBar({
      filters: {
        entityType: 'TRAVEL_REQUEST',
        entityId: 'TR-1',
        actorId: 'U-9',
        from: '2026-01-01',
        to: '2026-01-31',
      },
    })

    await user.click(screen.getByRole('button', { name: 'Clear Filters' }))

    expect(onClear).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText(/entity type/i)).toHaveValue('')
    expect(screen.getByLabelText(/entity id/i)).toHaveValue('')
    expect(screen.getByLabelText(/actor id/i)).toHaveValue('')
    expect(screen.getByLabelText(/^from$/i)).toHaveValue('')
    expect(screen.getByLabelText(/^to$/i)).toHaveValue('')
  })

  it('syncs the draft when the applied filters change externally', async () => {
    const user = userEvent.setup()
    const onApply = mockOnApply()
    const onClear = mockOnClear()
    const { rerender } = render(
      <AuditFilterBar filters={EMPTY_FILTERS} onApply={onApply} onClear={onClear} />,
    )

    // In-progress draft edit that was never applied...
    await user.type(screen.getByLabelText(/entity id/i), 'draft-only')

    // ...is replaced when the page applies different filter values (D-04).
    rerender(
      <AuditFilterBar
        filters={{
          ...EMPTY_FILTERS,
          entityType: 'EXPENSE_CLAIM',
          entityId: 'TR-42',
          from: '2026-02-01',
        }}
        onApply={onApply}
        onClear={onClear}
      />,
    )

    expect(screen.getByLabelText(/entity type/i)).toHaveValue('EXPENSE_CLAIM')
    expect(screen.getByLabelText(/entity id/i)).toHaveValue('TR-42')
    expect(screen.getByLabelText(/^from$/i)).toHaveValue('2026-02-01')
    expect(screen.getByLabelText(/^to$/i)).toHaveValue('')
  })

  it('offers exactly the three Entity Type options (All / TRAVEL_REQUEST / EXPENSE_CLAIM)', () => {
    renderBar()

    const options = screen.getAllByRole('option')
    expect(options).toHaveLength(3)
    // "All" is the empty value (E-05 AuditEntityType otherwise, models/audit.ts).
    expect(options.map((option) => (option as HTMLOptionElement).value)).toEqual([
      '',
      'TRAVEL_REQUEST',
      'EXPENSE_CLAIM',
    ])
  })

  it('disables every control while the page is loading', () => {
    renderBar({ disabled: true })

    expect(screen.getByRole('button', { name: 'Apply Filters' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Clear Filters' })).toBeDisabled()
    expect(screen.getByLabelText(/entity type/i)).toBeDisabled()
    expect(screen.getByLabelText(/entity id/i)).toBeDisabled()
    expect(screen.getByLabelText(/actor id/i)).toBeDisabled()
    expect(screen.getByLabelText(/^from$/i)).toBeDisabled()
    expect(screen.getByLabelText(/^to$/i)).toBeDisabled()
  })

  it('offers administrative entity types only to administrators (#13)', () => {
    const { unmount } = renderBar()
    const workflowOnly = Array.from(
      (screen.getByLabelText(/entity type/i) as HTMLSelectElement).options,
    ).map((option) => option.value)
    expect(workflowOnly).not.toContain('USER_ACCOUNT')
    unmount()

    renderBar({ includeAdministrative: true })
    const all = Array.from((screen.getByLabelText(/entity type/i) as HTMLSelectElement).options).map(
      (option) => option.value,
    )
    expect(all).toEqual(
      expect.arrayContaining(['TRAVEL_REQUEST', 'EXPENSE_CLAIM', 'USER_ACCOUNT', 'POLICY_CONFIG']),
    )
  })
})
