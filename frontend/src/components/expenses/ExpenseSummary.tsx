// P-10 Expense Claim Detail summary (I-10, frontend-tasks.md). Read-only
// definition-list render of the `ExpenseClaimResponse` fields per
// frontend-spec.md §4.8 / api-contract.md §5.9 (linked travel request,
// amount + currency, category, expense date, status, duplicate flag,
// timestamps). Reused read-only by P-13 (frontend-spec.md §4.10). Pure
// presentation — the page owns fetching; the rejection reason and
// reimbursed-at rows render only when present.

import type { ReactNode } from 'react'

import { DuplicateIndicator } from '../../shared/DuplicateIndicator'
import { StatusBadge } from '../../shared/StatusBadge'
import type { ExpenseClaimResponse } from '../../models/expenseClaim'

interface ExpenseSummaryProps {
  claim: ExpenseClaimResponse
}

// Same convention as TravelRequestSummary/ExpenseRow: contract dates are
// ISO 8601 strings; the display form keeps the YYYY-MM-DD portion only.
function displayDate(value: string): string {
  return value.slice(0, 10)
}

// Same convention as ApprovalHistoryList.displayDateTime: contract
// timestamps are ISO 8601 strings; the display form slices the YYYY-MM-DD
// date plus the HH:mm time portion instead of rendering the raw value.
function displayDateTime(value: string): string {
  return `${value.slice(0, 10)} ${value.slice(11, 16)}`
}

interface SummaryRowProps {
  label: string
  children: ReactNode
  valueClassName?: string
}

function SummaryRow({ label, children, valueClassName }: SummaryRowProps) {
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd className={valueClassName}>{children}</dd>
    </div>
  )
}

/**
 * Renders one fetched `ExpenseClaimResponse` as labeled summary rows: the
 * linked travel request reference, the employee identifier, amount with
 * currency, category, expense date (YYYY-MM-DD), the workflow status via
 * `StatusBadge`, and the potential-duplicate flag via `DuplicateIndicator`
 * ("None" when not flagged). The rejection reason row appears only when the
 * response carries one (REJECTED); the reimbursed-at row appears only when
 * the claim has been reimbursed (REIMBURSED).
 */
export function ExpenseSummary({ claim }: ExpenseSummaryProps) {
  return (
    <div className="card card-medium">
      <dl className="detail-list">
        <SummaryRow label="Travel request" valueClassName="numeric">
          {claim.travelRequestId}
        </SummaryRow>
        <SummaryRow label="Employee" valueClassName="numeric">
          {claim.employeeId}
        </SummaryRow>
        <SummaryRow label="Amount" valueClassName="numeric">
          {claim.amount} {claim.currency}
        </SummaryRow>
        <SummaryRow label="Category">{claim.category}</SummaryRow>
        <SummaryRow label="Expense date" valueClassName="numeric">
          {displayDate(claim.expenseDate)}
        </SummaryRow>
        <SummaryRow label="Status">
          <StatusBadge status={claim.status} />
        </SummaryRow>
        <SummaryRow label="Duplicate flag">
          {claim.duplicateFlag ? <DuplicateIndicator duplicateFlag /> : 'None'}
        </SummaryRow>
        {claim.rejectionReason ? (
          <SummaryRow label="Rejection reason" valueClassName="required">
            {claim.rejectionReason}
          </SummaryRow>
        ) : null}
        {claim.reimbursedAt ? (
          <SummaryRow label="Reimbursed at">{displayDateTime(claim.reimbursedAt)}</SummaryRow>
        ) : null}
        <SummaryRow label="Created at" valueClassName="numeric">
          {displayDateTime(claim.createdAt)}
        </SummaryRow>
        <SummaryRow label="Updated at" valueClassName="numeric">
          {displayDateTime(claim.updatedAt)}
        </SummaryRow>
      </dl>
    </div>
  )
}
