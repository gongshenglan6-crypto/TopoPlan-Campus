<script setup>
import { BookOpen, CheckCircle2, ChevronDown, FileText, Search, Sparkles } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getKnowledgeOverview, testKnowledgeRetrieval } from '../../api/admin'

const question = ref('')
const category = ref('all')
const topK = ref(5)
const retrievalMethod = ref('hybrid')
const isRunning = ref(false)
const hasResult = ref(false)
const toast = ref('')
const overview = ref(null)
const retrieval = ref(null)
let toastTimer = 0

const categoryOptions = ['全部知识库', '网络基础', '交换技术', '路由技术', '网络安全', '无线网络', '综合布线', '服务器与存储', '设备技术资料', '网络设计案例', '行业规范']
const categorySelectOptions = categoryOptions.map((item) => ({ value: item === '全部知识库' ? 'all' : item, label: item }))
const topKOptions = [{ value: 3, label: '3' }, { value: 5, label: '5' }, { value: 8, label: '8' }]
const retrievalOptions = [{ value: 'vector', label: '向量检索' }, { value: 'keyword', label: '关键词检索' }, { value: 'hybrid', label: '混合检索' }]
const activeSelect = ref(null)
const results = computed(() => retrieval.value?.results || [])
const selectedResults = computed(() => results.value.slice(0, Math.min(4, results.value.length)))
const contextText = computed(() => retrieval.value?.context || '')
const contextTokens = computed(() => Number(retrieval.value?.contextTokens || 0).toLocaleString())

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2200)
}

function applyExample(example) { question.value = example }
function toggleSelect(name) { activeSelect.value = activeSelect.value === name ? null : name }
function chooseOption(field, value) {
  if (field === 'category') category.value = value
  if (field === 'topK') topK.value = Number(value)
  if (field === 'retrievalMethod') retrievalMethod.value = value
  activeSelect.value = null
}
function closeSelect(event) {
  if (event.target instanceof Element && !event.target.closest('.kb-test-custom-select')) activeSelect.value = null
}
async function runTest() {
  if (!question.value.trim() || isRunning.value) return showToast('请输入测试问题后再开始检索')
  isRunning.value = true
  hasResult.value = false
  try {
    retrieval.value = await testKnowledgeRetrieval({ query: question.value, category: category.value, topK: topK.value, retrievalMethod: retrievalMethod.value })
    hasResult.value = true
    if (!retrieval.value.results?.length) showToast('未检索到匹配的知识块，请上传或重新解析相关资料')
  } catch (error) { showToast(error.message || '检索请求失败') } finally { isRunning.value = false }
}

async function copyContext() {
  try {
    await navigator.clipboard?.writeText(contextText.value)
    showToast('RAG 最终上下文已复制')
  } catch { showToast('复制失败，请手动选择内容') }
}

onMounted(async () => {
  window.addEventListener('pointerdown', closeSelect)
  try { overview.value = await getKnowledgeOverview() } catch (error) { showToast(error.message || '知识分类加载失败') }
})
onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', closeSelect)
  window.clearTimeout(toastTimer)
})
</script>

