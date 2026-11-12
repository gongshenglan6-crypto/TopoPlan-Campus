<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getAdminLoginRecordStats, listAdminLoginRecords } from '../../api/admin'
import {
  ChevronLeft,
  ChevronRight,
  Eye,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldAlert,
  X
} from '@lucide/vue'

const pageSizes = [10, 20, 50]
const accountTypeOptions = ['全部', '普通用户', '后台管理员']
const loginResultOptions = ['全部', '成功', '失败']
const riskStatusOptions = ['全部', '正常', '异常', '高风险']
const loginMethodOptions = ['全部', '账号密码', '管理员登录']
const timeRangeOptions = [
  { value: 'today', label: '今天' },
  { value: 'week', label: '近7天' },
  { value: 'month', label: '近30天' },
  { value: 'custom', label: '自定义' }
]

const loginRecords = ref([])
const totalRecords = ref(0)
const stats = ref({ today: 0, success: 0, failure: 0, risk: 0 })
const query = ref('')
const accountType = ref('全部')
const loginResult = ref('全部')
const riskStatus = ref('全部')
const loginMethod = ref('全部')
const timeRange = ref('today')
const customStartDate = ref(localDate())
const customEndDate = ref(localDate())
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRecord = ref(null)
const isRefreshing = ref(false)
const toast = ref('')
let toastTimer = 0

const totalPages = computed(() => Math.max(1, Math.ceil(totalRecords.value / pageSize.value)))
const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(currentPage.value - 2, totalPages.value - 4))
  return Array.from({ length: Math.min(5, totalPages.value) }, (_, index) => start + index)
})
const hasActiveFilters = computed(() => query.value.trim() || accountType.value !== '全部' || loginResult.value !== '全部'
  || riskStatus.value !== '全部' || loginMethod.value !== '全部' || timeRange.value !== 'today')

function localDate(date = new Date()) {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return offsetDate.toISOString().slice(0, 10)
}

function formatTimestamp(value) {
  if (!value) return '-'
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
  }).formatToParts(new Date(value)).reduce((result, part) => ({ ...result, [part.type]: part.value }), {})
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

function mapRecord(record) {
  return {
    ...record,
    name: record.displayName || record.loginIdentifier || '未知用户',
    userId: record.userId ?? '—',
    timestamp: formatTimestamp(record.createdAt),
    method: record.loginMethod || '账号密码',
    ip: record.ipAddress || '未知IP',
    location: record.location || '未知地区',
    device: record.device || '未知设备',
    os: record.operatingSystem || '未知操作系统',
    browser: record.browser || '未知浏览器',
    userAgent: record.userAgent || '未提供',
    failureReason: record.failureReason || '—',
    riskStatus: record.riskStatus || '正常'
  }
}

function dateRange() {
  if (timeRange.value === 'custom') {
    const start = new Date(`${customStartDate.value}T00:00:00`)
    const end = new Date(`${customEndDate.value}T00:00:00`)
    end.setDate(end.getDate() + 1)
    return { startAt: start.toISOString(), endAt: end.toISOString() }
  }

  const start = new Date()
  start.setHours(0, 0, 0, 0)
  if (timeRange.value === 'week') start.setDate(start.getDate() - 6)
  if (timeRange.value === 'month') start.setDate(start.getDate() - 29)
  return { startAt: start.toISOString() }
}

function requestOptions() {
  return {
    page: currentPage.value - 1,
    size: pageSize.value,
    keyword: query.value,
    accountType: accountType.value,
    result: loginResult.value,
    riskStatus: riskStatus.value,
    loginMethod: loginMethod.value,
    ...dateRange()
  }
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

async function loadRecords({ includeStats = false } = {}) {
  isRefreshing.value = true
  try {
    const requests = [listAdminLoginRecords(requestOptions())]
    if (includeStats) requests.push(getAdminLoginRecordStats())
    const [data, nextStats] = await Promise.all(requests)
    loginRecords.value = (data?.items || []).map(mapRecord)
    totalRecords.value = data?.total ?? loginRecords.value.length
    if (nextStats) stats.value = { ...stats.value, ...nextStats }
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
      await loadRecords()
    }
  } catch (error) {
    loginRecords.value = []
    totalRecords.value = 0
    showToast(error.message || '登录记录加载失败')
  } finally {
    isRefreshing.value = false
  }
}

