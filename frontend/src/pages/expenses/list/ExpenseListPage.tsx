import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { sortToParam, useListQueryState } from '../../../hooks/useListQueryState'
import { useExpenseList } from '../../../hooks/useExpenses'
import { useAuth } from '../../../context/AuthContext'
import * as expenseService from '../../../services/expenseService'
import type { DataTableColumn, SortState } from '../../../shared/DataTable'
import { DataTable } from '../../../shared/DataTable'
import { ExpenseRow } from '../../../components/expenses/ExpenseRow'
import type { ExpenseClaimResponse, ExpenseClaimStatus } from '../../../models/expenseClaim'
import { downloadCsv, fetchAllPages } from '../../../utils/csvExport'
import './ExpenseListPage.css'

const columns: DataTableColumn<ExpenseClaimResponse>[] = [
  {
    key: 'travelRequestId',
    header: 'Travel Request',
    render: (claim) => <ExpenseRow claim={claim} field="travelRequestId" />,
  },
  {
    key: 'amount',
    header: 'Amount',
    sortable: true,
    render: (claim) => <ExpenseRow claim={claim} field="amount" />,
  },
  {
    key: 'currency',
    header: 'Currency',
    render: (claim) => <ExpenseRow claim={claim} field="currency" />,
  },
  {
    key: 'category',
    header: 'Category',
    render: (claim) => <ExpenseRow claim={claim} field="category" />,
  },
  {
    key: 'expenseDate',
    header: 'Expense Date',
    sortable: true,
    render: (claim) => <ExpenseRow claim={claim} field="expenseDate" />,
  },
  {
    key: 'status',
    header: 'Status',
    sortable: true,
    render: (claim) => <ExpenseRow claim={claim} field="status" />,
  },
  {
    key: 'duplicateFlag',
    header: 'Duplicate',
    render: (claim) => <ExpenseRow claim={claim} field="duplicateFlag" />,
  },
]

const exportColumns = [
  { header: 'ID', value: (claim: ExpenseClaimResponse) => claim.id },
  { header: 'Employee ID', value: (claim: ExpenseClaimResponse) => claim.employeeId },
  { header: 'Travel Request ID', value: (claim: ExpenseClaimResponse) => claim.travelRequestId },
  { header: 'Amount', value: (claim: ExpenseClaimResponse) => claim.amount },
  { header: 'Currency', value: (claim: ExpenseClaimResponse) => claim.currency },
  { header: 'Category', value: (claim: ExpenseClaimResponse) => claim.category },
  { header: 'Expense Date', value: (claim: ExpenseClaimResponse) => claim.expenseDate },
  { header: 'Status', value: (claim: ExpenseClaimResponse) => claim.status },
  { header: 'Duplicate', value: (claim: ExpenseClaimResponse) => claim.duplicateFlag },
  { header: 'Rejection Reason', value: (claim: ExpenseClaimResponse) => claim.rejectionReason },
  { header: 'Reimbursed At', value: (claim: ExpenseClaimResponse) => claim.reimbursedAt },
  { header: 'Created At', value: (claim: ExpenseClaimResponse) => claim.createdAt },
  { header: 'Updated At', value: (claim: ExpenseClaimResponse) => claim.updatedAt },
]

export function ExpenseListPage() {
  const navigate = useNavigate()
  const { authState } = useAuth()
  const { queryState, setPage, setSize, setSort } = useListQueryState()
  const isAdmin = authState.roles.includes('ADMIN')
  const [isExporting, setIsExporting] = useState(false)
  const [exportError, setExportError] = useState(false)
  const { data, isLoading, error, refetch } = useExpenseList(queryState)

  const pagination = {
    page: data?.page ?? queryState.page,
    size: data?.size ?? queryState.size,
    totalElements: data?.totalElements ?? 0,
    totalPages: data?.totalPages ?? 0,
  }

  const handleSortChange = (sort: SortState) => {
    setSort(sort)
  }

  const handleExport = async () => {
    setIsExporting(true)
    setExportError(false)
    try {
      const claims = await fetchAllPages((page, size) =>
        expenseService.list({
          page,
          size,
          sort: sortToParam(queryState.sort),
          status: queryState.filters.status as ExpenseClaimStatus | undefined,
          employeeId: queryState.filters.employeeId,
          duplicateFlag:
            queryState.filters.duplicateFlag === 'true'
              ? true
              : queryState.filters.duplicateFlag === 'false'
                ? false
                : undefined,
        }),
      )
      downloadCsv(`expenses-${new Date().toISOString().slice(0, 10)}.csv`, claims, exportColumns)
    } catch {
      setExportError(true)
    } finally {
      setIsExporting(false)
    }
  }

  return (
    <section className="screen" data-screen="expenses" data-section="expenses">
      <div className="page-header">
        <div>
          <h1>{isAdmin ? 'Expenses' : 'My Expense Claims'}</h1>
          <p>
            {isAdmin
              ? 'View and export expenses.'
              : 'View and manage your submitted expense claims.'}
          </p>
        </div>
        <div className="expense-list-page__actions">
          {isAdmin ? (
            <button
              className="button button-secondary"
              type="button"
              disabled={isExporting}
              onClick={handleExport}
            >
              {isExporting ? 'Exporting…' : 'Export CSV'}
            </button>
          ) : null}
          <Link className="button button-primary" to="/expenses/new">
            <i className="icon icon-plus" />
            New Expense Claim
          </Link>
        </div>
      </div>

      {exportError ? (
        <p className="required" role="alert" style={{ fontSize: 13 }}>
          Expenses could not be exported. Please try again.
        </p>
      ) : null}

      <div className="card">
        {error ? (
          <>
            <p className="empty-state" role="alert">
              Expense claims could not be loaded. Please try again.
            </p>
            <button
              className="button button-secondary button-small"
              type="button"
              onClick={refetch}
            >
              Dismiss
            </button>
          </>
        ) : (
          <DataTable
            columns={columns}
            referenceLayout
            rows={data?.content ?? []}
            getRowId={(claim) => claim.id}
            isLoading={isLoading}
            emptyMessage="No expense claims found."
            pagination={pagination}
            onPageChange={setPage}
            onSizeChange={setSize}
            sort={queryState.sort}
            onSortChange={handleSortChange}
            onRowClick={(claim) => navigate(`/expenses/${claim.id}`)}
          />
        )}
      </div>
    </section>
  )
}
