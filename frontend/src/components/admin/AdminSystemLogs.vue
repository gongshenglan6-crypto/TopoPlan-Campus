<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, ChevronLeft, ChevronRight, Copy, Filter, Search, X } from '@lucide/vue'
import LogFilterDropdown from './LogFilterDropdown.vue'
import { listAuditLogs } from '../../api/admin'

const logs = ref([])
const totalLogCount = ref(0)
const dateRange = ref('today')
const level = ref('all')
const moduleName = ref('all')
const query = ref('')
const currentPage = ref(1)
const pageSizes = [20, 50, 100]
const pageSize = ref(20)
const pageSizeOptions = pageSizes.map((size) => ({ value: size, label: String(size) }))
const selectedLog = ref(null)
const copiedTraceId = ref('')
const toast = ref('')
let toastTimer = 0
let copiedTimer = 0

const moduleLabels = {
  'billing.tiers': '用户等级',
  'system.rules': '规则设置',
  'models.prompts': 'Prompt 配置',
  'models.channels': 'AI 模型配置',
  'knowledge.assets': '知识库',
  'risk.blacklist': '安全与风控',
  'audit.logs': '系统日志'
}

const modules = computed(() => [...new Set(logs.value.map((log) => log.module))])
const dateRangeOptions = [
  { value: 'hour', label: '最近 1 小时' },
  { value: 'today', label: '今天' },
  { value: 'week', label: '最近 7 天' }
]
const levelOptions = [
  { value: 'all', label: '全部' },
  { value: 'INFO', label: 'INFO' },
  { value: 'WARNING', label: 'WARNING' },
  { value: 'ERROR', label: 'ERROR' },
  { value: 'SECURITY', label: 'SECURITY' }
]
const moduleOptions = computed(() => [
  { value: 'all', label: '全部模块' },
  ...modules.value.map((item) => ({ value: item, label: formatModule(item) }))
])
const filteredLogs = computed(() => logs.value.filter((log) => {
  const keyword = query.value.trim().toLowerCase()
  const matchesRange = dateRange.value !== 'hour' || log.id <= 2
  const matchesLevel = level.value === 'all' || log.level === level.value
  const matchesModule = moduleName.value === 'all' || log.module === moduleName.value
  const matchesKeyword = !keyword || [log.title, log.detail, log.traceId, log.module, log.userId, log.ip].some((value) => String(value).toLowerCase().includes(keyword))
  return matchesRange && matchesLevel && matchesModule && matchesKeyword
}))
const totalRecords = computed(() => {
  const hasActiveFilter = dateRange.value !== 'today' || level.value !== 'all' || moduleName.value !== 'all' || query.value.trim()
  return hasActiveFilter ? filteredLogs.value.length : Math.max(totalLogCount.value, filteredLogs.value.length)
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredLogs.value.length / pageSize.value)))
const visiblePages = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1))
const paginatedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredLogs.value.slice(start, start + pageSize.value)
})
const rangeStart = computed(() => filteredLogs.value.length ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, filteredLogs.value.length))

watch([dateRange, level, moduleName, query, pageSize], () => {
  currentPage.value = 1
})
watch(totalPages, (pages) => {
  if (currentPage.value > pages) currentPage.value = pages
})

function changePage(delta) {
  currentPage.value = Math.min(totalPages.value, Math.max(1, currentPage.value + delta))
}

function setPage(page) {
  currentPage.value = Math.min(totalPages.value, Math.max(1, page))
}

function openLogDetails(log) {
  selectedLog.value = log
}

function closeLogDetails() {
  selectedLog.value = null
}

function formatTimestamp(timestamp) {
  return timestamp.replace(/\.\d{3}$/, '')
}

function formatCount(value) {
  return value.toLocaleString('zh-CN')
}

function formatModule(module) {
  const normalized = String(module ?? '').trim()
  return moduleLabels[normalized] || normalized.replace(/[._-]+/g, ' ') || '系统'
}

function formatResult(result) {
  const labels = { SUCCESS: '成功', FAILED: '失败', FAILURE: '失败', ERROR: '失败', BLOCKED: '已拦截' }
  return labels[String(result).toUpperCase()] || result || '未知'
}

function normalizeLogLevel(entry) {
  const currentLevel = String(entry.level || entry.severity || '').toUpperCase()
  if (currentLevel === 'WARN') return 'WARNING'
  if (['INFO', 'WARNING', 'ERROR', 'SECURITY'].includes(currentLevel)) return currentLevel
  if (entry.action === 'RISK_BLOCK') return 'SECURITY'
  return entry.result === 'SUCCESS' ? 'INFO' : 'ERROR'
}

