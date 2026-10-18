// Tests for TravelRequestPicker (I-11, frontend-tasks.md; screens P-11/P-12).
// The picker is a controlled component: a host-page stand-in owns the
// selected id and feeds it back, mirroring how the I-11 ExpenseFormPage will
// wire it. The hook is mocked so no network is touched; auth goes through the
// real AuthProvider (same pattern as TravelRequestListPage.test.tsx).

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect, useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from '../../context/AuthContext'
import type { ServerDataState } from '../../hooks/useAsyncResource'
import type { PaginatedResult } from '../../models/pagination'
import type { TravelRequestResponse } from '../../models/travelRequest'
import { useTravelRequestList } from '../../hooks/useTravelRequests'
import { TravelRequestPicker } from './TravelRequestPicker'

vi.mock('../../hooks/useTravelRequests', () => ({
  useTravelRequestList: vi.fn(),
}))

const mockedUseTravelRequestList = vi.mocked(useTravelRequestList)

const approvedRequests: TravelRequestResponse[] = [
  {
    id: 'tr-approved-1',
    employeeId: 'employee-1',
    destination: 'Bengaluru',
    startDate: '2026-11-03',
    endDate: '2026-11-07',
    justification: 'Client site visit',
    status: 'APPROVED',
    approverId: 'manager-1',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-05T10:30:00Z',
  },
  {
    id: 'tr-approved-2',
    employeeId: 'employee-1',
    destination: 'Mumbai',
    startDate: '2026-12-10',
    endDate: '2026-12-12',
    justification: 'Regional offsite',
    status: 'APPROVED',
    approverId: 'manager-2',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-10-20T09:00:00Z',
    updatedAt: '2026-10-24T10:30:00Z',
  },
]

type PickerDataState = ServerDataState<PaginatedResult<TravelRequestResponse>>

function mockPickerData(content: TravelRequestResponse[], overrides?: Partial<PickerDataState>) {
  mockedUseTravelRequestList.mockReturnValue({
    data: {
      content,
      page: 0,
      size: 50,
      totalElements: content.length,
      totalPages: content.length === 0 ? 0 : 1,
    },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  })
}

/** I-11 host-page stand-in: owns the selection state and passes it down. */
function PickerHost({
  onExternalSelect,
}: {
  onExternalSelect?: (request: TravelRequestResponse | null) => void
}) {
  const [selectedId, setSelectedId] = useState<string | undefined>(undefined)

  return (
    <TravelRequestPicker
      selectedId={selectedId}
      onSelect={(request) => {
        setSelectedId(request?.id)
        onExternalSelect?.(request)
      }}
    />
  )
}

function AuthenticatedPage({
  onExternalSelect,
}: {
  onExternalSelect?: (request: TravelRequestResponse | null) => void
}) {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'employee-1',
      roles: ['EMPLOYEE'],
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'token',
    })
  }, [setAuthState])

  if (!authState.isAuthenticated) {
    return null
  }

  return <PickerHost onExternalSelect={onExternalSelect} />
}

function renderPicker(onExternalSelect?: (request: TravelRequestResponse | null) => void) {
  return render(
    <AuthProvider>
      <AuthenticatedPage onExternalSelect={onExternalSelect} />
    </AuthProvider>,
  )
}

describe('TravelRequestPicker (I-11)', () => {
  it('renders the employee approved travel request options', async () => {
    mockPickerData(approvedRequests)
    renderPicker()

    expect(
      await screen.findByTestId('travel-request-picker-option-tr-approved-1'),
    ).toBeInTheDocument()
    expect(screen.getByTestId('travel-request-picker-option-tr-approved-2')).toBeInTheDocument()
    expect(screen.getByText('Bengaluru')).toBeInTheDocument()
    expect(screen.getByText('Mumbai')).toBeInTheDocument()
    expect(screen.getByText('2026-11-03 – 2026-11-07')).toBeInTheDocument()
    expect(screen.getByText('2026-12-10 – 2026-12-12')).toBeInTheDocument()
    expect(mockedUseTravelRequestList).toHaveBeenCalledWith(
      expect.objectContaining({ filters: { status: 'APPROVED' } }),
      'MINE',
    )
  })

  it('selects an option and keeps the selection in sync', async () => {
    mockPickerData(approvedRequests)
    const onExternalSelect = vi.fn()
    renderPicker(onExternalSelect)

    const first = await screen.findByTestId('travel-request-picker-option-tr-approved-1')
    const second = screen.getByTestId('travel-request-picker-option-tr-approved-2')

    await userEvent.click(first)

    expect(onExternalSelect).toHaveBeenLastCalledWith(approvedRequests[0])
    expect(first).toBeChecked()
    expect(second).not.toBeChecked()

    await userEvent.click(second)

    expect(onExternalSelect).toHaveBeenLastCalledWith(approvedRequests[1])
    expect(second).toBeChecked()
    expect(first).not.toBeChecked()
  })

  it('shows the empty state when the employee has no approved travel requests', async () => {
    mockPickerData([])
    renderPicker()

    expect(await screen.findByText('No approved travel requests.')).toBeInTheDocument()
  })

  it('shows the loading state while the requests are fetched', async () => {
    mockPickerData([], { data: null, isLoading: true })
    renderPicker()

    expect(await screen.findByTestId('travel-request-picker-loading')).toBeInTheDocument()
  })
})
