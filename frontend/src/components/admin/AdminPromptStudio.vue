<script setup>
import { Braces, CheckCircle2, ChevronDown, Clipboard, Copy, FileText, GitCompareArrows, History, Info, Play, Plus, RefreshCw, RotateCcw, Save, Sparkles, X } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import PromptSidebar from './PromptSidebar.vue'
import ModelRoutingPanel from './ModelRoutingPanel.vue'
import PromptSelect from './PromptSelect.vue'
import { adminModelChannels } from '../../data/adminModelPool'
import { createPrompt, deletePrompt, listModelChannels, listPromptVersions, listPrompts, restoreDefaultPrompt, restorePromptVersion, setPromptStatus, testPrompt, updatePrompt } from '../../api/admin'

const taskTypes = [
  { key: 'SYSTEM_BASE', label: 'TopoPlan 系统基础 Prompt', shortLabel: '系统基础', group: '基础', usage: '所有 AI 网络工程任务的基础身份与约束', scene: '所有 AI 功能调用前均会组合使用。', type: '系统 Prompt', outputFormat: 'NATURAL_LANGUAGE' },
  { key: 'REQUIREMENT_ANALYSIS', label: '需求分析', group: '规划', usage: '提取用户网络建设需求', scene: '需求分析页面与网络建设需求整理。', type: '任务 Prompt', outputFormat: 'STRUCTURED_JSON' },
  { key: 'TOPOLOGY_PLANNING', label: '拓扑规划', group: '规划', usage: '选择与规模匹配的网络架构', scene: '在创建节点前决定网络层级和设备角色。', type: '任务 Prompt', outputFormat: 'NATURAL_LANGUAGE' },
  { key: 'IP_VLAN_PLANNING', label: 'IP / VLAN 规划', group: '规划', usage: '规划网段、VLAN 与网关', scene: 'IP 地址和 VLAN 规划功能。', type: '任务 Prompt', outputFormat: 'NATURAL_LANGUAGE' },
  { key: 'TOPOLOGY_GENERATION', label: '拓扑生成', group: '规划', usage: '生成结构化拓扑节点与连接', scene: 'AI 生成拓扑功能。', type: '任务 Prompt', outputFormat: 'STRICT_JSON' },
  { key: 'TOPOLOGY_QA', label: '拓扑问答', group: '项目', usage: '围绕当前项目回答网络问题', scene: '项目右侧 AI 助手。', type: '任务 Prompt', outputFormat: 'MARKDOWN' },
  { key: 'DESIGN_EXPLANATION', label: '设计原理解释', group: '项目', usage: '解释当前拓扑为何这样设计', scene: '“为什么这样设计”相关问答。', type: '任务 Prompt', outputFormat: 'MARKDOWN' },
  { key: 'TOPOLOGY_CHECK', label: '拓扑检查', group: '检查', usage: '检查结构、层级与连接关系', scene: '当前拓扑检查功能。', type: '任务 Prompt', outputFormat: 'NATURAL_LANGUAGE' },
  { key: 'RISK_ANALYSIS', label: '风险分析', group: '检查', usage: '分析安全、容量与可靠性风险', scene: '网络风险分析功能。', type: '任务 Prompt', outputFormat: 'NATURAL_LANGUAGE' },
  { key: 'TOPOLOGY_OPTIMIZATION', label: '拓扑优化', group: '检查', usage: '提出可执行的拓扑优化方案', scene: '网络拓扑优化功能。', type: '任务 Prompt', outputFormat: 'NATURAL_LANGUAGE' },
  { key: 'SOLUTION_GENERATION', label: '网络方案生成', group: '输出', usage: '生成完整网络工程规划方案', scene: '网络方案导出与工程方案生成。', type: '任务 Prompt', outputFormat: 'MARKDOWN' },
  { key: 'RAG_QA', label: 'RAG 知识问答', group: '输出', usage: '结合知识库回答网络工程问题', scene: '知识库增强问答功能。', type: '任务 Prompt', outputFormat: 'MARKDOWN' }
]
const taskTypeOptions = taskTypes.map((item) => ({ value: item.key, label: item.label }))

const variables = [
  { group: '项目', token: '{{project_context}}', key: 'project_context', description: '当前项目允许提供给 AI 的上下文信息' },
  { group: '项目', token: '{{project_name}}', key: 'project_name', description: '当前项目名称' },
  { group: '项目', token: '{{project_scale}}', key: 'project_scale', description: '当前项目终端规模' },
  { group: '拓扑', token: '{{topology_nodes}}', key: 'topology_nodes', description: '当前拓扑中的设备节点数据' },
  { group: '拓扑', token: '{{topology_edges}}', key: 'topology_edges', description: '当前拓扑中的连接关系数据' },
  { group: '拓扑', token: '{{selected_node}}', key: 'selected_node', description: '当前选中的设备节点' },
  { group: '需求', token: '{{requirement_context}}', key: 'requirement_context', description: '当前请求中已整理的网络建设需求' },
  { group: '需求', token: '{{planning_context}}', key: 'planning_context', description: '当前网络架构规划结论' },
  { group: '会话', token: '{{conversation_context}}', key: 'conversation_context', description: '服务端受控的历史对话上下文' },
  { group: '知识库', token: '{{rag_context}}', key: 'rag_context', description: '知识库检索得到的参考内容' },
  { group: '用户', token: '{{user_question}}', key: 'user_question', description: '当前用户问题或管理员测试问题' },
  { group: '资源', token: '{{available_topology_resources}}', key: 'available_topology_resources', description: '后台启用且允许 AI 使用的拓扑资源目录' }
]
const variableGroups = computed(() => [...new Set(variables.map((item) => item.group))].map((group) => ({ group, items: variables.filter((item) => item.group === group) })))

const prompts = ref([])
const loading = ref(true)
const selectedPromptId = ref('')
const draftPrompt = ref(null)
const editor = ref(null)
const highlightLayer = ref(null)
const isSaving = ref(false)
const isDeleting = ref(false)
const isStatusUpdating = ref(false)
const isCreating = ref(false)
const historyOpen = ref(false)
const historyItems = ref([])
const historyLoading = ref(false)
const historySelection = ref([])
const testOpen = ref(false)
const testLoading = ref(false)
const testResult = ref(null)
const testForm = ref({ userQuestion: '请为 100 人的公司设计网络，包括研发、财务、行政、服务器和 WiFi。', projectContext: '模拟项目：100 人办公网络，研发、财务、行政三个区域，需要访客隔离和无线办公。', selectedNode: '', requirementContext: '' })
const saveDialogOpen = ref(false)
const copyDialogOpen = ref(false)
const restoreDefaultOpen = ref(false)
const variablesOpen = ref(false)
const toast = ref('')
let toastTimer = 0

