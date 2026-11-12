<script setup>
import { Activity, Bot, ChartBar, ChartNoAxesCombined, CircleCheck, CircleDollarSign, Database, Gauge, Network, Save, Search, Settings2, ShieldCheck, Timer, TrendingUp, Users, UsersRound, X } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getBillingOverview, listBillingQuotas, listBillingTiers, saveBillingOverview, saveBillingQuota, saveBillingTier } from '../../api/admin'

const props = defineProps({
  initialTab: {
    type: String,
    default: 'overview'
  }
})

const permissionGroups = [
  { title: '拓扑绘制', items: [{ key: 'manualDrawing', label: '手动绘制网络拓扑' }, { key: 'topologyEditing', label: '拓扑图编辑' }] },
  { title: 'AI能力', items: [{ key: 'aiRequirementAnalysis', label: 'AI需求分析' }, { key: 'aiTopologyGeneration', label: 'AI自动生成拓扑' }, { key: 'aiTopologyQa', label: 'AI拓扑问答' }] },
  { title: '网络工程分析', items: [{ key: 'networkPlanGeneration', label: '网络方案生成' }, { key: 'riskOptimization', label: '风险与优化分析' }, { key: 'engineeringDocumentGeneration', label: '工程文档生成' }] },
  { title: '联网与知识库', items: [{ key: 'onlineSearch', label: '联网搜索' }, { key: 'ragKnowledgeBase', label: 'RAG知识库增强' }] },
  { title: '成果导出', items: [{ key: 'pngExport', label: 'PNG导出' }, { key: 'svgExport', label: 'SVG导出' }, { key: 'wordExport', label: 'Word导出' }, { key: 'pdfExport', label: 'PDF导出' }] }
]

const overviewRange = ref('本月')
const overviewRanges = ['近7天', '本月', '近90天']
const overviewData = ref({})
const monthlyCost = ref(0)
const overviewGeneratedAt = ref(null)
const overviewLoading = ref(false)
const overviewError = ref('')
const overviewLoaded = ref(false)
const users = ref([])
const availableModels = ref([])
const tierConfigs = ref([])
const activeBillingTab = ref(props.initialTab)
const query = ref('')
const toast = ref('')
const selectedUser = ref(null)
const quotaDrawerOpen = ref(false)
const quotaDraft = ref(createQuotaDraft())
const tierDrawerOpen = ref(false)
const permissionDrawerOpen = ref(false)
const selectedTierId = ref(null)
const tierDraft = ref(createLevelDraft())
const permissionDraft = ref({})
let toastTimer = 0

const currentOverview = computed(() => overviewData.value ?? {})
const overviewStats = computed(() => currentOverview.value.stats ?? [])
const usageTrend = computed(() => currentOverview.value.trend ?? [])
const featureUsage = computed(() => currentOverview.value.features ?? [])
const tierUsage = computed(() => currentOverview.value.tiers ?? [])
const currentSummary = computed(() => currentOverview.value.summary ?? {})
const activeTrendPoint = ref(null)

const filteredUsers = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return users.value
  return users.value.filter((user) => [user.id, user.name, user.tier, levelName(user.tier)].some((value) => String(value).toLowerCase().includes(keyword)))
})

const levelStats = computed(() => ({
  total: tierConfigs.value.length,
  free: users.value.filter((user) => canonicalLevelCode(user.tier) === 'FREE').length,
  standard: users.value.filter((user) => canonicalLevelCode(user.tier) === 'STANDARD').length,
  pro: users.value.filter((user) => canonicalLevelCode(user.tier) === 'PRO').length
}))

const trendChartFrame = { width: 680, height: 238, left: 42, right: 16, top: 16, bottom: 34 }
const trendPlotWidth = trendChartFrame.width - trendChartFrame.left - trendChartFrame.right
const trendPlotHeight = trendChartFrame.height - trendChartFrame.top - trendChartFrame.bottom
const selectedUsageTrend = computed(() => usageTrend.value ?? [])
const trendDataMax = computed(() => Math.max(1, ...selectedUsageTrend.value.map((item) => Math.max(Number(item.aiCalls) || 0, Number(item.topology) || 0))))
// 保留旧版测试和外部引用所需的拓扑趋势最大值，图表刻度仍由共享 Y 轴动态计算。
const usageTopologyMax = computed(() => Math.max(1, ...selectedUsageTrend.value.map((item) => Number(item.topology) || 0)))
const featureCallTotal = computed(() => featureUsage.value.reduce((total, item) => total + (Number(item.calls) || 0), 0))
const usageYAxisTicks = computed(() => {
  const step = Math.max(1, Math.ceil(trendDataMax.value / 4))
  return [step * 4, step * 3, step * 2, step, 0]
})
const usageChartMax = computed(() => usageYAxisTicks.value[0] ?? 1)
const hasTrendData = computed(() => selectedUsageTrend.value.some((item) => Number(item.aiCalls) > 0 || Number(item.topology) > 0))
const hasTopologyTrend = computed(() => selectedUsageTrend.value.some((item) => Number(item.topology) > 0))
const trendCallTotal = computed(() => selectedUsageTrend.value.reduce((total, item) => total + (Number(item.aiCalls) || 0), 0))
const trendTopologyTotal = computed(() => selectedUsageTrend.value.reduce((total, item) => total + (Number(item.topology) || 0), 0))
const trendBarWidth = computed(() => Math.max(4, Math.min(18, trendPlotWidth / Math.max(selectedUsageTrend.value.length, 1) * .58)))
const trendHitWidth = computed(() => Math.max(12, trendPlotWidth / Math.max(selectedUsageTrend.value.length, 1)))
const trendSvgPoints = computed(() => selectedUsageTrend.value.map((item, index, items) => {
  const x = trendPointX(index, items.length)
  const aiCalls = Number(item.aiCalls) || 0
  const topology = Number(item.topology) || 0
  return {
    ...item,
    index,
    x,
    aiCalls,
    topology,
    aiY: trendPointY(aiCalls),
    topologyY: trendPointY(topology),
    aiHeight: Math.max(0, trendChartFrame.top + trendPlotHeight - trendPointY(aiCalls))
  }
}))
const trendTopologyLine = computed(() => trendSvgPoints.value.map((point) => `${point.x},${point.topologyY}`).join(' '))
const trendXAxisLabels = computed(() => {
  const points = trendSvgPoints.value
  if (points.length <= 6) return points
  const step = Math.ceil((points.length - 1) / 5)
  return points.filter((point) => point.index === 0 || point.index === points.length - 1 || point.index % step === 0)
})

const statIcons = { aiCalls: Activity, tokenUsage: Database, topologyGenerated: Network, activeUsers: Users, totalCost: CircleDollarSign, avgResponseTimeMs: Timer, successRate: CircleCheck, peakConcurrency: ChartNoAxesCombined }
const rangeKeys = { '近7天': '7d', '本月': 'month', '近90天': '90d' }

function statIcon(key) { return statIcons[key] ?? Activity }
function formatAxisValue(value) { return value >= 1000 ? `${Math.round(value / 1000)}k` : String(value) }
function rangeKey() { return rangeKeys[overviewRange.value] ?? 'month' }
function trendPointX(index, total) { return total <= 1 ? trendChartFrame.left + trendPlotWidth / 2 : trendChartFrame.left + (index / (total - 1)) * trendPlotWidth }
function trendPointY(value) { return trendChartFrame.top + trendPlotHeight - Math.min(1, Math.max(0, (Number(value) || 0) / usageChartMax.value)) * trendPlotHeight }
function activateTrendPoint(point) { activeTrendPoint.value = point }
function clearTrendPoint() { activeTrendPoint.value = null }
function applyOverview(data) {
  const selected = data?.rangeData ?? data?.selected ?? data?.ranges?.[overviewRange.value] ?? {}
  overviewData.value = selected
  activeTrendPoint.value = null
  monthlyCost.value = Number(data?.monthlyCost ?? selected?.overview?.totalCost ?? 0)
  overviewGeneratedAt.value = data?.updatedAt ?? data?.generatedAt ?? selected?.summary?.updatedAt ?? null
  overviewLoaded.value = true
}

function cloneLevel(level) {
  return { ...level, permissions: { ...(level?.permissions ?? {}) } }
}

function createLevelDraft() {
  return cloneLevel({
    id: '', name: '', code: '', description: '', enabled: true,
    aiCallQuota: 0, topologyQuota: 0, knowledgeQaQuota: 0, networkPlanQuota: 0, tokenQuota: 0,
    dailyCallLimit: 0, projectLimit: 0, knowledgeCapacityMb: 0, fileUploadLimit: 0, singleFileSizeMb: 0,
    updatedAt: '', permissions: {}
  })
}

