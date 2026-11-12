import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('用户等级与用户配额保持独立职责，并统一为三档数据库套餐', async () => {
  const [app, workspace, billing] = await Promise.all([
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/AdminWorkspace.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/admin/AdminBillingQuota.vue', import.meta.url), 'utf8')
  ])

  assert.match(app, /key: 'quota-tiers', label: '用户等级'/)
  assert.match(app, /key: 'quota-users', label: '用户配额'/)
  assert.doesNotMatch(app, /key: 'packages'/)
  assert.doesNotMatch(workspace, /packages: 'tiers'/)

  assert.match(billing, /资源与配额/)
  assert.match(billing, /用户等级/)
  assert.match(billing, /FREE、STANDARD、PRO/)
  assert.match(billing, /免费用户/)
  assert.match(billing, /普通用户/)
  assert.match(billing, /高级用户/)
  assert.match(billing, /listBillingTiers\(\)/)
  assert.match(billing, /refreshQuotaRows/)
  assert.match(billing, /权限配置/)
  assert.match(billing, /用户等级配置已更新/)
  assert.match(billing, /source\.code \?\? source\.name \?\? source\.key \?\? source\.id/)
  assert.doesNotMatch(billing, /新增等级/)
  assert.doesNotMatch(billing, /企业用户|企业版/)
})
