export interface SelectOption {
  value: string
  label: string
}

export interface BaseFieldProps {
  /** Must be unique on the page; used to associate the label, input, and error message. */
  id: string
  label: string
  required?: boolean
  /** Inline validation error message; when set, renders below the field and flags aria-invalid. */
  error?: string
  onBlur?: () => void
  wide?: boolean
}
