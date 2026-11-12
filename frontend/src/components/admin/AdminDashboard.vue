<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { Activity, Bot, FileOutput, Gauge, Network, Server, UsersRound } from '@lucide/vue'
import { getAdminDashboard } from '../../api/admin'

const props = defineProps({
  theme: { type: String, default: 'light' }
})

const isLoading = ref(true)
const isReady = ref(false)
const trendRange = ref(30)
const trendChartElement = ref(null)
const creationChartElement = ref(null)
const deviceChartElement = ref(null)
let trendChart = null
let creationChart = null
let deviceChart = null
let resizeObserver = null

const metricMeta = {
  '用户总数': { tone: 'blue', icon: UsersRound },
  '今日活跃用户': { tone: 'green', icon: Activity },
  '今日拓扑绘制': { tone: 'violet', icon: Network },
  'AI拓扑生成': { tone: 'cyan', icon: Bot },
  '工程文档导出': { tone: 'amber', icon: FileOutput },
  'AI服务状态': { tone: 'green', icon: Server }
}
const metricCards = ref([])
const platformData = ref({ creationModes: [], deviceTypes: [], aiCapabilities: [], resourceStats: { total: 0, enabled: 0, disabled: 0 } })
const trendData = ref({ 7: [], 30: [], 90: [] })
const serviceStatus = ref('检查中')

function chartPalette() {
  const dark = props.theme === 'dark' || document.documentElement.dataset.theme === 'dark'
  return {
    text: dark ? '#f4f4f5' : '#111827',
    muted: dark ? '#a1a1aa' : '#6b7280',
    grid: dark ? '#27272a' : '#eef2f7',
    panel: dark ? '#18181b' : '#ffffff'
  }
}

function buildCharts() {
  if (!trendChartElement.value || !creationChartElement.value || !deviceChartElement.value) return
  const palette = chartPalette()
  trendChart?.dispose(); creationChart?.dispose(); deviceChart?.dispose()
  trendChart = echarts.init(trendChartElement.value)
  creationChart = echarts.init(creationChartElement.value)
  deviceChart = echarts.init(deviceChartElement.value)
  const trend = trendData.value[trendRange.value] || []

  trendChart.setOption({
    color: ['#2563eb', '#10b981', '#f59e0b'],
    grid: { top: 18, right: 18, bottom: 58, left: 42, containLabel: true },
    legend: {
      left: 'center',
      bottom: 6,
      orient: 'horizontal',
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 8,
      itemGap: 24,
      textStyle: { color: palette.muted, fontSize: 12 }
    },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.map((item) => item.label), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.muted, fontSize: 11, margin: 10 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: palette.grid } }, axisLabel: { color: palette.muted, fontSize: 11 } },
    series: [
      { name: '拓扑绘制次数', type: 'line', smooth: true, showSymbol: false, data: trend.map((item) => item.topology), lineStyle: { width: 2 } },
      { name: 'AI 调用次数', type: 'line', smooth: true, showSymbol: false, data: trend.map((item) => item.ai), lineStyle: { width: 2 } },
      { name: '文档导出次数', type: 'line', smooth: true, showSymbol: false, data: trend.map((item) => item.exports), lineStyle: { width: 2 } }
    ]
  })

  creationChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
    series: [{ type: 'pie', radius: ['54%', '76%'], center: ['50%', '48%'], avoidLabelOverlap: true, label: { show: false }, data: platformData.value.creationModes.map((item) => ({ ...item, itemStyle: { color: item.color } })) }]
  })

  deviceChart.setOption({
    grid: { top: 8, right: 22, bottom: 8, left: 76 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (items) => `${items[0].name}: ${items[0].value}%` },
    xAxis: { type: 'value', max: 40, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: palette.grid } }, axisLabel: { color: palette.muted, fontSize: 10, formatter: '{value}%' } },
    yAxis: { type: 'category', inverse: true, data: platformData.value.deviceTypes.map((item) => item.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text, fontSize: 11 } },
    series: [{ type: 'bar', barWidth: 12, data: platformData.value.deviceTypes.map((item) => item.value), itemStyle: { color: '#2563eb', borderRadius: [0, 4, 4, 0] }, label: { show: true, position: 'right', color: palette.muted, fontSize: 11, formatter: '{c}%' } }]
  })
}

