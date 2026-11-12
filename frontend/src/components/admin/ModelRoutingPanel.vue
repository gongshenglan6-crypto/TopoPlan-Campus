<script setup>
import { Bot, Database, GitBranch, SlidersHorizontal } from '@lucide/vue'
import { computed } from 'vue'
import PromptSelect from './PromptSelect.vue'

const props = defineProps({
  models: { type: Array, required: true },
  modelId: { type: String, default: '' },
  fallbackModelId: { type: String, default: '' },
  smartRouting: { type: Boolean, default: false },
  ragEnabled: { type: Boolean, default: false },
  ragTopK: { type: Number, default: 5 },
  rerankEnabled: { type: Boolean, default: true },
  contextPolicy: { type: Object, default: () => ({}) },
  outputFormat: { type: String, default: 'MARKDOWN' },
  schemaJson: { type: String, default: '' },
  parameters: { type: Object, default: () => ({ useModelDefaults: true }) }
})

const emit = defineEmits(['update:model-id', 'update:fallback-model-id', 'update:smart-routing', 'update:rag-enabled', 'update:rag-top-k', 'update:rerank-enabled', 'update:context-policy', 'update:output-format', 'update:schema-json', 'update:parameters'])
const usableModels = computed(() => props.models.filter((model) => model.active !== false && ['chat', 'reasoning'].includes(String(model.modelType || 'chat').toLowerCase())))
const modelOptions = computed(() => [{ value: '', label: '请选择模型' }, ...usableModels.value.map((model) => ({ value: String(model.id), label: `${model.alias || model.name} · ${model.provider}` }))])
const fallbackModelOptions = computed(() => [{ value: '', label: '按系统优先级选择' }, ...modelOptions.value.filter((option) => option.value && option.value !== String(props.modelId))])
const showSchema = computed(() => ['STRICT_JSON', 'STRUCTURED_JSON'].includes(props.outputFormat))
const outputFormatOptions = [
  { value: 'NATURAL_LANGUAGE', label: '自然语言' },
  { value: 'MARKDOWN', label: 'Markdown' },
  { value: 'STRICT_JSON', label: '严格 JSON' },
  { value: 'STRUCTURED_JSON', label: '结构化 JSON' }
]
const contextSources = [
  ['project', '当前项目'], ['topology', '当前拓扑'], ['selectedNode', '当前选中设备'], ['conversation', '历史对话'], ['knowledge', '知识库']
]

function patchContext(key, value) { emit('update:context-policy', { project: true, topology: true, selectedNode: false, conversation: false, knowledge: false, ...props.contextPolicy, [key]: value }) }
function patchParameters(key, value) { emit('update:parameters', { useModelDefaults: true, ...props.parameters, [key]: value }) }
</script>

