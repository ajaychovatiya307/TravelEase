import { StatusBadge } from '../../shared/StatusBadge'
import { Link } from 'react-router-dom'
import type { TravelRequestResponse } from '../../models/travelRequest'

export type TravelRequestRowField = 'destination' | 'dates' | 'status' | 'approver' | 'created'

interface TravelRequestRowProps {
  request: TravelRequestResponse
  field: TravelRequestRowField
}

function displayDate(value: string): string {
  return value.slice(0, 10)
}

/**
 * Renders the individual values used by the travel-request list row.
 * Keeping this mapping out of the page keeps the table definition declarative
 * and gives the approval queue a reusable row renderer.
 */
export function TravelRequestRow({ request, field }: TravelRequestRowProps) {
  switch (field) {
    case 'destination':
      return (
        <Link
          className="row-link"
          to={`/travel-requests/${request.id}`}
        >
          {request.destination}
        </Link>
      )
    case 'dates':
      return (
        <span>
          {displayDate(request.startDate)} – {displayDate(request.endDate)}
        </span>
      )
    case 'status':
      return <StatusBadge status={request.status} />
    case 'approver':
      return <span>{request.approverId ?? 'Not assigned'}</span>
    case 'created':
      return <time dateTime={request.createdAt}>{displayDate(request.createdAt)}</time>
  }
}
