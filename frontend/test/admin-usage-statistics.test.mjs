import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('使用统计页包含范围筛选、趋势、功能明细和等级分布', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminBillingQuota.vue', import.meta.url), 'utf8')

  assert.match(source, /const overviewRange = ref\('本月'\)/)
  assert.match(source, /usageTrend/)
  assert.match(source, /featureUsage/)
  assert.match(source, /tierUsage/)
  assert.match(source, /usageTopologyMax/)
  assert.match(source, /数据范围/)
  assert.match(source, /AI调用趋势/)
  assert.match(source, /功能使用明细/)
  assert.match(source, /等级使用分布/)
  assert.match(source, /saveBillingOverview/) 
})
