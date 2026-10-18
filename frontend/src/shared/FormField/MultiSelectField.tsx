import { FormFieldWrapper } from './FormFieldWrapper'
import type { BaseFieldProps, SelectOption } from './FormField.types'

interface MultiSelectFieldProps extends BaseFieldProps {
  value: string[]
  options: SelectOption[]
  onChange: (value: string[]) => void
  size?: number
}

export function MultiSelectField({
  id,
  label,
  required,
  error,
  value,
  options,
  onChange,
  onBlur,
  size,
}: MultiSelectFieldProps) {
  return (
    <FormFieldWrapper fieldId={id} label={label} required={required} error={error}>
      <select
        id={id}
        multiple
        className="form-field__select"
        value={value}
        size={size ?? Math.min(options.length, 5)}
        aria-required={required || undefined}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) =>
          onChange(Array.from(event.target.selectedOptions, (option) => option.value))
        }
        onBlur={onBlur}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FormFieldWrapper>
  )
}
