<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Activity,
  Bot,
  ChevronLeft,
  ChevronRight,
  FileText,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Server,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  X
} from '@lucide/vue'
import { getMonitoring, getSystemRules, listAuditLogs, saveSystemRules } from '../../api/admin'

const REFRESH_INTERVAL = 1_000

const thresholdTypes = [
  { key: 'count', label: '次数', unit: '次', min: 0, step: 1 },
  { key: 'percentage', label: '百分比', unit: '%', min: 0, step: 1 },
  { key: 'duration', label: '超时时间', unit: '秒', min: 0, step: 1 },
  { key: 'frequency', label: '请求频率', unit: '次/分钟', min: 0, step: 1 },
  { key: 'retry', label: '重试次数', unit: '次', min: 0, step: 1 }
]

const defaultRuleGroups = [
  {
    id: 'ai-service',
    label: 'AI服务规则',
    icon: Bot,
    rules: [
      { id: 'ai-fallback-model', label: 'AI连续失败自动切换模型', enabled: true, threshold: { type: 'count', value: 3 } },
      { id: 'ai-token-warning', label: 'Token使用达到80%预警', enabled: true, threshold: { type: 'percentage', value: 80 } },
      { id: 'ai-timeout-retry', label: 'AI请求超时自动重试', enabled: true, threshold: { type: 'duration', value: 60 } }
    ]
  },
  {
    id: 'topology-service',
    label: '拓扑服务规则',
    icon: Settings2,
    rules: [
      { id: 'topology-retry', label: '拓扑生成失败自动重试', enabled: true, threshold: { type: 'retry', value: 2 } },
      { id: 'topology-timeout', label: '拓扑生成超时预警', enabled: true, threshold: { type: 'duration', value: 180 } },
      { id: 'topology-node-limit', label: '拓扑任务节点数量限制', enabled: true, threshold: { type: 'count', value: 300 } }
    ]
  },
  {
    id: 'knowledge-base',
    label: '知识库规则',
    icon: FileText,
    rules: [
      { id: 'knowledge-parse-retry', label: 'RAG解析失败自动重试', enabled: true, threshold: { type: 'retry', value: 3 } },
      { id: 'knowledge-vector-warning', label: '向量化失败预警', enabled: true, threshold: { type: 'count', value: 5 } },
      { id: 'knowledge-rag-rate', label: 'RAG检索失败率预警', enabled: true, threshold: { type: 'percentage', value: 5 } }
    ]
  },
  {
    id: 'security',
    label: '安全规则',
    icon: ShieldCheck,
    rules: [
      { id: 'security-ip-rate', label: '单IP高频请求自动限流', enabled: true, threshold: { type: 'frequency', value: 120 } },
      { id: 'security-login-failure', label: '连续登录失败触发风控', enabled: true, threshold: { type: 'count', value: 5 } },
      { id: 'security-export-limit', label: '敏感数据导出频率限制', enabled: true, threshold: { type: 'frequency', value: 10 } }
    ]
  },
  {
    id: 'system-resource',
    label: '系统资源规则',
    icon: Activity,
    rules: [
      { id: 'system-cpu-warning', label: 'CPU使用率达到阈值预警', enabled: true, threshold: { type: 'percentage', value: 85 } },
      { id: 'system-memory-warning', label: '内存使用率达到阈值预警', enabled: true, threshold: { type: 'percentage', value: 80 } },
      { id: 'system-health-timeout', label: '服务健康检查超时告警', enabled: true, threshold: { type: 'duration', value: 10 } }
    ]
  }
]

const cloneRuleGroups = (groups) => groups.map((group) => ({
  ...group,
  rules: group.rules.map((rule) => ({ ...rule, threshold: { ...rule.threshold } }))
}))

const ruleGroups = ref(cloneRuleGroups(defaultRuleGroups))
const monitoring = ref({ status: 'CHECKING', checkedAt: null, metrics: { errorCount: 0, todayLogCount: 0, activeServices: 0, totalServices: 0 }, services: [] })
const lastUpdatedAt = ref(null)
const recentLogs = ref([])
const logs = ref([])
const totalLogCount = ref(0)
const logTotal = ref(0)
const monitoringError = ref('')
const isRefreshing = ref(false)
const pollingInFlight = ref(false)
const isSavingRules = ref(false)
const rulesDirty = ref(false)
const ruleDrawerOpen = ref(false)
const logDrawerOpen = ref(false)
const toast = ref('')
const logQuery = ref('')
const logLevel = ref('all')
const logModule = ref('all')
const logRange = ref('today')
const logPage = ref(1)
const logPageSize = ref(20)

let refreshTimer = 0
let toastTimer = 0
let monitorTick = 0

const allRules = computed(() => ruleGroups.value.flatMap((group) => group.rules))
const importantRuleIds = ['ai-fallback-model', 'ai-token-warning', 'topology-retry', 'knowledge-parse-retry', 'security-ip-rate', 'security-login-failure']
const summaryRules = computed(() => importantRuleIds.map((id) => allRules.value.find((rule) => rule.id === id)).filter(Boolean))
const services = computed(() => (monitoring.value.services || []).map((service) => normalizeService(service, service.label || service.name)))
const onlineServices = computed(() => services.value.filter((service) => service.status === 'UP').length)
const aiService = computed(() => services.value.find((service) => service.name === 'ai-service'))
const isSystemHealthy = computed(() => monitoring.value.status === 'UP')
const summaryMetrics = computed(() => [
  { label: '运行服务', value: `${onlineServices.value} / ${services.value.length}`, icon: Server, tone: 'blue' },
  { label: 'AI服务状态', value: serviceStatusLabel(aiService.value?.status || 'CHECKING'), icon: Bot, tone: 'green' },
  { label: '今日异常', value: String(monitoring.value.metrics?.errorCount ?? 0), icon: Activity, tone: 'amber' },
  { label: '今日系统日志', value: Number(monitoring.value.metrics?.todayLogCount ?? totalLogCount.value ?? 0).toLocaleString('zh-CN'), icon: FileText, tone: 'violet' }
])
const modules = computed(() => {
  const seen = new Map()
  ;[...recentLogs.value, ...logs.value].forEach((log) => { if (log.sourceModule) seen.set(log.sourceModule, log.module) })
  return [...seen.entries()].map(([value, label]) => ({ value, label }))
})
const logPages = computed(() => Math.max(1, Math.ceil(logTotal.value / logPageSize.value)))
const pagedLogs = computed(() => logs.value)
const visibleLogPages = computed(() => {
  const total = logPages.value
  const start = Math.max(1, Math.min(logPage.value - 2, total - 4))
  return Array.from({ length: Math.min(5, total) }, (_, index) => start + index)
})

