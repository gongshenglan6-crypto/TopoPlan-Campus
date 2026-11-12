import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('admin API wrapper exposes dashboard and user management operations', async () => {
  const source = await readFile(new URL('../src/api/admin.js', import.meta.url), 'utf8')
  assert.match(source, /getAdminDashboard/)
  assert.match(source, /createAdminUser/)
  assert.match(source, /updateAdminUser/)
  assert.match(source, /setAdminUserStatus/)
  assert.match(source, /resetAdminUserPassword/)
  assert.match(source, /listAdminLoginRecords/)
  assert.match(source, /getAdminLoginRecordStats/)
  assert.match(source, /api\/admin\/users/)
  assert.match(source, /api\/admin\/login-records/)
})

test('admin pages consume the API instead of dashboard and user mock arrays', async () => {
  const [dashboard, users] = await Promise.all([
    readFile(new URL('../src/components/admin/AdminDashboard.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/admin/AdminUsersPanel.vue', import.meta.url), 'utf8')
  ])
  assert.match(dashboard, /getAdminDashboard/)
  assert.match(users, /listAdminUsers/)
  assert.match(users, /createAdminUser/)
  assert.match(users, /updateAdminUser/)
  assert.doesNotMatch(users, /student01@topoplan\.cn/)
})

test('admin configuration pages load and save through persistent APIs', async () => {
  const pages = await Promise.all([
    'AdminBillingQuota.vue',
    'AdminKnowledgeBase.vue',
    'AdminDeviceLibrary.vue',
    'AdminModelApi.vue',
    'AdminPromptStudio.vue',
    'AdminSecurityRisk.vue',
    'AdminSystemRules.vue',
    'AdminSystemLogs.vue'
  ].map((name) => readFile(new URL(`../src/components/admin/${name}`, import.meta.url), 'utf8')))

  for (const source of pages) {
    assert.match(source, /from '..\/..\/api\/admin'/)
    assert.match(source, /onMounted/)
  }

  const billing = pages[0]
  const logs = pages.at(-1)
  assert.match(billing, /getBillingOverview/)
  assert.match(billing, /saveBillingOverview/)
  assert.match(billing, /monthlyCost/)
  assert.match(logs, /const logs = ref\(\[\]\)/)
})

test('model configuration persists API key status without hydrating a secret into Vue state', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminModelApi.vue', import.meta.url), 'utf8')
  assert.match(source, /apiKeyConfigured/)
  assert.match(source, /serializableChannel\(channel\)/)
  assert.doesNotMatch(source, /const \{ apiKey, id, key, \.\.\.payload \} = channel/)
  assert.doesNotMatch(source, /if \(editingChannelId\.value\) \{\n\s*const channel = channels\.value\.find/)
})