function createQuotaDraft(user = {}) {
  return { rpm: user.rpm ?? 0, tpm: user.tpm ?? 0, customDailyCalls: user.customDailyCalls ?? 0, allowedModels: [...(user.allowedModels ?? [])] }
}

function canonicalLevelCode(value) {
  const identity = String(value ?? '').trim().toUpperCase()
  if (['FREE', 'NORMAL', 'BASIC'].includes(identity)) return 'FREE'
  if (['STANDARD', 'TEAM', 'EDUCATION'].includes(identity)) return 'STANDARD'
  if (['PRO', 'PREMIUM', 'ADVANCED', 'ENTERPRISE'].includes(identity)) return 'PRO'
  return 'FREE'
}

function normalizeTierConfig(stored) {
  const source = stored ?? {}
  const code = canonicalLevelCode(source.code ?? source.name ?? source.key ?? source.id)
  return {
    ...source, id: source.key ?? source.id ?? code.toLowerCase(), code,
    name: source.backendName || source.name || code,
    userName: source.userName || source.name || code,
    knowledgeQaQuota: Number(source.knowledgeQaQuota ?? source.aiQaQuota ?? 0),
    networkPlanQuota: Number(source.networkPlanQuota ?? source.documentQuota ?? 0),
    permissions: { ...(source.permissions ?? {}) }
  }
}

function tierByCode(code) {
  const normalizedCode = canonicalLevelCode(code)
  return tierConfigs.value.find((tier) => tier.code === normalizedCode) ?? tierConfigs.value[0]
}

function levelName(code) {
  return tierByCode(code)?.name ?? String(code)
}

