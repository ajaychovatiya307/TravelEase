import { FormFieldWrapper } from './FormFieldWrapper'
import type { BaseFieldProps, SelectOption } from './FormField.types'

interface SelectFieldProps extends BaseFieldProps {
  value: string
  options: SelectOption[]
  onChange: (value: string) => void
  placeholder?: string
}

export function SelectField({
  id,
  label,
  required,
  error,
  value,
  options,
  onChange,
  onBlur,
  placeholder,
}: SelectFieldProps) {
  return (
    <FormFieldWrapper fieldId={id} label={label} required={required} error={error}>
      <select
        id={id}
        className="input"
        value={value}
        aria-required={required || undefined}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
      >
        {placeholder ? <option value="">{placeholder}</option> : null}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FormFieldWrapper>
  )
}