function formatAuditEvent(entry) {
  const detail = String(entry.detail ?? '').trim()
  const resource = String(entry.module ?? '').trim()
  const conciseDetail = detail.length > 16 ? `${detail.slice(0, 8)}...` : detail
  const technicalDetail = [resource, conciseDetail].filter(Boolean).join(' · ')
  const resourceLabels = {
    'system.rules': '更新规则设置',
    'models.prompts': '修改 AI 模型 Prompt 配置',
    'knowledge.assets': '更新知识库文档解析状态',
    'models.channels': '更新 AI 模型接入配置',
    'risk.blacklist': '更新风险拦截黑名单'
  }

  if (resourceLabels[resource]) return { title: resourceLabels[resource], detail: technicalDetail }
  if (entry.action === 'LOGIN') return { title: entry.actor === 'admin' ? '管理员登录系统' : '用户登录系统', detail: technicalDetail }
  if (entry.action === 'PROMPT_QUERY') return { title: '发起 AI 模型提问', detail: technicalDetail }
  if (entry.action === 'RISK_BLOCK') return { title: '拦截高风险恶意提问', detail: technicalDetail || '安全与风控' }

  const actionLabels = { CREATE: '新建配置', UPDATE: '更新配置', DELETE: '删除配置', SAVE: '保存配置' }
  return { title: actionLabels[entry.action] ?? '记录系统操作', detail: technicalDetail }
}

async function copyTrace(traceId) {
  try {
    await navigator.clipboard.writeText(traceId)
    copiedTraceId.value = traceId
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => { copiedTraceId.value = '' }, 1800)
  } catch {
    toast.value = `Trace ID：${traceId}`
    window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
  }
}

async function loadAuditLogs() {
  try {
    const result = await listAuditLogs({ page: 0, size: 100 })
    totalLogCount.value = Number(result.total ?? result.items?.length ?? 0)
    logs.value = (result.items ?? []).map((entry) => ({
      id: entry.id,
      timestamp: new Date(entry.createdAt).toLocaleString('sv-SE').replace('T', ' '),
      userId: entry.userId || entry.actor || 'system',
      ip: entry.ip || '127.0.0.1',
      result: entry.result || 'SUCCESS',
      level: normalizeLogLevel(entry),
      module: entry.module || 'audit.logs',
      traceId: `audit_${entry.id}`,
      rawDetail: String(entry.detail ?? ''),
      ...formatAuditEvent(entry)
    }))
  } catch (error) {
    toast.value = error.message || 'Audit logs failed to load.'
  }
}

onMounted(loadAuditLogs)
onBeforeUnmount(() => {
  window.clearTimeout(toastTimer)
  window.clearTimeout(copiedTimer)
})
</script>

