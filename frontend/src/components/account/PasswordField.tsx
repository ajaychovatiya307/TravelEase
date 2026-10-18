interface PasswordFieldProps {
  id: string
  label: string
  autoComplete: 'current-password' | 'new-password'
  value: string
  error?: string
  hint?: string
  onChange: (value: string) => void
}

/** Labelled password input with an inline error, shared by the password-change forms (#14). */
export function PasswordField({ id, label, autoComplete, value, error, hint, onChange }: PasswordFieldProps) {
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null]
    .filter(Boolean)
    .join(' ')
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}{' '}
        <span className="required" aria-hidden="true">
          *
        </span>
      </label>
      <input
        className="input"
        id={id}
        type="password"
        autoComplete={autoComplete}
        required
        value={value}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {hint ? (
        <p id={`${id}-hint`} className="field-hint" style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="required" role="alert" style={{ fontSize: 13 }}>
          {error}
        </p>
      ) : null}
    </div>
  )
}
