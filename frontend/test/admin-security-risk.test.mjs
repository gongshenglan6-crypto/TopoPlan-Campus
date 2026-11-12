import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('security risk is a unified audit page with policies, blacklist, and events', async () => {
  const [app, workspace, security, defaults] = await Promise.all([
    source('src/App.vue'),
    source('src/components/AdminWorkspace.vue'),
    source('src/components/admin/AdminSecurityRisk.vue'),
    source('../backend/demo1/src/main/java/com/agent/admin/security/RiskPolicyDefaults.java')
  ])

  assert.match(app, /\{\s*key: 'security',\s*label: '安全与风控',\s*icon: ShieldCheck\s*\}/)
  assert.doesNotMatch(app, /key: 'blacklist'|key: 'security-events'/)
  assert.match(workspace, /const securityPages = \['security'\]/)
  assert.match(workspace, /AdminSecurityRisk v-else-if="securityPages.includes\(activePage\)"/)

  for (const label of ['今日请求', '风险请求', '已拦截', '黑名单', '待处理事件', '风控策略', '黑名单管理', '安全事件']) {
    assert.match(security, new RegExp(label))
  }
  for (const rule of ['登录失败防护', '单IP请求限流', 'AI高频调用检测', 'Token异常消耗检测', 'API连续失败保护', '异常登录检测', '敏感内容检测', '高频导出限制']) {
    assert.match(defaults, new RegExp(rule))
  }
  assert.match(security, /rules/)
  assert.match(security, /blacklistType/)
  assert.match(security, /eventRisk/)
  assert.match(security, /openRule/)
  assert.match(security, /openBlacklist/)
  assert.match(security, /selectedEvent/)
  assert.match(security, /executeConfirm/)
  assert.match(security, /每页/)
  assert.match(security, /User Agent|Trace ID/)
})
