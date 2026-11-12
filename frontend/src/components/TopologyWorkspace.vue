<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  AlignHorizontalJustifyCenter, AlignHorizontalJustifyEnd, AlignHorizontalJustifyStart,
  AlignHorizontalSpaceBetween, AlignVerticalJustifyCenter, AlignVerticalJustifyEnd,
  AlignVerticalJustifyStart, AlignVerticalSpaceBetween, ArrowLeft, Check, ChevronDown, ChevronLeft, ChevronRight, CircleAlert, Clipboard, Copy,
  Download, Expand, Eye, EyeOff, Globe2, GripVertical, LayoutTemplate, Maximize2, MousePointer2, Network, Pencil, Redo2, Save, Search,
  Plus, Send, ShieldCheck, Sparkles, Trash2, Undo2, ZoomIn, ZoomOut
} from '@lucide/vue'
import { getTopology, saveTopology } from '../api/user'
import { applyTopologyPlan, checkTopology } from '../api/user'
import { createChatSession, ensureProjectChatSession, getProjectChatMessages, getTopologyResource, getUserSubscription, listAiTopologyResources, listChatModels, listProjectChatSessions, listProjectFiles, listTopologyResources, streamProjectChat, streamProjectTopologyPlan, uploadProjectFile } from '../api/user'
import { inferTopologyResourceType, normalizeTopologyCategory } from '../data/topologyResources'
import AiMessageContent from './AiMessageContent.vue'
import { retryAssistantRead, assistantFailureState } from '../utils/assistantRecovery.js'

const props = defineProps({ projectId: { type: String, default: null }, projectName: { type: String, default: '未命名网络规划' }, initialMode: { type: String, default: 'manual' }, initialDescription: { type: String, default: '' } })
const emit = defineEmits(['back'])
const editorRoot = ref(null)

const toneColor = { blue: '#334155', teal: '#334155', red: '#334155', violet: '#334155', slate: '#334155', amber: '#334155' }
const resources = ref([])
const library = ref([])
const catalog = reactive({})
const resourcesLoading = ref(true)
const resourcesError = ref('')
const aiAllowedResourceTypes = ref(new Set())
const resourceCategory = normalizeTopologyCategory
const inferResourceType = (resource) => {
  return inferTopologyResourceType(resource)
}
function normalizeResource(resource, index) {
  const type = String(resource.type || resource.deviceType || inferResourceType(resource)).trim().toLowerCase()
  const label = resource.name || resource.englishName || type
  const group = resourceCategory(resource.category)
  const ports = normalizePorts(resource.ports)
  return {
    ...resource,
    type,
    label,
    icon: Network,
    asset: resource.asset || resource.iconPath || resource.iconUrl || '',
    color: toneColor[resource.tone] || '#334155',
    ports: ports.length ? ports : ['GE0/0/1'],
    order: Number(resource.order ?? index * 10),
    category: group,
    draggable: resource.draggable !== false,
    enabled: resource.enabled !== false,
    resourceId: resource.resourceId ?? resource.id ?? resource.key ?? type,
    portProfileId: resource.portProfileId || `${type}-default`,
    portProfileName: resource.portProfileName || '',
    defaultPortCount: Number(resource.defaultPortCount || ports.length || 1)
  }
}
function normalizePorts(value) {
  if (!Array.isArray(value)) return []
  return value.map((port) => typeof port === 'object' ? (port.name || port.portName || '') : port)
    .map((port) => String(port || '').trim()).filter(Boolean).filter((port, index, all) => all.indexOf(port) === index)
}
function syncResources(items) {
  const normalized = []
  const byType = new Map()
  items.map(normalizeResource).filter((item) => item.enabled).forEach((item) => {
    const current = byType.get(item.type)
    if (!current || String(item.resourceId) === item.type || item.order < current.order) byType.set(item.type, item)
  })
  normalized.push(...byType.values())
  resources.value = normalized
  aiAllowedResourceTypes.value = new Set(normalized.filter((item) => item.allowAi === true || item.allowAi === 'true').map((item) => item.type))
  const groups = new Map()
  normalized.sort((a, b) => a.order - b.order).forEach((item) => {
    if (!groups.has(item.category)) groups.set(item.category, [])
    groups.get(item.category).push(item)
  })
  library.value = [...groups].map(([name, items]) => ({ name, items }))
  normalized.forEach((item) => {
    // Keep every public identifier resolvable. The AI contract may return a
    // stable resource key/id instead of the normalized device type.
    const aliases = [item.type, item.resourceId, item.key, item.id, item.englishName, item.label, item.name]
    aliases.filter(Boolean).forEach((alias) => { catalog[String(alias).trim().toLowerCase().replaceAll('_', '-')] = item })
  })
  openGroups.value = restoreLibraryOpenState(library.value.map((group) => group.name))
}
function registerHistoricalResources(items) {
  items.forEach((resource, index) => {
    const normalized = normalizeResource(resource, index)
    const aliases = [normalized.type, normalized.resourceId, normalized.key, normalized.id, normalized.englishName, normalized.label, normalized.name]
    aliases.filter(Boolean).forEach((alias) => { catalog[String(alias).trim().toLowerCase().replaceAll('_', '-')] = normalized })
  })
}
function refreshNodeResourceBindings() {
  let changed = false
  nodes.value.forEach((item) => {
    const definition = catalog[item.type]
    if (!definition) return
    const current = normalizePorts(item.ports)
    const serverLike = ['server', 'database', 'nas'].includes(String(item.type || '').toLowerCase())
    if (serverLike) {
      const canonicalPort = current[0] || normalizePorts(definition.ports)[0] || 'ETH0'
      if (current.length !== 1 || current[0] !== canonicalPort) {
        item.ports = [canonicalPort]
        changed = true
      }
    }
    const legacyPorts = !current.length || (current.length <= 2 && current.every((port) => /^GE0\/0\/[12]$/.test(port)))
    if (!serverLike && legacyPorts && definition.ports?.length) {
      item.ports = [...definition.ports]
      changed = true
    }
    if (!item.resourceId && definition.resourceId) {
      item.resourceId = definition.resourceId
      changed = true
    }
  })
  if (changed && props.projectId) dirty()
}
const topologyChecks = ref([])
const checkSummary = ref({ score: 100, high: 0, medium: 0, low: 0, passed: 0 })
const checksLoading = ref(false)
const checkError = ref('')
const checkFilter = ref('all')

const RIGHT_PANEL_COLLAPSED_STORAGE_KEY = 'topology:right-panel:collapsed'
const RIGHT_PANEL_TAB_STORAGE_KEY = 'topology:right-panel:active-tab'
const RIGHT_PANEL_WIDTH_STORAGE_KEY = 'topology:right-panel:width'
const LIBRARY_COLLAPSED_CATEGORIES_STORAGE_KEY = 'topology_library_collapsed_categories'
const PENDING_CHANGE_STORAGE_PREFIX = 'topology:pending-change:'
const RESOLVED_CHANGE_STORAGE_PREFIX = 'topology:resolved-change-ids:'
const RIGHT_PANEL_TABS = new Set(['properties', 'ai', 'checks'])
function readRightPanelPreference(key, fallback) {
  try { return window.localStorage.getItem(key) ?? fallback } catch { return fallback }
}
function projectScopedStorageKey(prefix) { return `${prefix}${props.projectId || 'new'}` }
function readStoredJson(key, fallback) {
  try {
    const value = window.localStorage.getItem(key)
    return value ? JSON.parse(value) : fallback
  } catch { return fallback }
}
function resolvedChangeIds() {
  const values = readStoredJson(projectScopedStorageKey(RESOLVED_CHANGE_STORAGE_PREFIX), [])
  return new Set(Array.isArray(values) ? values.map(String) : [])
}
function persistPendingChange(messageId, change) {
  if (!props.projectId || !change?.id || !change?.plan) return
  try {
    window.localStorage.setItem(projectScopedStorageKey(PENDING_CHANGE_STORAGE_PREFIX), JSON.stringify({
      projectId: String(props.projectId),
      messageId: String(messageId || change.messageId || ''),
      change: { ...change, messageId: String(messageId || change.messageId || '') }
    }))
  } catch { /* A pending plan remains available during this browser session. */ }
}
function resolvePendingChange(change) {
  if (!change?.id) return
  try {
    const key = projectScopedStorageKey(RESOLVED_CHANGE_STORAGE_PREFIX)
    const resolved = [...resolvedChangeIds(), String(change.id)].slice(-100)
    window.localStorage.setItem(key, JSON.stringify(resolved))
    const pending = readStoredJson(projectScopedStorageKey(PENDING_CHANGE_STORAGE_PREFIX), null)
    if (pending?.change?.id === change.id) window.localStorage.removeItem(projectScopedStorageKey(PENDING_CHANGE_STORAGE_PREFIX))
  } catch { /* Resolution is still applied in memory. */ }
}
function clearPendingChangeStorage() {
  try { window.localStorage.removeItem(projectScopedStorageKey(PENDING_CHANGE_STORAGE_PREFIX)) } catch { /* Optional persistence. */ }
}
const savedRightTab = readRightPanelPreference(RIGHT_PANEL_TAB_STORAGE_KEY, 'ai')
const savedRightPanelCollapsed = readRightPanelPreference(RIGHT_PANEL_COLLAPSED_STORAGE_KEY, 'false')
const savedRightPanelWidth = Number(readRightPanelPreference(RIGHT_PANEL_WIDTH_STORAGE_KEY, '360'))

const nodes = ref([]), edges = ref([]), title = ref(props.projectName), renaming = ref(false), search = ref(''), openGroups = ref(new Set());
const selectedIds = ref(new Set()), selectedEdgeId = ref(''), rightTab = ref(RIGHT_PANEL_TABS.has(savedRightTab) ? savedRightTab : 'ai'), collapsed = ref(savedRightPanelCollapsed === 'true'), tool = ref('select'), connectFrom = ref(null), connectPreview = ref(null), zoom = ref(.9), pan = ref({ x: 36, y: 24 });
const showNodeNames = ref(true)
const saveState = ref('已保存'), editorBody = ref(null), canvas = ref(null), canvasSize = ref({ width: 0, height: 0 }), drag = ref(null), marquee = ref(null), menu = ref(null), copied = ref(null), history = ref([]), redoStack = ref([]), spacePressed = ref(false);
const guides = ref({ x: null, y: null });
const wheelState = { delta: 0, clientX: 0, clientY: 0, frame: 0 };
const panState = { clientX: 0, clientY: 0, frame: 0 };
const assistantDraft = ref(''), assistantSending = ref(false), assistantGenerationState = ref('IDLE'), assistantSendLock = ref(false), assistantScroll = ref(null), assistantComposerInput = ref(null), assistantMessages = ref([]), pendingChange = ref(null), ignored = ref(new Set());
const assistantAttachmentInput = ref(null), assistantAttachment = ref(null), assistantAttachmentError = ref(''), assistantAttachmentUploading = ref(false), assistantProjectFiles = ref([]), assistantWebSearch = ref(false)
const assistantFileLimits = ref({ count: 10, singleMb: 10, capacityMb: 50 })
const assistantAtBottom = ref(true), assistantHasOverflow = ref(false)
const connectionNotice = ref('')
const assistantConversationId = ref(null), assistantConversationPublicId = ref(null), assistantNewConversationRequested = ref(false), assistantConnectionStatus = ref('reading'), assistantError = ref(''), assistantAbortController = ref(null)
const topologyPlanDialog = ref(null)
let assistantStreamState = null
let assistantBackgroundPollTimer = 0
const assistantBackgroundCompletionHandled = new Set()
const rightPanelWidth = ref(Number.isFinite(savedRightPanelWidth) ? savedRightPanelWidth : 360)
const panelResize = ref(null)
let saveTimer, saveInFlight = null, saveQueued = false, viewportAnimationFrame = 0, layoutAnimationFrame = 0, canvasResizeObserver = null, editorBodyResizeObserver = null, panelResizeFrame = 0
let protectedTopologyImages = []
let topologyImageObserver = null
let assistantScrollListenerTarget = null
let assistantLatestButton = null

function restoreLibraryOpenState(groupNames) {
  const names = new Set(groupNames)
  try {
    const collapsed = JSON.parse(window.localStorage.getItem(LIBRARY_COLLAPSED_CATEGORIES_STORAGE_KEY) || '[]')
    const collapsedNames = new Set(Array.isArray(collapsed) ? collapsed : [])
    return new Set([...names].filter((name) => !collapsedNames.has(name)))
  } catch {
    return new Set(names)
  }
}
function persistLibraryOpenState() {
  try {
    const collapsed = library.value.map((group) => group.name).filter((name) => !openGroups.value.has(name))
    window.localStorage.setItem(LIBRARY_COLLAPSED_CATEGORIES_STORAGE_KEY, JSON.stringify(collapsed))
  } catch { /* Optional UI preference. */ }
}

const welcomeMessage = () => ({
  id: `assistant-${Date.now()}`,
  role: 'assistant',
  content: '你好，我已经读取当前项目的网络拓扑。你可以直接问我网络问题，也可以告诉我需要新增、删除、移动或优化什么。',
  suggestions: assistantSuggestions()
})

function assistantSuggestions() {
  const hasFirewall = nodes.value.some((item) => item.type === 'firewall')
  const hasCore = nodes.value.some((item) => ['core-switch', 'layer3-switch'].includes(item.type))
  return [
    '帮我完善当前拓扑',
    '分析当前网络结构',
    '规划100人办公网络',
    '检查当前网络问题',
    '优化当前拓扑',
    hasCore ? '为什么核心交换机这样连接？' : '当前拓扑有什么问题？',
    hasFirewall ? '为什么这里需要防火墙？' : '当前拓扑有哪些风险？'
  ]
}

const CANVAS_NODE_SIZE = 76
const NODE_LABEL_STORAGE_KEY = 'topoplan-topology-show-node-names'
function finiteNumber(value, fallback) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}
function sanitizeTopologyGeometry() {
  let changed = false
  nodes.value.forEach((item, index) => {
    const fallbackX = 280 + (index % 5) * 180
    const fallbackY = 180 + Math.floor(index / 5) * 150
    const x = finiteNumber(item.x, fallbackX)
    const y = finiteNumber(item.y, fallbackY)
    const width = Math.max(48, Math.min(160, finiteNumber(item.width, CANVAS_NODE_SIZE)))
    const height = Math.max(48, Math.min(160, finiteNumber(item.height, CANVAS_NODE_SIZE)))
    if (item.x !== x) { item.x = x; changed = true }
    if (item.y !== y) { item.y = y; changed = true }
    if (item.width !== width) { item.width = width; changed = true }
    if (item.height !== height) { item.height = height; changed = true }
  })
  const safeZoom = clampZoom(finiteNumber(zoom.value, .9))
  const safePan = {
    x: finiteNumber(pan.value?.x, 36),
    y: finiteNumber(pan.value?.y, 24)
  }
  if (zoom.value !== safeZoom) { zoom.value = safeZoom; changed = true }
  if (pan.value?.x !== safePan.x || pan.value?.y !== safePan.y) { pan.value = safePan; changed = true }
  return changed
}
function node(data) {
  const def = catalog[data.type] || {}
  const rawPorts = normalizePorts(data.ports)
  const serverLike = ['server', 'database', 'nas'].includes(String(data.type || '').toLowerCase())
  const definitionPorts = normalizePorts(def.ports)
  const fallbackPort = serverLike ? 'ETH0' : 'GE0/0/1'
  const normalized = {
    resourceId: data.resourceId ?? def.resourceId ?? null,
    ip: '', area: '未分配区域', vlan: '', model: '', notes: '',
    ports: serverLike
      ? [rawPorts[0] || definitionPorts[0] || fallbackPort]
      : (rawPorts.length ? rawPorts : [...(definitionPorts.length ? definitionPorts : [fallbackPort])]),
    color: def.color || '#2563eb', ...data,
    x: finiteNumber(data.x, 360),
    y: finiteNumber(data.y, 240),
    width: CANVAS_NODE_SIZE, height: CANVAS_NODE_SIZE
  }
  normalized.ports = serverLike
    ? [rawPorts[0] || definitionPorts[0] || fallbackPort]
    : (rawPorts.length ? rawPorts : normalizePorts(normalized.ports))
  return normalized
}
function edge(data) { return { id: `edge-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`, sourcePort: 'GE0/0/1', targetPort: 'GE0/0/1', connectionType: '千兆以太网', notes: '', ...data } }
function byId(id) { return nodes.value.find((item) => String(item.id) === String(id)) }
function usedPorts(nodeId, side = '') {
  const values = new Set()
  edges.value.forEach((item) => {
    if ((!side || side === 'source') && String(item.source) === String(nodeId) && item.sourcePort) values.add(item.sourcePort)
    if ((!side || side === 'target') && String(item.target) === String(nodeId) && item.targetPort) values.add(item.targetPort)
  })
  return values
}
function availablePort(item, preferred = '') {
  if (!item) return ''
  const ports = normalizePorts(item.ports)
  const used = usedPorts(item.id)
  if (preferred && ports.includes(preferred) && !used.has(preferred)) return preferred
  return ports.find((port) => !used.has(port)) || ''
}
function portState(item, port) { return usedPorts(item?.id).has(port) ? '已占用' : '空闲' }
function changeEdgePort(edgeItem, side, value) {
  const nodeId = side === 'source' ? edgeItem.source : edgeItem.target
  const used = new Set()
  edges.value.forEach((candidate) => {
    if (candidate.id === edgeItem.id) return
    if (side === 'source' && candidate.source === nodeId && candidate.sourcePort) used.add(candidate.sourcePort)
    if (side === 'target' && candidate.target === nodeId && candidate.targetPort) used.add(candidate.targetPort)
  })
  if (used.has(value)) {
    showConnectionNotice(`${value} 已被占用，请选择其他端口`)
    return
  }
  if (side === 'source') edgeItem.sourcePort = value
  else edgeItem.targetPort = value
  dirty()
}
const selectedNode = computed(() => selectedIds.value.size === 1 ? byId([...selectedIds.value][0]) : null)
const selectedEdge = computed(() => edges.value.find((item) => item.id === selectedEdgeId.value))
const selectedNodes = computed(() => nodes.value.filter((item) => selectedIds.value.has(item.id)))
const selectionBounds = computed(() => {
  if (selectedNodes.value.length < 2) return null
  const left = Math.min(...selectedNodes.value.map((item) => item.x))
  const top = Math.min(...selectedNodes.value.map((item) => item.y))
  const right = Math.max(...selectedNodes.value.map((item) => item.x + item.width))
  const bottom = Math.max(...selectedNodes.value.map((item) => item.y + item.height))
  return { left, top, right, bottom, width: right - left, height: bottom - top }
})
const selectionToolbarStyle = computed(() => {
  const bounds = selectionBounds.value
  if (!bounds) return {}
  return {
    left: `${(bounds.left + bounds.width / 2) * zoom.value + pan.value.x}px`,
    top: `${Math.max(8, (bounds.top - 46) * zoom.value + pan.value.y)}px`
  }
})
const viewportStyle = computed(() => ({
  backgroundSize: `${20 * zoom.value}px ${20 * zoom.value}px`,
  backgroundPosition: `${pan.value.x}px ${pan.value.y}px`
}))
const connectPreviewLine = computed(() => {
  if (!connectFrom.value || !connectPreview.value) return null
  const source = byId(connectFrom.value.id)
  if (!source) return null
  const target = { ...connectPreview.value, width: 0, height: 0 }
  const start = boundaryPoint(source, target)
  return { x1: start.x, y1: start.y, x2: target.x, y2: target.y }
})
const groups = computed(() => library.value.map((group) => ({ ...group, items: group.items.filter((item) => item.label.toLowerCase().includes(search.value.trim().toLowerCase())) })).filter((group) => group.items.length))
const visibleChecks = computed(() => checkFilter.value === 'passed' ? [] : topologyChecks.value.filter((item) => !ignored.value.has(item.id)).filter((item) => checkFilter.value === 'all' || item.level === checkFilter.value))
const checkCounts = computed(() => ({ all: topologyChecks.value.length, high: checkSummary.value.high || 0, medium: checkSummary.value.medium || 0, low: checkSummary.value.low || 0, passed: checkSummary.value.passed || 0 }))
const connecting = computed(() => tool.value === 'connect' || connectFrom.value)
const rightPanelStyle = computed(() => ({ '--right-panel-size': `${rightPanelWidth.value}px` }))

