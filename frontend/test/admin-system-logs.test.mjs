import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('complete logs stay in a right drawer with filters and paging', async () => {
  const logs = await source('src/components/admin/AdminSystemManagement.vue')

  assert.match(logs, /logDrawerOpen/)
  assert.match(logs, /openLogsDrawer/)
  assert.match(logs, /搜索操作、模块或结果/)
  assert.match(logs, /v-model="logLevel"/)
  assert.match(logs, /v-model="logModule"/)
  assert.match(logs, /v-model="logRange"/)
  assert.match(logs, /v-model\.number="logPageSize"/)
  assert.match(logs, /:value="20"/)
  assert.match(logs, /:value="50"/)
  assert.match(logs, /:value="100"/)
  assert.match(logs, /class="system-full-log-table"/)
  assert.match(logs, /aria-label="上一页"/)
  assert.match(logs, /aria-label="下一页"/)
})
