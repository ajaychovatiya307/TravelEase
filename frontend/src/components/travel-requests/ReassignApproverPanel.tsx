import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import type { UserAccountResponse } from '../../models/admin'
import type { TravelRequestResponse } from '../../models/travelRequest'
import { adminService } from '../../services/adminService'
import { isClassifiedErrorInstance } from '../../services/httpClient'
import * as travelRequestService from '../../services/travelRequestService'

/** Accounts considered: one page is enough for the approver pool of this application. */
const CANDIDATE_PAGE_SIZE = 200

interface ReassignApproverPanelProps {
  request: TravelRequestResponse
  onReassigned: () => void
}

function isCandidate(user: UserAccountResponse, request: TravelRequestResponse): boolean {
  return (
    user.active &&
    user.id !== request.employeeId &&
    user.id !== request.approverId &&
    user.roles.some((role) => role === 'MANAGER' || role === 'FINANCE' || role === 'ADMIN')
  )
}

/**
 * Admin-only control on a PENDING travel request (#11): moves the approval to another active
 * approver, e.g. when the assigned manager is absent or has left. The server is authoritative on
 * eligibility and explains a refusal (shown as returned).
 */
export function ReassignApproverPanel({ request, onReassigned }: ReassignApproverPanelProps) {
  const [candidates, setCandidates] = useState<UserAccountResponse[] | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [selected, setSelected] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    let isCurrent = true
    adminService.users
      .list({ page: 0, size: CANDIDATE_PAGE_SIZE, active: true })
      .then((result) => {
        if (isCurrent) setCandidates(result.content)
      })
      .catch(() => {
        if (isCurrent) setLoadFailed(true)
      })
    return () => {
      isCurrent = false
    }
  }, [])

  const options = (candidates ?? []).filter((user) => isCandidate(user, request))

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (selected === '') {
      setError('Choose the new approver.')
      return
    }
    setIsSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      await travelRequestService.reassign(request.id, selected)
      const chosen = options.find((user) => user.id === selected)
      setSuccess(`Approval reassigned to ${chosen?.fullName ?? `user ${selected}`}.`)
      setSelected('')
      onReassigned()
    } catch (caught) {
      setError(
        isClassifiedErrorInstance(caught) && caught.category === 'CONFLICT'
          ? 'This request was changed by someone else. Reload the page and try again.'
          : isClassifiedErrorInstance(caught) && caught.category !== 'INTERNAL_ERROR'
            ? caught.message
            : 'The approver could not be reassigned. Please try again.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="card" aria-labelledby="reassign-approver-title" style={{ padding: 16 }}>
      <h2 id="reassign-approver-title" style={{ fontSize: 16 }}>
        Reassign approver
      </h2>
      <p style={{ fontSize: 13 }}>
        Current approver: <span className="numeric">{request.approverId ?? 'none'}</span>
      </p>
      {success ? (
        <p role="status" style={{ fontSize: 13 }}>
          {success}
        </p>
      ) : null}
      {error ? (
        <p className="required" role="alert" style={{ fontSize: 13 }}>
          {error}
        </p>
      ) : null}
      {loadFailed ? (
        <p className="required" role="alert" style={{ fontSize: 13 }}>
          Could not load the list of approvers.
        </p>
      ) : (
        <form className="form-actions" noValidate onSubmit={handleSubmit}>
          <select
            id="reassign-approver"
            aria-label="New approver"
            className="input"
            value={selected}
            disabled={candidates === null || isSubmitting}
            onChange={(event) => {
              setSelected(event.target.value)
              setError(null)
            }}
          >
            <option value="">{candidates === null ? 'Loading approvers…' : 'Select new approver'}</option>
            {options.map((user) => (
              <option key={user.id} value={user.id}>
                {user.fullName} ({user.email}) — {user.roles.join(', ')}
              </option>
            ))}
          </select>
          <button className="button button-secondary" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Reassigning…' : 'Reassign'}
          </button>
        </form>
      )}
    </section>
  )
}
