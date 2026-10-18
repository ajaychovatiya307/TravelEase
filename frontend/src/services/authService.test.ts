import { beforeEach, describe, expect, it, vi } from 'vitest'

import { authService } from './authService'

const http = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('./httpClient', () => ({ post: http.post }))

beforeEach(() => {
  http.post.mockReset()
})

describe('authService.login', () => {
  it('posts username and password to the public authentication endpoint', async () => {
    const response = { token: 'signed.jwt.token' }
    const credentials = { username: 'asha.rao@travelease.example', password: 'Unit-test-pass-2026' }
    http.post.mockResolvedValueOnce(response)

    await expect(authService.login(credentials)).resolves.toBe(response)
    expect(http.post).toHaveBeenCalledWith('/api/auth/login', credentials)
  })

  it('propagates an unauthorized login response', async () => {
    const unauthorized = Object.assign(new Error('unauthorized'), { status: 401 })
    http.post.mockRejectedValueOnce(unauthorized)

    await expect(
      authService.login({ username: 'user@travelease.example', password: 'incorrect' }),
    ).rejects.toBe(unauthorized)
  })

  it('posts a self-service password change', async () => {
    http.post.mockResolvedValueOnce(undefined)
    const request = { currentPassword: 'Old-pass-2025', newPassword: 'New-pass-2026' }

    await authService.changePassword(request)
    expect(http.post).toHaveBeenCalledWith('/api/auth/change-password', request)
  })

  it('posts the temporary-password replacement to the public endpoint', async () => {
    http.post.mockResolvedValueOnce(undefined)
    const request = {
      username: 'user@example.com',
      temporaryPassword: 'Temp-pass-0001',
      newPassword: 'New-pass-2026',
    }

    await authService.completePasswordChange(request)
    expect(http.post).toHaveBeenCalledWith('/api/auth/complete-password-change', request)
  })
})
