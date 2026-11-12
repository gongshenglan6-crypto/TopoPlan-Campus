import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('系统管理移除系统通知入口、权限与发布接口，但保留内部提示机制', async () => {
  const [app, workspace, roles, api, logs, security, styles] = await Promise.all([
    source('src/App.vue'),
    source('src/components/AdminWorkspace.vue'),
    source('src/components/admin/AdminRolePermissions.vue'),
    source('src/api/admin.js'),
    source('src/components/admin/AdminSystemManagement.vue'),
    source('src/components/admin/AdminSecurityRisk.vue'),
    source('src/styles.css')
  ])

  assert.match(app, /key: 'system-management',\s*label: '系统管理'/)
  assert.doesNotMatch(app, /announcements|system-notifications|系统通知/)
  assert.doesNotMatch(workspace, /AdminSystemNotifications|notificationPages|announcements|system-notifications/)
  assert.doesNotMatch(roles, /系统通知|notifications\.(view|publish|edit|revoke)/)
  assert.doesNotMatch(api, /announcements|notification-rules|getNotificationRules|saveNotificationRules/)
  assert.doesNotMatch(logs, /notifications\.rules|系统通知规则/)

  assert.match(security, /toastMessage/)
  assert.match(styles, /\.admin-toast/)
})
