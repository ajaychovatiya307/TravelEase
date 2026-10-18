import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { UserAccountResponse } from '../../models/admin'
import type { TravelRequestResponse } from '../../models/travelRequest'
import { adminService } from '../../services/adminService'
import { toClassifiedError } from '../../services/httpClient'
import * as travelRequestService from '../../services/travelRequestService'
import { ReassignApproverPanel } from './ReassignApproverPanel'

vi.mock('../../services/adminService', () => ({
  adminService: { users: { list: vi.fn() } },
}))
vi.mock('../../services/travelRequestService', () => ({ reassign: vi.fn() }))

const request: TravelRequestResponse = {
  id: '12',
  employeeId: '1',
  destination: 'Pune',
  startDate: '2026-11-01',
  endDate: '2026-11-03',
  justification: 'Workshop',
  status: 'PENDING',
  approverId: '2',
  rejectionReason: null,
  resubmissionOfId: null,
  documents: [],
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
}

const user = (id: string, fullName: string, roles: UserAccountResponse['roles'], active = true) => ({
  id,
  fullName,
  email: `${fullName.toLowerCase().replace(' ', '.')}@example.com`,
  roles,
  active,
})

const users: UserAccountResponse[] = [
  user('1', 'Requester Emp', ['EMPLOYEE', 'MANAGER']),
  user('2', 'Current Mgr', ['MANAGER']),
  user('3', 'Backup Mgr', ['MANAGER']),
  user('4', 'Plain Emp', ['EMPLOYEE']),
  user('5', 'Gone Mgr', ['MANAGER'], false),
  user('6', 'Fin Lead', ['FINANCE']),
]

beforeEach(() => {
  vi.mocked(adminService.users.list).mockReset()
  vi.mocked(travelRequestService.reassign).mockReset()
  vi.mocked(adminService.users.list).mockResolvedValue({
    content: users,
    page: 0,
    size: 200,
    totalElements: users.length,
    totalPages: 1,
  })
})

describe('ReassignApproverPanel (#11)', () => {
  it('offers only active approvers other than the requester and the current approver', async () => {
    render(<ReassignApproverPanel request={request} onReassigned={vi.fn()} />)

    await screen.findByRole('option', { name: /Backup Mgr/ })
    const names = screen.getAllByRole('option').map((option) => option.textContent)
    expect(names).toHaveLength(3)
    expect(names.join('|')).toContain('Backup Mgr')
    expect(names.join('|')).toContain('Fin Lead')
    expect(names.join('|')).not.toMatch(/Requester|Current Mgr|Plain Emp|Gone Mgr/)
  })

  it('reassigns and refreshes the request', async () => {
    const onReassigned = vi.fn()
    vi.mocked(travelRequestService.reassign).mockResolvedValueOnce({ ...request, approverId: '3' })
    const actor = userEvent.setup()
    render(<ReassignApproverPanel request={request} onReassigned={onReassigned} />)

    await screen.findByRole('option', { name: /Backup Mgr/ })
    await actor.selectOptions(screen.getByLabelText('New approver'), '3')
    await actor.click(screen.getByRole('button', { name: 'Reassign' }))

    await waitFor(() => expect(onReassigned).toHaveBeenCalled())
    expect(travelRequestService.reassign).toHaveBeenCalledWith('12', '3')
    expect(screen.getByRole('status')).toHaveTextContent('Approval reassigned to Backup Mgr.')
  })

  it("shows the server's reason when the approver is not eligible", async () => {
    vi.mocked(travelRequestService.reassign).mockRejectedValueOnce(
      toClassifiedError({
        category: 'POLICY_VIOLATION',
        message: 'The selected approver is inactive',
        status: 422,
        errorCode: 'POLICY_VIOLATION',
        timestamp: null,
        fieldErrors: [],
      }),
    )
    const actor = userEvent.setup()
    render(<ReassignApproverPanel request={request} onReassigned={vi.fn()} />)

    await screen.findByRole('option', { name: /Backup Mgr/ })
    await actor.selectOptions(screen.getByLabelText('New approver'), '3')
    await actor.click(screen.getByRole('button', { name: 'Reassign' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The selected approver is inactive')
  })
})
