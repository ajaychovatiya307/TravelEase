// TravelRequestSummary component tests (I-05, P-04). The component is pure
// presentation, so no router or providers are needed — every test mounts it
// with a fully typed `TravelRequestDetailView` fixture. The expected date-range
// string is computed here with the same locale formatting the component uses
// (local-time parsing, no date library).

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { DocumentView, TravelRequestDetailView } from '../../models/viewModels'
import { TravelRequestSummary } from './TravelRequestSummary'

/** Fully typed fixture for a PENDING travel request; any field overridable. */
function makeRequest(overrides: Partial<TravelRequestDetailView> = {}): TravelRequestDetailView {
  return {
    id: 'TR-1',
    employeeId: 'U1',
    destination: 'Mumbai',
    startDate: '2026-03-02',
    endDate: '2026-03-05',
    justification: 'Client site visit for the Q2 rollout.',
    status: 'PENDING',
    approverId: 'U2',
    rejectionReason: null,
    resubmissionOfId: null,
    documents: [],
    createdAt: '2026-02-20T09:15:00Z',
    updatedAt: '2026-02-21T10:30:00Z',
    isEditable: true,
    isResubmittable: false,
    isDecidable: true,
    ...overrides,
  }
}

/** Fully typed document fixture (a `DocumentView` over `DocumentSummary`). */
function makeDocument(overrides: Partial<DocumentView> = {}): DocumentView {
  return {
    id: 'DOC-1',
    fileName: 'itinerary.pdf',
    fileType: 'PDF',
    fileSizeBytes: 20480,
    storageReference: 'travel-requests/TR-1/DOC-1',
    uploadStatus: 'CONFIRMED',
    fileSizeLabel: '20 KB',
    uploadStatusLabel: 'Confirmed',
    uploadStatusIcon: '✓',
    ...overrides,
  }
}

/** Same local-time date formatting the component applies to `startDate`/`endDate`. */
function formatDate(value: string): string {
  const parts = value.split('-')
  const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

function dateRange(startDate: string, endDate: string): string {
  return `${formatDate(startDate)} – ${formatDate(endDate)}`
}

describe('TravelRequestSummary (I-05, P-04)', () => {
  it('renders the destination, the formatted start–end date range, and the justification', () => {
    render(<TravelRequestSummary request={makeRequest()} />)

    expect(screen.getByRole('heading', { level: 2, name: 'Request summary' })).toBeInTheDocument()
    expect(screen.getByText('Mumbai')).toBeInTheDocument()
    expect(screen.getByText(dateRange('2026-03-02', '2026-03-05'))).toBeInTheDocument()
    expect(screen.getByText('Client site visit for the Q2 rollout.')).toBeInTheDocument()
  })

  it('renders the status through the shared StatusBadge', () => {
    render(<TravelRequestSummary request={makeRequest({ status: 'APPROVED' })} />)

    const badge = screen.getByText('Approved').closest('.status-badge') as HTMLElement
    expect(badge).toHaveClass('status-badge--approved')
    // The label text is the accessible status text, never color alone.
    expect(screen.queryByText('Pending')).not.toBeInTheDocument()
  })

  it('renders the assigned approver id', () => {
    render(<TravelRequestSummary request={makeRequest()} />)

    expect(screen.getByText('U2')).toBeInTheDocument()
    expect(screen.queryByText('Not yet assigned')).not.toBeInTheDocument()
  })

  it('shows "Not yet assigned" while approverId is null', () => {
    render(<TravelRequestSummary request={makeRequest({ approverId: null })} />)

    expect(screen.getByText('Not yet assigned')).toBeInTheDocument()
  })

  it('renders the rejection reason when it is set', () => {
    const request = makeRequest({
      status: 'REJECTED',
      rejectionReason: 'Budget exceeds the monthly travel limit.',
    })
    render(<TravelRequestSummary request={request} />)

    expect(screen.getByText('Rejection reason')).toBeInTheDocument()
    expect(screen.getByText('Budget exceeds the monthly travel limit.')).toBeInTheDocument()
  })

  it('hides the rejection reason when it is null', () => {
    render(<TravelRequestSummary request={makeRequest()} />)

    expect(screen.queryByText('Rejection reason')).not.toBeInTheDocument()
  })

  it('shows the resubmission lineage when resubmissionOfId is set', () => {
    render(<TravelRequestSummary request={makeRequest({ resubmissionOfId: 'TR-OLD' })} />)

    expect(screen.getByText('Resubmission of')).toBeInTheDocument()
    expect(screen.getByText('TR-OLD')).toBeInTheDocument()
  })

  it('hides the resubmission lineage when resubmissionOfId is null', () => {
    render(<TravelRequestSummary request={makeRequest()} />)

    expect(screen.queryByText('Resubmission of')).not.toBeInTheDocument()
  })

  it('renders the created and updated timestamps', () => {
    render(<TravelRequestSummary request={makeRequest()} />)

    expect(screen.getByText('2026-02-20T09:15:00Z')).toBeInTheDocument()
    expect(screen.getByText('2026-02-21T10:30:00Z')).toBeInTheDocument()
  })

  it('renders each document with its name, type, size, and upload status labels', () => {
    const request = makeRequest({
      documents: [
        makeDocument(),
        makeDocument({
          id: 'DOC-2',
          fileName: 'invoice.jpg',
          fileType: 'JPG',
          fileSizeBytes: 819200,
          storageReference: 'travel-requests/TR-1/DOC-2',
          uploadStatus: 'FAILED',
          fileSizeLabel: '800 KB',
          uploadStatusLabel: 'Failed',
          uploadStatusIcon: '✕',
        }),
      ],
    })
    render(<TravelRequestSummary request={request} />)

    expect(screen.getByText('itinerary.pdf')).toBeInTheDocument()
    expect(screen.getByText('PDF')).toBeInTheDocument()
    expect(screen.getByText('20 KB')).toBeInTheDocument()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
    expect(screen.getByText('invoice.jpg')).toBeInTheDocument()
    expect(screen.getByText('JPG')).toBeInTheDocument()
    expect(screen.getByText('800 KB')).toBeInTheDocument()
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.queryByText('No documents')).not.toBeInTheDocument()
  })

  it('shows the empty message when there are no documents', () => {
    render(<TravelRequestSummary request={makeRequest()} />)

    expect(screen.getByText('No documents')).toBeInTheDocument()
  })
})