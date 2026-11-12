import test from 'node:test'
import assert from 'node:assert/strict'
import { apiRequest, apiStream } from '../src/api/http.js'

test('a failed security handshake prevents sending a login request', async () => {
  const original = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url) => { calls.push(url); return new Response('unavailable', { status: 503 }) }
  try {
    await assert.rejects(apiRequest('/api/auth/login', { method: 'POST', body: {} }), e => e.code === 'CSRF_UNAVAILABLE' && e.status === 503)
    assert.deepEqual(calls, ['/api/auth/csrf'])
  } finally { globalThis.fetch = original }
})

test('offline login has an actionable network error', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => { throw new TypeError('fetch failed') }
  try {
    await assert.rejects(apiRequest('/api/auth/login', { method: 'POST' }), e => e.code === 'NETWORK_ERROR')
  } finally { globalThis.fetch = original }
})

test('SSE preserves events when CRLF and UTF-8 are split between chunks', async () => {
  const original = globalThis.fetch
  const received = []
  const encoder = new TextEncoder()
  const bytes = encoder.encode('event: token\r\ndata: {"content":"你好"}\r\n\r\nevent: done\r\ndata: {}\r\n\r\n')
  globalThis.fetch = async () => new Response(new ReadableStream({
    start(controller) { for (const byte of bytes) controller.enqueue(new Uint8Array([byte])); controller.close() }
  }))
  try {
    const result = await apiStream('/stream', { method: 'GET', onEvent: e => received.push(e) })
    assert.equal(result.receivedDone, true)
    assert.deepEqual(received.map(e => e.event), ['token', 'done'])
    assert.equal(received[0].data.content, '你好')
  } finally { globalThis.fetch = original }
})
