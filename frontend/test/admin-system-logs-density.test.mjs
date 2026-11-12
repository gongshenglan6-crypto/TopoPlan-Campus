import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = () => readFile(new URL('../src/components/admin/AdminSystemManagement.vue', import.meta.url), 'utf8')

test('recent system logs render compact 42px rows and preserve drawer paging', async () => {
  const logs = await source()

  assert.match(logs, /recentLogs\.value = items\.map\(normalizeAuditLog\)/)
  assert.match(logs, /<div class="system-log-row system-log-heading"/)
  assert.match(logs, /<b class="system-log-level"/)
  assert.match(logs, /INFO/)
  assert.match(logs, /WARNING/)
  assert.match(logs, /ERROR/)
  assert.match(logs, /SECURITY/)
  assert.match(logs, /v-for="page in visibleLogPages"/)
  assert.match(logs, /<style scoped>/)
  assert.match(logs, /\.system-log-row\s*\{[^}]*min-height:\s*42px;/s)
  assert.match(logs, /\.system-log-list\s*\{[^}]*overflow-y:\s*auto;/s)
  assert.match(logs, /\.system-full-log-table-wrap\s*\{[^}]*overflow:\s*auto;/s)
})
