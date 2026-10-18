import type { ExpenseClaimStatus } from '../../models/expenseClaim'
import type { TravelRequestStatus } from '../../models/travelRequest'

export type StatusValue = ExpenseClaimStatus | TravelRequestStatus

const STATUS_CONFIG: Record<string, { label: string; icon: string; className: string }> = {
  DRAFT: { label: 'Draft', icon: 'hourglass', className: 'status-pending status-badge--draft' },
  PENDING: {
    label: 'Pending',
    icon: 'hourglass',
    className: 'status-pending status-badge--pending',
  },
  APPROVED: {
    label: 'Approved',
    icon: 'check',
    className: 'status-approved status-badge--approved',
  },
  REJECTED: { label: 'Rejected', icon: 'x', className: 'status-rejected status-badge--rejected' },
  CANCELLED: { label: 'Cancelled', icon: 'x', className: 'status-reimbursed status-badge--cancelled' },
  REIMBURSED: {
    label: 'Reimbursed',
    icon: 'banknote',
    className: 'status-reimbursed status-badge--reimbursed',
  },
}

interface StatusBadgeProps {
  status: StatusValue
}

/**
 * Renders a workflow status as text + icon, never color alone (frontend-requirement.md §11).
 * Entity-agnostic by design (frontend-constitution.md §11) — it knows nothing about
 * "travel requests" or "expenses", only the status value itself.
 */
export function StatusBadge({ status }: StatusBadgeProps) {
  const config = STATUS_CONFIG[status] ?? {
    label: status,
    icon: 'hourglass',
    className: 'status-pending status-badge--pending',
  }
  return (
    <span className={`status status-badge ${config.className}`}>
      <i className={`icon icon-${config.icon}`} />
      {config.label}
    </span>
  )
}
