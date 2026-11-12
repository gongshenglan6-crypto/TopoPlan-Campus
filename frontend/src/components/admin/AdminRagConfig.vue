<script setup>
import { CheckCircle2, ChevronDown, Database, SlidersHorizontal } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getKnowledgeRagConfig, listModelChannels, saveKnowledgeRagConfig } from '../../api/admin'

const defaults = {
  embeddingModel: 'Text Embedding 3 Large',
  vectorDatabase: 'Vector Store',
  retrievalMethod: 'hybrid',
  topK: 5,
  threshold: 0.72,
  maxRecall: 10,
  rerankEnabled: false,
  rerankModel: '',
  maxContext: '16K',
  maxCitations: 6,
  deduplicate: true,
  neighborExpansion: true,
  lowQualityFilter: true,
  noKnowledgeAction: 'notify',
  chunkSize: 800,
  chunkOverlap: 120,
  splitMethod: 'auto'
}

const config = reactive({ ...defaults })
const saveState = ref('idle')
const activeSelect = ref(null)

const embeddingModels = ref([])
const embeddingModelsLoading = ref(true)
const embeddingModelsError = ref(false)
const rerankModels = ref([])
const rerankModelsLoading = ref(true)
const rerankModelsError = ref(false)
const contextOptions = ['8K', '16K', '32K']
const retrievalMethods = [
  { value: 'vector', label: '向量检索' },
  { value: 'keyword', label: '关键词检索' },
  { value: 'hybrid', label: '混合检索' }
]
const splitMethods = [
  { value: 'auto', label: '自动' },
  { value: 'heading', label: '按标题' },
  { value: 'paragraph', label: '按段落' }
]
const fallbackOptions = [
  { value: 'model', label: '使用模型直接回答', detail: '不附加知识库引用，直接生成通用回答。' },
  { value: 'notify', label: '提示未检索到可靠资料', detail: '明确告知当前知识库中没有足够依据。' },
  { value: 'search', label: '自动启动联网搜索', detail: '按平台策略补充可信公开资料。' }
]
const strategyLabel = computed(() => retrievalMethods.find((item) => item.value === config.retrievalMethod)?.label || '混合检索')
const summary = computed(() => `${strategyLabel.value} · Top K ${config.topK} · ${config.rerankEnabled ? 'Rerank 开启' : 'Rerank 关闭'} · ${config.maxContext} 上下文 · Chunk ${config.chunkSize}`)
const selectedEmbeddingModel = computed(() => embeddingModels.value.find((model) => model.value === config.embeddingModel))
const embeddingModelLabel = computed(() => selectedEmbeddingModel.value?.label || config.embeddingModel || '未配置 Embedding 模型')
const selectedRerankModel = computed(() => rerankModels.value.find((model) => model.value === config.rerankModel))
const rerankModelLabel = computed(() => selectedRerankModel.value?.label || config.rerankModel || '暂无可用 Rerank 模型')

function normalizeEmbeddingModels(channels) {
  return (Array.isArray(channels) ? channels : [])
    .filter((channel) => String(channel.modelType || '').toLowerCase() === 'embedding')
    .filter((channel) => channel.enabled !== false && String(channel.status || 'ACTIVE').toUpperCase() === 'ACTIVE')
    .map((channel) => {
      const modelId = String(channel.modelId || channel.id || '').trim()
      const alias = String(channel.alias || channel.displayName || modelId).trim()
      const provider = String(channel.provider || channel.providerName || '').trim()
      const modelName = alias && alias !== modelId ? `${alias} · ${modelId}` : modelId
      return {
        value: modelId,
        modelId,
        label: provider ? `${modelName} · ${provider}` : modelName
      }
    })
    .filter((model) => model.value)
}

function resolveEmbeddingModel(value) {
  const requested = String(value || '').trim()
  return embeddingModels.value.find((model) => model.value === requested || model.label === requested)?.value || embeddingModels.value[0]?.value || requested
}