let logQueryTimer = 0
watch([logQuery, logLevel, logModule, logRange, logPageSize], () => {
  logPage.value = 1
  window.clearTimeout(logQueryTimer)
  logQueryTimer = window.setTimeout(() => { if (logDrawerOpen.value) void loadDrawerLogs() }, 180)
})
watch(logPage, () => { if (logDrawerOpen.value) void loadDrawerLogs() })
watch(logDrawerOpen, (open) => { if (open) void loadDrawerLogs() })

function normalizeService(service, fallbackLabel) {
  const status = String(service.status || 'CHECKING').toUpperCase()
  return {
    ...service,
    label: service.label || fallbackLabel,
    status: ['UP', 'DEGRADED', 'DOWN', 'CHECKING', 'UNKNOWN', 'NOT_CONFIGURED'].includes(status) ? status : 'CHECKING'
  }
}

function serviceStatusLabel(status) {
  return { UP: '正常', DEGRADED: '异常', DOWN: '离线', CHECKING: '检查中', UNKNOWN: '未知', NOT_CONFIGURED: '未配置' }[status] || '未知'
}

function formatRuleThreshold(rule) {
  const threshold = rule.threshold || { type: 'count', value: 0 }
  const type = thresholdTypes.find((item) => item.key === threshold.type) || thresholdTypes[0]
  return `${threshold.value}${type.unit}`
}

function formatResponse(value) {
  if (value === null || value === undefined || value === '') return '-'
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '-'
  return numeric >= 1000 ? `${(numeric / 1000).toFixed(1)}s` : `${Math.round(numeric)}ms`
}

function formatPercent(value) {
  if (value === null || value === undefined || value === '') return '-'
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${numeric.toFixed(2)}%` : '-'
}

function formatWholePercent(value) {
  if (value === null || value === undefined || value === '') return '-'
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${Math.round(numeric)}%` : '-'
}

function formatTime(value) {
  if (!value || Number.isNaN(new Date(value).getTime())) return '--:--:--'
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
}