const currentPrompt = computed(() => draftPrompt.value)
const promptModels = computed(() => adminModelChannels.value.filter((channel) => channel.active !== false && ['chat', 'reasoning'].includes(String(channel.modelType || 'chat').toLowerCase())).map((channel) => ({ ...channel, id: String(channel.id), name: channel.alias || channel.name })))
const lineNumbers = computed(() => Array.from({ length: Math.max(1, String(currentPrompt.value?.content || '').split('\n').length) }, (_, index) => index + 1))
const selectedHistoryItems = computed(() => historySelection.value.map((id) => historyItems.value.find((item) => String(item.key ?? item.id) === id)).filter(Boolean))
const historyDiff = computed(() => selectedHistoryItems.value.length === 2 ? diffText(String(selectedHistoryItems.value[0].content || ''), String(selectedHistoryItems.value[1].content || '')) : [])
const highlightedContent = computed(() => highlightPrompt(String(currentPrompt.value?.content || '')))

function taskInfo(taskType) { return taskTypes.find((item) => item.key === taskType) || { key: taskType || 'CUSTOM', label: taskType || '自定义任务', group: '项目', usage: '自定义 AI 任务', scene: '由管理员配置的 AI 任务。', type: '任务 Prompt', outputFormat: 'MARKDOWN' } }
function typeHelp(type) { return type === '系统 Prompt' ? '定义 AI 的基础身份、行为原则以及全局约束。' : '定义某一个具体 AI 功能需要执行的任务。' }
function formatTime(value) { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value).replace('T', ' ').replace(/\.\d+Z?$/, '') : date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
function showToast(message) { toast.value = message; window.clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toast.value = '' }, 3200) }
function bool(value, fallback = false) { return value === undefined || value === null ? fallback : value === true || value === 'true' || value === 1 || value === '1' }
function isTruthy(value) { return value === true || value === 'true' || value === 1 || value === '1' }
function defaultContextPolicy(taskType) { const topology = ['TOPOLOGY_GENERATION', 'TOPOLOGY_QA', 'DESIGN_EXPLANATION', 'TOPOLOGY_CHECK', 'RISK_ANALYSIS', 'TOPOLOGY_OPTIMIZATION', 'SOLUTION_GENERATION'].includes(taskType); return { project: taskType !== 'SYSTEM_BASE', topology, selectedNode: ['TOPOLOGY_QA', 'DESIGN_EXPLANATION'].includes(taskType), conversation: taskType === 'TOPOLOGY_QA', knowledge: ['TOPOLOGY_GENERATION', 'TOPOLOGY_QA', 'DESIGN_EXPLANATION', 'RAG_QA', 'SOLUTION_GENERATION'].includes(taskType) } }
function normalizePrompt(item) {
  const source = item && typeof item === 'object' ? item : {}
  const taskType = String(source.taskType || source.type || 'CUSTOM')
  const info = taskInfo(taskType)
  const active = source.active !== undefined || source.enabled !== undefined
    ? bool(source.active ?? source.enabled, true)
    : !['inactive', 'disabled', '停用', 'DISABLED'].includes(String(source.status || '').toLowerCase())
  return {
    ...source,
    id: String(source.key ?? source.id ?? source.promptId ?? source.uuid ?? source.configId ?? ''),
    title: String(source.title ?? source.name ?? source.displayName ?? info.label),
    name: String(source.title ?? source.name ?? source.displayName ?? info.label),
    taskType,
    group: info.group,
    detail: String(source.detail ?? source.usage ?? source.description ?? info.usage),
    usage: String(source.usage ?? source.detail ?? source.description ?? info.usage),
    scene: String(source.scene ?? info.scene),
    typeLabel: info.type,
    version: String(source.version ?? '1.0').replace(/^v(?=\d)/i, ''),
    active,
    status: active ? 'active' : 'inactive',
    content: String(source.content ?? source.prompt ?? ''),
    modelId: String(source.modelId ?? ''),
    fallbackModelId: String(source.fallbackModelId ?? ''),
    smartRouting: bool(source.smartRouting, false) || source.routeMode === 'SMART',
    ragEnabled: bool(source.ragEnabled, false),
    knowledgeBaseId: String(source.knowledgeBaseId ?? ''),
    ragTopK: Number(source.ragTopK ?? 5),
    rerankEnabled: bool(source.rerankEnabled, true),
    contextPolicy: { ...defaultContextPolicy(taskType), ...(source.contextPolicy || {}) },
    outputFormat: String(source.outputFormat ?? info.outputFormat),
    schemaJson: String(source.schemaJson ?? ''),
    parameters: { useModelDefaults: true, ...(source.parameters || {}) },
    changeNote: String(source.changeNote ?? ''),
    validationWarnings: Array.isArray(source.validationWarnings) ? source.validationWarnings : [],
    updatedBy: String(source.updatedBy ?? '系统管理员'),
    updatedAt: source.updatedAt ?? source.createdAt ?? ''
  }
}
function createDraft(prompt = {}) { const taskType = prompt.taskType || 'REQUIREMENT_ANALYSIS'; const info = taskInfo(taskType); return normalizePrompt({ ...prompt, title: prompt.title || `新建${info.label}`, taskType, detail: prompt.detail || info.usage, usage: prompt.usage || prompt.detail || info.usage, scene: prompt.scene || info.scene, version: prompt.version || '1.0', content: prompt.content || '', smartRouting: prompt.smartRouting ?? true, active: prompt.active ?? true, outputFormat: prompt.outputFormat || info.outputFormat, contextPolicy: prompt.contextPolicy || defaultContextPolicy(taskType), parameters: prompt.parameters || { useModelDefaults: true } }) }
function clonePrompt(prompt) {
  if (!prompt) return null
  try {
    return JSON.parse(JSON.stringify(prompt))
  } catch {
    return { ...prompt, contextPolicy: { ...(prompt.contextPolicy || {}) }, parameters: { ...(prompt.parameters || {}) }, validationWarnings: [...(prompt.validationWarnings || [])] }
  }
}
function serializablePrompt(prompt) { return { title: String(prompt.title || '').trim(), name: String(prompt.title || '').trim(), taskType: prompt.taskType, detail: prompt.detail, usage: prompt.usage || prompt.detail, content: prompt.content || '', modelId: String(prompt.modelId || ''), fallbackModelId: String(prompt.fallbackModelId || ''), routeMode: prompt.smartRouting ? 'SMART' : 'MANUAL', smartRouting: Boolean(prompt.smartRouting), ragEnabled: Boolean(prompt.ragEnabled), knowledgeBaseId: String(prompt.knowledgeBaseId || ''), ragTopK: Number(prompt.ragTopK || 5), rerankEnabled: Boolean(prompt.rerankEnabled), contextPolicy: prompt.contextPolicy, outputFormat: prompt.outputFormat, schemaJson: prompt.schemaJson || '', parameters: prompt.parameters, active: Boolean(prompt.active), status: prompt.active ? 'active' : 'inactive', changeNote: String(prompt.changeNote || '').trim(), updatedBy: '系统管理员' } }

