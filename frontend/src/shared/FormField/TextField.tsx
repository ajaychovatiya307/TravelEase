import { FormFieldWrapper } from './FormFieldWrapper'
import type { BaseFieldProps } from './FormField.types'

interface TextFieldProps extends BaseFieldProps {
  value: string
  onChange: (value: string) => void
  /** `'datetime-local'` added additively for timestamp fields (J-05 `ReimburseForm`); the value format is `YYYY-MM-DDTHH:mm`. */
  type?: 'text' | 'email' | 'password' | 'number' | 'datetime-local'
  placeholder?: string
  autoComplete?: string
  icon?: string
}

export function TextField({
  id,
  label,
  required,
  wide,
  error,
  value,
  onChange,
  onBlur,
  type = 'text',
  placeholder,
  autoComplete,
  icon,
}: TextFieldProps) {
  return (
    <FormFieldWrapper fieldId={id} label={label} required={required} error={error} wide={wide}>
      {icon ? (
        <div className="input-with-icon">
          <i className={`icon icon-${icon}`} />
          <input
            id={id}
            type={type}
            autoComplete={autoComplete}
            className="input"
            value={value}
            placeholder={placeholder}
            aria-required={required || undefined}
            aria-invalid={Boolean(error) || undefined}
            aria-describedby={error ? `${id}-error` : undefined}
            onChange={(event) => onChange(event.target.value)}
            onBlur={onBlur}
          />
        </div>
      ) : (
        <input
          id={id}
          type={type}
          autoComplete={autoComplete}
          className="input"
          value={value}
          placeholder={placeholder}
          aria-required={required || undefined}
          aria-invalid={Boolean(error) || undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
        />
      )}
    </FormFieldWrapper>
  )
}
