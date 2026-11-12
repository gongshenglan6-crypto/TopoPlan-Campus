import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('系统管理使用单一入口，并且不恢复系统通知', async () => {
  const source = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(source, /\{\s*key: 'system-management',\s*label: '系统管理',\s*icon: Settings2\s*\}/)
  assert.doesNotMatch(source, /label: '系统规则'|label: '系统日志'|label: '服务监控'/)
  assert.doesNotMatch(source, /announcements|system-notifications|系统通知/)
})