function levelClass(level) {
  return `is-${String(level || 'INFO').toLowerCase()}`
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

function toggleRule(rule) {
  rule.enabled = !rule.enabled
  rulesDirty.value = true
}

function openRulesDrawer() {
  ruleDrawerOpen.value = true
}

function resetRules() {
  ruleGroups.value = cloneRuleGroups(defaultRuleGroups)
  rulesDirty.value = true
  showToast('已恢复默认规则，请保存后生效')
}

async function saveRules() {
  isSavingRules.value = true
  try {
    await saveSystemRules({ ruleGroups: ruleGroups.value })
    rulesDirty.value = false
    showToast('规则配置已保存')
  } catch (error) {
    showToast(error.message || '规则配置保存失败')
  } finally {
    isSavingRules.value = false
  }
}

function normalizeIncomingRuleGroups(groups) {
  if (!Array.isArray(groups)) return null
  const currentRules = new Map(groups.flatMap((group) => (group.rules || []).map((rule) => [rule.id, rule])))
  const next = cloneRuleGroups(defaultRuleGroups)
  next.forEach((group) => group.rules.forEach((rule) => {
    const stored = currentRules.get(rule.id)
    if (!stored) return
    rule.enabled = stored.enabled !== false
    if (stored.threshold?.type && stored.threshold?.value !== undefined) rule.threshold = { ...stored.threshold }
  }))
  return next
}

async function loadRules() {
  try {
    const data = await getSystemRules()
    const restored = normalizeIncomingRuleGroups(data?.ruleGroups)
    if (restored) ruleGroups.value = restored
  } catch {
    // Keep the operational defaults visible when the configuration service is unavailable.
  }
}

async function loadMonitoring({ manual = false } = {}) {
  if (pollingInFlight.value) return
  pollingInFlight.value = true
  if (manual) isRefreshing.value = true

  try {
    const data = await getMonitoring({ force: manual })
    monitoring.value = {
      status: String(data?.status || 'UNKNOWN').toUpperCase(),
      checkedAt: data?.checkedAt || monitoring.value.checkedAt,
      metrics: { ...(data?.metrics || {}) },
      services: Array.isArray(data?.services) ? data.services : []
    }
    monitoringError.value = ''
    lastUpdatedAt.value = data?.checkedAt || lastUpdatedAt.value
  } catch (error) {
    monitoringError.value = error.message || '无法读取实时检测结果'
    if (!monitoring.value.services.length) monitoring.value.status = 'UNKNOWN'
  } finally {
    pollingInFlight.value = false
    isRefreshing.value = false
  }
}

function scheduleMonitoringRefresh() {
  void loadMonitoring()
  monitorTick += 1
  if (monitorTick % 5 === 0) void loadRecentLogs()
}

function startAtForRange() {
  const now = Date.now()
  const duration = logRange.value === 'hour' ? 3_600_000 : logRange.value === 'week' ? 7 * 24 * 3_600_000 : new Date().setHours(0, 0, 0, 0) - now
  return new Date(logRange.value === 'today' ? new Date().setHours(0, 0, 0, 0) : now - duration).toISOString()
}

async function loadRecentLogs() {
  try {
    const result = await listAuditLogs({ page: 0, size: 12 })
    const items = Array.isArray(result?.items) ? result.items : []
    recentLogs.value = items.map(normalizeAuditLog)
    totalLogCount.value = Number(result?.total ?? 0)
  } catch (error) {
    recentLogs.value = []
    totalLogCount.value = 0
    monitoringError.value = error.message || monitoringError.value
  }
}

async function loadDrawerLogs() {
  try {
    const result = await listAuditLogs({
      page: logPage.value - 1,
      size: logPageSize.value,
      keyword: logQuery.value.trim(),
      level: logLevel.value,
      module: logModule.value,
      startAt: startAtForRange()
    })
    logs.value = (Array.isArray(result?.items) ? result.items : []).map(normalizeAuditLog)
    logTotal.value = Number(result?.total ?? 0)
    if (logPage.value > logPages.value) logPage.value = logPages.value
  } catch (error) {
    logs.value = []
    logTotal.value = 0
    showToast(error.message || '系统日志读取失败')
  }
}

function normalizeAuditLog(entry) {
  const createdAt = new Date(entry.createdAt)
  const level = normalizeLogLevel(entry)
  return {
    id: entry.id,
    timestamp: formatTime(createdAt),
    createdAt,
    level,
    sourceModule: String(entry.module || ''),
    module: formatModule(entry.module),
    operation: formatOperation(entry),
    result: formatResult(entry.result),
    detail: String(entry.detail || '')
  }
}

function normalizeLogLevel(entry) {
  const declared = String(entry.level || entry.severity || '').toUpperCase()
  if (declared === 'WARN') return 'WARNING'
  if (['INFO', 'WARNING', 'ERROR', 'SECURITY'].includes(declared)) return declared
  if (entry.action === 'RISK_BLOCK') return 'SECURITY'
  return String(entry.result || '').toUpperCase() === 'SUCCESS' ? 'INFO' : 'ERROR'
}

function formatModule(value) {
  const modulesMap = {
    'system.rules': '系统规则',
    'knowledge.assets': '知识库',
    'models.channels': 'AI模型',
    'models.prompts': 'AI模型',
    'risk.blacklist': '安全风控',
    'audit.logs': '系统日志',
    'system.monitoring': '服务监控',
    auth: '用户认证',
    users: '用户管理'
  }
  return modulesMap[value] || String(value || '系统').replace(/[._-]+/g, ' ')
}

function formatOperation(entry) {
  const actions = {
    LOGIN: '用户登录',
    RISK_BLOCK: '连续登录失败',
    CREATE: '新建配置',
    UPDATE: '更新配置',
    DELETE: '删除配置',
    SAVE: '保存配置',
    HEALTH_CHECK: '服务健康检查',
    UPDATE_PROFILE: '更新个人资料',
    CHANGE_PASSWORD: '修改密码',
    RESET_PASSWORD: '重置密码',
    STATUS: '更新用户状态'
  }
  return actions[entry.action] || String(entry.detail || '系统操作').slice(0, 24)
}

function formatResult(value) {
  return { SUCCESS: '成功', FAILED: '失败', FAILURE: '失败', ERROR: '失败', BLOCKED: '已拦截' }[String(value || '').toUpperCase()] || value || '成功'
}

function openLogsDrawer() {
  logDrawerOpen.value = true
}

function changeLogPage(delta) {
  logPage.value = Math.min(logPages.value, Math.max(1, logPage.value + delta))
}

onMounted(() => {
  void loadRules()
  void loadMonitoring()
  void loadRecentLogs()
  refreshTimer = window.setInterval(scheduleMonitoringRefresh, REFRESH_INTERVAL)
})

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer)
  window.clearTimeout(toastTimer)
  window.clearTimeout(logQueryTimer)
})
</script>

