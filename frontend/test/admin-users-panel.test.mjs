import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('the user management surface exposes user creation, role assignment, and row actions', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminUsersPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /添加用户/)
  assert.match(source, /系统角色/)
  assert.match(source, /编辑信息\/权限/)
  assert.match(source, /生成随机密码并复制/)
  assert.match(source, /登录账号：\{\{ user\.username \|\| '-' \}\}/)
  assert.match(source, /v-model="userDraft\.username"/)
  assert.match(source, /用户可使用用户名或邮箱登录/)
})