async function applyFilters() {
  currentPage.value = 1
  await loadRecords()
  showToast(`已找到 ${formatNumber(totalRecords.value)} 条登录记录`)
}

async function resetFilters() {
  query.value = ''
  accountType.value = '全部'
  loginResult.value = '全部'
  riskStatus.value = '全部'
  loginMethod.value = '全部'
  timeRange.value = 'today'
  customStartDate.value = localDate()
  customEndDate.value = localDate()
  currentPage.value = 1
  await loadRecords()
}

async function refreshRecords() {
  await loadRecords({ includeStats: true })
  if (!toast.value) showToast('登录记录已刷新')
}

function openDetail(record) {
  selectedRecord.value = record
}

function closeDetail() {
  selectedRecord.value = null
}

async function changePage(page) {
  const nextPage = Math.max(1, Math.min(totalPages.value, page))
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
  await loadRecords()
}

async function changePageSize() {
  currentPage.value = 1
  await loadRecords()
}

onBeforeUnmount(() => {
  window.clearTimeout(toastTimer)
})

onMounted(() => loadRecords({ includeStats: true }))
</script>

<template>
  <section class="admin-section-page admin-login-records-page">
    <header class="admin-page-intro compact admin-login-records-intro">
      <div>
        <p>用户与权限 / 登录记录</p>
        <h2>登录记录</h2>
        <span>查看平台用户及后台管理员登录行为与异常访问情况。</span>
      </div>
      <button type="button" class="admin-primary-button login-records-refresh" :disabled="isRefreshing" @click="refreshRecords">
        <RefreshCw :size="15" :class="{ 'is-spinning': isRefreshing }" />
        刷新记录
      </button>
    </header>

    <section class="login-stat-grid" aria-label="登录记录统计">
      <article class="admin-card login-stat-card">
        <span>今日登录</span><strong>{{ formatNumber(stats.today) }}</strong><small>平台账号登录行为</small>
      </article>
      <article class="admin-card login-stat-card is-success">
        <span>登录成功</span><strong>{{ formatNumber(stats.success) }}</strong><small>成功验证的访问</small>
      </article>
      <article class="admin-card login-stat-card is-danger">
        <span>登录失败</span><strong>{{ formatNumber(stats.failure) }}</strong><small>需留意失败尝试</small>
      </article>
      <article class="admin-card login-stat-card is-risk">
        <span>异常登录</span><strong>{{ formatNumber(stats.risk) }}</strong><small>待安全审计处理</small>
      </article>
    </section>

    <section class="admin-card login-records-filter-panel" aria-label="登录记录筛选">
      <div class="login-filter-main-row">
        <label class="login-record-search">
          <Search :size="16" />
          <input v-model="query" type="search" placeholder="搜索用户 / 用户ID / IP" aria-label="搜索用户昵称 / 用户ID / IP地址" @keydown.enter.prevent="applyFilters" />
        </label>
        <label class="login-filter-field"><span>账号类型</span><select v-model="accountType" aria-label="账号类型"><option v-for="option in accountTypeOptions" :key="option">{{ option }}</option></select></label>
        <label class="login-filter-field"><span>登录结果</span><select v-model="loginResult" aria-label="登录结果"><option v-for="option in loginResultOptions" :key="option">{{ option }}</option></select></label>
        <label class="login-filter-field"><span>风险状态</span><select v-model="riskStatus" aria-label="风险状态"><option v-for="option in riskStatusOptions" :key="option">{{ option }}</option></select></label>
        <label class="login-filter-field"><span>登录方式</span><select v-model="loginMethod" aria-label="登录方式"><option v-for="option in loginMethodOptions" :key="option">{{ option }}</option></select></label>
      </div>
      <div class="login-filter-actions-row">
        <div class="login-time-filter">
          <span>时间范围</span>
          <div class="login-time-options">
            <button v-for="option in timeRangeOptions" :key="option.value" type="button" :class="{ active: timeRange === option.value }" @click="timeRange = option.value">{{ option.label }}</button>
          </div>
          <div v-if="timeRange === 'custom'" class="login-custom-dates">
            <input v-model="customStartDate" type="date" aria-label="自定义开始日期" />
            <span>至</span>
            <input v-model="customEndDate" type="date" aria-label="自定义结束日期" />
          </div>
        </div>
        <div class="login-filter-actions">
          <button type="button" class="admin-ghost-button" @click="resetFilters"><RotateCcw :size="14" /> 重置</button>
          <button type="button" class="admin-primary-button" @click="applyFilters"><Search :size="14" /> 查询</button>
        </div>
      </div>
    </section>

    <div class="login-audit-notice"><ShieldAlert :size="15" /><span>登录记录仅用于平台账号安全审计，不记录或展示用户项目内容。</span></div>

    <section class="admin-card login-records-table-card">
      <div class="login-records-table-wrap">
        <table class="login-records-table">
          <thead>
            <tr>
              <th>登录时间</th><th>用户</th><th>用户ID</th><th>账号类型</th><th>登录方式</th><th>IP地址</th><th>登录地区</th><th>设备 / 浏览器</th><th>登录结果</th><th>风险状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody :aria-busy="isRefreshing">
            <template v-if="isRefreshing">
              <tr v-for="index in Number(pageSize)" :key="`skeleton-${index}`" class="login-skeleton-row"><td v-for="column in 11" :key="column"><span></span></td></tr>
            </template>
            <template v-else-if="loginRecords.length">
              <tr v-for="record in loginRecords" :key="record.id">
                <td class="login-time">{{ record.timestamp }}</td>
                <td><div class="login-user-cell"><span>{{ record.name.slice(0, 1).toUpperCase() }}</span><strong>{{ record.name }}</strong></div></td>
                <td><code>{{ record.userId }}</code></td>
                <td><span class="login-account-tag" :class="{ 'is-admin': record.accountType === '后台管理员' }">{{ record.accountType }}</span></td>
                <td>{{ record.method }}</td>
                <td><code>{{ record.ip }}</code></td>
                <td>{{ record.location }}</td>
                <td><div class="login-device-cell"><strong>{{ record.device }}</strong><small>{{ record.browser }}</small></div></td>
                <td><span class="login-status-tag" :class="record.result === '成功' ? 'is-success' : 'is-failure'">{{ record.result }}</span></td>
                <td><span class="login-risk-tag" :class="`is-${record.riskStatus === '正常' ? 'normal' : record.riskStatus === '异常' ? 'warning' : 'high'}`">{{ record.riskStatus }}</span></td>
                <td><button type="button" class="login-detail-button" @click="openDetail(record)"><Eye :size="14" /> 查看详情</button></td>
              </tr>
            </template>
            <tr v-else><td colspan="11" class="login-empty-state"><strong>{{ hasActiveFilters ? '未找到符合条件的登录记录，请调整筛选条件。' : '暂无登录记录' }}</strong><span v-if="!hasActiveFilters">当前没有可供审计的账号登录行为。</span><button v-else type="button" @click="resetFilters">重置筛选</button></td></tr>
          </tbody>
        </table>
      </div>

      <footer class="login-records-pagination" aria-label="登录记录分页">
        <p>共 {{ formatNumber(totalRecords) }} 条</p>
        <div class="login-pagination-controls">
          <label>每页<select v-model.number="pageSize" aria-label="每页记录数" @change="changePageSize"><option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option></select></label>
          <button type="button" :disabled="currentPage === 1" aria-label="上一页" @click="changePage(currentPage - 1)"><ChevronLeft :size="15" /></button>
          <button v-for="page in pageNumbers" :key="page" type="button" :class="{ active: currentPage === page }" :aria-label="`第 ${page} 页`" @click="changePage(page)">{{ page }}</button>
          <button type="button" :disabled="currentPage === totalPages" aria-label="下一页" @click="changePage(currentPage + 1)"><ChevronRight :size="15" /></button>
        </div>
      </footer>
    </section>

    <Teleport to="body">
      <Transition name="login-drawer">
        <div v-if="selectedRecord" class="login-drawer-backdrop" @pointerdown.self="closeDetail">
          <aside class="login-detail-drawer" role="dialog" aria-modal="true" aria-label="登录详情">
            <header class="login-detail-header">
              <div><span>账号登录审计</span><h3>登录详情</h3></div>
              <button type="button" class="admin-close-button" aria-label="关闭登录详情" @click="closeDetail"><X :size="18" /></button>
            </header>
            <div class="login-detail-body">
              <section class="login-detail-person">
                <span>{{ selectedRecord.name.slice(0, 1).toUpperCase() }}</span>
                <div><strong>{{ selectedRecord.name }}</strong><small>{{ selectedRecord.userId }} · {{ selectedRecord.accountType }}</small></div>
                <span class="login-status-tag" :class="selectedRecord.result === '成功' ? 'is-success' : 'is-failure'">{{ selectedRecord.result }}</span>
              </section>
              <section class="login-detail-grid">
                <div><span>登录时间</span><strong>{{ selectedRecord.timestamp }}</strong></div>
                <div><span>登录方式</span><strong>{{ selectedRecord.method }}</strong></div>
                <div><span>IP地址</span><strong>{{ selectedRecord.ip }}</strong></div>
                <div><span>IP归属地</span><strong>{{ selectedRecord.location }}</strong></div>
                <div><span>设备类型</span><strong>{{ selectedRecord.device }}</strong></div>
                <div><span>操作系统</span><strong>{{ selectedRecord.os }}</strong></div>
                <div><span>浏览器</span><strong>{{ selectedRecord.browser }}</strong></div>
                <div><span>风险等级</span><strong><span class="login-risk-tag" :class="`is-${selectedRecord.riskStatus === '正常' ? 'normal' : selectedRecord.riskStatus === '异常' ? 'warning' : 'high'}`">{{ selectedRecord.riskStatus }}</span></strong></div>
                <div class="is-wide"><span>User Agent</span><code>{{ selectedRecord.userAgent }}</code></div>
                <div class="is-wide"><span>失败原因</span><strong>{{ selectedRecord.failureReason }}</strong></div>
              </section>
            </div>
          </aside>
        </div>
      </Transition>
    </Teleport>
    <Transition name="login-toast"><p v-if="toast" class="login-records-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.admin-login-records-page { width: min(100%, 1440px); margin: 0 auto; padding: 28px 30px 36px; }
