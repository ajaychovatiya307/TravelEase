// AuditLogPage component tests (I-15 verification method). Cover the P-20
// screen (frontend-spec.md §4.13) through the real page — the service call is
// mocked (auditService module), while the `useAuditList` hook (F-06), the
// URL-synchronized list query state (D-04), the `AuditFilterBar`, the
// `DataTable`, and the `ClassifiedError` → UI mapping (frontend-api-mapping.md
// §1.4 P-20 row) run for real: the read-only table (FR-AUD-01 — no mutation
// controls), the TR-09 row derivation (formatted `occurredAt`, human action
// label, before → after transition), the filter → query mapping with Clear
// reset, the default `occurredAt,desc` sort, pagination refetch, and the
// error mappings (`FORBIDDEN_ACTION` → /access-denied redirect, anything
// else → whole-page error banner).

import { useEffect } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { AuditRecordResponse } from '../../models/audit'
import type { ClassifiedError } from '../../models/error'
import type { PaginatedResult } from '../../models/pagination'
import type { ClassifiedErrorInstance } from '../../services/httpClient'
import { AccessDeniedPage } from '../../shared/AccessDeniedPage'
import { AuditLogPage } from './AuditLogPage'

const auditServiceMock = vi.hoisted(() => ({ list: vi.fn() }))

vi.mock('../../services/auditService', () => ({ auditService: auditServiceMock }))

const listMock = vi.mocked(auditServiceMock.list)

/** Current `useLocation().pathname` + `search` inside the harness MemoryRouter. */
const probe = vi.hoisted(() => ({ pathname: '', search: '' }))

/** TR-09 fixture: an `APPROVED` travel request with a before-status. */
const approvedRecord: AuditRecordResponse = {
  id: 'AU-1',
  entityType: 'TRAVEL_REQUEST',
  entityId: 'TR-9',
  actorId: 'U2',
  action: 'APPROVED',
  beforeStatus: 'PENDING',
  afterStatus: 'APPROVED',
  occurredAt: '2026-10-07T09:30:00Z',
}

/** TR-09 fixture: a `CREATED` expense claim with no before-status. */
const createdRecord: AuditRecordResponse = {
  id: 'AU-2',
  entityType: 'EXPENSE_CLAIM',
  entityId: 'EX-3',
  actorId: 'U1',
  action: 'CREATED',
  beforeStatus: null,
  afterStatus: 'PENDING',
  occurredAt: '2026-10-06T14:00:00Z',
}

/** `PaginatedResult<AuditRecordResponse>` envelope factory. */
function makeAuditPage(
  content: AuditRecordResponse[],
  overrides: Partial<PaginatedResult<AuditRecordResponse>> = {},
): PaginatedResult<AuditRecordResponse> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    ...overrides,
  }
}

/** Classified error carried on a real `Error` (see ReimbursePage.test.tsx). */
function makeClassifiedError(
  error: Pick<ClassifiedError, 'category' | 'message'> &
    Partial<Omit<ClassifiedError, 'category' | 'message'>>,
): ClassifiedErrorInstance {
  return Object.assign(
    new Error(error.message),
    { status: null, errorCode: null, timestamp: null, fieldErrors: [] },
    error,
  ) as ClassifiedErrorInstance
}

/**
 * AuditLogPage inside a MemoryRouter starting at `/audit` + real
 * AuthProvider with the session seeded for an ADMIN user (the audit log's
 * viewer role, same pattern as Router.test.tsx): the page is held back until
 * `isAuthenticated` is true, so it only ever mounts once the session exists.
 * A `LocationProbe` sibling records the current pathname + search in the
 * hoisted `probe` state object.
 */
function Harness() {
  return (
    <MemoryRouter initialEntries={['/audit']}>
      <AuthProvider>
        <SeededPage />
        <LocationProbe />
      </AuthProvider>
    </MemoryRouter>
  )
}

