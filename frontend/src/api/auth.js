import { apiRequest, refreshSession } from './http'

export function getCurrentUser() {
  return apiRequest('/api/auth/me')
}

export function login(username, password) {
  return apiRequest('/api/auth/login', { method: 'POST', body: { username, password } })
    .then((session) => session?.user || session)
}

export function register({ avatar = '', nickname, username, email, password }) {
  return apiRequest('/api/auth/register', {
    method: 'POST',
    body: { avatar, nickname, username, email, password }
  }).then((session) => session?.user || session)
}

export { refreshSession }

export function changePassword({ currentPassword, newPassword }) {
  return apiRequest('/api/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } })
}

export function updateProfile({ username, displayName, email, avatar = '' }) {
  return apiRequest('/api/auth/profile', { method: 'PUT', body: { username, displayName, email, avatar } })
}

export function logout() {
  return apiRequest('/api/auth/logout', { method: 'POST' })
}

export function revokeRefreshToken() {
  return apiRequest('/api/auth/refresh/revoke', { method: 'POST' })
}
