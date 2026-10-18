import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { DateField, MultiSelectField, SelectField, TextareaField, TextField } from './index'

const options = [
  { value: 'a', label: 'Option A' },
  { value: 'b', label: 'Option B' },
]

describe('TextField', () => {
  it('renders a label associated with its input', () => {
    render(<TextField id="name" label="Name" value="" onChange={vi.fn()} />)
    expect(screen.getByLabelText('Name')).toBeInTheDocument()
  })

  it('shows inline error text when an error prop is set', () => {
    render(<TextField id="name" label="Name" value="" onChange={vi.fn()} error="Required" />)
    expect(screen.getByRole('alert')).toHaveTextContent('Required')
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
  })

  it('marks required fields visually and via aria-required', () => {
    render(<TextField id="name" label="Name" value="" onChange={vi.fn()} required />)
    expect(screen.getByLabelText(/Name/)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('*')).toBeInTheDocument()
  })
})

describe('TextareaField', () => {
  it('renders a label associated with its textarea', () => {
    render(<TextareaField id="justification" label="Justification" value="" onChange={vi.fn()} />)
    expect(screen.getByLabelText('Justification')).toBeInTheDocument()
  })

  it('shows inline error text when an error prop is set', () => {
    render(
      <TextareaField
        id="justification"
        label="Justification"
        value=""
        onChange={vi.fn()}
        error="Too short"
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Too short')
  })

  it('marks required fields visually and via aria-required', () => {
    render(
      <TextareaField
        id="justification"
        label="Justification"
        value=""
        onChange={vi.fn()}
        required
      />,
    )
    expect(screen.getByLabelText(/Justification/)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('*')).toBeInTheDocument()
  })
})

describe('SelectField', () => {
  it('renders a label associated with its select', () => {
    render(
      <SelectField id="category" label="Category" value="" options={options} onChange={vi.fn()} />,
    )
    expect(screen.getByLabelText('Category')).toBeInTheDocument()
  })

  it('shows inline error text when an error prop is set', () => {
    render(
      <SelectField
        id="category"
        label="Category"
        value=""
        options={options}
        onChange={vi.fn()}
        error="Select a category"
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Select a category')
  })

  it('marks required fields visually and via aria-required', () => {
    render(
      <SelectField
        id="category"
        label="Category"
        value=""
        options={options}
        onChange={vi.fn()}
        required
      />,
    )
    expect(screen.getByLabelText(/Category/)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('*')).toBeInTheDocument()
  })
})

describe('DateField', () => {
  it('renders a label associated with its date input', () => {
    render(<DateField id="startDate" label="Start Date" value="" onChange={vi.fn()} />)
    expect(screen.getByLabelText('Start Date')).toBeInTheDocument()
  })

  it('shows inline error text when an error prop is set', () => {
    render(
      <DateField
        id="startDate"
        label="Start Date"
        value=""
        onChange={vi.fn()}
        error="Invalid date"
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid date')
  })

  it('marks required fields visually and via aria-required', () => {
    render(<DateField id="startDate" label="Start Date" value="" onChange={vi.fn()} required />)
    expect(screen.getByLabelText(/Start Date/)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('*')).toBeInTheDocument()
  })
})

describe('MultiSelectField', () => {
  it('renders a label associated with its select', () => {
    render(
      <MultiSelectField id="roles" label="Roles" value={[]} options={options} onChange={vi.fn()} />,
    )
    expect(screen.getByLabelText('Roles')).toBeInTheDocument()
  })

  it('shows inline error text when an error prop is set', () => {
    render(
      <MultiSelectField
        id="roles"
        label="Roles"
        value={[]}
        options={options}
        onChange={vi.fn()}
        error="Select at least one role"
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Select at least one role')
  })

  it('marks required fields visually and via aria-required', () => {
    render(
      <MultiSelectField
        id="roles"
        label="Roles"
        value={[]}
        options={options}
        onChange={vi.fn()}
        required
      />,
    )
    expect(screen.getByLabelText(/Roles/)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('*')).toBeInTheDocument()
  })
})