async function loadEmbeddingModels() {
  embeddingModelsLoading.value = true
  embeddingModelsError.value = false
  try {
    embeddingModels.value = normalizeEmbeddingModels(await listModelChannels())
    config.embeddingModel = resolveEmbeddingModel(config.embeddingModel)
  } catch {
    embeddingModels.value = []
    embeddingModelsError.value = true
  } finally {
    embeddingModelsLoading.value = false
  }
}

async function loadRerankModels() {
  rerankModelsLoading.value = true
  rerankModelsError.value = false
  try {
    const channels = await listModelChannels()
    rerankModels.value = (Array.isArray(channels) ? channels : [])
      .filter((channel) => String(channel.modelType || '').toLowerCase() === 'rerank')
      .filter((channel) => channel.enabled !== false && String(channel.status || 'ACTIVE').toUpperCase() === 'ACTIVE')
      .map((channel) => {
        const modelId = String(channel.modelId || channel.id || '').trim()
        const alias = String(channel.alias || channel.displayName || modelId).trim()
        const provider = String(channel.provider || channel.providerName || '').trim()
        const modelName = alias && alias !== modelId ? `${alias} · ${modelId}` : modelId
        return { value: modelId, label: provider ? `${modelName} · ${provider}` : modelName }
      })
      .filter((model) => model.value)
    config.rerankModel = rerankModels.value.find((model) => model.value === config.rerankModel)?.value || rerankModels.value[0]?.value || ''
    if (!rerankModels.value.length) config.rerankEnabled = false
  } catch {
    rerankModels.value = []
    rerankModelsError.value = true
    config.rerankEnabled = false
  } finally {
    rerankModelsLoading.value = false
  }
}

function toggleSelect(name) {
  activeSelect.value = activeSelect.value === name ? null : name
}

function chooseOption(field, value) {
  config[field] = value
  activeSelect.value = null
}

function closeSelect(event) {
  if (event.target instanceof Element && !event.target.closest('.rag-custom-select')) activeSelect.value = null
}

function applyConfig(values) {
  Object.keys(defaults).forEach((key) => {
    if (values?.[key] !== undefined) config[key] = values[key]
  })
  if (values?.embeddingModel !== undefined) config.embeddingModel = resolveEmbeddingModel(values.embeddingModel)
}

function restoreDefaults() {
  Object.assign(config, defaults)
  activeSelect.value = null
  saveState.value = 'restored'
  window.setTimeout(() => { if (saveState.value === 'restored') saveState.value = 'idle' }, 1800)
}

async function saveConfig() {
  activeSelect.value = null
  try {
    applyConfig(await saveKnowledgeRagConfig({ ...config }))
    saveState.value = 'saved'
    window.setTimeout(() => { if (saveState.value === 'saved') saveState.value = 'idle' }, 2200)
  } catch {
    saveState.value = 'failed'
  }
}

onMounted(async () => {
  window.addEventListener('pointerdown', closeSelect)
  try {
    applyConfig(await getKnowledgeRagConfig())
    await Promise.all([loadEmbeddingModels(), loadRerankModels()])
  } catch { saveState.value = 'failed' }
})
onBeforeUnmount(() => window.removeEventListener('pointerdown', closeSelect))
</script>