function SeededPage() {
  const { authState, setAuthState } = useAuth()
  useEffect(() => {
    setAuthState({
      userId: 'U5',
      roles: ['ADMIN'],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'jwt-token',
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <Routes>
      <Route path="/audit" element={<AuditLogPage />} />
      <Route path="/access-denied" element={<AccessDeniedPage />} />
    </Routes>
  )
}

function LocationProbe() {
  const { pathname, search } = useLocation()
  useEffect(() => {
    probe.pathname = pathname
    probe.search = search
  }, [pathname, search])
  return null
}

describe('AuditLogPage (I-15)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    probe.pathname = ''
    probe.search = ''
  })

  it('renders the rows with the derived labels and formatted timestamps', async () => {
    listMock.mockResolvedValue(makeAuditPage([approvedRecord, createdRecord]))
    render(<Harness />)

    expect(await screen.findByRole('heading', { name: 'Audit Log' })).toBeInTheDocument()
    expect(screen.getByText('Read-only audit trail — records are immutable.')).toBeInTheDocument()
    // Entity id + actor columns for both rows.
    expect(screen.getByText('TR-9')).toBeInTheDocument()
    expect(screen.getByText('EX-3')).toBeInTheDocument()
    expect(screen.getByText('U2')).toBeInTheDocument()
    expect(screen.getByText('U1')).toBeInTheDocument()
    // TR-09 action labels (frontend-api-mapping.md §2).
    expect(screen.getByText('Approved')).toBeInTheDocument()
    expect(screen.getByText('Created')).toBeInTheDocument()
    // TR-09 before → after transition: full transition when beforeStatus is
    // set, just the afterStatus when it is null.
    expect(screen.getByText('PENDING → APPROVED')).toBeInTheDocument()
    expect(screen.getByText('PENDING')).toBeInTheDocument()
    // TR-09 formatted local date-time (YYYY-MM-DD HH:mm).
    expect(screen.getByText('2026-10-07 09:30')).toBeInTheDocument()
    expect(screen.getByText('2026-10-06 14:00')).toBeInTheDocument()
  })

  it('renders the table loading state while audit records are being fetched', async () => {
    listMock.mockReturnValue(new Promise(() => {}))
    render(<Harness />)

    expect(await screen.findByRole('status', { name: 'Loading' })).toHaveClass(
      'loading-skeleton--table',
    )
  })

  it('renders an empty state when no audit records match the filters', async () => {
    listMock.mockResolvedValue(makeAuditPage([]))
    render(<Harness />)

    expect(
      await screen.findByText('No audit records match the current filters.'),
    ).toBeInTheDocument()
  })

  it('exposes no mutation controls and no row navigation (FR-AUD-01)', async () => {
    listMock.mockResolvedValue(makeAuditPage([approvedRecord, createdRecord]))
    render(<Harness />)

    await screen.findByRole('heading', { name: 'Audit Log' })

    // The page is read-only: no create/edit/delete-style button anywhere.
    expect(
      screen.queryByRole('button', { name: /new|create|edit|delete|add/i }),
    ).not.toBeInTheDocument()
    // No row-click navigation: no links inside the table.
    const table = screen.getByTestId('data-table')
    expect(table.querySelectorAll('a')).toHaveLength(0)
  })

  it('maps the applied filters onto the list query', async () => {
    const user = userEvent.setup()
    listMock.mockResolvedValue(makeAuditPage([approvedRecord]))
    render(<Harness />)
    await screen.findByRole('heading', { name: 'Audit Log' })

    await user.selectOptions(screen.getByLabelText('Entity Type'), 'TRAVEL_REQUEST')
    await user.type(screen.getByLabelText('Entity Id'), 'TR-9')
    await user.type(screen.getByLabelText('Actor Id'), 'U2')
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-10-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-10-31' } })

    await user.click(screen.getByRole('button', { name: 'Apply Filters' }))

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({
          entityType: 'TRAVEL_REQUEST',
          entityId: 'TR-9',
          actorId: 'U2',
          from: '2026-10-01T00:00:00Z',
          to: '2026-10-31T23:59:59.999999999Z',
          page: 0,
        }),
      )
    })
  })

  it('clears the filters back to the unfiltered default query', async () => {
    const user = userEvent.setup()
    listMock.mockResolvedValue(makeAuditPage([approvedRecord]))
    render(<Harness />)
    await screen.findByRole('heading', { name: 'Audit Log' })

    await user.type(screen.getByLabelText('Entity Id'), 'TR-9')
    await user.click(screen.getByRole('button', { name: 'Apply Filters' }))
    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ entityId: 'TR-9' }))
    })

    await user.click(screen.getByRole('button', { name: 'Clear Filters' }))

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        sort: 'occurredAt,desc',
        entityType: undefined,
        entityId: undefined,
        actorId: undefined,
        from: undefined,
        to: undefined,
      })
    })
    expect(probe.search).not.toContain('entityType')
  })

  it('refetches page 1 when Next is clicked', async () => {
    const user = userEvent.setup()
    listMock.mockResolvedValue(
      makeAuditPage([approvedRecord], { totalElements: 40, totalPages: 2 }),
    )
    render(<Harness />)
    await screen.findByRole('heading', { name: 'Audit Log' })

    await user.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    })
  })

  it('issues the unfiltered default query with the newest-first sort on load', async () => {
    listMock.mockResolvedValue(makeAuditPage([approvedRecord]))
    render(<Harness />)

    await screen.findByRole('heading', { name: 'Audit Log' })

    expect(listMock).toHaveBeenCalledTimes(1)
    expect(listMock).toHaveBeenNthCalledWith(1, {
      page: 0,
      size: 20,
      sort: 'occurredAt,desc',
      entityType: undefined,
      entityId: undefined,
      actorId: undefined,
      from: undefined,
      to: undefined,
    })
  })

  it('renders the API pagination envelope without an undefined page index', async () => {
    listMock.mockResolvedValue(makeAuditPage([approvedRecord]))
    render(<Harness />)

    expect(await screen.findByText('Page 1 of 1')).toBeInTheDocument()
    expect(screen.queryByText(/Page NaN/)).not.toBeInTheDocument()
  })

  it('redirects to /access-denied on FORBIDDEN_ACTION', async () => {
    listMock.mockRejectedValue(
      makeClassifiedError({ category: 'FORBIDDEN_ACTION', status: 403, message: 'Forbidden' }),
    )
    render(<Harness />)

    await waitFor(() => expect(probe.pathname).toBe('/access-denied'))
    expect(screen.queryByRole('heading', { name: 'Audit Log' })).not.toBeInTheDocument()
  })

  it('replaces the page with an error banner for any other classified error', async () => {
    listMock.mockRejectedValue(
      makeClassifiedError({
        category: 'INTERNAL_ERROR',
        status: 500,
        message: 'Something went wrong',
      }),
    )
    render(<Harness />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Something went wrong')
    // The whole page is replaced — no table, no rows.
    expect(screen.queryByTestId('data-table')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Audit Log' })).not.toBeInTheDocument()
    // No navigation happened — the user stays on /audit.
    expect(probe.pathname).toBe('/audit')
  })
})
