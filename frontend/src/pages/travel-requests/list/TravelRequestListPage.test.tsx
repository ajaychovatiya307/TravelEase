import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'

import { AuthProvider, useAuth } from '../../../context/AuthContext'
import type { RoleType } from '../../../context/AuthContext'
import type { ServerDataState } from '../../../hooks/useAsyncResource'
import type { TravelRequestResponse } from '../../../models/travelRequest'
import type { PaginatedResult } from '../../../models/pagination'
import { useTravelRequestList } from '../../../hooks/useTravelRequests'
import * as travelRequestService from '../../../services/travelRequestService'
import { downloadCsv } from '../../../utils/csvExport'
import { TravelRequestListPage } from './TravelRequestListPage'

vi.mock('../../../hooks/useTravelRequests', () => ({
  useTravelRequestList: vi.fn(),
}))

vi.mock('../../../services/travelRequestService', () => ({
  list: vi.fn(),
}))

vi.mock('../../../utils/csvExport', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../utils/csvExport')>()
  return { ...actual, downloadCsv: vi.fn() }
})

const mockedUseTravelRequestList = vi.mocked(useTravelRequestList)
const mockedServiceList = vi.mocked(travelRequestService.list)
const mockedDownloadCsv = vi.mocked(downloadCsv)

const requests: TravelRequestResponse[] = [
  {
    id: 'tr-1',
    employeeId: 'employee-1',
    destination: 'Bengaluru',
    startDate: '2026-11-03',
    endDate: '2026-11-07',
    justification: 'Team workshop',
    status: 'PENDING',
    approverId: 'manager-1',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
  },
]

function LocationDisplay() {
  return <span data-testid="location">{useLocation().pathname + useLocation().search}</span>
}

function AuthenticatedPage({ roles }: { roles: RoleType[] }) {
  const { authState, setAuthState } = useAuth()

  useEffect(() => {
    setAuthState({
      userId: 'employee-1',
      roles,
      isAuthenticated: true,
      sessionExpiresAt: null,
      token: 'token',
    })
  }, [roles, setAuthState])

  if (!authState.isAuthenticated) {
    return null
  }

  return (
    <>
      <TravelRequestListPage />
      <LocationDisplay />
    </>
  )
}

function renderPage(
  path = '/travel-requests',
  overrides: Partial<ServerDataState<PaginatedResult<TravelRequestResponse>>> = {},
  roles: RoleType[] = ['EMPLOYEE'],
) {
  const state: ServerDataState<PaginatedResult<TravelRequestResponse>> = {
    data: { content: requests, page: 0, size: 10, totalElements: 11, totalPages: 2 },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  }
  mockedUseTravelRequestList.mockReturnValue(state)
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AuthenticatedPage roles={roles} />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('TravelRequestListPage (I-03)', () => {
  it('renders the employee list and new-request action', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'My Travel Requests' })).toBeInTheDocument()
    expect(screen.getByText('Bengaluru')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'New Travel Request' })).toHaveAttribute(
      'href',
      '/travel-requests/new',
    )
    expect(screen.queryByRole('button', { name: 'Export CSV' })).not.toBeInTheDocument()
    expect(mockedUseTravelRequestList).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
      'MINE',
    )
  })

  it('shows the export action to admins and loads every request (scope ALL)', () => {
    renderPage('/travel-requests', {}, ['ADMIN'])

    expect(screen.getByRole('heading', { name: 'Travel Requests' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeInTheDocument()
    expect(mockedUseTravelRequestList).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
      'ALL',
    )
  })

  it('exports every travel-request page for admins', async () => {
    mockedServiceList.mockResolvedValue({
      content: requests,
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderPage('/travel-requests', {}, ['ADMIN'])

    fireEvent.click(screen.getByRole('button', { name: 'Export CSV' }))

    await waitFor(() => expect(mockedDownloadCsv).toHaveBeenCalledOnce())
    expect(mockedServiceList).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: undefined,
    })
    expect(mockedDownloadCsv).toHaveBeenCalledWith(
      expect.stringMatching(/^travel-requests-\d{4}-\d{2}-\d{2}\.csv$/),
      requests,
      expect.any(Array),
    )
  })

  it('updates the URL when pagination changes', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('page=1'))
  })

  it('navigates to the selected request detail', async () => {
    renderPage()

    fireEvent.click(screen.getByText('Bengaluru'))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/travel-requests/tr-1'),
    )
  })

  it('shows the table loading state', () => {
    renderPage('/travel-requests', { data: null, isLoading: true })

    expect(screen.getByRole('status', { name: 'Loading' })).toHaveClass('loading-skeleton--table')
  })

  it('shows the empty state when there are no requests', () => {
    renderPage('/travel-requests', {
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })

    expect(screen.getByText('No travel requests found.')).toBeInTheDocument()
  })

  it('shows a retryable error when loading requests fails', async () => {
    const refetch = vi.fn()
    renderPage('/travel-requests', {
      data: null,
      error: new Error('transport details') as ServerDataState<
        PaginatedResult<TravelRequestResponse>
      >['error'],
      refetch,
    })

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Travel requests could not be loaded. Please try again.',
    )
    expect(screen.queryByText('transport details')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(refetch).toHaveBeenCalledOnce()
  })
})