<template>
  <section class="admin-section-page rag-config-page">
    <header class="rag-page-header">
      <div><h2>RAG 配置</h2><p>配置知识检索、召回、重排序与上下文构建策略。</p></div>
      <span class="rag-health"><i></i><CheckCircle2 :size="14" /> RAG 服务正常</span>
    </header>

    <section class="rag-config-grid">
      <article class="rag-panel rag-retrieval-panel">
        <header><div><h3>检索策略</h3><p>配置知识库召回方式与基础检索参数。</p></div><span class="rag-panel-icon"><Database :size="17" /></span></header>
        <div class="rag-panel-body">
          <label class="rag-field"><span>Embedding 模型</span><div class="rag-custom-select"><button type="button" :disabled="embeddingModelsLoading || !embeddingModels.length" :aria-expanded="activeSelect === 'embedding'" @click.stop="toggleSelect('embedding')"><span>{{ embeddingModelsLoading ? '正在读取模型配置…' : embeddingModels.length ? embeddingModelLabel : '暂无可用 Embedding 模型' }}</span><ChevronDown :size="15" /></button><div v-if="activeSelect === 'embedding' && embeddingModels.length" class="rag-select-menu"><button v-for="model in embeddingModels" :key="model.value" type="button" :class="{ selected: config.embeddingModel === model.value }" @click="chooseOption('embeddingModel', model.value)"><span>{{ model.label }}</span><CheckCircle2 v-if="config.embeddingModel === model.value" :size="14" /></button></div><small v-if="embeddingModelsError" class="rag-field-error">模型列表读取失败，请确认 AI 模型服务正常。</small><small v-else-if="!embeddingModelsLoading && !embeddingModels.length" class="rag-field-error">请先在 AI 引擎中添加并启用 Embedding 模型。</small></div></label>
          <label class="rag-field"><span>向量数据库</span><output class="rag-static-field">{{ config.vectorDatabase }}</output></label>
          <div class="rag-field"><span>检索方式</span><div class="rag-segmented" role="group" aria-label="检索方式"><button v-for="method in retrievalMethods" :key="method.value" type="button" :class="{ active: config.retrievalMethod === method.value }" @click="config.retrievalMethod = method.value">{{ method.label }}</button></div></div>
          <div class="rag-number-grid"><label><span>Top K</span><input v-model.number="config.topK" type="number" min="1" max="20" /></label><label><span>相似度阈值</span><input v-model.number="config.threshold" type="number" min="0" max="1" step="0.01" /></label><label><span>最大召回数量</span><input v-model.number="config.maxRecall" type="number" min="1" max="30" /></label></div>
        </div>
      </article>

      <article class="rag-panel rag-context-panel">
        <header><div><h3>上下文与重排</h3><p>控制召回排序、过滤与最终上下文构建。</p></div><span class="rag-panel-icon is-violet"><SlidersHorizontal :size="17" /></span></header>
        <div class="rag-panel-body">
          <div class="rag-switch-line"><div><strong>启用 Rerank</strong><small>{{ rerankModels.length ? '对首轮召回结果进行相关性精排。' : '请先在 AI 引擎中添加真实 Rerank 模型。' }}</small></div><button type="button" class="rag-switch" :class="{ active: config.rerankEnabled }" :disabled="!rerankModels.length" :aria-pressed="config.rerankEnabled" @click="config.rerankEnabled = !config.rerankEnabled"><i></i></button></div>
          <template v-if="config.rerankEnabled"><label class="rag-field"><span>Rerank 模型</span><div class="rag-custom-select"><button type="button" :disabled="rerankModelsLoading || !rerankModels.length" :aria-expanded="activeSelect === 'rerank'" @click.stop="toggleSelect('rerank')"><span>{{ rerankModelsLoading ? '正在读取模型配置…' : rerankModelLabel }}</span><ChevronDown :size="15" /></button><div v-if="activeSelect === 'rerank' && rerankModels.length" class="rag-select-menu"><button v-for="model in rerankModels" :key="model.value" type="button" :class="{ selected: config.rerankModel === model.value }" @click="chooseOption('rerankModel', model.value)"><span>{{ model.label }}</span><CheckCircle2 v-if="config.rerankModel === model.value" :size="14" /></button></div><small v-if="rerankModelsError" class="rag-field-error">模型列表读取失败，请确认 AI 模型服务正常。</small><small v-else-if="!rerankModelsLoading && !rerankModels.length" class="rag-field-error">当前没有真实可用的 Rerank 模型。</small></div></label><div class="rag-two-field-grid"><label class="rag-field"><span>最大上下文</span><div class="rag-custom-select"><button type="button" :aria-expanded="activeSelect === 'context'" @click.stop="toggleSelect('context')"><span>{{ config.maxContext }}</span><ChevronDown :size="15" /></button><div v-if="activeSelect === 'context'" class="rag-select-menu"><button v-for="option in contextOptions" :key="option" type="button" :class="{ selected: config.maxContext === option }" @click="chooseOption('maxContext', option)"><span>{{ option }}</span><CheckCircle2 v-if="config.maxContext === option" :size="14" /></button></div></div></label><label class="rag-field"><span>最大引用知识块</span><input v-model.number="config.maxCitations" type="number" min="1" max="12" /></label></div></template>
          <div class="rag-compact-switches"><div v-for="item in [{ key: 'deduplicate', label: '去重策略', detail: '合并高度重复片段' }, { key: 'neighborExpansion', label: '知识块邻接扩展', detail: '补充相邻段落语义' }, { key: 'lowQualityFilter', label: '低质量召回过滤', detail: '过滤弱相关内容' }]" :key="item.key"><div><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></div><button type="button" class="rag-switch" :class="{ active: config[item.key] }" :aria-pressed="config[item.key]" @click="config[item.key] = !config[item.key]"><i></i></button></div></div>
          <div class="rag-fallback"><span>无有效知识时</span><div><button v-for="option in fallbackOptions" :key="option.value" type="button" :class="{ active: config.noKnowledgeAction === option.value }" @click="config.noKnowledgeAction = option.value"><i></i><strong>{{ option.label }}</strong><small>{{ option.detail }}</small></button></div></div>
        </div>
      </article>

      <article class="rag-panel rag-chunk-panel">
        <header><div><h3>文档分块</h3><p>配置新文档解析和向量化时的知识块切分规则。</p></div><span class="rag-panel-icon is-slate"><Database :size="17" /></span></header>
        <div class="rag-panel-body">
          <label class="rag-slider-field"><span>默认 Chunk 大小</span><div><input v-model.number="config.chunkSize" type="range" min="200" max="1600" step="100" :style="{ '--fill': `${((config.chunkSize - 200) / 1400) * 100}%` }" /><input v-model.number="config.chunkSize" type="number" min="200" max="1600" step="100" /><output>Tokens</output></div></label>
          <label class="rag-slider-field"><span>Chunk 重叠</span><div><input v-model.number="config.chunkOverlap" type="range" min="0" max="400" step="20" :style="{ '--fill': `${(config.chunkOverlap / 400) * 100}%` }" /><input v-model.number="config.chunkOverlap" type="number" min="0" max="400" step="20" /><output>Tokens</output></div></label>
          <div class="rag-field"><span>分段方式</span><div class="rag-segmented" role="group" aria-label="分段方式"><button v-for="method in splitMethods" :key="method.value" type="button" :class="{ active: config.splitMethod === method.value }" @click="config.splitMethod = method.value">{{ method.label }}</button></div></div>
          <p class="rag-chunk-note">当前设置仅影响新上传或重新解析的知识文档。已有文档需要重新解析后才会应用新的 Chunk 配置。</p>
        </div>
      </article>
    </section>

    <footer class="rag-action-bar"><div><span v-if="saveState === 'saved'" class="rag-save-message"><CheckCircle2 :size="15" /> RAG 配置已保存</span><span v-else-if="saveState === 'restored'" class="rag-save-message">已恢复默认配置，保存后写入数据库</span><span v-else-if="saveState === 'failed'" class="rag-save-error">配置同步失败，请确认后端服务正常</span><span v-else>当前策略：{{ summary }}</span></div><div><button type="button" class="admin-ghost-button" @click="restoreDefaults">恢复默认</button><button type="button" class="admin-primary-button" @click="saveConfig">保存配置</button></div></footer>
  </section>