async function loadDashboard() {
  try {
    const data = await getAdminDashboard()
    const cards = (data?.metrics ?? []).map((metric) => ({ ...metric, ...(metricMeta[metric.label] || { tone: 'blue', icon: Gauge }) }))
    cards.push({ label: 'AI服务状态', value: data?.serviceStatus || '未知', trend: '数据库健康检查', tone: 'green', icon: Server })
    metricCards.value = cards
    platformData.value = {
      creationModes: (data?.creationModes ?? []).map((item, index) => ({ ...item, color: ['#2563eb', '#64748b', '#10b981', '#f59e0b'][index % 4] })),
      deviceTypes: data?.deviceTypes ?? [],
      aiCapabilities: data?.aiCapabilities ?? [],
      resourceStats: data?.resourceStats ?? { total: 0, enabled: 0, disabled: 0 }
    }
    trendData.value = data?.trends ?? { 7: [], 30: [], 90: [] }
    serviceStatus.value = data?.serviceStatus || '未知'
  } catch {
    metricCards.value = []
    platformData.value = { creationModes: [], deviceTypes: [], aiCapabilities: [], resourceStats: { total: 0, enabled: 0, disabled: 0 } }
    trendData.value = { 7: [], 30: [], 90: [] }
    serviceStatus.value = '读取失败'
  } finally {
    isLoading.value = false
  }
  await nextTick()
  buildCharts()
  isReady.value = true
  resizeObserver?.observe(trendChartElement.value)
  resizeObserver?.observe(creationChartElement.value)
  resizeObserver?.observe(deviceChartElement.value)
}

watch([trendRange, () => props.theme], () => {
  if (!isLoading.value) nextTick(buildCharts)
})

onMounted(() => {
  resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => { trendChart?.resize(); creationChart?.resize(); deviceChart?.resize() })
  loadDashboard()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  trendChart?.dispose(); creationChart?.dispose(); deviceChart?.dispose()
})
</script>

<template>
  <section class="platform-dashboard" :class="{ 'is-ready': isReady }" aria-label="系统总览">
    <header class="admin-page-intro compact platform-dashboard-intro">
      <div><p>网络拓扑工程规划辅助系统</p><h2>系统总览</h2><span>查看平台用户、AI 服务、拓扑绘制与系统运行情况。</span></div>
      <div class="admin-service-status"><span class="admin-status-pulse"></span>{{ serviceStatus }}</div>
    </header>

    <div v-if="isLoading" class="platform-dashboard-loading" aria-label="数据加载中"><span v-for="item in 9" :key="item"></span></div>

    <div v-else class="platform-dashboard-content">
      <section class="platform-metric-grid" aria-label="平台核心指标">
          <article v-for="metric in metricCards" :key="metric.label" class="platform-metric-card">
          <span class="platform-metric-icon"><component :is="metric.icon" :size="18" /></span>
          <span class="platform-metric-label">{{ metric.label }}</span>
          <strong>{{ metric.value }}<small v-if="metric.suffix">{{ metric.suffix }}</small></strong>
          <span class="platform-metric-note">{{ metric.trend }}</span>
        </article>
      </section>

      <section class="platform-chart-grid">
        <article class="admin-card platform-panel platform-creation-panel">
          <header class="platform-panel-header"><div><h3>项目类型分布</h3><p>根据数据库中已保存项目类型统计。</p></div></header>
          <div class="platform-creation-body"><div ref="creationChartElement" class="platform-chart platform-donut-chart" aria-label="拓扑创建方式环形图"></div><div class="platform-legend"><span v-for="item in platformData.creationModes" :key="item.name"><i :style="{ background: item.color }"></i><b>{{ item.name }}</b><em>{{ Number(item.value || 0).toFixed(1) }}%</em></span><span v-if="!platformData.creationModes.length" class="platform-empty">暂无项目数据</span></div></div>
        </article>

        <article class="admin-card platform-panel platform-trend-panel">
          <header class="platform-panel-header"><div><h3>近 30 天平台使用趋势</h3><p>聚合统计拓扑、AI 服务和文档导出调用。</p></div><div class="platform-segmented-control"><span class="platform-range-caption">支持 7 天、30 天、90 天</span><button v-for="range in [7, 30, 90]" :key="range" type="button" :class="{ active: trendRange === range }" @click="trendRange = range">{{ range }} 天</button></div></header>
          <div ref="trendChartElement" class="platform-chart platform-trend-chart" aria-label="平台使用趋势折线图"></div>
        </article>
      </section>

      <section class="platform-chart-grid platform-chart-grid-bottom">
        <article class="admin-card platform-panel platform-device-panel">
          <header class="platform-panel-header"><div><h3>常用网络设备</h3><p>从已保存拓扑节点统计设备类型。</p></div><div class="platform-resource-status"><span>系统图标 <b>{{ platformData.resourceStats.total }}</b></span><span class="is-enabled">启用 <b>{{ platformData.resourceStats.enabled }}</b></span><span class="is-disabled">停用 <b>{{ platformData.resourceStats.disabled }}</b></span></div></header>
          <div ref="deviceChartElement" class="platform-chart platform-device-chart" aria-label="常用网络设备横向柱状图"></div>
        </article>

        <article class="admin-card platform-panel platform-ai-panel">
          <header class="platform-panel-header"><div><h3>AI 能力运行情况</h3><p>平台级模型调用健康度。</p></div><span class="platform-ok-label">正常</span></header>
          <div class="platform-ai-table-wrap"><table class="platform-ai-table"><thead><tr><th>功能</th><th>今日调用</th><th>成功率</th><th>平均响应时间</th><th>状态</th></tr></thead><tbody><tr v-for="item in platformData.aiCapabilities" :key="item.key"><td>{{ item.name }}</td><td>{{ Number(item.calls || 0).toLocaleString() }}</td><td>{{ Number(item.successRate || 0).toFixed(1) }}%</td><td>{{ Number(item.averageLatencyMs || 0) }}ms</td><td><span class="platform-status-dot" :class="{ 'is-alert': item.status !== '正常' }"></span>{{ item.status }}</td></tr><tr v-if="!platformData.aiCapabilities.length"><td colspan="5" class="platform-empty">暂无 AI 调用数据</td></tr></tbody></table></div>
        </article>
      </section>
    </div>
  </section>
