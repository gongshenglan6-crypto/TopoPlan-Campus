import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('AI model management and chat use persistent and streaming API paths', async () => {
  const [modelPage, userApi, httpApi, workspace] = await Promise.all([
    readFile(new URL('../src/components/admin/AdminModelApi.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/user.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/http.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/UserWorkspace.vue', import.meta.url), 'utf8')
  ])

  assert.match(modelPage, /const channels = ref\(\[\]\)/)
  assert.match(modelPage, /testModelChannel/)
  assert.match(modelPage, /requestType: result\.requestType/)
  assert.match(modelPage, /providerMessage: error\?\.details\?\.providerMessage/)
  assert.match(modelPage, /Provider 适配器/)
  assert.match(modelPage, /服务返回信息/)
  assert.doesNotMatch(modelPage, /adminModelPool/)
  assert.doesNotMatch(modelPage, /Promise\.all\(channels\.value\.map/)
  assert.match(userApi, /export function streamChatCompletion/)
  assert.match(userApi, /\/api\/chat\/stream/)
  assert.match(httpApi, /export async function apiStream/)
  assert.match(httpApi, /\['UNAUTHORIZED', 'ACCOUNT_DISABLED'\]\.includes\(code\)/)
  assert.doesNotMatch(httpApi, /if \(status === 401\) \{/)
  assert.match(workspace, /await streamConversation\(/)
  assert.doesNotMatch(workspace, /createAssistantReply/)
})
