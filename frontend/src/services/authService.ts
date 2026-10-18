import { post } from './httpClient'

export interface LoginRequest {
  /** Existing account email, used as the username. */
  username: string
  password: string
}

export interface LoginResponse {
  token: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface CompletePasswordChangeRequest {
  username: string
  /** The administrator-issued temporary password. */
  temporaryPassword: string
  newPassword: string
}

/** Backend error code returned (403) by login while an administrator-issued password must be replaced. */
export const PASSWORD_CHANGE_REQUIRED = 'PASSWORD_CHANGE_REQUIRED'

/** Backend error code returned (429) once too many sign-in attempts were made. */
export const TOO_MANY_REQUESTS = 'TOO_MANY_REQUESTS'

/** Issues a session token using the backend's database-backed credential check. */
export const authService = {
  login(credentials: LoginRequest): Promise<LoginResponse> {
    return post<LoginResponse>('/api/auth/login', credentials)
  },

  /** Self-service change for the signed-in user; every existing session of the account ends. */
  changePassword(request: ChangePasswordRequest): Promise<void> {
    return post<void>('/api/auth/change-password', request)
  },

  /** Replaces a temporary password at sign-in (no session exists yet). */
  completePasswordChange(request: CompletePasswordChangeRequest): Promise<void> {
    return post<void>('/api/auth/complete-password-change', request)
  },
}
