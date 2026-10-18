import { DuplicateIndicator } from '../../shared/DuplicateIndicator'
import { StatusBadge } from '../../shared/StatusBadge'
import { Link } from 'react-router-dom'
import type { ExpenseClaimResponse } from '../../models/expenseClaim'

export type ExpenseRowField =
  | 'travelRequestId'
  | 'amount'
  | 'currency'
  | 'category'
  | 'expenseDate'
  | 'status'
  | 'duplicateFlag'

interface ExpenseRowProps {
  claim: ExpenseClaimResponse
  field: ExpenseRowField
}

function displayDate(value: string): string {
  return value.slice(0, 10)
}

/**
 * Renders the individual values used by the expense claim list row.
 * Keeping this mapping out of the page keeps the table definition declarative
 * and gives the Finance review queue (P-14) a reusable row renderer. The row
 * stays pure presentational: clickability is provided by the shared `DataTable`
 * and navigation to the detail route (`/expenses/:id`) is owned by the host
 * page via `DataTable`'s `onRowClick`, mirroring the travel-request list (I-03).
 */
export function ExpenseRow({ claim, field }: ExpenseRowProps) {
  switch (field) {
    case 'travelRequestId':
      return (
        <Link
          className="row-link numeric"
          to={`/expenses/${claim.id}`}
        >
          {claim.travelRequestId}
        </Link>
      )
    case 'amount':
      return <span>{claim.amount}</span>
    case 'currency':
      return <span>{claim.currency}</span>
    case 'category':
      return <span>{claim.category}</span>
    case 'expenseDate':
      return <time dateTime={claim.expenseDate}>{displayDate(claim.expenseDate)}</time>
    case 'status':
      return <StatusBadge status={claim.status} />
    case 'duplicateFlag':
      return <DuplicateIndicator duplicateFlag={claim.duplicateFlag} />
  }
}
