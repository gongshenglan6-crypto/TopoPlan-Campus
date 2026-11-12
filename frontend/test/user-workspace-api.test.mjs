import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('the user workspace persists projects and conversations through the API layer', async () => {
  const source = await readFile(new URL('../src/components/UserWorkspace.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{[^}]*createConversation[^}]*createProject[^}]*getConversationMessages[^}]*streamConversation[^}]*\} from '\.\.\/api\/user'/)
  assert.match(source, /emit\('project-created', project\)/)
  assert.match(source, /emit\('chat-created', session\)/)
  assert.match(source, /await streamConversation\(/)
  assert.match(source, /await getConversationMessages\(/)
  assert.match(source, /conversationAccessError/)
  assert.doesNotMatch(source, /searchableConversations/)
  assert.doesNotMatch(source, /createAssistantReply/)
})

test('the first message survives new-conversation routing and offers a retry on failure', async () => {
  const [workspace, userApi] = await Promise.all([
    readFile(new URL('../src/components/UserWorkspace.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/user.js', import.meta.url), 'utf8')
  ])

  assert.match(workspace, /let userMessage = createMessage\('user', content, \{ status: 'sending' \}\)/)
  assert.match(workspace, /newChatMessages\.value = targetMessages/)
  assert.match(workspace, /pendingChatHandoffId = sessionId/)
  assert.match(workspace, /hydrateChatMessages\(chatId, data\.items \?\? \[\]\)/)
  assert.match(workspace, /\{ immediate: true \}/)
  assert.match(workspace, /userMessage\.status = 'failed'/)
  assert.match(workspace, /@click="retryFailedMessage\(message\)"/)
  assert.match(workspace, /if \(data\?\.conversation\) emit\('chat-updated', data\.conversation\)/)
  assert.match(userApi, /apiRequest\('\/api\/conversations'/)
  assert.match(userApi, /\/api\/conversations\/\$\{conversationId\}\/messages/)
})

test('the topology editor loads and saves the active project topology', async () => {
  const source = await readFile(new URL('../src/components/TopologyWorkspace.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ getTopology, saveTopology \} from '\.\.\/api\/user'/)
  assert.match(source, /defineProps\(/)
  assert.match(source, /await getTopology\(props\.projectId\)/)
  assert.match(source, /await saveTopology\(props\.projectId/)
})
