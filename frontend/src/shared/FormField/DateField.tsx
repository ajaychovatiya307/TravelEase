import { FormFieldWrapper } from './FormFieldWrapper'
import type { BaseFieldProps } from './FormField.types'

interface DateFieldProps extends BaseFieldProps {
  /** ISO date string (yyyy-mm-dd), matching the native date input's value format. */
  value: string
  onChange: (value: string) => void
  min?: string
  max?: string
}

export function DateField({
  id,
  label,
  required,
  wide,
  error,
  value,
  onChange,
  onBlur,
  min,
  max,
}: DateFieldProps) {
  return (
    <FormFieldWrapper fieldId={id} label={label} required={required} error={error} wide={wide}>
      <input
        id={id}
        type="date"
        className="input"
        value={value}
        min={min}
        max={max}
        aria-required={required || undefined}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
      />
    </FormFieldWrapper>
  )
}