async function load() {
  loading.value = true
  try {
    const [modelsResult, promptsResult] = await Promise.allSettled([listModelChannels(), listPrompts()])
    if (modelsResult.status === 'fulfilled') {
      const storedModels = Array.isArray(modelsResult.value) ? modelsResult.value : []
      adminModelChannels.value = storedModels.map((channel) => ({
        ...channel,
        id: String(channel.key ?? channel.id),
        apiKey: '',
        apiKeyConfigured: Boolean(channel.apiKeyConfigured),
        active: channel.active ?? channel.enabled ?? true
      }))
    }
    if (promptsResult.status !== 'fulfilled') throw promptsResult.reason

    const rawPrompts = Array.isArray(promptsResult.value)
      ? promptsResult.value
      : promptsResult.value?.items ?? promptsResult.value?.data?.items ?? []
    prompts.value = rawPrompts
      .filter((item) => !isTruthy(item?.archived))
      .map(normalizePrompt)
      .filter((prompt) => prompt.id)

    const selectedStillExists = selectedPromptId.value && prompts.value.some((prompt) => prompt.id === selectedPromptId.value)
    if (!draftPrompt.value || !selectedStillExists) {
      const firstEnabled = prompts.value.find((prompt) => prompt.active) || prompts.value[0]
      selectPrompt(firstEnabled?.id || '')
    } else {
      syncCurrentPrompt()
    }
  } catch (error) {
    prompts.value = []
    draftPrompt.value = null
    showToast(error?.message || 'Prompt 配置加载失败')
  } finally {
    loading.value = false
  }
}
function resetEditorViewport(focus = false) {
  nextTick(() => {
    const reset = () => {
      const target = editor.value
      const highlight = highlightLayer.value
      const editorScroll = target?.closest('.admin-prompt-editor-scroll')
      const editorPanel = target?.closest('.admin-prompt-editor')

      if (target) {
        target.scrollTop = 0
        target.scrollLeft = 0
        if (focus) target.focus({ preventScroll: true })
      }
      if (highlight) {
        highlight.scrollTop = 0
        highlight.scrollLeft = 0
      }
      if (editorScroll) {
        editorScroll.scrollTop = 0
        editorScroll.scrollLeft = 0
      }
      if (editorPanel) {
        editorPanel.scrollTop = 0
        editorPanel.scrollLeft = 0
      }
    }

    reset()
    requestAnimationFrame(reset)
  })
}
function syncCurrentPrompt() { const prompt = prompts.value.find((item) => item.id === selectedPromptId.value); draftPrompt.value = clonePrompt(prompt); resetEditorViewport() }
function selectPrompt(id) { selectedPromptId.value = String(id || ''); isCreating.value = false; syncCurrentPrompt(); resetEditorViewport(true) }
function createPromptDraft() { isCreating.value = true; selectedPromptId.value = ''; draftPrompt.value = createDraft(); resetEditorViewport(true) }
function cancelEdit() { saveDialogOpen.value = false; if (isCreating.value) { isCreating.value = false; selectPrompt(prompts.value[0]?.id || '') } else syncCurrentPrompt() }
function updateDraftTask(taskType) { if (!draftPrompt.value) return; const info = taskInfo(taskType); Object.assign(draftPrompt.value, { taskType, group: info.group, detail: info.usage, usage: info.usage, scene: info.scene, typeLabel: info.type, outputFormat: info.outputFormat, contextPolicy: defaultContextPolicy(taskType), ragEnabled: ['TOPOLOGY_GENERATION', 'TOPOLOGY_QA', 'DESIGN_EXPLANATION', 'RAG_QA', 'SOLUTION_GENERATION'].includes(taskType), schemaJson: taskType === 'TOPOLOGY_GENERATION' ? '{\n  "nodes": [],\n  "edges": [],\n  "summary": "",\n  "assumptions": []\n}' : '' }) }
function unknownVariables(content) { return [...new Set([...String(content || '').matchAll(/\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*}}/g)].map((match) => match[1]).filter((key) => !variables.some((item) => item.key === key)))] }
function requestSave() { if (!draftPrompt.value) return; if (!String(draftPrompt.value.title || '').trim()) return showToast('Prompt 名称不能为空'); if (!String(draftPrompt.value.content || '').trim()) return showToast('Prompt 内容不能为空'); const unknown = unknownVariables(draftPrompt.value.content); if (unknown.length) return showToast(`未知变量：${unknown.join('、')}`); saveDialogOpen.value = true }
async function savePromptDraft() { if (!draftPrompt.value || isSaving.value) return; isSaving.value = true; try { const payload = serializablePrompt(draftPrompt.value); const saved = isCreating.value ? await createPrompt(payload) : await updatePrompt(draftPrompt.value.id, payload); const normalized = normalizePrompt(saved); const index = prompts.value.findIndex((item) => item.id === normalized.id); if (index === -1) prompts.value.unshift(normalized); else prompts.value[index] = normalized; selectedPromptId.value = normalized.id; draftPrompt.value = clonePrompt(normalized); isCreating.value = false; saveDialogOpen.value = false; showToast('Prompt 配置已保存') } catch (error) { showToast(error.message || '保存失败，请稍后重试') } finally { isSaving.value = false } }
async function toggleStatus() { if (!draftPrompt.value || isStatusUpdating.value || isCreating.value) return; isStatusUpdating.value = true; try { const updated = normalizePrompt(await setPromptStatus(draftPrompt.value.id, !draftPrompt.value.active)); prompts.value = prompts.value.map((item) => item.id === updated.id ? updated : (updated.active && item.taskType === updated.taskType ? { ...item, active: false, status: 'inactive' } : item)); draftPrompt.value = clonePrompt(updated); showToast(updated.active ? 'Prompt 已启用' : 'Prompt 已停用') } catch (error) { showToast(error.message || 'Prompt 状态更新失败') } finally { isStatusUpdating.value = false } }
async function removePrompt(id) { const prompt = prompts.value.find((item) => item.id === id); if (!prompt || isDeleting.value || !window.confirm(`确定删除“${prompt.title}”吗？删除后无法恢复。`)) return; isDeleting.value = true; try { await deletePrompt(id); prompts.value = prompts.value.filter((item) => item.id !== id); showToast('Prompt 已删除'); selectPrompt(prompts.value[0]?.id || '') } catch (error) { showToast(error.message || 'Prompt 删除失败') } finally { isDeleting.value = false } }
function openCopyDialog() { if (draftPrompt.value) copyDialogOpen.value = true }
async function copyContent() { if (!draftPrompt.value?.content) return; try { await navigator.clipboard.writeText(draftPrompt.value.content); copyDialogOpen.value = false; showToast('Prompt 内容已复制') } catch { showToast('复制失败，请手动选择内容') } }
function copyAsTemplate() { if (!draftPrompt.value) return; draftPrompt.value = createDraft({ ...draftPrompt.value, id: '', title: `${draftPrompt.value.title} - 副本`, version: '1.0', active: false, changeNote: '复制自原 Prompt' }); selectedPromptId.value = ''; isCreating.value = true; copyDialogOpen.value = false; showToast('已创建副本草稿') }
async function openHistory() { if (!draftPrompt.value?.id) return; historyOpen.value = true; historyLoading.value = true; historySelection.value = []; try { historyItems.value = await listPromptVersions(draftPrompt.value.id) } catch (error) { historyItems.value = []; showToast(error.message || '历史版本加载失败') } finally { historyLoading.value = false } }
function toggleHistorySelection(item) { const id = String(item.key ?? item.id); const next = historySelection.value.includes(id) ? historySelection.value.filter((value) => value !== id) : [...historySelection.value, id].slice(-2); historySelection.value = next }
async function restoreVersion(item) { if (!draftPrompt.value || !window.confirm(`确定恢复版本 v${item.version} 吗？恢复会创建一个新的版本。`)) return; try { const updated = normalizePrompt(await restorePromptVersion(draftPrompt.value.id, item.key ?? item.id)); prompts.value = prompts.value.map((prompt) => prompt.id === updated.id ? updated : prompt); draftPrompt.value = clonePrompt(updated); historyItems.value = await listPromptVersions(updated.id); showToast('已恢复为新版本') } catch (error) { showToast(error.message || '版本恢复失败') } }
async function restoreDefault() { if (!draftPrompt.value?.id) return; try { const updated = normalizePrompt(await restoreDefaultPrompt(draftPrompt.value.id)); prompts.value = prompts.value.map((item) => item.id === updated.id ? updated : item); draftPrompt.value = clonePrompt(updated); restoreDefaultOpen.value = false; showToast('已创建恢复默认的新版本') } catch (error) { showToast(error.message || '恢复默认失败') } }
function openTest() { if (!draftPrompt.value?.id) return showToast('请先保存新建 Prompt 再进行真实测试'); testResult.value = null; testOpen.value = true }
async function runTest() { if (!draftPrompt.value?.id || !testForm.value.userQuestion.trim()) return showToast('请输入测试用户问题'); testLoading.value = true; testResult.value = null; try { testResult.value = await testPrompt(draftPrompt.value.id, { prompt: serializablePrompt(draftPrompt.value), test: testForm.value }); if (testResult.value.validationWarnings?.length) showToast(testResult.value.validationWarnings[0]) } catch (error) { showToast(error.message || '测试调用失败') } finally { testLoading.value = false } }
function insertVariable(variable) { if (!draftPrompt.value) return; const target = editor.value; const start = target?.selectionStart ?? draftPrompt.value.content.length; const end = target?.selectionEnd ?? start; draftPrompt.value.content = `${draftPrompt.value.content.slice(0, start)}${variable.token}${draftPrompt.value.content.slice(end)}`; variablesOpen.value = false; nextTick(() => { target?.focus(); target?.setSelectionRange(start + variable.token.length, start + variable.token.length) }) }
function syncEditorScroll(event) { if (!highlightLayer.value) return; highlightLayer.value.scrollTop = event.target.scrollTop; highlightLayer.value.scrollLeft = event.target.scrollLeft }
function highlightPrompt(content) { if (!content) return '<span class="prompt-code-placeholder">请输入 Prompt 内容，例如：你是一名专业的网络工程规划助手……</span>'; const expression = /(\{\{\s*[a-zA-Z_][a-zA-Z0-9_]*\s*}})|(\b\d+(?:\.\d+)?\b)/g; let cursor = 0; let html = ''; for (const match of content.matchAll(expression)) { html += escapeHtml(content.slice(cursor, match.index)); html += match[1] ? `<mark class="prompt-token" title="动态变量">${escapeHtml(match[1])}</mark>` : `<mark class="prompt-number">${escapeHtml(match[2])}</mark>`; cursor = Number(match.index) + match[0].length } return html + escapeHtml(content.slice(cursor)) }
function escapeHtml(value) { return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') }
function diffText(before, after) { const beforeLines = before.split('\n'); const afterLines = after.split('\n'); const beforeOnly = new Set(beforeLines.filter((line) => !afterLines.includes(line))); const afterOnly = new Set(afterLines.filter((line) => !beforeLines.includes(line))); return [...beforeLines.filter((line) => beforeOnly.has(line)).map((line) => ({ type: 'removed', line })), ...afterLines.filter((line) => afterOnly.has(line)).map((line) => ({ type: 'added', line }))] }
onMounted(load)
onBeforeUnmount(() => window.clearTimeout(toastTimer))
</script>

<template>
  <section class="admin-section-page admin-prompt-page">
    <header class="admin-prompt-page-header"><div><p>AI 引擎</p><h2>Prompt 配置</h2><span>统一管理系统内置 AI Prompt，用于控制不同 AI 功能的角色设定、任务规则与输出要求。</span></div></header>
    <div class="admin-prompt-notice"><Info :size="15" /><span>修改 Prompt 会影响对应 AI 功能的生成结果，请确认内容无误后再保存。</span></div>
    <div v-if="loading" class="admin-prompt-layout"><aside class="admin-card admin-prompt-sidebar"><div class="admin-prompt-sidebar-empty">正在从数据库读取 Prompt 配置…</div></aside><section class="admin-card admin-prompt-editor"><div class="admin-prompt-editor-empty">正在加载编辑器…</div></section><aside class="admin-card admin-prompt-routing"><div class="admin-prompt-editor-empty">正在读取模型配置…</div></aside></div>
    <div v-else class="admin-prompt-layout">
      <PromptSidebar :templates="prompts" :active-template-id="selectedPromptId" @select="selectPrompt" @create="createPromptDraft" @delete="removePrompt" />
      <section v-if="currentPrompt" class="admin-card admin-prompt-editor" aria-label="Prompt 编辑器">
        <header class="admin-prompt-editor-header"><div class="admin-prompt-editor-heading"><span>Prompt 名称</span><input v-model="currentPrompt.title" class="admin-prompt-title-input" aria-label="Prompt 名称" /><small>用于管理员识别该 Prompt，不会直接发送给 AI。</small></div><div class="admin-prompt-editor-meta"><label>版本<input v-model="currentPrompt.version" class="admin-prompt-version-input" aria-label="Prompt 版本" /></label><button type="button" class="admin-code-status" :class="{ inactive: !currentPrompt.active }" :disabled="isCreating || isStatusUpdating" @click="toggleStatus"><i />{{ isStatusUpdating ? '处理中…' : (currentPrompt.active ? '已启用' : '已停用') }}</button></div></header>
        <div class="admin-prompt-editor-scroll">
          <div class="admin-prompt-detail-grid"><label><span>应用场景</span><PromptSelect :model-value="currentPrompt.taskType" :options="taskTypeOptions" aria-label="应用场景" @update:model-value="updateDraftTask" /><small>{{ currentPrompt.scene }}</small></label><label><span>Prompt 类型 <em :title="typeHelp(currentPrompt.typeLabel)">{{ currentPrompt.typeLabel }}</em></span><div class="admin-prompt-type-help">{{ typeHelp(currentPrompt.typeLabel) }}</div><small>{{ currentPrompt.taskType }}</small></label></div>
          <div class="admin-prompt-content-heading"><div><strong>Prompt 内容</strong><small>该内容将在调用对应 AI 功能时作为系统指令或任务指令发送给模型。</small></div><div class="prompt-editor-tools"><span>当前字符数：{{ currentPrompt.content.length.toLocaleString('zh-CN') }}</span><button type="button" :aria-expanded="variablesOpen" @click="variablesOpen = !variablesOpen"><Braces :size="14" />插入变量<ChevronDown :size="12" /></button><div v-if="variablesOpen" class="prompt-variable-popover"><section v-for="group in variableGroups" :key="group.group"><strong>{{ group.group }}</strong><button v-for="variable in group.items" :key="variable.key" type="button" :title="variable.description" @click="insertVariable(variable)">{{ variable.token }}</button></section></div></div></div>
          <div class="admin-prompt-editor-frame prompt-highlight-editor"><div class="admin-prompt-editor-gutter"><span v-for="line in lineNumbers" :key="line">{{ line }}</span></div><div class="prompt-editor-code"><pre ref="highlightLayer" :class="{ empty: !currentPrompt.content }" v-html="highlightedContent" /><textarea ref="editor" v-model="currentPrompt.content" wrap="off" spellcheck="false" aria-label="Prompt 内容" @scroll="syncEditorScroll" /></div></div>
          <div class="admin-prompt-content-help"><span>建议 Prompt 中明确描述 AI 的角色、任务目标、处理规则、限制条件以及输出要求。</span><span>修改后只会影响之后的新请求，不会修改已经生成的历史内容。</span></div>
          <section class="admin-prompt-variable-area"><header><div><strong>可用变量</strong><small>仅显示后端实际会替换的变量，点击可插入光标位置。</small></div><Sparkles :size="15" /></header><div class="admin-prompt-variable-tags"><button v-for="variable in variables" :key="variable.key" type="button" :title="variable.description" @click="insertVariable(variable)">{{ variable.token }}</button></div></section>
          <details class="admin-prompt-guidance"><summary>Prompt 编写建议</summary><div><p><strong>1. 明确 AI 的角色</strong> 例如：你是一名专业的网络工程规划助手。</p><p><strong>2. 明确任务</strong> 说明该 Prompt 需要完成的具体 AI 功能。</p><p><strong>3. 明确约束</strong> 不得虚构不存在的设备型号或网络参数。</p><p><strong>4. 明确输出内容</strong> 列出结果需要遵循的结构和格式。</p><p><strong>5. 保持 Prompt 简洁</strong> 避免重复、冲突或没有实际意义的指令。</p></div></details>
          <div class="admin-prompt-status-note"><div><strong>状态</strong><span>{{ currentPrompt.active ? '启用后，系统调用对应 AI 功能时会使用当前 Prompt。' : '停用后，该 Prompt 不再参与对应功能调用。' }}</span></div><div v-if="currentPrompt.validationWarnings?.length" class="prompt-warning"><Info :size="13" />{{ currentPrompt.validationWarnings[0] }}</div></div>
        </div>
        <footer class="admin-prompt-editor-footer"><div><button type="button" class="admin-ghost-button" @click="openTest"><Play :size="14" /> 测试</button><button type="button" class="admin-ghost-button" :disabled="!currentPrompt.id" @click="openHistory"><History :size="14" /> 历史版本</button><button type="button" class="admin-ghost-button" @click="openCopyDialog"><Copy :size="14" /> 复制</button><button type="button" class="admin-ghost-button" :disabled="!currentPrompt.id" @click="restoreDefaultOpen = true"><RefreshCw :size="14" /> 恢复默认</button></div><div><button type="button" class="admin-ghost-button" :disabled="isSaving" @click="cancelEdit">取消</button><button type="button" class="admin-primary-button" :disabled="isSaving" @click="requestSave"><Save :size="14" /> {{ isSaving ? '保存中…' : '保存修改' }}</button></div></footer>
      </section>
      <section v-else class="admin-card admin-prompt-editor"><div class="admin-prompt-editor-empty"><FileText :size="20" /><span>请选择一个 Prompt 模板开始编辑</span><button type="button" class="admin-primary-button" @click="createPromptDraft"><Plus :size="14" /> 新建 Prompt</button></div></section>
      <ModelRoutingPanel v-if="currentPrompt" :models="promptModels" :model-id="currentPrompt.modelId" :fallback-model-id="currentPrompt.fallbackModelId" :smart-routing="currentPrompt.smartRouting" :rag-enabled="currentPrompt.ragEnabled" :rag-top-k="currentPrompt.ragTopK" :rerank-enabled="currentPrompt.rerankEnabled" :context-policy="currentPrompt.contextPolicy" :output-format="currentPrompt.outputFormat" :schema-json="currentPrompt.schemaJson" :parameters="currentPrompt.parameters" @update:model-id="currentPrompt.modelId = $event" @update:fallback-model-id="currentPrompt.fallbackModelId = $event" @update:smart-routing="currentPrompt.smartRouting = $event" @update:rag-enabled="currentPrompt.ragEnabled = $event" @update:rag-top-k="currentPrompt.ragTopK = $event" @update:rerank-enabled="currentPrompt.rerankEnabled = $event" @update:context-policy="currentPrompt.contextPolicy = $event" @update:output-format="currentPrompt.outputFormat = $event" @update:schema-json="currentPrompt.schemaJson = $event" @update:parameters="currentPrompt.parameters = $event" />
      <aside v-else class="admin-card admin-prompt-routing"><div class="admin-prompt-editor-empty">暂无 Prompt 路由配置</div></aside>
    </div>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>

    <Transition name="admin-prompt-overlay"><div v-if="saveDialogOpen" class="admin-prompt-overlay prompt-dialog-overlay" @pointerdown.self="saveDialogOpen = false"><section class="prompt-small-dialog" role="dialog" aria-modal="true"><header><div><span>保存新版本</span><h3>修改说明</h3></div><button type="button" class="admin-prompt-icon-button" @click="saveDialogOpen = false"><X :size="18" /></button></header><label>说明本次修改的原因<textarea v-model="currentPrompt.changeNote" rows="3" placeholder="例如：优化 1000 人园区网络的设备分层规则" /></label><footer><button type="button" class="admin-ghost-button" @click="saveDialogOpen = false">取消</button><button type="button" class="admin-primary-button" :disabled="isSaving" @click="savePromptDraft"><Save :size="14" />{{ isSaving ? '保存中…' : '保存新版本' }}</button></footer></section></div></Transition>
    <Transition name="admin-prompt-overlay"><div v-if="copyDialogOpen" class="admin-prompt-overlay prompt-dialog-overlay" @pointerdown.self="copyDialogOpen = false"><section class="prompt-small-dialog" role="dialog" aria-modal="true"><header><div><span>复制 Prompt</span><h3>{{ currentPrompt?.title }}</h3></div><button type="button" class="admin-prompt-icon-button" @click="copyDialogOpen = false"><X :size="18" /></button></header><div class="prompt-copy-options"><button type="button" @click="copyContent"><Clipboard :size="16" /><span><strong>复制 Prompt 内容</strong><small>复制编辑器中的文本到剪贴板。</small></span></button><button type="button" @click="copyAsTemplate"><Copy :size="16" /><span><strong>复制为新模板</strong><small>生成一个独立的 Prompt 配置草稿。</small></span></button></div></section></div></Transition>
    <Transition name="admin-prompt-overlay"><div v-if="restoreDefaultOpen" class="admin-prompt-overlay prompt-dialog-overlay" @pointerdown.self="restoreDefaultOpen = false"><section class="prompt-small-dialog" role="dialog" aria-modal="true"><header><div><span>恢复系统默认</span><h3>恢复默认 Prompt</h3></div><button type="button" class="admin-prompt-icon-button" @click="restoreDefaultOpen = false"><X :size="18" /></button></header><p class="prompt-dialog-copy">当前内容将被系统默认内容替换，并保存为一个新的历史版本；原版本会继续保留。</p><footer><button type="button" class="admin-ghost-button" @click="restoreDefaultOpen = false">取消</button><button type="button" class="admin-primary-button" @click="restoreDefault">确认恢复</button></footer></section></div></Transition>
    <Transition name="admin-prompt-overlay"><div v-if="historyOpen" class="admin-prompt-overlay" @pointerdown.self="historyOpen = false"><aside class="admin-prompt-history-drawer" role="dialog" aria-modal="true"><header class="admin-prompt-drawer-header"><div><span>VERSION HISTORY</span><h3>{{ currentPrompt?.title }}</h3><small>选择两个版本即可查看内容差异，恢复会创建新的版本。</small></div><button type="button" class="admin-prompt-icon-button" @click="historyOpen = false"><X :size="18" /></button></header><div class="admin-prompt-history-list"><div v-if="historyLoading" class="admin-prompt-empty">正在读取历史版本…</div><div v-else-if="!historyItems.length" class="admin-prompt-empty">暂无历史版本</div><article v-for="item in historyItems" v-else :key="item.key ?? item.id" :class="{ selected: historySelection.includes(String(item.key ?? item.id)) }"><header><button type="button" class="prompt-history-select" :class="{ selected: historySelection.includes(String(item.key ?? item.id)) }" @click="toggleHistorySelection(item)"><GitCompareArrows :size="13" />v{{ item.version }}</button><time>{{ formatTime(item.createdAt) }}</time></header><p>{{ item.changeNote || '版本快照' }}</p><button type="button" class="admin-ghost-button" @click="restoreVersion(item)"><RotateCcw :size="14" /> 恢复为新版本</button></article><section v-if="historyDiff.length" class="prompt-history-diff"><header><strong>版本差异</strong><span>红色为删除，绿色为新增</span></header><pre><span v-for="(line, index) in historyDiff" :key="index" :class="line.type">{{ line.type === 'added' ? '+ ' : '- ' }}{{ line.line }}</span></pre></section></div></aside></div></Transition>
    <Transition name="admin-prompt-overlay"><div v-if="testOpen" class="admin-prompt-overlay" @pointerdown.self="testOpen = false"><aside class="admin-prompt-test-drawer" role="dialog" aria-modal="true"><header class="admin-prompt-drawer-header"><div><span>PROMPT TEST</span><h3>测试：{{ currentPrompt?.title }}</h3><small>只使用管理员填写的模拟数据，不读取普通用户项目、拓扑或对话。</small></div><button type="button" class="admin-prompt-icon-button" @click="testOpen = false"><X :size="18" /></button></header><div class="prompt-test-workspace"><section class="prompt-test-inputs"><label>用户问题<textarea v-model="testForm.userQuestion" rows="5" /></label><label>模拟项目上下文<textarea v-model="testForm.projectContext" rows="5" /></label><label>模拟选中设备<input v-model="testForm.selectedNode" placeholder="例如：Core01" /></label><label>模拟需求上下文<textarea v-model="testForm.requirementContext" rows="4" /></label><button type="button" class="admin-primary-button" :disabled="testLoading" @click="runTest"><Play :size="14" />{{ testLoading ? '正在调用模型…' : '开始真实测试' }}</button></section><section class="prompt-test-result"><div v-if="!testResult" class="admin-prompt-empty">填写测试输入后，将展示实际运行 Prompt、模型结果和调用指标。</div><template v-else><div class="prompt-test-model"><CheckCircle2 :size="16" /><span><strong>{{ testResult.model?.name }}</strong><small>{{ testResult.model?.provider }} · {{ testResult.model?.modelId }}{{ testResult.usedFallback ? ' · 已使用备用模型' : '' }}</small></span></div><pre class="prompt-test-answer">{{ testResult.result }}</pre><div class="prompt-test-metrics"><span>延迟 {{ testResult.metrics?.latencyMs }} ms</span><span>输入 {{ testResult.metrics?.inputTokens }} Token</span><span>输出 {{ testResult.metrics?.outputTokens }} Token</span><span>RAG 命中 {{ testResult.metrics?.ragHits }} 条</span></div><details class="prompt-effective-preview"><summary>查看变量替换后的实际 System Prompt</summary><pre>{{ testResult.effectivePrompt }}</pre></details></template></section></div></aside></div></Transition>
  </section>
</template>

<style scoped>
.admin-prompt-page { max-width:1500px; }.admin-prompt-page-header h2 { font-size:24px; }.admin-prompt-title-input { border-bottom:1px solid transparent; }.admin-prompt-title-input:focus { border-bottom-color:#d1d5db; }.admin-prompt-editor-frame textarea { color:#f8fafc !important; caret-color:#f8fafc; }.admin-prompt-page-header { margin-bottom:10px; }.admin-prompt-notice { display:flex; align-items:center; gap:7px; min-height:30px; margin:0 0 12px; padding:6px 10px; border:1px solid #dbeafe; border-radius:7px; background:#f8fbff; color:#2563eb; font-size:11px; }.admin-prompt-notice svg { flex:0 0 auto; }
.admin-prompt-editor-meta { display:flex; align-items:center; gap:12px; flex:0 0 auto; }.admin-prompt-editor-meta label { display:inline-flex; align-items:center; gap:5px; color:#94a3b8; font-size:10px; }.admin-prompt-version-input { width:48px; height:27px; padding:0 5px; border:1px solid #dbe3ec; border-radius:5px; background:#fff; color:#475569; font:inherit; text-align:center; }.admin-prompt-editor-heading small { display:block; margin-top:7px; color:#94a3b8; font-size:10px; font-weight:500; }.admin-code-status { display:inline-flex; align-items:center; gap:5px; min-width:62px; height:26px; padding:0 7px; border:1px solid #bbf7d0; border-radius:999px; background:#f0fdf4; color:#15803d; font-size:10px; font-weight:650; }.admin-code-status i { width:6px; height:6px; border-radius:50%; background:#22c55e; }.admin-code-status.inactive { border-color:#e2e8f0; background:#f8fafc; color:#64748b; }.admin-code-status.inactive i { background:#94a3b8; }
.admin-prompt-detail-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:14px; }.admin-prompt-detail-grid label { display:grid; gap:6px; color:#526174; font-size:11px; font-weight:700; }.admin-prompt-detail-grid select { width:100%; height:34px; padding:0 9px; border:1px solid #dbe3ec; border-radius:6px; outline:0; background:#fff; color:#334155; font-size:12px; }.admin-prompt-detail-grid small { color:#94a3b8; font-size:10px; font-weight:500; line-height:1.45; }.admin-prompt-detail-grid em { margin-left:5px; color:#2563eb; font-style:normal; font-weight:600; }.admin-prompt-type-help { min-height:34px; padding:8px 9px; border:1px solid #e5ebf2; border-radius:6px; background:#f8fafc; color:#64748b; font-size:11px; font-weight:500; line-height:1.4; }
.admin-prompt-content-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:10px; margin-bottom:8px; }.admin-prompt-content-heading strong,.admin-prompt-content-heading small { display:block; }.admin-prompt-content-heading strong { color:#334155; font-size:12px; }.admin-prompt-content-heading small { margin-top:3px; color:#94a3b8; font-size:10px; font-weight:500; }.prompt-editor-tools { position:relative; display:flex; align-items:center; gap:7px; color:#64748b; font-size:10px; white-space:nowrap; }.prompt-editor-tools>button { display:inline-flex; align-items:center; gap:3px; min-height:26px; padding:0 7px; border:1px solid #dbe3ec; border-radius:5px; background:#fff; color:#475569; font-size:10px; }.prompt-variable-popover { position:absolute; z-index:12; top:31px; right:0; display:grid; gap:7px; width:260px; padding:9px; border:1px solid #e2e8f0; border-radius:8px; background:#fff; box-shadow:0 14px 28px rgba(15,23,42,.15); white-space:normal; }.prompt-variable-popover section { display:flex; flex-wrap:wrap; gap:4px; }.prompt-variable-popover strong { flex-basis:100%; color:#94a3b8; font-size:9px; }.prompt-variable-popover button { padding:4px 5px; border:1px solid #bfdbfe; border-radius:4px; background:#fff; color:#2563eb; font:10px Consolas,monospace; }.prompt-variable-popover button:hover { background:#eff6ff; }
.prompt-highlight-editor { min-height:440px; }.prompt-editor-code { position:relative; min-width:0; min-height:440px; overflow:hidden; }.prompt-editor-code pre,.prompt-editor-code textarea { box-sizing:border-box; position:absolute; inset:0; width:100%; min-width:0; min-height:440px; margin:0; padding:17px 18px; border:0; outline:0; background:transparent; font:13px/1.62 Consolas,"Courier New",monospace; white-space:pre; tab-size:2; }.prompt-editor-code pre { overflow:auto; color:#e2e8f0; pointer-events:none; scrollbar-width:none; }.prompt-editor-code pre::-webkit-scrollbar { display:none; }.prompt-editor-code textarea { position:relative; z-index:1; overflow:auto; resize:none; color:transparent !important; caret-color:#f8fafc; text-shadow:none; }.prompt-editor-code textarea::selection { background:rgba(96,165,250,.35); }.prompt-editor-code pre.empty { color:#64748b; }.prompt-code-placeholder { color:#64748b; }.prompt-editor-code :deep(.prompt-token) { padding:1px 2px; border-radius:3px; background:rgba(14,165,233,.16); color:#7dd3fc; }.prompt-editor-code :deep(.prompt-number) { color:#c4b5fd; background:transparent; }.admin-prompt-content-help { display:grid; gap:3px; margin:8px 1px 14px; color:#94a3b8; font-size:10px; line-height:1.45; }
.admin-prompt-variable-area { margin-bottom:14px; padding:10px 11px; border:1px solid #e5ebf2; border-radius:7px; background:#fbfdff; }.admin-prompt-variable-area header { display:flex; align-items:center; justify-content:space-between; gap:8px; }.admin-prompt-variable-area header strong { color:#475569; font-size:11px; }.admin-prompt-variable-area header small { display:block; margin-top:3px; color:#94a3b8; font-size:10px; font-weight:500; }.admin-prompt-variable-area header svg { color:#2563eb; }.admin-prompt-variable-tags { display:flex; flex-wrap:wrap; gap:5px; margin-top:8px; }.admin-prompt-variable-tags button { padding:4px 6px; border:1px solid #bfdbfe; border-radius:4px; background:#fff; color:#2563eb; font-family:Consolas,monospace; font-size:10px; cursor:pointer; }.admin-prompt-variable-tags button:hover { background:#eff6ff; }.admin-prompt-guidance { margin-bottom:14px; border-top:1px solid #eef2f6; border-bottom:1px solid #eef2f6; }.admin-prompt-guidance summary { padding:10px 0; color:#475569; font-size:11px; font-weight:700; cursor:pointer; }.admin-prompt-guidance>div { display:grid; gap:7px; padding:0 0 11px; color:#64748b; font-size:10px; line-height:1.5; }.admin-prompt-guidance p { margin:0; }.admin-prompt-guidance strong { color:#475569; }.admin-prompt-status-note { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:10px 0 2px; color:#64748b; font-size:10px; }.admin-prompt-status-note>div:first-child { display:grid; gap:3px; }.admin-prompt-status-note strong { color:#475569; font-size:11px; }.prompt-warning { display:flex; align-items:flex-start; gap:4px; max-width:44%; color:#b45309; line-height:1.35; }.prompt-warning svg { flex:0 0 auto; }
.admin-prompt-editor-footer>div { display:flex; align-items:center; gap:6px; }.admin-prompt-editor-footer .admin-ghost-button,.admin-prompt-editor-footer .admin-primary-button { min-height:32px; padding:0 10px; font-size:11px; }
.admin-prompt-overlay { position:fixed; z-index:150; inset:0; display:flex; justify-content:flex-end; background:rgba(15,23,42,.3); backdrop-filter:blur(3px); }.prompt-dialog-overlay { align-items:center; justify-content:center; padding:16px; }.prompt-small-dialog { width:min(430px,100%); border:1px solid #e2e8f0; border-radius:9px; background:#fff; box-shadow:0 20px 48px rgba(15,23,42,.2); }.prompt-small-dialog>header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:16px 18px 12px; border-bottom:1px solid #eef2f6; }.prompt-small-dialog header span { color:#2563eb; font-size:10px; font-weight:750; letter-spacing:.06em; }.prompt-small-dialog h3 { margin:4px 0 0; color:#1f2937; font-size:16px; }.prompt-small-dialog>label { display:grid; gap:6px; padding:15px 18px; color:#475569; font-size:11px; font-weight:650; }.prompt-small-dialog textarea { box-sizing:border-box; width:100%; padding:8px; border:1px solid #dbe3ec; border-radius:6px; outline:0; resize:vertical; color:#334155; font:11px/1.5 inherit; }.prompt-small-dialog footer { display:flex; justify-content:flex-end; gap:7px; padding:12px 18px 16px; }.prompt-dialog-copy { margin:0; padding:16px 18px; color:#64748b; font-size:12px; line-height:1.6; }.prompt-copy-options { display:grid; gap:7px; padding:14px 18px 18px; }.prompt-copy-options button { display:flex; align-items:flex-start; gap:9px; padding:10px; border:1px solid #e2e8f0; border-radius:7px; background:#fff; color:#2563eb; text-align:left; }.prompt-copy-options button:hover { border-color:#bfdbfe; background:#f8fbff; }.prompt-copy-options span { display:grid; gap:3px; }.prompt-copy-options strong { color:#334155; font-size:12px; }.prompt-copy-options small { color:#94a3b8; font-size:10px; }
.admin-prompt-history-drawer,.admin-prompt-test-drawer { display:flex; flex-direction:column; width:min(620px,100vw); height:100%; overflow:hidden; border-left:1px solid #e2e8f0; background:#fff; box-shadow:-18px 0 40px rgba(15,23,42,.15); }.admin-prompt-test-drawer { width:min(900px,100vw); }.admin-prompt-drawer-header { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; padding:18px 20px 14px; border-bottom:1px solid #e6ebf1; }.admin-prompt-drawer-header span { color:#2563eb; font-size:10px; font-weight:800; letter-spacing:.1em; }.admin-prompt-drawer-header h3 { margin:4px 0 0; color:#1f2937; font-size:18px; }.admin-prompt-drawer-header small { display:block; margin-top:4px; color:#94a3b8; font-size:10px; }.admin-prompt-icon-button { display:grid; place-items:center; width:28px; height:28px; border:0; border-radius:5px; background:transparent; color:#64748b; }.admin-prompt-icon-button:hover { background:#f1f5f9; }.admin-prompt-history-list { min-height:0; overflow-y:auto; padding:16px 20px; }.admin-prompt-history-list article { display:grid; gap:8px; margin-bottom:8px; padding:12px; border:1px solid #e5ebf2; border-radius:7px; }.admin-prompt-history-list article.selected { border-color:#93c5fd; background:#f8fbff; }.admin-prompt-history-list article header { display:flex; align-items:center; justify-content:space-between; gap:8px; }.prompt-history-select { display:inline-flex; align-items:center; gap:5px; border:0; background:transparent; color:#2563eb; font-size:12px; font-weight:700; }.prompt-history-select.selected { color:#1d4ed8; }.admin-prompt-history-list article time { color:#94a3b8; font-size:10px; }.admin-prompt-history-list article p { margin:0; color:#64748b; font-size:11px; }.prompt-history-diff { margin-top:14px; border:1px solid #e2e8f0; border-radius:7px; overflow:hidden; }.prompt-history-diff header { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:8px 10px; border-bottom:1px solid #e5e7eb; color:#475569; font-size:10px; }.prompt-history-diff header span { color:#94a3b8; }.prompt-history-diff pre { display:grid; gap:2px; max-height:240px; margin:0; padding:9px; overflow:auto; background:#f8fafc; color:#475569; font:10px/1.55 Consolas,monospace; white-space:pre-wrap; }.prompt-history-diff pre span.added { padding:2px; background:#dcfce7; color:#15803d; }.prompt-history-diff pre span.removed { padding:2px; background:#fee2e2; color:#b91c1c; }
.prompt-test-workspace { display:grid; grid-template-columns:300px minmax(0,1fr); gap:14px; min-height:0; flex:1; padding:18px 20px; overflow:hidden; }.prompt-test-inputs,.prompt-test-result { min-height:0; overflow:auto; }.prompt-test-inputs { display:grid; align-content:start; gap:11px; }.prompt-test-inputs label { display:grid; gap:6px; color:#526174; font-size:11px; font-weight:700; }.prompt-test-inputs textarea,.prompt-test-inputs input { box-sizing:border-box; width:100%; padding:8px; border:1px solid #dbe3ec; border-radius:6px; outline:0; color:#334155; font-size:11px; line-height:1.5; resize:vertical; }.prompt-test-inputs input { height:32px; resize:none; }.prompt-test-inputs .admin-primary-button { justify-self:start; }.prompt-test-result { display:grid; align-content:start; gap:10px; padding-left:14px; border-left:1px solid #eef2f6; }.prompt-test-model { display:flex; align-items:center; gap:7px; padding:9px; border:1px solid #bbf7d0; border-radius:7px; background:#f0fdf4; color:#15803d; }.prompt-test-model span { display:grid; gap:2px; }.prompt-test-model strong { font-size:12px; }.prompt-test-model small { color:#64748b; font-size:10px; }.prompt-test-answer,.prompt-effective-preview pre { margin:0; padding:12px; overflow:auto; border-radius:7px; background:#111827; color:#e5e7eb; font:11px/1.6 Consolas,monospace; white-space:pre-wrap; }.prompt-test-answer { min-height:220px; }.prompt-test-metrics { display:flex; flex-wrap:wrap; gap:5px; }.prompt-test-metrics span { padding:4px 6px; border-radius:4px; background:#f1f5f9; color:#64748b; font-size:10px; }.prompt-effective-preview summary { color:#475569; font-size:11px; font-weight:700; cursor:pointer; }.prompt-effective-preview pre { max-height:260px; margin-top:8px; }.admin-prompt-empty { display:grid; place-items:center; min-height:180px; color:#94a3b8; font-size:11px; text-align:center; }
:global(html[data-theme='dark']) .admin-prompt-notice,:global(html[data-theme='dark']) .admin-prompt-variable-area { border-color:rgba(96,165,250,.3); background:rgba(30,64,175,.18); }:global(html[data-theme='dark']) .admin-prompt-version-input,:global(html[data-theme='dark']) .admin-prompt-detail-grid select,:global(html[data-theme='dark']) .prompt-editor-tools>button,:global(html[data-theme='dark']) .prompt-variable-popover,:global(html[data-theme='dark']) .admin-prompt-variable-tags button,:global(html[data-theme='dark']) .prompt-small-dialog,:global(html[data-theme='dark']) .prompt-small-dialog textarea,:global(html[data-theme='dark']) .admin-prompt-history-drawer,:global(html[data-theme='dark']) .admin-prompt-test-drawer,:global(html[data-theme='dark']) .prompt-test-inputs textarea,:global(html[data-theme='dark']) .prompt-test-inputs input { border-color:#334155; background:#111827; color:#e2e8f0; }:global(html[data-theme='dark']) .admin-prompt-type-help,:global(html[data-theme='dark']) .prompt-test-metrics span { border-color:#334155; background:#1e293b; color:#cbd5e1; }:global(html[data-theme='dark']) .prompt-variable-popover button { background:#0f172a; }:global(html[data-theme='dark']) .prompt-small-dialog>header,:global(html[data-theme='dark']) .admin-prompt-drawer-header,:global(html[data-theme='dark']) .prompt-test-result { border-color:#334155; }:global(html[data-theme='dark']) .prompt-small-dialog h3,:global(html[data-theme='dark']) .admin-prompt-drawer-header h3 { color:#f8fafc; }
@media (max-width:820px) { .admin-prompt-detail-grid,.prompt-test-workspace { grid-template-columns:1fr; }.prompt-test-result { padding-left:0; border-left:0; border-top:1px solid #eef2f6; padding-top:14px; }.prompt-editor-tools>span { display:none; } }
</style>