<template>
  <section class="admin-section-page admin-system-logs-page">
    <header class="admin-page-intro compact admin-logs-intro"><div><h2>系统日志</h2><span>集中查看服务请求、异常事件与可追踪的 API 调用链路。</span></div><span class="admin-log-retention">日志保留 30 天</span></header>
    <section class="admin-card admin-logs-console">
      <div class="admin-log-command-bar" aria-label="系统日志过滤器">
        <span class="admin-log-filter-icon"><Filter :size="15" /></span>
        <LogFilterDropdown v-model="dateRange" label="日期范围" :options="dateRangeOptions" />
        <LogFilterDropdown v-model="level" label="日志级别" :options="levelOptions" />
        <LogFilterDropdown v-model="moduleName" class="is-module-filter" label="操作模块" :options="moduleOptions" />
        <label class="admin-log-search"><Search :size="16" /><input v-model="query" type="search" placeholder="搜索事件描述、Trace ID、IP 或用户..." aria-label="搜索系统日志" /></label>
        <span class="admin-log-count">共 {{ formatCount(totalRecords) }} 条</span>
      </div>

      <div class="admin-logs-table-wrap">
        <table class="admin-logs-table">
          <thead><tr><th>时间</th><th>等级</th><th>用户 / 模块</th><th>操作</th><th>Trace ID</th><th>IP 地址</th><th>执行结果</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="log in paginatedLogs" :key="log.id" class="admin-log-row">
              <td class="admin-log-timestamp">{{ formatTimestamp(log.timestamp) }}</td>
              <td><span class="admin-log-level" :class="`is-${log.level.toLowerCase()}`"><i aria-hidden="true"></i>{{ log.level }}</span></td>
              <td class="admin-log-user-module"><strong>{{ log.userId }}</strong><small>{{ formatModule(log.module) }}</small></td>
              <td class="admin-log-operation">{{ log.title }}</td>
              <td class="admin-log-trace"><span class="admin-trace-id">{{ log.traceId }}</span><button type="button" class="admin-copy-trace" :aria-label="`复制 Trace ID ${log.traceId}`" @click.stop="copyTrace(log.traceId)"><Check v-if="copiedTraceId === log.traceId" :size="13" /><Copy v-else :size="13" /></button></td>
              <td class="admin-log-ip">{{ log.ip }}</td>
              <td><span class="admin-log-result" :class="`is-${log.result.toLowerCase()}`">{{ formatResult(log.result) }}</span></td>
              <td class="admin-log-action"><button type="button" @click="openLogDetails(log)">详情</button></td>
            </tr>
            <tr v-if="!paginatedLogs.length"><td colspan="8" class="admin-log-empty"><Search :size="18" /> 未找到匹配的系统日志</td></tr>
          </tbody>
        </table>
      </div>

      <footer class="admin-log-pagination" aria-label="系统日志分页">
        <p>显示 {{ rangeStart }} - {{ rangeEnd }} 条，共 {{ formatCount(totalRecords) }} 条</p>
        <div class="admin-log-pagination-actions">
          <LogFilterDropdown v-model="pageSize" class="admin-log-page-size-dropdown" label="每页" :options="pageSizeOptions" />
          <button type="button" class="admin-log-page-button" :disabled="currentPage === 1" aria-label="上一页" @click="changePage(-1)"><ChevronLeft :size="15" /> 上一页</button>
          <button v-for="page in visiblePages" :key="page" type="button" class="admin-log-page-number" :class="{ active: currentPage === page }" :aria-current="currentPage === page ? 'page' : undefined" @click="setPage(page)">{{ page }}</button>
          <button type="button" class="admin-log-page-button" :disabled="currentPage === totalPages" aria-label="下一页" @click="changePage(1)">下一页 <ChevronRight :size="15" /></button>
        </div>
      </footer>
    </section>

    <Teleport to="body">
      <Transition name="admin-log-drawer">
        <div v-if="selectedLog" class="admin-log-drawer-backdrop" @pointerdown.self="closeLogDetails">
          <aside class="admin-log-details-drawer" role="dialog" aria-modal="true" aria-label="系统日志详情">
            <header><div><span>系统日志</span><h3>{{ selectedLog.title }}</h3></div><button type="button" aria-label="关闭日志详情" @click="closeLogDetails"><X :size="18" /></button></header>
            <dl>
              <div><dt>时间</dt><dd>{{ formatTimestamp(selectedLog.timestamp) }}</dd></div>
              <div><dt>等级</dt><dd><span class="admin-log-level" :class="`is-${selectedLog.level.toLowerCase()}`"><i aria-hidden="true"></i>{{ selectedLog.level }}</span></dd></div>
              <div><dt>用户</dt><dd>{{ selectedLog.userId }}</dd></div>
              <div><dt>模块</dt><dd>{{ formatModule(selectedLog.module) }}</dd></div>
              <div><dt>Trace ID</dt><dd class="admin-trace-id">{{ selectedLog.traceId }}</dd></div>
              <div><dt>IP 地址</dt><dd>{{ selectedLog.ip }}</dd></div>
              <div><dt>执行结果</dt><dd>{{ formatResult(selectedLog.result) }}</dd></div>
            </dl>
            <section><h4>完整日志</h4><p>{{ selectedLog.rawDetail || selectedLog.detail || '未提供附加日志信息。' }}</p></section>
          </aside>
        </div>
      </Transition>
    </Teleport>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.admin-logs-table { width: 100%; min-width: 1120px; border-collapse: collapse; table-layout: fixed; }
