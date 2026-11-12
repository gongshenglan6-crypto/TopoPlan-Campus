<script setup>
import { AlertTriangle, Bot, Check, ChevronDown, CircleHelp, Copy, Eye, EyeOff, KeyRound, LoaderCircle, MoreHorizontal, Play, Plus, Save, Settings2, Trash2, X } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { createModelChannel, deleteModelChannel, listModelChannels, moveModelChannel, setModelChannelPrimary, setModelChannelStatus, testModelChannel, updateModelChannel } from '../../api/admin'

const channels = ref([])

const modelCategoryTabs = [
  { key: 'all', label: '全部模型' },
  { key: 'chat', label: '对话模型' },
  { key: 'reasoning', label: '推理模型' },
  { key: 'embedding', label: 'Embedding 模型' },
  { key: 'search', label: '搜索模型' }
]
const modelTypeOptions = [
  { key: 'chat', label: '对话模型' },
  { key: 'reasoning', label: '推理模型' },
  { key: 'embedding', label: 'Embedding 模型' },
  { key: 'search', label: '搜索模型' }
]
const modelUseCases = [
  { key: 'requirements-analysis', label: '需求分析' },
  { key: 'topology-generation', label: '拓扑生成' },
  { key: 'network-plan', label: '网络方案生成' },
  { key: 'risk-analysis', label: '风险分析' },
  { key: 'ai-chat', label: 'AI 问答' },
  { key: 'document-generation', label: '工程文档生成' },
  { key: 'knowledge-embedding', label: '知识库 Embedding' },
  { key: 'web-search', label: '联网搜索辅助' }
]
const activeModelCategory = ref('all')
const filteredChannels = computed(() => channels.value.filter((channel) => {
  if (activeModelCategory.value === 'all') return true
  return getModelType(channel) === activeModelCategory.value
}))

const modalTabs = [
  { key: 'basic', label: '基础配置' },
  { key: 'parameters', label: '模型参数' },
  { key: 'rate', label: '速率与并发' },
  { key: 'billing', label: '计费与成本' }
]

const modalOpen = ref(false)
const editingChannelId = ref(null)
const activeModalTab = ref('basic')
const draft = ref(createDraft())
const apiKeyVisible = ref(false)
const isSaving = ref(false)
const formError = ref('')
const toast = ref('')
const statusUpdating = ref({})
const testingChannelId = ref(null)
const testResult = ref(null)
const modelTypeMenuOpen = ref(false)
const modelTypeSelect = ref(null)
const modelApiModal = ref(null)
const tooltipPopover = ref(null)
const activeTooltip = ref({ visible: false, positioned: false, text: '', left: 0, top: 0, maxWidth: 260, arrowLeft: 12, placement: 'top' })
const actionMenuId = ref(null)
const deleteDialogOpen = ref(false)
const deleteTarget = ref(null)
const deleteForce = ref(false)
const isDeleting = ref(false)
let toastTimer = 0
let tooltipTimer = 0
let tooltipTarget = null

const modalTitle = computed(() => editingChannelId.value ? '编辑模型 API' : '新增模型 API')
const selectedModelType = computed(() => (
  modelTypeOptions.find((type) => type.key === inferModelType(draft.value)) || modelTypeOptions[0]
))
const baseUrlInvalid = computed(() => (
  draft.value.baseUrl.trim().length > 0 && !isValidBaseUrl(draft.value.baseUrl)
))

function createDraft(channel = {}) {
  return {
    provider: channel.provider ?? '',
    modelId: channel.modelId ?? '',
    alias: channel.alias ?? '',
    baseUrl: channel.baseUrl ?? '',
    apiKey: channel.apiKey ?? '',
    apiKeyConfigured: Boolean(channel.apiKeyConfigured),
    contextLimit: channel.contextLimit ?? 128000,
    maxTokens: channel.maxTokens ?? 4096,
    temperature: channel.temperature ?? 0.7,
    topP: channel.topP ?? 0.95,
    rpm: channel.rpm ?? 600,
    tpm: channel.tpm ?? 1000000,
    maxConcurrency: channel.maxConcurrency ?? 50,
    timeout: channel.timeout ?? 60,
    inputPrice: channel.inputPrice ?? 0.14,
    outputPrice: channel.outputPrice ?? 0.28,
    cachedTokenPrice: channel.cachedTokenPrice ?? 0,
    currency: channel.currency ?? 'USD',
    priceUnit: channel.priceUnit ?? 1000000,
    quotaMultiplier: channel.quotaMultiplier ?? 1,
    priority: channel.priority ?? 100,
    userSelectable: channel.userSelectable ?? true,
    allowedTiers: channel.allowedTiers ?? 'free,standard,pro',
    modelType: inferModelType(channel),
    useCases: compatibleUseCases({ ...channel, modelType: inferModelType(channel) })
  }
}

function resetModalState(channel) {
  hideTooltip()
  draft.value = createDraft(channel)
  activeModalTab.value = 'basic'
  apiKeyVisible.value = false
  modelTypeMenuOpen.value = false
  formError.value = ''
}

function openCreateModal() {
  editingChannelId.value = null
  resetModalState()
  modalOpen.value = true
}

function openEditModal(channel) {
  editingChannelId.value = channel.id
  resetModalState(channel)
  modalOpen.value = true
}

function closeModal() {
  if (isSaving.value) return
  hideTooltip()
  modalOpen.value = false
  editingChannelId.value = null
  modelTypeMenuOpen.value = false
  formError.value = ''
}

function toggleModelTypeMenu() {
  modelTypeMenuOpen.value = !modelTypeMenuOpen.value
}

function closeModelTypeMenu() {
  modelTypeMenuOpen.value = false
}

function chooseModelType(type) {
  draft.value.modelType = type.key
  draft.value.useCases = compatibleUseCases(draft.value)
  closeModelTypeMenu()
}

function handleDocumentPointerDown(event) {
  if (!modelTypeSelect.value?.contains(event.target)) closeModelTypeMenu()
  if (!event.target.closest?.('.admin-model-row-actions')) actionMenuId.value = null
}

function toggleActionMenu(channel) {
  actionMenuId.value = String(actionMenuId.value) === String(channel.id) ? null : String(channel.id)
}

async function reorderModel(channel, direction) {
  actionMenuId.value = null
  try {
    const updated = await moveModelChannel(channel.id, direction)
    Object.assign(channel, updated)
    await loadModelChannels()
    showToast(direction === 'up' ? '模型已上移' : '模型已下移')
  } catch (error) { showToast(error.message || '模型顺序更新失败') }
}

async function makePrimaryModel(channel) {
  actionMenuId.value = null
  try {
    const updated = await setModelChannelPrimary(channel.id)
    Object.assign(channel, updated)
    await loadModelChannels()
    showToast('已设为主模型')
  } catch (error) { showToast(error.message || '主模型设置失败') }
}

