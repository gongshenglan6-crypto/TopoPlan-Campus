import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('角色权限作为用户与权限下的独立后台页面挂载', async () => {
  const [app, workspace] = await Promise.all([
    source('src/App.vue'),
    source('src/components/AdminWorkspace.vue')
  ])

  assert.match(app, /\{ key: 'roles', label: '角色权限' \}/)
  assert.match(workspace, /import AdminRolePermissions from '\.\/admin\/AdminRolePermissions\.vue'/)
  assert.match(workspace, /<AdminRolePermissions v-else-if="activePage === 'roles'" \/>/)
})

test('角色页锁定用户内容访问，并提供后台 RBAC 权限配置', async () => {
  const rolePage = await source('src/components/admin/AdminRolePermissions.vue')

  for (const expectation of [
    '数据访问范围',
    '用户项目内容',
    '系统级隐私策略',
    '后台角色',
    '权限配置',
    '用户与权限',
    '资源与配额',
    '拓扑资源',
    '知识库',
    'AI引擎',
    '安全与风控',
    '系统管理'
  ]) assert.match(rolePage, new RegExp(expectation))

  assert.match(rolePage, /class="role-data-boundary-locked"/)
  assert.match(rolePage, /const permissionModules = \[/)
})

test('角色页具备模板、权限依赖、高风险确认及角色成员抽屉交互', async () => {
  const rolePage = await source('src/components/admin/AdminRolePermissions.vue')

  for (const expectation of [
    'applyTemplate',
    'togglePermission',
    'ensureReadDependencies',
    'pendingRiskPermission',
    'saveConfirmationOpen',
    'memberDrawerOpen',
    '创建并配置权限',
    '角色权限已更新'
  ]) assert.match(rolePage, new RegExp(expectation))
})
