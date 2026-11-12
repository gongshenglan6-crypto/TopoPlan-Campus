import {
  clearSession,
  getAccessToken,
  getRefreshPromise,
  setRefreshPromise,
  setSession
} from '../auth/authStore.js'

export class ApiError extends Error {
  constructor({ status, code, message, details = {}, requestId = '' }) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.status = status
    this.code = code || 'REQUEST_FAILED'
    this.details = details
    this.requestId = requestId || ''
  }
}

function readCookie(name) {
  if (typeof document === 'undefined') return ''
  const prefix = `${name}=`
  return document.cookie.split(';').map((item) => item.trim())
    .find((item) => item.startsWith(prefix))?.slice(prefix.length) || ''
}

async function readPayload(response) {
  const text = await response.text()
  if (!text) return null
  try { return JSON.parse(text) } catch { return { message: text } }
}

async function ensureCsrfToken() {
  let response
  try {
    response = await fetch('/api/auth/csrf', { credentials: 'include' })
  } catch {
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '无法连接到服务，请确认后端已启动。' })
  }
  if (!response.ok) {
    const payload = await readPayload(response)
    throw new ApiError({ status: response.status, code: payload?.code || 'CSRF_UNAVAILABLE', message: payload?.message || '暂时无法建立安全连接，请稍后重试。' })
  }
}

function csrfHeaders() {
  let token = ''
  try { token = decodeURIComponent(readCookie('XSRF-TOKEN')) } catch { /* Ignore a malformed cookie; the server will reject an invalid token. */ }
  return token ? { 'X-CSRF-TOKEN': token } : {}
}

function notifyAuthenticationInvalid(status, code) {
  if (typeof window === 'undefined') return
  if (status === 401 && ['UNAUTHORIZED', 'ACCOUNT_DISABLED'].includes(code)) {
    clearSession()
    window.dispatchEvent(new CustomEvent('topoplan-auth-invalid', { detail: { code } }))
    return
  }
  if (status === 401 && ['REFRESH_REQUIRED', 'REFRESH_INVALID', 'REFRESH_REUSED'].includes(code)) {
    clearSession()
    window.dispatchEvent(new CustomEvent('topoplan-auth-invalid', { detail: { code } }))
  }
}

async function refreshSession() {
  const existing = getRefreshPromise()
  if (existing) return existing
  const promise = (async () => {
    await ensureCsrfToken()
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json', ...csrfHeaders() }
    })
    const payload = await readPayload(response)
    if (!response.ok) {
      clearSession()
      notifyAuthenticationInvalid(response.status, payload?.code)
      throw new ApiError({ status: response.status, code: payload?.code, message: payload?.message || '登录状态已失效', requestId: payload?.requestId })
    }
    setSession(payload?.data)
    return payload?.data
  })()
  setRefreshPromise(promise)
  try { return await promise } finally { setRefreshPromise(null) }
}

export { refreshSession }

function buildHeaders(headers, unsafe) {
  const requestHeaders = { Accept: 'application/json', ...headers }
  const token = getAccessToken()
  if (token && !requestHeaders.Authorization) requestHeaders.Authorization = `Bearer ${token}`
  if (unsafe) Object.assign(requestHeaders, csrfHeaders())
  return requestHeaders
}

export async function apiRequest(path, options = {}) {
  const { method = 'GET', body, headers = {}, skipCsrf = false, skipRefresh = false } = options
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
  if (unsafe && !skipCsrf) await ensureCsrfToken()
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  const requestHeaders = buildHeaders(headers, unsafe)
  if (body !== undefined && !isFormData && !requestHeaders['Content-Type']) requestHeaders['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(path, {
      method,
      credentials: 'include',
      headers: requestHeaders,
      body: body === undefined || isFormData ? body : JSON.stringify(body)
    })
  } catch {
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '无法连接到服务，请确认后端已启动。' })
  }

  const payload = await readPayload(response)
  if (!response.ok) {
    if (!skipRefresh && response.status === 401 && payload?.code === 'TOKEN_EXPIRED' && getAccessToken()) {
      await refreshSession()
      return apiRequest(path, { ...options, skipRefresh: true })
    }
    notifyAuthenticationInvalid(response.status, payload?.code)
    throw new ApiError({ status: response.status, code: payload?.code, message: payload?.message || `请求失败 (${response.status})`, details: payload?.details, requestId: payload?.requestId })
  }
  if (['/api/auth/login', '/api/auth/register', '/api/auth/refresh'].includes(path) && payload?.data) setSession(payload.data)
  return payload?.data
}

