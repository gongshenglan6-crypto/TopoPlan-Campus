import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('admin navigation is organized around platform management domains', async () => {
  const source = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  for (const label of ['系统总览', '用户与权限', '资源与配额', '拓扑资源', '知识库', 'AI 引擎', '安全与风控', '系统管理']) {
    assert.match(source, new RegExp(`label: '${label}'`))
  }
  assert.match(source, /key: 'devices', label: '拓扑资源'/)
  assert.match(source, /key: 'system-management',\s*label: '系统管理'/)
  assert.doesNotMatch(source, /查看用户项目|查看用户拓扑|查看工程方案|查看AI聊天记录/)
})

test('admin workspace mounts topology assets and keeps knowledge route platform-only', async () => {
  const source = await readFile(new URL('../src/components/AdminWorkspace.vue', import.meta.url), 'utf8')

  assert.match(source, /AdminDeviceLibrary/)
  assert.match(source, /activePage === 'devices'/)
  assert.match(source, /activePage === 'assets' \\|\\| activePage === 'knowledge'/)
  assert.doesNotMatch(source, /AdminProjectReview/)
})

test('AI engine only exposes model API and Prompt management pages', async () => {
  const [app, workspace, rolePermissions] = await Promise.all([
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/AdminWorkspace.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/admin/AdminRolePermissions.vue', import.meta.url), 'utf8')
  ])

  for (const source of [app, workspace, rolePermissions]) {
    assert.doesNotMatch(source, /ai-tasks|search-config|AI ?任务配置|联网搜索配置/)
  }
})