<template>
  <section class="system-management-page">
    <header class="system-management-intro">
      <div class="system-management-title">
        <p class="system-management-breadcrumb">系统管理</p>
        <h2>系统管理</h2>
        <span>统一查看平台运行状态、系统规则、服务健康及最近日志。</span>
      </div>
      <div class="system-management-head-actions">
        <span class="system-management-health" :class="{ 'is-attention': !isSystemHealthy }"><i></i>{{ monitoring.status === 'CHECKING' ? '正在检测系统' : isSystemHealthy ? '系统运行正常' : '系统需要关注' }}</span>
        <small>最后更新：{{ formatTime(lastUpdatedAt) }}</small>
        <button type="button" class="system-management-refresh" :class="{ 'is-refreshing': isRefreshing }" :disabled="isRefreshing" @click="loadMonitoring({ manual: true })"><RefreshCw :size="15" />刷新</button>
      </div>
    </header>

    <section class="system-management-summary" aria-label="系统运行概览">
      <article v-for="metric in summaryMetrics" :key="metric.label" class="system-summary-card" :class="`is-${metric.tone}`">
        <span class="system-summary-icon"><component :is="metric.icon" :size="17" /></span>
        <div><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong></div>
      </article>
    </section>

    <section class="system-management-content">
      <div class="system-management-left">
        <section class="system-panel system-rules-panel">
          <header class="system-panel-header">
            <div><h3>系统规则</h3><span>关键自动化策略</span></div>
            <button type="button" class="system-panel-link" @click="openRulesDrawer"><SlidersHorizontal :size="14" />管理规则</button>
          </header>
          <div class="system-rules-summary-list">
            <article v-for="rule in summaryRules" :key="rule.id">
              <div><strong>{{ rule.label }}</strong><small>{{ formatRuleThreshold(rule) }}</small></div>
              <div class="system-rule-summary-state"><span :class="{ 'is-off': !rule.enabled }"><i></i>{{ rule.enabled ? '已启用' : '已停用' }}</span><button type="button" class="system-rule-switch" :class="{ 'is-active': rule.enabled }" role="switch" :aria-checked="rule.enabled" :aria-label="`切换${rule.label}`" @click="toggleRule(rule)"><i></i></button></div>
            </article>
          </div>
        </section>

        <section class="system-panel system-services-panel">
          <header class="system-panel-header">
            <div><h3>服务状态</h3><span>平台核心依赖健康度</span></div>
            <div class="system-service-live"><span><i></i>实时监控中</span><small>每1秒刷新</small></div>
          </header>
          <div class="system-service-table-wrap">
            <table class="system-service-table">
              <thead><tr><th>服务</th><th>状态</th><th>响应</th><th>错误率</th><th>CPU</th><th>内存</th></tr></thead>
              <tbody>
                <tr v-for="service in services" :key="service.name" :title="service.detail">
                  <td><span class="system-service-name"><i></i>{{ service.label }}</span></td>
                  <td><span class="system-service-status" :class="`is-${service.status.toLowerCase()}`"><i></i>{{ serviceStatusLabel(service.status) }}</span></td>
                  <td>{{ formatResponse(service.responseTime) }}</td>
                  <td>{{ formatPercent(service.errorRate) }}</td>
                  <td>{{ formatWholePercent(service.cpu) }}</td>
                  <td>{{ formatWholePercent(service.memory) }}</td>
                </tr>
                <tr v-if="!services.length"><td colspan="6" class="system-service-empty">正在等待真实检测结果</td></tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <section class="system-panel system-logs-panel">
        <header class="system-panel-header">
          <div><h3>最近系统日志</h3><span>最新运行、告警与安全事件</span></div>
          <button type="button" class="system-panel-link" @click="openLogsDrawer">查看全部<ChevronRight :size="15" /></button>
        </header>
        <div class="system-log-list" role="table" aria-label="最近系统日志">
          <div class="system-log-row system-log-heading" role="row"><span>时间</span><span>等级</span><span>模块</span><span>操作</span><span>结果</span></div>
          <div v-for="log in recentLogs" :key="log.id" class="system-log-row" role="row">
            <time>{{ log.timestamp }}</time>
            <span><b class="system-log-level" :class="levelClass(log.level)">{{ log.level }}</b></span>
            <strong>{{ log.module }}</strong>
            <span class="system-log-operation">{{ log.operation }}</span>
            <em :class="{ 'is-alert': ['失败', '警告', '已拦截', '处理中'].includes(log.result) }">{{ log.result }}</em>
          </div>
          <div v-if="!recentLogs.length" class="system-log-empty-row">暂无已入库系统日志</div>
        </div>
      </section>
    </section>

    <Teleport to="body">
      <Transition name="system-drawer">
        <div v-if="ruleDrawerOpen" class="system-drawer-backdrop" @pointerdown.self="ruleDrawerOpen = false">
          <aside class="system-rules-drawer" role="dialog" aria-modal="true" aria-label="系统规则管理">
            <header class="system-drawer-header"><div><span>系统管理</span><h3>规则管理</h3><p>调整自动化策略的启用状态和阈值。</p></div><button type="button" aria-label="关闭规则管理" @click="ruleDrawerOpen = false"><X :size="18" /></button></header>
            <div class="system-rules-drawer-body">
              <section v-for="group in ruleGroups" :key="group.id" class="system-drawer-rule-group">
                <header><span><component :is="group.icon" :size="15" /></span><h4>{{ group.label }}</h4></header>
                <article v-for="rule in group.rules" :key="rule.id" class="system-drawer-rule-row">
                  <div class="system-drawer-rule-name"><strong>{{ rule.label }}</strong><small>{{ rule.enabled ? '已启用' : '已停用' }}</small></div>
                  <label class="system-rule-field"><span>阈值类型</span><select v-model="rule.threshold.type" @change="rulesDirty = true"><option v-for="type in thresholdTypes" :key="type.key" :value="type.key">{{ type.label }}</option></select></label>
                  <label class="system-rule-field is-value"><span>数值</span><div><input v-model.number="rule.threshold.value" :min="0" :step="1" type="number" @input="rulesDirty = true" /><em>{{ (thresholdTypes.find((type) => type.key === rule.threshold.type) || thresholdTypes[0]).unit }}</em></div></label>
                  <button type="button" class="system-rule-switch" :class="{ 'is-active': rule.enabled }" role="switch" :aria-checked="rule.enabled" :aria-label="`切换${rule.label}`" @click="toggleRule(rule)"><i></i></button>
                </article>
              </section>
            </div>
            <footer class="system-drawer-footer"><span v-if="rulesDirty">有未保存的规则变更</span><div><button type="button" class="system-drawer-secondary" @click="resetRules"><RotateCcw :size="14" />恢复默认</button><button type="button" class="system-drawer-primary" :disabled="isSavingRules" @click="saveRules"><Save :size="14" />{{ isSavingRules ? '保存中' : '保存规则' }}</button></div></footer>
          </aside>
        </div>
      </Transition>

      <Transition name="system-drawer">
        <div v-if="logDrawerOpen" class="system-drawer-backdrop" @pointerdown.self="logDrawerOpen = false">
          <aside class="system-logs-drawer" role="dialog" aria-modal="true" aria-label="完整系统日志">
            <header class="system-drawer-header"><div><span>系统管理</span><h3>完整系统日志</h3><p>集中检索和审阅平台运行日志。</p></div><button type="button" aria-label="关闭完整系统日志" @click="logDrawerOpen = false"><X :size="18" /></button></header>
            <div class="system-log-filters">
              <label class="system-log-search"><Search :size="16" /><input v-model="logQuery" type="search" placeholder="搜索操作、模块或结果" /></label>
              <label><span>等级</span><select v-model="logLevel"><option value="all">全部等级</option><option value="INFO">INFO</option><option value="WARNING">WARNING</option><option value="ERROR">ERROR</option><option value="SECURITY">SECURITY</option></select></label>
              <label><span>模块</span><select v-model="logModule"><option value="all">全部模块</option><option v-for="module in modules" :key="module.value" :value="module.value">{{ module.label }}</option></select></label>
              <label><span>时间</span><select v-model="logRange"><option value="hour">最近1小时</option><option value="today">今天</option><option value="week">最近7天</option></select></label>
            </div>
            <div class="system-full-log-table-wrap">
              <table class="system-full-log-table"><thead><tr><th>时间</th><th>等级</th><th>模块</th><th>操作</th><th>结果</th></tr></thead><tbody><tr v-for="log in pagedLogs" :key="log.id"><td>{{ log.timestamp }}</td><td><b class="system-log-level" :class="levelClass(log.level)">{{ log.level }}</b></td><td>{{ log.module }}</td><td>{{ log.operation }}</td><td>{{ log.result }}</td></tr><tr v-if="!pagedLogs.length"><td colspan="5" class="system-log-empty">未找到匹配的系统日志</td></tr></tbody></table>
            </div>
             <footer class="system-log-pagination"><span>共 {{ logTotal }} 条</span><div><label>每页<select v-model.number="logPageSize"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select>条</label><button type="button" :disabled="logPage === 1" aria-label="上一页" @click="changeLogPage(-1)"><ChevronLeft :size="15" /></button><button v-for="page in visibleLogPages" :key="page" type="button" :class="{ 'is-current': logPage === page }" @click="logPage = page">{{ page }}</button><button type="button" :disabled="logPage === logPages" aria-label="下一页" @click="changeLogPage(1)"><ChevronRight :size="15" /></button></div></footer>
          </aside>
        </div>
      </Transition>
    </Teleport>

    <Transition name="system-toast"><p v-if="toast" class="system-management-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.system-management-page { display: grid; height: 100%; min-height: 0; grid-template-rows: 64px 88px minmax(0, 1fr); gap: 14px; color: #1f2937; }