.admin-system-logs-page .admin-logs-table th { height: 40px; padding: 0 12px; border-bottom: 1px solid #e5e7eb; background: #fafafa; color: #6b7280; font-size: 12px; font-weight: 600; text-align: left; white-space: nowrap; }
.admin-logs-table th:nth-child(1) { width: 15%; }
.admin-logs-table th:nth-child(2) { width: 10%; }
.admin-logs-table th:nth-child(3) { width: 14%; }
.admin-logs-table th:nth-child(4) { width: 17%; }
.admin-logs-table th:nth-child(5) { width: 13%; }
.admin-logs-table th:nth-child(6) { width: 11%; }
.admin-logs-table th:nth-child(7) { width: 10%; }
.admin-logs-table th:nth-child(8) { width: 10%; }
.admin-system-logs-page .admin-log-row { height: 52px; min-height: 0; cursor: default; }
.admin-system-logs-page .admin-log-row td { height: 52px; padding: 6px 12px; border-bottom: 1px solid #f1f5f9; box-sizing: border-box; color: #4b5563; font-size: 13px; line-height: 1.2; vertical-align: middle; white-space: nowrap; }
.admin-system-logs-page .admin-log-row:hover { background: #f9fafb; }
.admin-system-logs-page .admin-log-row td:last-child { min-width: 0; }
.admin-system-logs-page .admin-log-timestamp { color: #6b7280; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; }
.admin-log-user-module strong,.admin-log-user-module small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-log-user-module strong { color: #111827; font-size: 13px; font-weight: 600; line-height: 16px; }
.admin-log-user-module small { margin-top: 1px; color: #6b7280; font-size: 11px; line-height: 14px; }
.admin-log-operation,.admin-log-ip { overflow: hidden; color: #111827; font-weight: 500; text-overflow: ellipsis; }
.admin-log-trace { overflow: hidden; }
.admin-log-level { display: inline-flex; align-items: center; gap: 5px; min-height: 20px; padding: 0 7px; border: 1px solid #dbe4ee; border-radius: 999px; background: #f8fafc; color: #475569; font-size: 11px; font-weight: 700; }
.admin-log-level i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.admin-log-level.is-info { border-color: #dbeafe; background: #f8fbff; color: #475569; }
.admin-log-level.is-warning { border-color: #fed7aa; background: #fffaf3; color: #c2410c; }
.admin-log-level.is-error { border-color: #fecaca; background: #fff7f7; color: #dc2626; }
.admin-log-level.is-security { border-color: #e9d5ff; background: #faf5ff; color: #7e22ce; }
.admin-trace-id { display: inline-flex; max-width: calc(100% - 26px); overflow: hidden; padding: 2px 6px; border: 1px solid #e5e7eb; border-radius: 4px; background: #f3f4f6; color: #4b5563; font-family: monospace; font-size: 12px; line-height: 16px; text-overflow: ellipsis; vertical-align: middle; white-space: nowrap; }
.admin-copy-trace { display: inline-grid; width: 20px; height: 20px; margin-left: 4px; padding: 0; place-items: center; border: 0; border-radius: 4px; background: transparent; color: #9ca3af; vertical-align: middle; }
.admin-copy-trace:hover,.admin-copy-trace:focus-visible { background: #f3f4f6; color: #111827; }
.admin-log-result { display: inline-flex; min-height: 20px; align-items: center; color: #15803d; font-size: 12px; font-weight: 600; }
.admin-log-result.is-failed,.admin-log-result.is-failure,.admin-log-result.is-error { color: #dc2626; }
.admin-log-result.is-blocked { color: #c2410c; }
.admin-log-action button,.admin-log-page-number { min-height: 28px; padding: 0 8px; border: 1px solid #e5e7eb; border-radius: 5px; background: #ffffff; color: #4b5563; font-size: 12px; font-weight: 600; }
.admin-log-action button:hover,.admin-log-action button:focus-visible,.admin-log-page-number:hover,.admin-log-page-number:focus-visible { border-color: #cbd5e1; background: #f8fafc; color: #111827; outline: 0; }
.admin-log-page-number.active { border-color: #18181b; background: #18181b; color: #ffffff; }
.admin-log-empty { height: 156px; color: #9ca3af !important; text-align: center; }
.admin-log-pagination { display: flex; min-height: 60px; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 18px; border-top: 1px solid #e5e7eb; color: #6b7280; }
.admin-log-pagination p { margin: 0; font-size: 13px; white-space: nowrap; }
.admin-log-pagination-actions { display: flex; align-items: center; gap: 6px; }
.admin-log-page-size-dropdown { min-width: 86px; margin-right: 6px; }
.admin-log-page-size-dropdown :deep(.admin-log-filter-trigger) { min-height: 28px; height: 28px; padding: 0 7px; gap: 4px; }
.admin-log-page-size-dropdown :deep(.admin-log-filter-trigger > span) { font-size: 11px; }
.admin-log-page-size-dropdown :deep(.admin-log-filter-trigger > strong) { font-size: 12px; }
.admin-log-page-size-dropdown :deep(.admin-log-filter-menu) { width: 86px; }
.admin-log-page-button { display: inline-flex; min-height: 28px; align-items: center; gap: 4px; padding: 0 8px; border: 1px solid #e5e7eb; border-radius: 5px; background: #ffffff; color: #4b5563; font-size: 12px; font-weight: 600; }
.admin-log-page-button:hover:not(:disabled) { background: #f8fafc; color: #111827; }
.admin-log-page-button:disabled { cursor: not-allowed; opacity: 0.5; }
.admin-log-drawer-backdrop { position: fixed; z-index: 140; inset: 0; display: flex; justify-content: flex-end; background: rgba(9, 9, 11, 0.36); backdrop-filter: blur(2px); }
.admin-log-details-drawer { width: min(420px, 100vw); height: 100%; padding: 22px; overflow-y: auto; border-left: 1px solid #e5e7eb; background: #ffffff; box-shadow: -18px 0 46px rgba(15, 23, 42, 0.16); }
.admin-log-details-drawer header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.admin-log-details-drawer header span { color: #6b7280; font-size: 12px; }
.admin-log-details-drawer h3 { margin: 4px 0 0; color: #111827; font-size: 17px; line-height: 1.35; }
.admin-log-details-drawer header button { display: grid; width: 30px; height: 30px; flex: 0 0 auto; padding: 0; place-items: center; border: 1px solid #e5e7eb; border-radius: 6px; background: #ffffff; color: #6b7280; }
.admin-log-details-drawer header button:hover { background: #f9fafb; color: #111827; }
.admin-log-details-drawer dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 24px 0; }
.admin-log-details-drawer dl div { min-width: 0; padding: 10px; border: 1px solid #f1f5f9; border-radius: 7px; background: #fafafa; }
.admin-log-details-drawer dt { margin-bottom: 5px; color: #6b7280; font-size: 11px; }
.admin-log-details-drawer dd { min-width: 0; margin: 0; overflow: hidden; color: #111827; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.admin-log-details-drawer dd.admin-trace-id { display: block; max-width: none; }
.admin-log-details-drawer section { padding-top: 18px; border-top: 1px solid #e5e7eb; }
.admin-log-details-drawer h4 { margin: 0 0 8px; color: #111827; font-size: 13px; }
.admin-log-details-drawer p { margin: 0; color: #4b5563; font-size: 13px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.admin-log-drawer-enter-active,.admin-log-drawer-leave-active { transition: opacity 180ms ease; }
.admin-log-drawer-enter-active .admin-log-details-drawer,.admin-log-drawer-leave-active .admin-log-details-drawer { transition: transform 220ms ease; }
.admin-log-drawer-enter-from,.admin-log-drawer-leave-to { opacity: 0; }
.admin-log-drawer-enter-from .admin-log-details-drawer,.admin-log-drawer-leave-to .admin-log-details-drawer { transform: translateX(100%); }

:global(html[data-theme='dark']) .admin-system-logs-page .admin-logs-table th { border-color: #27272a; background: #18181b; color: #a1a1aa; }
:global(html[data-theme='dark']) .admin-system-logs-page .admin-log-row td { border-color: #27272a; color: #d4d4d8; }
:global(html[data-theme='dark']) .admin-system-logs-page .admin-log-row:hover { background: rgba(255, 255, 255, 0.04); }
:global(html[data-theme='dark']) .admin-log-user-module strong,:global(html[data-theme='dark']) .admin-log-operation,:global(html[data-theme='dark']) .admin-log-ip { color: #fafafa; }
:global(html[data-theme='dark']) .admin-trace-id,:global(html[data-theme='dark']) .admin-log-page-button,:global(html[data-theme='dark']) .admin-log-page-number,:global(html[data-theme='dark']) .admin-log-action button { border-color: #3f3f46; background: #18181b; color: #d4d4d8; }
:global(html[data-theme='dark']) .admin-log-details-drawer { border-color: #27272a; background: #18181b; }
:global(html[data-theme='dark']) .admin-log-details-drawer h3,:global(html[data-theme='dark']) .admin-log-details-drawer h4,:global(html[data-theme='dark']) .admin-log-details-drawer dd { color: #fafafa; }
:global(html[data-theme='dark']) .admin-log-details-drawer dl div { border-color: #27272a; background: #111113; }
:global(html[data-theme='dark']) .admin-log-details-drawer section { border-color: #27272a; }
:global(html[data-theme='dark']) .admin-log-details-drawer p { color: #d4d4d8; }

@media (max-width: 760px) {
  .admin-log-pagination { align-items: flex-start; flex-direction: column; }
  .admin-log-pagination-actions { width: 100%; overflow-x: auto; padding-bottom: 2px; }
  .admin-log-details-drawer { padding: 18px; }
}
</style>