</template>

<style scoped>
.rag-config-page { display:grid; grid-template-rows:auto minmax(0,1fr) auto; gap:14px; height:calc(100dvh - 122px); max-width:1500px; margin:0 auto; overflow:hidden; }.rag-page-header { display:flex; align-items:center; justify-content:space-between; gap:18px; min-height:58px; }.rag-page-header h2 { margin:0; color:#111827; font-size:24px; font-weight:700; letter-spacing:-.02em; }.rag-page-header p { margin:6px 0 0; color:#6b7280; font-size:13px; }.rag-health { display:inline-flex; align-items:center; gap:5px; min-height:29px; padding:0 10px; border:1px solid #bbf7d0; border-radius:999px; background:#f0fdf4; color:#15803d; font-size:12px; font-weight:650; white-space:nowrap; }.rag-health>i { width:6px; height:6px; border-radius:50%; background:#22c55e; }.rag-config-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; min-height:0; }.rag-panel { display:flex; min-width:0; min-height:0; flex-direction:column; border:1px solid #e5e7eb; border-radius:11px; background:#fff; overflow:visible; }.rag-panel>header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:16px 16px 13px; border-bottom:1px solid #f1f5f9; }.rag-panel h3 { margin:0; color:#1f2937; font-size:16px; font-weight:650; }.rag-panel header p { margin:4px 0 0; color:#6b7280; font-size:12px; line-height:1.45; }.rag-panel-icon { display:grid; flex:0 0 auto; place-items:center; width:30px; height:30px; border-radius:8px; background:#eff6ff; color:#2563eb; }.rag-panel-icon.is-violet { background:#f5f3ff; color:#7c3aed; }.rag-panel-icon.is-slate { background:#f1f5f9; color:#475569; }.rag-panel-body { display:grid; align-content:start; gap:12px; min-height:0; padding:14px 16px 16px; }.rag-field,.rag-number-grid>label,.rag-two-field-grid>label { display:grid; gap:6px; min-width:0; }.rag-field>span,.rag-number-grid span,.rag-two-field-grid span,.rag-slider-field>span { color:#374151; font-size:13px; font-weight:650; }.rag-static-field,.rag-field>input,.rag-number-grid input,.rag-two-field-grid input { box-sizing:border-box; width:100%; height:36px; padding:0 10px; border:1px solid #dfe3e8; border-radius:8px; background:#f9fafb; color:#374151; font-size:12px; outline:0; }.rag-field>input,.rag-number-grid input,.rag-two-field-grid input { background:#fff; }.rag-field>input:focus,.rag-number-grid input:focus,.rag-two-field-grid input:focus,.rag-slider-field input[type='number']:focus { border-color:#60a5fa; box-shadow:0 0 0 3px rgba(37,99,235,.1); }.rag-static-field { display:flex; align-items:center; }.rag-custom-select { position:relative; }.rag-custom-select>button { display:flex; align-items:center; justify-content:space-between; width:100%; height:36px; padding:0 10px; border:1px solid #dfe3e8; border-radius:8px; background:#fff; color:#374151; font-size:12px; text-align:left; }.rag-custom-select>button:hover,.rag-custom-select>button[aria-expanded='true'] { border-color:#93c5fd; }.rag-custom-select>button>span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.rag-custom-select>button>svg { flex:0 0 auto; color:#64748b; }.rag-select-menu { position:absolute; z-index:20; top:calc(100% + 6px); left:0; right:0; padding:4px; border:1px solid #e5e7eb; border-radius:9px; background:#fff; box-shadow:0 12px 28px rgba(15,23,42,.14); }.rag-select-menu button { display:flex; align-items:center; justify-content:space-between; width:100%; min-height:32px; padding:0 8px; border:0; border-radius:6px; background:transparent; color:#4b5563; font-size:12px; text-align:left; }.rag-select-menu button:hover,.rag-select-menu button.selected { background:#eff6ff; color:#1d4ed8; }.rag-number-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; }.rag-two-field-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px; }.rag-segmented { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:6px; }.rag-segmented button { min-height:34px; padding:0 6px; border:1px solid #e5e7eb; border-radius:7px; background:#fff; color:#4b5563; font-size:12px; font-weight:600; white-space:nowrap; }.rag-segmented button.active { border-color:#60a5fa; background:#eff6ff; color:#1d4ed8; }.rag-switch-line,.rag-compact-switches>div { display:flex; align-items:center; justify-content:space-between; gap:12px; }.rag-switch-line strong,.rag-compact-switches strong { display:block; color:#374151; font-size:13px; font-weight:650; }.rag-switch-line small,.rag-compact-switches small { display:block; margin-top:2px; color:#6b7280; font-size:11px; }.rag-switch { position:relative; flex:0 0 auto; width:36px; height:21px; padding:0; border:0; border-radius:999px; background:#d1d5db; transition:background 160ms ease; }.rag-switch i { position:absolute; top:3px; left:3px; width:15px; height:15px; border-radius:50%; background:#fff; box-shadow:0 1px 2px rgba(15,23,42,.2); transition:transform 160ms ease; }.rag-switch.active { background:#2563eb; }.rag-switch.active i { transform:translateX(15px); }.rag-compact-switches { display:grid; gap:0; border-top:1px solid #f1f5f9; }.rag-compact-switches>div { min-height:42px; border-bottom:1px solid #f1f5f9; }.rag-fallback { display:grid; gap:7px; }.rag-fallback>span { color:#374151; font-size:13px; font-weight:650; }.rag-fallback>div { display:grid; gap:6px; }.rag-fallback button { position:relative; display:grid; grid-template-columns:auto minmax(0,1fr); column-gap:7px; align-items:center; min-height:42px; padding:6px 8px; border:1px solid transparent; border-radius:8px; background:#f9fafb; text-align:left; }.rag-fallback button>i { grid-row:span 2; width:11px; height:11px; border:1px solid #cbd5e1; border-radius:50%; }.rag-fallback button.active { border-color:#bfdbfe; background:#eff6ff; }.rag-fallback button.active>i { border:4px solid #2563eb; }.rag-fallback button strong { color:#374151; font-size:12px; font-weight:650; }.rag-fallback button small { overflow:hidden; color:#6b7280; font-size:10px; text-overflow:ellipsis; white-space:nowrap; }.rag-slider-field { display:grid; gap:7px; }.rag-slider-field>div { display:grid; grid-template-columns:minmax(0,1fr) 58px auto; align-items:center; gap:7px; }.rag-slider-field input[type='range'] { width:100%; height:5px; margin:0; border-radius:999px; appearance:none; background:linear-gradient(to right,#2563eb 0%,#2563eb var(--fill),#dbe3ee var(--fill),#dbe3ee 100%); }.rag-slider-field input[type='range']::-webkit-slider-thumb { width:14px; height:14px; border:2px solid #fff; border-radius:50%; appearance:none; background:#2563eb; box-shadow:0 1px 3px rgba(15,23,42,.25); }.rag-slider-field input[type='range']::-moz-range-thumb { width:12px; height:12px; border:2px solid #fff; border-radius:50%; background:#2563eb; box-shadow:0 1px 3px rgba(15,23,42,.25); }.rag-slider-field input[type='number'] { box-sizing:border-box; width:58px; height:33px; padding:0 6px; border:1px solid #dfe3e8; border-radius:7px; color:#374151; font-size:12px; text-align:center; outline:0; }.rag-slider-field output { color:#6b7280; font-size:10px; font-weight:650; }.rag-chunk-note { margin:1px 0 0; padding-top:10px; border-top:1px solid #f1f5f9; color:#6b7280; font-size:11px; line-height:1.55; }.rag-action-bar { display:flex; align-items:center; justify-content:space-between; gap:14px; min-height:45px; color:#6b7280; font-size:12px; }.rag-action-bar>div:last-child { display:flex; gap:8px; }.rag-action-bar .admin-ghost-button,.rag-action-bar .admin-primary-button { min-height:34px; }.rag-action-bar .admin-primary-button { border-color:#111827; background:#111827; }.rag-save-message { display:inline-flex; align-items:center; gap:6px; color:#15803d; font-weight:650; }:global(html[data-theme='dark']) .rag-panel { border-color:#27272a; background:#18181b; }:global(html[data-theme='dark']) .rag-panel>header,:global(html[data-theme='dark']) .rag-compact-switches,:global(html[data-theme='dark']) .rag-compact-switches>div,:global(html[data-theme='dark']) .rag-chunk-note { border-color:#27272a; }:global(html[data-theme='dark']) .rag-page-header h2,:global(html[data-theme='dark']) .rag-panel h3,:global(html[data-theme='dark']) .rag-switch-line strong,:global(html[data-theme='dark']) .rag-compact-switches strong,:global(html[data-theme='dark']) .rag-field>span,:global(html[data-theme='dark']) .rag-number-grid span,:global(html[data-theme='dark']) .rag-two-field-grid span,:global(html[data-theme='dark']) .rag-slider-field>span,:global(html[data-theme='dark']) .rag-fallback>span,:global(html[data-theme='dark']) .rag-fallback button strong { color:#f4f4f5; }:global(html[data-theme='dark']) .rag-page-header p,:global(html[data-theme='dark']) .rag-panel header p,:global(html[data-theme='dark']) .rag-switch-line small,:global(html[data-theme='dark']) .rag-compact-switches small,:global(html[data-theme='dark']) .rag-fallback button small,:global(html[data-theme='dark']) .rag-chunk-note,:global(html[data-theme='dark']) .rag-action-bar { color:#a1a1aa; }:global(html[data-theme='dark']) .rag-static-field,:global(html[data-theme='dark']) .rag-field>input,:global(html[data-theme='dark']) .rag-number-grid input,:global(html[data-theme='dark']) .rag-two-field-grid input,:global(html[data-theme='dark']) .rag-custom-select>button,:global(html[data-theme='dark']) .rag-slider-field input[type='number'] { border-color:#3f3f46; background:#27272a; color:#f4f4f5; }:global(html[data-theme='dark']) .rag-select-menu { border-color:#3f3f46; background:#27272a; box-shadow:0 12px 28px rgba(0,0,0,.36); }:global(html[data-theme='dark']) .rag-select-menu button { color:#d4d4d8; }:global(html[data-theme='dark']) .rag-select-menu button:hover,:global(html[data-theme='dark']) .rag-select-menu button.selected,:global(html[data-theme='dark']) .rag-segmented button.active,:global(html[data-theme='dark']) .rag-fallback button.active { background:rgba(37,99,235,.18); color:#93c5fd; }:global(html[data-theme='dark']) .rag-segmented button,:global(html[data-theme='dark']) .rag-fallback button { border-color:#3f3f46; background:#27272a; color:#d4d4d8; }:global(html[data-theme='dark']) .rag-slider-field input[type='range'] { background:linear-gradient(to right,#3b82f6 0%,#3b82f6 var(--fill),#3f3f46 var(--fill),#3f3f46 100%); }:global(html[data-theme='dark']) .rag-action-bar .admin-primary-button { border-color:#f4f4f5; background:#f4f4f5; color:#18181b; }@media (max-width:1180px) { .rag-config-page { height:auto; overflow:visible; }.rag-config-grid { grid-template-columns:1fr; }.rag-panel { overflow:visible; }.rag-action-bar { padding-bottom:20px; } }@media (max-width:680px) { .rag-page-header,.rag-action-bar { align-items:flex-start; flex-direction:column; }.rag-health { align-self:flex-start; }.rag-number-grid,.rag-two-field-grid { grid-template-columns:1fr; }.rag-action-bar>div:last-child { width:100%; }.rag-action-bar button { flex:1; }.rag-fallback button small { white-space:normal; }.rag-slider-field>div { grid-template-columns:minmax(0,1fr) 58px; }.rag-slider-field output { display:none; } }
 .rag-custom-select > button:disabled { cursor: not-allowed; color: #94a3b8; background: #f9fafb; }
.rag-field-error { color: #dc2626; font-size: 11px; line-height: 1.45; }
</style>