.system-management-intro { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 18px; }
.system-management-title { display: grid; min-width: 0; gap: 2px; }.system-management-title p,.system-management-title h2,.system-management-title span { margin: 0; }.system-management-breadcrumb { color: #64748b; font-size: 12px; font-weight: 650; }.system-management-title h2 { color: #111827; font-size: 23px; line-height: 1.18; font-weight: 760; }.system-management-title span { overflow: hidden; color: #6b7280; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.system-management-head-actions { display: grid; grid-template-columns: auto auto auto; align-items: center; gap: 9px; flex: 0 0 auto; }.system-management-head-actions small { color: #6b7280; font-size: 11px; white-space: nowrap; }.system-management-health { display: inline-flex; align-items: center; gap: 6px; color: #15803d; font-size: 12px; font-weight: 700; white-space: nowrap; }.system-management-health i,.system-service-live span i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 3px rgba(34, 197, 94, .12); }.system-management-health.is-attention { color: #c2410c; }.system-management-refresh { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 32px; padding: 0 10px; border: 1px solid #d9dee7; border-radius: 6px; background: #fff; color: #374151; font-size: 12px; font-weight: 650; }.system-management-refresh:hover:not(:disabled) { border-color: #c4cbd6; background: #f8fafc; }.system-management-refresh:disabled { cursor: wait; opacity: .7; }.system-management-refresh.is-refreshing svg { animation: system-spin .8s linear infinite; }
.system-management-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; min-height: 0; }.system-summary-card { display: flex; min-width: 0; align-items: center; gap: 11px; height: 88px; padding: 13px 16px; border: 1px solid #e4e7ec; border-radius: 8px; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, .025); }.system-summary-icon { display: grid; flex: 0 0 34px; width: 34px; height: 34px; place-items: center; border-radius: 7px; background: #eff6ff; color: #2563eb; }.system-summary-card.is-green .system-summary-icon { background: #ecfdf5; color: #059669; }.system-summary-card.is-amber .system-summary-icon { background: #fffbeb; color: #d97706; }.system-summary-card.is-violet .system-summary-icon { background: #f5f3ff; color: #7c3aed; }.system-summary-card div { display: grid; min-width: 0; gap: 4px; }.system-summary-card small { overflow: hidden; color: #6b7280; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.system-summary-card strong { overflow: hidden; color: #111827; font-size: 22px; font-weight: 760; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; }
.system-management-content { display: grid; min-height: 0; grid-template-columns: minmax(0, 55fr) minmax(0, 45fr); gap: 14px; overflow: hidden; }.system-management-left { display: grid; min-width: 0; min-height: 0; grid-template-rows: minmax(0, .83fr) minmax(0, 1.17fr); gap: 14px; overflow: hidden; }.system-panel { display: flex; min-width: 0; min-height: 0; flex-direction: column; overflow: hidden; border: 1px solid #e3e7ed; border-radius: 8px; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, .025); }.system-panel-header { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; border-bottom: 1px solid #eef1f4; }.system-panel-header > div:first-child { display: grid; min-width: 0; gap: 3px; }.system-panel-header h3,.system-panel-header span { margin: 0; }.system-panel-header h3 { color: #111827; font-size: 14px; font-weight: 740; }.system-panel-header > div:first-child span { color: #8a94a3; font-size: 11px; }.system-panel-link { display: inline-flex; align-items: center; gap: 5px; flex: 0 0 auto; padding: 4px 2px; border: 0; background: transparent; color: #2563eb; font-size: 12px; font-weight: 680; }.system-panel-link:hover { color: #1d4ed8; }
.system-rules-summary-list { min-height: 0; overflow-y: auto; padding: 0 14px; }.system-rules-summary-list article { display: flex; min-height: 43px; align-items: center; justify-content: space-between; gap: 14px; border-bottom: 1px solid #f1f3f6; }.system-rules-summary-list article:last-child { border-bottom: 0; }.system-rules-summary-list article > div:first-child { display: flex; min-width: 0; align-items: center; gap: 10px; }.system-rules-summary-list strong { overflow: hidden; color: #374151; font-size: 12px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }.system-rules-summary-list small { flex: 0 0 auto; min-width: 38px; color: #7b8797; font-size: 11px; text-align: right; }.system-rule-summary-state { display: flex; align-items: center; gap: 9px; flex: 0 0 auto; }.system-rule-summary-state > span { display: inline-flex; align-items: center; gap: 5px; color: #15803d; font-size: 11px; white-space: nowrap; }.system-rule-summary-state > span i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.system-rule-summary-state > span.is-off { color: #94a3b8; }.system-rule-switch { position: relative; width: 30px; height: 18px; flex: 0 0 auto; padding: 2px; border: 0; border-radius: 999px; background: #cbd5e1; }.system-rule-switch i { display: block; width: 14px; height: 14px; border-radius: 50%; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, .23); transition: transform 160ms ease; }.system-rule-switch.is-active { background: #2563eb; }.system-rule-switch.is-active i { transform: translateX(12px); }.system-rule-switch:focus-visible { outline: 2px solid #93c5fd; outline-offset: 2px; }
.system-service-live { display: grid; justify-items: end; gap: 3px; flex: 0 0 auto; }.system-service-live span { display: inline-flex; align-items: center; gap: 5px; color: #15803d; font-size: 11px; font-weight: 700; white-space: nowrap; }.system-service-live small { color: #94a3b8; font-size: 10px; white-space: nowrap; }.system-service-table-wrap { min-height: 0; overflow: auto; }.system-service-table { width: 100%; min-width: 560px; border-collapse: collapse; table-layout: fixed; }.system-service-table th { position: sticky; top: 0; z-index: 1; height: 31px; padding: 0 14px; border-bottom: 1px solid #eef1f4; background: #fbfcfd; color: #7c8797; font-size: 10px; font-weight: 700; text-align: left; white-space: nowrap; }.system-service-table th:nth-child(1) { width: 29%; }.system-service-table th:nth-child(n+2) { width: 14.2%; }.system-service-table td { height: 35px; padding: 0 14px; border-bottom: 1px solid #f1f3f6; color: #475569; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 11px; white-space: nowrap; }.system-service-table tr:last-child td { border-bottom: 0; }.system-service-name,.system-service-status { display: inline-flex; align-items: center; gap: 6px; font-family: inherit; }.system-service-name { color: #374151; font-size: 12px; font-weight: 650; }.system-service-name i { width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; }.system-service-status { color: #15803d; font-size: 11px; font-weight: 650; }.system-service-status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.system-service-status.is-degraded,.system-service-status.is-not_configured { color: #c2410c; }.system-service-status.is-down { color: #dc2626; }.system-service-status.is-checking,.system-service-status.is-unknown { color: #64748b; }.system-service-empty { color: #94a3b8 !important; font-family: inherit !important; text-align: center; }
.system-logs-panel { min-height: 0; }.system-log-list { min-height: 0; overflow-y: auto; }.system-log-row { display: grid; grid-template-columns: 84px 82px minmax(78px, .9fr) minmax(120px, 1.25fr) 58px; min-width: 570px; min-height: 42px; align-items: center; column-gap: 8px; padding: 0 14px; border-bottom: 1px solid #f1f3f6; color: #475569; font-size: 11px; }.system-log-row:last-child { border-bottom: 0; }.system-log-row time { color: #6b7280; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }.system-log-row strong,.system-log-operation { overflow: hidden; color: #475569; font-weight: 550; text-overflow: ellipsis; white-space: nowrap; }.system-log-row em { color: #15803d; font-size: 11px; font-style: normal; font-weight: 650; }.system-log-row em.is-alert { color: #c2410c; }.system-log-heading { position: sticky; top: 0; z-index: 1; min-height: 31px; background: #fbfcfd; color: #7c8797; font-size: 10px; font-weight: 700; }.system-log-heading span { white-space: nowrap; }.system-log-empty-row { display: grid; min-height: 86px; place-items: center; color: #94a3b8; font-size: 12px; }.system-log-level { display: inline-flex; align-items: center; min-height: 20px; padding: 0 6px; border: 1px solid #dbeafe; border-radius: 5px; background: #f8fbff; color: #64748b; font-size: 10px; font-weight: 750; line-height: 1; }.system-log-level.is-warning { border-color: #fed7aa; background: #fffaf3; color: #c2410c; }.system-log-level.is-error { border-color: #fecaca; background: #fff7f7; color: #dc2626; }.system-log-level.is-security { border-color: #f3d3e6; background: #fff7fb; color: #be185d; }
.system-drawer-backdrop { position: fixed; z-index: 200; inset: 0; display: flex; justify-content: flex-end; background: rgba(15, 23, 42, .34); backdrop-filter: blur(2px); }.system-rules-drawer,.system-logs-drawer { display: flex; height: 100%; min-height: 0; flex-direction: column; border-left: 1px solid #e5e7eb; background: #fff; box-shadow: -18px 0 48px rgba(15, 23, 42, .15); }.system-rules-drawer { width: min(700px, 100vw); }.system-logs-drawer { width: min(980px, 72vw); }.system-drawer-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 18px 20px 16px; border-bottom: 1px solid #e9edf2; }.system-drawer-header div { min-width: 0; }.system-drawer-header span { color: #6b7280; font-size: 11px; }.system-drawer-header h3 { margin: 3px 0 4px; color: #111827; font-size: 18px; font-weight: 760; }.system-drawer-header p { margin: 0; color: #6b7280; font-size: 12px; }.system-drawer-header > button { display: grid; width: 30px; height: 30px; flex: 0 0 auto; place-items: center; border: 1px solid #e1e5ea; border-radius: 6px; background: #fff; color: #64748b; }.system-drawer-header > button:hover { background: #f8fafc; color: #111827; }.system-rules-drawer-body { min-height: 0; overflow-y: auto; padding: 0 20px; }.system-drawer-rule-group { padding: 16px 0; border-bottom: 1px solid #e9edf2; }.system-drawer-rule-group:last-child { border-bottom: 0; }.system-drawer-rule-group > header { display: flex; align-items: center; gap: 7px; margin-bottom: 8px; }.system-drawer-rule-group > header span { display: grid; width: 25px; height: 25px; place-items: center; border-radius: 6px; background: #eff6ff; color: #2563eb; }.system-drawer-rule-group h4 { margin: 0; color: #374151; font-size: 13px; font-weight: 720; }.system-drawer-rule-row { display: grid; grid-template-columns: minmax(0, 1fr) 115px 104px 30px; align-items: end; gap: 10px; padding: 8px 0 8px 32px; }.system-drawer-rule-name { display: grid; gap: 3px; min-width: 0; padding-bottom: 3px; }.system-drawer-rule-name strong { overflow: hidden; color: #374151; font-size: 12px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }.system-drawer-rule-name small { color: #94a3b8; font-size: 10px; }.system-rule-field { display: grid; min-width: 0; gap: 4px; }.system-rule-field > span { color: #94a3b8; font-size: 10px; }.system-rule-field select,.system-rule-field input,.system-log-filters select,.system-log-filters input,.system-log-pagination select { width: 100%; height: 30px; padding: 0 8px; border: 1px solid #dfe4ea; border-radius: 5px; outline: 0; background: #fff; color: #374151; font-size: 11px; }.system-rule-field select:focus,.system-rule-field input:focus,.system-log-filters select:focus,.system-log-filters input:focus,.system-log-pagination select:focus { border-color: #93c5fd; box-shadow: 0 0 0 2px rgba(59, 130, 246, .12); }.system-rule-field.is-value > div { display: flex; align-items: center; gap: 5px; }.system-rule-field.is-value em { width: 30px; flex: 0 0 auto; color: #94a3b8; font-size: 10px; font-style: normal; white-space: nowrap; }.system-drawer-footer { display: flex; min-height: 62px; align-items: center; justify-content: space-between; gap: 14px; padding: 12px 20px; border-top: 1px solid #e9edf2; }.system-drawer-footer > span { color: #b45309; font-size: 11px; }.system-drawer-footer > div { display: flex; gap: 8px; margin-left: auto; }.system-drawer-secondary,.system-drawer-primary { display: inline-flex; min-height: 32px; align-items: center; justify-content: center; gap: 5px; padding: 0 11px; border-radius: 6px; font-size: 12px; font-weight: 650; }.system-drawer-secondary { border: 1px solid #dfe4ea; background: #fff; color: #4b5563; }.system-drawer-secondary:hover { background: #f8fafc; }.system-drawer-primary { border: 1px solid #111827; background: #111827; color: #fff; }.system-drawer-primary:hover:not(:disabled) { background: #27272a; }.system-drawer-primary:disabled { cursor: wait; opacity: .65; }
.system-log-filters { display: grid; grid-template-columns: minmax(190px, 1fr) 122px 132px 120px; gap: 8px; padding: 12px 20px; border-bottom: 1px solid #e9edf2; }.system-log-filters > label { display: grid; gap: 4px; color: #94a3b8; font-size: 10px; }.system-log-search { position: relative; display: flex !important; align-items: center; }.system-log-search svg { position: absolute; left: 9px; color: #94a3b8; }.system-log-search input { padding-left: 30px; }.system-full-log-table-wrap { min-height: 0; flex: 1; overflow: auto; }.system-full-log-table { width: 100%; min-width: 740px; border-collapse: collapse; table-layout: fixed; }.system-full-log-table th { position: sticky; top: 0; z-index: 1; height: 34px; padding: 0 20px; border-bottom: 1px solid #e9edf2; background: #fbfcfd; color: #7c8797; font-size: 10px; text-align: left; }.system-full-log-table td { height: 40px; padding: 0 20px; border-bottom: 1px solid #f0f2f5; color: #475569; font-size: 12px; white-space: nowrap; }.system-full-log-table th:nth-child(1) { width: 15%; }.system-full-log-table th:nth-child(2) { width: 14%; }.system-full-log-table th:nth-child(3) { width: 19%; }.system-full-log-table th:nth-child(4) { width: 32%; }.system-full-log-table th:nth-child(5) { width: 20%; }.system-log-empty { color: #94a3b8 !important; text-align: center; }.system-log-pagination { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 20px; border-top: 1px solid #e9edf2; color: #6b7280; font-size: 11px; }.system-log-pagination > div { display: flex; align-items: center; gap: 5px; }.system-log-pagination label { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }.system-log-pagination select { width: 54px; height: 28px; padding-inline: 5px; }.system-log-pagination button { display: grid; width: 28px; height: 28px; place-items: center; padding: 0; border: 1px solid #dfe4ea; border-radius: 5px; background: #fff; color: #4b5563; font-size: 11px; }.system-log-pagination button:hover:not(:disabled) { background: #f8fafc; }.system-log-pagination button.is-current { border-color: #111827; background: #111827; color: #fff; }.system-log-pagination button:disabled { cursor: not-allowed; opacity: .45; }
.system-management-toast { position: fixed; right: 24px; bottom: 24px; z-index: 240; margin: 0; padding: 10px 13px; border: 1px solid #27272a; border-radius: 7px; background: #18181b; color: #fff; font-size: 12px; box-shadow: 0 14px 32px rgba(15, 23, 42, .2); }.system-drawer-enter-active,.system-drawer-leave-active { transition: opacity 180ms ease; }.system-drawer-enter-active .system-rules-drawer,.system-drawer-enter-active .system-logs-drawer,.system-drawer-leave-active .system-rules-drawer,.system-drawer-leave-active .system-logs-drawer { transition: transform 220ms ease; }.system-drawer-enter-from,.system-drawer-leave-to { opacity: 0; }.system-drawer-enter-from .system-rules-drawer,.system-drawer-enter-from .system-logs-drawer,.system-drawer-leave-to .system-rules-drawer,.system-drawer-leave-to .system-logs-drawer { transform: translateX(100%); }.system-toast-enter-active,.system-toast-leave-active { transition: opacity 160ms ease, transform 180ms ease; }.system-toast-enter-from,.system-toast-leave-to { opacity: 0; transform: translateY(8px); }@keyframes system-spin { to { transform: rotate(360deg); } }
:global(html[data-theme='dark']) .system-management-page { color: #e4e4e7; }:global(html[data-theme='dark']) .system-management-title h2,:global(html[data-theme='dark']) .system-summary-card strong,:global(html[data-theme='dark']) .system-panel-header h3,:global(html[data-theme='dark']) .system-drawer-header h3,:global(html[data-theme='dark']) .system-drawer-rule-group h4 { color: #fafafa; }:global(html[data-theme='dark']) .system-summary-card,:global(html[data-theme='dark']) .system-panel,:global(html[data-theme='dark']) .system-rules-drawer,:global(html[data-theme='dark']) .system-logs-drawer { border-color: rgba(255,255,255,.1); background: #18181b; box-shadow: 0 1px 2px rgba(0,0,0,.3); }:global(html[data-theme='dark']) .system-panel-header,:global(html[data-theme='dark']) .system-rules-summary-list article,:global(html[data-theme='dark']) .system-service-table th,:global(html[data-theme='dark']) .system-service-table td,:global(html[data-theme='dark']) .system-log-row,:global(html[data-theme='dark']) .system-drawer-header,:global(html[data-theme='dark']) .system-drawer-rule-group,:global(html[data-theme='dark']) .system-drawer-footer,:global(html[data-theme='dark']) .system-log-filters,:global(html[data-theme='dark']) .system-full-log-table th,:global(html[data-theme='dark']) .system-full-log-table td,:global(html[data-theme='dark']) .system-log-pagination { border-color: rgba(255,255,255,.08); }:global(html[data-theme='dark']) .system-summary-card,:global(html[data-theme='dark']) .system-service-table th,:global(html[data-theme='dark']) .system-log-heading,:global(html[data-theme='dark']) .system-full-log-table th { background: #18181b; }:global(html[data-theme='dark']) .system-management-title span,:global(html[data-theme='dark']) .system-management-breadcrumb,:global(html[data-theme='dark']) .system-management-head-actions small,:global(html[data-theme='dark']) .system-summary-card small,:global(html[data-theme='dark']) .system-panel-header > div:first-child span,:global(html[data-theme='dark']) .system-rules-summary-list small,:global(html[data-theme='dark']) .system-service-live small,:global(html[data-theme='dark']) .system-drawer-header span,:global(html[data-theme='dark']) .system-drawer-header p,:global(html[data-theme='dark']) .system-drawer-rule-name small,:global(html[data-theme='dark']) .system-rule-field > span,:global(html[data-theme='dark']) .system-log-pagination { color: #a1a1aa; }:global(html[data-theme='dark']) .system-rules-summary-list strong,:global(html[data-theme='dark']) .system-service-name,:global(html[data-theme='dark']) .system-drawer-rule-name strong,:global(html[data-theme='dark']) .system-full-log-table td { color: #e4e4e7; }:global(html[data-theme='dark']) .system-log-row strong,:global(html[data-theme='dark']) .system-log-operation,:global(html[data-theme='dark']) .system-service-table td { color: #d4d4d8; }:global(html[data-theme='dark']) .system-management-refresh,:global(html[data-theme='dark']) .system-drawer-header > button,:global(html[data-theme='dark']) .system-drawer-secondary,:global(html[data-theme='dark']) .system-rule-field select,:global(html[data-theme='dark']) .system-rule-field input,:global(html[data-theme='dark']) .system-log-filters select,:global(html[data-theme='dark']) .system-log-filters input,:global(html[data-theme='dark']) .system-log-pagination select,:global(html[data-theme='dark']) .system-log-pagination button { border-color: rgba(255,255,255,.12); background: #111113; color: #d4d4d8; }
/* Slightly larger operational type without growing the fixed-height data rows. */
.system-management-breadcrumb { font-size: 13px; }.system-management-title h2 { font-size: 25px; }.system-management-title span { font-size: 13px; }.system-management-head-actions small,.system-management-health { font-size: 12px; }.system-management-refresh { font-size: 13px; }.system-summary-card small { font-size: 13px; }.system-summary-card strong { font-size: 24px; }.system-panel-header h3 { font-size: 15px; }.system-panel-header > div:first-child span { font-size: 12px; }.system-panel-link { font-size: 13px; }.system-rules-summary-list strong { font-size: 13px; }.system-rules-summary-list small,.system-rule-summary-state > span { font-size: 12px; }.system-service-live span { font-size: 12px; }.system-service-live small { font-size: 11px; }.system-service-table th,.system-log-heading { font-size: 11px; }.system-service-table td,.system-log-row { font-size: 12px; }.system-service-name { font-size: 13px; }.system-service-status { font-size: 12px; }.system-log-row em { font-size: 12px; }.system-log-level { font-size: 11px; }.system-drawer-header h3 { font-size: 19px; }.system-drawer-header p { font-size: 13px; }.system-drawer-rule-group h4 { font-size: 14px; }.system-drawer-rule-name strong { font-size: 13px; }.system-rule-field select,.system-rule-field input,.system-log-filters select,.system-log-filters input,.system-log-pagination select { font-size: 12px; }.system-full-log-table td { font-size: 13px; }
@media (max-width: 1180px) { .system-management-page { grid-template-rows: auto 88px minmax(0, 1fr); }.system-management-intro { align-items: flex-start; }.system-management-head-actions { grid-template-columns: auto auto; }.system-management-head-actions small { grid-column: 1 / -1; }.system-management-content { grid-template-columns: minmax(0, 1fr); overflow-y: auto; }.system-management-left { min-height: 600px; }.system-logs-panel { min-height: 560px; }.system-logs-drawer { width: min(980px, 92vw); } }
@media (max-width: 760px) { .system-management-page { height: auto; min-height: 0; grid-template-rows: auto auto auto; }.system-management-intro { display: grid; }.system-management-title span { white-space: normal; }.system-management-head-actions { justify-content: start; }.system-management-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }.system-management-content,.system-management-left { overflow: visible; }.system-management-left { grid-template-rows: auto auto; }.system-panel { min-height: 360px; }.system-rules-drawer,.system-logs-drawer { width: 100vw; }.system-drawer-rule-row { grid-template-columns: minmax(0, 1fr) 30px; align-items: center; padding-left: 0; }.system-rule-field { grid-column: span 1; }.system-drawer-rule-name { grid-column: 1; }.system-drawer-rule-row > .system-rule-switch { grid-column: 2; grid-row: 1; }.system-log-filters { grid-template-columns: 1fr 1fr; }.system-log-search { grid-column: 1 / -1; }.system-log-pagination { align-items: flex-start; flex-direction: column; }.system-log-pagination > div { overflow-x: auto; max-width: 100%; } }
</style>
