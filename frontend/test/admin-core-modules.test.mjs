import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { parseAppRoute } from '../src/utils/appRouting.js'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('admin data contracts cover tiers, documents, security, models, and rules', async () => {
  const types = await source('src/types/admin.ts')

  for (const contract of [
    'export interface TierConfig',
    'monthlyTokenLimit',
    'maxMonthlyCalls',
    'dailyCallLimit',
    'maxContextWindow',
    'export interface KnowledgeDocument',
    'export interface BlacklistEntry',
    'export interface ModelChannel',
    'export interface AlertRule'
  ]) assert.match(types, new RegExp(contract))
  assert.doesNotMatch(types, /NotificationChannel/)
})

test('representative admin surfaces expose the required management controls', async () => {
  const [security, rules, knowledge, models, billing] = await Promise.all([
    source('src/components/admin/AdminSecurityRisk.vue'),
    source('src/components/admin/AdminSystemRules.vue'),
    source('src/components/admin/AdminKnowledgeDocuments.vue'),
    source('src/components/admin/AdminModelApi.vue'),
    source('src/components/admin/AdminBillingQuota.vue')
  ])

  assert.match(security, /const rules = ref\(\[/)
  assert.match(security, /黑名单管理/)
  assert.match(security, /blacklistType/)
  assert.match(rules, /const ruleGroups = ref\(/)
  assert.match(rules, /AI服务规则/)
  assert.match(rules, /安全与系统规则/)
  assert.doesNotMatch(rules, /notification|announcement/i)
  assert.match(knowledge, /downloadDocument/)
  assert.match(knowledge, /deleteKnowledgeDocument/)
  assert.match(models, /modelCategoryTabs/)
  assert.match(models, /latency/)
  assert.match(billing, /tierConfigs/)
})

test('knowledge route alias resolves to the knowledge-base admin page', () => {
  const route = parseAppRoute('/admin/knowledge', ['dashboard', 'assets'])

  assert.deepEqual(route, {
    mode: 'admin',
    adminPage: 'assets',
    chatId: null,
    userPage: 'new-chat'
  })
})
