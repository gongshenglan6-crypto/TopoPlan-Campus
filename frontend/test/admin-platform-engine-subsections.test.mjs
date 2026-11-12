import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('model API management exposes engineering model types and use-case bindings', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminModelApi.vue', import.meta.url), 'utf8')

  assert.match(source, /modelTypeOptions/)
  assert.match(source, /modelUseCases/)
  assert.match(source, /需求分析|topology-generation/)
  assert.match(source, /Embedding模型|搜索模型/)
})

test('prompt studio provides engineering templates and synthetic-only test input', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminPromptStudio.vue', import.meta.url), 'utf8')

  for (const template of ['SOLUTION_GENERATION', 'TOPOLOGY_GENERATION', 'RISK_ANALYSIS', 'TOPOLOGY_QA', 'RAG_QA']) {
    assert.match(source, new RegExp(template))
  }
  assert.match(source, /testForm/)
  assert.match(source, /模拟项目/)
  assert.doesNotMatch(source, /真实用户项目进行测试|查看用户项目/)
})

test('security risk page reports platform-level risk aggregates', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminSecurityRisk.vue', import.meta.url), 'utf8')

  assert.match(source, /const metrics = computed/)
  for (const metric of ['今日请求', '风险请求', '已拦截', '黑名单', '待处理事件']) {
    assert.match(source, new RegExp(metric))
  }
})

test('monitoring page covers required services and runtime metrics', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminMonitoring.vue', import.meta.url), 'utf8')

  assert.match(source, /requiredServices/)
  for (const service of ['api-service', 'ai-service', 'topology-service', 'rag-service', 'search-service', 'document-service', 'mysql', 'redis']) {
    assert.match(source, new RegExp(service))
  }
  for (const field of ['responseTime', 'requestCount', 'errorRate', 'cpu', 'memory']) {
    assert.match(source, new RegExp(field))
  }
})
