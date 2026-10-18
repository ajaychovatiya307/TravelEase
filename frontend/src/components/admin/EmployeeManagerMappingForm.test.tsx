/**
 * J-07 — Admin employee-manager mapping create/edit form (screen P-17) unit
 * tests.
 *
 * Mirrors the PolicyConfigForm test harness (J-08): `renderMappingForm`
 * mounts the form with a mocked host `onSubmit`, all error copy comes from
 * EMPLOYEE_MANAGER_MAPPING_MESSAGES (the named L-04 rule-set in
 * validation/adminSchemas.ts), and the fields are located through their
 * labels. The host page (I-14 MappingsPage) owns the admin mapping
 * create/update service call, so these tests only assert the request
 * mapping (including the nullable-manager convention) and the validation
 * behavior. The date field is filled through fireEvent.change — user-event
 * typing into date inputs is flaky under jsdom.
 */

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import {
  EmployeeManagerMappingForm,
  type EmployeeManagerMappingFormProps,
} from './EmployeeManagerMappingForm'
import { EMPLOYEE_MANAGER_MAPPING_MESSAGES } from '../../validation/adminSchemas'

// Shared literals (defined once, reused by every case).
const EMPLOYEE_ID = 'user-1'
const MANAGER_ID = 'user-9'
const EDITED_MANAGER_ID = 'user-10'
const EFFECTIVE_FROM = '2026-04-01'
const EDITED_EFFECTIVE_FROM = '2026-01-05'
const INVALID_DATE = '2026-02-30'

function renderMappingForm(props: Partial<EmployeeManagerMappingFormProps> = {}) {
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined)
  const view = render(<EmployeeManagerMappingForm mode="create" onSubmit={onSubmit} {...props} />)
  return { onSubmit, ...view }
}

const getEmployeeIdInput = () => screen.getByLabelText(/^Employee ID/) as HTMLInputElement
const getManagerIdInput = () => screen.getByLabelText(/^Manager ID/) as HTMLInputElement
const getEffectiveFromInput = () => screen.getByLabelText(/^Effective from/) as HTMLInputElement
const getSubmitButton = () => screen.getByTestId('employee-manager-mapping-form-submit')
const getForm = () => screen.getByTestId('employee-manager-mapping-form')

describe('empty submit (create mode)', () => {
  it('blocks the submit and alerts employeeId + effectiveFrom', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm()

    await user.click(getSubmitButton())

    await waitFor(() => {
      expect(screen.getByText(EMPLOYEE_MANAGER_MAPPING_MESSAGES.employeeId)).toBeTruthy()
    })
    expect(screen.getByText(EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFrom)).toBeTruthy()
    expect(onSubmit).not.toHaveBeenCalled()
  })
})

describe('valid submit (create mode)', () => {
  it('submits the request once with the trimmed manager id', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm()

    await user.type(getEmployeeIdInput(), EMPLOYEE_ID)
    await user.type(getManagerIdInput(), MANAGER_ID)
    fireEvent.change(getEffectiveFromInput(), { target: { value: EFFECTIVE_FROM } })
    await user.click(getSubmitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      employeeId: EMPLOYEE_ID,
      managerId: MANAGER_ID,
      effectiveFrom: EFFECTIVE_FROM,
    })
  })

  it('maps an empty manager id to null', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm()

    await user.type(getEmployeeIdInput(), EMPLOYEE_ID)
    fireEvent.change(getEffectiveFromInput(), { target: { value: EFFECTIVE_FROM } })
    await user.click(getSubmitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      employeeId: EMPLOYEE_ID,
      managerId: null,
      effectiveFrom: EFFECTIVE_FROM,
    })
  })

  it('maps a whitespace-only manager id to null', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm()

    await user.type(getEmployeeIdInput(), EMPLOYEE_ID)
    await user.type(getManagerIdInput(), '   ')
    fireEvent.change(getEffectiveFromInput(), { target: { value: EFFECTIVE_FROM } })
    await user.click(getSubmitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      employeeId: EMPLOYEE_ID,
      managerId: null,
      effectiveFrom: EFFECTIVE_FROM,
    })
  })
})

describe('invalid date', () => {
  it('blocks an invalid calendar date with the effectiveFromFormat message', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm()

    await user.type(getEmployeeIdInput(), EMPLOYEE_ID)
    fireEvent.change(getEffectiveFromInput(), { target: { value: INVALID_DATE } })
    await user.click(getSubmitButton())

    await waitFor(() => {
      expect(screen.getByText(EMPLOYEE_MANAGER_MAPPING_MESSAGES.effectiveFromFormat)).toBeTruthy()
    })
    expect(onSubmit).not.toHaveBeenCalled()
  })
})

describe('edit mode', () => {
  it('prefills the initialValues, shows Save changes, and maps an edited field', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm({
      mode: 'edit',
      initialValues: {
        employeeId: EMPLOYEE_ID,
        managerId: MANAGER_ID,
        effectiveFrom: EDITED_EFFECTIVE_FROM,
      },
    })

    expect(getEmployeeIdInput().value).toBe(EMPLOYEE_ID)
    expect(getManagerIdInput().value).toBe(MANAGER_ID)
    expect(getEffectiveFromInput().value).toBe(EDITED_EFFECTIVE_FROM)
    expect(getSubmitButton().textContent).toBe('Save changes')

    await user.clear(getManagerIdInput())
    await user.type(getManagerIdInput(), EDITED_MANAGER_ID)
    await user.click(getSubmitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      employeeId: EMPLOYEE_ID,
      managerId: EDITED_MANAGER_ID,
      effectiveFrom: EDITED_EFFECTIVE_FROM,
    })
  })
})

describe('rejected host submit', () => {
  it('leaves the form open when the host onSubmit rejects', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderMappingForm({
      onSubmit: vi.fn().mockRejectedValue(new Error('service failed')),
    })

    await user.type(getEmployeeIdInput(), EMPLOYEE_ID)
    fireEvent.change(getEffectiveFromInput(), { target: { value: EFFECTIVE_FROM } })
    await user.click(getSubmitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(getForm()).toBeTruthy()
  })
})

describe('server field errors', () => {
  it('renders server errors inline for the matching field', () => {
    renderMappingForm({
      serverFieldErrors: { employeeId: 'The selected employee was not found.' },
    })

    expect(screen.getByText('The selected employee was not found.')).toHaveAttribute('role', 'alert')
  })
})
