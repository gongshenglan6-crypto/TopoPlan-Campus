<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Activity, CheckCircle2, RefreshCw, Server, TriangleAlert } from '@lucide/vue'
import { getMonitoring } from '../../api/admin'

const REFRESH_INTERVAL = 1_000
const MAX_MONITORING_EVENTS = 8

const isInitialLoading = ref(true)
const hasLoaded = ref(false)
const isManualRefreshing = ref(false)
const pollingInFlight = ref(false)
const errorMessage = ref('')
const lastUpdatedAt = ref(null)
const monitoringEvents = ref([])
const monitoring = ref({ status: 'CHECKING', checkedAt: null, metrics: {}, services: [] })

let refreshTimer = 0
let queuedManualRefresh = false
let previousServiceStatuses = new Map()

const requiredServices = [
  { name: 'api-service', detail: '平台API' },
  { name: 'ai-service', detail: 'AI模型服务' },
  { name: 'topology-service', detail: '拓扑生成服务' },
  { name: 'rag-service', detail: '知识库服务' },
  { name: 'search-service', detail: '联网搜索服务' },
  { name: 'document-service', detail: '文档生成服务' },
  { name: 'mysql', detail: '数据库' },
  { name: 'redis', detail: '缓存' },
  { name: 'topo-api', detail: '管理API' }
]

function defaultServiceMetrics() {
  return { responseTime: null, requestCount: 0, errorRate: 0, cpu: null, memory: null, checkedAt: monitoring.value.checkedAt }
}

function normalizeService(service) {
  const status = String(service.status || 'CHECKING').toUpperCase()
  return {
    ...defaultServiceMetrics(),
    ...service,
    status: ['UP', 'DEGRADED', 'DOWN', 'CHECKING', 'UNKNOWN'].includes(status) ? status : 'CHECKING',
    checkedAt: service.checkedAt || monitoring.value.checkedAt
  }
}

const services = computed(() => {
  const reportedServices = Array.isArray(monitoring.value.services) ? monitoring.value.services : []
  const reportedByName = new Map(reportedServices.map((service) => [service.name, service]))
  const required = requiredServices.map((service) => normalizeService({ ...service, ...(reportedByName.get(service.name) || {}) }))
  const requiredNames = new Set(requiredServices.map((service) => service.name))
  const additional = reportedServices.filter((service) => !requiredNames.has(service.name)).map(normalizeService)
  return [...required, ...additional]
})
const metrics = computed(() => monitoring.value.metrics || {})
const activeServiceCount = computed(() => services.value.filter((service) => service.status === 'UP').length)
const metricCards = computed(() => [
  { label: '已记录请求', value: formatNumber(metrics.value.requestCount), hint: '轻量聚合计数', icon: Activity },
  { label: '成功率', value: formatRate(metrics.value.successRate), hint: '按服务请求结果统计', icon: CheckCircle2 },
  { label: '异常请求', value: formatNumber(metrics.value.errorCount), hint: '非 SUCCESS 结果', icon: TriangleAlert },
  { label: '在线服务', value: `${metrics.value.activeServices ?? activeServiceCount.value} / ${services.value.length}`, hint: '当前健康实例', icon: Server }
])

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatRate(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)}%` : '暂无'
}

function formatRuntimeMetric(value, suffix = '') {
  if (value === null || value === undefined || value === '') return '-'
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${numeric}${suffix}` : String(value)
}