function openDeleteDialog(channel) {
  actionMenuId.value = null
  deleteTarget.value = channel
  deleteForce.value = false
  deleteDialogOpen.value = true
}

function closeDeleteDialog() {
  if (isDeleting.value) return
  deleteDialogOpen.value = false
  deleteTarget.value = null
  deleteForce.value = false
}

async function confirmDeleteModel() {
  const channel = deleteTarget.value
  if (!channel || isDeleting.value) return
  isDeleting.value = true
  try {
    const result = await deleteModelChannel(channel.id, deleteForce.value)
    channels.value = channels.value.filter((item) => String(item.id) !== String(channel.id))
    deleteDialogOpen.value = false
    deleteTarget.value = null
    deleteForce.value = false
    showToast(result?.replacementModelName ? `模型已删除，已自动切换到 ${result.replacementModelName}` : '模型已删除')
  } catch (error) {
    if (error?.code === 'LAST_CHAT_MODEL') {
      deleteForce.value = true
      showToast('当前是最后一个可用对话模型，请再次确认删除。')
    } else if (error?.code === 'MODEL_NOT_FOUND') {
      channels.value = channels.value.filter((item) => String(item.id) !== String(channel.id))
      closeDeleteDialog()
      showToast('该模型已不存在，列表已刷新')
    } else {
      showToast(error?.message || '模型删除失败，请重试')
    }
  } finally {
    isDeleting.value = false
  }
}

function clamp(value, minimum, maximum) {
  return Math.min(Math.max(value, minimum), Math.max(minimum, maximum))
}

function getTooltipButton(target) {
  return target instanceof Element ? target.closest('.admin-model-tooltip') : null
}

function hideTooltip() {
  window.clearTimeout(tooltipTimer)
  tooltipTimer = 0
  tooltipTarget = null
  activeTooltip.value.visible = false
}

function positionTooltip(button) {
  const modalRect = modelApiModal.value?.getBoundingClientRect()
  const popover = tooltipPopover.value
  if (!modalRect || !popover || tooltipTarget !== button) return

  const targetRect = button.getBoundingClientRect()
  const popoverRect = popover.getBoundingClientRect()
  const margin = 8
  const gap = 8
  const canShowAbove = targetRect.top - modalRect.top >= popoverRect.height + gap + margin
  const canShowBelow = modalRect.bottom - targetRect.bottom >= popoverRect.height + gap + margin
  const placement = canShowAbove || !canShowBelow ? 'top' : 'bottom'
  const preferredTop = placement === 'top'
    ? targetRect.top - popoverRect.height - gap
    : targetRect.bottom + gap
  const left = clamp(targetRect.left, modalRect.left + margin, modalRect.right - popoverRect.width - margin)
  const top = clamp(preferredTop, modalRect.top + margin, modalRect.bottom - popoverRect.height - margin)
  const arrowLeft = clamp(targetRect.left + (targetRect.width / 2) - left, 12, popoverRect.width - 12)

  activeTooltip.value = {
    ...activeTooltip.value,
    left,
    top,
    arrowLeft,
    placement,
    positioned: true
  }
}

function showTooltip(button) {
  const modalRect = modelApiModal.value?.getBoundingClientRect()
  if (!modalRect || !button.isConnected || tooltipTarget !== button) return

  activeTooltip.value = {
    visible: true,
    positioned: false,
    text: button.dataset.tooltip || '',
    left: modalRect.left + 8,
    top: modalRect.top + 8,
    maxWidth: Math.min(260, modalRect.width - 16),
    arrowLeft: 12,
    placement: 'top'
  }
  nextTick(() => positionTooltip(button))
}

function handleTooltipPointerOver(event) {
  if (event.pointerType && event.pointerType !== 'mouse') return
  const button = getTooltipButton(event.target)
  if (!button || button.contains(event.relatedTarget)) return

  hideTooltip()
  tooltipTarget = button
  tooltipTimer = window.setTimeout(() => showTooltip(button), 240)
}

function handleTooltipPointerOut(event) {
  const button = getTooltipButton(event.target)
  if (!button || button.contains(event.relatedTarget)) return
  if (tooltipTarget === button) hideTooltip()
}

function updateTooltipPosition() {
  if (activeTooltip.value.visible && tooltipTarget) positionTooltip(tooltipTarget)
}