<template>
  <aside class="admin-card admin-prompt-routing" aria-label="Prompt 模型路由">
    <header class="admin-prompt-routing-header"><div><span class="admin-prompt-routing-kicker">EXECUTION</span><h3>运行模型与路由</h3><p>模型与 API 凭证均由后台统一托管。</p></div><span class="admin-prompt-routing-icon" aria-hidden="true"><GitBranch :size="16" /></span></header>

    <section class="prompt-route-block">
      <div class="prompt-route-label"><strong>运行模型</strong><span>{{ usableModels.length }} 个适用模型</span></div>
      <div class="prompt-route-mode"><button type="button" :class="{ active: !smartRouting }" @click="emit('update:smart-routing', false)">手动绑定</button><button type="button" :class="{ active: smartRouting }" @click="emit('update:smart-routing', true)">智能路由</button></div>
      <label class="prompt-route-field"><span>主模型</span><PromptSelect :model-value="modelId" :options="modelOptions" placeholder="请选择模型" aria-label="主模型" :disabled="smartRouting" @update:model-value="emit('update:model-id', $event)" /></label>
      <p v-if="smartRouting" class="prompt-route-note"><Bot :size="13" />根据任务复杂度、优先级、上下文长度和可用状态选择模型。</p>
      <label class="prompt-route-field"><span>备用模型</span><PromptSelect :model-value="fallbackModelId" :options="fallbackModelOptions" placeholder="按系统优先级选择" aria-label="备用模型" @update:model-value="emit('update:fallback-model-id', $event)" /><small>主模型超时或失败时仅尝试一次备用模型。</small></label>
    </section>

    <section class="prompt-route-block">
      <div class="prompt-route-label"><strong>知识库增强</strong><button type="button" class="prompt-mini-switch" :class="{ active: ragEnabled }" :aria-checked="ragEnabled" role="switch" @click="emit('update:rag-enabled', !ragEnabled)"><i /></button></div>
      <div v-if="ragEnabled" class="prompt-route-grid"><label class="prompt-route-field"><span>检索 Top K</span><input :value="ragTopK" type="number" min="1" max="20" @input="emit('update:rag-top-k', Number($event.target.value || 1))" /></label><label class="prompt-check-row"><input :checked="rerankEnabled" type="checkbox" @change="emit('update:rerank-enabled', $event.target.checked)" /><span>启用 Rerank</span></label></div>
      <small class="prompt-route-help">测试与运行时只使用权限范围内的知识库检索结果。</small>
    </section>

    <section class="prompt-route-block">
      <div class="prompt-route-label"><strong>上下文来源</strong><span>按需注入</span></div>
      <label v-for="[key, label] in contextSources" :key="key" class="prompt-check-row"><input :checked="contextPolicy[key] !== false" type="checkbox" @change="patchContext(key, $event.target.checked)" /><span>{{ label }}</span></label>
    </section>

    <section class="prompt-route-block">
      <div class="prompt-route-label"><strong>输出格式</strong><span>供运行时校验</span></div>
      <PromptSelect :model-value="outputFormat" :options="outputFormatOptions" aria-label="输出格式" @update:model-value="emit('update:output-format', $event)" />
      <label v-if="showSchema" class="prompt-route-field prompt-schema-field"><span>JSON Schema</span><textarea :value="schemaJson" rows="5" placeholder='例如：{ "nodes": [], "edges": [] }' @input="emit('update:schema-json', $event.target.value)" /></label>
    </section>

    <details class="prompt-advanced"><summary><SlidersHorizontal :size="14" />高级参数</summary><div><label class="prompt-check-row"><input :checked="parameters.useModelDefaults !== false" type="checkbox" @change="patchParameters('useModelDefaults', $event.target.checked)" /><span>使用模型默认参数</span></label><div v-if="parameters.useModelDefaults === false" class="prompt-parameter-grid"><label>Temperature<input :value="parameters.temperature ?? 0.7" type="number" min="0" max="2" step="0.1" @input="patchParameters('temperature', Number($event.target.value))" /></label><label>Top P<input :value="parameters.topP ?? 0.95" type="number" min="0.01" max="1" step="0.01" @input="patchParameters('topP', Number($event.target.value))" /></label><label>Max Tokens<input :value="parameters.maxOutputTokens ?? 4096" type="number" min="1" max="32768" @input="patchParameters('maxOutputTokens', Number($event.target.value))" /></label><label>超时（秒）<input :value="parameters.timeout ?? 60" type="number" min="5" max="180" @input="patchParameters('timeout', Number($event.target.value))" /></label></div></div></details>
  </aside>
</template>