function toggleRightPanel() { collapsed.value = !collapsed.value }
function rightPanelWidthRange() {
  const editorWidth = editorBody.value?.getBoundingClientRect().width || window.innerWidth || 1200
  const max = Math.min(700, Math.max(0, Math.floor(editorWidth * .48)))
  return { min: Math.min(320, max), max }
}
function clampRightPanelWidth(value) {
  const { min, max } = rightPanelWidthRange()
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return Math.min(Math.max(360, min), max)
  return Math.min(max, Math.max(min, Math.round(numeric)))
}
function syncRightPanelWidth() {
  rightPanelWidth.value = clampRightPanelWidth(rightPanelWidth.value)
}
function persistRightPanelWidth() {
  try { window.localStorage.setItem(RIGHT_PANEL_WIDTH_STORAGE_KEY, String(rightPanelWidth.value)) } catch { /* Panel size is optional when browser storage is unavailable. */ }
}
function startPanelResize(event) {
  if (collapsed.value || event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()
  panelResize.value = { pointerId: event.pointerId, width: rightPanelWidth.value }
  event.currentTarget.setPointerCapture?.(event.pointerId)
}
function movePanelResize(event) {
  if (!panelResize.value || panelResize.value.pointerId !== event.pointerId) return
  event.preventDefault()
  event.stopPropagation()
  const editorRect = editorBody.value?.getBoundingClientRect()
  if (!editorRect) return
  const nextWidth = clampRightPanelWidth(editorRect.right - event.clientX)
  panelResize.value.width = nextWidth
  if (panelResizeFrame) cancelAnimationFrame(panelResizeFrame)
  panelResizeFrame = requestAnimationFrame(() => {
    rightPanelWidth.value = nextWidth
    panelResizeFrame = 0
  })
}
function stopPanelResize(event) {
  if (!panelResize.value || (event?.pointerId != null && panelResize.value.pointerId !== event.pointerId)) return
  if (panelResizeFrame) {
    cancelAnimationFrame(panelResizeFrame)
    panelResizeFrame = 0
  }
  rightPanelWidth.value = clampRightPanelWidth(panelResize.value.width)
  event?.currentTarget?.releasePointerCapture?.(panelResize.value.pointerId)
  panelResize.value = null
  persistRightPanelWidth()
  nextTick(refreshCanvasSize)
}
function resetRightPanelWidth(event) {
  event.preventDefault()
  event.stopPropagation()
  rightPanelWidth.value = clampRightPanelWidth(360)
  persistRightPanelWidth()
  nextTick(refreshCanvasSize)
}
function resizeAssistantDraft() {
  nextTick(() => {
    const input = assistantComposerInput.value
    if (!input || input.scrollHeight <= input.clientHeight) return
    const height = Math.min(260, Math.max(64, input.scrollHeight))
    input.style.height = `${height}px`
    input.style.overflowY = input.scrollHeight > height ? 'auto' : 'hidden'
  })
}
watch([collapsed, rightTab], ([nextCollapsed, nextTab]) => {
  try {
    window.localStorage.setItem(RIGHT_PANEL_COLLAPSED_STORAGE_KEY, String(nextCollapsed))
    window.localStorage.setItem(RIGHT_PANEL_TAB_STORAGE_KEY, nextTab)
  } catch { /* Panel preferences are optional when browser storage is unavailable. */ }
})
watch(rightTab, (nextTab) => {
  if (nextTab === 'checks') runTopologyCheck()
  if (nextTab === 'ai') nextTick(bindAssistantScrollControls)
})
watch(assistantScroll, () => nextTick(bindAssistantScrollControls))
watch(assistantSending, () => {
  if (assistantLatestButton) {
    assistantLatestButton.textContent = assistantSending.value ? '↓ 查看最新回复' : '↓ 回到底部'
    assistantLatestButton.hidden = !(assistantHasOverflow.value && !assistantAtBottom.value)
  }
})

function pos(item) { return { left: `${item.x}px`, top: `${item.y}px`, width: `${item.width}px`, height: `${item.height}px` } }
function boundaryPoint(item, other, lane = 0) {
  const centerX = item.x + item.width / 2
  const centerY = item.y + item.height / 2
  const dx = other.x + other.width / 2 - centerX
  const dy = other.y + other.height / 2 - centerY
  // A lane belongs to a single device pair. Rotating the boundary direction
  // by a small fixed angle gives parallel physical links separate contact
  // points while keeping every edge a single straight segment.
  const angle = Math.atan2(dy, dx) + lane * 0.018
  const vx = Math.cos(angle), vy = Math.sin(angle)
  const scaleX = Math.abs(vx) < .0001 ? Infinity : item.width / 2 / Math.abs(vx)
  const scaleY = Math.abs(vy) < .0001 ? Infinity : item.height / 2 / Math.abs(vy)
  const scale = Math.min(scaleX, scaleY)
  return { x: centerX + vx * scale, y: centerY + vy * scale }
}
function edgePairKey(item) {
  return [String(item.source), String(item.target)].sort((left, right) => left.localeCompare(right)).join('::')
}
const edgeEndpointMap = computed(() => {
  const map = new Map()
  const edgesByPair = new Map()
  edges.value.forEach((item) => {
    const key = edgePairKey(item)
    if (!edgesByPair.has(key)) edgesByPair.set(key, [])
    edgesByPair.get(key).push(item)
  })
  edgesByPair.forEach((pairEdges) => {
    const ordered = [...pairEdges].sort((left, right) => {
      const leftKey = `${left.sourcePort || ''}:${left.targetPort || ''}:${left.id || ''}`
      const rightKey = `${right.sourcePort || ''}:${right.targetPort || ''}:${right.id || ''}`
      return leftKey.localeCompare(rightKey)
    })
    ordered.forEach((item, index) => {
      const source = byId(item.source), target = byId(item.target)
      if (!source || !target) return
      // 14px separation keeps dual links distinct without creating an
      // exaggerated fan-out. The centered sequence works for 2, 3, or more.
      const lane = (index - (ordered.length - 1) / 2) * 14
      const start = boundaryPoint(source, target, lane)
      const end = boundaryPoint(target, source, -lane)
      map.set(String(item.id), { x1: start.x, y1: start.y, x2: end.x, y2: end.y })
    })
  })
  return map
})
function edgeEndpoints(item) { return edgeEndpointMap.value.get(String(item.id)) || { x1: 0, y1: 0, x2: 0, y2: 0 } }
function point(event) {
  const rect = canvas.value.getBoundingClientRect()
  const safeZoom = clampZoom(finiteNumber(zoom.value, .9))
  const safePan = { x: finiteNumber(pan.value?.x, 36), y: finiteNumber(pan.value?.y, 24) }
  return { x: (event.clientX - rect.left - safePan.x) / safeZoom, y: (event.clientY - rect.top - safePan.y) / safeZoom }
}
function clampZoom(value) { return Math.min(2, Math.max(.2, finiteNumber(value, .9))) }
function applyZoomAt(nextZoom, clientX, clientY) {
  const rect = canvas.value?.getBoundingClientRect()
  if (!rect) return
  const currentZoom = clampZoom(finiteNumber(zoom.value, .9))
  const currentPan = { x: finiteNumber(pan.value?.x, 36), y: finiteNumber(pan.value?.y, 24) }
  const localX = clientX - rect.left
  const localY = clientY - rect.top
  const worldX = (localX - currentPan.x) / currentZoom
  const worldY = (localY - currentPan.y) / currentZoom
  const value = clampZoom(nextZoom)
  zoom.value = value
  pan.value = { x: localX - worldX * value, y: localY - worldY * value }
}
function adjustZoom(amount) {
  const rect = canvas.value?.getBoundingClientRect()
  if (!rect) return
  applyZoomAt(zoom.value + amount, rect.left + rect.width / 2, rect.top + rect.height / 2)
}
function handleWheel(event) {
  event.preventDefault()
  wheelState.delta += -event.deltaY
  wheelState.clientX = event.clientX
  wheelState.clientY = event.clientY
  if (wheelState.frame) return
  wheelState.frame = requestAnimationFrame(() => {
    const steps = Math.max(-4, Math.min(4, wheelState.delta / 120))
    applyZoomAt(zoom.value * Math.pow(1.08, steps), wheelState.clientX, wheelState.clientY)
    wheelState.delta = 0
    wheelState.frame = 0
  })
}
function refreshCanvasSize() {
  const rect = canvas.value?.getBoundingClientRect()
  if (rect) canvasSize.value = { width: rect.width, height: rect.height }
}
function canvasBounds() {
  // The stage is an infinite work surface from the user's perspective. The
  // old bound only used the viewport width, so a negative pan (or a wide
  // auto-layout) made the right side of the canvas unreachable. Keep a
  // generous world margin and include the currently visible world edge.
  const scale = Math.max(.2, zoom.value)
  const visibleRight = (canvasSize.value.width - pan.value.x) / scale
  const visibleBottom = (canvasSize.value.height - pan.value.y) / scale
  const furthestRight = nodes.value.reduce((max, item) => Math.max(max, item.x + item.width), 0)
  const furthestBottom = nodes.value.reduce((max, item) => Math.max(max, item.y + item.height), 0)
  return {
    width: Math.max(2200, visibleRight + 640, furthestRight + 640),
    height: Math.max(1400, visibleBottom + 480, furthestBottom + 480)
  }
}
function snap(n) { return Math.round(n / 10) * 10 }
function snapshot() { return JSON.stringify({ nodes: nodes.value, edges: edges.value, zoom: zoom.value, pan: pan.value }) }
function seedHistory() { history.value = [snapshot()]; redoStack.value = [] }
function record() { const state = snapshot(); if (history.value.at(-1) !== state) { history.value = [...history.value.slice(-39), state]; redoStack.value = [] } }
function restore(state) { const parsed = JSON.parse(state); nodes.value = parsed.nodes.map(node); edges.value = parsed.edges.map(edge); zoom.value = parsed.zoom || .9; pan.value = parsed.pan || { x: 36, y: 24 }; clear() }
function undo() { if (history.value.length < 2) return; const current = history.value.at(-1); redoStack.value = [...redoStack.value, current]; history.value = history.value.slice(0, -1); restore(history.value.at(-1)); dirty() }
function redo() { const state = redoStack.value.at(-1); if (!state) return; redoStack.value = redoStack.value.slice(0, -1); history.value = [...history.value, state]; restore(state); dirty() }
function dirty() { saveState.value = '正在保存...'; clearTimeout(saveTimer); saveTimer = setTimeout(() => save(true), 900) }
function saveFailureMessage(error) {
  if (error?.status === 0 || error?.code === 'NETWORK_ERROR') return '保存失败，请检查网络连接。'
  const message = String(error?.message || '').trim()
  return message ? `保存失败：${message}` : '保存失败，请稍后重试。'
}
async function save(auto = false) {
  clearTimeout(saveTimer)
  if (!props.projectId) { saveState.value = '已保存'; return }
  if (saveInFlight) { saveQueued = true; return saveInFlight }
  sanitizeTopologyGeometry()
  saveState.value = '正在保存...'
  const payload = { nodes: nodes.value, edges: edges.value, zoom: zoom.value, pan: pan.value }
  saveInFlight = (async () => {
    try {
      await saveTopology(props.projectId, payload)
      saveState.value = '已保存'
    } catch (error) {
      saveState.value = saveFailureMessage(error)
      if (!auto) return
    } finally {
      saveInFlight = null
      if (saveQueued) {
        saveQueued = false
        dirty()
      }
    }
  })()
  return saveInFlight
}
async function load() { if (!props.projectId) return seedHistory(); try { const data = await getTopology(props.projectId); if (data.version > 0 && Array.isArray(data.nodes)) { nodes.value = data.nodes.map((item) => node({ ...item, type: item.type?.replaceAll('_', '-') })); edges.value = (data.edges || []).map(edge); zoom.value = Number(data.zoom) || .9; pan.value = data.pan || pan.value } } finally { seedHistory() } }
async function loadResources() {
  resourcesLoading.value = true
  resourcesError.value = ''
  try {
    const items = await listTopologyResources()
    if (!Array.isArray(items)) throw new Error('公共资源接口返回格式无效')
    syncResources(items)
    try {
      const aiItems = await listAiTopologyResources()
      const aiTypes = (Array.isArray(aiItems) ? aiItems : [])
        .map((item) => String(item.type || item.deviceType || item.resourceKey || item.resourceId || item.key || '').trim().toLowerCase().replaceAll('_', '-'))
        .filter(Boolean)
      // Older servers may return an empty AI list even though the public
      // resource list is valid. Never turn that transient response into a
      // deny-all whitelist.
      if (aiTypes.length) aiAllowedResourceTypes.value = new Set(aiTypes)
    } catch { /* The public list remains the fallback for older servers. */ }
  } catch (error) {
    resources.value = []
    library.value = []
    aiAllowedResourceTypes.value = new Set()
    resourcesError.value = error?.message || '设备资源加载失败'
  } finally {
    resourcesLoading.value = false
  }
}
async function runTopologyCheck({ open = false } = {}) {
  if (!props.projectId || checksLoading.value) return
  checksLoading.value = true
  checkError.value = ''
  try {
    const result = await checkTopology(props.projectId)
    const payload = result?.data || result || {}
    checkSummary.value = payload.summary || { score: 100, high: 0, medium: 0, low: 0, passed: 0 }
    topologyChecks.value = Array.isArray(payload.issues) ? payload.issues.map((item) => ({
      ...item,
      level: String(item.level || 'LOW').toLowerCase(),
      tag: String(item.level || 'LOW').toUpperCase() === 'HIGH' ? '高风险' : String(item.level || '').toUpperCase() === 'MEDIUM' ? '中风险' : '建议',
      detail: item.suggestion || item.description || '',
      nodeIds: item.affectedNodeIds || [],
      edgeIds: item.affectedEdgeIds || []
    })) : []
    // When a real issue is found, surface it immediately instead of leaving
    // the panel on a previously selected "passed" filter.
    checkFilter.value = topologyChecks.value.length ? 'all' : 'passed'
    ignored.value = new Set()
    if (open) { rightTab.value = 'checks'; collapsed.value = false }
  } catch (error) {
    checkError.value = error?.message || '拓扑检查失败，请重试'
  } finally {
    checksLoading.value = false
  }
}
async function loadHistoricalResources() {
  const resourceIds = [...new Set(nodes.value.map((item) => item.resourceId).filter((value) => value !== null && value !== undefined && String(value).trim()))]
  if (!resourceIds.length) return
  const resourcesById = await Promise.all(resourceIds.map(async (resourceId) => {
    try { return await getTopologyResource(resourceId) } catch { return null }
  }))
  registerHistoricalResources(resourcesById.filter(Boolean))
}
function clear() { selectedIds.value = new Set(); selectedEdgeId.value = ''; guides.value = { x: null, y: null } }
function selectNode(item, event) {
  if (connecting.value) return finishConnect(item)
  const multi = Boolean(event?.shiftKey || event?.ctrlKey || event?.metaKey)
  const next = multi ? new Set(selectedIds.value) : new Set()
  if (multi && next.has(item.id)) next.delete(item.id)
  else next.add(item.id)
  selectedIds.value = next
  selectedEdgeId.value = ''
  rightTab.value = 'properties'
}
function selectEdge(item) { selectedEdgeId.value = item.id; selectedIds.value = new Set(); rightTab.value = 'properties' }
function removeEdge(item) {
  const edgeId = item?.id || selectedEdgeId.value
  if (!edgeId) return
  edges.value = edges.value.filter((edgeItem) => String(edgeItem.id) !== String(edgeId))
  selectedEdgeId.value = ''
  clear()
  record()
  dirty()
}
function toggleGroup(name) { const next = new Set(openGroups.value); next.has(name) ? next.delete(name) : next.add(name); openGroups.value = next; persistLibraryOpenState() }
function expandAllGroups() { openGroups.value = new Set(library.value.map((group) => group.name)); persistLibraryOpenState() }
function collapseAllGroups() { openGroups.value = new Set(); persistLibraryOpenState() }
function add(item, at) { if (!item || item.draggable === false) return; const amount = nodes.value.filter((n) => n.type === item.type).length + 1, place = at || { x: 400 + amount * 30, y: 240 + amount * 40 }; const added = node({ id: `${item.type}-${Date.now()}`, resourceId: item.resourceId ?? item.id ?? item.key ?? item.type, type: item.type, name: `${item.label}${String(amount).padStart(2, '0')}`, x: snap(place.x), y: snap(place.y) }); nodes.value.push(added); selectNode(added); record(); dirty() }
function preventNativeImageDrag(event) { event.preventDefault() }
function preventImageDragCapture(event) {
  if (event.target?.tagName === 'IMG' || event.target?.closest?.('img')) preventNativeImageDrag(event)
}
function protectTopologyImages() {
  protectedTopologyImages = [...(editorRoot.value?.querySelectorAll('img') || [])]
  protectedTopologyImages.forEach((image) => {
    image.draggable = false
    image.addEventListener('dragstart', preventNativeImageDrag)
  })
}
function releaseProtectedTopologyImages() {
  protectedTopologyImages.forEach((image) => image.removeEventListener('dragstart', preventNativeImageDrag))
  protectedTopologyImages = []
}
function paletteDrag(item, event) { if (item?.draggable === false) { event.preventDefault(); return }; event.dataTransfer.setData('topoplan-device', item.type); event.dataTransfer.effectAllowed = 'copy' }
function drop(event) { const item = catalog[event.dataTransfer.getData('topoplan-device')]; if (item) add(item, point(event)) }
function dragNode(item, event) {
  if (event.button !== 0) return
  if (spacePressed.value) return panStart(event)
  // Connection completion belongs to the node click handler below. Never
  // create a connection on pointer-down or while the pointer is being moved.
  if (connecting.value) return
  const multi = Boolean(event.shiftKey || event.ctrlKey || event.metaKey)
  if (!selectedIds.value.has(item.id) || multi) selectNode(item, event)
  if (!selectedIds.value.has(item.id)) return
  const start = point(event)
  const active = [...selectedIds.value]
  drag.value = { kind: 'nodes', start, initial: active.map((id) => ({ id, x: byId(id).x, y: byId(id).y })) }
  event.currentTarget.setPointerCapture?.(event.pointerId)
}
function panStart(event) {
  if (!(event.button === 0 && spacePressed.value) || drag.value?.kind === 'pan') return
  event.preventDefault()
  cancelViewportAnimation()
  panState.clientX = event.clientX
  panState.clientY = event.clientY
  drag.value = { kind: 'pan', x: event.clientX, y: event.clientY, initial: { ...pan.value } }
  canvas.value?.setPointerCapture?.(event.pointerId)
}
function blockMiddleMouse(event) {
  if (event.button !== 1) return
  // The editor deliberately has no middle-button gesture. Blocking the native
  // mouse-down prevents browser auto-scroll without entering any drag state.
  event.preventDefault()
  event.stopImmediatePropagation?.()
}
function queuePan(event) {
  panState.clientX = event.clientX
  panState.clientY = event.clientY
  if (panState.frame) return
  panState.frame = requestAnimationFrame(() => {
    const active = drag.value
    if (active?.kind === 'pan') pan.value = { x: active.initial.x + panState.clientX - active.x, y: active.initial.y + panState.clientY - active.y }
    panState.frame = 0
  })
}
function flushPan() {
  if (panState.frame) cancelAnimationFrame(panState.frame)
  panState.frame = 0
  const active = drag.value
  if (active?.kind === 'pan') pan.value = { x: active.initial.x + panState.clientX - active.x, y: active.initial.y + panState.clientY - active.y }
}
function canvasStart(event) {
  if (event.button === 1) {
    event.preventDefault()
    return
  }
  if (event.button === 0 && spacePressed.value) return panStart(event)
  if (event.button !== 0 || event.target.closest?.('.topology-node, .edge-hit, .selection-toolbar, button, input, textarea, select')) return
  if (connecting.value) { connectFrom.value = null; connectPreview.value = null; tool.value = 'select'; return }
  const start = point(event)
  const additive = Boolean(event.shiftKey || event.ctrlKey || event.metaKey)
  marquee.value = { x: start.x, y: start.y, width: 0, height: 0 }
  drag.value = { kind: 'box', start, initial: additive ? new Set(selectedIds.value) : new Set(), toggle: Boolean(event.ctrlKey || event.metaKey) }
  event.currentTarget.setPointerCapture?.(event.pointerId)
}
function handleNodeClick(item, event) {
  if (!connecting.value || event.target?.closest?.('.node-actions')) return
  event.preventDefault()
  finishConnect(item)
}
function alignmentValue(item, axis, edge) {
  if (axis === 'x') return edge === 'start' ? item.x : edge === 'center' ? item.x + item.width / 2 : item.x + item.width
  return edge === 'start' ? item.y : edge === 'center' ? item.y + item.height / 2 : item.y + item.height
}
function updateGuides() {
  const active = selectedNodes.value
  const other = nodes.value.filter((item) => !selectedIds.value.has(item.id))
  if (!active.length || !other.length) { guides.value = { x: null, y: null }; return }
  let bestX = null, bestY = null
  active.forEach((item) => {
    ;['start', 'center', 'end'].forEach((edge) => {
      const x = alignmentValue(item, 'x', edge)
      const y = alignmentValue(item, 'y', edge)
      other.forEach((target) => {
        ;['start', 'center', 'end'].forEach((targetEdge) => {
          const targetX = alignmentValue(target, 'x', targetEdge)
          const targetY = alignmentValue(target, 'y', targetEdge)
          if (Math.abs(x - targetX) <= 8 && (!bestX || Math.abs(x - targetX) < bestX.distance)) bestX = { value: targetX, distance: Math.abs(x - targetX) }
          if (Math.abs(y - targetY) <= 8 && (!bestY || Math.abs(y - targetY) < bestY.distance)) bestY = { value: targetY, distance: Math.abs(y - targetY) }
        })
      })
    })
  })
  guides.value = { x: bestX?.value ?? null, y: bestY?.value ?? null }
}
function move(event) {
  if (connectFrom.value && !drag.value) connectPreview.value = point(event)
  if (!drag.value) return
  if (drag.value.kind === 'pan') { event.preventDefault(); queuePan(event); return }
  if (drag.value.kind === 'nodes') {
    const current = point(event), dx = current.x - drag.value.start.x, dy = current.y - drag.value.start.y, bounds = canvasBounds()
    drag.value.initial.forEach((old) => {
      const item = byId(old.id)
      if (!item) return
      item.x = Math.max(20, Math.min(bounds.width - item.width - 20, old.x + dx))
      item.y = Math.max(20, Math.min(bounds.height - item.height - 20, old.y + dy))
    })
    updateGuides()
  }
  if (drag.value.kind === 'box') {
    const current = point(event)
    marquee.value = { x: Math.min(drag.value.start.x, current.x), y: Math.min(drag.value.start.y, current.y), width: Math.abs(current.x - drag.value.start.x), height: Math.abs(current.y - drag.value.start.y) }
    const hits = nodes.value.filter((item) => marquee.value.x <= item.x + item.width && marquee.value.x + marquee.value.width >= item.x && marquee.value.y <= item.y + item.height && marquee.value.y + marquee.value.height >= item.y).map((item) => item.id)
    const next = new Set(drag.value.initial)
    hits.forEach((id) => drag.value.toggle ? (next.has(id) ? next.delete(id) : next.add(id)) : next.add(id))
    selectedIds.value = next
    selectedEdgeId.value = ''
  }
}
function end(event) {
  if (drag.value?.kind === 'pan') flushPan()
  if (drag.value?.kind === 'nodes') { record(); dirty() }
  if (drag.value?.kind === 'box' && marquee.value) {
    const selected = nodes.value.filter((item) => marquee.value.x <= item.x + item.width && marquee.value.x + marquee.value.width >= item.x && marquee.value.y <= item.y + item.height && marquee.value.y + marquee.value.height >= item.y).map((item) => item.id)
    const next = new Set(drag.value.initial)
    selected.forEach((id) => drag.value.toggle ? (next.has(id) ? next.delete(id) : next.add(id)) : next.add(id))
    selectedIds.value = next
    selectedEdgeId.value = ''
  }
  if (event?.currentTarget?.releasePointerCapture && event.pointerId != null) {
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* Pointer capture may already be released. */ }
  }
  drag.value = null
  marquee.value = null
  guides.value = { x: null, y: null }
}
function remove() { if (selectedEdge.value) edges.value = edges.value.filter((item) => item.id !== selectedEdgeId.value); if (selectedIds.value.size) { nodes.value = nodes.value.filter((item) => !selectedIds.value.has(item.id)); edges.value = edges.value.filter((item) => !selectedIds.value.has(item.source) && !selectedIds.value.has(item.target)) } clear(); record(); dirty() }
function copy() { const active = nodes.value.filter((item) => selectedIds.value.has(item.id)); if (active.length) copied.value = active.map((item) => ({ ...item, ports: [...item.ports] })) }
function paste() { if (!copied.value?.length) return; const created = copied.value.map((item) => node({ ...item, id: `${item.type}-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`, name: `${item.name} 副本`, x: item.x + 40, y: item.y + 40 })); nodes.value.push(...created); selectedIds.value = new Set(created.map((item) => item.id)); record(); dirty() }
function showConnectionNotice(message) {
  connectionNotice.value = message
  window.clearTimeout(showConnectionNotice.timer)
  showConnectionNotice.timer = window.setTimeout(() => { connectionNotice.value = '' }, 2600)
}
function startConnect(item, port) {
  const selectedPort = availablePort(item, port)
  if (!selectedPort) { showConnectionNotice(`${item?.name || '设备'}可用端口不足`); return }
  connectFrom.value = { id: item.id, port: selectedPort }
  connectPreview.value = null
  tool.value = 'connect'
  selectedIds.value = new Set([item.id])
}
function finishConnect(item) {
  if (!connectFrom.value) return startConnect(item)
  if (connectFrom.value.id === item.id) { connectFrom.value = null; connectPreview.value = null; tool.value = 'select'; return }
  const targetPort = availablePort(item)
  if (!targetPort) { showConnectionNotice(`${item?.name || '目标设备'}可用端口不足`); return }
  edges.value.push(edge({ source: connectFrom.value.id, sourcePort: connectFrom.value.port, target: item.id, targetPort }))
  record()
  dirty()
  connectFrom.value = null
  connectPreview.value = null
  tool.value = 'select'
}
function alignSelection(axis, edge) {
  const items = selectedNodes.value
  if (items.length < 2) return
  const value = axis === 'x'
    ? edge === 'start' ? Math.min(...items.map((item) => item.x)) : edge === 'center' ? (Math.min(...items.map((item) => item.x)) + Math.max(...items.map((item) => item.x + item.width))) / 2 : Math.max(...items.map((item) => item.x + item.width))
    : edge === 'start' ? Math.min(...items.map((item) => item.y)) : edge === 'center' ? (Math.min(...items.map((item) => item.y)) + Math.max(...items.map((item) => item.y + item.height))) / 2 : Math.max(...items.map((item) => item.y + item.height))
  items.forEach((item) => {
    if (axis === 'x') item.x = snap(edge === 'start' ? value : edge === 'center' ? value - item.width / 2 : value - item.width)
    else item.y = snap(edge === 'start' ? value : edge === 'center' ? value - item.height / 2 : value - item.height)
  })
  record()
  dirty()
}
function distributeSelection(axis) {
  const items = [...selectedNodes.value].sort((a, b) => axis === 'x' ? a.x - b.x : a.y - b.y)
  if (items.length < 3) return
  if (axis === 'x') {
    const gap = (items.at(-1).x + items.at(-1).width - items[0].x - items.reduce((sum, item) => sum + item.width, 0)) / (items.length - 1)
    let cursor = items[0].x
    items.forEach((item) => { item.x = snap(cursor); cursor += item.width + gap })
  } else {
    const gap = (items.at(-1).y + items.at(-1).height - items[0].y - items.reduce((sum, item) => sum + item.height, 0)) / (items.length - 1)
    let cursor = items[0].y
    items.forEach((item) => { item.y = snap(cursor); cursor += item.height + gap })
  }
  record()
  dirty()
}
function arrangeSelection() {
  const items = [...selectedNodes.value]
  if (items.length < 2) return
  const bounds = selectionBounds.value
  const columns = Math.max(2, Math.ceil(Math.sqrt(items.length)))
  const gapX = 36, gapY = 34
  const cellWidth = Math.max(...items.map((item) => item.width)) + gapX
  const cellHeight = Math.max(...items.map((item) => item.height)) + gapY
  items.forEach((item, index) => {
    const column = index % columns
    const row = Math.floor(index / columns)
    item.x = snap(bounds.left + column * cellWidth)
    item.y = snap(bounds.top + row * cellHeight)
  })
  record()
  dirty()
}
function cancelLayoutAnimation() {
  if (layoutAnimationFrame) cancelAnimationFrame(layoutAnimationFrame)
  layoutAnimationFrame = 0
}
function animateLayout(targets) {
  cancelLayoutAnimation()
  // Layout used to animate every node from its previous position. A malformed
  // historical coordinate could turn one animation frame into NaN, leaving the
  // viewport invalid and the editor apparently locked. Apply one validated,
  // deterministic layout instead so the canvas is immediately editable.
  sanitizeTopologyGeometry()
  nodes.value.forEach((item) => {
    const target = targets.get(item.id)
    if (!target) return
    item.x = snap(finiteNumber(target.x, item.x))
    item.y = snap(finiteNumber(target.y, item.y))
  })
  sanitizeTopologyGeometry()
  drag.value = null
  marquee.value = null
  guides.value = { x: null, y: null }
  record()
  dirty()
  fit(false)
  return Promise.resolve()
}
function layout() {
  // Ignore repeated clicks while an animation is in progress. A second
  // layout based on half-moved nodes used to race the first one and corrupt
  // the visual arrangement.
  if (layoutAnimationFrame) return Promise.resolve()
  if (!nodes.value.length) return fit()
  sanitizeTopologyGeometry()

  const adjacency = new Map(nodes.value.map((item) => [item.id, []]))
  edges.value.forEach((item) => {
    adjacency.get(item.source)?.push(item.target)
    adjacency.get(item.target)?.push(item.source)
  })
  const degree = (item) => adjacency.get(item.id)?.length || 0
  const typeOf = (item) => String(item?.type || '').toLowerCase().replaceAll('_', '-')
  const nameOf = (item) => String(item?.name || '').toLowerCase()
  const matches = (item, values) => {
    const type = typeOf(item)
    const name = nameOf(item)
    const hasUsableType = Boolean(type) && !['device', 'network-device', 'unknown'].includes(type)
    return values.some((value) => type.includes(value) || (!hasUsableType && name.includes(value)))
  }
  const isInternet = (item) => matches(item, ['internet', 'cloud', '互联网', '云'])
  const isRouter = (item) => matches(item, ['router', '路由器'])
  const isSecurity = (item) => matches(item, ['firewall', 'vpn-gateway', 'ids-ips', 'ids', 'ips', '防火墙', '入侵检测', '入侵防御', 'vpn'])
  const isCore = (item) => matches(item, ['core-switch', 'layer3-switch', '核心交换机', '三层交换机'])
  const isAccess = (item) => !isCore(item) && matches(item, ['access-switch', 'aggregation-switch', 'distribution-switch', '接入交换机', '汇聚交换机', '汇聚'])
  const isController = (item) => matches(item, ['ac-controller', 'wireless-controller', 'wireless-ac', '无线控制器', '无线ac'])
  const isService = (item) => matches(item, ['server', 'database', 'nas', '服务器', '数据库', '存储'])
  const isEndpoint = (item) => matches(item, ['wireless-ap', 'access-point', 'pc', 'laptop', 'printer', 'ip-phone', 'camera', '终端', '电脑', '笔记本', '打印机', '电话', '摄像头', 'ap'])
  const isInfrastructure = (item) => isInternet(item) || isRouter(item) || isSecurity(item) || isCore(item) || isAccess(item) || isController(item) || isService(item)

  // Keep the layout in generous world coordinates so the topology breathes on
  // wide screens instead of collapsing every layer into the center.
  // Keep layout coordinates independent of the current zoom. `fit()` changes
  // zoom after every layout; using zoom here made repeated clicks drift the
  // entire topology farther right on each pass.
  const worldWidth = Math.max(2200, (canvasSize.value.width || 1600) * 1.45)
  const centerX = worldWidth / 2
  const targets = new Map()
  const placed = new Set()
  const place = (item, center, top) => {
    if (!item || placed.has(item.id)) return
    targets.set(item.id, { x: snap(center - item.width / 2), y: snap(top) })
    placed.add(item.id)
  }
  const placeRow = (items, top, gap = 44, center = centerX) => {
    const list = items.filter((item) => item && !placed.has(item.id))
    if (!list.length) return
    const total = list.reduce((sum, item) => sum + item.width, 0) + gap * Math.max(0, list.length - 1)
    let cursor = center - total / 2
    list.forEach((item) => {
      place(item, cursor + item.width / 2, top)
      cursor += item.width + gap
    })
  }
  const placeColumn = (items, x, top, gap = 42) => {
    items.filter((item) => item && !placed.has(item.id)).forEach((item, index) => place(item, x, top + index * (item.height + gap)))
  }

  const corePool = nodes.value.filter(isCore).sort((left, right) => degree(right) - degree(left))
  const fallbackHub = nodes.value
    .filter((item) => !isInternet(item) && !isRouter(item) && !isEndpoint(item))
    .sort((left, right) => degree(right) - degree(left))[0]
  const mainCore = corePool[0] || fallbackHub || nodes.value[0]
  const coreRow = corePool.length ? corePool : [mainCore]

  // Main trunk: Internet -> router -> core. Core switches are deliberately
  // separated so their redundant uplinks and downstream zones remain legible.
  placeRow(nodes.value.filter(isInternet), 70, 140)
  placeRow(nodes.value.filter(isRouter), 225, 140)
  placeRow(coreRow, 430, coreRow.length > 1 ? 220 : 120)
  const placedCoreCenters = coreRow.map((item) => {
    const target = targets.get(item)
    return target ? target.x + item.width / 2 : null
  }).filter((value) => value !== null)
  const coreCenter = placedCoreCenters.length
    ? placedCoreCenters.reduce((sum, value) => sum + value, 0) / placedCoreCenters.length
    : centerX

  // Security and shared services sit around the core instead of being stretched into another full row.
  const security = nodes.value.filter(isSecurity)
  const securityOffsets = [-560, 560, -330, 330, -800, 800]
  security.forEach((item, index) => place(item, coreCenter + (securityOffsets[index] || (index % 2 ? 330 : -330)), 315 + Math.floor(index / 2) * 92))
  const serviceNodes = nodes.value.filter((item) => isService(item) && !(adjacency.get(item.id) || []).some((id) => isAccess(byId(id))))
  placeRow(serviceNodes, 650, 120, centerX - 520)
  const controllers = nodes.value.filter(isController)
  if (controllers.length === 1 && coreRow.length >= 2) {
    // A single AC stays centered over the redundant core pair because it must
    // dual-home to both cores rather than visually belonging to one side.
    place(controllers[0], coreCenter, 290)
  } else if (controllers.length >= 2 && coreRow.length >= 2) {
    // Dual AC is spread left/right around the core pair so the redundant
    // wireless control plane reads clearly at a glance.
    place(controllers[0], placedCoreCenters[0] - 170, 290)
    place(controllers[1], placedCoreCenters.at(-1) + 170, 290)
    placeRow(controllers.slice(2), 175, 110, coreCenter)
  } else {
    placeRow(controllers, 290, 110, coreCenter)
  }

  // Build compact groups under each access/distribution switch using the actual graph edges.
  const serverAccessNodes = nodes.value.filter((item) => isAccess(item) && (
    nameOf(item).includes('服务器') || nameOf(item).includes('机房') || nameOf(item).includes('server') ||
    (adjacency.get(item.id) || []).map((id) => byId(id)).some((child) => child && isService(child))
  ))
  const serverZoneCenter = centerX
  placeRow(serverAccessNodes, 635, 170, serverZoneCenter)
  serverAccessNodes.forEach((access, index) => {
    const children = (adjacency.get(access.id) || [])
      .map((id) => byId(id))
      .filter((item) => item && !placed.has(item.id) && !isInternet(item) && !isRouter(item) && !isSecurity(item) && !isCore(item) && !isAccess(item) && !isController(item))
    const accessTarget = targets.get(access)
    const accessCenter = accessTarget ? accessTarget.x + access.width / 2 : serverZoneCenter
    const columns = Math.min(3, Math.max(1, children.length))
    children.forEach((item, childIndex) => {
      const column = childIndex % columns
      const row = Math.floor(childIndex / columns)
      const rowItems = children.slice(row * columns, row * columns + columns)
      const rowWidth = rowItems.reduce((sum, child) => sum + child.width, 0) + 44 * Math.max(0, rowItems.length - 1)
      const rowStart = accessCenter - rowWidth / 2
      const offset = rowItems.slice(0, column).reduce((sum, child) => sum + child.width + 44, 0)
      place(item, rowStart + offset + item.width / 2, 755 + row * 112)
    })
  })
  const accessNodes = nodes.value.filter((item) => isAccess(item) && !serverAccessNodes.includes(item)).sort((left, right) => {
    const leftChildren = (adjacency.get(left.id) || []).filter((id) => isEndpoint(byId(id))).length
    const rightChildren = (adjacency.get(right.id) || []).filter((id) => isEndpoint(byId(id))).length
    return rightChildren - leftChildren || String(left.name).localeCompare(String(right.name), 'zh-CN')
  })
  const groups = accessNodes.map((access) => {
    const children = (adjacency.get(access.id) || [])
      .map((id) => byId(id))
      .filter((item) => item && !placed.has(item.id) && !isInternet(item) && !isRouter(item) && !isSecurity(item) && !isCore(item) && !isAccess(item) && !isController(item))
    const fallbackChildren = children.length ? children : (adjacency.get(access.id) || []).map((id) => byId(id)).filter((item) => item && !placed.has(item.id) && (isEndpoint(item) || isService(item)))
    const columns = Math.min(2, Math.max(1, fallbackChildren.length))
    const width = Math.max(access.width, columns * 76 + (columns - 1) * 34)
    return { access, children: fallbackChildren, width }
  })
  const groupGap = 120
  const groupWidth = groups.reduce((sum, group) => sum + group.width, 0) + groupGap * Math.max(0, groups.length - 1)
  let groupCursor = centerX - groupWidth / 2
  groups.forEach((group) => {
    const groupCenter = groupCursor + group.width / 2
    place(group.access, groupCenter, 820)
    const columns = Math.min(3, Math.max(1, group.children.length))
    const rows = Math.max(1, Math.ceil(group.children.length / columns))
    group.children.forEach((item, index) => {
      const column = index % columns
      const row = Math.floor(index / columns)
      const rowItems = group.children.slice(row * columns, row * columns + columns)
      const rowWidth = rowItems.reduce((sum, child) => sum + child.width, 0) + 44 * Math.max(0, rowItems.length - 1)
      const rowStart = groupCenter - rowWidth / 2
      const offset = rowItems.slice(0, column).reduce((sum, child) => sum + child.width + 44, 0)
      place(item, rowStart + offset + item.width / 2, 940 + row * 112)
    })
    groupCursor += group.width + groupGap
  })

  // Any remaining devices are kept in a small, separate lower row rather than expanding the main topology.
  const remaining = nodes.value.filter((item) => !placed.has(item.id))
  if (remaining.length) placeRow(remaining, 1100, 80, centerX)

  return animateLayout(targets)
}
function cancelViewportAnimation() {
  if (viewportAnimationFrame) cancelAnimationFrame(viewportAnimationFrame)
  viewportAnimationFrame = 0
}
function updateViewport(target, animate = true) {
  cancelViewportAnimation()
  const safeTarget = {
    zoom: clampZoom(finiteNumber(target?.zoom, .9)),
    pan: {
      x: finiteNumber(target?.pan?.x, 36),
      y: finiteNumber(target?.pan?.y, 24)
    }
  }
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (!animate || reducedMotion) { zoom.value = safeTarget.zoom; pan.value = safeTarget.pan; return }
  const start = {
    zoom: finiteNumber(zoom.value, .9),
    pan: { x: finiteNumber(pan.value?.x, 36), y: finiteNumber(pan.value?.y, 24) }
  }
  const startedAt = performance.now()
  const easeOut = (value) => 1 - Math.pow(1 - value, 3)
  const frame = (now) => {
    const progress = Math.min(1, (now - startedAt) / 240)
    const eased = easeOut(progress)
    zoom.value = start.zoom + (safeTarget.zoom - start.zoom) * eased
    pan.value = { x: start.pan.x + (safeTarget.pan.x - start.pan.x) * eased, y: start.pan.y + (safeTarget.pan.y - start.pan.y) * eased }
    if (progress < 1) viewportAnimationFrame = requestAnimationFrame(frame)
    else viewportAnimationFrame = 0
  }
  viewportAnimationFrame = requestAnimationFrame(frame)
}
function fit(animate = true) {
  const rect = canvas.value?.getBoundingClientRect()
  if (!rect) return
  sanitizeTopologyGeometry()
  if (!nodes.value.length) return updateViewport({ zoom: 1, pan: { x: rect.width / 2, y: rect.height / 2 } }, animate)
  const minX = Math.min(...nodes.value.map((item) => item.x))
  const minY = Math.min(...nodes.value.map((item) => item.y))
  const maxX = Math.max(...nodes.value.map((item) => item.x + item.width))
  const maxY = Math.max(...nodes.value.map((item) => item.y + item.height))
  const width = Math.max(1, maxX - minX)
  const height = Math.max(1, maxY - minY)
  const padding = Math.max(60, Math.min(90, Math.min(rect.width, rect.height) * .12))
  const targetZoom = clampZoom(finiteNumber(Math.min((rect.width - padding * 2) / width, (rect.height - padding * 2) / height), .9))
  const zoomValue = nodes.value.length === 1 ? Math.min(1, targetZoom) : targetZoom
  updateViewport({ zoom: zoomValue, pan: { x: rect.width / 2 - (minX + width / 2) * zoomValue, y: rect.height / 2 - (minY + height / 2) * zoomValue } }, animate)
}
function toggleNodeNames() {
  showNodeNames.value = !showNodeNames.value
  try { window.localStorage.setItem(NODE_LABEL_STORAGE_KEY, String(showNodeNames.value)) } catch { /* Keep the current session preference when storage is unavailable. */ }
}
function canvasKeys(event) {
  if (event.key.toLowerCase() === 'f' && !event.ctrlKey && !event.metaKey && !event.altKey) { event.preventDefault(); fit() }
}
function handleKeyup(event) {
  if (event.code === 'Space') spacePressed.value = false
}
function handleWindowBlur() {
  spacePressed.value = false
  if (drag.value?.kind === 'pan') { flushPan(); drag.value = null }
}
function assistantIsNearBottom(scroll = assistantScroll.value) {
  if (!scroll) return true
  return scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 56
}
function updateAssistantScrollState(event) {
  const scroll = event?.currentTarget || assistantScroll.value
  if (!scroll) return
  assistantAtBottom.value = assistantIsNearBottom(scroll)
  assistantHasOverflow.value = scroll.scrollHeight > scroll.clientHeight + 8
  if (assistantStreamState) assistantStreamState.followTail = assistantAtBottom.value
  if (assistantLatestButton) {
    assistantLatestButton.hidden = !(assistantHasOverflow.value && !assistantAtBottom.value)
    assistantLatestButton.textContent = assistantSending.value ? '↓ 查看最新回复' : '↓ 回到底部'
    const panel = assistantScroll.value?.closest('.assistant-panel')
    if (panel) {
      const rect = panel.getBoundingClientRect()
      assistantLatestButton.style.left = `${rect.left + rect.width / 2}px`
      assistantLatestButton.style.top = `${rect.top + rect.height / 2}px`
    }
  }
}
function bindAssistantScrollControls() {
  const scroll = assistantScroll.value
  const panel = scroll?.closest('.assistant-panel')
  if (!scroll || !panel) return
  if (assistantScrollListenerTarget && assistantScrollListenerTarget !== scroll) {
    assistantScrollListenerTarget.removeEventListener('scroll', updateAssistantScrollState)
  }
  if (assistantScrollListenerTarget !== scroll) {
    assistantScrollListenerTarget = scroll
    scroll.addEventListener('scroll', updateAssistantScrollState, { passive: true })
  }
  if (!assistantLatestButton || !document.body.contains(assistantLatestButton)) {
    assistantLatestButton?.remove()
    assistantLatestButton = document.createElement('button')
    assistantLatestButton.type = 'button'
    assistantLatestButton.className = 'assistant-scroll-latest'
    assistantLatestButton.addEventListener('click', scrollAssistantToLatest)
    document.body.appendChild(assistantLatestButton)
  }
  updateAssistantScrollState()
}
function scrollAssistantToEnd({ behavior = 'auto', force = false } = {}) {
  nextTick(() => {
    const scroll = assistantScroll.value
    if (!scroll) return
    if (!force && !assistantIsNearBottom(scroll)) {
      updateAssistantScrollState()
      return
    }
    scroll.scrollTo({ top: Math.max(0, scroll.scrollHeight - scroll.clientHeight), behavior })
    assistantAtBottom.value = true
    assistantHasOverflow.value = scroll.scrollHeight > scroll.clientHeight + 8
  })
}
const PROJECT_FILE_ACCEPT = '.pdf,.docx,.xlsx,.txt,.md'
const PROJECT_FILE_MAX_SIZE = 10 * 1024 * 1024
const PROJECT_FILE_MAX_COUNT = 10
const PROJECT_FILE_MAX_TOTAL_SIZE = 50 * 1024 * 1024
function clearAssistantAttachment() {
  assistantAttachment.value = null
  assistantAttachmentError.value = ''
}
async function handleAssistantFileSelection(event) {
  const [file] = event.target.files || []
  event.target.value = ''
  if (!file || !props.projectId) return
  const allowed = /\.(pdf|docx|xlsx|txt|md)$/i.test(file.name || '')
  if (!allowed) {
    assistantAttachmentError.value = '仅支持 PDF、DOCX、XLSX、TXT、Markdown 文件。'
    return
  }
  const limits = assistantFileLimits.value
  if (assistantProjectFiles.value.length >= limits.count) {
    assistantAttachmentError.value = `当前套餐每个项目最多上传 ${limits.count} 份资料。`
    return
  }
  if (file.size > limits.singleMb * 1024 * 1024) {
    assistantAttachmentError.value = `单个资料不能超过 ${limits.singleMb} MB。`
    return
  }
  const totalSize = assistantProjectFiles.value.reduce((sum, item) => sum + Number(item.size || 0), 0)
  if (totalSize + file.size > limits.capacityMb * 1024 * 1024) {
    assistantAttachmentError.value = `项目资料总容量不能超过 ${limits.capacityMb} MB。`
    return
  }
  assistantAttachmentError.value = ''
  assistantAttachmentUploading.value = true
  try {
    const uploaded = await uploadProjectFile(props.projectId, file)
    assistantProjectFiles.value = [uploaded, ...assistantProjectFiles.value.filter((item) => item.id !== uploaded.id)]
    if (uploaded?.textAvailable === false || uploaded?.status === '读取失败') {
      assistantAttachment.value = null
      assistantAttachmentError.value = uploaded?.errorMessage || '资料已上传，但未提取到可读取的正文内容。请换用包含文本层的文件。'
    } else {
      assistantAttachment.value = uploaded
    }
  } catch (error) {
    assistantAttachmentError.value = error?.message || '资料上传失败，请重试。'
  } finally {
    assistantAttachmentUploading.value = false
  }
}
async function loadAssistantProjectFiles() {
  if (!props.projectId) return
  try {
    const [files, subscription] = await Promise.all([listProjectFiles(props.projectId), getUserSubscription()])
    assistantProjectFiles.value = Array.isArray(files) ? files : []
    const quota = subscription?.quota || {}
    assistantFileLimits.value = {
      count: Math.max(1, Number(quota.fileUploads?.total ?? PROJECT_FILE_MAX_COUNT)),
      singleMb: Math.max(1, Number(quota.singleFileSizeMb?.total ?? PROJECT_FILE_MAX_SIZE / (1024 * 1024))),
      capacityMb: Math.max(1, Number(quota.knowledgeCapacityMb?.total ?? PROJECT_FILE_MAX_TOTAL_SIZE / (1024 * 1024)))
    }
  } catch {
    assistantProjectFiles.value = []
    assistantFileLimits.value = { count: PROJECT_FILE_MAX_COUNT, singleMb: PROJECT_FILE_MAX_SIZE / (1024 * 1024), capacityMb: PROJECT_FILE_MAX_TOTAL_SIZE / (1024 * 1024) }
  }
}
function scrollAssistantToLatest() {
  if (assistantStreamState) assistantStreamState.followTail = true
  scrollAssistantToEnd({ behavior: 'smooth', force: true })
  if (assistantLatestButton) assistantLatestButton.hidden = true
}
function clearAssistantStreamState() {
  if (!assistantStreamState) return
  assistantStreamState.cancelled = true
  if (assistantStreamState.timerId) window.clearTimeout(assistantStreamState.timerId)
  assistantStreamState.queue.length = 0
  assistantStreamState = null
  assistantSending.value = false
  assistantSendLock.value = false
}
function pumpAssistantStream(state) {
  if (!state || state.cancelled) return
  state.timerId = 0
  if (state.queue.length) {
    const next = [...assistantMessages.value]
    if (next[state.index]) {
      // Consume exactly one Unicode code point per tick. The server chunk is
      // still shown immediately, but the browser renders it progressively so
      // long answers remain readable and scrollable while arriving.
      next[state.index] = {
        ...next[state.index],
        content: `${next[state.index].content || ''}${state.queue.shift()}`,
        status: state.done ? 'draining' : 'streaming'
      }
      assistantMessages.value = next
    }
    if (state.followTail) scrollAssistantToEnd({ force: true })
    else updateAssistantScrollState()
    state.timerId = window.setTimeout(() => pumpAssistantStream(state), 18)
    return
  }
  if (!state.done) return
  if (state.eof && !state.suppressContent && !assistantMessages.value[state.index]?.content) state.interrupted = true
  const next = [...assistantMessages.value]
  if (next[state.index]) {
    next[state.index] = { ...next[state.index], status: state.interrupted ? 'interrupted' : state.error ? 'failed' : 'complete', ephemeral: false }
    assistantMessages.value = next
  }
  assistantSending.value = false
  assistantSendLock.value = false
  assistantAbortController.value = null
  assistantGenerationState.value = state.interrupted ? 'INTERRUPTED' : state.error ? 'FAILED' : 'COMPLETED'
  if (assistantStreamState === state) assistantStreamState = null
  window.setTimeout(() => {
    if (assistantStreamState !== state && ['COMPLETED', 'FAILED', 'INTERRUPTED'].includes(assistantGenerationState.value)) assistantGenerationState.value = 'IDLE'
  }, 0)
}
function enqueueAssistantText(state, content) {
  if (!state || state.cancelled || typeof content !== 'string' || !content) return
  if (state.followTail === undefined) state.followTail = assistantIsNearBottom()
  state.queue.push(...Array.from(content))
  if (!state.timerId) state.timerId = window.setTimeout(() => pumpAssistantStream(state), 0)
}
function addAssistantMessage(message) {
  const created = { id: `message-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`, role: 'assistant', ...message }
  assistantMessages.value = [...assistantMessages.value, created]
  scrollAssistantToEnd({ force: true })
  return created
}
function normalizeAssistantMessage(message) {
  const status = String(message.status || 'complete').toLowerCase()
  const role = message.role === 'user' ? 'user' : 'assistant'
  const generatingInBackground = role === 'assistant' && ['pending', 'generating', 'streaming', 'waiting'].includes(status)
  const rawContent = message.content || ''
  const internalPlanPrompt = role === 'user' && rawContent.includes('当前检查关联的真实设备 ID')
  const displayContent = internalPlanPrompt
    ? `请修复拓扑检查项：${(rawContent.match(/针对“([^”]+)”/) || [])[1] || '当前风险'}`
    : rawContent.length > 2400 ? `${rawContent.slice(0, 2400)}\n…（内部上下文已折叠）` : rawContent
  return {
    id: `chat-${message.id || `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`,
    role,
    content: displayContent,
    requestContent: rawContent,
    createdAt: message.createdAt,
    status: generatingInBackground ? 'background' : status
  }
}
function stopAssistantBackgroundPolling() {
  if (!assistantBackgroundPollTimer) return
  window.clearTimeout(assistantBackgroundPollTimer)
  assistantBackgroundPollTimer = 0
}
function scheduleAssistantBackgroundPolling() {
  stopAssistantBackgroundPolling()
  const visibleMessages = assistantMessages.value.filter((message) => ['user', 'assistant'].includes(message.role))
  const lastVisibleMessage = visibleMessages.at(-1)
  // A pending server turn is intentionally blank and therefore omitted by the
  // history API. A user message at the end means the assistant may still be
  // generating after a refresh, so keep polling until its reply is persisted.
  const hasBackgroundTurn = assistantMessages.value.some((message) => message.role === 'assistant' && message.status === 'background')
    || lastVisibleMessage?.role === 'user'
  if (!hasBackgroundTurn || !props.projectId || !assistantConversationPublicId.value || assistantSending.value) return
  assistantBackgroundPollTimer = window.setTimeout(async () => {
    try {
      const response = await getProjectChatMessages(props.projectId, assistantConversationPublicId.value)
      const items = response?.items || response || []
      if (Array.isArray(items) && items.length) {
        const hadBackgroundTurn = assistantMessages.value.some((message) => message.role === 'assistant' && message.status === 'background')
        const nextMessages = items.map(normalizeAssistantMessage)
        const completedAssistant = [...nextMessages].reverse().find((message) => message.role === 'assistant' && message.status === 'complete' && message.content)
        const parsedCompletion = completedAssistant ? parseGeneratedTopology(completedAssistant.content) : null
        let recoveredPlan = ''
        if (hadBackgroundTurn && completedAssistant && parsedCompletion?.plan && !assistantBackgroundCompletionHandled.has(completedAssistant.id)) {
          assistantBackgroundCompletionHandled.add(completedAssistant.id)
          recoveredPlan = completedAssistant.content
          completedAssistant.content = parsedCompletion.summary || '已恢复后台完成的拓扑规划预览，请确认后应用。'
        }
        assistantMessages.value = nextMessages
        assistantError.value = ''
        if (recoveredPlan) await applyGeneratedTopology(recoveredPlan, completedAssistant.id)
        scrollAssistantToEnd()
      }
    } catch {
      // Keep the saved history visible and retry while the server-side task runs.
    } finally {
      scheduleAssistantBackgroundPolling()
    }
  }, 1600)
}
function normalizeAiPlan(candidate) {
  const source = candidate?.changePlan || candidate?.plan || candidate
  if (!source || typeof source !== 'object') return null
  const explicitPlanArrays = [
    source.addNodes, source.nodesToAdd, source.updateNodes, source.nodesToUpdate,
    source.removeNodes, source.nodesToRemove, source.addEdges, source.edgesToAdd,
    source.updateEdges, source.edgesToUpdate, source.removeEdges, source.edgesToRemove
  ].some((value) => Array.isArray(value))
  const planMarker = source.type === 'topology_change_plan' || source.action === 'TOPOLOGY_PLAN'
  // A response containing nodes + edges without an explicit change-plan marker
  // is a complete topology snapshot. Let parseGeneratedTopology handle it as
  // a merge so existing devices are not mistaken for newly added devices.
  const fullTopologySnapshot = Array.isArray(source.nodes)
    && (Array.isArray(source.edges) || Array.isArray(source.connections))
    && !explicitPlanArrays && !planMarker
  if (fullTopologySnapshot) return null
  const pickArray = (...values) => values.find((value) => Array.isArray(value) && value.length) || values.find((value) => Array.isArray(value)) || []
  const arrays = {
    addNodes: pickArray(source.addNodes, source.nodesToAdd, planMarker || !Array.isArray(source.edges) ? source.nodes : []),
    updateNodes: pickArray(source.updateNodes, source.nodesToUpdate),
    removeNodes: pickArray(source.removeNodes, source.nodesToRemove),
    addEdges: pickArray(source.addEdges, source.edgesToAdd, source.connections, planMarker || !Array.isArray(source.nodes) ? source.edges : []),
    updateEdges: pickArray(source.updateEdges, source.edgesToUpdate),
    removeEdges: pickArray(source.removeEdges, source.edgesToRemove)
  }
  const hasPlanFields = Object.values(arrays).some((items) => items.length) || planMarker || Array.isArray(source.connections)
  return hasPlanFields ? { ...source, ...arrays } : null
}
function topologyReference(value) {
  if (value && typeof value === 'object') {
    return String(value.id || value.nodeId || value.deviceId || value.tempId || value.name || value.label || value.resourceId || value.resourceKey || '').trim()
  }
  return String(value ?? '').trim()
}
function nodeReferences(item) {
  return [item?.id, item?.nodeId, item?.deviceId, item?.tempId, item?.name, item?.deviceName, item?.label, item?.resourceId, item?.resourceKey]
    .map(topologyReference).filter(Boolean)
}
function resourceAlias(value) {
  return String(value || '').trim().toLowerCase().replaceAll('_', '-').replace(/\s+/g, '-')
}
function resolveCatalogResource(item) {
  const candidates = [item?.type, item?.deviceType, item?.resourceKey, item?.resourceId, item?.key, item?.englishName, item?.label, item?.name]
    .map(resourceAlias).filter(Boolean)
  for (const candidate of candidates) if (catalog[candidate]) return catalog[candidate]
  return resources.value.find((resource) => {
    const aliases = [resource.type, resource.resourceId, resource.key, resource.id, resource.englishName, resource.label, resource.name].map(resourceAlias)
    return candidates.some((candidate) => aliases.includes(candidate))
  }) || null
}
function aiResourceAllowed(item, definition) {
  if (!definition) return false
  if (!aiAllowedResourceTypes.value.size) return definition.allowAi !== false
  const aliases = [item?.type, item?.deviceType, item?.resourceKey, item?.resourceId, item?.key, definition.type, definition.resourceId, definition.key]
    .map(resourceAlias).filter(Boolean)
  return aliases.some((alias) => aiAllowedResourceTypes.value.has(alias))
}
function showTopologyPlanDialog(title, message, details = []) {
  topologyPlanDialog.value = {
    title: String(title || '无法应用拓扑规划'),
    message: String(message || '本次拓扑规划未能应用，当前画布没有被修改。'),
    details: Array.isArray(details) ? details.filter(Boolean).map(String) : []
  }
}
function restorePendingChange() {
  pendingChange.value = null
  const stored = readStoredJson(projectScopedStorageKey(PENDING_CHANGE_STORAGE_PREFIX), null)
  const resolved = resolvedChangeIds()
  const storedChange = stored?.change
  if (!storedChange?.id || !storedChange?.plan || resolved.has(String(storedChange.id))) {
    pendingChange.value = null
    return
  }
  let target = stored.messageId ? assistantMessages.value.find((message) => message.id === stored.messageId) : null
  if (!target) {
    target = [...assistantMessages.value].reverse().find((message) => {
      if (message.role !== 'assistant') return false
      return Boolean(parseGeneratedTopology(message.content)?.plan)
    })
  }
  if (!target) {
    const syntheticId = String(stored.messageId || `pending-${storedChange.id}`)
    target = { id: syntheticId, role: 'assistant', content: storedChange.summary || 'AI 已完成拓扑规划，请确认是否应用。', status: 'complete' }
    assistantMessages.value = [...assistantMessages.value, target]
  }
  const change = { ...storedChange, messageId: target.id }
  pendingChange.value = change
  assistantMessages.value = assistantMessages.value.map((message) => message.id === target.id
    ? { ...message, content: assistantDisplayContent(message) || change.summary || 'AI 已完成拓扑规划，请确认是否应用。', status: 'complete', ephemeral: false, operation: previewOperation(change.plan), change }
    : message)
  persistPendingChange(target.id, change)
}
function assistantDisplayContent(message) {
  const content = String(message?.content || '').trim()
  if (!content) return ''
  // Planning responses are contracts for the editor, not useful chat prose.
  // Keep the concise summary in the transcript and expose details in the
  // lightweight operation preview below it.
  if (message?.role === 'assistant') {
    const parsed = parseGeneratedTopology(content)
    if (parsed?.plan) return parsed.summary || '已生成拓扑变更预览，请查看下方操作卡片。'
  }
  return content
}
function closeTopologyPlanDialog() { topologyPlanDialog.value = null }
function topologyPlanFailure(error, fallback = '拓扑规划应用失败，当前画布没有被修改。') {
  const code = String(error?.code || '').toUpperCase()
  if (code === 'TOPOLOGY_PLAN_RESOURCE_INVALID' || code === 'TOPOLOGY_RESOURCE_NOT_FOUND' || code === 'TOPOLOGY_RESOURCE_AI_DISABLED') {
    return {
      title: '设备资源不可用',
      message: '本次规划引用的设备不在当前可用于 AI 的设备库中，因此没有应用到画布。',
      details: ['请在管理端启用对应拓扑资源并允许 AI 使用，或重新生成规划。']
    }
  }
  if (code === 'TOPOLOGY_PORT_CAPACITY_EXCEEDED' || code === 'TOPOLOGY_PLAN_PORT_INVALID') {
    return {
      title: '设备端口不足',
      message: '规划所需的设备端口无法满足连接要求，当前画布没有被修改。',
      details: ['请增加设备端口资源，或让 AI 使用更多接入设备后重新规划。']
    }
  }
  return { title: '无法应用拓扑规划', message: error?.message || fallback, details: [] }
}
function normalizePlanResources(plan) {
  const addNodes = Array.isArray(plan?.addNodes) ? plan.addNodes : []
  if (!addNodes.length) return { plan }
  const rejected = []
  const normalizedNodes = addNodes.map((item, index) => {
    const definition = resolveCatalogResource(item)
    if (!definition || !aiResourceAllowed(item, definition)) {
      rejected.push(item?.resourceKey || item?.resourceId || item?.type || item?.deviceType || item?.name || `设备 ${index + 1}`)
      return null
    }
    // The AI may use a display name such as "Core Switch". Persist only the
    // canonical resource fields from the device library so server validation,
    // ports and icons always refer to the same configured resource.
    return {
      ...item,
      type: definition.type,
      deviceType: definition.type,
      resourceId: definition.resourceId,
      resourceKey: definition.key || definition.type,
      ports: [...definition.ports]
    }
  })
  if (rejected.length) {
    return {
      error: 'AI 规划引用了当前设备库中不可用的设备，因此本次规划没有应用。',
      rejected: [...new Set(rejected)]
    }
  }
  return { plan: { ...plan, addNodes: normalizedNodes.filter(Boolean) } }
}
function parseGeneratedTopology(content) {
  const source = String(content || '').trim()
  const candidates = [source]
  const fenced = source.match(/```(?:json)?\s*([\s\S]*?)```/i)?.[1]?.trim()
  if (fenced) candidates.unshift(fenced)
  for (const candidate of candidates) {
    try {
      const parsed = JSON.parse(candidate)
      const structuredPlan = normalizeAiPlan(parsed)
      if (structuredPlan) {
        const normalized = normalizePlanResources(structuredPlan)
        if (normalized.error) return normalized
        return { plan: normalized.plan, summary: normalized.plan.summary || normalized.plan.reasoningSummary?.join('；') || '' }
      }
      const topology = parsed?.topology && typeof parsed.topology === 'object' ? parsed.topology : parsed
      const topologyEdges = Array.isArray(topology?.edges) ? topology.edges : topology?.connections
      if (!Array.isArray(topology?.nodes) || !Array.isArray(topologyEdges)) continue
      const existingByReference = new Map()
      nodes.value.forEach((item) => nodeReferences(item).forEach((reference) => existingByReference.set(reference.toLowerCase(), item)))
      const rejectedResources = []
      const generatedReferenceMap = new Map()
      const generatedNodes = topology.nodes.map((item, index) => {
        const definition = resolveCatalogResource(item)
        const type = resourceAlias(item.type || item.deviceType || item.resourceKey || item.resourceId || definition?.type)
        const canonicalType = definition?.type || type

        const requestedId = topologyReference(item.id || item.nodeId || item.deviceId || item.tempId || item.name || `${type}-${index + 1}`)
        const existing = nodeReferences(item).map((reference) => existingByReference.get(reference.toLowerCase())).find(Boolean)
        if (existing) {
          nodeReferences(item).forEach((reference) => generatedReferenceMap.set(reference.toLowerCase(), String(existing.id)))
          return node({ ...existing, id: String(existing.id), type: existing.type || canonicalType })
        }
        if (!type || !definition || !aiResourceAllowed(item, definition)) {
          rejectedResources.push(item?.resourceKey || item?.resourceId || item?.type || item?.deviceType || item?.name || '未知设备')
          return null
        }
        const actualId = requestedId || `${type}-${index + 1}`
        const properties = item.properties && typeof item.properties === 'object' ? item.properties : {}
        const created = node({
          id: actualId,
          resourceId: item.resourceId ?? item.resourceKey ?? definition.resourceId ?? canonicalType,
          type: canonicalType,
          name: item.name || `${definition.label}${String(index + 1).padStart(2, '0')}`,
          x: snap(Number(item.x ?? properties.x ?? 240 + (index % 4) * 180)),
          y: snap(Number(item.y ?? properties.y ?? 180 + Math.floor(index / 4) * 150)),
          ip: item.ip || properties.ip || '',
          area: item.area || properties.area || '未分配区域',
          vlan: item.vlan || properties.vlan || '',
          model: item.model || properties.model || '',
          notes: item.notes || properties.notes || '',
          properties
        })
        nodeReferences(item).forEach((reference) => generatedReferenceMap.set(reference.toLowerCase(), String(created.id)))
        generatedReferenceMap.set(String(created.id).toLowerCase(), String(created.id))
        return created
      }).filter(Boolean)
      if (!generatedNodes.length) return { error: 'AI 规划引用了当前设备库中不可用的设备，因此本次规划没有应用。', rejected: [...new Set(rejectedResources)] }
      const ids = new Set(generatedNodes.map((item) => item.id))
      const generatedById = new Map(generatedNodes.map((item) => [item.id, item]))
      const generatedUsage = new Map(generatedNodes.map((item) => [item.id, new Set(
        edges.value.flatMap((link) => [
          String(link.source) === String(item.id) ? link.sourcePort : '',
          String(link.target) === String(item.id) ? link.targetPort : ''
        ]).filter(Boolean)
      )]))
      const generatedPort = (nodeId, requested) => {
        const target = generatedById.get(nodeId)
        const ports = normalizePorts(target?.ports)
        const used = generatedUsage.get(nodeId)
        if (!target || !used) return ''
        const preferred = requested && ports.includes(requested) && !used.has(requested) ? requested : ports.find((port) => !used.has(port))
        if (preferred) used.add(preferred)
        return preferred || ''
      }
      const generatedEdges = topologyEdges.map((item, index) => {
        const sourceReference = topologyReference(item.source || item.from || item.sourceDevice)
        const targetReference = topologyReference(item.target || item.to || item.targetDevice)
        const sourceId = generatedReferenceMap.get(sourceReference.toLowerCase()) || sourceReference
        const targetId = generatedReferenceMap.get(targetReference.toLowerCase()) || targetReference
        const sourcePort = generatedPort(sourceId, item.sourcePort)
        const targetPort = generatedPort(targetId, item.targetPort)
        // Multiple physical links between the same devices are valid (for
        // example Core-A/Core-B LAG). Only reject the exact same port pair;
        // a different source/target port must remain available for planning.
        const duplicate = sourcePort && targetPort && edges.value.some((link) => (
          (String(link.source) === String(sourceId) && String(link.target) === String(targetId)
            && String(link.sourcePort || '') === String(sourcePort) && String(link.targetPort || '') === String(targetPort))
          || (String(link.source) === String(targetId) && String(link.target) === String(sourceId)
            && String(link.sourcePort || '') === String(targetPort) && String(link.targetPort || '') === String(sourcePort))
        ))
        if (duplicate) return null
        const aggregation = item.aggregationId || item.aggregationGroup || item.lagId || item.portChannel || item.portChannelId
          || (item.aggregation && typeof item.aggregation === 'object' ? (item.aggregation.id || item.aggregation.group || item.aggregation.name) : '')
        return ids.has(sourceId) && ids.has(targetId) && sourcePort && targetPort ? edge({
          id: String(item.id || `edge-${index + 1}`),
          source: sourceId,
          target: targetId,
          sourcePort,
          targetPort,
          connectionType: item.connectionType || item.linkType || '千兆以太网',
          aggregationId: item.aggregationId || aggregation || '',
          aggregationGroup: item.aggregationGroup || aggregation || '',
          notes: item.notes || ''
        }) : null
      }).filter(Boolean)
      const existingIds = new Set(nodes.value.map((item) => String(item.id)))
      const existingLinks = new Set(edges.value.map((item) => `${item.source}|${item.target}|${item.sourcePort || ''}|${item.targetPort || ''}`))
      const addNodes = generatedNodes.filter((item) => !existingIds.has(String(item.id)))
      const addEdges = generatedEdges.filter((item) => {
        const direct = `${item.source}|${item.target}|${item.sourcePort || ''}|${item.targetPort || ''}`
        const reverse = `${item.target}|${item.source}|${item.targetPort || ''}|${item.sourcePort || ''}`
        return !existingLinks.has(direct) && !existingLinks.has(reverse)
      })
      return { plan: { addNodes, addEdges, removeNodes: [] }, nodes: generatedNodes, edges: generatedEdges }
    } catch {
      // AI responses may include explanatory text around the JSON contract.
    }
  }
  return { error: source ? '模型未返回可应用的拓扑结构，请重试或换一种描述。' : 'AI 未返回内容，请重试。' }
}
async function applyGeneratedTopology(content, targetMessageId = '') {
  const parsed = parseGeneratedTopology(content)
  if (!parsed?.plan || !props.projectId) {
    if (parsed?.error) {
      showTopologyPlanDialog('设备资源不可用', parsed.error, parsed.rejected?.length ? [`未匹配资源：${parsed.rejected.join('、')}`, '请在管理端启用对应资源后重新生成规划。'] : [])
    }
    return false
  }
  const hasChanges = ['addNodes', 'addEdges', 'removeNodes', 'removeEdges', 'updateNodes', 'updateEdges'].some((key) => Array.isArray(parsed.plan[key]) && parsed.plan[key].length)
  if (!hasChanges) return false
  const change = { id: `plan-${Date.now()}`, plan: parsed.plan, summary: 'AI 已完成拓扑规划，请确认后应用。' }
  pendingChange.value = change
  const operation = previewOperation(parsed.plan)
  if (targetMessageId) {
    assistantMessages.value = assistantMessages.value.map((message) => message.id === targetMessageId
      ? { ...message, content: parsed.summary || 'AI 已完成拓扑规划，请确认是否应用。', status: 'complete', ephemeral: false, operation, change: { ...change, messageId: targetMessageId } }
      : message)
    change.messageId = targetMessageId
    scrollAssistantToEnd()
  } else {
    const created = addAssistantMessage({ content: parsed.summary || 'AI 已完成拓扑规划，请确认是否应用。', operation, change })
    change.messageId = created.id
  }
  pendingChange.value = change
  persistPendingChange(change.messageId, change)
  assistantMessages.value = assistantMessages.value.map((message) => message.change?.id === change.id ? { ...message, change } : message)
  return true
}
function previewOperation(plan) {
  const nodesAdded = Array.isArray(plan?.addNodes) ? plan.addNodes : []
  const edgesAdded = Array.isArray(plan?.addEdges) ? plan.addEdges : []
  const edgesUpdated = Array.isArray(plan?.updateEdges) ? plan.updateEdges : []
  const edgesRemoved = Array.isArray(plan?.removeEdges) ? plan.removeEdges : []
  const addedByRef = new Map(nodesAdded.flatMap((item) => {
    const label = item?.name || item?.label || item?.resourceKey || item?.type || '新增设备'
    return [item?.id, label, item?.tempId, label].filter(Boolean).map((key) => [String(key), label])
  }))
  const nodeLabel = (value) => {
    const reference = typeof value === 'object' ? (value?.id || value?.tempId || value?.name || value?.label || '') : value
    const existing = nodes.value.find((item) => String(item.id) === String(reference)
      || String(item.name).toLowerCase() === String(reference).toLowerCase()
      || String(item.resourceId || '').toLowerCase() === String(reference).toLowerCase()
      || String(item.resourceKey || '').toLowerCase() === String(reference).toLowerCase())
    return existing?.name || addedByRef.get(String(reference)) || String(reference || '设备')
  }
  const edgeLabel = (item) => `+ ${nodeLabel(item?.source || item?.from || item?.sourceDevice)} → ${nodeLabel(item?.target || item?.to || item?.targetDevice)}`
  return {
    status: 'preview',
    title: '自动规划方案',
    lines: [
      plan?.summary || '已生成当前拓扑的增量规划',
      `新增设备：${nodesAdded.length}`,
      `新增连线：${edgesAdded.length}`,
      ...(edgesUpdated.length ? [`更新连线：${edgesUpdated.length}`] : []),
      ...(edgesRemoved.length ? [`删除连线：${edgesRemoved.length}`] : []),
      ...(nodesAdded.slice(0, 4).map((item) => `+ ${nodeLabel(item)}`)),
      ...(edgesAdded.slice(0, 4).map(edgeLabel)),
      ...(edgesUpdated.slice(0, 4).map((item) => `↻ ${item?.id || item?.edgeId || '真实连接'}${item?.aggregationGroup ? ` → 聚合组 ${item.aggregationGroup}` : ''}`)),
      ...(edgesRemoved.slice(0, 4).map((item) => `− ${item?.id || item?.edgeId || '真实连接'}`)),
      ...(nodesAdded.length > 4 || edgesAdded.length > 4 ? ['其余变更可在应用后查看。'] : [])
    ]
  }
}
async function applyAssistantChange(change) {
  if (!change?.plan || !props.projectId) return
  assistantError.value = ''
  try {
    const previousNodes = [...nodes.value]
    const previousEdges = [...edges.value]
    const result = await applyTopologyPlan(props.projectId, change.plan, change.id)
    const payload = result?.data || result || {}
    if (Array.isArray(payload.nodes)) nodes.value = payload.nodes.map((item) => node({ ...item, type: item.type?.replaceAll('_', '-') }))
    if (Array.isArray(payload.edges)) {
      const incomingEdges = payload.edges.map(edge)
      const hasExplicitEdgeRemoval = Array.isArray(change.plan.removeEdges) && change.plan.removeEdges.length > 0
      const hasExistingEdgeReferences = incomingEdges.some((link) => byId(link.source) && byId(link.target))
      // Never let an incomplete/empty response blank a working canvas. A
      // deliberate removeEdges plan is the only case where an empty result is
      // allowed to clear all connections.
      const allIncomingEdgesInvalid = incomingEdges.length > 0 && !hasExistingEdgeReferences
      if (!allIncomingEdgesInvalid && (incomingEdges.length || !previousEdges.length || hasExplicitEdgeRemoval || hasExistingEdgeReferences)) edges.value = incomingEdges
      else edges.value = previousEdges
    }
    if (!nodes.value.length && previousNodes.length) nodes.value = previousNodes
    resolvePendingChange(change)
    pendingChange.value = null
    assistantMessages.value = assistantMessages.value.map((message) => message.change?.id === change.id
      ? { ...message, operation: { ...message.operation, status: 'done', title: '拓扑已应用', lines: ['新增设备和链路已保存到当前项目', '画布已自动布局并完成重新检查'] } }
      : message)
    await layout()
    record()
    await nextTick()
    await save(false)
    await runTopologyCheck({ open: true })
  } catch (error) {
    const failure = topologyPlanFailure(error)
    showTopologyPlanDialog(failure.title, failure.message, failure.details)
  }
}
function cancelAssistantChange(change) {
  if (!change || pendingChange.value?.id !== change.id) return
  resolvePendingChange(change)
  pendingChange.value = null
  assistantMessages.value = assistantMessages.value.map((message) => message.change?.id === change.id
    ? { ...message, operation: { ...message.operation, status: 'cancelled', title: '已取消拓扑修改', lines: ['当前画布未发生变化'] } }
    : message)
}
async function loadAssistantSession() {
  assistantConnectionStatus.value = 'reading'
  assistantGenerationState.value = 'IDLE'
  assistantSending.value = false
  assistantSendLock.value = false
  assistantNewConversationRequested.value = false
  assistantError.value = ''
  if (!props.projectId) { assistantConnectionStatus.value = 'unavailable'; assistantMessages.value = [welcomeMessage()]; return }
  await loadAssistantProjectFiles()
  try {
    const [sessions, models] = await Promise.all([
      retryAssistantRead(() => listProjectChatSessions(props.projectId)),
      retryAssistantRead(() => listChatModels())
    ])
    const current = sessions?.items?.[0] || sessions?.[0]
    assistantConversationId.value = current?.id || null
    assistantConversationPublicId.value = current?.publicId || null
    if (assistantConversationId.value) {
      const messages = await retryAssistantRead(() => getProjectChatMessages(props.projectId, assistantConversationPublicId.value || assistantConversationId.value))
      const items = messages?.items || messages || []
      assistantMessages.value = items.length ? items.map(normalizeAssistantMessage) : [welcomeMessage()]
      const loadedMessages = assistantMessages.value.filter((message) => ['user', 'assistant'].includes(message.role))
      if (loadedMessages.at(-1)?.role === 'user') {
        assistantMessages.value = [...assistantMessages.value, { id: `background-${Date.now()}`, role: 'assistant', content: '', status: 'background', ephemeral: true }]
      }
    } else {
      assistantMessages.value = [welcomeMessage()]
    }
    restorePendingChange()
    assistantConnectionStatus.value = Array.isArray(models) && models.length ? 'connected' : 'unavailable'
    scrollAssistantToEnd()
    scheduleAssistantBackgroundPolling()
  } catch (error) {
    assistantConnectionStatus.value = error?.code === 'MODEL_UNAVAILABLE' ? 'unavailable' : 'error'
    assistantError.value = error?.message || 'AI 服务暂不可用'
    assistantMessages.value = [welcomeMessage()]
  }
}
async function newAssistantConversation() {
  if (!props.projectId || assistantSending.value || assistantSendLock.value) return
  assistantAbortController.value?.abort()
  clearAssistantStreamState()
  stopAssistantBackgroundPolling()
  if (pendingChange.value) resolvePendingChange(pendingChange.value)
  else clearPendingChangeStorage()
  pendingChange.value = null
  assistantError.value = ''
  // Do not persist an empty project conversation. The first real user message
  // creates and binds the conversation in sendAssistantMessage.
  assistantConversationId.value = null
  assistantConversationPublicId.value = null
  assistantNewConversationRequested.value = true
  assistantGenerationState.value = 'IDLE'
  assistantMessages.value = [welcomeMessage()]
  assistantConnectionStatus.value = 'connected'
  scrollAssistantToEnd()
}
async function sendAssistantMessage(taskTypeOverride = '', displayText = '') {
  const text = assistantDraft.value.trim()
  if (!text || assistantSending.value || assistantSendLock.value || !props.projectId) return
  assistantSendLock.value = true
  const requestedTaskType = typeof taskTypeOverride === 'string' ? taskTypeOverride : ''
  if (!assistantConversationId.value) {
    try {
      const created = assistantNewConversationRequested.value
        ? await createChatSession(props.projectId, { title: '网络拓扑助手' })
        : await ensureProjectChatSession(props.projectId)
      assistantConversationId.value = created?.id || null
      assistantConversationPublicId.value = created?.publicId || null
      assistantNewConversationRequested.value = false
    } catch (error) {
      assistantError.value = error?.message || 'AI 会话创建失败，请重试'
      assistantConnectionStatus.value = 'error'
      assistantSendLock.value = false
      return
    }
  }
  if (!assistantConversationId.value) { assistantSendLock.value = false; return }
  clearAssistantStreamState()
        assistantDraft.value = ''
  assistantError.value = ''
  assistantMessages.value = [...assistantMessages.value.filter((item) => !item.ephemeral), { id: `user-${Date.now()}`, role: 'user', content: displayText || text, requestContent: text, status: 'complete' }, { id: `assistant-${Date.now()}`, role: 'assistant', content: '', status: 'waiting', ephemeral: true }]
  const assistantIndex = assistantMessages.value.length - 1
  const clientMessageId = `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const streamState = { index: assistantIndex, queue: [], timerId: 0, done: false, eof: false, draining: false, cancelled: false, error: false, interrupted: false, suppressContent: false, generationId: null, followTail: assistantIsNearBottom() }
  assistantStreamState = streamState
  assistantSending.value = true
  assistantGenerationState.value = 'THINKING'
  assistantConnectionStatus.value = 'connected'
  let completedContent = ''
  assistantAbortController.value = new AbortController()
   scrollAssistantToEnd({ force: true })
  const topologyMutation = /(?:添加|新增|增加|加上|加一个|加一台|删除|移除|去掉|移动|连接|接入|挂载|替换|修改).{0,40}(?:交换机|路由器|防火墙|服务器|数据库|NAS|AP|无线|节点|设备|核心|财务|研发)/i.test(text)
    || /(?:交换机|路由器|防火墙|服务器|数据库|NAS|AP|无线|节点|设备).{0,24}(?:添加|新增|增加|加|删除|移除|移动|连接|接入|挂载|替换|修改)/i.test(text)
  const taskType = requestedTaskType || (topologyMutation || /(?:规划|设计).{0,30}网络|规划完整|完善当前|自动规划|需求生成|增加(?:双核心|财务VLAN|VLAN|设备|防火墙|服务器|无线)|新增|补全拓扑|生成拓扑|公司网络|园区网络|办公楼网络|无线覆盖|内外网隔离|高可用/.test(text) ? 'TOPOLOGY_PLANNING' : /风险|故障/.test(text) ? 'RISK_ANALYSIS' : /检查|问题|合理吗/.test(text) ? 'TOPOLOGY_CHECK' : /优化|调整|整理/.test(text) ? 'TOPOLOGY_OPTIMIZATION' : /为什么|为何|作用|IP|NAT|VRRP|OSPF|VLAN/.test(text) ? 'DESIGN_EXPLANATION' : 'TOPOLOGY_QA')
   streamState.suppressContent = ['TOPOLOGY_GENERATION', 'TOPOLOGY_PLANNING', 'TOPOLOGY_OPTIMIZATION'].includes(taskType)
  try {
    const streamOptions = {
      projectId: props.projectId,
      conversationId: assistantConversationId.value,
      conversationPublicId: assistantConversationPublicId.value,
      message: text,
      selectedNodeId: selectedNode.value?.id || null,
      taskType,
      fileIds: assistantAttachment.value?.id ? [assistantAttachment.value.id] : [],
      webSearch: assistantWebSearch.value,
      clientMessageId,
      signal: assistantAbortController.value.signal,
      onEvent: ({ event, data }) => {
        if (streamState.cancelled || assistantStreamState !== streamState) return
        if (data?.generationId && streamState.generationId && String(data.generationId) !== String(streamState.generationId)) return
        if (event === 'message' && data?.generationId) streamState.generationId = String(data.generationId)
        if (event === 'message' && data?.conversation?.id) assistantConversationId.value = data.conversation.id
        if (event === 'message' && data?.conversation?.publicId) assistantConversationPublicId.value = data.conversation.publicId
        if ((event === 'delta' || event === 'token') && typeof data?.content === 'string') {
          const seq = Number(data?.seq)
          if (Number.isFinite(seq) && seq <= (streamState.lastSeq || 0)) return
          if (Number.isFinite(seq)) streamState.lastSeq = seq
          if (data.content && !streamState.suppressContent) {
            const next = [...assistantMessages.value]
            if (next[assistantIndex]) next[assistantIndex] = { ...next[assistantIndex], status: 'streaming' }
            assistantMessages.value = next
            assistantGenerationState.value = 'STREAMING'
            enqueueAssistantText(streamState, data.content)
          }
        }
        if (event === 'eof') {
          if (!streamState.done) {
            streamState.eof = true
            streamState.done = true
            streamState.draining = true
            assistantAbortController.value = null
            const next = [...assistantMessages.value]
            if (next[assistantIndex]) next[assistantIndex] = { ...next[assistantIndex], status: 'draining' }
            assistantMessages.value = next
            assistantGenerationState.value = 'DRAINING'
            pumpAssistantStream(streamState)
          }
          return
        }
        if (event === 'done') {
          const finalMessage = data?.assistantMessage
          const finalContent = typeof finalMessage?.content === 'string' ? finalMessage.content : ''
          const current = assistantMessages.value[assistantIndex]?.content || ''
          const queued = streamState.queue.join('')
          const assembled = current + queued
          completedContent = finalContent || assembled
          if (!streamState.suppressContent) {
             if (finalContent && !assembled) enqueueAssistantText(streamState, finalContent)
             else if (finalContent.startsWith(assembled)) enqueueAssistantText(streamState, finalContent.slice(assembled.length))
           }
           streamState.done = true
           streamState.draining = true
           assistantGenerationState.value = 'DRAINING'
          const draining = [...assistantMessages.value]
          if (draining[assistantIndex]) draining[assistantIndex] = { ...draining[assistantIndex], status: 'draining' }
          assistantMessages.value = draining
          // Provider generation is complete; only the local character queue remains.
           assistantAbortController.value = null
          if (finalMessage) {
            const next = [...assistantMessages.value]
            next[assistantIndex] = { ...next[assistantIndex], id: `chat-${finalMessage.id || next[assistantIndex].id}`, createdAt: finalMessage.createdAt }
            assistantMessages.value = next
          }
          if (data?.conversation?.id) assistantConversationId.value = data.conversation.id
          if (data?.conversation?.publicId) assistantConversationPublicId.value = data.conversation.publicId
          pumpAssistantStream(streamState)
        }
        if (event === 'error') {
          const error = new Error(data?.message || 'AI 服务暂不可用')
          error.code = data?.code || 'PROVIDER_ERROR'
          error.status = data?.status || 0
          error.details = data?.details || {}
          throw error
        }
      }
    }
    const planningStream = ['TOPOLOGY_GENERATION', 'TOPOLOGY_PLANNING'].includes(taskType)
    if (planningStream) {
      await streamProjectTopologyPlan({
        projectId: props.projectId,
        message: text,
        conversationPublicId: assistantConversationPublicId.value,
        selectedNodeId: selectedNode.value?.id || null,
        fileIds: assistantAttachment.value?.id ? [assistantAttachment.value.id] : [],
        webSearch: assistantWebSearch.value,
        clientMessageId,
        signal: assistantAbortController.value.signal,
        onEvent: streamOptions.onEvent
      })
    } else {
      await streamProjectChat(streamOptions)
    }
    if (!streamState.interrupted && ['TOPOLOGY_GENERATION', 'TOPOLOGY_PLANNING', 'TOPOLOGY_OPTIMIZATION'].includes(taskType)) {
       const structured = parseGeneratedTopology(completedContent)
       if (!structured?.plan) {
         const reason = structured?.error || '模型未返回可应用的拓扑结构，请重试或换一种描述。'
         // Validation failures are shown in the modal below instead of being
         // inserted into the chat transcript as a noisy inline error.
         assistantMessages.value = assistantMessages.value.filter((_, index) => index !== assistantIndex)
         showTopologyPlanDialog('无法生成可应用的拓扑', reason, structured?.rejected?.length ? [`未匹配资源：${structured.rejected.join('、')}`, 'AI 仅能使用当前设备库中已启用且允许 AI 使用的资源。'] : [])
       } else {
         if (streamState.suppressContent) {
           const next = [...assistantMessages.value]
           if (next[assistantIndex]) next[assistantIndex] = { ...next[assistantIndex], content: structured.summary || '已生成拓扑规划预览，请查看下方方案后应用。', status: 'complete' }
           assistantMessages.value = next
         }
          try { await applyGeneratedTopology(completedContent, assistantMessages.value[assistantIndex]?.id || '') } catch (error) {
           const failure = topologyPlanFailure(error, '生成的拓扑保存失败，当前画布没有被修改。')
           showTopologyPlanDialog(failure.title, failure.message, failure.details)
         }
       }
     }
    assistantConnectionStatus.value = 'connected'
  } catch (error) {
    streamState.cancelled = true
    streamState.error = error?.name !== 'AbortError'
    streamState.interrupted = error?.name === 'AbortError'
    assistantGenerationState.value = streamState.interrupted ? 'INTERRUPTED' : 'FAILED'
    if (streamState.timerId) window.clearTimeout(streamState.timerId)
    streamState.queue.length = 0
    const errorMessage = error?.name === 'AbortError' ? '已停止生成。' : (error?.message || 'AI 服务暂不可用')
    const next = [...assistantMessages.value]
    next[assistantIndex] = { ...next[assistantIndex], content: next[assistantIndex]?.content || errorMessage, status: error?.name === 'AbortError' ? 'interrupted' : 'failed', ephemeral: false }
    assistantMessages.value = next
    if (assistantStreamState === streamState) assistantStreamState = null
    assistantError.value = error?.name === 'AbortError' ? '' : errorMessage
    assistantConnectionStatus.value = error?.name === 'AbortError' ? 'connected' : assistantFailureState(error)
  } finally {
     if (!streamState.draining) {
       assistantSending.value = false
       assistantSendLock.value = false
     }
    assistantAbortController.value = null
    scrollAssistantToEnd()
  }
}function stopAssistantGeneration() {
  if (!assistantSending.value) return
  assistantGenerationState.value = 'INTERRUPTED'
  assistantAbortController.value?.abort()
}
function useAssistantSuggestion(suggestion) { assistantDraft.value = suggestion; sendAssistantMessage() }
async function copyAssistantMessage(message) { if (!message?.content) return; try { await navigator.clipboard.writeText(message.content) } catch { assistantError.value = '复制失败，请手动选择文本。' } }
function retryAssistantMessage(message) { const index = assistantMessages.value.findIndex((item) => item.id === message?.id); const previous = index > 0 ? [...assistantMessages.value].slice(0, index).reverse().find((item) => item.role === 'user') : null; if (previous) { assistantDraft.value = previous.requestContent || previous.content; sendAssistantMessage() } }
function focus(item) {
  const nodeIds = item?.nodeIds?.length ? item.nodeIds : item?.affectedNodeIds || []
  const edgeIds = item?.edgeIds?.length ? item.edgeIds : item?.affectedEdgeIds || []
  const targets = nodes.value.filter((node) => nodeIds.includes(node.id))
  if (targets.length) {
    selectedIds.value = new Set(targets.map((node) => node.id))
    selectedEdgeId.value = ''
    const minX = Math.min(...targets.map((node) => node.x)), maxX = Math.max(...targets.map((node) => node.x + node.width))
    const minY = Math.min(...targets.map((node) => node.y)), maxY = Math.max(...targets.map((node) => node.y + node.height))
    const rect = canvas.value?.getBoundingClientRect()
    if (rect) updateViewport({ zoom: clampZoom(Math.min((rect.width - 160) / Math.max(80, maxX - minX), (rect.height - 160) / Math.max(80, maxY - minY))), pan: { x: rect.width / 2 - ((minX + maxX) / 2) * zoom.value, y: rect.height / 2 - ((minY + maxY) / 2) * zoom.value } })
  } else if (edgeIds.length) {
    selectedEdgeId.value = edgeIds[0]
    selectedIds.value = new Set()
  }
  rightTab.value = 'checks'
}
function apply(item) {
  const nodeIds = (item?.nodeIds || item?.affectedNodeIds || []).map(String)
  const edgeIds = (item?.edgeIds || item?.affectedEdgeIds || []).map(String)
  const edgeDetails = edgeIds.map((id) => {
    const edge = edges.value.find((link) => String(link.id) === id)
    if (!edge) return `${id}（当前画布未找到）`
    return `${id}: ${edge.source}[${edge.sourcePort || ''}] → ${edge.target}[${edge.targetPort || ''}]`
  })
  // This check is deterministic: the backend already identified the exact
  // core-to-core links. Build a safe preview locally instead of asking the AI
  // to guess edge identifiers again.
  if (item?.ruleCode === 'CORE_PAIR_NO_LAG' && edgeIds.length >= 2) {
    const pair = edgeIds.map((id) => edges.value.find((link) => String(link.id) === id)).find(Boolean)
    const endpoints = pair ? [String(pair.source), String(pair.target)].sort().join('-').replace(/[^a-zA-Z0-9_-]/g, '_') : 'core-pair'
    const aggregationGroup = `CORE-LAG-${endpoints}`
    const plan = {
      type: 'topology_change_plan',
      summary: '为双核心互联链路配置统一链路聚合组。',
      allowDestructive: false,
      updateEdges: edgeIds.map((id) => ({ id, aggregationId: aggregationGroup, aggregationGroup }))
    }
    rightTab.value = 'ai'
    applyGeneratedTopology(JSON.stringify(plan))
    return
  }
  const prompt = `请结合当前项目真实拓扑，针对“${item.title}”生成可预览的拓扑变更计划。当前检查关联的真实设备 ID：${nodeIds.join('、') || '无'}；真实连接 ID：${edgeDetails.join('；') || '无'}。只能引用当前项目中真实存在的设备 ID、设备名称、连接 ID 和端口，禁止虚构 edgeId 或 updateEdges 目标。若需要删除不合理连接，必须把对应真实连接 ID 放入 removeEdges，并设置 allowDestructive=true；若无法定位精确连接则不要生成 updateEdges，改为在 summary/reasons 中说明。保留无关设备，应用前等待用户确认，不要直接修改画布。`
  rightTab.value = 'ai'
  assistantDraft.value = prompt
  sendAssistantMessage('TOPOLOGY_OPTIMIZATION', `请修复拓扑检查项：${item.title}`)
}
function ignore(id) { ignored.value = new Set([...ignored.value, id]) }
function context(kind, event, item) {
  if (kind === 'edge' && item) selectEdge(item)
  else if (item) selectNode(item)
  menu.value = { kind, item, x: event.clientX, y: event.clientY }
}
function layer(top) { const item = selectedNode.value; if (!item) return; nodes.value = nodes.value.filter((node) => node.id !== item.id); top ? nodes.value.push(item) : nodes.value.unshift(item); record(); dirty() }
function exportJson() { const blob = new Blob([JSON.stringify({ devices: nodes.value, connections: edges.value }, null, 2)], { type: 'application/json' }), url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = `${title.value || 'topology'}.json`; link.click(); URL.revokeObjectURL(url) }
function keys(event) {
  if (['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return
  if (event.key === 'Escape' && topologyPlanDialog.value) {
    event.preventDefault()
    closeTopologyPlanDialog()
    return
  }
  if (event.code === 'Space') { event.preventDefault(); spacePressed.value = true; return }
  const modifier = event.ctrlKey || event.metaKey
  if (modifier && event.key === '\\') { event.preventDefault(); toggleRightPanel(); return }
  if (modifier && event.key.toLowerCase() === 'a') { event.preventDefault(); selectedIds.value = new Set(nodes.value.map((item) => item.id)); selectedEdgeId.value = ''; return }
  if (modifier && event.key.toLowerCase() === 'c') { event.preventDefault(); copy() }
  if (modifier && event.key.toLowerCase() === 'v') { event.preventDefault(); paste() }
  if (modifier && event.key.toLowerCase() === 'y') { event.preventDefault(); redo() }
  if (modifier && event.key.toLowerCase() === 'z') { event.preventDefault(); event.shiftKey ? redo() : undo() }
  if (['Delete', 'Backspace'].includes(event.key)) { event.preventDefault(); remove() }
  if (event.key === 'Escape') { connectFrom.value = null; connectPreview.value = null; tool.value = 'select'; menu.value = null; drag.value = null; marquee.value = null; guides.value = { x: null, y: null } }
}
function focusCanvas() { canvas.value?.focus?.({ preventScroll: true }) }
onMounted(async () => {
  try { showNodeNames.value = window.localStorage.getItem(NODE_LABEL_STORAGE_KEY) !== 'false' } catch { /* Use the default when storage is unavailable. */ }
  await load()
  await loadResources()
  await loadHistoricalResources()
  refreshNodeResourceBindings()
  await loadAssistantSession()
  await runTopologyCheck()
  // The AI project brief is persisted with the project. Submit it only when
  // there is no saved project-chat turn yet, so a browser refresh cannot send
  // the same brief twice while the first request is still running.
  const hasPersistedAssistantTurn = assistantMessages.value.some((message) => message.role === 'user')
  const initialGenerationKey = `topology:initial-ai-submitted:${props.projectId || 'new'}`
  const initialGenerationAlreadySubmitted = readRightPanelPreference(initialGenerationKey, 'false') === 'true'
  if (props.initialMode === 'ai' && props.initialDescription.trim() && !nodes.value.length && !hasPersistedAssistantTurn && !initialGenerationAlreadySubmitted) {
    try { window.localStorage.setItem(initialGenerationKey, 'true') } catch { /* The persisted chat turn remains the primary guard. */ }
    assistantDraft.value = props.initialDescription.trim()
    await sendAssistantMessage('TOPOLOGY_GENERATION')
  }
  await nextTick()
  bindAssistantScrollControls()
  protectTopologyImages()
  editorRoot.value?.addEventListener('dragstart', preventImageDragCapture, true)
  if (editorRoot.value && typeof MutationObserver !== 'undefined') {
    topologyImageObserver = new MutationObserver(protectTopologyImages)
    topologyImageObserver.observe(editorRoot.value, { childList: true, subtree: true })
  }
  document.addEventListener('keydown', keys)
  document.addEventListener('keyup', handleKeyup)
  window.addEventListener('blur', handleWindowBlur)
  document.addEventListener('pointerdown', () => { menu.value = null })
  canvas.value?.addEventListener('pointerdown', focusCanvas)
  canvas.value?.addEventListener('keydown', canvasKeys)
  if (canvas.value) canvas.value.tabIndex = 0
  refreshCanvasSize()
  if (canvas.value && typeof ResizeObserver !== 'undefined') {
    canvasResizeObserver = new ResizeObserver(refreshCanvasSize)
    canvasResizeObserver.observe(canvas.value)
  }
  syncRightPanelWidth()
  if (editorBody.value && typeof ResizeObserver !== 'undefined') {
    editorBodyResizeObserver = new ResizeObserver(syncRightPanelWidth)
    editorBodyResizeObserver.observe(editorBody.value)
  }
  await nextTick()
  fit(false)
})
onBeforeUnmount(() => {
  stopAssistantBackgroundPolling()
  clearTimeout(saveTimer)
  assistantAbortController.value?.abort()
  if (wheelState.frame) cancelAnimationFrame(wheelState.frame)
  if (panState.frame) cancelAnimationFrame(panState.frame)
  cancelViewportAnimation()
  cancelLayoutAnimation()
  if (panelResizeFrame) cancelAnimationFrame(panelResizeFrame)
  panelResizeFrame = 0
  canvasResizeObserver?.disconnect()
  canvasResizeObserver = null
  editorBodyResizeObserver?.disconnect()
  editorBodyResizeObserver = null
  releaseProtectedTopologyImages()
  topologyImageObserver?.disconnect()
  topologyImageObserver = null
  editorRoot.value?.removeEventListener('dragstart', preventImageDragCapture, true)
  document.removeEventListener('keydown', keys)
  document.removeEventListener('keyup', handleKeyup)
  window.removeEventListener('blur', handleWindowBlur)
  if (assistantScrollListenerTarget) assistantScrollListenerTarget.removeEventListener('scroll', updateAssistantScrollState)
  assistantScrollListenerTarget = null
  assistantLatestButton?.remove()
  assistantLatestButton = null
  canvas.value?.removeEventListener('pointerdown', focusCanvas)
  canvas.value?.removeEventListener('keydown', canvasKeys)
})
</script>

<template>
  <div ref="editorRoot" class="topology-editor">
    <header class="editor-topbar">
      <div class="editor-project"><button class="editor-icon" type="button" title="返回项目列表" @click="emit('back')"><ArrowLeft :size="18" /></button><div><input v-if="renaming" v-model="title" @blur="renaming = false" @keyup.enter="renaming = false" /><button v-else type="button" class="editor-title" @click="renaming = true"><strong>{{ title }}</strong><Pencil :size="14" /></button><span class="save-state-dot" :class="{ saving: saveState === '正在保存...', failed: saveState.includes('失败') }"><i></i>{{ saveState }}</span></div></div>
      <div class="editor-tools"><button class="editor-icon" type="button" title="撤销" :disabled="history.length < 2" @click="undo"><Undo2 :size="17" /></button><button class="editor-icon" type="button" title="重做" :disabled="!redoStack.length" @click="redo"><Redo2 :size="17" /></button><i></i><button class="editor-icon" :class="{ active: tool === 'select' }" type="button" title="选择" @click="tool = 'select'"><MousePointer2 :size="17" /></button><button class="editor-icon" :class="{ active: tool === 'connect' }" type="button" title="连线" @click="tool = 'connect'"><Network :size="17" /></button><button class="editor-icon" type="button" title="删除" @click="remove"><Trash2 :size="17" /></button><i class="wide"></i><button class="editor-text wide" type="button" @click="layout"><LayoutTemplate :size="16" /> 自动布局</button><button class="editor-icon" type="button" title="适应全部设备" aria-label="适应全部设备" @click="fit"><Maximize2 :size="17" /></button><button class="editor-icon" type="button" :title="showNodeNames ? '隐藏设备名称' : '显示设备名称'" :aria-label="showNodeNames ? '隐藏设备名称' : '显示设备名称'" @click="toggleNodeNames"><EyeOff v-if="showNodeNames" :size="17" /><Eye v-else :size="17" /></button><button class="editor-icon" type="button" title="缩小" @click="adjustZoom(-.1)"><ZoomOut :size="17" /></button><span class="zoom-value">{{ Math.round(zoom * 100) }}%</span><button class="editor-icon" type="button" title="放大" @click="adjustZoom(.1)"><ZoomIn :size="17" /></button></div>
      <div class="editor-actions"><button class="editor-text wide" type="button" @click="runTopologyCheck({ open: true })"><ShieldCheck :size="16" /> 拓扑检查</button><button class="editor-text save wide" type="button" @click="save(false)"><Save :size="16" /> 保存</button><button class="editor-icon" type="button" title="导出" @click="exportJson"><Download :size="17" /></button></div>
    </header>

    <div ref="editorBody" class="editor-body" :class="{ 'right-panel-collapsed': collapsed, 'right-panel-resizing': panelResize }" :style="rightPanelStyle">
      <aside class="device-library"><header><strong>设备库</strong><span v-if="resourcesLoading">加载中...</span><span v-else>{{ resources.length }} 种可用资源</span></header><label class="device-search"><Search :size="16" /><input v-model="search" placeholder="搜索设备" :disabled="resourcesLoading || Boolean(resourcesError)" /></label><div class="library-actions"><button type="button" @click="expandAllGroups">全部展开</button><i>·</i><button type="button" @click="collapseAllGroups">全部收起</button></div><div v-if="resourcesLoading" class="device-library-state skeleton" aria-busy="true"><i v-for="index in 6" :key="index"></i></div><div v-else-if="resourcesError" class="device-library-state error" role="alert"><strong>设备资源加载失败</strong><span>{{ resourcesError }}</span><button type="button" @click="loadResources">重新加载</button></div><div v-else-if="!groups.length" class="device-library-state"><span>{{ search ? '没有匹配的设备资源' : '暂无可用设备资源' }}</span></div><div v-else class="groups"><section v-for="group in groups" :key="group.name"><button class="group-title" type="button" @click="toggleGroup(group.name)"><span>{{ group.name }}</span><ChevronDown :class="{ up: openGroups.has(group.name) }" :size="15" /></button><div v-if="openGroups.has(group.name)" class="device-grid"><button v-for="item in group.items" :key="item.resourceId || item.type" type="button" class="device-card" draggable="true" @dragstart="paletteDrag(item, $event)" @click="add(item)"><span :style="{ color: item.color }"><img v-if="item.asset" :src="item.asset" :alt="`${item.label}图标`" draggable="false" @dragstart.prevent /><component v-else :is="item.icon" :size="19" /></span><small>{{ item.label }}</small></button></div></section></div></aside>

      <main ref="canvas" class="topology-viewport" :class="{ connecting, panning: drag && drag.kind === 'pan', 'space-pan': spacePressed, 'show-node-names': showNodeNames }" :style="viewportStyle" @dragover.prevent @drop.prevent="drop" @wheel="handleWheel" @mousedown.capture="blockMiddleMouse" @pointerdown.capture="blockMiddleMouse" @auxclick.capture="blockMiddleMouse" @pointerdown="canvasStart" @pointermove="move" @pointerup="end" @pointercancel="end" @contextmenu.prevent="context('canvas', $event)"><div class="topology-stage" :style="{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})` }"><svg class="links"><g v-for="item in edges" :key="item.id" :class="{ selected: selectedEdgeId === item.id }"><line class="edge-hit" :x1="edgeEndpoints(item).x1" :y1="edgeEndpoints(item).y1" :x2="edgeEndpoints(item).x2" :y2="edgeEndpoints(item).y2" @click.stop="selectEdge(item)" @contextmenu.stop.prevent="context('edge', $event, item)" /><line class="edge-line" :x1="edgeEndpoints(item).x1" :y1="edgeEndpoints(item).y1" :x2="edgeEndpoints(item).x2" :y2="edgeEndpoints(item).y2" /></g><line v-if="connectPreviewLine" class="edge-preview" :x1="connectPreviewLine.x1" :y1="connectPreviewLine.y1" :x2="connectPreviewLine.x2" :y2="connectPreviewLine.y2" /></svg><article v-for="item in nodes" :key="item.id" class="topology-node" :class="{ selected: selectedIds.has(item.id), source: connectFrom?.id === item.id }" :data-node-name="item.name" :data-node-id="item.id" :title="item.name" :style="pos(item)" @pointerdown="dragNode(item, $event)" @click.stop="handleNodeClick(item, $event)" @contextmenu.stop.prevent="context('node', $event, item)"><div class="node-symbol" :style="{ color: item.color }"><img v-if="catalog[item.type]?.asset" :src="catalog[item.type].asset" :alt="`${item.name}图标`" draggable="false" @dragstart.prevent /><component v-else :is="catalog[item.type]?.icon || Network" :size="56" /></div><div class="node-actions"><button type="button" title="创建连接" @pointerdown.stop="startConnect(item)" @click.stop><Network :size="14" /></button><button type="button" title="复制" @pointerdown.stop="selectNode(item); copy()" @click.stop><Copy :size="14" /></button><button type="button" title="删除" @pointerdown.stop="selectNode(item); remove()" @click.stop><Trash2 :size="14" /></button></div></article><div v-if="guides.x !== null" class="alignment-guide vertical" :style="{ left: `${guides.x}px` }"></div><div v-if="guides.y !== null" class="alignment-guide horizontal" :style="{ top: `${guides.y}px` }"></div></div><div v-if="selectionBounds" class="selection-toolbar" :style="selectionToolbarStyle" @pointerdown.stop><button type="button" title="顶部对齐" @click="alignSelection('y', 'start')"><AlignHorizontalJustifyStart :size="14" /></button><button type="button" title="水平居中" @click="alignSelection('y', 'center')"><AlignHorizontalJustifyCenter :size="14" /></button><button type="button" title="底部对齐" @click="alignSelection('y', 'end')"><AlignHorizontalJustifyEnd :size="14" /></button><i></i><button type="button" title="左对齐" @click="alignSelection('x', 'start')"><AlignVerticalJustifyStart :size="14" /></button><button type="button" title="垂直居中" @click="alignSelection('x', 'center')"><AlignVerticalJustifyCenter :size="14" /></button><button type="button" title="右对齐" @click="alignSelection('x', 'end')"><AlignVerticalJustifyEnd :size="14" /></button><i></i><button type="button" title="横向等距" :disabled="selectedNodes.length < 3" @click="distributeSelection('x')"><AlignHorizontalSpaceBetween :size="14" /></button><button type="button" title="纵向等距" :disabled="selectedNodes.length < 3" @click="distributeSelection('y')"><AlignVerticalSpaceBetween :size="14" /></button><button type="button" title="自动排列" @click="arrangeSelection"><LayoutTemplate :size="14" /></button></div><div v-if="marquee" class="marquee" :style="{ left: `${marquee.x * zoom + pan.x}px`, top: `${marquee.y * zoom + pan.y}px`, width: `${marquee.width * zoom}px`, height: `${marquee.height * zoom}px` }"></div><div v-if="!nodes.length" class="empty-canvas"><Network :size="28" /><strong>从设备库拖入设备，或直接向 AI 助手描述需求</strong><span>设备、端口和连接会自动保存到当前项目。</span><button type="button" @click="rightTab = 'ai'; collapsed = false"><Sparkles :size="16" /> 打开 AI 助手</button></div><div class="canvas-tip"><GripVertical :size="15" /> 左键框选 · 空格+左键拖动画布 · 滚轮缩放</div></main>
      <div v-if="connectionNotice" class="connection-notice" role="status">{{ connectionNotice }}</div>

      <div class="panel-resizer" :class="{ collapsed, active: panelResize }" role="separator" aria-orientation="vertical" :aria-valuemin="320" :aria-valuemax="700" :aria-valuenow="rightPanelWidth" @pointerdown.stop.prevent="startPanelResize" @pointermove.stop.prevent="movePanelResize" @pointerup.stop.prevent="stopPanelResize" @pointercancel.stop.prevent="stopPanelResize" @dblclick.stop.prevent="resetRightPanelWidth"></div>
      <button class="panel-toggle" :class="{ collapsed }" type="button" :aria-expanded="!collapsed" :title="collapsed ? '展开右侧面板' : '收起右侧面板'" @click="toggleRightPanel"><ChevronLeft v-if="collapsed" :size="17" /><ChevronRight v-else :size="17" /></button>
      <aside class="inspector" :class="{ collapsed }" :aria-hidden="collapsed"><div class="inspector-panel-content"><nav><button :class="{ active: rightTab === 'properties' }" type="button" @click="rightTab = 'properties'">属性</button><button :class="{ active: rightTab === 'ai' }" type="button" @click="rightTab = 'ai'">AI 助手</button><button :class="{ active: rightTab === 'checks' }" type="button" @click="rightTab = 'checks'">拓扑检查<span v-if="visibleChecks.length">{{ visibleChecks.length }}</span></button></nav>
        <section v-if="rightTab === 'properties'" class="inspector-content"><template v-if="selectedNode"><header class="side-title"><div><span>设备属性</span><strong>{{ selectedNode.name }}</strong></div><b>{{ catalog[selectedNode.type]?.label }}</b></header><label>设备名称<input v-model="selectedNode.name" @input="dirty" /></label><label>设备类型<input :value="catalog[selectedNode.type]?.label" disabled /></label><label>端口规格<input :value="catalog[selectedNode.type]?.portProfileName || '公共资源端口模板'" disabled /></label><label>设备型号（可选）<input v-model="selectedNode.model" placeholder="例如：S5735-L24T4X-A" @input="dirty" /></label><label>管理 IP<input v-model="selectedNode.ip" placeholder="192.168.1.1" @input="dirty" /></label><label>所属区域<input v-model="selectedNode.area" @input="dirty" /></label><label>VLAN<input v-model="selectedNode.vlan" @input="dirty" /></label><label>备注<textarea v-model="selectedNode.notes" rows="3" @input="dirty"></textarea></label><div class="port-summary"><span>端口使用情况</span><strong>{{ usedPorts(selectedNode.id).size }} 已用 / {{ selectedNode.ports.length }} 总数</strong><em>剩余 {{ Math.max(0, selectedNode.ports.length - usedPorts(selectedNode.id).size) }}</em></div><div class="ports"><span>端口（点击空闲端口开始连接）</span><button v-for="port in selectedNode.ports" :key="port" type="button" :class="{ occupied: portState(selectedNode, port) === '已占用' }" :disabled="portState(selectedNode, port) === '已占用'" @click="startConnect(selectedNode, port)">{{ port }}<small>{{ portState(selectedNode, port) }}</small></button></div><button class="danger" type="button" @click="remove"><Trash2 :size="15" /> 删除设备</button></template><template v-else-if="selectedEdge"><header class="side-title"><div><span>连接属性</span><strong>{{ byId(selectedEdge.source)?.name }} - {{ byId(selectedEdge.target)?.name }}</strong></div></header><label>源设备<input :value="byId(selectedEdge.source)?.name" disabled /></label><label>源端口<select :value="selectedEdge.sourcePort" @change="changeEdgePort(selectedEdge, 'source', $event.target.value)"><option v-for="port in byId(selectedEdge.source)?.ports || []" :key="port" :value="port">{{ port }}{{ usedPorts(selectedEdge.source).has(port) && port !== selectedEdge.sourcePort ? '（已占用）' : '' }}</option></select></label><label>目标设备<input :value="byId(selectedEdge.target)?.name" disabled /></label><label>目标端口<select :value="selectedEdge.targetPort" @change="changeEdgePort(selectedEdge, 'target', $event.target.value)"><option v-for="port in byId(selectedEdge.target)?.ports || []" :key="port" :value="port">{{ port }}{{ usedPorts(selectedEdge.target).has(port) && port !== selectedEdge.targetPort ? '（已占用）' : '' }}</option></select></label><label>连接类型<select v-model="selectedEdge.connectionType" @change="dirty"><option>千兆以太网</option><option>万兆以太网</option><option>光纤链路</option><option>无线链路</option></select></label><label>备注<textarea v-model="selectedEdge.notes" rows="3" @input="dirty"></textarea></label><button class="danger" type="button" @click="remove"><Trash2 :size="15" /> 删除连接</button></template><template v-else><header class="side-title"><div><span>项目概览</span><strong>{{ title }}</strong></div></header><div class="overview"><article><span>设备数量</span><strong>{{ nodes.length }}</strong></article><article><span>连接数量</span><strong>{{ edges.length }}</strong></article><article><span>网络区域</span><strong>{{ new Set(nodes.map((item) => item.area).filter(Boolean)).size }}</strong></article><article><span>最近保存</span><strong>{{ saveState === '已保存' ? '刚刚' : saveState }}</strong></article></div><p class="tip">选择设备或连接线后，可在此直接编辑详细属性。</p></template></section>
        <section v-else-if="rightTab === 'ai'" class="assistant-panel"><header class="assistant-header"><div><span>AI 助手</span><strong :class="`assistant-status-${assistantConnectionStatus}`"><i></i>{{ assistantConnectionStatus === 'reading' ? '正在读取项目…' : assistantConnectionStatus === 'connected' ? '已连接当前项目' : assistantConnectionStatus === 'unavailable' ? '暂无可用 AI 模型' : assistantConnectionStatus === 'degraded' ? '上次请求失败，可重试' : 'AI 连接暂时异常' }}</strong><p>可回答网络问题，也可以基于当前拓扑继续协助。</p></div><button class="assistant-new-chat" type="button" title="新对话" @click="newAssistantConversation"><Plus :size="15" /> 新对话</button></header><div v-if="assistantError" class="assistant-error"><span>{{ assistantError }}</span><button type="button" :disabled="assistantSending" @click="loadAssistantSession">重新检查</button></div><div ref="assistantScroll" class="assistant-messages"><article v-for="message in assistantMessages" :key="message.id" class="assistant-message" :class="message.role"><div class="assistant-message-copy"><template v-if="message.role === 'assistant' && (assistantDisplayContent(message) || ['waiting', 'streaming', 'background'].includes(message.status))"><AiMessageContent :content="assistantDisplayContent(message)" :status="message.status" /></template><template v-else>{{ message.content || ((message.status === 'waiting' || message.status === 'streaming' || message.status === 'background') ? (message.status === 'background' ? 'AI 正在后台生成，完成后会自动显示。' : '正在回复…') : '') }}</template></div><div v-if="message.suggestions" class="assistant-suggestions"><button v-for="suggestion in message.suggestions" :key="suggestion" type="button" :disabled="assistantSending" @click="useAssistantSuggestion(suggestion)">{{ suggestion }}</button></div><section v-if="message.operation" class="assistant-operation" :class="message.operation.status"><header><Check :size="14" /><strong>{{ message.operation.title }}</strong></header><span v-for="line in message.operation.lines" :key="line">{{ line }}</span><div v-if="message.operation.status === 'preview' && pendingChange?.id === message.change?.id" class="assistant-operation-actions"><button type="button" class="assistant-apply" @click="applyAssistantChange(message.change)">应用修改</button><button type="button" @click="cancelAssistantChange(message.change)">取消</button></div></section></article></div><input ref="assistantAttachmentInput" class="assistant-file-input" type="file" :accept="PROJECT_FILE_ACCEPT" @change="handleAssistantFileSelection"><div v-if="assistantAttachment || assistantAttachmentError || assistantAttachmentUploading" class="assistant-attachment-status"><span v-if="assistantAttachment"><Check :size="14" /> 已读取：{{ assistantAttachment.name }}</span><span v-else-if="assistantAttachmentUploading">正在读取资料…</span><span v-else class="error">{{ assistantAttachmentError }}</span><button v-if="assistantAttachment || assistantAttachmentError" type="button" title="清除资料提示" @click="clearAssistantAttachment">×</button></div><form class="assistant-composer" @submit.prevent="assistantSending ? stopAssistantGeneration() : sendAssistantMessage()"><button class="assistant-attach" type="button" title="上传资料" :disabled="assistantAttachmentUploading" @click="assistantAttachmentInput?.click()"><Plus :size="18" /></button><textarea ref="assistantComposerInput" v-model="assistantDraft" rows="2" placeholder="问我关于当前拓扑的问题，或告诉我需要修改什么……" @input="resizeAssistantDraft" @keydown.enter.exact.prevent="sendAssistantMessage()"></textarea><button class="assistant-web-search" :class="{ active: assistantWebSearch }" type="button" title="联网搜索" :aria-pressed="assistantWebSearch" @click="assistantWebSearch = !assistantWebSearch"><Globe2 :size="16" /></button><button v-if="assistantSending" class="assistant-send stop" type="button" title="停止生成" aria-label="停止生成" @click="stopAssistantGeneration"><span></span></button><button v-else class="assistant-send" type="submit" :disabled="!assistantDraft.trim() || assistantAttachmentUploading" title="发送"><Send :size="17" /></button></form></section>
         <section v-else class="inspector-content"><header class="side-title"><div><span>AI 拓扑检查</span><strong>综合评分 {{ checkSummary.score }} / 100</strong><span>高风险 {{ checkSummary.high }} · 中风险 {{ checkSummary.medium }} · 建议 {{ checkSummary.low }} · 已通过 {{ checkSummary.passed }}</span></div><button class="check-refresh" type="button" :disabled="checksLoading" @click="runTopologyCheck({ open: true })">{{ checksLoading ? '检查中…' : '重新检查' }}</button></header><div v-if="checkError" class="assistant-error">{{ checkError }}</div><div class="check-filters"><button v-for="filter in [{ key: 'all', label: '全部' }, { key: 'high', label: '高风险' }, { key: 'medium', label: '中风险' }, { key: 'low', label: '建议' }, { key: 'passed', label: '已通过' }]" :key="filter.key" type="button" :class="{ active: checkFilter === filter.key }" @click="checkFilter = filter.key">{{ filter.label }} {{ checkCounts[filter.key] || 0 }}</button></div><article v-for="item in visibleChecks" :key="item.id" class="check" :class="item.level"><span>{{ item.tag }}</span><strong>{{ item.title }}</strong><p>{{ item.description || item.detail }}</p><p v-if="item.suggestion">优化建议：{{ item.suggestion }}</p><div><button type="button" @click="focus(item)">定位设备</button><button type="button" @click="rightTab = 'ai'; assistantDraft = '请结合当前项目解释为什么' + item.title + '，以及当前方案为什么会产生这个问题。'; sendAssistantMessage('DESIGN_EXPLANATION')">让 AI 解释</button><button type="button" class="apply" @click="apply(item)">生成优化方案</button><button type="button" @click="ignore(item.id)">忽略</button></div></article><div v-if="!visibleChecks.length && !checksLoading" class="clear-check"><Check :size="21" /><strong>{{ checkFilter === 'passed' ? `当前检查已通过 ${checkSummary.passed || 0} 项` : '当前拓扑未发现待处理建议' }}</strong></div></section></div></aside>
    </div>
    <div v-if="menu" class="context-menu" :style="{ left: `${menu.x}px`, top: `${menu.y}px` }" @pointerdown.stop><template v-if="menu.kind === 'node'"><button type="button" @click="rightTab = 'properties'; menu = null"><Pencil :size="15" /> 编辑属性</button><button type="button" @click="startConnect(menu.item); menu = null"><Network :size="15" /> 创建连接</button><button type="button" @click="copy(); menu = null"><Copy :size="15" /> 复制</button><button type="button" @click="remove(); menu = null"><Trash2 :size="15" /> 删除</button><button type="button" @click="layer(true); menu = null"><ChevronRight :size="15" /> 置于顶层</button><button type="button" @click="layer(false); menu = null"><ChevronLeft :size="15" /> 置于底层</button></template><template v-else-if="menu.kind === 'edge'"><button type="button" @click="rightTab = 'properties'; menu = null"><Pencil :size="15" /> 编辑连接</button><button type="button" @click="removeEdge(menu.item); menu = null"><Trash2 :size="15" /> 删除连接</button></template><template v-else><button type="button" @click="paste(); menu = null"><Clipboard :size="15" /> 粘贴</button><button type="button" @click="selectedIds = new Set(nodes.map((item) => item.id)); menu = null"><Check :size="15" /> 全选</button><button type="button" @click="layout(); menu = null"><LayoutTemplate :size="15" /> 自动布局</button><button type="button" @click="fit(); menu = null"><Expand :size="15" /> 适应画布</button></template></div>
    <Teleport to="body">
      <div v-if="topologyPlanDialog" class="topology-plan-dialog-backdrop" role="presentation" @pointerdown.self="closeTopologyPlanDialog">
        <section class="topology-plan-dialog" role="alertdialog" aria-modal="true" aria-labelledby="topology-plan-dialog-title">
          <header><span class="topology-plan-dialog-icon"><CircleAlert :size="20" /></span><div><h2 id="topology-plan-dialog-title">{{ topologyPlanDialog.title }}</h2><p>AI 拓扑规划未应用，当前画布保持不变。</p></div></header>
          <p class="topology-plan-dialog-message">{{ topologyPlanDialog.message }}</p>
          <ul v-if="topologyPlanDialog.details.length" class="topology-plan-dialog-details"><li v-for="detail in topologyPlanDialog.details" :key="detail">{{ detail }}</li></ul>
          <footer><button type="button" @click="closeTopologyPlanDialog">我知道了</button></footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.topology-editor { display: flex; flex-direction: column; height: 100%; min-height: 0; color: #1e293b; background: #f8fafc; overflow: hidden; }
.topology-plan-dialog-backdrop { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: 20px; background: rgba(15, 23, 42, .25); backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px); }
.topology-plan-dialog { width: min(440px, calc(100vw - 40px)); padding: 22px; border: 1px solid #d8e3ef; border-radius: 10px; background: #fff; box-shadow: 0 20px 52px rgba(15, 23, 42, .24); }
.topology-plan-dialog header { display: flex; align-items: flex-start; gap: 12px; }.topology-plan-dialog-icon { display: grid; flex: 0 0 auto; place-items: center; width: 36px; height: 36px; border-radius: 50%; background: #fff7ed; color: #c2410c; }.topology-plan-dialog h2 { margin: 0; color: #1e293b; font-size: 16px; line-height: 1.35; }.topology-plan-dialog header p { margin: 4px 0 0; color: #7c8ca0; font-size: 12px; line-height: 1.55; }.topology-plan-dialog-message { margin: 18px 0 0; color: #475569; font-size: 13px; line-height: 1.7; }.topology-plan-dialog-details { display: grid; gap: 6px; margin: 14px 0 0; padding: 10px 12px 10px 28px; border: 1px solid #e6edf5; border-radius: 6px; background: #f8fafc; color: #64748b; font-size: 12px; line-height: 1.55; }.topology-plan-dialog footer { display: flex; justify-content: flex-end; margin-top: 20px; }.topology-plan-dialog footer button { height: 34px; padding: 0 13px; border: 1px solid #2563eb; border-radius: 5px; background: #2563eb; color: #fff; font-size: 12px; font-weight: 700; }.topology-plan-dialog footer button:hover { background: #1d4ed8; }
.editor-topbar { display: grid; grid-template-columns: minmax(220px, 1fr) auto minmax(270px, 1fr); align-items: center; gap: 14px; min-height: 52px; padding: 8px 14px; border-bottom: 1px solid #dce5ef; background: #fff; }
.editor-project, .editor-tools, .editor-actions { display: flex; align-items: center; gap: 5px; min-width: 0; }
.editor-project > div { display: grid; min-width: 0; gap: 2px; }.editor-actions { justify-content: flex-end; }.editor-project input { width: min(290px, 100%); height: 28px; padding: 0 6px; border: 1px solid #93c5fd; border-radius: 4px; color: #0f172a; font-size: 14px; font-weight: 700; outline: 0; }
.editor-title { display: inline-flex; align-items: center; min-width: 0; gap: 6px; padding: 0; border: 0; background: transparent; color: #162033; text-align: left; }.editor-title strong { max-width: 290px; overflow: hidden; font-size: 14px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }.editor-title svg { color: #94a3b8; }
.save-state-dot { display: inline-flex; align-items: center; gap: 5px; color: #047857; font-size: 11px; font-weight: 650; white-space: nowrap; }.save-state-dot i { width: 6px; height: 6px; border-radius: 50%; background: #10b981; }.save-state-dot.saving { color: #2563eb; }.save-state-dot.saving i { background: #3b82f6; animation: pulse 1s ease infinite; }.save-state-dot.failed { color: #dc2626; }.save-state-dot.failed i { background: #dc2626; }
.editor-icon, .editor-text { display: inline-flex; align-items: center; justify-content: center; min-width: 32px; height: 32px; gap: 6px; padding: 0 8px; border: 1px solid transparent; border-radius: 5px; background: transparent; color: #526174; font-size: 12px; font-weight: 650; }.editor-icon:hover, .editor-text:hover, .editor-icon.active { border-color: #d7e1ee; background: #f3f7fc; color: #2563eb; }.editor-icon:disabled { opacity: .38; cursor: default; }.editor-tools > i { width: 1px; height: 18px; margin: 0 3px; background: #e2e8f0; }.zoom-value { min-width: 40px; color: #526174; font-size: 12px; font-weight: 700; text-align: center; }.editor-text.save { border-color: #2563eb; background: #2563eb; color: #fff; }.editor-text.save:hover { background: #1d4ed8; color: #fff; }
.editor-body { --right-panel-size: 360px; position: relative; display: grid; grid-template-columns: 220px minmax(0, 1fr) var(--right-panel-size); flex: 1; min-height: 0; transition: grid-template-columns .22s ease; }.editor-body.right-panel-collapsed { grid-template-columns: 220px minmax(0, 1fr) 0; }.editor-body.right-panel-resizing { cursor: col-resize; transition: none; user-select: none; }.device-library { display: flex; flex-direction: column; min-width: 0; border-right: 1px solid #dce5ef; background: #fff; }.device-library > header { display: flex; align-items: baseline; justify-content: space-between; padding: 16px 14px 11px; }.device-library > header strong { color: #162033; font-size: 14px; }.device-library > header span { color: #94a3b8; font-size: 11px; }.device-search { display: flex; align-items: center; gap: 7px; height: 34px; margin: 0 12px 10px; padding: 0 9px; border: 1px solid #dce5ef; border-radius: 5px; color: #94a3b8; background: #fff; }.device-search input { min-width: 0; width: 100%; border: 0; outline: 0; color: #334155; font-size: 12px; }.groups { flex: 1; overflow-y: auto; padding: 0 8px 14px; }.group-title { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 32px; padding: 0 6px; border: 0; background: transparent; color: #64748b; font-size: 12px; font-weight: 700; text-align: left; }.group-title svg { transition: transform .18s ease; }.group-title svg.up { transform: rotate(180deg); }.device-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; padding: 0 4px 10px; }.device-card { display: grid; justify-items: start; gap: 6px; min-height: 62px; padding: 8px; border: 1px solid #e2e8f0; border-radius: 5px; background: #fff; color: #526174; text-align: left; }.device-card:hover { border-color: #93c5fd; background: #f8fbff; }.device-card > span { display: grid; place-items: center; width: 21px; height: 21px; }.device-card img { width: 20px; height: 20px; object-fit: contain; }.device-card small { max-width: 84px; overflow: hidden; color: #475569; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.topology-viewport { position: relative; min-width: 0; overflow: hidden; background-color: #f9fbfd; background-image: radial-gradient(#cbd5e1 1px, transparent 1px); background-size: 20px 20px; cursor: default; }.topology-viewport.connecting { cursor: crosshair; }.topology-stage { position: absolute; width: 100%; height: 100%; min-width: 1600px; min-height: 1000px; transform-origin: 0 0; }.links { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }.edge-line { fill: none; stroke: #94a3b8; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; pointer-events: none; }.edge-hit { fill: none; stroke: transparent; stroke-width: 16; cursor: pointer; }.links g.selected .edge-line { stroke: #2563eb; stroke-width: 2.5; }
.topology-node { position: absolute; display: grid; box-sizing: border-box; place-items: center; padding: 0; border: 1px solid #d6e0eb; border-radius: 10px; background: #fff; box-shadow: 0 3px 10px rgba(15, 23, 42, .1); cursor: grab; user-select: none; transition: border-color .16s ease, box-shadow .16s ease, background .16s ease; }.topology-node:hover { border-color: #9fc2ed; background: #fbfdff; box-shadow: 0 6px 16px rgba(15, 23, 42, .13); }.topology-node.selected { border-color: #3b82f6; background: #fafdff; box-shadow: 0 0 0 3px rgba(59, 130, 246, .16), 0 8px 18px rgba(37, 99, 235, .16); }.topology-node.source { border-color: #8b5cf6; box-shadow: 0 0 0 3px rgba(139, 92, 246, .14), 0 6px 16px rgba(76, 29, 149, .13); }.topology-node::after { position: absolute; top: calc(100% + 7px); left: 50%; z-index: 8; display: none; width: 144px; overflow: hidden; color: #334155; content: attr(data-node-name); font-size: 12px; font-weight: 650; line-height: 18px; pointer-events: none; text-align: center; text-overflow: ellipsis; text-shadow: 0 1px 2px rgba(255, 255, 255, .95); white-space: nowrap; transform: translateX(-50%); }.topology-viewport.show-node-names .topology-node::after { display: block; }.topology-node::before { content: none; }.node-symbol { display: grid; place-items: center; width: 56px; height: 56px; }.node-symbol img, .node-symbol svg { width: 56px; height: 56px; object-fit: contain; }.node-actions { position: absolute; right: 50%; bottom: -31px; z-index: 9; display: none; height: 27px; overflow: hidden; border: 1px solid #d5deea; border-radius: 5px; background: #fff; box-shadow: 0 5px 12px rgba(15, 23, 42, .12); transform: translateX(50%); }.topology-viewport.show-node-names .node-actions { bottom: -57px; }.topology-node:hover .node-actions, .topology-node.selected .node-actions { display: flex; }.node-actions button { display: grid; place-items: center; width: 27px; padding: 0; border: 0; border-right: 1px solid #e2e8f0; background: #fff; color: #64748b; }.node-actions button:last-child { border-right: 0; }.node-actions button:hover { background: #f8fbff; color: #2563eb; }.marquee { position: absolute; border: 1px solid #3b82f6; background: rgba(59, 130, 246, .08); pointer-events: none; }.empty-canvas { position: absolute; top: 50%; left: 50%; display: grid; justify-items: center; gap: 9px; width: min(380px, calc(100% - 40px)); transform: translate(-50%, -50%); color: #64748b; text-align: center; }.empty-canvas strong { color: #334155; font-size: 14px; }.empty-canvas span { font-size: 12px; }.empty-canvas button { display: inline-flex; align-items: center; gap: 6px; height: 34px; margin-top: 5px; padding: 0 11px; border: 1px solid #2563eb; border-radius: 5px; background: #2563eb; color: #fff; font-size: 12px; font-weight: 700; }.canvas-tip { position: absolute; bottom: 12px; left: 14px; display: inline-flex; align-items: center; gap: 5px; padding: 5px 8px; border: 1px solid #dce5ef; border-radius: 4px; background: rgba(255,255,255,.9); color: #718096; font-size: 11px; }
.panel-resizer { position: absolute; top: 0; right: calc(var(--right-panel-size) - 3px); bottom: 0; z-index: 12; width: 6px; cursor: col-resize; touch-action: none; transition: right .22s ease, opacity .18s ease; }.panel-resizer::after { position: absolute; top: 0; bottom: 0; left: 2px; width: 1px; background: #e5e7eb; content: ''; transition: width .16s ease, background .16s ease; }.panel-resizer:hover::after { width: 2px; background: #bfdbfe; }.panel-resizer.active::after { width: 2px; background: #2563eb; }.panel-resizer.collapsed { opacity: 0; pointer-events: none; }.editor-body.right-panel-resizing .panel-resizer, .editor-body.right-panel-resizing .panel-toggle { transition: none; }.panel-toggle { position: absolute; top: 14px; right: calc(var(--right-panel-size) - 14px); z-index: 14; display: grid; place-items: center; width: 28px; height: 28px; padding: 0; border: 1px solid #dce5ef; border-radius: 50%; background: #fff; color: #64748b; box-shadow: 0 2px 6px rgba(15,23,42,.08); transition: right .22s ease, background .16s ease, color .16s ease, border-color .16s ease; }.panel-toggle:hover { border-color: #bfdbfe; background: #f8fafc; color: #2563eb; }.panel-toggle.collapsed { right: 8px; }.inspector { position: relative; min-width: 0; overflow: hidden; border-left: 1px solid #dce5ef; background: #fff; opacity: 1; transform: translateX(0); transition: opacity .18s ease, transform .22s ease, border-color .18s ease; }.inspector.collapsed { border-left-color: transparent; opacity: 0; pointer-events: none; transform: translateX(20px); }.inspector-panel-content { display: contents; }.inspector nav { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); min-height: 47px; border-bottom: 1px solid #e2e8f0; }.inspector nav button { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; position: relative; border: 0; background: transparent; color: #64748b; font-size: 12px; font-weight: 650; }.inspector nav button.active { color: #2563eb; }.inspector nav button.active::after { position: absolute; right: 12px; bottom: 0; left: 12px; height: 2px; background: #2563eb; content: ''; }.inspector nav span { display: inline-grid; place-items: center; min-width: 15px; height: 15px; margin-left: 3px; border-radius: 8px; background: #dbeafe; color: #2563eb; font-size: 10px; }.inspector-content { height: calc(100% - 47px); overflow-y: auto; padding: 17px 16px 24px; }.side-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 16px; }.side-title div { display: grid; gap: 4px; }.side-title span { color: #7c8ca0; font-size: 11px; }.side-title strong { color: #1e293b; font-size: 14px; }.side-title b { max-width: 102px; padding: 4px 6px; border-radius: 4px; background: #eff6ff; color: #2563eb; font-size: 10px; text-align: center; }.inspector-content label { display: grid; gap: 5px; margin-bottom: 12px; color: #66758a; font-size: 11px; font-weight: 650; }.inspector-content input, .inspector-content textarea, .inspector-content select { width: 100%; border: 1px solid #d9e2ec; border-radius: 4px; background: #fff; color: #334155; font: inherit; font-size: 12px; outline: 0; }.inspector-content input, .inspector-content select { height: 33px; padding: 0 8px; }.inspector-content textarea { padding: 8px; resize: vertical; }.inspector-content input:focus, .inspector-content textarea:focus, .inspector-content select:focus { border-color: #60a5fa; box-shadow: 0 0 0 2px rgba(59,130,246,.1); }.inspector-content input:disabled { background: #f8fafc; color: #94a3b8; }.ports { display: flex; flex-wrap: wrap; gap: 5px; margin: 4px 0 16px; }.ports > span { flex-basis: 100%; margin-bottom: 2px; color: #64748b; font-size: 11px; font-weight: 650; }.ports button { height: 24px; padding: 0 6px; border: 1px solid #cbd5e1; border-radius: 3px; background: #fff; color: #475569; font-size: 10px; }.ports button:hover { border-color: #60a5fa; color: #2563eb; }.danger { display: inline-flex; align-items: center; justify-content: center; gap: 6px; width: 100%; height: 34px; border: 1px solid #fecaca; border-radius: 4px; background: #fff; color: #dc2626; font-size: 12px; font-weight: 650; }.overview { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }.overview article { display: grid; gap: 5px; min-height: 72px; padding: 11px; border: 1px solid #e2e8f0; border-radius: 5px; background: #fbfdff; }.overview span { color: #718096; font-size: 11px; }.overview strong { color: #263548; font-size: 15px; }.tip { margin: 16px 0 0; color: #7c8ca0; font-size: 12px; line-height: 1.7; }.check { display: grid; gap: 8px; margin-bottom: 10px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 5px; }.check > span { width: max-content; padding: 3px 5px; border-radius: 3px; font-size: 10px; font-weight: 700; }.check.high > span { background: #fef2f2; color: #dc2626; }.check.medium > span { background: #fff7ed; color: #c2410c; }.check.low > span { background: #eff6ff; color: #2563eb; }.check strong { color: #334155; font-size: 12px; line-height: 1.45; }.check p { margin: 0; color: #64748b; font-size: 11px; line-height: 1.55; }.check div { display: flex; gap: 8px; }.check div button { padding: 0; border: 0; background: transparent; color: #64748b; font-size: 11px; }.check div .apply { color: #2563eb; font-weight: 700; }.clear-check { display: grid; justify-items: center; gap: 8px; padding: 36px 12px; color: #10b981; text-align: center; }.clear-check strong { color: #526174; font-size: 12px; }
.assistant-panel { display: flex; flex-direction: column; height: calc(100% - 47px); min-height: 0; background: #fff; }.assistant-header { display: flex; justify-content: space-between; gap: 10px; padding: 15px 15px 12px; border-bottom: 1px solid #edf1f5; }.assistant-header > div { display: grid; gap: 3px; min-width: 0; }.assistant-header span { color: #64748b; font-size: 11px; }.assistant-header strong { color: #1e293b; font-size: 14px; }.assistant-header p { margin: 1px 0 0; color: #718096; font-size: 11px; line-height: 1.55; }.assistant-new-chat { display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; gap: 4px; height: 28px; padding: 0 7px; border: 1px solid #dce5ef; border-radius: 4px; background: #fff; color: #526174; font-size: 11px; font-weight: 650; }.assistant-new-chat:hover { border-color: #93c5fd; color: #2563eb; background: #f8fbff; }.assistant-messages { display: flex; flex: 1; flex-direction: column; gap: 12px; min-height: 0; padding: 14px 15px; overflow-y: auto; overscroll-behavior: contain; }.assistant-message { display: grid; gap: 8px; align-self: flex-start; max-width: 100%; }.assistant-message.user { align-self: flex-end; max-width: 88%; }.assistant-message-copy { color: #3f4d60; font-size: 12px; line-height: 1.7; white-space: pre-line; }.assistant-message.user .assistant-message-copy { padding: 8px 10px; border-radius: 8px; background: #eff6ff; color: #1e3a5f; }.assistant-suggestions { display: grid; gap: 5px; padding-top: 2px; }.assistant-suggestions button { width: max-content; max-width: 100%; padding: 0; border: 0; background: transparent; color: #2563eb; font-size: 11px; line-height: 1.45; text-align: left; }.assistant-suggestions button:hover { color: #1d4ed8; text-decoration: underline; }.assistant-thinking { display: flex; align-items: center; gap: 6px; color: #7c8ca0; font-size: 11px; }.assistant-thinking i { width: 12px; height: 12px; border: 2px solid #bfdbfe; border-top-color: #2563eb; border-radius: 50%; animation: spin .8s linear infinite; }.assistant-operation { display: grid; gap: 5px; padding: 9px 10px; border: 1px solid #dbe5ef; border-radius: 5px; background: #fbfdff; color: #526174; font-size: 11px; line-height: 1.45; }.assistant-operation header { display: flex; align-items: center; gap: 5px; color: #2563eb; }.assistant-operation header strong { font-size: 11px; }.assistant-operation.done { border-color: #ccebdc; background: #f4fbf7; }.assistant-operation.done header { color: #15803d; }.assistant-operation-actions { display: flex; gap: 8px; margin-top: 3px; }.assistant-operation-actions button { height: 27px; padding: 0 8px; border: 1px solid #d6e0eb; border-radius: 4px; background: #fff; color: #526174; font-size: 11px; font-weight: 650; }.assistant-operation-actions .assistant-apply { border-color: #2563eb; background: #2563eb; color: #fff; }.assistant-composer { display: grid; grid-template-columns: 30px minmax(0, 1fr) 32px; align-items: end; gap: 5px; flex: 0 0 auto; padding: 10px 12px; border-top: 1px solid #e5eaf0; background: #fff; }.assistant-composer textarea { width: 100%; min-height: 32px; max-height: 88px; padding: 7px 0; border: 0; outline: 0; color: #334155; font-size: 12px; line-height: 1.45; resize: none; }.assistant-composer textarea::placeholder { color: #94a3b8; }.assistant-attach, .assistant-send { display: grid; place-items: center; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: #64748b; }.assistant-attach:hover { background: #f1f5f9; color: #2563eb; }.assistant-send { background: #2563eb; color: #fff; }.assistant-send:hover { background: #1d4ed8; color: #fff; }.assistant-send:disabled { opacity: .42; cursor: default; }
.assistant-file-input { display: none; }
.assistant-attachment-status { display: flex; align-items: center; gap: 6px; min-height: 28px; padding: 5px 12px; border-top: 1px solid #eef2f6; background: #fbfdff; color: #2563eb; font-size: 11px; }
.assistant-attachment-status .error { color: #dc2626; }
.assistant-attachment-status button { margin-left: auto; padding: 0 3px; border: 0; background: transparent; color: #94a3b8; font-size: 16px; line-height: 1; }
.assistant-composer { grid-template-columns: 30px minmax(0, 1fr) 30px 32px; }
.assistant-attach, .assistant-web-search { display: grid; place-items: center; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: #64748b; }
.assistant-attach:hover, .assistant-web-search:hover { background: #f1f5f9; color: #2563eb; }
.assistant-web-search.active { background: #eff6ff; color: #2563eb; }
.assistant-message-copy, .assistant-message-copy :deep(.ai-message-content), .assistant-message-copy :deep(.markdown-content) { min-width: 0; max-width: 100%; overflow-wrap: anywhere; word-break: break-word; }
.assistant-message-copy :deep(.markdown-content) { overflow-x: hidden; }
.assistant-message-copy :deep(pre), .assistant-message-copy :deep(code) { max-width: 100%; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.assistant-message-copy :deep(.markdown-table-scroll) { max-width: 100%; overflow-x: auto; }
.context-menu { position: fixed; z-index: 20; display: grid; min-width: 156px; padding: 5px; border: 1px solid #d8e1ec; border-radius: 5px; background: #fff; box-shadow: 0 12px 28px rgba(15,23,42,.16); }.context-menu button { display: flex; align-items: center; gap: 8px; height: 31px; padding: 0 8px; border: 0; border-radius: 3px; background: transparent; color: #475569; font-size: 12px; text-align: left; }.context-menu button:hover { background: #f1f5f9; color: #2563eb; }
@keyframes spin { to { transform: rotate(360deg); } } @keyframes pulse { 50% { opacity: .4; } }
.library-actions { display: flex; align-items: center; gap: 5px; padding: 0 14px 8px; color: #cbd5e1; font-size: 11px; }.library-actions button { padding: 0; border: 0; background: transparent; color: #64748b; font-size: 11px; cursor: pointer; }.library-actions button:hover { color: #2563eb; }
.port-summary { display: grid; grid-template-columns: 1fr auto; gap: 4px 8px; margin: 4px 0 10px; padding: 9px 10px; border: 1px solid #e2e8f0; border-radius: 5px; background: #fbfdff; color: #64748b; font-size: 11px; }.port-summary span { grid-column: 1 / -1; color: #526174; font-weight: 650; }.port-summary strong { color: #334155; font-size: 11px; }.port-summary em { color: #15803d; font-size: 11px; font-style: normal; }.ports button { display: inline-flex; align-items: center; gap: 4px; }.ports button small { color: #15803d; font-size: 9px; }.ports button.occupied { border-color: #e2e8f0; background: #f8fafc; color: #94a3b8; cursor: not-allowed; }.ports button.occupied small { color: #94a3b8; }.connection-notice { position: absolute; top: 14px; left: 50%; z-index: 18; padding: 8px 12px; border: 1px solid #fed7aa; border-radius: 5px; background: #fff7ed; color: #c2410c; box-shadow: 0 8px 20px rgba(15,23,42,.1); font-size: 12px; transform: translateX(-50%); pointer-events: none; }
@media (max-width: 1220px) { .editor-body { --right-panel-size: 272px; grid-template-columns: 190px minmax(0, 1fr) var(--right-panel-size); }.editor-body.right-panel-collapsed { grid-template-columns: 190px minmax(0, 1fr) 0; }.wide { display: none; }.editor-topbar { grid-template-columns: minmax(205px, 1fr) auto auto; }.editor-actions { gap: 4px; } }
@media (max-width: 900px) { .editor-body, .editor-body.right-panel-collapsed { --right-panel-size: 300px; grid-template-columns: 180px minmax(0, 1fr); }.inspector { position: absolute; top: 52px; right: 0; bottom: 0; width: var(--right-panel-size); box-shadow: -6px 0 18px rgba(15,23,42,.08); z-index: 8; }.inspector.collapsed { transform: translateX(100%); }.editor-topbar { grid-template-columns: 1fr auto; }.editor-tools { grid-column: 1 / -1; order: 3; }.editor-topbar { min-height: 92px; }.inspector { top: 92px; } }
@media (max-width: 640px) { .editor-body, .editor-body.right-panel-collapsed { --right-panel-size: min(300px, calc(100vw - 20px)); grid-template-columns: 1fr; }.device-library { display: none; }.editor-topbar { grid-template-columns: 1fr auto; gap: 7px; padding: 7px; }.editor-project { min-width: 0; }.editor-title strong { max-width: 135px; }.editor-tools { gap: 2px; overflow: hidden; }.editor-tools > i { margin: 0 1px; }.editor-actions .editor-icon { display: none; }.inspector { width: var(--right-panel-size); }.canvas-tip { display: none; } }
.device-card > span { width: 34px; height: 34px; border-radius: 8px; background: #eff6ff !important; color: #334155 !important; }
.device-card > span.is-teal, .device-card > span.is-red, .device-card > span.is-violet, .device-card > span.is-slate, .device-card > span.is-amber { background: #eff6ff !important; color: #334155 !important; }
.node-symbol { color: #334155 !important; }
.device-card img { width: 22px; height: 22px; }
.topology-viewport { touch-action: none; cursor: default; }
.topology-viewport:not(.connecting):not(.panning):not(.space-pan) { cursor: default; }
.topology-viewport:focus { outline: 0; }
.topology-viewport.panning { cursor: grabbing; }
.topology-viewport.panning .topology-node { cursor: grabbing; }
.topology-viewport.space-pan, .topology-viewport.space-pan .topology-node { cursor: grab; }
.topology-viewport.space-pan.panning, .topology-viewport.space-pan.panning .topology-node { cursor: grabbing; }
.edge-preview { fill: none; stroke: #2563eb; stroke-width: 2; stroke-dasharray: 6 5; stroke-linecap: round; pointer-events: none; opacity: .8; }
.alignment-guide { position: absolute; z-index: 2; pointer-events: none; border-color: rgba(37, 99, 235, .58); border-style: dashed; border-width: 0; }
.alignment-guide.vertical { top: -2000px; bottom: -2000px; width: 0; border-left-width: 1px; }
.alignment-guide.horizontal { right: -2000px; left: -2000px; height: 0; border-top-width: 1px; }
.selection-toolbar { position: absolute; z-index: 12; display: flex; align-items: center; gap: 2px; min-height: 30px; padding: 3px; border: 1px solid #cbd5e1; border-radius: 6px; background: rgba(255, 255, 255, .96); box-shadow: 0 8px 20px rgba(15, 23, 42, .16); transform: translateX(-50%); }
.selection-toolbar button { display: grid; place-items: center; width: 26px; height: 26px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: #526174; }
.selection-toolbar button:hover:not(:disabled) { background: #eff6ff; color: #2563eb; }
.selection-toolbar button:disabled { cursor: default; opacity: .32; }
.selection-toolbar i { width: 1px; height: 18px; margin: 0 2px; background: #e2e8f0; }
html[data-theme='dark'] .selection-toolbar { border-color: #334155; background: rgba(15, 23, 42, .96); }
html[data-theme='dark'] .selection-toolbar button { color: #cbd5e1; }
html[data-theme='dark'] .selection-toolbar button:hover:not(:disabled) { background: #1e3a5f; color: #93c5fd; }
.topology-node { -webkit-user-drag: none; }
.node-symbol { pointer-events: none; color: #334155 !important; }
.node-symbol img, .node-symbol svg, .device-card img { pointer-events: none; user-select: none; -webkit-user-drag: none; }
.inspector, .inspector-panel-content, .inspector-content, .assistant-panel, .assistant-header, .assistant-messages, .assistant-composer, .assistant-message, .assistant-operation { min-width: 0; max-width: none; }
.inspector-content { box-sizing: border-box; width: 100%; }
.assistant-panel { width: 100%; }
.assistant-header { box-sizing: border-box; width: 100%; }
.assistant-messages { box-sizing: border-box; width: 100%; overflow-x: hidden; }
.assistant-messages { font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif; }
.assistant-message { width: min(100%, 720px); max-width: 100%; margin: 0 auto; align-self: center; }
.assistant-message:not(.user) { align-self: stretch; }
.assistant-message.user { width: auto; max-width: min(90%, 620px); margin-left: auto; margin-right: 0; align-self: flex-end; }
.assistant-message-copy { font-size: 13px; line-height: 1.75; }
.assistant-message:not(.user) .assistant-message-copy { width: 100%; }
.assistant-operation { width: 100%; max-width: 100%; }
.assistant-message-copy, .assistant-operation span { min-width: 0; overflow-wrap: anywhere; word-break: break-word; }
.assistant-message-copy { white-space: pre-wrap; }
.assistant-message-copy .ai-message-content { width: 100%; max-width: 100%; overflow-wrap: anywhere; word-break: break-word; }
.assistant-message-copy :deep(.markdown-content) { max-width: 100%; overflow-wrap: anywhere; word-break: break-word; }
.assistant-message-copy :deep(.markdown-content h1), .assistant-message-copy :deep(.markdown-content h2), .assistant-message-copy :deep(.markdown-content h3) { margin: 12px 0 7px; font-size: 14px; line-height: 1.45; }
.assistant-message-copy :deep(.markdown-content p), .assistant-message-copy :deep(.markdown-content li) { margin-bottom: 7px; font-size: 13px; line-height: 1.65; }
.assistant-message-copy :deep(.markdown-content ul), .assistant-message-copy :deep(.markdown-content ol) { margin-bottom: 8px; padding-left: 18px; }
.assistant-message-copy :deep(.markdown-content pre), .assistant-message-copy :deep(.markdown-content code) { max-width: 100%; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.assistant-message-copy :deep(.markdown-content .markdown-code-block) { max-width: 100%; margin: 8px 0; }
.assistant-message-copy :deep(.markdown-content .markdown-code-block pre) { overflow-x: auto; }
.assistant-composer { box-sizing: border-box; width: 100%; min-height: 94px; align-items: end; }
.assistant-composer textarea { height: 72px; min-height: 64px; max-height: 260px; resize: vertical; overflow: auto; }
.assistant-composer .assistant-send { align-self: end; }
.device-library-state { display: grid; justify-items: center; align-content: start; gap: 8px; min-height: 160px; padding: 26px 16px; color: #94a3b8; font-size: 11px; text-align: center; }
.device-library-state.error strong { color: #475569; font-size: 12px; }
.device-library-state.error span { max-width: 170px; line-height: 1.5; }
.device-library-state button { height: 28px; padding: 0 10px; border: 1px solid #bfdbfe; border-radius: 4px; background: #eff6ff; color: #2563eb; font-size: 11px; cursor: pointer; }
.device-library-state.skeleton { justify-items: stretch; gap: 7px; padding: 12px; }
.device-library-state.skeleton i { display: block; height: 58px; border: 1px solid #edf2f7; border-radius: 5px; background: linear-gradient(90deg, #f8fafc 25%, #eef2f7 50%, #f8fafc 75%); background-size: 200% 100%; animation: device-library-loading 1.2s ease-in-out infinite; }
.device-library-state.skeleton i:nth-child(even) { opacity: .72; }
@keyframes device-library-loading { from { background-position: 100% 0; } to { background-position: -100% 0; } }
.check-refresh { flex: 0 0 auto; height: 28px; padding: 0 9px; border: 1px solid #dce5ef; border-radius: 4px; background: #fff; color: #526174; font-size: 11px; font-weight: 650; }
.check-refresh:hover:not(:disabled) { border-color: #93c5fd; color: #2563eb; background: #f8fbff; }
.check-refresh:disabled { cursor: wait; opacity: .6; }
.check-filters { display: flex; gap: 5px; margin: 0 0 12px; padding-bottom: 2px; overflow-x: auto; }
.check-filters button { flex: 0 0 auto; height: 26px; padding: 0 8px; border: 1px solid #e2e8f0; border-radius: 4px; background: #fff; color: #64748b; font-size: 10px; }
.check-filters button:hover { border-color: #bfdbfe; color: #2563eb; }
.check-filters button.active { border-color: #93c5fd; background: #eff6ff; color: #1d4ed8; font-weight: 700; }
.assistant-composer .assistant-send.stop { position: relative; border: 1px solid #fecaca; background: #fff; color: #dc2626; }
.assistant-composer .assistant-send.stop:hover { border-color: #f87171; background: #fff5f5; color: #b91c1c; }
.assistant-composer .assistant-send.stop span { width: 10px; height: 10px; border-radius: 2px; background: currentColor; }
.assistant-panel { position: relative; }
.assistant-scroll-latest { position: fixed; top: 50%; left: 50%; z-index: 100; min-width: 126px; height: 30px; padding: 0 12px; border: 1px solid #bfdbfe; border-radius: 15px; background: rgba(255, 255, 255, .96); color: #2563eb; box-shadow: 0 5px 16px rgba(15, 23, 42, .14); font-size: 11px; font-weight: 700; transform: translate(-50%, -50%); cursor: pointer; }
.assistant-scroll-latest:hover { border-color: #60a5fa; background: #eff6ff; }
.assistant-scroll-latest[hidden] { display: none; }
</style>
