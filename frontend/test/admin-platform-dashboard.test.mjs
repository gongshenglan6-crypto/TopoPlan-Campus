import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('dashboard exposes platform-level topology and AI operating metrics', async () => {
  const source = (await Promise.all(['../src/components/admin/AdminDashboard.vue', '../../backend/demo1/src/main/java/com/agent/admin/dashboard/AdminDashboardService.java'].map(p => readFile(new URL(p, import.meta.url),'utf8')))).join('\n')

  for (const copy of ['系统总览', '今日拓扑绘制', 'AI拓扑生成', '工程文档导出', '拓扑创建方式', '近 30 天平台使用趋势', '常用网络设备', 'AI 能力运行情况']) {
    assert.match(source, new RegExp(copy))
  }
  assert.match(source, /creationModes/)
  assert.match(source, /platformData/)
  assert.match(source, /90 天/)
  assert.match(source, /平均响应时间/)
  assert.doesNotMatch(source, /用户项目|打开拓扑|查看方案|完整 Prompt/)
})