<style scoped>
.prompt-route-block { display:grid; gap:8px; padding:14px 0; border-top:1px solid #eef2f7; }.prompt-route-block:first-of-type { padding-top:18px; border-top:0; }.prompt-route-label { display:flex; align-items:center; justify-content:space-between; gap:8px; }.prompt-route-label strong { color:#334155; font-size:12px; }.prompt-route-label>span { color:#94a3b8; font-size:10px; }.prompt-route-mode { display:grid; grid-template-columns:1fr 1fr; gap:5px; }.prompt-route-mode button { min-height:29px; border:1px solid #e2e8f0; border-radius:6px; background:#fff; color:#64748b; font-size:10px; }.prompt-route-mode button.active { border-color:#bfdbfe; background:#eff6ff; color:#2563eb; font-weight:700; }.prompt-route-field { display:grid; gap:5px; }.prompt-route-field>span { color:#64748b; font-size:10px; font-weight:650; }.prompt-route-field select,.prompt-route-field input,.prompt-output-select,.prompt-schema-field textarea { box-sizing:border-box; width:100%; min-height:31px; padding:0 8px; border:1px solid #dbe3ec; border-radius:6px; outline:0; background:#fff; color:#334155; font-size:11px; }.prompt-route-field textarea { min-height:88px; padding:8px; resize:vertical; font:10px/1.5 Consolas,monospace; }.prompt-route-field select:focus,.prompt-route-field input:focus,.prompt-output-select:focus,.prompt-schema-field textarea:focus { border-color:#93c5fd; box-shadow:0 0 0 3px rgba(37,99,235,.08); }.prompt-route-field select:disabled { cursor:not-allowed; background:#f8fafc; color:#94a3b8; }.prompt-route-field small,.prompt-route-help { color:#94a3b8; font-size:10px; line-height:1.45; }.prompt-route-note { display:flex; align-items:flex-start; gap:5px; margin:0; padding:7px; border:1px solid #dbeafe; border-radius:6px; background:#f8fbff; color:#2563eb; font-size:10px; line-height:1.45; }.prompt-route-note svg { flex:0 0 auto; margin-top:1px; }.prompt-mini-switch { position:relative; width:34px; height:19px; padding:0; border:0; border-radius:99px; background:#cbd5e1; }.prompt-mini-switch i { position:absolute; top:3px; left:3px; width:13px; height:13px; border-radius:50%; background:#fff; transition:transform .16s ease; }.prompt-mini-switch.active { background:#2563eb; }.prompt-mini-switch.active i { transform:translateX(15px); }.prompt-route-grid { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:end; gap:8px; }.prompt-check-row { display:flex; align-items:center; gap:7px; min-height:24px; color:#64748b; font-size:10px; }.prompt-check-row input { width:13px; height:13px; accent-color:#2563eb; }.prompt-advanced { margin:4px 0 0; border-top:1px solid #eef2f7; }.prompt-advanced summary { display:flex; align-items:center; gap:6px; padding:13px 0; color:#475569; font-size:11px; font-weight:700; cursor:pointer; }.prompt-advanced>div { display:grid; gap:9px; padding:0 0 12px; }.prompt-parameter-grid { display:grid; grid-template-columns:1fr 1fr; gap:7px; }.prompt-parameter-grid label { display:grid; gap:4px; color:#64748b; font-size:9px; }.prompt-parameter-grid input { box-sizing:border-box; width:100%; height:28px; padding:0 6px; border:1px solid #dbe3ec; border-radius:5px; color:#334155; font-size:10px; outline:0; }
:global(html[data-theme='dark']) .prompt-route-block,:global(html[data-theme='dark']) .prompt-advanced { border-color:rgba(148,163,184,.12); }:global(html[data-theme='dark']) .prompt-route-label strong { color:#e2e8f0; }:global(html[data-theme='dark']) .prompt-route-mode button,:global(html[data-theme='dark']) .prompt-route-field select,:global(html[data-theme='dark']) .prompt-route-field input,:global(html[data-theme='dark']) .prompt-output-select,:global(html[data-theme='dark']) .prompt-schema-field textarea,:global(html[data-theme='dark']) .prompt-parameter-grid input { border-color:#334155; background:#0f172a; color:#e2e8f0; }:global(html[data-theme='dark']) .prompt-route-mode button.active { background:rgba(37,99,235,.18); color:#93c5fd; }:global(html[data-theme='dark']) .prompt-route-note { border-color:rgba(96,165,250,.25); background:rgba(30,64,175,.16); color:#bfdbfe; }
</style>