function formatCount(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatQuota(value, support = true) {
  return support && Number(value) > 0 ? `${formatCount(value)}次/月` : '不支持'
}

function tierUserCount(code) {
  return users.value.filter((user) => canonicalLevelCode(user.tier) === canonicalLevelCode(code)).length
}

function featureColor(index) {
  return ['blue', 'violet', 'green', 'orange', 'slate'][index % 5]
}

function formatSummaryRate(value) {
  return value == null ? '--' : `${Number(value).toFixed(1)}%`
}

function formatLatency(value) {
  if (value == null) return '--'
  const milliseconds = Number(value)
  return milliseconds < 1000 ? `${Math.round(milliseconds)}ms` : `${(milliseconds / 1000).toFixed(2)}s`
}

function formatDate(value) {
  if (!value) return '无数据'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

function permissionCount(tier) {
  return Object.values(tier.permissions ?? {}).filter(Boolean).length
}

function progressValue(user) {
  if (!user.tokenLimit) return user.usedTokens > 0 ? 100 : 0
  return Math.min(100, Math.round((user.usedTokens / user.tokenLimit) * 100))
}

function progressTone(user) {
  const progress = progressValue(user)
  if (progress >= 95) return 'critical'
  if (progress >= 80) return 'warning'
  return 'normal'
}

function quotaPayload(user) {
  return {
    tier: storageTier(user.tier),
    rpm: Math.max(1, Number(user.rpm) || 1),
    tpm: Math.max(1, Number(user.tpm) || 1),
    customDailyCalls: Math.max(0, Number(user.customDailyCalls) || 0),
    allowedModels: [...(user.allowedModels ?? [])]
  }
}

function storageTier(value) {
  return canonicalLevelCode(value).toLowerCase()
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

function openTierDrawer(tier = null) {
  if (!tier) {
    showToast('系统仅支持编辑现有的 FREE、STANDARD、PRO 套餐')
    return
  }
  selectedTierId.value = tier.id
  tierDraft.value = cloneLevel(tier)
  tierDrawerOpen.value = true
}

function closeTierDrawer() {
  tierDrawerOpen.value = false
  selectedTierId.value = null
}

async function persistTier(tier) {
  const saved = await saveBillingTier(tier.id, tier)
  return normalizeTierConfig({ ...tier, ...saved, id: saved.key ?? tier.id })
}

async function saveTier() {
  const code = String(tierDraft.value.code ?? '').trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_')
  const name = String(tierDraft.value.name ?? '').trim()
  if (!code || !name) {
    showToast('请填写等级名称和等级标识')
    return
  }
  if (!['FREE', 'STANDARD', 'PRO'].includes(code)) {
    showToast('系统只保留 FREE、STANDARD、PRO 三种用户等级')
    return
  }

  const record = {
    ...cloneLevel(tierDraft.value), id: code.toLowerCase(), name, code,
    userCount: Math.max(0, Number(tierDraft.value.userCount) || 0),
    aiCallQuota: Math.max(0, Number(tierDraft.value.aiCallQuota) || 0),
    topologyQuota: Math.max(0, Number(tierDraft.value.topologyQuota) || 0),
    knowledgeQaQuota: Math.max(0, Number(tierDraft.value.knowledgeQaQuota) || 0),
    networkPlanQuota: Math.max(0, Number(tierDraft.value.networkPlanQuota) || 0),
    tokenQuota: Math.max(0, Number(tierDraft.value.tokenQuota) || 0),
    dailyCallLimit: Math.max(0, Number(tierDraft.value.dailyCallLimit) || 0),
    projectLimit: Math.max(0, Number(tierDraft.value.projectLimit) || 0),
    knowledgeCapacityMb: Math.max(0, Number(tierDraft.value.knowledgeCapacityMb) || 0),
    fileUploadLimit: Math.max(0, Number(tierDraft.value.fileUploadLimit) || 0),
    singleFileSizeMb: Math.max(0, Number(tierDraft.value.singleFileSizeMb) || 0),
    updatedAt: new Date().toLocaleString('zh-CN', { hour12: false })
  }

  try {
    const saved = await persistTier(record)
    const index = tierConfigs.value.findIndex((tier) => tier.code === record.code)
    if (index === -1) tierConfigs.value.push(saved)
    else tierConfigs.value.splice(index, 1, saved)
    await refreshQuotaRows()
    closeTierDrawer()
    showToast('用户等级配置已更新')
  } catch (error) {
    showToast(error.message || '用户等级保存失败，请稍后重试')
  }
}

function openPermissionDrawer(tier) {
  selectedTierId.value = tier.id
  permissionDraft.value = { ...tier.permissions }
  permissionDrawerOpen.value = true
}

function closePermissionDrawer() {
  permissionDrawerOpen.value = false
  selectedTierId.value = null
}

async function savePermissions() {
  const tier = tierConfigs.value.find((item) => item.id === selectedTierId.value)
  if (!tier) return
  const record = { ...cloneLevel(tier), permissions: { ...permissionDraft.value }, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
  try {
    const saved = await persistTier(record)
    const index = tierConfigs.value.findIndex((item) => item.id === tier.id)
    tierConfigs.value.splice(index, 1, saved)
    closePermissionDrawer()
    showToast('用户等级配置已更新')
  } catch (error) {
    showToast(error.message || '权限配置保存失败，请稍后重试')
  }
}

async function changeUserTier(user) {
  const previousTier = canonicalLevelCode(user.tier)
  user.tier = canonicalLevelCode(user.tier)
  try {
    const saved = await saveBillingQuota(user.id, quotaPayload(user))
    Object.assign(user, normalizeQuota(saved))
    showToast(`${user.name} 已切换至 ${levelName(user.tier)}`)
  } catch (error) {
    await refreshQuotaRows().catch(() => { user.tier = previousTier })
    showToast(error.message || '用户等级调整失败，请稍后重试')
  }
}

function openQuotaDrawer(user) {
  selectedUser.value = user
  quotaDraft.value = createQuotaDraft(user)
  quotaDrawerOpen.value = true
}

function closeQuotaDrawer() {
  quotaDrawerOpen.value = false
  selectedUser.value = null
}

function toggleModelAccess(modelId) {
  const next = quotaDraft.value.allowedModels
  quotaDraft.value.allowedModels = next.includes(modelId) ? next.filter((id) => id !== modelId) : [...next, modelId]
}

async function saveQuotaConfig() {
  if (!selectedUser.value) return
  selectedUser.value.rpm = Math.max(1, Number(quotaDraft.value.rpm) || 1)
  selectedUser.value.tpm = Math.max(1, Number(quotaDraft.value.tpm) || 1)
  selectedUser.value.customDailyCalls = Math.max(0, Number(quotaDraft.value.customDailyCalls) || 0)
  selectedUser.value.allowedModels = [...quotaDraft.value.allowedModels]
  try {
    const saved = await saveBillingQuota(selectedUser.value.id, quotaPayload(selectedUser.value))
    Object.assign(selectedUser.value, normalizeQuota(saved))
    closeQuotaDrawer()
    showToast('用户实际配额已更新')
  } catch (error) {
    showToast(error.message || '用户配额保存失败，请稍后重试')
  }
}

function normalizeStoredTiers(storedTiers) {
  return (Array.isArray(storedTiers) ? storedTiers : []).map(normalizeTierConfig)
}

async function refreshQuotaRows() {
  const storedQuotas = await listBillingQuotas()
  users.value = (Array.isArray(storedQuotas) ? storedQuotas : []).map(normalizeQuota)
}

function normalizeQuota(user) {
  const monthlyTokenLimit = Number(user?.monthlyTokenLimit ?? 0)
  return {
    ...user,
    id: user?.userId ?? user?.id,
    name: user?.displayName || user?.username || String(user?.userId ?? user?.id ?? ''),
    tier: canonicalLevelCode(user?.tier),
    usedTokens: Number(user?.totalUsedTokens ?? 0) / 1000000,
    tokenLimit: monthlyTokenLimit / 1000000,
    aiChatUsed: Number(user?.aiChatUsed ?? 0),
    aiChatTotal: Number(user?.aiChatTotal ?? 0),
    apiRemaining: Number(user?.aiChatRemaining ?? 0),
    topologyUsed: Number(user?.topologyUsed ?? 0),
    topologyTotal: Number(user?.topologyTotal ?? 0),
    knowledgeQaUsed: Number(user?.subscription?.usage?.knowledgeQa?.used ?? 0),
    knowledgeQaTotal: Number(user?.subscription?.quota?.knowledgeQa?.total ?? 0),
    rpm: Number(user?.rpm ?? 0),
    tpm: Number(user?.tpm ?? 0),
    customDailyCalls: Number(user?.customDailyCalls ?? 0),
    allowedModels: (Array.isArray(user?.allowedModels) ? user.allowedModels : String(user?.allowedModels ?? '').split(',').filter(Boolean)).map(String)
  }
}

async function loadBillingConfiguration() {
  overviewLoading.value = true
  overviewError.value = ''
  try {
    const [overview, storedTiers, storedQuotas] = await Promise.all([
      getBillingOverview({ range: rangeKey() }),
      listBillingTiers(),
      listBillingQuotas()
    ])
    applyOverview(overview)
    tierConfigs.value = normalizeStoredTiers(storedTiers)
    users.value = (Array.isArray(storedQuotas) ? storedQuotas : []).map(normalizeQuota)
    availableModels.value = (Array.isArray(overview?.models) ? overview.models : []).map((model) => ({
      id: String(model.id ?? model.key ?? ''),
      name: model.displayName || model.alias || model.modelId || String(model.id ?? ''),
      provider: model.provider || model.providerName || '',
      description: model.description || model.modelType || ''
    })).filter((model) => model.id)
  } catch (error) {
    overviewError.value = error.message || '统计数据加载失败'
    overviewLoaded.value = false
    showToast(overviewError.value)
  } finally {
    overviewLoading.value = false
  }
}

onMounted(loadBillingConfiguration)
watch(overviewRange, () => {
  if (activeBillingTab.value === 'overview') void loadBillingConfiguration()
})
onBeforeUnmount(() => window.clearTimeout(toastTimer))
</script>

<template>
  <section class="admin-section-page admin-billing-page">
    <section v-if="activeBillingTab === 'tiers'" class="user-level-page">
      <header class="user-level-intro">
        <div>
          <nav class="user-level-breadcrumb" aria-label="面包屑">资源与配额 <span>/</span> 用户等级</nav>
          <h2>用户等级</h2>
          <p>配置不同用户等级的基础功能权限与默认资源额度。</p>
        </div>
      </header>

      <section class="user-level-stats" aria-label="用户等级统计">
        <article><span>用户等级</span><strong>{{ levelStats.total }}</strong></article>
        <article><span>免费用户</span><strong>{{ formatCount(levelStats.free) }}</strong></article>
        <article><span>普通用户</span><strong>{{ formatCount(levelStats.standard) }}</strong></article>
        <article><span>高级用户</span><strong>{{ formatCount(levelStats.pro) }}</strong></article>
      </section>

      <section class="user-level-table-card">
        <div class="user-level-table-wrap">
          <table class="user-level-table">
            <thead><tr><th>等级名称</th><th>等级标识</th><th>用户数量</th><th>等级状态</th><th>基础AI额度</th><th>拓扑生成额度</th><th>功能权限</th><th>更新时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="tier in tierConfigs" :key="tier.id">
                <td><div class="user-level-name"><strong>{{ tier.name }}</strong><small>{{ tier.description }}</small></div></td>
                <td><code>{{ tier.code }}</code></td>
                <td><strong>{{ formatCount(tierUserCount(tier.code)) }}</strong></td>
                <td><span class="user-level-status" :class="{ disabled: !tier.enabled }"><i></i>{{ tier.enabled ? '启用' : '停用' }}</span></td>
                <td><strong>{{ formatQuota(tier.aiCallQuota) }}</strong></td>
                <td><strong>{{ formatQuota(tier.topologyQuota) }}</strong></td>
                <td><span class="user-level-permission-count">{{ permissionCount(tier) }} 项</span></td>
                <td><time>{{ tier.updatedAt }}</time></td>
                <td><div class="user-level-actions"><button type="button" @click="openTierDrawer(tier)">编辑</button><button type="button" @click="openPermissionDrawer(tier)">权限配置</button></div></td>
              </tr>
              <tr v-if="!tierConfigs.length"><td colspan="9" class="admin-billing-empty">数据库中暂无用户等级配置</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="user-level-reference" aria-label="等级默认能力概览">
        <article v-for="tier in tierConfigs.slice(0, 3)" :key="`${tier.id}-reference`">
          <header><strong>{{ tier.name }}</strong><span>{{ tier.code }}</span></header>
          <dl>
            <div><dt>手动绘图</dt><dd :class="{ unsupported: !tier.permissions.manualDrawing }">{{ tier.permissions.manualDrawing ? '支持' : '不支持' }}</dd></div>
            <div><dt>AI拓扑生成</dt><dd>{{ formatQuota(tier.topologyQuota) }}</dd></div>
            <div><dt>知识库AI问答</dt><dd>{{ formatQuota(tier.knowledgeQaQuota) }}</dd></div>
            <div><dt>AI方案生成</dt><dd>{{ formatQuota(tier.networkPlanQuota) }}</dd></div>
            <div><dt>成果导出</dt><dd>{{ tier.permissions.wordExport ? 'Word' : '' }}{{ tier.permissions.wordExport && tier.permissions.pdfExport ? ' / ' : '' }}{{ tier.permissions.pdfExport ? 'PDF' : '' }}{{ !tier.permissions.wordExport && !tier.permissions.pdfExport ? '不支持' : '' }}</dd></div>
          </dl>
        </article>
      </section>
    </section>

    <section v-else-if="activeBillingTab === 'overview'" class="resource-statistics-page">
      <header class="resource-statistics-intro">
        <div><p>资源与配额 / 使用统计</p><h2>使用统计</h2><span>查看平台聚合的 AI 调用、Token 消耗与拓扑生成情况。</span></div>
        <div class="resource-statistics-actions"><span class="resource-sync-status"><i></i> {{ overviewGeneratedAt ? `更新于 ${formatDate(overviewGeneratedAt)}` : '等待数据库数据' }}</span><div class="resource-range-tabs" role="tablist" aria-label="数据范围"><button v-for="range in overviewRanges" :key="range" type="button" role="tab" :aria-selected="overviewRange === range" :class="{ active: overviewRange === range }" @click="overviewRange = range">{{ range }}</button></div></div>
      </header>

      <section class="resource-statistics-grid" aria-label="核心指标" :aria-busy="overviewLoading">
        <template v-if="overviewLoading">
          <article v-for="index in 8" :key="`stat-skeleton-${index}`" class="resource-stat-skeleton"><span></span><strong></strong><small></small></article>
        </template>
        <div v-else-if="overviewError" class="resource-statistics-state"><strong>统计数据加载失败</strong><span>{{ overviewError }}</span><button type="button" @click="loadBillingConfiguration">重新加载</button></div>
        <template v-else>
          <article v-for="item in overviewStats" :key="item.key || item.label" :class="`is-${item.tone}`"><div class="resource-stat-card-head"><span>{{ item.label }}</span><component :is="statIcon(item.key)" :size="18" /></div><strong>{{ item.value }}</strong><div class="resource-stat-card-foot"><small>{{ item.hint }}</small></div></article>
        </template>
      </section>

      <section class="resource-statistics-main-grid">
        <article class="resource-usage-panel resource-trend-panel">
          <header class="resource-panel-header resource-trend-header"><div><h3>AI调用趋势</h3><p>展示所选时间范围内每日 AI 调用量与拓扑生成趋势</p></div><TrendingUp :size="18" aria-hidden="true" /></header>
          <div class="resource-trend-legend"><span><i class="is-blue"></i> AI调用次数</span><span><i class="is-green"></i> 拓扑生成</span></div>
          <div v-if="overviewLoading" class="resource-chart-loading">正在加载真实统计数据…</div><div v-else-if="overviewError" class="resource-chart-loading">统计数据加载失败</div><div v-else-if="!hasTrendData" class="resource-chart-empty">当前统计周期暂无使用数据</div>
          <div v-else class="resource-trend-chart">
            <svg class="resource-trend-svg" :viewBox="`0 0 ${trendChartFrame.width} ${trendChartFrame.height}`" role="img" :aria-label="`${overviewRange} AI调用趋势图`">
              <g v-for="tick in usageYAxisTicks" :key="tick" class="resource-trend-grid-line"><line :x1="trendChartFrame.left" :x2="trendChartFrame.width - trendChartFrame.right" :y1="trendPointY(tick)" :y2="trendPointY(tick)" /><text :x="trendChartFrame.left - 10" :y="trendPointY(tick) + 4">{{ formatAxisValue(tick) }}</text></g>
              <line class="resource-trend-axis" :x1="trendChartFrame.left" :x2="trendChartFrame.width - trendChartFrame.right" :y1="trendChartFrame.top + trendPlotHeight" :y2="trendChartFrame.top + trendPlotHeight" />
              <g v-for="point in trendXAxisLabels" :key="`label-${point.date || point.index}`" class="resource-trend-x-label"><line :x1="point.x" :x2="point.x" :y1="trendChartFrame.top + trendPlotHeight" :y2="trendChartFrame.top + trendPlotHeight + 4" /><text :x="point.x" :y="trendChartFrame.height - 8" :text-anchor="point.index === 0 ? 'start' : point.index === trendSvgPoints.length - 1 ? 'end' : 'middle'">{{ point.label }}</text></g>
              <g v-for="point in trendSvgPoints" :key="point.date || point.index" class="resource-trend-series" :class="{ 'is-active': activeTrendPoint?.index === point.index }"><rect class="resource-trend-call-bar" :x="point.x - trendBarWidth / 2" :y="point.aiY" :width="trendBarWidth" :height="point.aiHeight" rx="3" /></g>
              <polyline class="resource-trend-topology-line" :points="trendTopologyLine" />
              <circle v-for="point in trendSvgPoints" :key="`topology-${point.date || point.index}`" class="resource-trend-topology-point" :class="{ 'is-active': activeTrendPoint?.index === point.index }" :cx="point.x" :cy="point.topologyY" r="3.5" />
              <rect v-for="point in trendSvgPoints" :key="`hit-${point.date || point.index}`" class="resource-trend-hit-area" :x="point.x - trendHitWidth / 2" :y="trendChartFrame.top" :width="trendHitWidth" :height="trendPlotHeight" tabindex="0" @mouseenter="activateTrendPoint(point)" @mouseleave="clearTrendPoint" @focus="activateTrendPoint(point)" @blur="clearTrendPoint"><title>{{ `${point.label}：AI调用 ${point.aiCalls.toLocaleString()} 次，拓扑生成 ${point.topology.toLocaleString()} 次` }}</title></rect>
            </svg>
          </div>
        </article>

        <article class="resource-usage-panel resource-feature-panel">
          <header class="resource-panel-header"><div><span>FEATURE USAGE</span><h3>功能使用明细</h3><p>各能力在当前范围内的调用占比</p></div><ChartBar :size="18" /></header>
          <div v-if="overviewLoading || overviewError" class="resource-panel-loading">{{ overviewError || '正在加载真实统计数据…' }}</div><template v-else><div class="resource-feature-total"><strong>{{ featureCallTotal.toLocaleString() }}</strong><span>次功能调用</span></div><ul class="resource-feature-list"><li v-for="(feature, index) in featureUsage" :key="feature.key || feature.name"><div class="resource-feature-row"><span>{{ feature.name }}</span><b>{{ Number(feature.calls || 0).toLocaleString() }}</b></div><div class="resource-feature-progress"><i :class="`is-${featureColor(index)}`" :style="{ width: `${Number(feature.share || 0)}%` }"></i></div></li><li v-if="!featureUsage.length" class="admin-billing-empty">数据库中暂无功能调用记录</li></ul></template>
        </article>
      </section>

      <section class="resource-statistics-bottom-grid">
        <article class="resource-usage-panel resource-tier-panel">
          <header class="resource-panel-header"><div><span>LEVEL DISTRIBUTION</span><h3>等级使用分布</h3><p>不同用户等级的真实用户占比</p></div><UsersRound :size="18" /></header>
          <div v-if="overviewLoading || overviewError" class="resource-panel-loading">{{ overviewError || '正在加载真实统计数据…' }}</div><div v-else class="resource-tier-list"><div v-for="tier in tierUsage" :key="tier.code" class="resource-tier-row"><div><span class="admin-tier" :class="`is-${String(tier.code).toLowerCase()}`">{{ tier.name }}</span><small>{{ Number(tier.users || 0).toLocaleString() }} 名用户</small></div><div class="resource-tier-bar"><i :style="{ width: `${Number(tier.share || 0)}%` }"></i></div><strong>{{ Number(tier.share || 0).toFixed(1) }}%</strong></div><div v-if="!tierUsage.length" class="admin-billing-empty">数据库中暂无等级使用记录</div></div>
        </article>
        <article class="resource-usage-panel resource-summary-panel">
          <header class="resource-panel-header"><div><span>RESOURCE SUMMARY</span><h3>资源使用摘要</h3><p>本范围内的关键运行状态</p></div><Gauge :size="18" /></header>
          <div v-if="overviewLoading || overviewError" class="resource-panel-loading">{{ overviewError || '正在加载真实统计数据…' }}</div><dl v-else class="resource-summary-list"><div><dt>平均单次 AI 调用</dt><dd>{{ currentSummary.averageTokensPerCall == null ? '--' : `${formatCount(currentSummary.averageTokensPerCall)} Token` }}</dd></div><div><dt>拓扑生成成功率</dt><dd class="is-success">{{ formatSummaryRate(currentSummary.topologySuccessRate) }}</dd></div><div><dt>平均响应时长</dt><dd>{{ formatLatency(currentSummary.averageResponseTimeMs) }}</dd></div><div><dt>AI调用成功率</dt><dd class="is-success">{{ formatSummaryRate(currentSummary.successRate) }}</dd></div><div><dt>当前活跃用户</dt><dd>{{ currentSummary.activeUserCount == null ? '--' : `${formatCount(currentSummary.activeUserCount)} 人` }}</dd></div><div><dt>数据更新时间</dt><dd>{{ formatDate(currentSummary.updatedAt) }}</dd></div></dl>
        </article>
      </section>
    </section>

    <section v-else class="user-quota-page">
      <header class="resource-statistics-intro"><div><p>资源与配额 / 用户配额</p><h2>用户配额</h2><span>管理某一具体用户当前的实际可用额度与调用限制。</span></div><Users :size="22" /></header>
      <section class="admin-card admin-billing-table-card">
        <div class="admin-billing-table-header"><div><h3>用户配额列表</h3><span>此处仅管理具体用户的当前使用量、剩余额度与个性化限制。</span></div><label class="admin-billing-search"><Search :size="16" /><input v-model="query" type="search" placeholder="搜索用户 ID、昵称或等级" aria-label="搜索用户配额" /></label></div>
        <div class="admin-billing-table-wrap"><table class="admin-billing-table"><thead><tr><th>用户 ID / 昵称</th><th>当前等级</th><th>AI调用</th><th>Token</th><th>拓扑生成</th><th>知识库问答</th><th>操作</th></tr></thead><tbody><tr v-for="user in filteredUsers" :key="user.id"><td><div class="admin-billing-user"><span>{{ user.name.slice(0, 1).toUpperCase() }}</span><div><strong>{{ user.name }}</strong><small>{{ user.id }}</small></div></div></td><td><div class="admin-inline-tier-select"><span class="admin-tier" :class="`is-${canonicalLevelCode(user.tier).toLowerCase()}`">{{ levelName(user.tier) }}</span><select v-model="user.tier" :aria-label="`切换 ${user.name} 用户等级`" @change="changeUserTier(user)"><option v-for="tier in tierConfigs" :key="tier.id" :value="tier.code">{{ tier.name }}</option></select></div></td><td><strong>{{ user.aiChatUsed.toLocaleString() }} / {{ user.aiChatTotal.toLocaleString() }}</strong><small class="admin-api-unit">剩余 {{ user.apiRemaining.toLocaleString() }} 次</small></td><td><div class="admin-token-usage"><div><strong>{{ user.usedTokens.toFixed(2) }} M</strong><span>/ {{ user.tokenLimit.toFixed(2) }} M</span><small class="admin-token-percent">{{ progressValue(user) }}%</small></div><div class="admin-token-progress" :class="`is-${progressTone(user)}`" role="progressbar" :aria-valuenow="progressValue(user)" aria-valuemin="0" aria-valuemax="100"><i :style="{ width: `${progressValue(user)}%` }" /></div></div></td><td><strong>{{ user.topologyUsed.toLocaleString() }} / {{ user.topologyTotal.toLocaleString() }}</strong></td><td><strong>{{ user.knowledgeQaUsed.toLocaleString() }} / {{ user.knowledgeQaTotal.toLocaleString() }}</strong></td><td><button type="button" class="admin-table-action admin-billing-adjust" @click="openQuotaDrawer(user)"><Settings2 :size="15" /> 精细配置</button></td></tr><tr v-if="!filteredUsers.length"><td colspan="7" class="admin-billing-empty"><Search :size="18" /> 未找到匹配的用户配额记录</td></tr></tbody></table></div>
      </section>
    </section>

    <Transition name="admin-drawer"><div v-if="tierDrawerOpen" class="admin-drawer-backdrop" @pointerdown.self="closeTierDrawer"><aside class="user-level-drawer" role="dialog" aria-modal="true" aria-label="编辑用户等级"><header><div><span>编辑等级</span><h3>用户等级配置</h3></div><button type="button" class="admin-close-button" aria-label="关闭等级编辑" @click="closeTierDrawer"><X :size="18" /></button></header><div class="user-level-drawer-body"><label><span>后台等级名称</span><input v-model.trim="tierDraft.name" type="text" readonly /></label><label><span>等级标识</span><input v-model.trim="tierDraft.code" type="text" readonly /></label><label class="drawer-field-wide"><span>等级说明</span><textarea v-model.trim="tierDraft.description" rows="3" maxlength="160" /></label><label class="drawer-toggle"><span><b>等级状态</b><small>停用后不再作为新用户默认身份</small></span><input v-model="tierDraft.enabled" type="checkbox" role="switch" /></label><div class="drawer-section-title">默认 AI 与资源额度</div><label><span>AI对话额度</span><div class="drawer-unit-input"><input v-model.number="tierDraft.aiCallQuota" min="0" type="number" /><small>次/月</small></div></label><label><span>AI拓扑生成</span><div class="drawer-unit-input"><input v-model.number="tierDraft.topologyQuota" min="0" type="number" /><small>次/月</small></div></label><label><span>知识库AI问答</span><div class="drawer-unit-input"><input v-model.number="tierDraft.knowledgeQaQuota" min="0" type="number" /><small>次/月</small></div></label><label><span>AI方案生成</span><div class="drawer-unit-input"><input v-model.number="tierDraft.networkPlanQuota" min="0" type="number" /><small>次/月</small></div></label><label><span>每日调用限制</span><div class="drawer-unit-input"><input v-model.number="tierDraft.dailyCallLimit" min="0" type="number" /><small>次/日</small></div></label><label><span>项目数量限制</span><div class="drawer-unit-input"><input v-model.number="tierDraft.projectLimit" min="0" type="number" /><small>个</small></div></label><label><span>知识库容量</span><div class="drawer-unit-input"><input v-model.number="tierDraft.knowledgeCapacityMb" min="0" type="number" /><small>MB</small></div></label><label><span>文件上传数量</span><div class="drawer-unit-input"><input v-model.number="tierDraft.fileUploadLimit" min="0" type="number" /><small>个</small></div></label><label><span>单文件大小</span><div class="drawer-unit-input"><input v-model.number="tierDraft.singleFileSizeMb" min="0" type="number" /><small>MB</small></div></label><label class="drawer-field-wide"><span>Token额度</span><div class="drawer-unit-input"><input v-model.number="tierDraft.tokenQuota" min="0" step="1000" type="number" /><small>Token/月</small></div></label></div><footer><button type="button" class="admin-ghost-button" @click="closeTierDrawer">取消</button><button type="button" class="admin-primary-button" @click="saveTier"><Save :size="16" /> 保存修改</button></footer></aside></div></Transition>

    <Transition name="admin-drawer"><div v-if="permissionDrawerOpen" class="admin-drawer-backdrop" @pointerdown.self="closePermissionDrawer"><aside class="user-level-drawer permission-drawer" role="dialog" aria-modal="true" aria-label="配置用户等级权限"><header><div><span>权限配置</span><h3>{{ tierConfigs.find((tier) => tier.id === selectedTierId)?.name }} 默认权限</h3></div><button type="button" class="admin-close-button" aria-label="关闭权限配置" @click="closePermissionDrawer"><X :size="18" /></button></header><div class="permission-drawer-body"><section v-for="group in permissionGroups" :key="group.title"><h4>{{ group.title }}</h4><label v-for="permission in group.items" :key="permission.key" class="permission-switch"><span>{{ permission.label }}</span><input v-model="permissionDraft[permission.key]" type="checkbox" role="switch" /></label></section></div><footer><button type="button" class="admin-ghost-button" @click="closePermissionDrawer">取消</button><button type="button" class="admin-primary-button" @click="savePermissions"><ShieldCheck :size="16" /> 保存配置</button></footer></aside></div></Transition>

    <Transition name="admin-drawer"><div v-if="quotaDrawerOpen" class="admin-drawer-backdrop" @pointerdown.self="closeQuotaDrawer"><aside class="user-level-drawer quota-drawer" role="dialog" aria-modal="true" aria-label="用户配额精细配置"><header><div><span>用户配额精细配置</span><h3>{{ selectedUser?.name }}</h3></div><button type="button" class="admin-close-button" aria-label="关闭用户配额配置" @click="closeQuotaDrawer"><X :size="18" /></button></header><div class="user-level-drawer-body"><label><span>每分钟请求数</span><div class="drawer-unit-input"><input v-model.number="quotaDraft.rpm" min="1" type="number" /><small>RPM</small></div></label><label><span>每分钟Token数</span><div class="drawer-unit-input"><input v-model.number="quotaDraft.tpm" min="1" type="number" /><small>TPM</small></div></label><label class="drawer-field-wide"><span>额外AI调用额度</span><div class="drawer-unit-input"><input v-model.number="quotaDraft.customDailyCalls" min="0" type="number" /><small>次/日</small></div></label><section class="drawer-field-wide quota-models"><h4>可用模型权限</h4><button v-for="model in availableModels" :key="model.id" type="button" :class="{ active: quotaDraft.allowedModels.includes(model.id) }" @click="toggleModelAccess(model.id)"><Bot :size="16" /><span><strong>{{ model.name }}</strong><small>{{ model.provider }} · {{ model.description }}</small></span><i><b /></i></button></section></div><footer><button type="button" class="admin-ghost-button" @click="closeQuotaDrawer">取消</button><button type="button" class="admin-primary-button" @click="saveQuotaConfig"><Save :size="16" /> 保存配置</button></footer></aside></div></Transition>

    <Transition name="admin-toast"><div v-if="toast" class="admin-toast" role="status">{{ toast }}</div></Transition>
  </section>
</template>

<style scoped>
.admin-billing-page { width: min(100%, 1280px); min-height: 100%; margin: 0 auto; padding: 26px 28px 40px; color: #111827; }
.user-level-intro,.resource-statistics-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.user-level-breadcrumb,.resource-statistics-intro p { margin: 0 0 8px; color: #6b7280; font-size: 13px; }.user-level-breadcrumb span { margin: 0 7px; color: #9ca3af; }
.user-level-intro h2,.resource-statistics-intro h2 { margin: 0; color: #111827; font-size: 24px; font-weight: 700; line-height: 1.25; }.user-level-intro p,.resource-statistics-intro > div > span { display: block; margin-top: 7px; color: #6b7280; font-size: 13px; line-height: 1.6; }
.user-level-create { flex: 0 0 auto; min-height: 36px; padding: 0 13px; border-radius: 6px; }.user-level-create svg { margin-right: 5px; }
.user-level-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }.user-level-stats article { display: grid; gap: 5px; min-height: 82px; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 7px; background: #fff; }.user-level-stats span { color: #6b7280; font-size: 12px; }.user-level-stats strong { color: #111827; font-size: 22px; font-weight: 700; line-height: 1.1; }
.user-level-table-card { overflow: hidden; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }.user-level-table-wrap { overflow-x: auto; }.user-level-table { width: 100%; min-width: 1080px; border-collapse: collapse; }.user-level-table th { padding: 12px 14px; background: #f9fafb; color: #6b7280; font-size: 11px; font-weight: 600; text-align: left; white-space: nowrap; }.user-level-table td { padding: 14px; border-top: 1px solid #f1f5f9; color: #374151; font-size: 12px; vertical-align: middle; }.user-level-table tbody tr:hover { background: #fafafa; }
.user-level-name { display: grid; min-width: 165px; gap: 4px; }.user-level-name strong { color: #111827; font-size: 13px; font-weight: 650; }.user-level-name small { overflow: hidden; max-width: 205px; color: #6b7280; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.user-level-table code { padding: 3px 6px; border-radius: 4px; background: #f3f4f6; color: #374151; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; }.user-level-table td > strong { color: #1f2937; font-weight: 600; white-space: nowrap; }
.user-level-status { display: inline-flex; align-items: center; gap: 6px; color: #047857; font-weight: 600; white-space: nowrap; }.user-level-status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.user-level-status.disabled { color: #9ca3af; }.user-level-permission-count { color: #2563eb; font-weight: 600; white-space: nowrap; }.user-level-table time { color: #6b7280; white-space: nowrap; }.user-level-actions { display: flex; align-items: center; gap: 12px; white-space: nowrap; }.user-level-actions button { padding: 0; border: 0; background: transparent; color: #2563eb; font: inherit; font-weight: 600; cursor: pointer; }.user-level-actions button:hover { color: #1d4ed8; text-decoration: underline; }
.user-level-reference { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }.user-level-reference article { padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 7px; background: #fff; }.user-level-reference header { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; padding-bottom: 10px; border-bottom: 1px solid #f1f5f9; }.user-level-reference header strong { color: #111827; font-size: 13px; }.user-level-reference header span { color: #9ca3af; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }.user-level-reference dl { display: grid; gap: 8px; margin: 11px 0 0; }.user-level-reference dl > div { display: flex; justify-content: space-between; gap: 10px; }.user-level-reference dt,.user-level-reference dd { margin: 0; font-size: 12px; }.user-level-reference dt { color: #6b7280; }.user-level-reference dd { color: #047857; font-weight: 600; text-align: right; }.user-level-reference dd.unsupported { color: #9ca3af; }
.resource-statistics-intro { align-items: center; padding-bottom: 16px; border-bottom: 1px solid #e5e7eb; }.resource-statistics-intro > div:first-child { min-width: 0; }.resource-statistics-intro > svg { color: #2563eb; }.resource-statistics-actions { display: grid; justify-items: end; gap: 10px; }.resource-sync-status { display: inline-flex; align-items: center; gap: 6px; color: #94a3b8; font-size: 11px; }.resource-sync-status i { width: 6px; height: 6px; border-radius: 50%; background: #10b981; box-shadow: 0 0 0 3px rgba(16, 185, 129, .1); }.resource-range-tabs { display: inline-flex; gap: 2px; padding: 3px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }.resource-range-tabs button { min-height: 28px; padding: 0 10px; border: 0; border-radius: 6px; background: transparent; color: #64748b; font-size: 11px; cursor: pointer; }.resource-range-tabs button:hover { color: #1f2937; }.resource-range-tabs button.active { background: #eff6ff; color: #2563eb; font-weight: 650; box-shadow: none; }
.resource-statistics-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }.resource-statistics-grid article { display: grid; gap: 9px; min-height: 112px; padding: 17px 18px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, .03); }.resource-stat-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.resource-stat-card-head span,.resource-stat-card-foot small { color: #64748b; font-size: 12px; }.resource-stat-card-head > svg { width: 18px; height: 18px; padding: 0; color: #94a3b8; }.resource-stat-card-head i,.resource-statistics-grid article.is-violet .resource-stat-card-head i,.resource-statistics-grid article.is-green .resource-stat-card-head i,.resource-statistics-grid article.is-orange .resource-stat-card-head i { display: none; }.resource-statistics-grid strong { color: #111827; font-size: 28px; font-weight: 700; line-height: 1; }.resource-stat-card-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.resource-stat-card-foot b { color: #64748b; font-size: 11px; font-weight: 650; }.resource-stat-card-foot small { overflow: hidden; color: #94a3b8; text-overflow: ellipsis; white-space: nowrap; }.resource-statistics-state { grid-column: 1 / -1; display: grid; place-items: center; gap: 7px; min-height: 112px; padding: 18px; border: 1px dashed #cbd5e1; border-radius: 10px; background: #fff; color: #64748b; text-align: center; }.resource-statistics-state strong { color: #334155; font-size: 13px; }.resource-statistics-state span { font-size: 12px; }.resource-statistics-state button { min-height: 28px; padding: 0 10px; border: 1px solid #dbeafe; border-radius: 6px; background: #eff6ff; color: #2563eb; font-size: 12px; font-weight: 650; cursor: pointer; }.resource-stat-skeleton { overflow: hidden; background: #fff !important; }.resource-stat-skeleton > span,.resource-stat-skeleton > strong,.resource-stat-skeleton > small { display: block; border-radius: 4px; background: #eef2f7; animation: resource-stat-pulse 1.1s ease-in-out infinite alternate; }.resource-stat-skeleton > span { width: 42%; height: 12px; }.resource-stat-skeleton > strong { width: 58%; height: 27px; }.resource-stat-skeleton > small { width: 78%; height: 11px; }@keyframes resource-stat-pulse { from { opacity: .55; } to { opacity: 1; } }
.resource-statistics-main-grid,.resource-statistics-bottom-grid { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(330px, 1fr); gap: 14px; margin-bottom: 14px; }.resource-usage-panel { min-width: 0; padding: 18px 19px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, .025); }.resource-panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }.resource-panel-header > div { display: grid; gap: 4px; }.resource-panel-header > div > span { color: #9ca3af; font-size: 10px; font-weight: 750; letter-spacing: .08em; }.resource-panel-header h3 { margin: 0; color: #111827; font-size: 16px; font-weight: 700; }.resource-panel-header p { margin: 0; color: #94a3b8; font-size: 11px; }.resource-panel-header > svg { color: #94a3b8; }
.resource-trend-header { align-items: center; }.resource-trend-legend { display: flex; gap: 16px; margin-top: 17px; color: #64748b; font-size: 11px; }.resource-trend-legend span { display: inline-flex; align-items: center; gap: 6px; }.resource-trend-legend span.is-muted { opacity: .5; }.resource-trend-legend i { width: 7px; height: 7px; border-radius: 50%; background: #2563eb; }.resource-trend-legend i.is-green { background: #10b981; }.resource-trend-chart { height: 238px; margin-top: 9px; }.resource-trend-svg { display: block; width: 100%; height: 100%; overflow: visible; }.resource-trend-grid-line line { stroke: #e8eef7; stroke-width: 1; }.resource-trend-grid-line text,.resource-trend-x-label text { fill: #94a3b8; font-size: 10px; }.resource-trend-grid-line text { text-anchor: end; }.resource-trend-x-label line { stroke: #cbd5e1; stroke-width: 1; }.resource-trend-axis { stroke: #cbd5e1; stroke-width: 1; }.resource-trend-call-bar { fill: #2563eb; opacity: .92; transition: fill .16s ease, opacity .16s ease; }.resource-trend-series.is-active .resource-trend-call-bar { fill: #1d4ed8; opacity: 1; }.resource-trend-topology-line { fill: none; stroke: #10b981; stroke-width: 2.25; stroke-linecap: round; stroke-linejoin: round; }.resource-trend-topology-point { fill: #fff; stroke: #10b981; stroke-width: 2; transition: fill .16s ease, r .16s ease; }.resource-trend-topology-point.is-active { fill: #10b981; }.resource-trend-hit-area { fill: transparent; cursor: pointer; outline: none; }.resource-trend-hit-area:focus { fill: rgba(37, 99, 235, .05); }.resource-chart-empty,.resource-chart-loading { display: grid; min-height: 238px; place-items: center; margin-top: 11px; border-top: 1px solid #f1f5f9; color: #94a3b8; font-size: 12px; }.resource-panel-loading { display: grid; min-height: 170px; place-items: center; color: #94a3b8; font-size: 12px; }
.resource-feature-total { display: flex; align-items: baseline; gap: 7px; margin: 19px 0 16px; }.resource-feature-total strong { color: #111827; font-size: 25px; font-weight: 720; }.resource-feature-total span { color: #6b7280; font-size: 11px; }.resource-feature-list { display: grid; gap: 14px; margin: 0; padding: 0; list-style: none; }.resource-feature-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #4b5563; font-size: 12px; }.resource-feature-row b { color: #111827; font-size: 12px; font-weight: 650; }.resource-feature-progress { height: 5px; margin-top: 7px; overflow: hidden; border-radius: 99px; background: #f1f5f9; }.resource-feature-progress i { display: block; height: 100%; border-radius: inherit; background: #2563eb; }.resource-feature-progress i.is-violet { background: #8b5cf6; }.resource-feature-progress i.is-green { background: #10b981; }.resource-feature-progress i.is-orange { background: #f59e0b; }.resource-feature-progress i.is-slate { background: #64748b; }
.resource-tier-list { display: grid; gap: 16px; margin-top: 21px; }.resource-tier-row { display: grid; grid-template-columns: minmax(130px, 1fr) minmax(100px, 1.5fr) auto; align-items: center; gap: 12px; }.resource-tier-row > div:first-child { display: flex; align-items: center; gap: 8px; min-width: 0; }.resource-tier-row .admin-tier { border: 1px solid #e2e8f0; background: #f8fafc; color: #64748b; }.resource-tier-row .admin-tier.is-standard { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }.resource-tier-row .admin-tier.is-pro { border-color: #dbeafe; background: #eff6ff; color: #2563eb; }.resource-tier-row small { color: #9ca3af; font-size: 10px; white-space: nowrap; }.resource-tier-bar { height: 6px; overflow: hidden; border-radius: 99px; background: #f1f5f9; }.resource-tier-bar i { display: block; height: 100%; border-radius: inherit; background: #64748b; }.resource-tier-row:nth-child(2) .resource-tier-bar i { background: #10b981; }.resource-tier-row:nth-child(3) .resource-tier-bar i { background: #2563eb; }.resource-tier-row > strong { color: #374151; font-size: 12px; font-weight: 650; }.resource-summary-list { display: grid; gap: 0; margin: 18px 0 0; }.resource-summary-list div { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 1px solid #eef2f7; }.resource-summary-list div:last-child { border-bottom: 0; }.resource-summary-list dt { color: #6b7280; font-size: 12px; }.resource-summary-list dd { margin: 0; color: #111827; font-size: 12px; font-weight: 650; }.resource-summary-list dd.is-success { color: #059669; }
.admin-billing-table-card { overflow: hidden; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }.admin-billing-table-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 16px; border-bottom: 1px solid #e5e7eb; }.admin-billing-table-header h3 { margin: 0; color: #111827; font-size: 15px; }.admin-billing-table-header > div > span { display: block; margin-top: 5px; color: #6b7280; font-size: 12px; }.admin-billing-search { display: flex; align-items: center; gap: 8px; width: 260px; height: 36px; padding: 0 10px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; color: #6b7280; }.admin-billing-search:focus-within { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37, 99, 235, .14); }.admin-billing-search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: #111827; font-size: 13px; }.admin-billing-table-wrap { overflow-x: auto; }.admin-billing-table { width: 100%; min-width: 900px; border-collapse: collapse; }.admin-billing-table th { padding: 11px 16px; background: #f9fafb; color: #6b7280; font-size: 11px; text-align: left; }.admin-billing-table td { padding: 13px 16px; border-top: 1px solid #f3f4f6; color: #374151; font-size: 12px; }.admin-billing-user { display: flex; align-items: center; gap: 10px; }.admin-billing-user > span { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 7px; background: #eff6ff; color: #2563eb; font-weight: 700; }.admin-billing-user strong,.admin-billing-user small { display: block; }.admin-billing-user strong { color: #111827; }.admin-billing-user small { margin-top: 3px; color: #9ca3af; }.admin-inline-tier-select { display: flex; align-items: center; gap: 7px; }.admin-inline-tier-select select { min-width: 95px; height: 28px; padding: 0 6px; border: 1px solid #e5e7eb; border-radius: 5px; background: #fff; color: #374151; font-size: 11px; }.admin-tier { display: inline-flex; align-items: center; min-height: 23px; padding: 0 7px; border-radius: 99px; background: #f3f4f6; color: #4b5563; font-size: 11px; font-weight: 600; white-space: nowrap; }.admin-tier.is-standard { background: #f0fdf4; color: #15803d; }.admin-tier.is-pro { background: #eff6ff; color: #2563eb; }.admin-token-usage { width: 205px; }.admin-token-usage > div:first-child { display: flex; align-items: baseline; gap: 4px; }.admin-token-usage strong,.admin-api-remaining { color: #1f2937; }.admin-token-usage small,.admin-token-usage span,.admin-api-unit { color: #6b7280; font-size: 11px; }.admin-token-percent { margin-left: auto; }.admin-token-progress { height: 5px; margin-top: 7px; overflow: hidden; border-radius: 99px; background: #e5e7eb; }.admin-token-progress i { display: block; height: 100%; border-radius: inherit; background: #2563eb; }.admin-token-progress.is-warning i { background: #d97706; }.admin-token-progress.is-critical i { background: #dc2626; }.admin-table-action { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: #2563eb; font-size: 12px; font-weight: 600; cursor: pointer; }.admin-billing-empty { height: 140px; color: #9ca3af !important; text-align: center; }
.admin-drawer-backdrop { position: fixed; z-index: 80; inset: 0; display: flex; justify-content: flex-end; background: rgba(15, 23, 42, .28); }.user-level-drawer { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; width: min(560px, 100%); height: 100%; background: #fff; box-shadow: -16px 0 40px rgba(15, 23, 42, .16); }.user-level-drawer > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 20px 22px 16px; border-bottom: 1px solid #e5e7eb; }.user-level-drawer header span { color: #6b7280; font-size: 12px; }.user-level-drawer header h3 { margin: 4px 0 0; color: #111827; font-size: 17px; }.admin-close-button { display: grid; width: 32px; height: 32px; place-items: center; padding: 0; border: 0; border-radius: 6px; background: transparent; color: #6b7280; cursor: pointer; }.admin-close-button:hover { background: #f3f4f6; color: #111827; }.user-level-drawer-body { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 14px; overflow-y: auto; padding: 18px 22px; }.user-level-drawer-body label { display: grid; gap: 7px; color: #374151; font-size: 12px; font-weight: 600; }.user-level-drawer-body input:not([type='checkbox']),.user-level-drawer-body textarea { width: 100%; box-sizing: border-box; border: 1px solid #d1d5db; border-radius: 6px; outline: 0; background: #fff; color: #111827; font: inherit; font-size: 13px; }.user-level-drawer-body input:not([type='checkbox']) { height: 36px; padding: 0 10px; }.user-level-drawer-body textarea { min-height: 72px; padding: 9px 10px; resize: vertical; }.user-level-drawer-body input:focus,.user-level-drawer-body textarea:focus { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37, 99, 235, .14); }.drawer-field-wide { grid-column: 1 / -1; }.drawer-toggle { display: flex !important; grid-column: 1 / -1; flex-direction: row; align-items: center; justify-content: space-between; padding: 11px 12px; border: 1px solid #e5e7eb; border-radius: 6px; }.drawer-toggle span { display: grid; gap: 3px; }.drawer-toggle b { color: #111827; }.drawer-toggle small { color: #6b7280; font-size: 11px; font-weight: 400; }.drawer-section-title { grid-column: 1 / -1; padding-top: 4px; color: #111827; font-size: 13px; font-weight: 700; }.drawer-unit-input { display: flex; align-items: center; height: 36px; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden; background: #fff; }.drawer-unit-input:focus-within { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37, 99, 235, .14); }.drawer-unit-input input { border: 0 !important; box-shadow: none !important; }.drawer-unit-input small { padding-right: 10px; color: #6b7280; font-size: 11px; white-space: nowrap; }.user-level-drawer footer { display: flex; justify-content: flex-end; gap: 10px; padding: 15px 22px; border-top: 1px solid #e5e7eb; }.admin-ghost-button { min-height: 34px; padding: 0 13px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; color: #374151; font-size: 13px; cursor: pointer; }.admin-primary-button { display: inline-flex; align-items: center; justify-content: center; min-height: 34px; border: 0; background: #2563eb; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }.admin-primary-button:hover { background: #1d4ed8; }.admin-primary-button svg { margin-right: 5px; }
.permission-drawer-body { overflow-y: auto; padding: 8px 22px 18px; }.permission-drawer-body section { padding: 16px 0; border-bottom: 1px solid #e5e7eb; }.permission-drawer-body section:last-child { border-bottom: 0; }.permission-drawer-body h4,.quota-models h4 { margin: 0 0 8px; color: #111827; font-size: 13px; }.permission-switch { display: flex; align-items: center; justify-content: space-between; min-height: 40px; color: #374151; font-size: 13px; }.permission-switch input,.drawer-toggle input { width: 34px; height: 20px; appearance: none; border: 0; border-radius: 999px; outline: 0; background: #d1d5db; cursor: pointer; transition: background .18s ease; }.permission-switch input::after,.drawer-toggle input::after { display: block; width: 16px; height: 16px; margin: 2px; border-radius: 50%; background: #fff; content: ''; transition: transform .18s ease; }.permission-switch input:checked,.drawer-toggle input:checked { background: #2563eb; }.permission-switch input:checked::after,.drawer-toggle input:checked::after { transform: translateX(14px); }
.quota-models { display: grid; gap: 7px; }.quota-models > button { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 9px; padding: 9px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; color: #6b7280; text-align: left; cursor: pointer; }.quota-models > button.active { border-color: #93c5fd; background: #eff6ff; color: #2563eb; }.quota-models span { display: grid; gap: 2px; }.quota-models strong { color: #111827; font-size: 12px; }.quota-models small { overflow: hidden; color: #6b7280; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.quota-models i { width: 30px; height: 17px; border-radius: 999px; background: #d1d5db; }.quota-models i b { display: block; width: 13px; height: 13px; margin: 2px; border-radius: 50%; background: #fff; transition: transform .18s ease; }.quota-models > button.active i { background: #2563eb; }.quota-models > button.active i b { transform: translateX(13px); }
.admin-toast { position: fixed; z-index: 100; right: 24px; bottom: 24px; padding: 11px 14px; border-radius: 6px; background: #111827; color: #fff; font-size: 13px; box-shadow: 0 10px 24px rgba(15, 23, 42, .22); }.admin-drawer-enter-active,.admin-drawer-leave-active { transition: opacity .18s ease; }.admin-drawer-enter-active .user-level-drawer,.admin-drawer-leave-active .user-level-drawer { transition: transform .18s ease; }.admin-drawer-enter-from,.admin-drawer-leave-to { opacity: 0; }.admin-drawer-enter-from .user-level-drawer,.admin-drawer-leave-to .user-level-drawer { transform: translateX(100%); }.admin-toast-enter-active,.admin-toast-leave-active { transition: opacity .16s ease, transform .16s ease; }.admin-toast-enter-from,.admin-toast-leave-to { opacity: 0; transform: translateY(8px); }
:global(html[data-theme='dark']) .admin-billing-page { color: #e5e7eb; }:global(html[data-theme='dark']) .user-level-intro h2,:global(html[data-theme='dark']) .resource-statistics-intro h2,:global(html[data-theme='dark']) .user-level-stats strong,:global(html[data-theme='dark']) .user-level-name strong,:global(html[data-theme='dark']) .user-level-table td > strong,:global(html[data-theme='dark']) .user-level-reference header strong,:global(html[data-theme='dark']) .resource-statistics-grid strong { color: #f9fafb; }:global(html[data-theme='dark']) .user-level-stats article,:global(html[data-theme='dark']) .user-level-table-card,:global(html[data-theme='dark']) .user-level-reference article,:global(html[data-theme='dark']) .resource-statistics-grid article,:global(html[data-theme='dark']) .admin-billing-table-card,:global(html[data-theme='dark']) .user-level-drawer { border-color: #334155; background: #111827; }:global(html[data-theme='dark']) .user-level-table th,:global(html[data-theme='dark']) .admin-billing-table th { background: #1f2937; }:global(html[data-theme='dark']) .user-level-table td,:global(html[data-theme='dark']) .admin-billing-table td,:global(html[data-theme='dark']) .user-level-reference header,:global(html[data-theme='dark']) .resource-statistics-intro,:global(html[data-theme='dark']) .admin-billing-table-header,:global(html[data-theme='dark']) .user-level-drawer > header,:global(html[data-theme='dark']) .user-level-drawer footer,:global(html[data-theme='dark']) .permission-drawer-body section { border-color: #334155; }:global(html[data-theme='dark']) .user-level-table tbody tr:hover { background: #1f2937; }:global(html[data-theme='dark']) .user-level-table code,:global(html[data-theme='dark']) .admin-inline-tier-select select,:global(html[data-theme='dark']) .admin-billing-search,:global(html[data-theme='dark']) .user-level-drawer-body input:not([type='checkbox']),:global(html[data-theme='dark']) .user-level-drawer-body textarea,:global(html[data-theme='dark']) .drawer-unit-input,:global(html[data-theme='dark']) .drawer-toggle,:global(html[data-theme='dark']) .admin-ghost-button,:global(html[data-theme='dark']) .quota-models > button { border-color: #475569; background: #1f2937; color: #e5e7eb; }:global(html[data-theme='dark']) .user-level-drawer header h3,:global(html[data-theme='dark']) .user-level-drawer-body input:not([type='checkbox']),:global(html[data-theme='dark']) .user-level-drawer-body textarea,:global(html[data-theme='dark']) .quota-models strong,:global(html[data-theme='dark']) .admin-billing-table-header h3,:global(html[data-theme='dark']) .admin-billing-user strong,:global(html[data-theme='dark']) .admin-token-usage strong,:global(html[data-theme='dark']) .admin-api-remaining { color: #f9fafb; }
:global(html[data-theme='dark']) .resource-usage-panel { border-color: #334155; background: #111827; }:global(html[data-theme='dark']) .resource-range-tabs { border-color: #334155; background: #1f2937; }:global(html[data-theme='dark']) .resource-range-tabs button.active { background: #374151; color: #f9fafb; }:global(html[data-theme='dark']) .resource-stat-card-head > svg { color: #94a3b8; }:global(html[data-theme='dark']) .resource-trend-grid-line line,:global(html[data-theme='dark']) .resource-trend-axis { stroke: #334155; }:global(html[data-theme='dark']) .resource-trend-grid-line text,:global(html[data-theme='dark']) .resource-trend-x-label text { fill: #64748b; }:global(html[data-theme='dark']) .resource-trend-x-label line { stroke: #475569; }:global(html[data-theme='dark']) .resource-trend-topology-point { fill: #111827; }:global(html[data-theme='dark']) .resource-feature-progress,:global(html[data-theme='dark']) .resource-tier-bar { background: #1f2937; }:global(html[data-theme='dark']) .resource-summary-list div { border-color: #334155; }:global(html[data-theme='dark']) .resource-statistics-grid article strong,:global(html[data-theme='dark']) .resource-feature-total strong,:global(html[data-theme='dark']) .resource-feature-row b,:global(html[data-theme='dark']) .resource-summary-list dd,:global(html[data-theme='dark']) .resource-tier-row > strong { color: #f9fafb; }
@media (max-width: 900px) { .admin-billing-page { padding: 22px 18px 32px; }.user-level-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }.user-level-reference { grid-template-columns: 1fr; }.resource-statistics-grid { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .user-level-intro,.admin-billing-table-header { flex-direction: column; }.user-level-create,.admin-billing-search { width: 100%; }.user-level-create { min-height: 38px; }.user-level-drawer-body { grid-template-columns: 1fr; }.drawer-field-wide { grid-column: auto; }.user-level-drawer { width: 100%; }.admin-toast { right: 16px; bottom: 16px; left: 16px; text-align: center; } }
@media (max-width: 900px) { .resource-statistics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.resource-statistics-main-grid,.resource-statistics-bottom-grid { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .resource-statistics-intro { align-items: flex-start; flex-direction: column; }.resource-statistics-actions { justify-items: start; width: 100%; }.resource-range-tabs { width: 100%; }.resource-range-tabs button { flex: 1; }.resource-statistics-grid { grid-template-columns: 1fr; }.resource-usage-panel { padding: 15px; }.resource-trend-header { align-items: flex-start; flex-direction: column; }.resource-trend-chart { height: 192px; }.resource-tier-row { grid-template-columns: minmax(120px, 1fr) auto; }.resource-tier-bar { grid-column: 1 / -1; grid-row: 2; }.resource-tier-row > strong { grid-column: 2; grid-row: 1; } }
</style>
