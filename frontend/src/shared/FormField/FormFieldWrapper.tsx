import type { ReactNode } from 'react'
import './FormField.css'

interface FormFieldWrapperProps {
  fieldId: string
  label: string
  required?: boolean
  error?: string
  wide?: boolean
  children: ReactNode
}

/** Shared label + inline-error + required-marker chrome used by every FormField variant (frontend-constitution.md §11, §13). */
export function FormFieldWrapper({
  fieldId,
  label,
  required,
  error,
  wide = false,
  children,
}: FormFieldWrapperProps) {
  return (
    <div className={wide ? 'field field-wide' : 'field'}>
      <label htmlFor={fieldId}>
        {label}
        {required ? (
          <span className="required" aria-hidden="true">
            {' '}
            *
          </span>
        ) : null}
      </label>
      {children}
      {error ? (
        <p id={`${fieldId}-error`} className="required" style={{ fontSize: 13 }} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
