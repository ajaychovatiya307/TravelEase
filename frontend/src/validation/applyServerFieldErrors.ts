import type { FieldError } from '../models/error'

/**
 * Splits normalized server field errors into errors for currently visible
 * form fields and messages that must be shown in the form-level summary.
 */
export function applyServerFieldErrors<FieldName extends string>(
  serverErrors: readonly FieldError[] | null | undefined,
  visibleFields: readonly FieldName[],
): {
  fieldErrors: Partial<Record<FieldName, string>>
  formErrors: string[]
} {
  const visibleFieldSet = new Set<string>(visibleFields)
  const fieldErrors: Partial<Record<FieldName, string>> = {}
  const formErrors: string[] = []

  for (const serverError of serverErrors ?? []) {
    const field = serverError.field
    if (visibleFieldSet.has(field)) {
      const fieldName = field as FieldName
      if (!(fieldName in fieldErrors)) {
        fieldErrors[fieldName] = serverError.message
      }
    } else {
      formErrors.push(serverError.message)
    }
  }

  return { fieldErrors, formErrors }
}
