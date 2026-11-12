import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('resource overview presents real usage trends with loading, error, and empty states', async () => {
 const page=await readFile(new URL('../src/components/admin/AdminBillingQuota.vue',import.meta.url),'utf8')
 assert.doesNotMatch(page,/admin-cost-budget|预算上限|预算执行/)
 for(const marker of ['getBillingOverview','overviewLoading','overviewError','hasTrendData','resource-trend-panel','resource-feature-panel','resource-tier-panel','resource-summary-panel']) assert.ok(page.includes(marker),marker)
 assert.match(page,/@click="loadBillingConfiguration"/)
 assert.match(page,/\.resource-usage-panel\s*\{[^}]*min-width:\s*0;/)
 assert.match(page,/当前统计周期暂无使用数据/)
})
