// Client-side mirror of admin-service PasswordPolicy (#14). The server remains authoritative;
// this only gives immediate feedback before submitting.

export const PASSWORD_MIN_LENGTH = 10
export const PASSWORD_MAX_LENGTH = 72

export const PASSWORD_RULES_HINT = `${PASSWORD_MIN_LENGTH}–${PASSWORD_MAX_LENGTH} characters, with at least one letter and one digit.`

/** Returns an error message for the new password, or undefined when it satisfies the policy. */
export function validateNewPassword(
  newPassword: string,
  confirmation: string,
  currentPassword?: string,
): { newPassword?: string; confirmPassword?: string } {
  const errors: { newPassword?: string; confirmPassword?: string } = {}
  if (newPassword.length < PASSWORD_MIN_LENGTH || newPassword.length > PASSWORD_MAX_LENGTH) {
    errors.newPassword = `Password must be between ${PASSWORD_MIN_LENGTH} and ${PASSWORD_MAX_LENGTH} characters.`
  } else if (!/\p{L}/u.test(newPassword) || !/\p{Nd}/u.test(newPassword)) {
    errors.newPassword = 'Password must contain at least one letter and one digit.'
  } else if (currentPassword !== undefined && newPassword === currentPassword) {
    errors.newPassword = 'The new password must be different from the current one.'
  }
  if (confirmation !== newPassword) {
    errors.confirmPassword = 'Passwords do not match.'
  }
  return errors
}
