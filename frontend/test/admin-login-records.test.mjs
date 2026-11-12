import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8').catch(() => '')

test('login records uses persisted API data with server-side filters, pagination, and detail drawer', async () => {
  const [workspace, page] = await Promise.all([
    source('src/components/AdminWorkspace.vue'),
    source('src/components/admin/AdminLoginRecords.vue')
  ])

  assert.match(workspace, /AdminLoginRecords/)
  assert.match(workspace, /activePage === 'login-records'/)
  assert.match(page, /登录记录/)
  assert.match(page, /搜索用户昵称 \/ 用户ID \/ IP地址/)
  assert.match(page, /今日登录/)
  assert.match(page, /登录成功/)
  assert.match(page, /登录失败/)
  assert.match(page, /异常登录/)
  assert.match(page, /listAdminLoginRecords/)
  assert.match(page, /getAdminLoginRecordStats/)
  assert.match(page, /onMounted\(\(\) => loadRecords\(\{ includeStats: true \}\)\)/)
  assert.match(page, /账号类型/)
  assert.match(page, /登录结果/)
  assert.match(page, /风险状态/)
  assert.match(page, /登录方式/)
  assert.match(page, /登录详情/)
  assert.match(page, /failureReason/)
  assert.doesNotMatch(page, /createLoginRecords/)
  assert.doesNotMatch(page, /seedProfiles/)
  assert.match(page, /v-if="isRefreshing"/)
  assert.match(page, /登录记录仅用于平台账号安全审计，不记录或展示用户项目内容。/)
  assert.doesNotMatch(page, /网络拓扑|AI问答|工程文档/)
})