async function openStream(path, options, skipRefresh) {
  const { method = 'POST', body, headers = {}, signal } = options
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
  if (unsafe) await ensureCsrfToken()
  // Keep the SSE media type after the shared headers are merged. The previous
  // order let buildHeaders() overwrite it with application/json, causing
  // Spring's text/event-stream endpoint to return 406 before routing models.
  const requestHeaders = { ...buildHeaders(headers, unsafe), Accept: 'text/event-stream, application/json' }
  if (body !== undefined && !requestHeaders['Content-Type']) requestHeaders['Content-Type'] = 'application/json'
  let response
  try {
    response = await fetch(path, {
      method,
      credentials: 'include',
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '无法连接到服务，请确认后端已启动。' })
  }
  if (!response.ok) {
    const payload = await readPayload(response)
    if (!skipRefresh && response.status === 401 && payload?.code === 'TOKEN_EXPIRED' && getAccessToken()) {
      await refreshSession()
      return openStream(path, options, true)
    }
    notifyAuthenticationInvalid(response.status, payload?.code)
    throw new ApiError({ status: response.status, code: payload?.code, message: payload?.message || `请求失败 (${response.status})`, details: payload?.details, requestId: payload?.requestId })
  }
  if (!response.body) throw new ApiError({ status: response.status, code: 'STREAM_UNAVAILABLE', message: '浏览器无法建立 AI 流式连接。' })
  return response
}

export async function apiStream(path, options = {}) {
  const response = await openStream(path, options, false)
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let receivedDone = false

  const dispatch = (rawEvent) => {
    if (!rawEvent) return
    if (import.meta.env?.DEV) {
      const sample = rawEvent.replace(/[\r\n]+/g, ' ').trim()
      console.debug('[FRONTEND_RAW_EVENT]', sample.length > 320 ? `${sample.slice(0, 320)}...` : sample)
    }
    const lines = rawEvent.split(/\r?\n/)
    const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim().toLowerCase() || 'message'
    if (!['message', 'status', 'token', 'delta', 'done', 'error'].includes(event)) return
    const rawData = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).replace(/^ /, '')).join('\n')
    if (!rawData || rawData === '[DONE]') return
    let data
    try { data = JSON.parse(rawData) } catch { return }
    if (!data || typeof data !== 'object' || Array.isArray(data)) return

    // SSE metadata such as id/seq is transport-only and must never become answer text.
    const expectedType = event === 'delta' ? 'token' : event
    if (typeof data.type === 'string' && data.type.trim().toLowerCase() !== expectedType) return
    if (expectedType === 'token' && typeof data.content !== 'string') return
    if (expectedType === 'token' && data.content.includes('\ufffd')) return
    if (typeof data.type !== 'string') data = { ...data, type: expectedType }
    if (event === 'done') receivedDone = true
    options.onEvent?.({ event, data })
  }

  const flushEvents = (flushRemainder = false) => {
    // Retain a trailing CR until the next chunk; CRLF can cross read boundaries.
    buffer = buffer.replace(/\r\n/g, '\n').replace(flushRemainder ? /\r/g : /\r(?!$)/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      dispatch(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
    if (flushRemainder && buffer.trim()) {
      dispatch(buffer)
      buffer = ''
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (value?.byteLength) buffer += decoder.decode(value, { stream: true })
    if (!done) {
      flushEvents(false)
      continue
    }
    // Flush the same UTF-8 decoder once at EOF for a split final code point.
    buffer += decoder.decode()
    flushEvents(true)
    if (!receivedDone) options.onEvent?.({ event: 'eof', data: { type: 'eof' } })
    break
  }
  return { receivedDone }
}
