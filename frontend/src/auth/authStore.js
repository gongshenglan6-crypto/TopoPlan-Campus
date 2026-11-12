let accessToken = ''
let currentUser = null
let refreshPromise = null
let authStatus = 'loading'

export function getAccessToken() {
  return accessToken
}

export function getCurrentUserSnapshot() {
  return currentUser
}

export function getAuthStatus() {
  return authStatus
}

export function setSession(session) {
  accessToken = session?.accessToken || ''
  currentUser = session?.user || (session?.username ? session : null)
  authStatus = currentUser ? 'authenticated' : 'unauthenticated'
  return currentUser
}

export function clearSession() {
  accessToken = ''
  currentUser = null
  authStatus = 'unauthenticated'
}

export function getRefreshPromise() {
  return refreshPromise
}

export function setRefreshPromise(promise) {
  refreshPromise = promise
  return promise
}