function formatDateTime(value) {
  if (!value || Number.isNaN(new Date(value).getTime())) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatTime(value) {
  if (!value || Number.isNaN(new Date(value).getTime())) return '--:--:--'
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
}

function serviceLabel(status) {
  return { UP: '正常', DEGRADED: '异常', DOWN: '离线', CHECKING: '检查中' }[status] || '未知'
}

function statusEventTitle(service, previousStatus, nextStatus) {
  const name = service.detail || service.name
  if (nextStatus === 'UP') return `${name}恢复正常`
  if (nextStatus === 'DOWN') return `${name}离线`
  if (nextStatus === 'DEGRADED') return `${name}状态异常`
  return `${name}状态已更新`
}

function recordStatusTransitions(nextServices, checkedAt) {
  const nextStatuses = new Map()
  const events = []

  nextServices.forEach((service) => {
    const nextStatus = service.status
    const previousStatus = previousServiceStatuses.get(service.name)
    nextStatuses.set(service.name, nextStatus)
    if (!previousStatus || previousStatus === nextStatus || nextStatus === 'CHECKING') return

    events.push({
      id: `${service.name}-${checkedAt}-${nextStatus}`,
      title: statusEventTitle(service, previousStatus, nextStatus),
      detail: `${serviceLabel(previousStatus)} -> ${serviceLabel(nextStatus)}`,
      service: service.detail || service.name,
      status: nextStatus,
      createdAt: checkedAt
    })
  })

  previousServiceStatuses = nextStatuses
  if (events.length) monitoringEvents.value = [...events, ...monitoringEvents.value].slice(0, MAX_MONITORING_EVENTS)
}

function markMonitoringUnavailable(checkedAt) {
  const unavailableServices = services.value.map((service) => ({ ...service, status: 'UNKNOWN', checkedAt }))
  recordStatusTransitions(unavailableServices, checkedAt)
  monitoring.value = { ...monitoring.value, status: 'DEGRADED', checkedAt, services: unavailableServices }
  lastUpdatedAt.value = checkedAt
}

async function loadMonitoring({ manual = false } = {}) {
  if (pollingInFlight.value) {
    if (manual) {
      queuedManualRefresh = true
      isManualRefreshing.value = true
    }
    return
  }

  pollingInFlight.value = true
  if (manual) isManualRefreshing.value = true
  if (!hasLoaded.value) {
    isInitialLoading.value = true
    errorMessage.value = ''
  }

  try {
    const data = await getMonitoring()
    const nextMonitoring = {
      status: String(data?.status || 'CHECKING').toUpperCase(),
      checkedAt: data?.checkedAt || new Date().toISOString(),
      metrics: data?.metrics || {},
      services: Array.isArray(data?.services) ? data.services : []
    }
    const checkedAt = nextMonitoring.checkedAt
    recordStatusTransitions(nextMonitoring.services.map(normalizeService), checkedAt)
    monitoring.value = nextMonitoring
    lastUpdatedAt.value = new Date().toISOString()
    hasLoaded.value = true
    errorMessage.value = ''
  } catch (error) {
    if (!hasLoaded.value) {
      errorMessage.value = error.message || '监控数据加载失败，请稍后重试。'
    } else {
      markMonitoringUnavailable(new Date().toISOString())
    }
  } finally {
    pollingInFlight.value = false
    isInitialLoading.value = false

    if (queuedManualRefresh) {
      queuedManualRefresh = false
      void loadMonitoring({ manual: true })
    } else {
      isManualRefreshing.value = false
    }
  }
}

function scheduleMonitoringRefresh() {
  void loadMonitoring()
}

function refreshMonitoring() {
  return loadMonitoring({ manual: true })
}

onMounted(() => {
  void loadMonitoring()
  refreshTimer = window.setInterval(scheduleMonitoringRefresh, REFRESH_INTERVAL)
})
onBeforeUnmount(() => window.clearInterval(refreshTimer))
</script>

<template>
  <section class="admin-section-page admin-monitoring-page">
    <header class="admin-page-intro compact admin-monitoring-intro">
      <div><p>运行健康</p><h2>服务监控</h2><span>实时查看平台服务的健康状态与轻量运行指标。</span></div>
      <div class="admin-monitoring-actions">
        <span class="admin-monitoring-live-status"><i></i>实时监控中</span>
        <button type="button" class="admin-monitoring-refresh" :class="{ 'is-loading': isManualRefreshing }" :disabled="isManualRefreshing" @click="refreshMonitoring"><RefreshCw :size="15" />刷新</button>
        <small>每 1 秒自动刷新</small>
        <small>最后更新：{{ formatTime(lastUpdatedAt) }}</small>
      </div>
    </header>

    <div v-if="isInitialLoading && !hasLoaded" class="admin-monitoring-skeleton" aria-label="监控数据加载中" aria-busy="true"><i v-for="item in 6" :key="item"></i></div>
    <section v-else-if="errorMessage && !hasLoaded" class="admin-card admin-monitoring-error" role="alert"><TriangleAlert :size="20" /><div><strong>无法读取监控数据</strong><p>{{ errorMessage }}</p></div><button type="button" class="admin-ghost-button" @click="loadMonitoring()">重试</button></section>

    <div v-else class="admin-monitoring-content">
      <section class="admin-monitoring-metrics" aria-label="服务运行指标">
        <article v-for="metric in metricCards" :key="metric.label" class="admin-card admin-monitoring-metric"><span><component :is="metric.icon" :size="18" /></span><div><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong><em>{{ metric.hint }}</em></div></article>
      </section>

      <section class="admin-monitoring-grid">
        <article class="admin-card admin-monitoring-services">
          <header class="admin-card-header"><div><span>依赖健康</span><h3>服务状态</h3></div><small class="admin-monitoring-pulse"><i></i>每 1 秒自动刷新</small></header>
          <div class="admin-monitoring-service-list">
            <article v-for="service in services" :key="service.name"><span class="admin-monitoring-service-icon"><Server :size="17" /></span><div><strong>{{ service.name }}</strong><small>{{ service.detail }}</small><dl class="admin-monitoring-runtime-metrics"><div><dt>响应</dt><dd>{{ formatRuntimeMetric(service.responseTime, ' ms') }}</dd></div><div><dt>请求</dt><dd>{{ formatNumber(service.requestCount) }}</dd></div><div><dt>错误率</dt><dd>{{ formatRate(service.errorRate) }}</dd></div><div><dt>CPU</dt><dd>{{ formatRuntimeMetric(service.cpu, '%') }}</dd></div><div><dt>内存</dt><dd>{{ formatRuntimeMetric(service.memory, '%') }}</dd></div></dl></div><aside><b :class="`is-${service.status.toLowerCase()}`"><i></i>{{ serviceLabel(service.status) }}</b><small>{{ formatDateTime(service.checkedAt || monitoring.checkedAt) }}</small></aside></article>
          </div>
        </article>

        <article class="admin-card admin-monitoring-events">
          <header class="admin-card-header"><div><span>状态变化</span><h3>监控事件</h3></div><Activity :size="18" /></header>
          <div v-if="monitoringEvents.length" class="admin-monitoring-event-list">
            <article v-for="event in monitoringEvents" :key="event.id"><i :class="`is-${event.status.toLowerCase()}`"></i><div><strong>{{ event.title }}</strong><small>{{ event.service }} · {{ event.detail }}</small></div><time>{{ formatDateTime(event.createdAt) }}</time></article>
          </div>
          <p v-else class="admin-monitoring-empty">状态变化会显示在这里。</p>
        </article>
      </section>
    </div>
  </section>
</template>

<style scoped>
.admin-monitoring-page,.admin-monitoring-content { display:grid; gap:20px; }.admin-monitoring-intro{align-items:flex-start}.admin-monitoring-actions{display:grid;grid-template-columns:auto auto;justify-content:end;gap:6px 9px}.admin-monitoring-actions>small{color:#9ca3af;font-size:11px;text-align:right}.admin-monitoring-actions>small:last-child{grid-column:1 / -1}.admin-monitoring-live-status,.admin-monitoring-refresh{display:inline-flex;align-items:center;gap:6px;min-height:30px;padding:0 10px;border:1px solid #e5e7eb;border-radius:7px;background:#fff;color:#374151;font-size:12px;font-weight:650}.admin-monitoring-live-status{border-color:#bbf7d0;background:#f0fdf4;color:#15803d;border-radius:999px}.admin-monitoring-live-status i{width:6px;height:6px;border-radius:50%;background:currentColor;box-shadow:0 0 0 3px #dcfce7;animation:admin-monitoring-live 1.8s ease-out infinite}.admin-monitoring-refresh:hover:not(:disabled){background:#f8fafc}.admin-monitoring-refresh:disabled{cursor:wait;opacity:.7}.admin-monitoring-refresh.is-loading svg{animation:admin-monitoring-spin .8s linear infinite}@keyframes admin-monitoring-spin{to{transform:rotate(360deg)}}@keyframes admin-monitoring-live{50%{box-shadow:0 0 0 5px rgba(34,197,94,.08)}}.admin-monitoring-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.admin-monitoring-metric{display:flex;align-items:flex-start;gap:12px;min-width:0;padding:18px}.admin-monitoring-metric>span,.admin-monitoring-service-icon{display:grid;place-items:center;flex:0 0 34px;width:34px;height:34px;border-radius:8px;background:#f1f5f9;color:#2563eb}.admin-monitoring-metric:nth-child(2)>span{background:#ecfdf5;color:#059669}.admin-monitoring-metric:nth-child(3)>span{background:#fffbeb;color:#d97706}.admin-monitoring-metric:nth-child(4)>span{background:#ecfeff;color:#0f766e}.admin-monitoring-metric>div{display:grid;min-width:0;gap:4px}.admin-monitoring-metric small,.admin-monitoring-metric em{overflow:hidden;color:#6b7280;font-size:12px;font-style:normal;text-overflow:ellipsis;white-space:nowrap}.admin-monitoring-metric em{color:#9ca3af;font-size:11px}.admin-monitoring-metric strong{color:#111827;font-size:22px;line-height:1.1;white-space:nowrap}.admin-monitoring-grid{display:grid;grid-template-columns:1.1fr .9fr;gap:20px}.admin-monitoring-services,.admin-monitoring-events{min-width:0;padding:20px}.admin-monitoring-pulse{display:inline-flex;align-items:center;gap:5px;color:#6b7280;font-size:11px}.admin-monitoring-pulse i{width:6px;height:6px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 3px #dcfce7}.admin-monitoring-service-list article{display:grid;grid-template-columns:34px minmax(0,1fr) auto;align-items:start;gap:11px;min-height:66px;padding:11px 0;border-top:1px solid #f1f5f9}.admin-monitoring-service-icon{width:32px;height:32px;color:#64748b}.admin-monitoring-service-list article>div,.admin-monitoring-event-list article>div{display:grid;min-width:0;gap:3px}.admin-monitoring-service-list strong,.admin-monitoring-event-list strong{overflow:hidden;color:#111827;font-size:13px;font-weight:650;text-overflow:ellipsis;white-space:nowrap}.admin-monitoring-service-list small,.admin-monitoring-event-list small{overflow:hidden;color:#6b7280;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.admin-monitoring-service-list aside{display:grid;justify-items:end;gap:4px;padding-top:2px}.admin-monitoring-service-list b{display:inline-flex;align-items:center;gap:5px;color:#15803d;font-size:12px}.admin-monitoring-service-list b.is-degraded{color:#c2410c}.admin-monitoring-service-list b.is-down{color:#dc2626}.admin-monitoring-service-list b.is-checking{color:#2563eb}.admin-monitoring-service-list b.is-unknown{color:#6b7280}.admin-monitoring-service-list b i{width:6px;height:6px;border-radius:50%;background:currentColor}.admin-monitoring-service-list aside small{color:#9ca3af;font-size:10px}.admin-monitoring-event-list article{display:grid;grid-template-columns:7px minmax(0,1fr) auto;align-items:start;gap:10px;padding:11px 0;border-top:1px solid #f1f5f9}.admin-monitoring-event-list article>i{width:7px;height:7px;margin-top:5px;border-radius:50%;background:#22c55e}.admin-monitoring-event-list article>i.is-degraded{background:#f97316}.admin-monitoring-event-list article>i.is-down{background:#ef4444}.admin-monitoring-event-list time{color:#9ca3af;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:10px;white-space:nowrap}.admin-monitoring-empty{margin:18px 0 4px;color:#9ca3af;font-size:13px}.admin-monitoring-error{display:flex;align-items:center;gap:12px;padding:22px;color:#b91c1c}.admin-monitoring-error>div{flex:1;min-width:0}.admin-monitoring-error strong{color:#991b1b;font-size:14px}.admin-monitoring-error p{margin:4px 0 0;color:#7f1d1d;font-size:13px}.admin-monitoring-skeleton{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.admin-monitoring-skeleton i{min-height:104px;border-radius:12px;background:linear-gradient(100deg,#eef2f7 28%,#f8fafc 44%,#eef2f7 62%);background-size:250% 100%;animation:admin-monitoring-skeleton 1.2s linear infinite}.admin-monitoring-skeleton i:nth-child(n+5){grid-column:span 2;min-height:220px}@keyframes admin-monitoring-skeleton{to{background-position:-250% 0}}.admin-monitoring-runtime-metrics{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:6px;margin:7px 0 0}.admin-monitoring-runtime-metrics div{display:grid;gap:2px;min-width:0}.admin-monitoring-runtime-metrics dt{color:#9ca3af;font-size:10px}.admin-monitoring-runtime-metrics dd{overflow:hidden;margin:0;color:#4b5563;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:10px;text-overflow:ellipsis;white-space:nowrap}
:global(html[data-theme='dark']) .admin-monitoring-live-status{border-color:#14532d;background:#052e16;color:#86efac}:global(html[data-theme='dark']) .admin-monitoring-refresh{border-color:#3f3f46;background:#18181b;color:#d4d4d8}:global(html[data-theme='dark']) .admin-monitoring-metric strong,:global(html[data-theme='dark']) .admin-monitoring-service-list strong,:global(html[data-theme='dark']) .admin-monitoring-event-list strong{color:#f4f4f5}:global(html[data-theme='dark']) .admin-monitoring-service-list article,:global(html[data-theme='dark']) .admin-monitoring-event-list article{border-color:#27272a}:global(html[data-theme='dark']) .admin-monitoring-service-icon{background:#27272a;color:#a1a1aa}:global(html[data-theme='dark']) .admin-monitoring-runtime-metrics dd{color:#a1a1aa}
@media(max-width:980px){.admin-monitoring-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.admin-monitoring-grid{grid-template-columns:1fr}}@media(max-width:640px){.admin-monitoring-actions{grid-template-columns:1fr;justify-content:stretch}.admin-monitoring-actions>small,.admin-monitoring-actions>small:last-child{text-align:left;grid-column:auto}.admin-monitoring-service-list article{grid-template-columns:34px minmax(0,1fr)}.admin-monitoring-service-list aside{grid-column:2;justify-items:start}.admin-monitoring-event-list time{display:none}.admin-monitoring-runtime-metrics{grid-template-columns:repeat(3,minmax(0,1fr))}}
</style>