<template>
  <section class="admin-section-page kb-test-page">
    <header class="admin-page-intro compact kb-test-intro"><div><span>RAG RETRIEVAL QUALITY CHECK</span><h2>检索测试</h2><p>输入测试问题，验证数据库知识库的召回结果及 RAG 检索质量。</p></div><span class="kb-test-privacy"><CheckCircle2 :size="15" /> 仅使用管理员主动输入的问题</span></header>

    <section class="admin-card kb-test-query-card"><header><div><h3>测试问题</h3><span>不会读取、调用或展示任何真实用户项目内容。</span></div><span>当前知识范围：{{ category === 'all' ? '全部知识库' : category }}</span></header><textarea v-model="question" rows="4" placeholder="请输入测试问题……"></textarea><div class="kb-test-examples"><span>示例问题</span><button type="button" @click="applyExample('核心交换机在企业网络中主要承担什么作用？')">核心交换机在企业网络中主要承担什么作用？</button><button type="button" @click="applyExample('三层办公楼网络如何规划 VLAN 隔离？')">三层办公楼网络如何规划 VLAN 隔离？</button></div><footer><div class="kb-test-controls"><label><span>知识分类</span><div class="kb-test-custom-select"><button type="button" :aria-expanded="activeSelect === 'category'" @click.stop="toggleSelect('category')"><span>{{ categorySelectOptions.find((item) => item.value === category)?.label || '全部知识库' }}</span><ChevronDown :size="15" /></button><div v-if="activeSelect === 'category'" class="kb-test-select-menu"><button v-for="option in categorySelectOptions" :key="option.value" type="button" :class="{ selected: category === option.value }" @click="chooseOption('category', option.value)"><span>{{ option.label }}</span><CheckCircle2 v-if="category === option.value" :size="14" /></button></div></div></label><label><span>Top K</span><div class="kb-test-custom-select"><button type="button" :aria-expanded="activeSelect === 'topK'" @click.stop="toggleSelect('topK')"><span>{{ topK }}</span><ChevronDown :size="15" /></button><div v-if="activeSelect === 'topK'" class="kb-test-select-menu"><button v-for="option in topKOptions" :key="option.value" type="button" :class="{ selected: topK === option.value }" @click="chooseOption('topK', option.value)"><span>{{ option.label }}</span><CheckCircle2 v-if="topK === option.value" :size="14" /></button></div></div></label><label><span>检索方式</span><div class="kb-test-custom-select"><button type="button" :aria-expanded="activeSelect === 'retrievalMethod'" @click.stop="toggleSelect('retrievalMethod')"><span>{{ retrievalOptions.find((item) => item.value === retrievalMethod)?.label || '混合检索' }}</span><ChevronDown :size="15" /></button><div v-if="activeSelect === 'retrievalMethod'" class="kb-test-select-menu"><button v-for="option in retrievalOptions" :key="option.value" type="button" :class="{ selected: retrievalMethod === option.value }" @click="chooseOption('retrievalMethod', option.value)"><span>{{ option.label }}</span><CheckCircle2 v-if="retrievalMethod === option.value" :size="14" /></button></div></div></label></div><button type="button" class="admin-primary-button kb-test-run" :disabled="isRunning" @click="runTest"><Search :size="16" /> {{ isRunning ? '正在检索…' : '开始检索' }}</button></footer></section>

    <section v-if="isRunning" class="admin-card kb-test-loading"><Sparkles :size="20" /><div><strong>正在构建检索请求</strong><span>执行 {{ retrievalMethod === 'hybrid' ? '混合检索、重排序与上下文过滤' : retrievalMethod === 'vector' ? '向量相似度检索' : '关键词检索' }}</span></div></section>

    <template v-else-if="hasResult">
      <section class="kb-test-summary"><article><span>检索耗时</span><strong>{{ retrieval.elapsedMs }} <small>ms</small></strong></article><article><span>召回数量</span><strong>{{ retrieval.count }}</strong></article><article><span>最高相似度</span><strong>{{ retrieval.highestScore }}<small>%</small></strong></article><article><span>检索方式</span><strong>{{ retrieval.retrievalMethod === 'hybrid' ? '混合检索' : retrieval.retrievalMethod === 'vector' ? '向量检索' : '关键词检索' }}</strong></article><article><span>Embedding 模型</span><strong>{{ retrieval.embeddingModel }}</strong></article></section>
      <section class="kb-test-results-layout">
        <article class="admin-card kb-results-card"><header><div><span>RETRIEVAL RESULTS</span><h3>召回结果</h3></div><small>按相似度从高到低排列</small></header><div class="kb-result-list"><article v-for="(result, index) in results" :key="result.source" class="kb-result-item"><header><span>结果 #{{ index + 1 }}</span><b>{{ result.score }}%</b></header><div class="kb-result-meta"><span>来源文档：<strong>{{ result.source }}</strong></span><span>知识分类：{{ result.category }}</span><span>Chunk：{{ result.chunk }}</span></div><p>{{ result.content }}</p><footer><button type="button" @click="showToast(`已展开 ${result.source} 的相邻上下文`)">查看上下文</button><span>相似度 {{ result.score }}%</span></footer></article></div></article>
        <article class="admin-card kb-context-card"><header><div><span>MODEL READY CONTEXT</span><h3>RAG 最终上下文</h3></div><FileText :size="18" /></header><div class="kb-context-stats"><span>已选择 <strong>{{ selectedResults.length }} / {{ results.length }}</strong> 个知识块</span><span>上下文长度 <strong>{{ contextTokens }} tokens</strong></span></div><pre>{{ contextText }}</pre><footer><span>仅用于当前管理员检索测试</span><button type="button" class="admin-ghost-button" @click="copyContext"><FileText :size="15" /> 复制上下文</button></footer></article>
      </section>
    </template>

    <section v-else class="admin-card kb-test-empty"><span><Search :size="23" /></span><div><h3>等待开始检索</h3><p>输入一个网络工程问题，即可查看知识库的召回内容、相似度和模型最终上下文。</p></div></section>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.kb-test-page { display:grid; gap:18px; max-width:1500px; margin:0 auto; padding-bottom:28px; }.kb-test-intro { align-items:center; }.kb-test-intro p { margin:7px 0 0; }.kb-test-privacy { display:inline-flex; align-items:center; gap:6px; min-height:31px; padding:0 10px; border:1px solid #bfdbfe; border-radius:999px; background:#eff6ff; color:#1d4ed8; font-size:12px; font-weight:700; }.kb-test-query-card { overflow:hidden; }.kb-test-query-card>header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:17px 18px 0; }.kb-test-query-card h3 { margin:0; color:#1e293b; font-size:16px; font-weight:740; }.kb-test-query-card header span { color:#64748b; font-size:12px; }.kb-test-query-card>textarea { box-sizing:border-box; display:block; width:calc(100% - 36px); margin:16px 18px 10px; padding:12px; border:1px solid #dfe3e8; border-radius:9px; background:#f8fafc; color:#334155; font:inherit; font-size:13px; line-height:1.6; outline:0; resize:vertical; }.kb-test-query-card>textarea:focus { border-color:#2563eb; background:#fff; box-shadow:0 0 0 3px rgba(37,99,235,.1); }.kb-test-examples { display:flex; align-items:center; flex-wrap:wrap; gap:7px; padding:0 18px 16px; }.kb-test-examples>span { color:#94a3b8; font-size:11px; }.kb-test-examples button { padding:4px 8px; border:1px solid #dbeafe; border-radius:999px; background:#f8fbff; color:#2563eb; font-size:11px; }.kb-test-query-card>footer { display:flex; align-items:flex-end; justify-content:space-between; gap:14px; padding:14px 18px; border-top:1px solid #e5e7eb; }.kb-test-controls { display:flex; gap:9px; }.kb-test-controls label { display:grid; gap:5px; }.kb-test-controls label>span { color:#94a3b8; font-size:10px; font-weight:700; }.kb-test-controls select { height:33px; min-width:104px; padding:0 8px; border:1px solid #dfe3e8; border-radius:7px; background:#fff; color:#475569; font-size:12px; outline:0; }.kb-test-run { min-height:36px; }.kb-test-loading { display:flex; align-items:center; gap:12px; padding:20px; border-color:#dbeafe; background:#f8fbff; color:#2563eb; }.kb-test-loading strong,.kb-test-loading span { display:block; }.kb-test-loading strong { color:#1e40af; font-size:13px; }.kb-test-loading span { margin-top:3px; color:#3b82f6; font-size:12px; }.kb-test-summary { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:12px; }.kb-test-summary article { min-height:76px; padding:14px; border:1px solid #e5e7eb; border-radius:10px; background:#fff; }.kb-test-summary span { display:block; color:#64748b; font-size:11px; }.kb-test-summary strong { display:block; margin-top:7px; color:#0f172a; font-size:20px; font-weight:760; line-height:1.1; }.kb-test-summary strong small { font-size:12px; }.kb-test-summary article:nth-child(4) strong,.kb-test-summary article:nth-child(5) strong { font-size:13px; line-height:1.35; }.kb-test-results-layout { display:grid; grid-template-columns:minmax(0,1.16fr) minmax(340px,.84fr); gap:18px; align-items:start; }.kb-results-card,.kb-context-card { overflow:hidden; }.kb-results-card>header,.kb-context-card>header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:17px 18px; border-bottom:1px solid #e5e7eb; }.kb-results-card header span,.kb-context-card header span { color:#94a3b8; font-size:10px; font-weight:750; letter-spacing:.08em; }.kb-results-card h3,.kb-context-card h3 { margin:4px 0 0; color:#1e293b; font-size:16px; font-weight:740; }.kb-results-card header>small { color:#64748b; font-size:11px; }.kb-result-list { display:grid; }.kb-result-item { padding:15px 18px; border-bottom:1px solid #eef2f7; }.kb-result-item:last-child { border-bottom:0; }.kb-result-item>header,.kb-result-item>footer { display:flex; align-items:center; justify-content:space-between; gap:10px; }.kb-result-item>header>span { color:#334155; font-size:12px; font-weight:750; }.kb-result-item>header>b { min-width:43px; padding:4px 7px; border-radius:999px; background:#ecfdf5; color:#047857; font-size:11px; text-align:center; }.kb-result-meta { display:flex; flex-wrap:wrap; gap:7px 12px; margin-top:10px; color:#64748b; font-size:11px; }.kb-result-meta strong { color:#475569; }.kb-result-item>p { margin:11px 0; color:#475569; font-size:12px; line-height:1.7; }.kb-result-item footer button { padding:0; border:0; background:transparent; color:#2563eb; font-size:11px; font-weight:700; }.kb-result-item footer span { color:#94a3b8; font-size:11px; }.kb-context-card>header>svg { color:#2563eb; }.kb-context-stats { display:flex; justify-content:space-between; gap:10px; padding:12px 18px; border-bottom:1px solid #eef2f7; color:#64748b; font-size:11px; }.kb-context-stats strong { color:#334155; }.kb-context-card pre { max-height:480px; margin:0; padding:15px 18px; overflow:auto; background:#fafafa; color:#475569; font:11px/1.75 ui-monospace,SFMono-Regular,Consolas,monospace; white-space:pre-wrap; }.kb-context-card footer { display:flex; align-items:center; justify-content:space-between; gap:10px; padding:12px 18px; border-top:1px solid #e5e7eb; color:#94a3b8; font-size:11px; }.kb-context-card footer button { display:inline-flex; align-items:center; gap:5px; }.kb-test-empty { display:flex; align-items:center; gap:14px; padding:28px; }.kb-test-empty>span { display:grid; place-items:center; width:43px; height:43px; border-radius:10px; background:#eff6ff; color:#2563eb; }.kb-test-empty h3 { margin:0; color:#334155; font-size:14px; }.kb-test-empty p { margin:5px 0 0; color:#64748b; font-size:12px; }:global(html[data-theme='dark']) .kb-test-query-card,:global(html[data-theme='dark']) .kb-results-card,:global(html[data-theme='dark']) .kb-context-card,:global(html[data-theme='dark']) .kb-test-summary article,:global(html[data-theme='dark']) .kb-test-empty { border-color:#27272a; background:#18181b; }:global(html[data-theme='dark']) .kb-test-query-card>header,:global(html[data-theme='dark']) .kb-test-query-card>footer,:global(html[data-theme='dark']) .kb-results-card>header,:global(html[data-theme='dark']) .kb-context-card>header,:global(html[data-theme='dark']) .kb-context-card footer { border-color:#27272a; }:global(html[data-theme='dark']) .kb-test-query-card h3,:global(html[data-theme='dark']) .kb-results-card h3,:global(html[data-theme='dark']) .kb-context-card h3,:global(html[data-theme='dark']) .kb-result-item>header>span,:global(html[data-theme='dark']) .kb-test-summary strong,:global(html[data-theme='dark']) .kb-test-empty h3 { color:#f4f4f5; }:global(html[data-theme='dark']) .kb-test-query-card>textarea,:global(html[data-theme='dark']) .kb-test-controls select { border-color:#3f3f46; background:#27272a; color:#f4f4f5; }:global(html[data-theme='dark']) .kb-test-query-card>textarea:focus { background:#18181b; }:global(html[data-theme='dark']) .kb-test-summary span,:global(html[data-theme='dark']) .kb-result-meta,:global(html[data-theme='dark']) .kb-result-item>p,:global(html[data-theme='dark']) .kb-context-stats,:global(html[data-theme='dark']) .kb-test-empty p { color:#a1a1aa; }:global(html[data-theme='dark']) .kb-result-item,:global(html[data-theme='dark']) .kb-context-stats { border-color:#27272a; }:global(html[data-theme='dark']) .kb-context-card pre { background:#111113; color:#d4d4d8; }:global(html[data-theme='dark']) .kb-test-privacy { border-color:rgba(59,130,246,.35); background:rgba(30,64,175,.2); color:#93c5fd; }@media (max-width:1100px) { .kb-test-summary { grid-template-columns:repeat(3,minmax(0,1fr)); }.kb-test-results-layout { grid-template-columns:1fr; } }@media (max-width:680px) { .kb-test-intro { align-items:flex-start; flex-direction:column; }.kb-test-query-card>footer { align-items:stretch; flex-direction:column; }.kb-test-controls { display:grid; grid-template-columns:1fr 1fr; }.kb-test-controls label:last-child { grid-column:1/-1; }.kb-test-run { width:100%; }.kb-test-summary { grid-template-columns:1fr 1fr; }.kb-test-summary article:last-child { grid-column:1/-1; }.kb-context-stats,.kb-context-card footer { align-items:flex-start; flex-direction:column; } }

.kb-test-query-card { overflow:visible; }
.kb-test-custom-select { position:relative; min-width:104px; }
.kb-test-custom-select>button { display:flex; align-items:center; justify-content:space-between; width:100%; height:36px; padding:0 10px; border:1px solid #dfe3e8; border-radius:8px; background:#fff; color:#374151; font-size:12px; text-align:left; outline:0; }
.kb-test-custom-select>button:hover,.kb-test-custom-select>button[aria-expanded='true'] { border-color:#93c5fd; box-shadow:0 0 0 3px rgba(37,99,235,.08); }
.kb-test-custom-select>button>span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.kb-test-custom-select>button>svg { flex:0 0 auto; color:#64748b; }
.kb-test-select-menu { position:absolute; z-index:60; top:calc(100% + 6px); left:0; right:0; padding:4px; border:1px solid #e5e7eb; border-radius:9px; background:#fff; box-shadow:0 12px 28px rgba(15,23,42,.14); }
.kb-test-select-menu button { display:flex; align-items:center; justify-content:space-between; width:100%; min-height:32px; padding:0 8px; border:0; border-radius:6px; background:transparent; color:#4b5563; font-size:12px; text-align:left; }
.kb-test-select-menu button:hover,.kb-test-select-menu button.selected { background:#eff6ff; color:#1d4ed8; }
:global(html[data-theme='dark']) .kb-test-custom-select>button { border-color:#3f3f46; background:#27272a; color:#f4f4f5; }
:global(html[data-theme='dark']) .kb-test-select-menu { border-color:#3f3f46; background:#27272a; box-shadow:0 12px 28px rgba(0,0,0,.36); }
:global(html[data-theme='dark']) .kb-test-select-menu button { color:#d4d4d8; }
:global(html[data-theme='dark']) .kb-test-select-menu button:hover,:global(html[data-theme='dark']) .kb-test-select-menu button.selected { background:rgba(37,99,235,.18); color:#93c5fd; }

</style>
