import { FormFieldWrapper } from './FormFieldWrapper'
import type { BaseFieldProps } from './FormField.types'

interface TextareaFieldProps extends BaseFieldProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  rows?: number
}

export function TextareaField({
  id,
  label,
  required,
  wide,
  error,
  value,
  onChange,
  onBlur,
  placeholder,
  rows = 4,
}: TextareaFieldProps) {
  return (
    <FormFieldWrapper fieldId={id} label={label} required={required} error={error} wide={wide}>
      <textarea
        id={id}
        className="input"
        value={value}
        placeholder={placeholder}
        rows={rows}
        aria-required={required || undefined}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
      />
    </FormFieldWrapper>
  )
}
