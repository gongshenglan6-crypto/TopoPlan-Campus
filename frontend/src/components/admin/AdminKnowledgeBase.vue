<script setup>
import { BookOpen, CheckCircle2, Database, FileText, Sparkles, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { getKnowledgeOverview } from '../../api/admin'

const overview = ref(null)
const loadError = ref('')
const metrics = computed(() => (overview.value?.metrics || []).map((metric, index) => ({ ...metric, icon: [FileText, BookOpen, Database, Sparkles, CheckCircle2][index], tone: ['blue', 'teal', 'violet', 'amber', 'green'][index] })))
const categories = computed(() => {
  const values = overview.value?.categories || []
  const max = Math.max(1, ...values.map((item) => item.count || 0))
  return values.map((item, index) => ({ ...item, percent: Math.max(8, Math.round((item.count / max) * 100)), tone: ['blue', 'indigo', 'cyan', 'rose', 'teal', 'amber', 'violet', 'slate'][index % 8] }))
})
const serviceStatuses = computed(() => overview.value?.services || [])
const activities = computed(() => overview.value?.activities || [])

async function loadOverview() {
  try { overview.value = await getKnowledgeOverview(); loadError.value = '' } catch (error) { loadError.value = error.message || '知识库概览加载失败' }
}

function openDocuments() {
  try {
    window.history.pushState({}, '', '/admin/knowledge-docs')
    window.dispatchEvent(new PopStateEvent('popstate'))
  } catch {}
}

onMounted(loadOverview)
</script>

<template>
  <section class="admin-section-page kb-overview-page">
    <header class="admin-page-intro compact kb-overview-intro">
      <div><span>KNOWLEDGE BASE OVERVIEW</span><h2>网络工程知识库</h2><p>查看网络工程专业知识资源、向量索引及 RAG 服务整体运行情况。</p></div>
      <button type="button" class="admin-primary-button kb-overview-action" @click="openDocuments"><FileText :size="16" /> 进入文档管理</button>
    </header>

    <p v-if="loadError" class="admin-toast" role="status">{{ loadError }}</p>
    <section v-if="overview" class="kb-overview-metrics" aria-label="知识库整体指标">
      <article v-for="metric in metrics" :key="metric.label" class="kb-overview-metric" :class="`is-${metric.tone}`"><span><component :is="metric.icon" :size="18" /></span><div><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong><p>{{ metric.detail }}</p></div></article>
    </section>

    <section v-if="overview" class="kb-overview-grid">
      <article class="admin-card kb-overview-card kb-category-card">
        <header><div><span>KNOWLEDGE DISTRIBUTION</span><h3>知识分类分布</h3></div><small>326 份文档</small></header>
        <div class="kb-category-list"><div v-for="category in categories" :key="category.name" class="kb-category-row"><div><span>{{ category.name }}</span><small>{{ category.count }} 份</small></div><span class="kb-category-track"><i :class="`is-${category.tone}`" :style="{ width: `${category.percent}%` }"></i></span></div></div>
      </article>

      <article class="admin-card kb-overview-card kb-service-card">
        <header><div><span>SERVICE HEALTH</span><h3>知识库运行状态</h3></div><CheckCircle2 :size="19" /></header>
        <div class="kb-service-list"><div v-for="service in serviceStatuses" :key="service.name" class="kb-service-row"><span class="kb-service-dot"></span><div><strong>{{ service.name }}</strong><small>{{ service.detail }}</small></div><b>{{ service.status }}</b></div></div>
      </article>
    </section>

    <section v-if="overview" class="admin-card kb-overview-card kb-activity-card">
      <header><div><span>RECENT ACTIVITY</span><h3>最近知识库活动</h3></div><small>仅展示平台知识资源变更</small></header>
      <div class="kb-activity-list"><article v-for="activity in activities" :key="`${activity.name}-${activity.time}`"><span :class="`is-${activity.type}`"><CheckCircle2 v-if="activity.type === 'success'" :size="15" /><TriangleAlert v-else-if="activity.type === 'warning'" :size="15" /><BookOpen v-else :size="15" /></span><div><strong>{{ activity.name }}</strong><p>{{ activity.action }}</p></div><time>{{ activity.time }}</time></article><p v-if="!activities.length" class="kb-empty-activity">暂无文档活动，上传知识文档后会显示真实处理记录。</p></div>
    </section>
  </section>
</template>

<style scoped>
.kb-overview-page { display:grid; gap:18px; max-width:1500px; margin:0 auto; padding-bottom:28px; }.kb-overview-intro { align-items:center; }.kb-overview-intro p { margin:7px 0 0; }.kb-overview-action { min-height:40px; }.kb-overview-metrics { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:12px; }.kb-overview-metric { display:flex; gap:11px; min-width:0; padding:15px; border:1px solid #e5e7eb; border-radius:12px; background:#fff; }.kb-overview-metric>span { display:grid; flex:0 0 auto; place-items:center; width:34px; height:34px; border-radius:9px; background:#eff6ff; color:#2563eb; }.kb-overview-metric.is-teal>span { background:#f0fdfa; color:#0f766e; }.kb-overview-metric.is-violet>span { background:#f5f3ff; color:#7c3aed; }.kb-overview-metric.is-amber>span { background:#fffbeb; color:#b45309; }.kb-overview-metric.is-green>span { background:#ecfdf5; color:#047857; }.kb-overview-metric div { min-width:0; }.kb-overview-metric small,.kb-overview-metric p { display:block; margin:0; color:#64748b; font-size:11px; line-height:1.4; }.kb-overview-metric strong { display:block; margin:3px 0; color:#0f172a; font-size:22px; font-weight:760; line-height:1.05; }.kb-overview-grid { display:grid; grid-template-columns:minmax(0,1.25fr) minmax(360px,.75fr); gap:18px; }.kb-overview-card { overflow:hidden; }.kb-overview-card>header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:17px 18px; border-bottom:1px solid #e5e7eb; }.kb-overview-card header>div>span { color:#94a3b8; font-size:10px; font-weight:750; letter-spacing:.08em; }.kb-overview-card h3 { margin:4px 0 0; color:#1e293b; font-size:16px; font-weight:740; }.kb-overview-card header>small { color:#64748b; font-size:12px; }.kb-category-list { display:grid; gap:13px; padding:17px 18px 19px; }.kb-category-row>div { display:flex; justify-content:space-between; gap:14px; margin-bottom:6px; color:#334155; font-size:12px; font-weight:650; }.kb-category-row small { color:#94a3b8; font-size:11px; font-weight:600; }.kb-category-track { display:block; height:7px; overflow:hidden; border-radius:999px; background:#f1f5f9; }.kb-category-track i { display:block; height:100%; border-radius:inherit; background:#3b82f6; }.kb-category-track i.is-indigo { background:#6366f1; }.kb-category-track i.is-cyan { background:#06b6d4; }.kb-category-track i.is-rose { background:#f43f5e; }.kb-category-track i.is-teal { background:#14b8a6; }.kb-category-track i.is-amber { background:#f59e0b; }.kb-category-track i.is-violet { background:#8b5cf6; }.kb-category-track i.is-slate { background:#94a3b8; }.kb-service-card>header>svg { color:#16a34a; }.kb-service-list { display:grid; }.kb-service-row { display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:10px; padding:14px 18px; border-bottom:1px solid #f1f5f9; }.kb-service-row:last-child { border-bottom:0; }.kb-service-dot { width:8px; height:8px; border-radius:50%; background:#22c55e; box-shadow:0 0 0 4px #f0fdf4; }.kb-service-row strong,.kb-service-row small { display:block; }.kb-service-row strong { color:#334155; font-size:12px; font-weight:700; }.kb-service-row small { margin-top:3px; color:#94a3b8; font-size:11px; }.kb-service-row b { color:#15803d; font-size:11px; }.kb-activity-list { display:grid; }.kb-activity-list article { display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:11px; padding:13px 18px; border-bottom:1px solid #f1f5f9; }.kb-activity-list article:last-child { border-bottom:0; }.kb-activity-list article>span { display:grid; place-items:center; width:29px; height:29px; border-radius:50%; background:#ecfdf5; color:#059669; }.kb-activity-list article>span.is-warning { background:#fffbeb; color:#d97706; }.kb-activity-list article>span.is-neutral { background:#eff6ff; color:#2563eb; }.kb-activity-list strong { display:block; overflow:hidden; color:#334155; font-size:12px; font-weight:700; text-overflow:ellipsis; white-space:nowrap; }.kb-activity-list p { margin:3px 0 0; color:#64748b; font-size:11px; }.kb-activity-list time { color:#94a3b8; font-size:11px; white-space:nowrap; }:global(html[data-theme='dark']) .kb-overview-metric,:global(html[data-theme='dark']) .kb-overview-card { border-color:#27272a; background:#18181b; }:global(html[data-theme='dark']) .kb-overview-metric strong,:global(html[data-theme='dark']) .kb-overview-card h3,:global(html[data-theme='dark']) .kb-service-row strong,:global(html[data-theme='dark']) .kb-activity-list strong { color:#f4f4f5; }:global(html[data-theme='dark']) .kb-overview-metric small,:global(html[data-theme='dark']) .kb-overview-metric p,:global(html[data-theme='dark']) .kb-overview-card header>small,:global(html[data-theme='dark']) .kb-service-row small,:global(html[data-theme='dark']) .kb-activity-list p { color:#a1a1aa; }:global(html[data-theme='dark']) .kb-overview-card>header,:global(html[data-theme='dark']) .kb-service-row,:global(html[data-theme='dark']) .kb-activity-list article { border-color:#27272a; }:global(html[data-theme='dark']) .kb-category-row>div { color:#d4d4d8; }:global(html[data-theme='dark']) .kb-category-track { background:#27272a; }@media (max-width:1180px) { .kb-overview-metrics { grid-template-columns:repeat(3,minmax(0,1fr)); }.kb-overview-grid { grid-template-columns:1fr; } }@media (max-width:680px) { .kb-overview-intro { align-items:flex-start; flex-direction:column; }.kb-overview-metrics { grid-template-columns:1fr; }.kb-overview-action { width:100%; }.kb-activity-list time { display:none; } }
</style>