</template>

<style scoped>
.platform-dashboard { width: min(100%, 1500px); margin: 0 auto; padding: 30px 32px 44px; color: #111827; }
.platform-dashboard-intro { margin-bottom: 22px; }
.platform-dashboard-intro p { color: #6b7280; }
.platform-dashboard-intro h2 { font-size: 24px; }
.platform-dashboard-loading { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 14px; }
.platform-dashboard-loading span { min-height: 104px; border: 1px solid #eef2f7; border-radius: 10px; background: #f8fafc; animation: platform-pulse 1.2s ease-in-out infinite alternate; }
.platform-dashboard-loading span:nth-child(n+7) { grid-column: span 3; min-height: 300px; }
.platform-dashboard-content { display: grid; gap: 18px; }
.platform-metric-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.platform-metric-card { display: grid; grid-template-columns: auto 1fr; align-items: center; column-gap: 10px; min-width: 0; min-height: 112px; padding: 16px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; box-shadow: 0 8px 30px rgba(0,0,0,.04); }
.platform-metric-icon { display: grid; width: 32px; height: 32px; grid-row: span 2; place-items: center; border-radius: 8px; background: #eff6ff; color: #2563eb; }
.platform-metric-card:nth-child(2) .platform-metric-icon { background: #ecfdf5; color: #059669; }
.platform-metric-card:nth-child(3) .platform-metric-icon { background: #f5f3ff; color: #7c3aed; }
.platform-metric-card:nth-child(4) .platform-metric-icon { background: #ecfeff; color: #0891b2; }
.platform-metric-card:nth-child(5) .platform-metric-icon { background: #fffbeb; color: #d97706; }
.platform-metric-label { min-width: 0; overflow: hidden; color: #6b7280; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.platform-metric-card strong { min-width: 0; color: #111827; font-size: 24px; line-height: 1.15; }
.platform-metric-card strong small { margin-left: 2px; font-size: 13px; }
.platform-metric-note { grid-column: 2; color: #9ca3af; font-size: 11px; white-space: nowrap; }
.platform-chart-grid { display: grid; grid-template-columns: minmax(280px, .8fr) minmax(0, 1.6fr); gap: 18px; }
.platform-chart-grid-bottom { grid-template-columns: minmax(0, 1fr) minmax(420px, 1fr); }
.platform-panel { min-width: 0; overflow: hidden; padding: 20px; }
.platform-panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.platform-panel-header h3 { margin: 0; color: #111827; font-size: 16px; font-weight: 700; }
.platform-panel-header p { margin: 4px 0 0; color: #9ca3af; font-size: 12px; }
.platform-chart { width: 100%; min-height: 220px; }
.platform-trend-chart { min-height: 248px; }
.platform-donut-chart { width: 52%; min-height: 206px; }
.platform-creation-body { display: flex; min-height: 220px; align-items: center; gap: 10px; }
.platform-legend { display: grid; flex: 1; gap: 11px; }
.platform-legend span { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 7px; color: #6b7280; font-size: 12px; }
.platform-legend i { width: 8px; height: 8px; border-radius: 50%; }
.platform-legend b { font-weight: 500; }
.platform-legend em { color: #111827; font-style: normal; font-weight: 700; }
.platform-segmented-control { display: inline-flex; padding: 2px; border: 1px solid #e5e7eb; border-radius: 7px; background: #f9fafb; }
.platform-range-caption { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.platform-segmented-control button { min-height: 26px; padding: 0 8px; border: 0; border-radius: 5px; background: transparent; color: #6b7280; font-size: 11px; }
.platform-segmented-control button.active { background: #111827; color: #fff; }
.platform-resource-status { display: flex; flex-wrap: wrap; gap: 9px; color: #6b7280; font-size: 11px; }
.platform-resource-status span { white-space: nowrap; }
.platform-resource-status b { color: #111827; font-size: 13px; }
.platform-resource-status .is-enabled b { color: #059669; }
.platform-resource-status .is-disabled b { color: #dc2626; }
.platform-device-chart { min-height: 266px; }
.platform-ok-label { color: #059669; font-size: 12px; font-weight: 700; }
.platform-ai-table-wrap { overflow-x: auto; }
.platform-ai-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.platform-ai-table th { padding: 8px 6px; border-bottom: 1px solid #e5e7eb; color: #9ca3af; font-size: 11px; font-weight: 600; text-align: left; white-space: nowrap; }
.platform-ai-table td { padding: 10px 6px; border-bottom: 1px solid #f3f4f6; color: #4b5563; white-space: nowrap; }
.platform-ai-table td:first-child { color: #111827; font-weight: 600; }
.platform-status-dot { display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: #10b981; }
.platform-status-dot.is-alert { background: #dc2626; }
.platform-empty { padding: 18px 6px !important; color: #9ca3af !important; text-align: center; }
@keyframes platform-pulse { from { opacity: .6; } to { opacity: 1; } }
:global(html[data-theme='dark']) .platform-dashboard { color: #f4f4f5; }
:global(html[data-theme='dark']) .platform-metric-card, :global(html[data-theme='dark']) .platform-panel { border-color: #27272a; background: #18181b; box-shadow: 0 8px 30px rgba(0,0,0,.18); }
:global(html[data-theme='dark']) .platform-metric-card strong, :global(html[data-theme='dark']) .platform-panel-header h3, :global(html[data-theme='dark']) .platform-legend em, :global(html[data-theme='dark']) .platform-resource-status b, :global(html[data-theme='dark']) .platform-ai-table td:first-child { color: #f4f4f5; }
:global(html[data-theme='dark']) .platform-metric-label, :global(html[data-theme='dark']) .platform-panel-header p, :global(html[data-theme='dark']) .platform-metric-note { color: #a1a1aa; }
:global(html[data-theme='dark']) .platform-ai-table th { border-color: #27272a; color: #a1a1aa; }
:global(html[data-theme='dark']) .platform-ai-table td { border-color: #27272a; color: #d4d4d8; }
@media (max-width: 1280px) { .platform-metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .platform-chart-grid, .platform-chart-grid-bottom { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .platform-dashboard { padding: 20px 14px 30px; } .platform-metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .platform-metric-card { padding: 13px; } .platform-metric-card strong { font-size: 20px; } .platform-panel { padding: 16px; } .platform-panel-header { align-items: flex-start; flex-direction: column; } .platform-creation-body { align-items: stretch; flex-direction: column; } .platform-donut-chart { width: 100%; min-height: 180px; } .platform-legend { grid-template-columns: repeat(3, minmax(0, 1fr)); } .platform-legend span { grid-template-columns: auto 1fr; } .platform-legend em { grid-column: 2; } }
</style>
