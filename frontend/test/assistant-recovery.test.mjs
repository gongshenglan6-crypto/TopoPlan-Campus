import test from 'node:test'
import assert from 'node:assert/strict'
import { retryAssistantRead, assistantFailureState } from '../src/utils/assistantRecovery.js'

test('login initialization recovers from a transient read failure', async () => {
  let calls = 0
  const result = await retryAssistantRead(async () => {
    if (++calls === 1) throw { status: 503 }
    return ['available-model']
  }, async () => {})
  assert.deepEqual(result, ['available-model'])
  assert.equal(calls, 2)
})

test('authentication failures are surfaced without retry', async () => {
  let calls = 0
  await assert.rejects(retryAssistantRead(async () => {
    calls++
    throw Object.assign(new Error('login expired'), { status: 401 })
  }, async () => {}), /login expired/)
  assert.equal(calls, 1)
})

test('a persistent read failure stops after one retry', async () => {
  let calls = 0
  await assert.rejects(retryAssistantRead(async () => {
    calls++
    throw Object.assign(new Error('offline'), { status: 0 })
  }, async () => {}), /offline/)
  assert.equal(calls, 2)
})

test('a failed provider request does not mark all AI connections unavailable', () => {
  assert.equal(assistantFailureState({ code: 'PROVIDER_UNAVAILABLE' }), 'degraded')
  assert.equal(assistantFailureState({ code: 'MODEL_DISABLED' }), 'unavailable')
  assert.equal(assistantFailureState({ status: 401 }), 'error')
})
