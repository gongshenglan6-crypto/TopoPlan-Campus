import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('the API layer uses same-origin session requests and CSRF headers', async () => {
  const source = await readFile(new URL('../src/api/http.js', import.meta.url), 'utf8').catch(() => '')

  assert.match(source, /credentials: 'include'/)
  assert.match(source, /X-CSRF-TOKEN/)
  assert.match(source, /class ApiError extends Error/)
  assert.match(source, /\/api\/auth\/csrf/)
})

test('the API layer refreshes CSRF even when an old token cookie exists', async () => {
  const source = await readFile(new URL('../src/api/http.js', import.meta.url), 'utf8')

  assert.match(source, /async function ensureCsrfToken\(\) \{[\s\S]*?await fetch\('\/api\/auth\/csrf'/)
  assert.doesNotMatch(source, /if \(readCookie\('XSRF-TOKEN'\)\) return/)
})

test('auth and user API modules expose the frontend integration contract', async () => {
  const [authSource, userSource] = await Promise.all([
    readFile(new URL('../src/api/auth.js', import.meta.url), 'utf8').catch(() => ''),
    readFile(new URL('../src/api/user.js', import.meta.url), 'utf8').catch(() => '')
  ])

  assert.match(authSource, /export function getCurrentUser/)
  assert.match(authSource, /export function login/)
  assert.match(authSource, /export function register/)
  assert.match(authSource, /export function changePassword/)
  assert.match(authSource, /export function updateProfile/)
  assert.match(authSource, /export function logout/)
  assert.match(userSource, /export function listProjects/)
  assert.match(userSource, /export function listConversations/)
  assert.match(userSource, /export function createConversation/)
  assert.match(userSource, /export function getConversationMessages/)
  assert.match(userSource, /export function updateConversation/)
  assert.match(userSource, /export function deleteConversation/)
  assert.match(userSource, /\/api\/conversations/)
  assert.match(userSource, /export function createChatSession/)
  assert.match(userSource, /export function sendChatMessage/)
})