function setActiveModalTab(tabKey) {
  hideTooltip()
  activeModalTab.value = tabKey
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

function isStatusUpdating(channel) {
  return Boolean(statusUpdating.value[channel.id])
}

function setStatusUpdating(channel, value) {
  const next = { ...statusUpdating.value }
  if (value) next[channel.id] = true
  else delete next[channel.id]
  statusUpdating.value = next
}

function requestStatusToggle(channel) {
  if (isStatusUpdating(channel)) return
  persistChannelStatus(channel, !channel.active)
}

async function persistChannelStatus(channel, enabled) {
  setStatusUpdating(channel, true)
  try {
    const saved = await setModelChannelStatus(channel.id, enabled)
    Object.assign(channel, {
      ...saved,
      id: saved.key ?? channel.id,
      active: Boolean(saved.active ?? saved.enabled),
      enabled: Boolean(saved.enabled ?? saved.active)
    })
    showToast(enabled ? '模型已启用' : '模型已停用')
    return true
  } catch (error) {
    const fallback = enabled ? '启用失败，请稍后重试' : '停用失败，请稍后重试'
    showToast(error?.message || fallback)
    return false
  } finally {
    setStatusUpdating(channel, false)
  }
}

async function testChannel(channel) {
  if (String(testingChannelId.value) === String(channel.id)) return
  testingChannelId.value = channel.id
  Object.assign(channel, {
    lastTestStatus: 'TESTING',
    lastTestErrorCode: null,
    lastTestErrorMessage: null,
    lastTestLatency: 0
  })
  try {
    const result = await testModelChannel(channel.id)
    Object.assign(channel, {
      latency: result.latencyMs,
      lastTestStatus: 'PASSED',
      lastTestLatency: result.latencyMs,
      lastTestErrorCode: null,
      lastTestErrorMessage: null
    })
    testResult.value = {
      success: true,
      provider: result.provider,
      modelId: result.modelId,
      requestType: result.requestType,
      adapter: result.adapter,
      httpStatus: result.httpStatus,
      latencyMs: result.latencyMs,
      inputTokens: result.inputTokens,
      outputTokens: result.outputTokens
    }
  } catch (error) {
    const latencyMs = Number(error?.details?.latencyMs) || 0
    const httpStatus = Number(error?.details?.httpStatus) || Number(error?.status) || 0
    Object.assign(channel, {
      lastTestStatus: 'FAILED',
      lastTestLatency: latencyMs,
      lastTestErrorCode: error?.code || 'PROVIDER_ERROR',
      lastTestErrorMessage: error?.message || '模型测试失败'
    })
    testResult.value = {
      success: false,
      provider: error?.details?.provider || channel.provider,
      modelId: error?.details?.modelId || channel.modelId,
      requestType: error?.details?.requestType,
      httpStatus,
      latencyMs,
      errorCode: error?.code || 'PROVIDER_ERROR',
      errorMessage: error?.message || '模型测试失败',
      providerMessage: error?.details?.providerMessage
    }
  } finally {
    testingChannelId.value = null
  }
}

function closeTestResult() {
  testResult.value = null
}

function isTesting(channel) {
  return String(testingChannelId.value) === String(channel.id) || channel.lastTestStatus === 'TESTING'
}

function testStatus(channel) {
  if (isTesting(channel)) return 'TESTING'
  return channel.lastTestStatus || 'NOT_TESTED'
}

function testStatusLabel(channel) {
  return ({ NOT_TESTED: '未测试', TESTING: '测试中', PASSED: '测试通过', FAILED: '测试失败' })[testStatus(channel)] || '未测试'
}

function testLatency(channel) {
  const latency = Number(channel.lastTestLatency ?? (testStatus(channel) === 'PASSED' ? channel.latency : 0))
  return latency > 0 ? `${latency}ms` : ''
}

function isValidBaseUrl(value) {
  try {
    const url = new URL(value.trim())
    return url.protocol === 'https:' || url.protocol === 'http:'
  } catch {
    return false
  }
}

function isPositiveNumber(value) {
  return Number.isFinite(Number(value)) && Number(value) > 0
}

function showFieldError(tab, message) {
  activeModalTab.value = tab
  formError.value = message
  showToast(message)
  return false
}

function validateDraft() {
  const { value } = draft
  if (!value.provider.trim() || !value.modelId.trim() || !value.alias.trim()) {
    return showFieldError('basic', '请补全提供商、模型真实 ID 与显示别名。')
  }
  if (!isValidBaseUrl(value.baseUrl)) return showFieldError('basic', 'Base URL 仅支持有效的 HTTP 或 HTTPS 地址。')
  if (!value.apiKey.trim() && !value.apiKeyConfigured) return showFieldError('basic', '请输入 API Key 后再保存。')
  if (!isPositiveNumber(value.contextLimit) || !isPositiveNumber(value.maxTokens)) {
    return showFieldError('parameters', '上下文窗口和最大生成 Token 必须大于 0。')
  }
  if (!Number.isFinite(Number(value.temperature)) || Number(value.temperature) < 0 || Number(value.temperature) > 2) {
    return showFieldError('parameters', '默认温度必须处于 0.0 到 2.0 之间。')
  }
  if (!Number.isFinite(Number(value.topP)) || Number(value.topP) < 0 || Number(value.topP) > 1) {
    return showFieldError('parameters', 'Top P 必须处于 0.0 到 1.0 之间。')
  }
  if (![value.rpm, value.tpm, value.maxConcurrency, value.timeout].every(isPositiveNumber)) {
    return showFieldError('rate', '速率、并发和超时时间必须大于 0。')
  }
  if (![value.inputPrice, value.outputPrice, value.cachedTokenPrice, value.quotaMultiplier].every((item) => Number.isFinite(Number(item)) && Number(item) >= 0) || !isPositiveNumber(value.priceUnit)) {
    return showFieldError('billing', '计费单价和扣费倍率不能小于 0。')
  }
  formError.value = ''
  return true
}

function applyQuickContextLimit(value) {
  draft.value.contextLimit = value
}

async function copyApiKey() {
  if (!draft.value.apiKey) {
    showToast(draft.value.apiKeyConfigured ? 'API Key 已配置，服务器不会回显原始密钥。' : '请先输入 API Key。')
    return
  }
  try {
    await navigator.clipboard.writeText(draft.value.apiKey)
    showToast('API Key 已复制到剪贴板')
  } catch {
    showToast('浏览器未允许复制，请手动复制 API Key')
  }
}

function inferModelType(channel) {
  const modelId = String(channel.modelId || '').toLowerCase()
  if (modelId.includes('embedding') || modelId.includes('bge')) return 'embedding'
  if (modelId.includes('search') || modelId.includes('serp')) return 'search'
  if (['chat', 'reasoning', 'embedding', 'search'].includes(channel.modelType)) return channel.modelType
  if (modelId.includes('coder') || modelId.includes('reasoner') || modelId.includes('r1')) return 'reasoning'
  return 'chat'
}

function getModelType(channel) {
  return inferModelType(channel)
}

function inferModelUseCases(channel) {
  const type = getModelType(channel)
  if (type === 'embedding') return ['knowledge-embedding']
  if (type === 'search') return ['web-search']
  if (type === 'reasoning') return ['requirements-analysis', 'topology-generation', 'risk-analysis']
  return ['network-plan', 'ai-chat', 'document-generation']
}

function compatibleUseCases(channel) {
  const type = getModelType(channel)
  if (type === 'embedding') return ['knowledge-embedding']
  if (type === 'search') return ['web-search']
  return Array.isArray(channel.useCases) && channel.useCases.length ? channel.useCases : inferModelUseCases(channel)
}

function typeLabel(channel) {
  return modelTypeOptions.find((item) => item.key === getModelType(channel))?.label || '未分类'
}

function useCaseLabels(channel) {
  const values = compatibleUseCases(channel)
  return values.map((key) => modelUseCases.find((item) => item.key === key)?.label || key)
}

function normalizeNumber(value, minimum = 0) {
  return Math.max(minimum, Number(value) || 0)
}

async function saveChannel() {
  if (isSaving.value || !validateDraft()) return

  isSaving.value = true
  const source = draft.value
  const modelType = getModelType(source)
  const payload = {
    provider: source.provider.trim(),
    modelId: source.modelId.trim(),
    alias: source.alias.trim(),
    baseUrl: source.baseUrl.trim(),
    contextLimit: normalizeNumber(source.contextLimit, 1),
    maxTokens: normalizeNumber(source.maxTokens, 1),
    temperature: Math.min(2, normalizeNumber(source.temperature)),
    topP: Math.min(1, normalizeNumber(source.topP)),
    rpm: normalizeNumber(source.rpm, 1),
    tpm: normalizeNumber(source.tpm, 1),
    maxConcurrency: normalizeNumber(source.maxConcurrency, 1),
    timeout: normalizeNumber(source.timeout, 1),
    inputPrice: normalizeNumber(source.inputPrice),
    outputPrice: normalizeNumber(source.outputPrice),
    cachedTokenPrice: normalizeNumber(source.cachedTokenPrice),
    currency: source.currency.trim().toUpperCase(),
    priceUnit: normalizeNumber(source.priceUnit, 1),
    quotaMultiplier: normalizeNumber(source.quotaMultiplier),
    priority: normalizeNumber(source.priority),
    userSelectable: Boolean(source.userSelectable),
    allowedTiers: source.allowedTiers.trim(),
    modelType,
    useCases: compatibleUseCases({ ...source, modelType })
  }
  if (source.apiKey.trim() && source.apiKey.trim() !== 'configured') payload.apiKey = source.apiKey.trim()

  try {
    const saved = editingChannelId.value
      ? await updateModelChannel(editingChannelId.value, payload)
      : await createModelChannel({ ...payload, active: true, concurrency: 0 })
    const channel = { ...saved, id: saved.key ?? editingChannelId.value, active: saved.active ?? true, concurrency: saved.concurrency ?? 0 }
    if (editingChannelId.value) {
      const existing = channels.value.find((item) => item.id === editingChannelId.value)
      if (existing) Object.assign(existing, channel)
    } else {
      channels.value.unshift(channel)
    }
    showToast('Model channel configuration saved.')
    isSaving.value = false
    closeModal()
  } catch (error) {
    const message = error.message || '模型配置保存失败，请稍后重试。'
    formError.value = message
    showToast(message)
  } finally {
    isSaving.value = false
  }
}

function serializableChannel(channel) {
  const { id, key, ...payload } = channel
  return payload
}

async function loadModelChannels() {
  try {
    const stored = await listModelChannels()
    if (stored.length) {
      channels.value = stored.map((channel) => ({ ...channel, id: channel.key ?? channel.id, apiKey: '', apiKeyConfigured: Boolean(channel.apiKeyConfigured), active: channel.active ?? true, concurrency: channel.concurrency ?? 0 }))
      return
    }
    channels.value = []
  } catch (error) {
    showToast(error.message || 'Model channels failed to load.')
  }
}

onMounted(() => {
  loadModelChannels()
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('scroll', updateTooltipPosition, true)
  window.addEventListener('resize', updateTooltipPosition)
})

onBeforeUnmount(() => {
  window.clearTimeout(toastTimer)
  hideTooltip()
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('scroll', updateTooltipPosition, true)
  window.removeEventListener('resize', updateTooltipPosition)
})
</script>

<template>
  <section class="admin-section-page admin-model-api-page">
    <header class="admin-page-intro compact admin-model-api-intro">
      <div>
        <p>模型接入与调用策略</p>
        <h2>AI 模型与 API 管理</h2>
        <span>集中维护模型渠道、网络工程用途绑定、实时并发能力和调用速率限制。</span>
      </div>
      <button type="button" class="admin-primary-button admin-model-create" @click="openCreateModal"><Plus :size="16" /> 新增模型 API</button>
    </header>

    <section class="admin-card admin-model-api-card">
      <div class="admin-model-category-bar flex items-center justify-between border-b border-gray-200 px-5 py-3">
        <nav class="admin-model-category-tabs" aria-label="模型类型筛选" role="tablist">
          <button v-for="tab in modelCategoryTabs" :key="tab.key" type="button" role="tab" :aria-selected="activeModelCategory === tab.key" :class="{ active: activeModelCategory === tab.key }" @click="activeModelCategory = tab.key">{{ tab.label }}</button>
        </nav>
        <div class="admin-model-micro-stats" aria-label="模型渠道统计">
          <span>共计 <strong>{{ channels.length }}</strong> 个渠道</span>
          <span class="admin-model-stats-divider" aria-hidden="true"></span>
          <span class="admin-model-active-badge"><i aria-hidden="true"></i>{{ channels.filter((channel) => channel.active).length }} 个可用</span>
        </div>
      </div>
      <div class="admin-model-api-table-wrap">
        <table class="admin-model-api-table">
          <colgroup>
            <col class="admin-model-api-col-name" />
            <col class="admin-model-api-col-type" />
            <col class="admin-model-api-col-use-case" />
            <col class="admin-model-api-col-concurrency" />
            <col class="admin-model-api-col-status" />
            <col class="admin-model-api-col-actions" />
          </colgroup>
          <thead><tr><th>模型提供商与名称</th><th>模型类型</th><th>主要用途</th><th>实时并发</th><th>状态</th><th><span class="sr-only">编辑</span></th></tr></thead>
          <tbody>
            <tr v-for="channel in filteredChannels" :key="channel.id">
              <td>
                <div class="admin-model-provider"><span><Bot :size="17" /></span><div><strong>{{ channel.alias }}</strong><small>{{ channel.provider }} · {{ channel.modelId }}</small></div></div>
              </td>
              <td><span class="admin-model-type-badge">{{ typeLabel(channel) }}</span></td>
              <td><div class="admin-model-use-cases"><span v-for="label in useCaseLabels(channel).slice(0, 2)" :key="label">{{ label }}</span><small v-if="useCaseLabels(channel).length > 2">+{{ useCaseLabels(channel).length - 2 }}</small></div></td>
              <td><span class="admin-model-concurrency"><i :class="{ inactive: !channel.active || channel.concurrency === 0 }"></i>{{ channel.concurrency }} / {{ channel.maxConcurrency }}</span><small class="admin-model-latency" :class="{ unhealthy: testStatus(channel) === 'FAILED', testing: testStatus(channel) === 'TESTING' }">{{ testStatusLabel(channel) }}<template v-if="testLatency(channel)"> · {{ testLatency(channel) }}</template></small></td>
              <td class="admin-model-status-cell"><button type="button" class="admin-model-switch" :class="{ active: channel.active, 'is-loading': isStatusUpdating(channel) }" :disabled="isStatusUpdating(channel)" role="switch" :aria-checked="channel.active" :aria-busy="isStatusUpdating(channel)" :aria-label="`${channel.alias}${isStatusUpdating(channel) ? '状态更新中' : channel.active ? '已启用，点击停用' : '已停用，点击启用'}`" @click="requestStatusToggle(channel)"><span class="admin-model-switch-track" aria-hidden="true"></span><span class="admin-model-switch-spinner-slot" aria-hidden="true"><LoaderCircle v-if="isStatusUpdating(channel)" class="admin-model-switch-spinner" :size="14" /></span><b class="admin-model-switch-label">{{ isStatusUpdating(channel) ? '处理中' : channel.active ? '启用' : '停用' }}</b></button></td>
              <td><div class="admin-model-row-actions"><button type="button" class="admin-model-icon-button" :class="{ 'is-testing': isTesting(channel) }" :disabled="isTesting(channel)" :aria-busy="isTesting(channel)" :aria-label="`${isTesting(channel) ? '正在测试' : '测试'} ${channel.alias}`" title="测试模型连接" @click="testChannel(channel)"><LoaderCircle v-if="isTesting(channel)" class="admin-model-test-spinner" :size="16" /><Play v-else :size="16" /></button><button type="button" class="admin-model-icon-button" :aria-label="`编辑 ${channel.alias}`" title="编辑模型 API" @click="openEditModal(channel)"><Settings2 :size="16" /></button><div class="admin-model-action-menu-wrap"><button type="button" class="admin-model-icon-button" :aria-expanded="String(actionMenuId) === String(channel.id)" :aria-label="`更多操作 ${channel.alias}`" title="更多操作" @click.stop="toggleActionMenu(channel)"><MoreHorizontal :size="17" /></button><div v-if="String(actionMenuId) === String(channel.id)" class="admin-model-action-menu" role="menu"><button type="button" role="menuitem" @click="openEditModal(channel)"><Settings2 :size="14" />编辑</button><button type="button" role="menuitem" @click="testChannel(channel)"><Play :size="14" />测试</button><button type="button" role="menuitem" @click="makePrimaryModel(channel)">设为主模型</button><button type="button" role="menuitem" @click="reorderModel(channel, 'up')">上移</button><button type="button" role="menuitem" @click="reorderModel(channel, 'down')">下移</button><button type="button" role="menuitem" @click="requestStatusToggle(channel)"><span class="admin-model-action-status-dot" :class="{ active: channel.active }" />{{ channel.active ? '禁用' : '启用' }}</button><button type="button" role="menuitem" class="danger" @click="openDeleteDialog(channel)"><Trash2 :size="14" />删除</button></div></div></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <Transition name="admin-modal">
      <div v-if="modalOpen" class="admin-modal-backdrop" @pointerdown.self="closeModal">
        <form ref="modelApiModal" novalidate class="admin-modal-card admin-model-api-modal" role="dialog" aria-modal="true" aria-labelledby="model-api-modal-title" @submit.prevent="saveChannel" @keydown.esc.prevent="closeModal" @pointerover="handleTooltipPointerOver" @pointerout="handleTooltipPointerOut">
          <header class="admin-model-modal-header">
            <div><span>模型渠道配置</span><h3 id="model-api-modal-title">{{ modalTitle }}</h3></div>
            <button type="button" class="admin-close-button" aria-label="关闭模型 API 配置" :disabled="isSaving" @click="closeModal"><X :size="18" /></button>
          </header>

          <nav class="admin-model-api-tabs" aria-label="模型 API 配置分区" role="tablist">
            <button
              v-for="tab in modalTabs"
              :id="`model-api-tab-${tab.key}`"
              :key="tab.key"
              type="button"
              role="tab"
              :aria-controls="`model-api-panel-${tab.key}`"
              :aria-selected="activeModalTab === tab.key"
              :class="{ active: activeModalTab === tab.key }"
              @click="setActiveModalTab(tab.key)"
            >{{ tab.label }}</button>
          </nav>

          <div class="admin-model-api-form" :aria-busy="isSaving">
            <section v-show="activeModalTab === 'basic'" id="model-api-panel-basic" class="admin-model-tab-panel" role="tabpanel" aria-labelledby="model-api-tab-basic">
              <div class="admin-model-form-grid">
                <label>
                  <span class="admin-model-field-label">模型提供商 <button type="button" class="admin-model-tooltip" aria-label="模型提供商说明" data-tooltip="模型所属的服务提供商，例如 OpenAI、DeepSeek、阿里云等。"><CircleHelp :size="14" /></button></span>
                  <input v-model="draft.provider" list="model-providers" required placeholder="例如：DeepSeek" />
                  <datalist id="model-providers"><option value="OpenAI" /><option value="Alibaba Cloud / Qwen" /><option value="Zhipu AI / GLM" /><option value="DeepSeek" /><option value="OpenAI Compatible" /></datalist>
                </label>
                <label>
                  <span class="admin-model-field-label">模型真实 ID <button type="button" class="admin-model-tooltip" aria-label="模型真实 ID 说明" data-tooltip="调用 API 时实际使用的模型标识，例如 gpt-4.1。"><CircleHelp :size="14" /></button></span>
                  <input v-model="draft.modelId" required placeholder="例如：deepseek-coder" autocapitalize="off" spellcheck="false" />
                </label>
                <label>
                  <span class="admin-model-field-label">模型显示别名 <button type="button" class="admin-model-tooltip" aria-label="模型显示别名说明" data-tooltip="仅用于管理后台展示，不影响实际 API 调用。"><CircleHelp :size="14" /></button></span>
                  <input v-model="draft.alias" required placeholder="例如：DeepSeek-Coder" />
                </label>
                <label>
                  <span class="admin-model-field-label">模型类型</span>
                  <div ref="modelTypeSelect" class="admin-model-type-select" :class="{ 'is-open': modelTypeMenuOpen }">
                    <button type="button" class="admin-model-type-trigger" aria-haspopup="listbox" :aria-expanded="modelTypeMenuOpen" aria-controls="model-api-model-type-listbox" @click="toggleModelTypeMenu" @keydown.esc.stop.prevent="closeModelTypeMenu">
                      <span>{{ selectedModelType.label }}</span>
                      <ChevronDown :size="16" aria-hidden="true" />
                    </button>
                    <div v-if="modelTypeMenuOpen" id="model-api-model-type-listbox" class="admin-model-type-menu" role="listbox" aria-label="模型类型" @keydown.esc.stop.prevent="closeModelTypeMenu">
                      <button v-for="type in modelTypeOptions" :key="type.key" type="button" class="admin-model-type-option" role="option" :aria-selected="draft.modelType === type.key" @click="chooseModelType(type)">
                        <span>{{ type.label }}</span>
                        <Check v-if="draft.modelType === type.key" :size="15" aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                </label>
                <label>
                  <span class="admin-model-field-label">Base URL <button type="button" class="admin-model-tooltip" aria-label="Base URL 说明" data-tooltip="模型服务的 HTTP 或 HTTPS API 根地址。"><CircleHelp :size="14" /></button></span>
                  <input v-model="draft.baseUrl" required type="url" placeholder="https://api.example.com/v1" :aria-invalid="baseUrlInvalid" />
                  <small v-if="baseUrlInvalid" class="admin-model-field-error">请输入有效的 HTTP/HTTPS 地址。</small>
                </label>
                <label class="is-wide">
                  <span class="admin-model-field-label">API Key <button type="button" class="admin-model-tooltip" aria-label="API Key 说明" data-tooltip="用于调用模型服务的身份认证密钥，请妥善保管。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-key-input"><KeyRound :size="16" /><input v-model="draft.apiKey" :required="!editingChannelId || !draft.apiKeyConfigured" :type="apiKeyVisible ? 'text' : 'password'" :placeholder="draft.apiKeyConfigured ? '已配置；输入新 Key 可替换' : '请输入 API Key'" autocomplete="off" /><button type="button" :aria-label="apiKeyVisible ? '隐藏 API Key' : '显示 API Key'" :title="apiKeyVisible ? '隐藏 API Key' : '显示 API Key'" @click="apiKeyVisible = !apiKeyVisible"><EyeOff v-if="apiKeyVisible" :size="16" /><Eye v-else :size="16" /></button><button type="button" aria-label="复制 API Key" title="复制 API Key" :disabled="!draft.apiKey" @click="copyApiKey"><Copy :size="16" /></button></div>
                </label>
                <fieldset class="admin-model-use-case-field is-wide">
                  <legend>绑定用途</legend>
                  <label v-for="useCase in modelUseCases" :key="useCase.key"><input v-model="draft.useCases" type="checkbox" :value="useCase.key" />{{ useCase.label }}</label>
                </fieldset>
                <label>
                  <span class="admin-model-field-label">路由优先级</span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.priority" min="0" type="number" /><small>越小越优先</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">允许用户选择</span>
                  <input v-model="draft.userSelectable" type="checkbox" />
                </label>
                <label class="is-wide">
                  <span class="admin-model-field-label">可用用户等级</span>
                  <input v-model="draft.allowedTiers" placeholder="free,standard,pro" />
                </label>
              </div>
            </section>

            <section v-show="activeModalTab === 'parameters'" id="model-api-panel-parameters" class="admin-model-tab-panel" role="tabpanel" aria-labelledby="model-api-tab-parameters">
              <div class="admin-model-form-grid">
                <label class="is-wide">
                  <span class="admin-model-field-label">上下文窗口限制 <button type="button" class="admin-model-tooltip" aria-label="上下文窗口限制说明" data-tooltip="单次请求中输入与输出可使用的 Token 总量上限。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.contextLimit" required min="1" type="number" /><small>Tokens</small></div>
                  <div class="admin-model-quick-set" aria-label="上下文窗口快捷设置"><span>Quick set</span><button v-for="value in [16000, 32000, 128000, 1000000]" :key="value" type="button" :class="{ active: draft.contextLimit === value }" @click="applyQuickContextLimit(value)">{{ value === 1000000 ? '1M' : `${value / 1000}k` }}</button></div>
                </label>
                <label>
                  <span class="admin-model-field-label">最大生成 Token <button type="button" class="admin-model-tooltip" aria-label="最大生成 Token 说明" data-tooltip="限制单次模型输出长度，避免长响应占用过多资源。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.maxTokens" required min="1" type="number" /><small>Tokens</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">缓存 Token 单价</span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.cachedTokenPrice" required min="0" step="0.0001" type="number" /><small>每单位</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">币种</span>
                  <input v-model="draft.currency" maxlength="8" required />
                </label>
                <label>
                  <span class="admin-model-field-label">计费单位</span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.priceUnit" required min="1" type="number" /><small>Tokens</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">默认温度 <button type="button" class="admin-model-tooltip" aria-label="默认温度说明" data-tooltip="控制输出随机性，数值越高，表达越具发散性。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-range-input"><input v-model.number="draft.temperature" min="0" max="2" step="0.1" type="range" aria-label="默认温度滑动条" /><div class="admin-model-unit-input"><input v-model.number="draft.temperature" min="0" max="2" step="0.1" type="number" /><small>值</small></div></div>
                  <small class="admin-model-help-copy">0 为确定性输出，值越高越具创意。</small>
                </label>
                <label class="is-wide">
                  <span class="admin-model-field-label">Top P <button type="button" class="admin-model-tooltip" aria-label="Top P 说明" data-tooltip="通过累计概率采样控制输出候选集的范围。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-range-input"><input v-model.number="draft.topP" min="0" max="1" step="0.05" type="range" aria-label="Top P 滑动条" /><div class="admin-model-unit-input"><input v-model.number="draft.topP" min="0" max="1" step="0.05" type="number" /><small>概率</small></div></div>
                </label>
              </div>
            </section>

            <section v-show="activeModalTab === 'rate'" id="model-api-panel-rate" class="admin-model-tab-panel" role="tabpanel" aria-labelledby="model-api-tab-rate">
              <div class="admin-model-form-grid">
                <label>
                  <span class="admin-model-field-label">RPM 限制 <button type="button" class="admin-model-tooltip" aria-label="RPM 限制说明" data-tooltip="每分钟允许向模型服务发起的最大请求数。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.rpm" required min="1" type="number" /><small>RPM</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">TPM 限制 <button type="button" class="admin-model-tooltip" aria-label="TPM 限制说明" data-tooltip="每分钟允许消耗的最大 Token 数。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.tpm" required min="1" type="number" /><small>TPM</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">最大并发数 <button type="button" class="admin-model-tooltip" aria-label="最大并发数说明" data-tooltip="同一模型渠道可同时处理的最大请求数量。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.maxConcurrency" required min="1" type="number" /><small>并发</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">请求超时时间 <button type="button" class="admin-model-tooltip" aria-label="请求超时时间说明" data-tooltip="请求超过此时长将被网关中断并进入容错策略。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.timeout" required min="1" type="number" /><small>s</small></div>
                </label>
              </div>
            </section>

            <section v-show="activeModalTab === 'billing'" id="model-api-panel-billing" class="admin-model-tab-panel" role="tabpanel" aria-labelledby="model-api-tab-billing">
              <div class="admin-model-form-grid">
                <label>
                  <span class="admin-model-field-label">输入 Token 单价 <button type="button" class="admin-model-tooltip" aria-label="输入 Token 单价说明" data-tooltip="每个配置的计费单位对应的输入成本，用于计算模型调用费用。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.inputPrice" required min="0" step="0.01" type="number" /><small>{{ draft.currency || 'USD' }}/{{ draft.priceUnit || 1 }}</small></div>
                </label>
                <label>
                  <span class="admin-model-field-label">输出 Token 单价 <button type="button" class="admin-model-tooltip" aria-label="输出 Token 单价说明" data-tooltip="每个配置的计费单位对应的输出成本，用于计算模型调用费用。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.outputPrice" required min="0" step="0.01" type="number" /><small>{{ draft.currency || 'USD' }}/{{ draft.priceUnit || 1 }}</small></div>
                </label>
                <label class="is-wide">
                  <span class="admin-model-field-label">扣费权重 / 倍率 <button type="button" class="admin-model-tooltip" aria-label="扣费权重说明" data-tooltip="多模型路由时，用户配额按此倍率扣减。"><CircleHelp :size="14" /></button></span>
                  <div class="admin-model-unit-input"><input v-model.number="draft.quotaMultiplier" required min="0" step="0.1" type="number" /><small>x</small></div>
                </label>
              </div>
            </section>

            <p v-if="formError" class="admin-model-form-error" role="alert">{{ formError }}</p>
          </div>

          <footer class="admin-model-modal-footer"><button type="button" class="admin-ghost-button" :disabled="isSaving" @click="closeModal">取消</button><button type="submit" class="admin-primary-button" :disabled="isSaving"><LoaderCircle v-if="isSaving" class="admin-model-saving-spinner" :size="16" /><Save v-else :size="16" />{{ isSaving ? '保存中...' : '保存配置' }}</button></footer>
        </form>
      </div>
    </Transition>
    <Transition name="admin-modal">
      <div v-if="testResult" class="admin-modal-backdrop" @pointerdown.self="closeTestResult">
        <section class="admin-modal-card admin-model-test-modal" role="dialog" aria-modal="true" aria-labelledby="model-test-result-title" @keydown.esc.prevent="closeTestResult">
          <header>
            <div><span>模型连通性测试</span><h3 id="model-test-result-title">{{ testResult.success ? '连接成功' : '连接失败' }}</h3></div>
            <button type="button" class="admin-close-button" aria-label="关闭测试结果" @click="closeTestResult"><X :size="18" /></button>
          </header>
          <dl class="admin-model-test-result-grid" :class="{ failed: !testResult.success }">
            <template v-if="testResult.success">
              <div><dt>Provider</dt><dd>{{ testResult.provider }}</dd></div>
              <div><dt>Model</dt><dd>{{ testResult.modelId }}</dd></div>
              <div v-if="testResult.requestType"><dt>测试类型</dt><dd>{{ testResult.requestType }}</dd></div>
              <div v-if="testResult.adapter"><dt>Provider 适配器</dt><dd>{{ testResult.adapter }}</dd></div>
              <div><dt>HTTP</dt><dd>{{ testResult.httpStatus }}</dd></div>
              <div><dt>响应时间</dt><dd>{{ testResult.latencyMs }} ms</dd></div>
              <div><dt>输入 Token</dt><dd>{{ testResult.inputTokens }}</dd></div>
              <div><dt>输出 Token</dt><dd>{{ testResult.outputTokens }}</dd></div>
            </template>
            <template v-else>
              <div><dt>Provider</dt><dd>{{ testResult.provider }}</dd></div>
              <div><dt>Model</dt><dd>{{ testResult.modelId }}</dd></div>
              <div v-if="testResult.requestType"><dt>测试类型</dt><dd>{{ testResult.requestType }}</dd></div>
              <div><dt>错误类型</dt><dd>{{ testResult.errorCode }}</dd></div>
              <div><dt>HTTP</dt><dd>{{ testResult.httpStatus || '未获取' }}</dd></div>
              <div class="is-wide"><dt>错误信息</dt><dd>{{ testResult.errorMessage }}</dd></div>
              <div v-if="testResult.providerMessage && testResult.providerMessage !== testResult.errorMessage" class="is-wide"><dt>服务返回信息</dt><dd>{{ testResult.providerMessage }}</dd></div>
              <div v-if="testResult.latencyMs" class="is-wide"><dt>响应时间</dt><dd>{{ testResult.latencyMs }} ms</dd></div>
            </template>
          </dl>
          <footer><button type="button" class="admin-primary-button" @click="closeTestResult">关闭</button></footer>
        </section>
      </div>
    </Transition>
    <Transition name="admin-modal">
      <div v-if="deleteDialogOpen && deleteTarget" class="admin-modal-backdrop" @pointerdown.self="closeDeleteDialog">
        <section class="admin-modal-card admin-model-delete-modal" role="alertdialog" aria-modal="true" aria-labelledby="model-delete-title" @keydown.esc.prevent="closeDeleteDialog">
          <header><div class="admin-model-delete-icon"><AlertTriangle :size="20" /></div><button type="button" class="admin-close-button" aria-label="关闭删除确认" :disabled="isDeleting" @click="closeDeleteDialog"><X :size="18" /></button></header>
          <h3 id="model-delete-title">删除 AI 模型</h3>
          <p>确定删除「{{ deleteTarget.alias }}」吗？</p>
          <div class="admin-model-delete-impact"><strong>删除后该模型将不再参与：</strong><span>AI 对话、拓扑规划、Prompt 模型路由、主备模型切换和 RAG 相关任务。</span><span v-if="deleteForce" class="is-warning">当前模型是系统最后一个可用对话模型，仍然删除后相关功能将暂时不可用。</span></div>
          <footer><button type="button" class="admin-ghost-button" :disabled="isDeleting" @click="closeDeleteDialog">取消</button><button type="button" class="admin-danger-button" :disabled="isDeleting" @click="confirmDeleteModel"><LoaderCircle v-if="isDeleting" class="admin-model-saving-spinner" :size="16" /><Trash2 v-else :size="16" />{{ isDeleting ? '正在删除...' : deleteForce ? '仍然删除' : '删除模型' }}</button></footer>
        </section>
      </div>
    </Transition>
    <Teleport to="body">
      <div
        v-if="activeTooltip.visible"
        ref="tooltipPopover"
        class="admin-model-tooltip-popover"
        :class="{ 'is-positioned': activeTooltip.positioned, 'is-bottom': activeTooltip.placement === 'bottom' }"
        :style="{ left: `${activeTooltip.left}px`, top: `${activeTooltip.top}px`, maxWidth: `${activeTooltip.maxWidth}px`, '--admin-tooltip-arrow-left': `${activeTooltip.arrowLeft}px` }"
        role="tooltip"
      >{{ activeTooltip.text }}</div>
    </Teleport>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.admin-model-row-actions { display:flex; gap:6px; }
.admin-model-action-menu-wrap { position:relative; }
.admin-model-action-menu { position:absolute; z-index:20; top:calc(100% + 6px); right:0; display:grid; min-width:132px; padding:5px; border:1px solid #e5e7eb; border-radius:8px; background:#fff; box-shadow:0 12px 28px rgba(15,23,42,.14); }
.admin-model-action-menu button { display:flex; align-items:center; gap:8px; min-height:32px; padding:0 9px; border:0; border-radius:6px; background:transparent; color:#475569; font-size:12px; text-align:left; cursor:pointer; }
.admin-model-action-menu button:hover { background:#f1f5f9; color:#1d4ed8; }
.admin-model-action-menu button.danger { color:#dc2626; }
.admin-model-action-menu button.danger:hover { background:#fef2f2; color:#b91c1c; }
.admin-model-action-status-dot { width:7px; height:7px; border:1px solid #94a3b8; border-radius:50%; }
.admin-model-action-status-dot.active { border-color:#22c55e; background:#22c55e; }
.admin-model-delete-modal { width:min(460px,calc(100vw - 32px)); padding:24px; border:1px solid #fecaca; border-radius:14px; background:#fff; box-shadow:0 24px 64px rgba(15,23,42,.2); }
.admin-model-delete-modal > header { display:flex; align-items:flex-start; justify-content:space-between; }
.admin-model-delete-icon { display:grid; width:42px; height:42px; place-items:center; border-radius:12px; background:#fef2f2; color:#dc2626; }
.admin-model-delete-modal h3 { margin:18px 0 8px; color:#111827; font-size:20px; }
.admin-model-delete-modal > p { margin:0; color:#374151; font-size:14px; line-height:1.55; }
.admin-model-delete-impact { display:grid; gap:6px; margin-top:16px; padding:12px 14px; border:1px solid #fee2e2; border-radius:9px; background:#fff7f7; color:#7f1d1d; font-size:12px; line-height:1.55; }
.admin-model-delete-impact strong { color:#991b1b; }
.admin-model-delete-impact .is-warning { color:#b45309; }
.admin-model-delete-modal > footer { display:flex; justify-content:flex-end; gap:9px; margin-top:22px; }
.admin-model-delete-modal .admin-danger-button { display:inline-flex; align-items:center; gap:7px; }
:global(html[data-theme='dark']) .admin-model-action-menu { border-color:#3f3f46; background:#18181b; box-shadow:0 12px 28px rgba(0,0,0,.34); }
:global(html[data-theme='dark']) .admin-model-action-menu button { color:#d4d4d8; }
:global(html[data-theme='dark']) .admin-model-action-menu button:hover { background:#27272a; color:#bfdbfe; }
:global(html[data-theme='dark']) .admin-model-action-menu button.danger { color:#fca5a5; }
:global(html[data-theme='dark']) .admin-model-action-menu button.danger:hover { background:rgba(127,29,29,.28); color:#fecaca; }
:global(html[data-theme='dark']) .admin-model-delete-modal { border-color:#7f1d1d; background:#18181b; }
:global(html[data-theme='dark']) .admin-model-delete-modal h3 { color:#fafafa; }
:global(html[data-theme='dark']) .admin-model-delete-modal > p { color:#d4d4d8; }
:global(html[data-theme='dark']) .admin-model-delete-impact { border-color:#7f1d1d; background:rgba(127,29,29,.2); color:#fecaca; }
.admin-model-type-badge { display:inline-flex; align-items:center; min-height:24px; padding:0 8px; border:1px solid #dbeafe; border-radius:999px; background:#eff6ff; color:#1d4ed8; font-size:12px; font-weight:650; white-space:nowrap; }
.admin-model-use-cases { display:flex; align-items:center; flex-wrap:wrap; gap:5px; max-width:230px; }
.admin-model-use-cases span,.admin-model-use-cases small { display:inline-flex; align-items:center; min-height:22px; padding:0 7px; border-radius:5px; background:#f3f4f6; color:#4b5563; font-size:11px; font-weight:600; }
.admin-model-use-cases small { background:#fff7ed; color:#c2410c; }
.admin-model-use-case-field { display:flex; flex-wrap:wrap; gap:8px 12px; margin:0; padding:12px; border:1px solid #e5e7eb; border-radius:8px; }
.admin-model-use-case-field legend { padding:0 4px; color:#374151; font-size:12px; font-weight:650; }
.admin-model-use-case-field label { display:inline-flex; align-items:center; gap:6px; color:#4b5563; font-size:12px; white-space:nowrap; }
.admin-model-use-case-field input { width:14px; height:14px; accent-color:#2563eb; }
.admin-model-switch:disabled { cursor:not-allowed; opacity:.68; }
.admin-model-switch.is-loading { color:#64748b; }
.admin-model-switch-spinner { flex:0 0 auto; animation:admin-model-status-spin 760ms linear infinite; }
.admin-model-test-spinner { animation:admin-model-status-spin 760ms linear infinite; }
.admin-model-icon-button.is-testing,.admin-model-icon-button:disabled { cursor:not-allowed; opacity:.68; }
.admin-model-latency.testing { color:#2563eb; }
.admin-model-latency.testing::before { background:#3b82f6; }
.admin-model-test-modal { width:min(500px, calc(100vw - 32px)); padding:0; overflow:hidden; border:1px solid #e5e7eb; border-radius:10px; background:#ffffff; box-shadow:0 24px 64px rgba(15,23,42,.2); }
.admin-model-test-modal > header { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding:22px 24px 17px; border-bottom:1px solid #e5e7eb; }.admin-model-test-modal header > div { display:grid; gap:3px; }.admin-model-test-modal header span { color:#6b7280; font-size:11px; font-weight:650; }.admin-model-test-modal h3 { margin:0; color:#111827; font-size:19px; }.admin-model-test-modal > footer { display:flex; justify-content:flex-end; padding:16px 24px 20px; border-top:1px solid #e5e7eb; }
.admin-model-test-result-grid { display:grid; grid-template-columns:1fr 1fr; gap:0; margin:0; }.admin-model-test-result-grid > div { min-width:0; padding:14px 16px; border-right:1px solid #eef2f7; border-bottom:1px solid #eef2f7; }.admin-model-test-result-grid > div:nth-child(2n) { border-right:0; }.admin-model-test-result-grid > div.is-wide { grid-column:1 / -1; border-right:0; }.admin-model-test-result-grid dt { margin:0 0 5px; color:#64748b; font-size:11px; font-weight:650; }.admin-model-test-result-grid dd { margin:0; overflow-wrap:anywhere; color:#111827; font-size:13px; font-weight:700; line-height:1.45; }.admin-model-test-result-grid.failed dd { color:#b91c1c; }
@keyframes admin-model-status-spin { to { transform:rotate(360deg); } }
:global(html[data-theme='dark']) .admin-model-type-badge { border-color:#1e3a5f; background:#172554; color:#93c5fd; }
:global(html[data-theme='dark']) .admin-model-use-cases span { background:#27272a; color:#d4d4d8; }
:global(html[data-theme='dark']) .admin-model-use-case-field { border-color:#3f3f46; }
:global(html[data-theme='dark']) .admin-model-use-case-field legend,:global(html[data-theme='dark']) .admin-model-use-case-field label { color:#d4d4d8; }
:global(html[data-theme='dark']) .admin-model-test-modal { border-color:#3f3f46; background:#18181b; }.admin-model-test-modal > header,:global(html[data-theme='dark']) .admin-model-test-modal > footer { border-color:#3f3f46; }:global(html[data-theme='dark']) .admin-model-test-modal h3,:global(html[data-theme='dark']) .admin-model-test-result-grid dd { color:#f4f4f5; }:global(html[data-theme='dark']) .admin-model-test-result-grid > div { border-color:#303036; }:global(html[data-theme='dark']) .admin-model-test-result-grid dt,:global(html[data-theme='dark']) .admin-model-test-modal header span { color:#a1a1aa; }:global(html[data-theme='dark']) .admin-model-test-result-grid.failed dd { color:#fca5a5; }
</style>
