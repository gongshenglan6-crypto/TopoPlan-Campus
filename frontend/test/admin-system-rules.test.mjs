import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = () => readFile(new URL('../src/components/admin/AdminSystemManagement.vue', import.meta.url), 'utf8')

test('系统规则在管理抽屉中提供完整配置与显式保存', async () => {
  const rules = await source()

  for (const expectation of [
    'ruleDrawerOpen',
    'openRulesDrawer',
    'resetRules',
    'saveRules',
    '恢复默认',
    '保存规则',
    'AI服务规则',
    '系统资源规则'
  ]) assert.match(rules, new RegExp(expectation))
})

test('规则抽屉支持次数、百分比、超时时间、请求频率和重试次数', async () => {
  const rules = await source()

  for (const expectation of ['次数', '百分比', '超时时间', '请求频率', '重试次数', 'type="number"']) {
    assert.match(rules, new RegExp(expectation))
  }
})