.admin-login-records-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.admin-login-records-intro h2 { margin: 4px 0 6px; }
.admin-login-records-intro p { margin-bottom: 4px; color: #6b7280; font-size: 12px; }
.login-records-refresh { min-height: 34px; padding: 0 12px; }
.login-records-refresh:disabled { cursor: wait; opacity: .72; }
.is-spinning { animation: login-spin .8s linear infinite; }

.login-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.login-stat-card { display: grid; gap: 4px; min-height: 108px; padding: 17px 18px; border: 1px solid #e5e7eb; border-radius: 9px; background: #fff; box-shadow: 0 1px 2px rgba(17, 24, 39, .04); }
.login-stat-card > span { color: #6b7280; font-size: 12px; font-weight: 650; }
.login-stat-card strong { color: #111827; font-size: 24px; line-height: 1.1; font-weight: 750; }
.login-stat-card small { color: #9ca3af; font-size: 11px; }
.login-stat-card.is-success { border-color: #d1fae5; }.login-stat-card.is-success strong { color: #047857; }
.login-stat-card.is-danger { border-color: #fee2e2; }.login-stat-card.is-danger strong { color: #dc2626; }
.login-stat-card.is-risk { border-color: #fed7aa; }.login-stat-card.is-risk strong { color: #c2410c; }

.login-records-filter-panel { padding: 16px 18px; border: 1px solid #e5e7eb; border-radius: 9px; background: #fff; box-shadow: 0 1px 2px rgba(17, 24, 39, .04); }
.login-filter-main-row { display: grid; grid-template-columns: minmax(230px, 1.5fr) repeat(4, minmax(115px, .7fr)); gap: 10px; align-items: end; }
.login-record-search { display: flex; align-items: center; gap: 8px; height: 34px; padding: 0 10px; border: 1px solid #e5e7eb; border-radius: 7px; color: #9ca3af; background: #fff; }
.login-record-search:focus-within { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59, 130, 246, .11); }
.login-record-search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: #111827; font-size: 12px; }
.login-record-search input::placeholder { color: #9ca3af; }
.login-filter-field { display: grid; gap: 5px; min-width: 0; }
.login-filter-field > span,.login-time-filter > span { color: #6b7280; font-size: 11px; font-weight: 650; }
.login-filter-field select,.login-custom-dates input,.login-pagination-controls select { height: 34px; min-width: 0; padding: 0 9px; border: 1px solid #e5e7eb; border-radius: 7px; outline: 0; background: #fff; color: #374151; font-size: 12px; }
.login-filter-field select:focus,.login-custom-dates input:focus,.login-pagination-controls select:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59, 130, 246, .11); }
.login-filter-actions-row { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-top: 13px; padding-top: 13px; border-top: 1px solid #f3f4f6; }
.login-time-filter { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.login-time-options { display: inline-flex; gap: 2px; padding: 2px; border-radius: 7px; background: #f3f4f6; }
.login-time-options button { min-height: 27px; padding: 0 9px; border: 0; border-radius: 5px; background: transparent; color: #6b7280; font-size: 11px; font-weight: 650; cursor: pointer; }
.login-time-options button:hover { color: #111827; }.login-time-options button.active { background: #fff; color: #2563eb; box-shadow: 0 1px 2px rgba(17, 24, 39, .08); }
.login-custom-dates { display: inline-flex; align-items: center; gap: 6px; color: #9ca3af; font-size: 11px; }.login-custom-dates input { height: 29px; }
.login-filter-actions { display: flex; gap: 8px; }.login-filter-actions button { min-height: 33px; padding: 0 11px; font-size: 12px; }

.login-audit-notice { display: flex; align-items: center; gap: 7px; margin: 14px 0; padding: 9px 11px; border: 1px solid #dbeafe; border-radius: 7px; background: #f8fbff; color: #2563eb; font-size: 12px; }
.login-audit-notice svg { flex: 0 0 auto; }
.login-records-table-card { overflow: hidden; border: 1px solid #e5e7eb; border-radius: 9px; background: #fff; box-shadow: 0 1px 2px rgba(17, 24, 39, .04); }
.login-records-table-wrap { overflow-x: auto; }
.login-records-table { width: 100%; min-width: 1410px; border-collapse: collapse; }
.login-records-table th { padding: 11px 13px; border-bottom: 1px solid #e5e7eb; background: #f9fafb; color: #6b7280; font-size: 11px; font-weight: 700; text-align: left; white-space: nowrap; }
.login-records-table td { padding: 11px 13px; border-bottom: 1px solid #f3f4f6; color: #4b5563; font-size: 12px; vertical-align: middle; white-space: nowrap; }
.login-records-table tbody tr:not(.login-skeleton-row):hover { background: #fafafa; }
.login-records-table tbody tr:last-child td { border-bottom: 0; }.login-records-table code { color: #4b5563; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; }
.login-time { color: #374151 !important; font-variant-numeric: tabular-nums; }
.login-user-cell { display: flex; align-items: center; gap: 8px; }.login-user-cell > span { display: grid; width: 24px; height: 24px; place-items: center; border-radius: 50%; background: #eff6ff; color: #2563eb; font-size: 11px; font-weight: 750; }.login-user-cell strong { color: #111827; font-size: 12px; }
.login-account-tag,.login-status-tag,.login-risk-tag { display: inline-flex; align-items: center; min-height: 21px; padding: 0 7px; border: 1px solid #e5e7eb; border-radius: 999px; background: #f9fafb; color: #4b5563; font-size: 10px; font-weight: 700; }
.login-account-tag.is-admin { border-color: #bfdbfe; background: #eff6ff; color: #1d4ed8; }.login-status-tag.is-success { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }.login-status-tag.is-failure,.login-risk-tag.is-high { border-color: #fecaca; background: #fef2f2; color: #dc2626; }.login-risk-tag.is-normal { border-color: #d1fae5; background: #f0fdf4; color: #047857; }.login-risk-tag.is-warning { border-color: #fed7aa; background: #fff7ed; color: #c2410c; }
.login-device-cell { display: grid; gap: 3px; }.login-device-cell strong { color: #374151; font-size: 11px; font-weight: 650; }.login-device-cell small { color: #9ca3af; font-size: 10px; }
.login-detail-button { display: inline-flex; align-items: center; gap: 5px; min-height: 27px; padding: 0; border: 0; background: transparent; color: #2563eb; font-size: 11px; font-weight: 700; cursor: pointer; }.login-detail-button:hover { color: #1d4ed8; text-decoration: underline; }
.login-empty-state { padding: 46px 16px !important; text-align: center; }.login-empty-state strong,.login-empty-state span { display: block; }.login-empty-state strong { color: #374151; font-size: 13px; }.login-empty-state span { margin-top: 5px; color: #9ca3af; font-size: 12px; }.login-empty-state button { margin-top: 9px; border: 0; background: transparent; color: #2563eb; font-size: 12px; cursor: pointer; }
.login-skeleton-row td span { display: block; width: 76%; height: 13px; border-radius: 4px; background: linear-gradient(90deg, #f3f4f6 20%, #e5e7eb 40%, #f3f4f6 60%); background-size: 200% 100%; animation: login-skeleton 1.2s ease-in-out infinite; }
.login-records-pagination { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 14px; border-top: 1px solid #f3f4f6; }.login-records-pagination p { margin: 0; color: #6b7280; font-size: 12px; }.login-pagination-controls { display: flex; align-items: center; gap: 4px; }.login-pagination-controls label { display: inline-flex; align-items: center; gap: 6px; margin-right: 7px; color: #6b7280; font-size: 11px; }.login-pagination-controls select { height: 28px; padding: 0 6px; font-size: 11px; }.login-pagination-controls > button { display: grid; width: 28px; height: 28px; place-items: center; padding: 0; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; color: #4b5563; font-size: 11px; cursor: pointer; }.login-pagination-controls > button:hover:not(:disabled) { border-color: #bfdbfe; background: #eff6ff; color: #2563eb; }.login-pagination-controls > button.active { border-color: #2563eb; background: #2563eb; color: #fff; }.login-pagination-controls > button:disabled { cursor: not-allowed; opacity: .45; }

.login-drawer-backdrop,.login-confirm-backdrop { position: fixed; z-index: 90; inset: 0; background: rgba(17, 24, 39, .34); }.login-drawer-backdrop { display: flex; justify-content: flex-end; }.login-detail-drawer { width: min(520px, 100vw); height: 100%; overflow-y: auto; background: #fff; box-shadow: -18px 0 42px rgba(17, 24, 39, .18); }.login-detail-header { display: flex; align-items: flex-start; justify-content: space-between; padding: 20px 22px 16px; border-bottom: 1px solid #e5e7eb; }.login-detail-header span { color: #6b7280; font-size: 11px; font-weight: 700; letter-spacing: .06em; }.login-detail-header h3 { margin: 4px 0 0; color: #111827; font-size: 18px; }.login-detail-body { display: grid; gap: 18px; padding: 20px 22px 24px; }.login-detail-person { display: flex; align-items: center; gap: 10px; padding-bottom: 18px; border-bottom: 1px solid #f3f4f6; }.login-detail-person > span:first-child { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 50%; background: #eff6ff; color: #2563eb; font-size: 15px; font-weight: 750; }.login-detail-person > div { display: grid; flex: 1; gap: 3px; }.login-detail-person strong { color: #111827; font-size: 14px; }.login-detail-person small { color: #6b7280; font-size: 11px; }.login-detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 18px; }.login-detail-grid > div { display: grid; gap: 5px; min-width: 0; }.login-detail-grid > div.is-wide { grid-column: 1 / -1; }.login-detail-grid span { color: #9ca3af; font-size: 11px; }.login-detail-grid strong { overflow-wrap: anywhere; color: #374151; font-size: 12px; font-weight: 650; }.login-detail-grid code { overflow-wrap: anywhere; color: #4b5563; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; line-height: 1.55; }.login-risk-explanation { display: flex; gap: 10px; padding: 13px; border: 1px solid #fed7aa; border-radius: 8px; background: #fff7ed; color: #c2410c; }.login-risk-explanation svg { flex: 0 0 auto; margin-top: 2px; }.login-risk-explanation strong { font-size: 12px; }.login-risk-explanation p { margin: 4px 0 0; font-size: 12px; line-height: 1.55; }.login-detail-danger-zone { display: grid; gap: 12px; padding-top: 17px; border-top: 1px solid #fee2e2; }.login-detail-danger-zone > div:first-child { display: grid; gap: 4px; }.login-detail-danger-zone strong { color: #991b1b; font-size: 13px; }.login-detail-danger-zone p { margin: 0; color: #9ca3af; font-size: 11px; }.login-detail-danger-zone > div:last-child { display: flex; gap: 8px; }.login-detail-danger-zone button { min-height: 32px; padding: 0 10px; font-size: 11px; }.login-danger-outline { display: inline-flex; align-items: center; justify-content: center; gap: 6px; border: 1px solid #fecaca; border-radius: 7px; background: #fff; color: #b91c1c; font-weight: 700; cursor: pointer; }.login-danger-outline:disabled,.login-detail-danger-zone .admin-danger-button:disabled { cursor: default; opacity: .5; }
.login-confirm-backdrop { display: grid; place-items: center; padding: 20px; }.login-confirm-card { width: min(390px, 100%); padding: 22px; border-radius: 10px; background: #fff; box-shadow: 0 18px 48px rgba(17, 24, 39, .22); }.login-confirm-card > span { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 50%; background: #fef2f2; color: #dc2626; }.login-confirm-card h3 { margin: 14px 0 8px; color: #111827; font-size: 16px; }.login-confirm-card p { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.55; }.login-confirm-card footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }.login-confirm-card footer button { min-height: 33px; padding: 0 12px; font-size: 12px; }
.login-records-toast { position: fixed; right: 22px; bottom: 22px; z-index: 110; margin: 0; padding: 10px 13px; border-radius: 7px; background: #111827; color: #fff; font-size: 12px; box-shadow: 0 10px 26px rgba(17, 24, 39, .2); }
.login-drawer-enter-active,.login-drawer-leave-active { transition: opacity .18s ease; }.login-drawer-enter-active .login-detail-drawer,.login-drawer-leave-active .login-detail-drawer { transition: transform .22s ease; }.login-drawer-enter-from,.login-drawer-leave-to { opacity: 0; }.login-drawer-enter-from .login-detail-drawer,.login-drawer-leave-to .login-detail-drawer { transform: translateX(100%); }.login-confirm-enter-active,.login-confirm-leave-active,.login-toast-enter-active,.login-toast-leave-active { transition: opacity .16s ease, transform .16s ease; }.login-confirm-enter-from,.login-confirm-leave-to,.login-toast-enter-from,.login-toast-leave-to { opacity: 0; transform: translateY(6px); }
@keyframes login-spin { to { transform: rotate(360deg); } } @keyframes login-skeleton { to { background-position: -200% 0; } }

@media (max-width: 1180px) { .login-filter-main-row { grid-template-columns: minmax(230px, 1.4fr) repeat(2, minmax(130px, 1fr)); }.login-record-search { grid-row: span 2; } }
@media (max-width: 820px) { .admin-login-records-page { padding: 22px 16px 30px; }.admin-login-records-intro { gap: 12px; }.login-stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.login-filter-main-row { grid-template-columns: 1fr 1fr; }.login-record-search { grid-column: 1 / -1; grid-row: auto; }.login-filter-actions-row { align-items: flex-start; flex-direction: column; }.login-filter-actions { align-self: stretch; }.login-filter-actions button { flex: 1; }.login-records-pagination { align-items: flex-start; flex-direction: column; }.login-pagination-controls { flex-wrap: wrap; } }
@media (max-width: 560px) { .admin-login-records-intro { flex-direction: column; }.login-records-refresh { width: 100%; }.login-stat-grid { gap: 10px; }.login-stat-card { min-height: 90px; padding: 14px; }.login-stat-card strong { font-size: 21px; }.login-filter-main-row { grid-template-columns: 1fr; }.login-record-search { grid-column: auto; }.login-time-filter { align-items: flex-start; flex-direction: column; }.login-time-options { width: 100%; overflow-x: auto; }.login-time-options button { flex: 0 0 auto; }.login-custom-dates { width: 100%; }.login-custom-dates input { min-width: 0; flex: 1; }.login-detail-grid { grid-template-columns: 1fr; }.login-detail-grid > div.is-wide { grid-column: auto; }.login-detail-drawer { width: 100vw; }.login-detail-danger-zone > div:last-child { flex-direction: column; }.login-detail-danger-zone button { width: 100%; } }

:global(html[data-theme='dark']) .login-stat-card,:global(html[data-theme='dark']) .login-records-filter-panel,:global(html[data-theme='dark']) .login-records-table-card,:global(html[data-theme='dark']) .login-detail-drawer,:global(html[data-theme='dark']) .login-confirm-card { border-color: #3f3f46; background: #18181b; }
:global(html[data-theme='dark']) .login-stat-card strong,:global(html[data-theme='dark']) .login-user-cell strong,:global(html[data-theme='dark']) .login-detail-header h3,:global(html[data-theme='dark']) .login-detail-person strong { color: #f4f4f5; }
:global(html[data-theme='dark']) .login-stat-card > span,:global(html[data-theme='dark']) .login-stat-card small,:global(html[data-theme='dark']) .login-records-pagination p,:global(html[data-theme='dark']) .login-detail-person small,:global(html[data-theme='dark']) .login-detail-grid span { color: #a1a1aa; }
:global(html[data-theme='dark']) .login-record-search,:global(html[data-theme='dark']) .login-filter-field select,:global(html[data-theme='dark']) .login-custom-dates input,:global(html[data-theme='dark']) .login-pagination-controls select,:global(html[data-theme='dark']) .login-pagination-controls > button { border-color: #3f3f46; background: #111113; color: #e4e4e7; }
:global(html[data-theme='dark']) .login-record-search input,:global(html[data-theme='dark']) .login-records-table td,:global(html[data-theme='dark']) .login-detail-grid strong,:global(html[data-theme='dark']) .login-detail-grid code { color: #d4d4d8; }
:global(html[data-theme='dark']) .login-records-table th { border-color: #3f3f46; background: #27272a; color: #a1a1aa; }:global(html[data-theme='dark']) .login-records-table td,:global(html[data-theme='dark']) .login-records-pagination,:global(html[data-theme='dark']) .login-detail-header,:global(html[data-theme='dark']) .login-detail-person { border-color: #27272a; }:global(html[data-theme='dark']) .login-records-table tbody tr:not(.login-skeleton-row):hover { background: #27272a; }:global(html[data-theme='dark']) .login-audit-notice { border-color: rgba(59, 130, 246, .35); background: rgba(30, 64, 175, .2); color: #93c5fd; }
</style>

<style scoped>
.admin-login-records-page { display: flex; flex-direction: column; height: 100%; min-height: 0; overflow: hidden; box-sizing: border-box; }
.admin-login-records-intro { flex: 0 0 auto; min-height: 58px; margin-bottom: 10px; }
.admin-login-records-intro p { margin-bottom: 2px; }.admin-login-records-intro h2 { font-size: 23px; line-height: 1.2; }.admin-login-records-intro span { font-size: 12px; }.login-records-refresh { min-height: 34px; padding: 0 11px; }
.login-stat-grid { flex: 0 0 auto; gap: 10px; margin-bottom: 10px; }.login-stat-card { min-height: 74px; padding: 11px 14px; border-radius: 8px; }.login-stat-card > span { font-size: 11px; }.login-stat-card strong { margin-top: 3px; font-size: 22px; line-height: 1; }.login-stat-card small { margin-top: 3px; font-size: 10px; }
.login-records-filter-panel { flex: 0 0 auto; padding: 10px 12px; border-radius: 8px; }.login-filter-main-row { gap: 10px; }.login-record-search { min-height: 34px; }.login-filter-field { gap: 3px; }.login-filter-field > span,.login-time-filter > span { font-size: 10px; }.login-filter-field select { height: 31px; font-size: 11px; }.login-filter-actions-row { min-height: 33px; margin-top: 9px; padding-top: 9px; }.login-time-options button { min-height: 25px; }.login-filter-actions button { min-height: 31px; }
.login-audit-notice { flex: 0 0 auto; min-height: 30px; margin: 8px 0; padding: 6px 10px; font-size: 11px; }
.login-records-table-card { display: flex; flex: 1 1 auto; flex-direction: column; min-height: 0; overflow: hidden; border-radius: 8px; }.login-records-table-wrap { flex: 1 1 auto; min-height: 0; overflow-x: auto; overflow-y: hidden; }.login-records-table { min-width: 1360px; }.login-records-table th { height: 38px; padding: 7px 11px; font-size: 10px; }.login-records-table td { height: 48px; padding: 5px 11px; font-size: 11px; }.login-user-cell { gap: 7px; }.login-user-cell > span { width: 22px; height: 22px; font-size: 10px; }.login-user-cell strong { font-size: 11px; }.login-device-cell { gap: 2px; }.login-device-cell strong { font-size: 10px; }.login-device-cell small { font-size: 9px; }.login-account-tag,.login-status-tag,.login-risk-tag { min-height: 19px; padding: 0 6px; font-size: 9px; }.login-detail-button { min-height: 24px; font-size: 10px; }.login-records-pagination { flex: 0 0 auto; min-height: 42px; padding: 7px 12px; }.login-records-pagination p { font-size: 11px; }.login-pagination-controls > button { width: 26px; height: 26px; }.login-pagination-controls select { height: 26px; }
</style>
