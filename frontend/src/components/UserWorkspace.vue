<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  CheckCircle2,
  ChevronRight,
  Copy,
  Database,
  Download,
  FileOutput,
  FileText,
  ListChecks,
  MessageSquare,
  MoreHorizontal,
  MoreVertical,
  Network,
  Pencil,
  Pin,
  Plus,
  RotateCcw,
  Search,
  SearchX,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  TriangleAlert,
  UploadCloud,
  X,
} from '@lucide/vue'
import ChatComposer from './ChatComposer.vue'
import AiMessageContent from './AiMessageContent.vue'
import MetricStrip from './MetricStrip.vue'
import HeightCollapse from './HeightCollapse.vue'
import TopologyWorkspace from './TopologyWorkspace.vue'
import { createConversation, createProject, editConversationMessage, getConversationMessages, listProjectFiles, streamConversation, streamRegenerateConversation, uploadProjectFile } from '../api/user'
import { deleteProject, duplicateProject, getTopology, getUserSubscription, updateProject } from '../api/user'
import {
  deviceInventory,
  files,
  reportSections,
  requirementSummary,
  riskItems
} from '../data/mockData'

const props = defineProps({
  activePage: {
    type: String,
    required: true
  },
  chatId: {
    type: String,
    default: null
  },
  chatTitle: {
    type: String,
    default: '新对话'
  },
  chatPinned: {
    type: Boolean,
    default: false
  },
  project: {
    type: Object,
    required: true
  },
  newChatSessionKey: {
    type: Number,
    default: 0
  },
  recentChats: {
    type: Array,
    default: () => []
  },
  projectItems: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['select', 'open-chat', 'open-project', 'rename-chat', 'rename-new-chat', 'toggle-chat-pin', 'project-created', 'project-updated', 'project-deleted', 'project-duplicated', 'chat-created', 'chat-updated', 'resource-unavailable'])

const requirementText = ref('请帮我设计一个三层办公楼网络。一楼有前台和会议室，二楼是办公区，三楼有财务部和服务器机房，约 150 台终端，需要无线覆盖、内外网隔离和后续扩展。')
const requirementState = ref('已完成 AI 分析')
const chatInput = ref('')
const conversationMenuOpen = ref(false)
const conversationMenuPosition = ref({ top: 0, left: 0 })
const isConversationTitleEditing = ref(false)
const conversationTitleDraft = ref('')
const conversationTitleInput = ref(null)
const conversationToast = ref(null)
const chatMessages = ref({})
const newChatMessages = ref([])
let pendingChatHandoffId = null
const newProjectName = ref('')
const selectedProjectColor = ref('#1a73e8')
const projectCreateOpen = ref(false)
const projectCreateMode = ref('manual')
const projectCreating = ref(false)
const projectMenuId = ref(null)
const projectRenameOpen = ref(false)
const projectRenameTarget = ref(null)
const projectRenameDraft = ref('')
const projectDeleteOpen = ref(false)
const projectDeleteTarget = ref(null)
const projectExportOpen = ref(false)
const projectExportTarget = ref(null)
const projectAction = ref('')
const projectActionError = ref('')
const projectFiles = ref([])
const projectFilesLoading = ref(false)
const projectFileUploading = ref(false)
const projectFileError = ref('')
const projectSubscription = ref(null)
const projectFileInput = ref(null)
const topologyPreviews = ref({})
const conversationSearchTerm = ref('')
const conversationSearchInput = ref(null)
const chatThread = ref(null)
const threadAtBottom = ref(true)
const threadHasOverflow = ref(false)
const messageElements = new Map()
const conversationMenuRef = ref(null)
const chatComposer = ref(null)
const editingMessageInput = ref(null)
const pendingReplyCount = ref(0)
const isReplying = computed(() => pendingReplyCount.value > 0)
const activeMessageMenu = ref(null)
const feedbackMenuMessageId = ref(null)
const copiedMessageId = ref(null)
const regeneratedMessageId = ref(null)
const editingMessageId = ref(null)
const editingMessageDraft = ref('')
let replyTimers = new Set()
const activeReplies = new Map()
let copiedNoticeTimer = null
let regeneratedNoticeTimer = null
let conversationToastTimer = null
let conversationToastSequence = 0
let messageSequence = 10
let streamAbortController = null
const conversationAccessError = ref('')
let conversationCreationPromise = null
const conversationLoadVersions = new Map()
let conversationBackgroundPollTimer = 0
let conversationBackgroundPollChatId = null

function projectKey(project) { return project?.publicId || project?.id || null }

const projectColors = ['#1a73e8', '#12b886', '#f59f00', '#e03131', '#7048e8', '#0f766e']

const quickPrompts = [
  '根据三层办公楼需求生成拓扑图',
  '这个方案有哪些风险？',
  '帮我生成设备清单',
  '只回答：防火墙放在这里的原因'
]

const metrics = computed(() => [
  { label: '项目数量', value: props.projectItems.length, trend: '真实项目数据' },
  { label: '当前终端', value: props.project.terminals || '-', trend: props.project.type },
  { label: '资料文件', value: props.project.files, trend: '已建立项目记忆' },
  { label: '拓扑版本', value: props.project.topologyVersion, trend: props.project.status }
])
const previewNodeColors = {
  internet: '#475569', cloud: '#0284c7', router: '#2563eb', firewall: '#dc2626',
  'core-switch': '#0f766e', 'layer3-switch': '#0891b2', 'access-switch': '#0e7490',
  'wireless-ap': '#7c3aed', 'ac-controller': '#6d28d9', server: '#475569',
  database: '#0f766e', nas: '#0369a1', pc: '#64748b', laptop: '#64748b',
  printer: '#64748b', 'ip-phone': '#64748b', camera: '#64748b'
}
function createTopologyPreview(topology) {
  const sourceNodes = Array.isArray(topology?.nodes) ? topology.nodes : []
  const sourceEdges = Array.isArray(topology?.edges) ? topology.edges : []
  if (!sourceNodes.length) return { nodes: [], edges: [] }

  const nodes = sourceNodes.map((item, index) => ({
    id: item.id || `node-${index}`,
    x: Number.isFinite(Number(item.x)) ? Number(item.x) : index * 110,
    y: Number.isFinite(Number(item.y)) ? Number(item.y) : 80,
    width: Math.max(36, Number(item.width) || 76),
    height: Math.max(36, Number(item.height) || 76),
    color: previewNodeColors[String(item.type || '').replaceAll('_', '-')] || '#64748b'
  }))
  const minX = Math.min(...nodes.map((item) => item.x))
  const minY = Math.min(...nodes.map((item) => item.y))
  const maxX = Math.max(...nodes.map((item) => item.x + item.width))
  const maxY = Math.max(...nodes.map((item) => item.y + item.height))
  const canvasWidth = 320, canvasHeight = 128, padding = 18
  const topologyWidth = Math.max(1, maxX - minX)
  const topologyHeight = Math.max(1, maxY - minY)
  const scale = Math.min(1.35, (canvasWidth - padding * 2) / topologyWidth, (canvasHeight - padding * 2) / topologyHeight)
  const offsetX = (canvasWidth - topologyWidth * scale) / 2 - minX * scale
  const offsetY = (canvasHeight - topologyHeight * scale) / 2 - minY * scale
  const transformed = nodes.map((item) => {
    const width = Math.max(7, Math.min(18, item.width * scale))
    const height = Math.max(7, Math.min(18, item.height * scale))
    const centerX = (item.x + item.width / 2) * scale + offsetX
    const centerY = (item.y + item.height / 2) * scale + offsetY
    return { ...item, x: centerX - width / 2, y: centerY - height / 2, width, height, centerX, centerY }
  })
  const nodeById = new Map(transformed.map((item) => [item.id, item]))
  const edges = sourceEdges.map((item, index) => {
    const source = nodeById.get(item.source)
    const target = nodeById.get(item.target)
    return source && target ? { id: item.id || `edge-${index}`, x1: source.centerX, y1: source.centerY, x2: target.centerX, y2: target.centerY } : null
  }).filter(Boolean)
  return { nodes: transformed, edges }
}
let topologyPreviewRequest = 0
async function loadTopologyPreviews() {
  const request = ++topologyPreviewRequest
  const results = await Promise.all(props.projectItems.map(async (project) => {
    try { return [projectKey(project), createTopologyPreview(await getTopology(projectKey(project)))] } catch { return [projectKey(project), { nodes: [], edges: [] }] }
  }))
  if (request !== topologyPreviewRequest || props.activePage !== 'projects') return
  topologyPreviews.value = Object.fromEntries(results)
}
function formatProjectUpdatedAt(value) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const elapsed = Math.max(0, Date.now() - date.getTime())
  if (elapsed < 60 * 1000) return '刚刚'
  if (elapsed < 60 * 60 * 1000) return `${Math.floor(elapsed / (60 * 1000))}分钟前`
  if (elapsed < 24 * 60 * 60 * 1000) return `${Math.floor(elapsed / (60 * 60 * 1000))}小时前`
  if (elapsed < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(elapsed / (24 * 60 * 60 * 1000))}天前`
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function projectTypeLabel(type) {
  return type === 'topo_design' ? '网络拓扑设计' : (type || '网络项目')
}

const projectCards = computed(() => props.projectItems.map((project) => ({
  ...project,
  type: projectTypeLabel(project.type),
  creation: project.creationMode === 'ai' ? 'AI生成' : '手动设计',
  topologyPreview: topologyPreviews.value[projectKey(project)] || null,
  devices: topologyPreviews.value[projectKey(project)]?.nodes.length ?? 0,
  connections: topologyPreviews.value[projectKey(project)]?.edges.length ?? 0,
  updated: formatProjectUpdatedAt(project.updatedAt),
  state: project.status || '草稿'
})))

const isNewDialogue = computed(() => props.activePage === 'new-chat')
const isDialoguePage = computed(() => props.activePage === 'chat' || props.activePage === 'new-chat')
const activeChatMessages = computed(() => chatMessages.value[props.chatId] || [])
const dialogueMessages = computed(() => (isNewDialogue.value ? newChatMessages.value : activeChatMessages.value))
const visibleDialogueMessages = computed(() => dialogueMessages.value.filter((message) => (
  (message.role === 'user' || message.role === 'assistant') && !['invalid', 'superseded'].includes(String(message.status || '').toLowerCase())
)))
const currentConversation = computed(() => ({
  id: props.chatId ?? null,
  title: props.chatTitle?.trim() || '新对话',
  messages: dialogueMessages.value
}))
const activeConversationTitle = computed(() => currentConversation.value.title)
const conversationActions = computed(() => [
  { action: 'pin', label: props.chatPinned ? '取消固定' : '固定', icon: Pin },
  { action: 'rename', label: '重命名', icon: Pencil },
  { action: 'pdf', label: '下载 PDF', icon: Download }
])
const isNewDialogueEmpty = computed(() => isNewDialogue.value && newChatMessages.value.length === 0)
const filteredConversations = computed(() => {
  const keyword = conversationSearchTerm.value.trim().toLocaleLowerCase()
  if (!keyword) return props.recentChats
  return props.recentChats.filter((conversation) => (
    conversation.title.toLocaleLowerCase().includes(keyword)
  ))
})

function clearConversationSearch() {
  conversationSearchTerm.value = ''
  conversationSearchInput.value?.focus()
}

async function focusConversationSearch() {
  if (props.activePage !== 'chat-search') emit('select', 'chat-search')
  await nextTick()
  conversationSearchInput.value?.focus()
}

function highlightedTitleParts(title) {
  const keyword = conversationSearchTerm.value.trim()
  if (!keyword) return [{ text: title, matches: false }]

  const lowerTitle = title.toLocaleLowerCase()
  const lowerKeyword = keyword.toLocaleLowerCase()
  const parts = []
  let startIndex = 0
  let matchIndex = lowerTitle.indexOf(lowerKeyword, startIndex)

  while (matchIndex !== -1) {
    if (matchIndex > startIndex) parts.push({ text: title.slice(startIndex, matchIndex), matches: false })
    parts.push({ text: title.slice(matchIndex, matchIndex + keyword.length), matches: true })
    startIndex = matchIndex + keyword.length
    matchIndex = lowerTitle.indexOf(lowerKeyword, startIndex)
  }

  if (startIndex < title.length) parts.push({ text: title.slice(startIndex), matches: false })
  return parts.length ? parts : [{ text: title, matches: false }]
}

const projectFileLimits = computed(() => {
  const quota = projectSubscription.value?.quota || {}
  return {
    count: Number(quota.fileUploads?.total ?? 10),
    singleMb: Number(quota.singleFileSizeMb?.total ?? 10),
    capacityMb: Number(quota.knowledgeCapacityMb?.total ?? 50)
  }
})

function formatProjectFileSize(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function formatProjectFileStatus(file) {
  if (file?.textAvailable || file?.status === '已读取') return '已读取，可用于 AI'
  return file?.errorMessage || file?.status || '读取失败'
}

async function loadProjectFiles() {
  const id = projectKey(props.project)
  if (!id) return
  projectFilesLoading.value = true
  projectFileError.value = ''
  try {
    const [items, subscription] = await Promise.all([listProjectFiles(id), getUserSubscription()])
    projectFiles.value = Array.isArray(items) ? items : []
    projectSubscription.value = subscription || null
  } catch (error) {
    projectFileError.value = error?.message || '项目资料加载失败'
  } finally {
    projectFilesLoading.value = false
  }
}

function openProjectFilePicker() {
  projectFileInput.value?.click()
}

async function handleProjectFileSelection(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const limits = projectFileLimits.value
  const allowed = ['pdf', 'docx', 'xlsx', 'txt', 'md']
  const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : ''
  if (!allowed.includes(extension)) {
    showConversationToast('仅支持 PDF、DOCX、XLSX、TXT、Markdown 文件', 'warning')
    return
  }
  if (file.size > limits.singleMb * 1024 * 1024) {
    showConversationToast(`单个文件不能超过 ${limits.singleMb} MB`, 'warning')
    return
  }
  if (projectFiles.value.length >= limits.count) {
    showConversationToast(`当前套餐最多上传 ${limits.count} 份资料`, 'warning')
    return
  }
  const totalSize = projectFiles.value.reduce((total, item) => total + Number(item.size || 0), 0)
  if (totalSize + file.size > limits.capacityMb * 1024 * 1024) {
    showConversationToast(`项目资料总容量不能超过 ${limits.capacityMb} MB`, 'warning')
    return
  }
  projectFileUploading.value = true
  projectFileError.value = ''
  try {
    await uploadProjectFile(projectKey(props.project), file)
    await loadProjectFiles()
    showConversationToast('资料已上传并完成 AI 读取')
  } catch (error) {
    projectFileError.value = error?.message || '资料上传失败'
    showConversationToast(projectFileError.value, 'warning')
  } finally {
    projectFileUploading.value = false
  }
}

watch(
  () => props.newChatSessionKey,
  () => {
    cancelReply()
    stopConversationBackgroundPolling()
    messageElements.clear()
    newChatMessages.value = []
    chatInput.value = ''
    conversationMenuOpen.value = false
    threadAtBottom.value = true
    threadHasOverflow.value = false
  }
)

watch(
  () => props.activePage,
  (activePage) => {
    if (activePage === 'projects') loadTopologyPreviews()
    if (activePage === 'files') loadProjectFiles()
  },
  { immediate: true }
)

watch(
  () => projectKey(props.project),
  (projectId) => {
    if (projectId && props.activePage === 'files') loadProjectFiles()
  }
)

watch(
  () => props.projectItems,
  () => {
    if (props.activePage === 'projects') loadTopologyPreviews()
  }
)

watch(
  () => props.chatId,
  async (chatId) => {
    const isInternalHandoff = pendingChatHandoffId === chatId
    if (isInternalHandoff) pendingChatHandoffId = null
    else cancelReply()
    stopConversationBackgroundPolling()
    messageElements.clear()
    editingMessageId.value = null
    editingMessageDraft.value = ''
    activeMessageMenu.value = null
    chatInput.value = ''
    closeConversationOverlays()
    conversationAccessError.value = ''
    if (!chatId) return
    const loadVersion = (conversationLoadVersions.get(chatId) || 0) + 1
    conversationLoadVersions.set(chatId, loadVersion)
    try {
      const data = await getConversationMessages(chatId)
      if (props.chatId !== chatId || conversationLoadVersions.get(chatId) !== loadVersion) return
      hydrateChatMessages(chatId, data.items ?? [])
      scheduleConversationBackgroundPolling(chatId)
      await scrollConversationToLatest(chatId, loadVersion)
    } catch (error) {
      if (props.chatId !== chatId || conversationLoadVersions.get(chatId) !== loadVersion) return
      // A transient history failure must never erase optimistic or streaming
      // messages already visible in this conversation. The computed view
      // already falls back to an empty list when no cache exists.
      conversationAccessError.value = ''
      if (error?.status === 404 || error?.code === 'RESOURCE_NOT_AVAILABLE') {
        emit('resource-unavailable', { type: 'conversation', id: chatId })
      } else {
        showConversationToast(error.message || '对话加载失败', 'warning')
      }
    }
  },
  { immediate: true }
)

const intentItems = [
  { intent: 'qa_only', label: '只回答问题', sample: '防火墙有什么作用？' },
  { intent: 'generate_topology', label: '生成拓扑图', sample: '根据这些需求画拓扑' },
  { intent: 'modify_topology', label: '修改拓扑图', sample: '给服务器区加一台防火墙' },
  { intent: 'generate_report', label: '生成工程材料', sample: '生成 Word 方案内容' }
]

function analyzeRequirement() {
  requirementState.value = '重新分析完成'
}

function openCreateProjectModal() {
  newProjectName.value = ''
  projectCreateMode.value = 'manual'
  projectCreateOpen.value = true
}

async function persistProject(mode = projectCreateMode.value) {
  const name = newProjectName.value.trim()
  if (!name || projectCreating.value) return
  projectCreating.value = true
  try {
    const project = await createProject({
      name,
      type: 'topo_design',
      color: selectedProjectColor.value,
      creationMode: mode === 'ai' ? 'ai' : 'manual',
      description: mode === 'ai' ? requirementText.value.trim() : '',
      clientRequestId: globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID() : ('project-' + Date.now() + '-' + Math.random().toString(36).slice(2))
    })
    newProjectName.value = ''
    projectCreateOpen.value = false
    emit('project-created', project)
    showConversationToast(mode === 'ai' ? '项目已创建，正在打开 AI 设计' : '项目已创建，正在打开拓扑编辑器')
  } catch (error) {
    showConversationToast(error.message || '项目创建失败', 'warning')
  } finally {
    projectCreating.value = false
  }
}

function openProjectMenu(projectId) {
  projectMenuId.value = projectMenuId.value === projectId ? null : projectId
}

function projectErrorMessage(error, fallback) {
  if (error?.status === 403 || error?.code === 'PROJECT_FORBIDDEN') return '无权操作该项目'
  if (error?.code === 'PROJECT_NOT_FOUND' || error?.status === 404) return '项目不存在或已被删除'
  return error?.message || fallback
}

function openProjectRename(project) {
  projectMenuId.value = null
  projectRenameTarget.value = project
  projectRenameDraft.value = project.name || ''
  projectActionError.value = ''
  projectRenameOpen.value = true
}

async function saveProjectRename() {
  const project = projectRenameTarget.value
  const name = projectRenameDraft.value.trim()
  if (!name) { projectActionError.value = '项目名称不能为空'; return }
  if (name.length > 50) { projectActionError.value = '项目名称不能超过50个字符'; return }
  projectAction.value = `rename:${projectKey(project)}`
  projectActionError.value = ''
  try {
    const updated = await updateProject(projectKey(project), { name })
    emit('project-updated', updated)
    projectRenameOpen.value = false
    projectRenameTarget.value = null
    showConversationToast('项目名称已更新')
  } catch (error) {
    projectActionError.value = projectErrorMessage(error, '项目名称更新失败')
  } finally {
    projectAction.value = ''
  }
}

async function duplicateProjectFromMenu(project) {
  projectMenuId.value = null
  if (projectAction.value) return
  projectAction.value = `duplicate:${projectKey(project)}`
  projectActionError.value = ''
  showConversationToast('正在复制项目…')
  try {
    const copied = await duplicateProject(projectKey(project))
    emit('project-duplicated', copied)
    showConversationToast('项目复制成功')
  } catch (error) {
    showConversationToast(projectErrorMessage(error, '项目复制失败'), 'warning')
  } finally {
    projectAction.value = ''
  }
}

function openProjectExport(project) {
  projectMenuId.value = null
  projectExportTarget.value = project
  projectActionError.value = ''
  projectExportOpen.value = true
}

function openProjectDelete(project) {
  projectMenuId.value = null
  projectDeleteTarget.value = project
  projectActionError.value = ''
  projectDeleteOpen.value = true
}

async function confirmProjectDelete() {
  const project = projectDeleteTarget.value
  if (!project || projectAction.value) return
  projectAction.value = `delete:${projectKey(project)}`
  projectActionError.value = ''
  try {
    await deleteProject(projectKey(project))
    emit('project-deleted', projectKey(project))
    projectDeleteOpen.value = false
    projectDeleteTarget.value = null
    showConversationToast('项目已删除')
  } catch (error) {
    projectActionError.value = projectErrorMessage(error, '项目删除失败')
  } finally {
    projectAction.value = ''
  }
}

function downloadProjectBlob(content, type, fileName) {
  const blob = content instanceof Blob ? content : new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function safeProjectFileName(name) {
  return String(name || '网络拓扑项目').replace(/[\\/:*?"<>|]/g, '_').trim() || '网络拓扑项目'
}

function projectExportSvg(project, topology) {
  const nodes = Array.isArray(topology?.nodes) ? topology.nodes : []
  const edges = Array.isArray(topology?.edges) ? topology.edges : []
  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const pad = 48
  const points = nodes.flatMap((node) => [
    { x: Number(node.x) || 0, y: Number(node.y) || 0 },
    { x: (Number(node.x) || 0) + (Number(node.width) || 76), y: (Number(node.y) || 0) + (Number(node.height) || 76) }
  ])
  const minX = points.length ? Math.min(...points.map((point) => point.x)) : 0
  const minY = points.length ? Math.min(...points.map((point) => point.y)) : 0
  const maxX = points.length ? Math.max(...points.map((point) => point.x)) : 640
  const maxY = points.length ? Math.max(...points.map((point) => point.y)) : 360
  const width = Math.max(640, maxX - minX + pad * 2)
  const height = Math.max(360, maxY - minY + pad * 2)
  const tx = pad - minX
  const ty = pad - minY
  const center = (node) => ({ x: (Number(node.x) || 0) + (Number(node.width) || 76) / 2, y: (Number(node.y) || 0) + (Number(node.height) || 76) / 2 })
  const lines = edges.map((edge) => {
    const source = nodeMap.get(edge.source), target = nodeMap.get(edge.target)
    if (!source || !target) return ''
    const a = center(source), b = center(target)
    return `<line x1="${a.x + tx}" y1="${a.y + ty}" x2="${b.x + tx}" y2="${b.y + ty}" />`
  }).join('')
  const shapes = nodes.map((node) => {
    const x = (Number(node.x) || 0) + tx, y = (Number(node.y) || 0) + ty
    const nodeWidth = Number(node.width) || 76, nodeHeight = Number(node.height) || 76
    const label = String(node.name || node.deviceName || node.type || '设备').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
    return `<g><rect x="${x}" y="${y}" width="${nodeWidth}" height="${nodeHeight}" rx="10" /><text x="${x + nodeWidth / 2}" y="${y + nodeHeight + 18}" text-anchor="middle">${label}</text></g>`
  }).join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="100%" height="100%" fill="#ffffff"/><g fill="none" stroke="#94a3b8" stroke-width="2">${lines}</g><g fill="#eef4fb" stroke="#334155" stroke-width="1.5" font-family="Arial, Microsoft YaHei, sans-serif" font-size="12">${shapes}</g><text x="${pad}" y="26" fill="#1e293b" font-family="Arial, Microsoft YaHei, sans-serif" font-size="16" font-weight="700">${String(project.name || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</text></svg>`
}

async function exportProject(project, format) {
  if (projectAction.value) return
  projectAction.value = `export:${projectKey(project)}`
  projectActionError.value = ''
  try {
    const topology = await getTopology(projectKey(project))
    const base = `${safeProjectFileName(project.name)}_拓扑`
    if (format === 'json') {
      const payload = { project: { id: project.id, name: project.name, type: project.type, creationMode: project.creationMode, description: project.description || '', status: project.status || '', topologyVersion: project.topologyVersion || 'V0' }, nodes: topology?.nodes || [], edges: topology?.edges || [], settings: { zoom: topology?.zoom ?? 1, version: topology?.version ?? 0 } }
      downloadProjectBlob(JSON.stringify(payload, null, 2), 'application/json;charset=utf-8', `${safeProjectFileName(project.name)}_项目数据.json`)
    } else {
      const svg = projectExportSvg(project, topology)
      if (format === 'svg') downloadProjectBlob(svg, 'image/svg+xml;charset=utf-8', `${base}.svg`)
      if (format === 'png') {
        const image = new Image()
        const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }))
        await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = reject; image.src = url })
        URL.revokeObjectURL(url)
        const canvas = document.createElement('canvas')
        canvas.width = image.width * 2; canvas.height = image.height * 2
        const context = canvas.getContext('2d'); context.scale(2, 2); context.drawImage(image, 0, 0)
        const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
        if (!blob) throw new Error('PNG导出失败')
        downloadProjectBlob(blob, 'image/png', `${base}.png`)
      }
    }
    projectExportOpen.value = false
    showConversationToast(`已导出 ${format.toUpperCase()} 文件`)
  } catch (error) {
    projectActionError.value = projectErrorMessage(error, '项目导出失败')
  } finally {
    projectAction.value = ''
  }
}

function createMessage(role, content, details = {}) {
  const message = { id: `message-${messageSequence}`, role, content, ...details }
  messageSequence += 1
  if (role === 'assistant') message.feedback = null
  return message
}

function clearReplyAnimation(state) {
  if (!state) return
  if (state.timerId) window.clearTimeout(state.timerId)
  if (state.rafId && typeof window.cancelAnimationFrame === 'function') window.cancelAnimationFrame(state.rafId)
  state.timerId = 0
  state.rafId = 0
  state.queue.length = 0
}

function releasePendingReply(state) {
  if (!state || state.pendingReleased) return
  state.pendingReleased = true
  pendingReplyCount.value = Math.max(0, pendingReplyCount.value - 1)
}

function finishReplyState(state) {
  if (!state || state.finished || state.queue.length) return
  releasePendingReply(state)
  state.finished = true
  clearReplyAnimation(state)
  const message = state.message
  // The provider's final message is authoritative, but apply it only after
  // every queued streamed character has been rendered. This avoids dropping
  // the tail of a response when the final payload differs from the deltas.
  if (typeof state.finalContent === 'string' && state.finalContent.length > 0 && state.finalContent !== message.content) {
    message.content = state.finalContent
  }
  if (state.cancelled) {
    message.status = 'interrupted'
    message.stage = ''
  } else if (state.terminalStatus) {
    message.status = state.terminalStatus
    message.stage = ''
    if (state.terminalStatus === 'failed' && !message.content) message.content = state.errorText || friendlyAiError(state.errorCode)
  } else if (state.done) {
    message.status = 'complete'
    message.stage = ''
  }
  activeReplies.delete(message)
}

function liveMessageIndex(targetMessages, message) {
  if (!Array.isArray(targetMessages) || !message) return -1
  return targetMessages.findIndex((candidate) => (
    candidate === message
      || (candidate.clientMessageId && message.clientMessageId
        && candidate.clientMessageId === message.clientMessageId)
      || (candidate.id && message.id && String(candidate.id) === String(message.id))
  ))
}

function replaceLiveMessage(targetMessages, message, patch, state = null) {
  const index = liveMessageIndex(targetMessages, message)
  if (index < 0) return message
  const current = targetMessages[index]
  const next = typeof patch === 'function' ? patch(current) : { ...current, ...patch }
  targetMessages.splice(index, 1, next)
  if (state && state.message === message) {
    const previous = state.message
    if (activeReplies.get(previous) === state) {
      activeReplies.delete(previous)
      activeReplies.set(next, state)
    }
    state.message = next
  }
  return next
}

function scheduleReplyPump(state) {
  if (!state || state.finished || state.cancelled) return
  pumpReplyQueue(state)
}

function pumpReplyQueue(state) {
  if (!state || state.finished || state.cancelled) return
  state.timerId = 0
  state.rafId = 0
  if (state.queue.length) {
    // Keep the same visible cadence as the project AI assistant: one Unicode
    // code point per tick. A provider may deliver a large SSE chunk (or the
    // final answer may arrive before the browser finishes painting), so the
    // local queue is deliberately drained one character at a time instead of
    // replacing the message with the completed answer in one render.
    const character = state.queue.shift()
    state.message = replaceLiveMessage(
      state.targetMessages,
      state.message,
      (current) => ({
        ...current,
        content: `${current.content || ''}${character}`,
        status: state.done ? 'draining' : 'streaming'
      }),
      state
    )
    if (state.followTail && threadIsNearBottom()) {
      void scrollThreadToLatest({ behavior: 'auto', force: true })
    } else {
      state.followTail = false
      updateThreadScrollState()
    }
    state.timerId = window.setTimeout(() => pumpReplyQueue(state), 18)
    return
  }
  if (state.done || state.terminalStatus) finishReplyState(state)
}

function enqueueReplyText(state, text) {
  if (!state || state.finished || state.cancelled || typeof text !== 'string' || !text) return
  if (state.followTail === undefined) state.followTail = threadIsNearBottom()
  state.queue.push(...Array.from(text))
  scheduleReplyPump(state)
  if (import.meta.env.DEV) console.debug('[PROVIDER_STREAM_CHUNK]', { contentLength: text.length })
}
function createReplyState(message, targetMessages) {
  const state = {
    message,
    targetMessages,
    queue: [],
    timerId: 0,
    rafId: 0,
    done: false,
    cancelled: false,
    finished: false,
    pendingReleased: false,
    fastDrain: false,
    followTail: true,
    terminalStatus: '',
    errorCode: '',
    errorText: ''
  }
  activeReplies.set(message, state)
  return state
}

function createClientMessageId() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  return `message-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

function messageMatchesPersisted(localMessage, persistedMessage) {
  if (localMessage.clientMessageId && persistedMessage.clientMessageId
      && localMessage.clientMessageId === persistedMessage.clientMessageId) return true
  if (localMessage.id && persistedMessage.id && String(localMessage.id) === String(persistedMessage.id)) return true
  return localMessage.role === persistedMessage.role
    && Boolean(localMessage.content)
    && localMessage.content === persistedMessage.content
}

function hydrateChatMessages(chatId, persistedItems) {
  const localMessages = chatMessages.value[chatId] ?? []
  const consumed = new Set()
  const validPersistedItems = (persistedItems ?? []).filter((message) => (
    (message.role === 'user' || message.role === 'assistant')
    && !['invalid', 'superseded'].includes(String(message.status || '').toLowerCase())
  ))
  const merged = validPersistedItems.map((message) => {
    const localMessage = localMessages.find((candidate) => !consumed.has(candidate) && messageMatchesPersisted(candidate, message))
    if (!localMessage) return { ...message, feedback: null }
    consumed.add(localMessage)
    const localFeedback = localMessage.feedback
    Object.assign(localMessage, message, {
      clientMessageId: message.clientMessageId || localMessage.clientMessageId || null,
      feedback: localFeedback ?? null
    })
    return localMessage
  })

  // Keep local optimistic, streaming and terminal-failure messages that the
  // server response does not contain yet; a stale history response must not
  // make a visible turn disappear.
  localMessages.forEach((message) => {
    if (!consumed.has(message)) merged.push(message)
  })
  localMessages.splice(0, localMessages.length, ...merged)
  chatMessages.value[chatId] = localMessages
}

function stopConversationBackgroundPolling() {
  if (conversationBackgroundPollTimer) window.clearTimeout(conversationBackgroundPollTimer)
  conversationBackgroundPollTimer = 0
  conversationBackgroundPollChatId = null
}

function conversationVisibleMessages(chatId) {
  return (chatMessages.value[chatId] || []).filter((message) => (
    (message.role === 'user' || message.role === 'assistant')
      && !['invalid', 'superseded'].includes(String(message.status || '').toLowerCase())
      && !message.ephemeral
  ))
}

function removeConversationBackgroundMessage(chatId) {
  const current = chatMessages.value[chatId]
  if (!Array.isArray(current)) return
  const next = current.filter((message) => !(message.ephemeral && message.role === 'assistant' && message.status === 'background'))
  if (next.length !== current.length) chatMessages.value[chatId] = next
}

function ensureConversationBackgroundMessage(chatId) {
  const current = chatMessages.value[chatId] || []
  if (current.some((message) => message.ephemeral && message.role === 'assistant' && message.status === 'background')) return
  chatMessages.value[chatId] = [...current, createMessage('assistant', '', {
    status: 'background',
    ephemeral: true,
    attachments: [],
    clientMessageId: `background-${chatId}`
  })]
}

// Messages inserted into a reactive array must be read back from that array
// before a live stream mutates them. This keeps each streamed token visible
// immediately instead of waiting for the final persisted response.
function getLiveMessage(targetMessages, message) {
  if (!Array.isArray(targetMessages) || !message) return message
  return targetMessages.find((candidate) => (
    candidate === message
      || (candidate.clientMessageId && message.clientMessageId
        && candidate.clientMessageId === message.clientMessageId)
      || (candidate.id && message.id && String(candidate.id) === String(message.id))
  )) || message
}

function scheduleConversationBackgroundPolling(chatId) {
  stopConversationBackgroundPolling()
  if (!chatId || props.chatId !== chatId) return
  // Never hydrate history over a browser-owned stream. The server may persist
  // the completed assistant message before the local character queue drains;
  // polling during that window would duplicate the answer and its thinking
  // placeholder. Refreshes still work because activeReplies is empty then.
  if (activeReplies.size > 0 || pendingReplyCount.value > 0) return
  const current = chatMessages.value[chatId] || []
  // A live stream already owns this turn. Poll only when a refresh/navigation
  // left a pending server-side assistant response without a browser consumer.
  if (current.some((message) => (
    message.role === 'assistant' && message.ephemeral && ['waiting', 'streaming', 'draining'].includes(message.status)
  ))) return
  const visible = conversationVisibleMessages(chatId)
  if (visible.at(-1)?.role !== 'user') {
    removeConversationBackgroundMessage(chatId)
    return
  }
  ensureConversationBackgroundMessage(chatId)
  conversationBackgroundPollChatId = chatId
  conversationBackgroundPollTimer = window.setTimeout(async () => {
    try {
      const data = await getConversationMessages(chatId)
      if (props.chatId !== chatId || conversationBackgroundPollChatId !== chatId) return
      if (activeReplies.size > 0 || pendingReplyCount.value > 0) return
      hydrateChatMessages(chatId, data.items ?? [])
      scheduleConversationBackgroundPolling(chatId)
      const refreshed = conversationVisibleMessages(chatId)
      if (refreshed.at(-1)?.role !== 'user') {
        removeConversationBackgroundMessage(chatId)
        return
      }
    } catch {
      // Keep the local transcript visible and retry while the server-side turn runs.
    } finally {
      if (props.chatId === chatId && conversationBackgroundPollChatId === chatId) {
        scheduleConversationBackgroundPolling(chatId)
      }
    }
  }, 1600)
}

function messageKey(message) {
  return message?.clientMessageId || String(message?.id ?? '')
}

async function streamAssistantReply(sessionId, content, userMessage, assistantMessage, targetMessages, clientMessageId = createClientMessageId(), streamOptions = {}) {
  // Always operate on the proxy held by the reactive transcript. Callers may
  // have created a plain object immediately before pushing it into the array.
  userMessage = getLiveMessage(targetMessages, userMessage)
  assistantMessage = getLiveMessage(targetMessages, assistantMessage)
  streamAbortController?.abort()
  const abortController = new AbortController()
  streamAbortController = abortController
  const replyState = createReplyState(assistantMessage, targetMessages)
  let completed = false
  let streamFailed = false
  // A healthy answer can reach the browser just before the SSE connection is
  // closed. In that case the transport has no final `done` event, but the
  // answer is still usable. Keep this separate from an explicit server
  // `error` event so real provider failures continue to be reported.
  let streamEndedAtEof = false
  let userMessageAccepted = false
  let lastSeq = 0
  let generationId = null

  try {
    const streamOptionsPayload = {
      signal: abortController.signal,
      onEvent: ({ event, data }) => {
        if (replyState.cancelled || replyState.finished) return
        // The character pump replaces the assistant entry immutably. Refresh
        // both references before applying transport metadata so later SSE
        // events always target the currently rendered message.
        userMessage = getLiveMessage(targetMessages, userMessage)
        assistantMessage = getLiveMessage(targetMessages, assistantMessage)
        if (event === 'message' && data?.generationId) generationId = String(data.generationId)
        if (event !== 'message' && data?.generationId && generationId && String(data.generationId) !== generationId) return
        if (import.meta.env.DEV && (event === 'token' || event === 'done' || event === 'error')) {
          console.debug('[FRONTEND_PARSED_EVENT]', { event, generationId: data?.generationId || generationId, seq: data?.seq, contentLength: typeof data?.content === 'string' ? data.content.length : 0 })
        }
        if (event === 'message' && data?.userMessage) {
          userMessageAccepted = true
          const localClientMessageId = userMessage.clientMessageId
          Object.assign(userMessage, data.userMessage, {
            clientMessageId: data.userMessage.clientMessageId || localClientMessageId,
            feedback: null,
            status: 'sent'
          })
          if (data?.conversation) emit('chat-updated', data.conversation)
        } else if (event === 'status') {
          assistantMessage.stage = typeof data?.text === 'string' ? data.text : ''
        } else if ((event === 'delta' || event === 'token') && typeof data?.content === 'string') {
          const seq = Number(data?.seq)
          if (Number.isFinite(seq) && seq <= lastSeq) return
          if (Number.isFinite(seq)) lastSeq = seq
          if (data.content) {
            userMessageAccepted = true
            userMessage.status = 'sent'
            assistantMessage.status = 'streaming'
            assistantMessage.stage = ''
            enqueueReplyText(replyState, data.content)
          }
        } else if (event === 'done') {
          userMessageAccepted = true
          userMessage.status = 'sent'
          const finalMessage = data?.assistantMessage
          if (finalMessage) {
            const finalContent = typeof finalMessage.content === 'string' ? finalMessage.content : ''
            const currentContent = assistantMessage.content || ''
            const queuedContent = replyState.queue.join('')
            const assembledContent = currentContent + queuedContent
            // Apply metadata without replacing text already rendered to the user.
            const { content: _content, status: _status, ...metadata } = finalMessage
            const localClientMessageId = assistantMessage.clientMessageId
            Object.assign(assistantMessage, metadata, { feedback: null })
            if (!assistantMessage.clientMessageId && localClientMessageId) assistantMessage.clientMessageId = localClientMessageId
            replyState.finalContent = finalContent
            if (finalContent && !assembledContent) enqueueReplyText(replyState, finalContent)
            else if (finalContent.startsWith(assembledContent)) enqueueReplyText(replyState, finalContent.slice(assembledContent.length))
            else if (finalContent && finalContent !== assembledContent) {
              // Keep the streamed queue intact. If the provider sanitizes the
              // final text, finishReplyState reconciles it after the queue has
              // drained instead of discarding unread characters here.
            }
          }
          replyState.done = true
          replyState.fastDrain = true
          assistantMessage.status = 'draining'
          assistantMessage.stage = ''
          if (data?.conversation) emit('chat-updated', data.conversation)
          completed = true
          releasePendingReply(replyState)
          // Provider generation is finished. Detach the controller now so a
          // new user message cannot interrupt the final UI drain.
          if (streamAbortController === abortController) streamAbortController = null
          if (!replyState.queue.length) finishReplyState(replyState)
          else pumpReplyQueue(replyState)
          void scrollThreadToLatest({ behavior: 'auto' })
        } else if (event === 'error') {
          replyState.terminalStatus = data?.code === 'STREAM_INTERRUPTED' ? 'interrupted' : 'failed'
          replyState.errorCode = data?.code || 'INTERNAL_AI_ERROR'
          replyState.errorText = friendlyAiError(replyState.errorCode, data?.message)
          replyState.fastDrain = true
          assistantMessage.stage = ''
          userMessage.status = userMessageAccepted ? 'sent' : 'failed'
          streamFailed = true
          if (replyState.queue.length) assistantMessage.status = 'draining'
          releasePendingReply(replyState)
          if (isQuotaError(data)) showQuotaLimitNotice(data)
          else if (replyState.terminalStatus === 'failed' && !assistantMessage.content && !replyState.queue.length) assistantMessage.content = replyState.errorText
          if (!replyState.queue.length) finishReplyState(replyState)
          else pumpReplyQueue(replyState)
        } else if (event === 'eof') {
          const hasAnswer = Boolean(assistantMessage.content || replyState.queue.length || replyState.finalContent)
          if (!replyState.terminalStatus && hasAnswer) {
            streamEndedAtEof = true
            userMessageAccepted = true
            userMessage.status = 'sent'
            replyState.done = true
            replyState.fastDrain = true
            assistantMessage.status = replyState.queue.length ? 'draining' : 'complete'
            assistantMessage.stage = ''
            releasePendingReply(replyState)
            if (!replyState.queue.length) finishReplyState(replyState)
            else pumpReplyQueue(replyState)
          }
        }
      }
    }
    if (streamOptions.messageId) {
      await streamRegenerateConversation(sessionId, streamOptions.messageId, null, clientMessageId, streamOptionsPayload)
    } else {
      await streamConversation(sessionId, content, null, clientMessageId, streamOptionsPayload)
    }
    if (!completed && !streamFailed && !streamEndedAtEof && !replyState.cancelled) {
      replyState.terminalStatus = 'failed'
      replyState.errorCode = 'STREAM_INTERRUPTED'
      replyState.errorText = friendlyAiError('STREAM_INTERRUPTED')
      replyState.fastDrain = true
      if (userMessageAccepted) userMessage.status = 'sent'
      if (replyState.queue.length) assistantMessage.status = 'draining'
      releasePendingReply(replyState)
      if (!replyState.queue.length) finishReplyState(replyState)
      else pumpReplyQueue(replyState)
      showConversationToast(replyState.errorText, 'warning')
    }
  } catch (error) {
    if (error?.name === 'AbortError') {
      if (!replyState.cancelled) {
        replyState.cancelled = true
        clearReplyAnimation(replyState)
        assistantMessage.status = 'interrupted'
        assistantMessage.stage = ''
        releasePendingReply(replyState)
        activeReplies.delete(assistantMessage)
      }
      userMessage.status = userMessageAccepted ? 'sent' : 'failed'
      return
    }
    replyState.terminalStatus = 'failed'
    replyState.errorCode = error?.code || 'NETWORK_ERROR'
    replyState.errorText = friendlyAiError(replyState.errorCode, error?.message)
    replyState.fastDrain = true
    assistantMessage.stage = ''
    if (userMessageAccepted) userMessage.status = 'sent'
    if (replyState.queue.length) assistantMessage.status = 'draining'
    releasePendingReply(replyState)
    if (!replyState.queue.length) {
      if (!assistantMessage.content) assistantMessage.content = replyState.errorText
      finishReplyState(replyState)
    } else pumpReplyQueue(replyState)
    throw error
  } finally {
    if (streamAbortController === abortController) streamAbortController = null
  }
}
async function sendMessage(message) {
  const content = message?.trim()
  if (!content) return
  if (conversationAccessError.value) {
    showConversationToast('该会话不存在或无法访问', 'warning')
    return
  }

  pendingReplyCount.value += 1
  let userMessage = createMessage('user', content, { status: 'sending' })
  userMessage.clientMessageId = createClientMessageId()
  let sessionId = props.chatId
  let targetMessages = sessionId
    ? (chatMessages.value[sessionId] ?? [])
    : newChatMessages.value
  if (sessionId) {
    targetMessages.push(userMessage)
    chatMessages.value[sessionId] = targetMessages
    targetMessages = chatMessages.value[sessionId] || targetMessages
  } else {
    targetMessages.push(userMessage)
    newChatMessages.value = targetMessages
    targetMessages = newChatMessages.value
  }
  userMessage = getLiveMessage(targetMessages, userMessage)
  chatInput.value = ''
  void scrollThreadToLatest({ behavior: 'auto', force: true })

  let streamStarted = false
  try {
    if (!sessionId) {
      if (!conversationCreationPromise) {
        conversationCreationPromise = createConversation({ title: '新对话' })
          .finally(() => { conversationCreationPromise = null })
      }
      const session = await conversationCreationPromise
      sessionId = session.publicId || session.token || session.conversationToken || session.id
      if (!sessionId) throw new Error('会话创建失败，请重试。')
      if (!chatMessages.value[sessionId]) {
        chatMessages.value[sessionId] = targetMessages
        if (newChatMessages.value === targetMessages) newChatMessages.value = []
        pendingChatHandoffId = sessionId
        emit('chat-created', session)
      }
    }
    targetMessages = chatMessages.value[sessionId] ?? targetMessages
    let assistantMessage = createMessage('assistant', '', {
      status: 'waiting',
      attachments: [],
      clientMessageId: `${userMessage.clientMessageId}:assistant`
    })
    targetMessages.push(assistantMessage)
    chatMessages.value[sessionId] = targetMessages
    assistantMessage = getLiveMessage(targetMessages, assistantMessage)
    streamStarted = true
    await streamAssistantReply(sessionId, content, userMessage, assistantMessage, targetMessages, userMessage.clientMessageId)
    await scrollThreadToLatest({ behavior: 'auto', force: true })
  } catch (error) {
    if (error?.name !== 'AbortError') {
      // A provider/SSE failure happens after the server has accepted the user
      // message. Keep that turn marked as sent; only conversation creation or
      // the initial message handshake can leave it in the sending state.
      if (userMessage.status === 'sending') userMessage.status = 'failed'
      if (isQuotaError(error)) showQuotaLimitNotice(error)
      else showConversationToast(error.message || '消息发送失败', 'warning')
    }
  } finally {
    if (!streamStarted) pendingReplyCount.value = Math.max(0, pendingReplyCount.value - 1)
  }
}

function retryFailedMessage(message) {
  const messages = dialogueMessages.value
  const index = messages.indexOf(message)
  if (index !== -1) messages.splice(index, 1)
  void sendMessage(message.content)
}

function usePrompt(prompt) {
  chatInput.value = prompt
}

function closeFloatingMenus() {
  conversationMenuOpen.value = false
}

function closeConversationOverlays() {
  closeFloatingMenus()
  isConversationTitleEditing.value = false
  conversationTitleDraft.value = ''
}

function isQuotaError(error) {
  return ['QUOTA_EXCEEDED', 'TOKEN_LIMIT_EXCEEDED', 'AI_CALL_QUOTA_EXCEEDED', 'TOKEN_QUOTA_EXCEEDED', 'TOPOLOGY_QUOTA_EXCEEDED', 'KNOWLEDGE_AI_QUOTA_EXCEEDED', 'NETWORK_PLAN_QUOTA_EXCEEDED'].includes(error?.code)
}

function friendlyAiError(code, fallback = '') {
  return {
    AUTH_REQUIRED: '登录状态已失效，请重新登录。',
    TOKEN_EXPIRED: '登录状态已失效，请重新登录。',
    CONVERSATION_NOT_FOUND: '该会话不存在或无法访问。',
    RESOURCE_NOT_AVAILABLE: '该会话不存在或无法访问。',
    QUOTA_EXCEEDED: '今日 AI 使用额度已用完。',
    TOKEN_QUOTA_EXCEEDED: '今日 AI 使用额度已用完。',
    MODEL_UNAVAILABLE: '当前暂无可用 AI 模型，请稍后再试。',
    MODEL_DISABLED: '当前 AI 模型已停用，请稍后再试。',
    MODEL_ROUTE_NOT_FOUND: 'AI 服务配置异常，请联系管理员。',
    MODEL_NOT_AVAILABLE: '当前暂无可用 AI 模型，请稍后再试。',
    MODEL_TYPE_INCOMPATIBLE: '当前暂无可用 AI 模型，请稍后再试。',
    API_KEY_NOT_CONFIGURED: 'AI 服务配置异常，请联系管理员。',
    PROVIDER_AUTH_FAILED: '管理端模型 API Key 无效或未配置，请重新保存后再测试。',
    AUTHENTICATION_ERROR: '管理端模型 API Key 无效或未配置，请重新保存后再测试。',
    API_KEY_NOT_CONFIGURED: '管理端模型 API Key 无效或未配置，请重新保存后再测试。',
    PROVIDER_TIMEOUT: 'AI 响应超时，请重新尝试。',
    PROVIDER_RATE_LIMIT: 'AI 服务当前请求较多，请稍后重试。',
    PROVIDER_UNAVAILABLE: 'AI 服务暂时无法连接，请稍后重试。',
    PROVIDER_CONTENT_INVALID: '当前模型返回内容异常，备用模型也未返回有效内容，请稍后重试。',
    PROVIDER_CONTENT_ANOMALY: '当前模型返回内容异常，备用模型也未返回有效内容，请稍后重试。',
    PROVIDER_PROTOCOL_ERROR: '当前模型返回内容格式异常，备用模型也未返回有效内容，请稍后重试。',
    RAG_FAILED: '知识库服务暂时不可用，已跳过知识库检索。',
    PROMPT_UNAVAILABLE: '当前 AI 任务尚未配置 Prompt，请联系管理员。',
    PROMPT_DISABLED: '当前 AI 任务 Prompt 已停用，请联系管理员。',
    PROMPT_NOT_FOUND: '当前 AI 任务尚未配置 Prompt，请联系管理员。',
    STREAM_INTERRUPTED: 'AI 流式响应中断，请重新生成。',
    NETWORK_ERROR: '网络连接异常，请检查网络后重试。',
    INTERNAL_AI_ERROR: 'AI 服务暂时不可用，请稍后重试。'
  }[code] || fallback || 'AI 服务暂时不可用，请稍后重试。'
}

function formatQuotaReset(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

function quotaToastDetails(error, subscription = null) {
  const code = error?.code
  const details = error?.details ?? {}
  const key = ['TOKEN_LIMIT_EXCEEDED', 'TOKEN_QUOTA_EXCEEDED'].includes(code) ? 'tokens'
    : code === 'TOPOLOGY_QUOTA_EXCEEDED' ? 'topologyGeneration'
      : code === 'KNOWLEDGE_AI_QUOTA_EXCEEDED' ? 'knowledgeQa'
        : code === 'NETWORK_PLAN_QUOTA_EXCEEDED' ? 'networkPlan' : 'aiChat'
  const quota = subscription?.quota?.[key] ?? {}
  const usage = subscription?.usage?.[key] ?? {}
  const remaining = subscription?.remaining?.[key] ?? {}
  const total = Number(details.total ?? quota.total ?? 0)
  const used = Number(details.used ?? usage.used ?? 0)
  return {
    code,
    planName: details.planName || subscription?.planName || '-',
    level: details.planLevel || subscription?.level || '',
    label: details.quotaLabel || quota.label || (['TOKEN_LIMIT_EXCEEDED', 'TOKEN_QUOTA_EXCEEDED'].includes(code) ? 'Token' : 'AI 调用'),
    used,
    total,
    remaining: Number(details.remaining ?? remaining.value ?? Math.max(0, total - used)),
    estimatedInputTokens: Number(details.estimatedInputTokens ?? 0),
    nextResetAt: details.nextResetAt || subscription?.nextResetAt || ''
  }
}

function showQuotaLimitNotice(error) {
  const toastId = showConversationToast(error?.message || '本周期额度不足', 'warning', quotaToastDetails(error))
  void getUserSubscription().then((subscription) => {
    if (conversationToast.value?.id !== toastId) return
    conversationToast.value.quota = quotaToastDetails(error, subscription)
  }).catch(() => {
    // The server already supplied the actionable quota error; loading details is best-effort.
  })
}

function openProfileModal(modal) {
  window.dispatchEvent(new CustomEvent('topoplan-open-profile-modal', { detail: { modal } }))
  conversationToast.value = null
}

function showConversationToast(message, tone = 'default', quota = null) {
  if (conversationToastTimer) window.clearTimeout(conversationToastTimer)
  const toastId = ++conversationToastSequence
  conversationToast.value = { id: toastId, message, tone, quota }
  conversationToastTimer = window.setTimeout(() => {
    conversationToast.value = null
    conversationToastTimer = null
  }, 3200)
  return toastId
}

function toggleConversationMenu(event) {
  event.stopPropagation()
  if (conversationMenuOpen.value) {
    closeFloatingMenus()
    return
  }

  const triggerRect = event.currentTarget.getBoundingClientRect()
  const menuWidth = 244
  conversationMenuPosition.value = {
    top: Math.max(12, Math.min(triggerRect.bottom + 8, window.innerHeight - 334)),
    left: Math.max(12, Math.min(triggerRect.right - menuWidth, window.innerWidth - menuWidth - 12))
  }
  conversationMenuOpen.value = true
}

function hasPersistedConversation() {
  if (currentConversation.value.id !== null) return true
  showConversationToast('对话将在发送第一条消息后自动保存', 'warning')
  return false
}

function beginConversationRename() {
  conversationTitleDraft.value = activeConversationTitle.value
  isConversationTitleEditing.value = true
  closeFloatingMenus()
  nextTick(() => {
    conversationTitleInput.value?.focus()
    conversationTitleInput.value?.select()
  })
}

function saveConversationTitle() {
  const nextTitle = conversationTitleDraft.value.trim()
  if (nextTitle && nextTitle !== activeConversationTitle.value) {
    if (currentConversation.value.id === null) {
      emit('rename-new-chat', nextTitle)
    } else {
      emit('rename-chat', { chatId: currentConversation.value.id, title: nextTitle })
    }
    showConversationToast('对话名称已更新')
  }
  isConversationTitleEditing.value = false
}

function cancelConversationRename() {
  isConversationTitleEditing.value = false
  conversationTitleDraft.value = ''
}

function toggleConversationPin() {
  if (!hasPersistedConversation()) return
  emit('toggle-chat-pin', currentConversation.value.id)
  closeFloatingMenus()
  showConversationToast(props.chatPinned ? '已取消固定该对话' : '已固定到最近对话顶部')
}

function downloadBlob(content, type, fileName) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function downloadConversationFile(file) {
  const content = `${file.name}\n\n来自 TopoPlan 对话：${activeConversationTitle.value}`
  downloadBlob(content, 'text/plain;charset=utf-8', file.name)
  showConversationToast(`已下载 ${file.name}`)
}

function previewConversationFile(file) {
  showConversationToast(`正在预览 ${file.name}`)
}

function wrapPdfLine(text, maxLength = 28) {
  const characters = Array.from(text)
  const lines = []
  for (let index = 0; index < characters.length; index += maxLength) {
    lines.push(characters.slice(index, index + maxLength).join(''))
  }
  return lines.length ? lines : ['']
}

function toPdfHex(text) {
  return Array.from(text)
    .map((character) => character.charCodeAt(0).toString(16).padStart(4, '0'))
    .join('')
}

function createConversationPdf() {
  const exportedLines = [
    activeConversationTitle.value,
    `导出时间：${new Date().toLocaleString('zh-CN')}`,
    '',
    ...dialogueMessages.value.flatMap((message) => [
      `${message.role === 'user' ? '用户' : 'TopoPlan'}：`,
      ...wrapPdfLine(message.content),
      ''
    ])
  ].slice(0, 42)
  const stream = exportedLines
    .map((line, index) => `BT /F1 ${index === 0 ? 16 : 11} Tf 48 ${800 - (index * 18)} Td <${toPdfHex(line)}> Tj ET`)
    .join('\n')
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
    '<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H /DescendantFonts [6 0 R] >>',
    '<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 2 >> >>'
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, index) => {
    offsets.push(pdf.length)
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`
  })
  const xrefOffset = pdf.length
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  offsets.slice(1).forEach((offset) => {
    pdf += `${String(offset).padStart(10, '0')} 00000 n \n`
  })
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`
  return pdf
}

function downloadConversationPdf() {
  if (!hasPersistedConversation()) return
  downloadBlob(createConversationPdf(), 'application/pdf', `${activeConversationTitle.value}.pdf`)
  closeFloatingMenus()
  showConversationToast('对话 PDF 已开始下载')
}

function handleConversationAction(action) {
  switch (action) {
    case 'pin':
      toggleConversationPin()
      break
    case 'rename':
      beginConversationRename()
      break
    case 'pdf':
      downloadConversationPdf()
      break
  }
}

function toggleMessageFeedback(message, feedback) {
  message.feedback = message.feedback === feedback ? null : feedback
  if (feedback === 'down' && message.feedback === 'down') {
    feedbackMenuMessageId.value = message.id
    activeMessageMenu.value = null
  } else {
    feedbackMenuMessageId.value = null
  }
}

function selectFeedbackReason(message, reason) {
  message.feedbackReason = reason
  feedbackMenuMessageId.value = null
  showConversationToast('感谢你的反馈')
}

async function copyMessage(message) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(message.content)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = message.content
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      textarea.remove()
    }
  } catch {
    // The visual confirmation is retained when browser clipboard permission is unavailable.
  }

  copiedMessageId.value = message.id
  activeMessageMenu.value = null
  feedbackMenuMessageId.value = null
  if (copiedNoticeTimer) window.clearTimeout(copiedNoticeTimer)
  copiedNoticeTimer = window.setTimeout(() => {
    copiedMessageId.value = null
    copiedNoticeTimer = null
  }, 1600)
}

async function regenerateMessage(message) {
  const index = dialogueMessages.value.findIndex((candidate) => candidate.id === message.id)
  const originalUserIndex = dialogueMessages.value.slice(0, index).map((candidate) => candidate.role).lastIndexOf('user')
  const sessionId = props.chatId
  const persistedMessageId = Number(message.id)
  if (index < 0 || originalUserIndex < 0 || !sessionId || !Number.isSafeInteger(persistedMessageId) || pendingReplyCount.value) return

  const targetMessages = dialogueMessages.value
  const originalUserMessage = targetMessages[originalUserIndex]
  const content = originalUserMessage.content
  const replacementUser = createMessage('user', content, { status: 'sending' })
  replacementUser.clientMessageId = createClientMessageId()
  const replacementAssistant = createMessage('assistant', '', { status: 'waiting', attachments: [], clientMessageId: `${replacementUser.clientMessageId}:assistant` })
  targetMessages.splice(originalUserIndex, targetMessages.length - originalUserIndex, replacementUser, replacementAssistant)
  const liveReplacementUser = getLiveMessage(targetMessages, replacementUser)
  const liveReplacementAssistant = getLiveMessage(targetMessages, replacementAssistant)
  activeMessageMenu.value = null
  feedbackMenuMessageId.value = null
  pendingReplyCount.value += 1
  void scrollThreadToLatest({ behavior: 'auto', force: true })
  let streamStarted = false
  try {
    streamStarted = true
    await streamAssistantReply(sessionId, content, liveReplacementUser, liveReplacementAssistant, targetMessages,
      liveReplacementUser.clientMessageId, { messageId: persistedMessageId })
    await scrollThreadToLatest({ behavior: 'auto', force: true })
  } catch (error) {
    if (error?.name !== 'AbortError') showConversationToast(error.message || '重新生成失败', 'warning')
  } finally {
    if (!streamStarted) pendingReplyCount.value = Math.max(0, pendingReplyCount.value - 1)
  }
  regeneratedMessageId.value = replacementAssistant.id
  if (regeneratedNoticeTimer) window.clearTimeout(regeneratedNoticeTimer)
  regeneratedNoticeTimer = window.setTimeout(() => {
    regeneratedMessageId.value = null
    regeneratedNoticeTimer = null
  }, 1600)
}
function toggleMessageMenu(messageId) {
  activeMessageMenu.value = activeMessageMenu.value === messageId ? null : messageId
  feedbackMenuMessageId.value = null
}

async function startEditingMessage(message) {
  editingMessageId.value = message.id
  editingMessageDraft.value = message.content
  activeMessageMenu.value = null
  await nextTick()
  resizeEditingMessageInput()
  editingMessageInput.value?.focus()
}

function resizeEditingMessageInput() {
  const input = editingMessageInput.value
  if (!input) return

  input.style.height = '0px'
  input.style.height = `${Math.max(input.scrollHeight, 48)}px`
}

function cancelEditingMessage() {
  editingMessageId.value = null
  editingMessageDraft.value = ''
}

async function resubmitEditedMessage(message) {
  const nextContent = editingMessageDraft.value.trim()
  if (!nextContent) return
  const index = dialogueMessages.value.findIndex((candidate) => candidate.id === message.id)
  const sessionId = props.chatId
  const persistedMessageId = Number(message.id)
  const hasFollowingMessages = index >= 0 && index < dialogueMessages.value.length - 1
  if (hasFollowingMessages && !window.confirm('编辑此消息后，将从这里重新生成后续回答。')) return

  if (sessionId && Number.isSafeInteger(persistedMessageId)) {
    pendingReplyCount.value += 1
    let streamStarted = false
    try {
      await editConversationMessage(sessionId, persistedMessageId, nextContent)
      const targetMessages = dialogueMessages.value
      const replacementUser = createMessage('user', nextContent, { status: 'sending' })
      replacementUser.clientMessageId = createClientMessageId()
      const replacementAssistant = createMessage('assistant', '', { status: 'waiting', attachments: [], clientMessageId: `${replacementUser.clientMessageId}:assistant` })
      if (index >= 0) targetMessages.splice(index, targetMessages.length - index, replacementUser, replacementAssistant)
      const liveReplacementUser = getLiveMessage(targetMessages, replacementUser)
      const liveReplacementAssistant = getLiveMessage(targetMessages, replacementAssistant)
      cancelEditingMessage()
      streamStarted = true
      await streamAssistantReply(sessionId, nextContent, liveReplacementUser, liveReplacementAssistant, targetMessages,
        liveReplacementUser.clientMessageId, { messageId: persistedMessageId })
      await scrollThreadToLatest()
    } catch (error) {
      if (error?.name !== 'AbortError') showConversationToast(error.message || '编辑消息失败', 'warning')
    } finally {
      if (!streamStarted) pendingReplyCount.value = Math.max(0, pendingReplyCount.value - 1)
    }
    return
  }

  cancelEditingMessage()
  await sendMessage(nextContent)
}
async function quoteMessage(message) {
  const quotedContent = message.content
    .split(/\r?\n/)
    .map((line) => `> ${line}`)
    .join('\n')

  chatInput.value = `${quotedContent}\n\n`
  activeMessageMenu.value = null
  await nextTick()
  chatComposer.value?.focusInput()
}

function deleteMessageTurn(message) {
  const messageIndex = dialogueMessages.value.findIndex((candidate) => candidate.id === message.id)
  if (messageIndex === -1) return

  let turnStartIndex = messageIndex
  for (let index = messageIndex - 1; index >= 0; index -= 1) {
    if (dialogueMessages.value[index].role === 'user') {
      turnStartIndex = index
      break
    }
  }

  dialogueMessages.value.splice(turnStartIndex, messageIndex - turnStartIndex + 1)
  activeMessageMenu.value = null
  feedbackMenuMessageId.value = null
}

function handleDocumentPointerDown(event) {
  const target = event.target
  if (!target.closest('.project-card-menu-wrap')) projectMenuId.value = null
  if (!conversationMenuRef.value?.contains(target) && !target.closest('.conversation-popover')) closeFloatingMenus()
  if (!target.closest('.message-actions')) activeMessageMenu.value = null
  if (!target.closest('.feedback-popover')) feedbackMenuMessageId.value = null
}

function handleDocumentKeydown(event) {
  if (editingMessageId.value && (event.metaKey || event.ctrlKey) && event.key === 'Enter') {
    event.preventDefault()
    document.querySelector('.user-message-editor')?.requestSubmit()
    return
  }

  if (editingMessageId.value && event.key === 'Escape') {
    event.preventDefault()
    cancelEditingMessage()
    return
  }

  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    void focusConversationSearch()
    return
  }

  if (event.key === 'Escape') {
    projectMenuId.value = null
    projectRenameOpen.value = false
    projectDeleteOpen.value = false
    projectExportOpen.value = false
    closeConversationOverlays()
    activeMessageMenu.value = null
    feedbackMenuMessageId.value = null
  }
}

function cancelReply() {
  streamAbortController?.abort()
  streamAbortController = null
  replyTimers.forEach((timerId) => {
    window.clearTimeout(timerId)
    window.clearInterval(timerId)
  })
  replyTimers = new Set()
  activeReplies.forEach((state, reply) => {
    if (state.done || state.terminalStatus) return
    state.cancelled = true
    clearReplyAnimation(state)
    reply.stage = ''
    if (reply.status === 'streaming') reply.status = 'interrupted'
  })
  activeReplies.clear()
  pendingReplyCount.value = 0
}
function threadIsNearBottom(thread = chatThread.value) {
  if (!thread) return true
  return thread.scrollHeight - thread.scrollTop - thread.clientHeight <= 56
}

function updateThreadScrollState(event) {
  const thread = event?.currentTarget || chatThread.value
  if (!thread) return
  threadAtBottom.value = threadIsNearBottom(thread)
  threadHasOverflow.value = thread.scrollHeight > thread.clientHeight + 8
  activeReplies.forEach((state) => {
    state.followTail = threadAtBottom.value
  })
}

async function scrollThreadToLatest({ behavior = 'smooth', force = false, settle = false } = {}) {
  await nextTick()
  const thread = chatThread.value
  if (!thread) return
  if (!force && !threadIsNearBottom(thread)) {
    updateThreadScrollState()
    return
  }
  const scrollTop = Math.max(0, thread.scrollHeight - thread.clientHeight)
  if (behavior === 'auto') thread.scrollTop = scrollTop
  else thread.scrollTo({ top: scrollTop, behavior })
  threadAtBottom.value = true
  threadHasOverflow.value = thread.scrollHeight > thread.clientHeight + 8
  if (settle) {
    requestAnimationFrame(() => {
      if (chatThread.value !== thread) return
      thread.scrollTop = Math.max(0, thread.scrollHeight - thread.clientHeight)
      updateThreadScrollState()
    })
  }
}

async function scrollConversationToLatest(chatId, loadVersion) {
  await nextTick()
  if (props.chatId !== chatId || conversationLoadVersions.get(chatId) !== loadVersion) return
  await scrollThreadToLatest({ behavior: 'auto', force: true, settle: true })
  // A transition may mount the new thread one frame after its messages are
  // hydrated. Re-run once the thread is definitely visible so opening a saved
  // conversation always lands on its newest turn.
  requestAnimationFrame(() => {
    if (props.chatId !== chatId || conversationLoadVersions.get(chatId) !== loadVersion) return
    void scrollThreadToLatest({ behavior: 'auto', force: true, settle: true })
  })
}

function scrollToLatestMessage() {
  activeReplies.forEach((state) => {
    state.followTail = true
  })
  // Keep the user-visible jump animated. The settle pass is intentionally
  // disabled here because it would overwrite the browser's smooth scroll on
  // the next animation frame.
  void scrollThreadToLatest({ behavior: 'smooth', force: true, settle: false })
}

function scrollActiveThreadAfterEnter() {
  if (!props.chatId) return
  void scrollThreadToLatest({ behavior: 'auto', force: true, settle: true })
}

function setMessageElement(messageId, element) {
  if (element) messageElements.set(messageId, element)
  else messageElements.delete(messageId)
}

async function scrollMessageIntoView(messageId) {
  await nextTick()
  messageElements.get(messageId)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
    inline: 'nearest'
  })
}

async function alignReplyAnchor(reply) {
  if (!reply.anchorMessageId || reply.anchorAlignmentLocked || reply.anchorAlignmentPending) return
  reply.anchorAlignmentPending = true
  await nextTick()
  reply.anchorAlignmentPending = false
  if (!activeReplies.has(reply)) return

  const thread = chatThread.value
  if (!thread || thread.scrollHeight <= thread.clientHeight + 12) return

  const anchor = messageElements.get(reply.anchorMessageId)
  if (!anchor) return

  const anchorOffset = anchor.getBoundingClientRect().top - thread.getBoundingClientRect().top
  if (anchorOffset <= 48) {
    reply.anchorAlignmentLocked = true
    return
  }

  const targetTop = Math.max(0, thread.scrollTop + anchorOffset - 32)
  const maxScrollTop = thread.scrollHeight - thread.clientHeight
  if (maxScrollTop < targetTop) return

  reply.anchorAlignmentLocked = true

  anchor.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
    inline: 'nearest'
  })

  // Nested scrolling can stop scrollIntoView early; finish at the thread's safe top offset.
  thread.scrollTo({
    top: targetTop,
    behavior: 'smooth'
  })
}

async function alignCompletedReplyAnchor(reply) {
  if (!reply.anchorMessageId) return

  await nextTick()

  const thread = chatThread.value
  const anchor = messageElements.get(reply.anchorMessageId)
  if (!thread || !anchor || thread.scrollHeight <= thread.clientHeight + 12) return

  const anchorOffset = anchor.getBoundingClientRect().top - thread.getBoundingClientRect().top
  const targetTop = Math.min(
    thread.scrollHeight - thread.clientHeight,
    Math.max(0, thread.scrollTop + anchorOffset - 32)
  )

  if (Math.abs(thread.scrollTop - targetTop) < 2) return

  thread.scrollTo({
    top: targetTop,
    behavior: 'smooth'
  })
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
  cancelReply()
  stopConversationBackgroundPolling()
  if (copiedNoticeTimer) window.clearTimeout(copiedNoticeTimer)
  if (regeneratedNoticeTimer) window.clearTimeout(regeneratedNoticeTimer)
  if (conversationToastTimer) window.clearTimeout(conversationToastTimer)
})
</script>

<template>
  <div class="workspace">
    <template v-if="activePage === 'chat-search'">
      <section class="chat-search-surface">
        <div class="chat-search-panel">
          <div class="conversation-search-field" role="search">
            <Search class="conversation-search-icon" :size="21" aria-hidden="true" />
            <input
              ref="conversationSearchInput"
              v-model="conversationSearchTerm"
              type="search"
              placeholder="搜索对话内容"
              aria-label="搜索对话内容"
              autofocus
            />
            <div class="conversation-search-actions">
              <kbd class="conversation-search-shortcut" title="按 Ctrl 或 Command 加 K 聚焦搜索">Ctrl K</kbd>
              <button
                v-if="conversationSearchTerm"
                class="conversation-search-clear"
                type="button"
                aria-label="清除搜索"
                title="清除搜索"
                @click="clearConversationSearch"
              >
                <X :size="16" />
              </button>
            </div>
          </div>

          <div class="conversation-search-results">
            <header class="conversation-search-results-header">
              <h1>近期对话</h1>
              <span v-if="conversationSearchTerm" class="conversation-search-count">{{ filteredConversations.length }} 条匹配</span>
            </header>
            <div v-if="filteredConversations.length" class="conversation-search-list">
              <button
                v-for="conversation in filteredConversations"
                :key="conversation.id"
                class="conversation-search-result"
                type="button"
                @click="emit('open-chat', conversation.id)"
              >
                <span class="conversation-search-result-main">
                  <MessageSquare class="conversation-search-result-icon" :size="17" aria-hidden="true" />
                  <span class="conversation-search-result-title">
                    <template v-for="(part, index) in highlightedTitleParts(conversation.title)" :key="`${conversation.id}-${index}`">
                      <mark v-if="part.matches">{{ part.text }}</mark>
                      <template v-else>{{ part.text }}</template>
                    </template>
                  </span>
                </span>
                <time>{{ conversation.time }}</time>
              </button>
            </div>
            <div v-else class="conversation-search-empty" role="status" aria-live="polite">
              <span class="conversation-search-empty-icon"><SearchX :size="22" /></span>
              <strong>未找到相关的对话记录</strong>
              <p>尝试使用其他关键词搜索。</p>
            </div>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="activePage === 'projects'">
      <section class="project-hub">
        <header class="project-hub-header">
          <div>
            <h2>我的项目</h2>
            <p>创建、管理并继续编辑你的网络拓扑规划。</p>
          </div>
          <button type="button" class="project-hub-create" @click="openCreateProjectModal"><Plus :size="17" /> 新建项目</button>
        </header>
        <div class="project-card-grid">
          <button type="button" class="project-new-card" @click="openCreateProjectModal">
            <span><Plus :size="24" /></span>
            <strong>新建网络项目</strong>
            <small>从空白拓扑开始，或交给 AI 生成第一版方案。</small>
          </button>

          <article v-for="item in projectCards" :key="item.id" class="project-card" @click="emit('open-project', item)">
            <div class="project-card-preview" :class="{ empty: !item.topologyPreview?.nodes.length }" aria-label="项目拓扑缩略图">
              <svg v-if="item.topologyPreview?.nodes.length" viewBox="0 0 320 128" role="img" :aria-label="`${item.name}完整拓扑缩略图`">
                <line v-for="edge in item.topologyPreview.edges" :key="edge.id" :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" />
                <rect v-for="node in item.topologyPreview.nodes" :key="node.id" :x="node.x" :y="node.y" :width="node.width" :height="node.height" rx="3" :fill="node.color" />
              </svg>
              <div v-else class="project-preview-empty"><Network :size="17" /><span>暂无拓扑</span></div>
            </div>
            <header>
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ item.type }}</span>
              </div>
              <div class="project-card-menu-wrap">
                <button type="button" class="project-more-button" title="项目更多操作" @click.stop="openProjectMenu(item.id)"><MoreHorizontal :size="18" /></button>
                <div v-if="projectMenuId === item.id" class="project-more-menu" @click.stop>
                  <button type="button" @click="openProjectRename(item)">重命名</button>
                  <button type="button" :disabled="Boolean(projectAction)" @click="duplicateProjectFromMenu(item)">复制项目</button>
                  <button type="button" @click="openProjectExport(item)">导出</button>
                  <button type="button" class="danger-item" @click="openProjectDelete(item)">删除</button>
                </div>
              </div>
            </header>
            <div class="project-card-tags"><span>{{ item.creation }}</span><span :class="{ done: item.state === '已完成' }">{{ item.state }}</span></div>
            <dl>
              <div><dt>设备</dt><dd>{{ item.devices }}</dd></div>
              <div><dt>连接</dt><dd>{{ item.connections }}</dd></div>
              <div><dt>更新时间</dt><dd>{{ item.updated }}</dd></div>
            </dl>
            <footer><button type="button" @click.stop="emit('open-project', item)">打开项目 <ChevronRight :size="16" /></button><span>{{ item.topologyVersion }}</span></footer>
          </article>
        </div>
      </section>

      <Transition name="project-create-modal">
        <div v-if="projectCreateOpen" class="project-create-backdrop" @pointerdown.self="projectCreateOpen = false">
          <section class="project-create-modal" role="dialog" aria-modal="true" aria-label="新建网络规划">
            <header>
              <div><span>新建项目</span><h2>新建网络规划</h2></div>
              <button type="button" aria-label="关闭" @click="projectCreateOpen = false"><X :size="19" /></button>
            </header>
            <label class="project-name-field">项目名称<input v-model="newProjectName" autofocus placeholder="例如：XX公司办公网络规划" @keyup.enter="persistProject(projectCreateMode)" /></label>
            <p class="project-choice-label">选择创建方式</p>
            <div class="project-mode-grid">
              <article :class="{ active: projectCreateMode === 'manual' }">
                <span class="project-mode-icon manual"><Network :size="26" /></span>
                <strong>空白拓扑</strong>
                <p>从空白画布开始，自由添加网络设备并建立连接。</p>
                <button type="button" @click="projectCreateMode = 'manual'">选择空白拓扑</button>
              </article>
              <article :class="{ active: projectCreateMode === 'ai' }">
                <span class="project-mode-icon ai"><Sparkles :size="26" /></span>
                <strong>AI智能生成</strong>
                <p>描述网络建设需求，AI自动分析并生成初始拓扑方案。</p>
                <button type="button" @click="projectCreateMode = 'ai'">选择 AI 生成</button>
              </article>
            </div>
            <label v-if="projectCreateMode === 'ai'" class="project-requirement-field">网络需求<textarea v-model="requirementText" rows="3" placeholder="例如：为100人的公司设计局域网，包括研发、财务、行政，需要无线网络和服务器。"></textarea></label>
            <footer class="project-create-actions">
              <button type="button" class="secondary-button" @click="projectCreateOpen = false">取消</button>
              <button type="button" class="primary-button" :disabled="projectCreating || !newProjectName.trim()" @click="persistProject(projectCreateMode)">{{ projectCreating ? '创建中…' : '创建项目' }}</button>
            </footer>
          </section>
        </div>
      </Transition>

      <Transition name="project-create-modal">
        <div v-if="projectRenameOpen" class="project-create-backdrop" @pointerdown.self="projectRenameOpen = false">
          <section class="project-create-modal project-action-modal" role="dialog" aria-modal="true" aria-label="重命名项目">
            <header><div><span>项目设置</span><h2>重命名项目</h2></div><button type="button" aria-label="关闭" @click="projectRenameOpen = false"><X :size="19" /></button></header>
            <label class="project-name-field">项目名称<input v-model="projectRenameDraft" maxlength="50" autofocus @keyup.enter="saveProjectRename" /></label>
            <p v-if="projectActionError" class="project-action-error" role="alert">{{ projectActionError }}</p>
            <footer class="project-create-actions"><button type="button" class="secondary-button" @click="projectRenameOpen = false">取消</button><button type="button" class="primary-button" :disabled="projectAction === `rename:${projectKey(projectRenameTarget)}`" @click="saveProjectRename">{{ projectAction ? '保存中…' : '保存' }}</button></footer>
          </section>
        </div>
      </Transition>

      <Transition name="project-create-modal">
        <div v-if="projectDeleteOpen" class="project-create-backdrop" @pointerdown.self="projectDeleteOpen = false">
          <section class="project-create-modal project-action-modal" role="alertdialog" aria-modal="true" aria-label="删除项目">
            <header><div><span>项目设置</span><h2>删除项目</h2></div><button type="button" aria-label="关闭" @click="projectDeleteOpen = false"><X :size="19" /></button></header>
            <p class="project-delete-copy">确定要删除「{{ projectDeleteTarget?.name }}」吗？</p>
            <p class="project-delete-warning">删除后该项目的拓扑和相关数据将无法继续访问。</p>
            <p v-if="projectActionError" class="project-action-error" role="alert">{{ projectActionError }}</p>
            <footer class="project-create-actions"><button type="button" class="secondary-button" @click="projectDeleteOpen = false">取消</button><button type="button" class="dialog-danger" :disabled="projectAction === `delete:${projectKey(projectDeleteTarget)}`" @click="confirmProjectDelete">{{ projectAction ? '删除中…' : '删除项目' }}</button></footer>
          </section>
        </div>
      </Transition>

      <Transition name="project-create-modal">
        <div v-if="projectExportOpen" class="project-create-backdrop" @pointerdown.self="projectExportOpen = false">
          <section class="project-create-modal project-action-modal" role="dialog" aria-modal="true" aria-label="导出项目">
            <header><div><span>项目文件</span><h2>导出项目</h2></div><button type="button" aria-label="关闭" @click="projectExportOpen = false"><X :size="19" /></button></header>
            <p class="project-export-copy">导出「{{ projectExportTarget?.name }}」的最新已保存拓扑。</p>
            <div class="project-export-options"><button type="button" :disabled="Boolean(projectAction)" @click="exportProject(projectExportTarget, 'png')"><Download :size="17" /><span><strong>拓扑图片 PNG</strong><small>完整拓扑图像</small></span></button><button type="button" :disabled="Boolean(projectAction)" @click="exportProject(projectExportTarget, 'svg')"><FileOutput :size="17" /><span><strong>拓扑 SVG</strong><small>可继续编辑的矢量文件</small></span></button><button type="button" :disabled="Boolean(projectAction)" @click="exportProject(projectExportTarget, 'json')"><FileText :size="17" /><span><strong>项目数据 JSON</strong><small>节点、连线与项目设置</small></span></button><button type="button" disabled><FileText :size="17" /><span><strong>网络规划 Word</strong><small>开发中</small></span></button><button type="button" disabled><FileText :size="17" /><span><strong>网络规划 PDF</strong><small>开发中</small></span></button></div>
            <p v-if="projectActionError" class="project-action-error" role="alert">{{ projectActionError }}</p>
          </section>
        </div>
      </Transition>
    </template>

    <template v-else-if="activePage === 'requirements'">
      <section class="split-layout">
        <div class="panel">
          <div class="panel-head">
            <div>
              <h2>需求输入</h2>
              <p>自然语言需求会先整理成结构化项目数据。</p>
            </div>
            <span class="status-chip strong">{{ requirementState }}</span>
          </div>
          <textarea v-model="requirementText" class="requirement-input"></textarea>
          <div class="button-row">
            <button class="primary-button" type="button" @click="analyzeRequirement"><Sparkles :size="16" /> AI 分析需求</button>
            <button class="secondary-button" type="button"><CheckCircle2 :size="16" /> 确认需求</button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <div>
              <h2>结构化结果</h2>
              <p>{{ requirementSummary.scale }}</p>
            </div>
            <Network :size="22" />
          </div>
          <div class="summary-list">
            <article v-for="area in requirementSummary.areas" :key="area.name">
              <strong>{{ area.name }}</strong>
              <span>{{ area.detail }}</span>
            </article>
          </div>
          <div class="tag-cloud">
            <span v-for="item in requirementSummary.requirements" :key="item">{{ item }}</span>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="activePage === 'files'">
      <section class="split-layout">
        <div class="panel">
          <div class="panel-head">
            <div>
              <h2>资料管理</h2>
              <p>上传后提取正文并用于当前项目 AI 问答、拓扑生成和方案分析。</p>
            </div>
            <input ref="projectFileInput" class="project-file-input" type="file" accept=".pdf,.docx,.xlsx,.txt,.md" @change="handleProjectFileSelection" />
            <button class="primary-button" type="button" :disabled="projectFileUploading" @click="openProjectFilePicker"><UploadCloud :size="16" /> {{ projectFileUploading ? '正在读取…' : '上传资料' }}</button>
          </div>
          <div class="file-drop">
            <UploadCloud :size="28" />
            <strong>支持 PDF、DOCX、XLSX、TXT、Markdown</strong>
            <span>当前限制：最多 {{ projectFileLimits.count }} 份，单文件 {{ projectFileLimits.singleMb }} MB，总容量 {{ projectFileLimits.capacityMb }} MB</span>
            <small>资料会按项目隔离保存，并由 AI 自动读取正文</small>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <div>
              <h2>项目资料</h2>
              <p>当前资料仅属于本项目和当前用户。</p>
            </div>
            <Database :size="22" />
          </div>
          <div v-if="projectFileError" class="file-list-state error">{{ projectFileError }}</div>
          <div v-else-if="projectFilesLoading" class="file-list-state">正在加载项目资料…</div>
          <div v-else-if="!projectFiles.length" class="file-list-state">暂无项目资料，上传后即可用于 AI 读取。</div>
          <div v-else class="file-list">
            <article v-for="file in projectFiles" :key="file.id || file.name">
              <FileText :size="18" />
              <div>
                <strong>{{ file.name }}</strong>
                <span>{{ String(file.format || '').toUpperCase() }} · {{ formatProjectFileSize(file.size) }} · {{ file.textLength || 0 }} 字</span>
              </div>
              <small :class="{ 'is-error': !file.textAvailable }">{{ formatProjectFileStatus(file) }}</small>
            </article>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="activePage === 'topology'">
      <TopologyWorkspace :key="project.publicId || project.id" :project-id="project.publicId || project.id" :project-name="project.name" :initial-mode="project.creationMode" :initial-description="project.description" @back="emit('select', 'projects')" />
    </template>

    <template v-else-if="activePage === 'report'">
      <section class="split-layout wide-left">
        <div class="panel">
          <div class="panel-head">
            <div>
              <h2>方案材料</h2>
              <p>按章节保存，后续可对接 Word 和 PDF 模板。</p>
            </div>
            <button class="primary-button" type="button"><FileOutput :size="16" /> 重新生成</button>
          </div>
          <div class="section-list">
            <article v-for="section in reportSections" :key="section.title">
              <ListChecks :size="18" />
              <div>
                <strong>{{ section.title }}</strong>
                <span>{{ section.summary }}</span>
              </div>
              <small>{{ section.state }}</small>
            </article>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <div>
              <h2>设备清单</h2>
              <p>由当前拓扑节点同步生成。</p>
            </div>
          </div>
          <div class="inventory-list">
            <article v-for="device in deviceInventory" :key="device.type">
              <strong>{{ device.type }}</strong>
              <span>{{ device.count }} 台</span>
              <small>{{ device.purpose }}</small>
            </article>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="activePage === 'risk'">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>风险与优化</h2>
            <p>检查单点故障、隔离边界、连接完整性和扩展能力。</p>
          </div>
          <button class="secondary-button" type="button"><TriangleAlert :size="16" /> 重新检测</button>
        </div>
        <div class="risk-list">
          <article v-for="risk in riskItems" :key="risk.name">
            <span class="risk-level">{{ risk.level }}</span>
            <div>
              <strong>{{ risk.name }}</strong>
              <p>{{ risk.impact }}</p>
              <small>{{ risk.suggestion }}</small>
            </div>
          </article>
        </div>
      </section>
    </template>

    <template v-else-if="isDialoguePage">
      <section
        class="gemini-chat-surface"
        :class="{ 'new-dialogue-surface': isNewDialogueEmpty }"
      >
        <header class="dialogue-topbar">
          <div class="conversation-title-wrap">
            <form v-if="isConversationTitleEditing" class="conversation-title-editor" @submit.prevent="saveConversationTitle">
              <input
                ref="conversationTitleInput"
                v-model="conversationTitleDraft"
                aria-label="对话名称"
                maxlength="36"
                @blur="saveConversationTitle"
                @keydown.enter.prevent="saveConversationTitle"
                @keydown.esc.prevent="cancelConversationRename"
              />
            </form>
            <div v-else class="conversation-title-display">
              <span>{{ activeConversationTitle }}</span>
              <Pin v-if="chatPinned" :size="14" aria-label="已固定" />
            </div>
          </div>
          <div ref="conversationMenuRef" class="top-menu-wrap">
            <button
              class="more-button"
              type="button"
              aria-label="对话更多操作"
              data-tooltip="对话操作"
              data-tooltip-position="bottom"
              :aria-expanded="conversationMenuOpen"
              aria-controls="conversation-actions-menu"
              @click="toggleConversationMenu"
            >
              <MoreVertical :size="20" />
            </button>
          </div>
        </header>

        <Transition name="chat-thread-view" mode="out-in" @after-enter="scrollActiveThreadAfterEnter">
          <div
            :key="`${activePage}-${chatId ?? 'new'}`"
            ref="chatThread"
            class="gemini-thread scrollbar-hide"
            :class="{ 'new-dialogue-thread': isNewDialogueEmpty }"
            :aria-busy="isReplying"
            @scroll="updateThreadScrollState"
          >
          <div v-if="isNewDialogueEmpty" class="new-dialogue-welcome">
            <h1>你好！想规划什么网络？</h1>
            <p>描述你的网络规模、部门、设备或业务需求，我可以帮你分析需求、规划拓扑并检查设计。</p>
            <div class="prompt-row gemini-prompts">
              <button v-for="prompt in ['规划100人的企业局域网', '规划1000人的园区网络', '帮我完善当前拓扑', '分析一个双核心网络']" :key="prompt" type="button" @click="usePrompt(prompt)">{{ prompt }}</button>
            </div>
          </div>

          <div v-else-if="!isNewDialogue && visibleDialogueMessages.length === 0" class="chat-welcome">
            <h2>今天想规划什么网络？</h2>
            <p>描述建设场景、楼层、部门、终端数量或直接上传资料提问，我会判断是回答问题、生成拓扑还是整理方案。</p>
            <div class="prompt-row gemini-prompts">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                type="button"
                @click="usePrompt(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <article
            v-for="message in visibleDialogueMessages"
            :key="messageKey(message)"
            :ref="(element) => setMessageElement(message.id, element)"
            class="gemini-message"
            :class="[message.role, { 'message-menu-open': activeMessageMenu === message.id, 'is-editing': editingMessageId === message.id }]"
          >
            <form v-if="message.role === 'user' && editingMessageId === message.id" class="user-message-editor" @submit.prevent="resubmitEditedMessage(message)">
              <div class="message-body user-message-edit-body">
                <textarea ref="editingMessageInput" v-model="editingMessageDraft" rows="1" aria-label="编辑消息" @input="resizeEditingMessageInput"></textarea>
              </div>
              <div class="user-message-editor-actions">
                <button type="button" @click="cancelEditingMessage">取消</button>
                <button type="submit">保存并发送</button>
              </div>
            </form>
            <div v-else-if="message.role === 'user'" class="user-message-wrapper">
              <div class="message-body">
                <p>{{ message.content }}</p>
              </div>
              <div v-if="message.status === 'failed'" class="user-message-delivery-status failed" role="alert">
                <span>发送失败</span>
                <span aria-hidden="true">·</span>
                <button type="button" @click="retryFailedMessage(message)">重试</button>
              </div>
              <div class="user-message-actions">
                <button
                  type="button"
                  :aria-label="copiedMessageId === message.id ? '已复制' : '复制消息'"
                  :data-tooltip="copiedMessageId === message.id ? '已复制' : '复制消息'"
                  :class="{ active: copiedMessageId === message.id }"
                  @click="copyMessage(message)"
                >
                  <CheckCircle2 v-if="copiedMessageId === message.id" :size="15" />
                  <Copy v-else :size="15" />
                </button>
                <button type="button" aria-label="编辑消息" data-tooltip="编辑消息" data-tooltip-position="bottom" @click="startEditingMessage(message)">
                  <Pencil :size="15" />
                </button>
              </div>
            </div>
            <div v-else class="message-body" :aria-live="['waiting', 'streaming'].includes(message.status) ? 'polite' : undefined">
              <AiMessageContent
                :content="message.content"
                :status="message.status"
                :attachments="message.attachments"
                @download-attachment="downloadConversationFile"
                @preview-attachment="previewConversationFile"
              />
              <div v-if="message.status === 'streaming'" class="message-actions is-streaming">
                <button type="button" class="stop-generation-button" aria-label="停止生成" @click="cancelReply">
                  <span class="stop-generation-icon" aria-hidden="true"></span>
                  <span>停止生成</span>
                </button>
              </div>
              <div v-else-if="message.status === 'failed' || message.status === 'interrupted'" class="message-actions is-failed" role="alert">
                <span class="message-failed-label">{{ message.status === 'interrupted' ? '响应中断' : '生成失败' }}</span>
                <button type="button" class="retry-generation-button" aria-label="重新生成" @click="regenerateMessage(message)">
                  <RotateCcw :size="15" />
                  <span>重新生成</span>
                </button>
              </div>
              <div v-else-if="!message.status || message.status === 'complete'" class="message-actions">
                <button
                  class="message-like-button"
                  type="button"
                  aria-label="点赞"
                  data-tooltip="答得好"
                  data-tooltip-position="bottom"
                  :aria-pressed="message.feedback === 'up'"
                  :class="{ active: message.feedback === 'up' }"
                  @click="toggleMessageFeedback(message, 'up')"
                ><ThumbsUp :size="16" /></button>
                <button
                  type="button"
                  aria-label="踩"
                  data-tooltip="踩"
                  :aria-pressed="message.feedback === 'down'"
                  :class="{ active: message.feedback === 'down' }"
                  @click="toggleMessageFeedback(message, 'down')"
                ><ThumbsDown :size="16" /></button>
                <HeightCollapse>
                  <div v-if="feedbackMenuMessageId === message.id" class="feedback-popover" @pointerdown.stop>
                    <button type="button" @click="selectFeedbackReason(message, '回答不准确')">回答不准确</button>
                    <button type="button" @click="selectFeedbackReason(message, '没有解决问题')">没有解决问题</button>
                    <button type="button" @click="selectFeedbackReason(message, '内容不清楚')">内容不清楚</button>
                    <button type="button" @click="selectFeedbackReason(message, '其他')">其他</button>
                  </div>
                </HeightCollapse>
                <button
                  type="button"
                  :aria-label="copiedMessageId === message.id ? '已复制' : '复制'"
                  :data-tooltip="copiedMessageId === message.id ? '已复制' : '复制'"
                  :class="{ active: copiedMessageId === message.id }"
                  @click="copyMessage(message)"
                >
                  <CheckCircle2 v-if="copiedMessageId === message.id" :size="16" />
                  <Copy v-else :size="16" />
                </button>
                <button
                  type="button"
                  :aria-label="regeneratedMessageId === message.id ? '已重新生成' : '重新生成'"
                  :data-tooltip="regeneratedMessageId === message.id ? '已重新生成' : '重新生成'"
                  :class="{ active: regeneratedMessageId === message.id }"
                  @click="regenerateMessage(message)"
                ><RotateCcw :size="16" /></button>
                <div class="message-more-wrap">
                  <button
                    type="button"
                    aria-label="更多消息操作"
                    data-tooltip="更多操作"
                    :aria-expanded="activeMessageMenu === message.id"
                    @click.stop="toggleMessageMenu(message.id)"
                  ><MoreHorizontal :size="16" /></button>
                  <HeightCollapse>
                    <div v-if="activeMessageMenu === message.id" class="message-action-popover" @pointerdown.stop>
                      <button type="button" @click="regenerateMessage(message)">
                        <RotateCcw :size="15" />
                        <span>重新生成</span>
                      </button>
                      <button type="button" @click="copyMessage(message)">
                        <Copy :size="15" />
                        <span>复制</span>
                      </button>
                      <button type="button" class="danger" @click="deleteMessageTurn(message)">
                        <Trash2 :size="15" />
                        <span>删除回答</span>
                      </button>
                    </div>
                  </HeightCollapse>
                </div>
              </div>
            </div>
          </article>

          </div>
        </Transition>

        <div class="chat-scroll-latest-slot" aria-live="polite">
          <button
            v-show="threadHasOverflow && !threadAtBottom"
            class="chat-scroll-latest"
            type="button"
            aria-label="回到底部"
            @pointerdown.stop
            @click.stop="scrollToLatestMessage"
          >
            <ChevronRight :size="15" />
            <span>{{ isReplying ? '查看最新回复' : '回到底部' }}</span>
          </button>
        </div>

        <ChatComposer
          ref="chatComposer"
          v-model="chatInput"
          :is-new-dialogue="isNewDialogueEmpty"
          :reset-key="newChatSessionKey"
          @submit="sendMessage"
        />
        <p class="chat-disclaimer">AI 生成内容请结合实际网络设备和工程规范复核。</p>
      </section>

      <Teleport to="body">
        <Transition name="conversation-popover">
          <div
            v-if="conversationMenuOpen"
            id="conversation-actions-menu"
            class="conversation-popover"
            :style="{ top: `${conversationMenuPosition.top}px`, left: `${conversationMenuPosition.left}px` }"
            role="menu"
            @pointerdown.stop
            @click.stop
          >
            <button
              v-for="item in conversationActions"
              :key="item.action"
              type="button"
              role="menuitem"
              :class="{ danger: item.danger }"
              @click="handleConversationAction(item.action)"
            >
              <component :is="item.icon" :size="17" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </Transition>
      </Teleport>

      <Teleport to="body">
        <Transition name="conversation-toast">
          <section v-if="conversationToast" class="conversation-toast" :class="conversationToast.tone" role="status" aria-live="polite">
            <p>{{ conversationToast.message }}</p>
            <small v-if="conversationToast.quota">
              当前套餐：{{ conversationToast.quota.planName }} · 已使用 {{ conversationToast.quota.used.toLocaleString('zh-CN') }} / {{ conversationToast.quota.total.toLocaleString('zh-CN') }} {{ conversationToast.quota.label }}<br>
              剩余 {{ conversationToast.quota.remaining.toLocaleString('zh-CN') }}{{ conversationToast.quota.estimatedInputTokens ? `，本次预计至少需要 ${conversationToast.quota.estimatedInputTokens.toLocaleString('zh-CN')} Token` : '' }} · 下次重置：{{ formatQuotaReset(conversationToast.quota.nextResetAt) }}
            </small>
            <div v-if="conversationToast.quota" class="conversation-toast-actions">
              <button type="button" @click="openProfileModal('usage')">查看额度</button>
              <button type="button" @click="openProfileModal('subscription')">{{ conversationToast.quota.level === 'PRO' ? '查看套餐' : '升级专业版' }}</button>
            </div>
          </section>
        </Transition>
      </Teleport>
    </template>

    <template v-else-if="activePage === 'export'">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>成果导出</h2>
            <p>拓扑图和工程材料可独立导出，也可打包导出。</p>
          </div>
          <button class="primary-button" type="button"><Download :size="16" /> 导出全部</button>
        </div>
        <div class="export-grid">
          <article>
            <FileOutput :size="24" />
            <strong>工程方案 Word</strong>
            <span>封面、目录、章节、设备清单</span>
            <button class="secondary-button" type="button">生成 Word</button>
          </article>
          <article>
            <FileText :size="24" />
            <strong>工程方案 PDF</strong>
            <span>课程设计、实训报告、项目汇报</span>
            <button class="secondary-button" type="button">生成 PDF</button>
          </article>
          <article>
            <Network :size="24" />
            <strong>拓扑图图片</strong>
            <span>PNG、SVG、当前画布快照</span>
            <button class="secondary-button" type="button">导出图片</button>
          </article>
        </div>
      </section>
    </template>

    <template v-else-if="activePage === 'versions'">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>历史版本</h2>
            <p>拓扑 JSON、方案材料和导出记录按项目保存。</p>
          </div>
          <button class="secondary-button" type="button">保存新版本</button>
        </div>
        <div class="timeline">
          <article>
            <strong>V3 当前方案</strong>
            <span>调整服务器区连接，增加访客无线 VLAN</span>
            <small>2026-07-22 08:42</small>
          </article>
          <article>
            <strong>V2 优化方案</strong>
            <span>增加防火墙和三楼服务器区</span>
            <small>2026-07-21 21:18</small>
          </article>
          <article>
            <strong>V1 初始方案</strong>
            <span>AI 根据自然语言需求自动生成</span>
            <small>2026-07-21 19:50</small>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.project-hub { width: min(980px, 100%); margin: 0 auto; padding: 36px 20px; }
.project-hub-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding-bottom: 22px; border-bottom: 1px solid #dce5ef; }.project-hub-header > div { display: grid; gap: 7px; }.project-hub-header span { color: #2563eb; font-size: 11px; font-weight: 750; letter-spacing: .08em; }.project-hub-header h2 { margin: 0; color: #182334; font-size: 25px; }.project-hub-header p { margin: 0; color: #64748b; font-size: 13px; }.project-hub-create { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 7px; height: 36px; padding: 0 12px; border: 1px solid #2563eb; border-radius: 5px; background: #2563eb; color: #fff; font-size: 13px; font-weight: 700; }
.project-hub-list { display: grid; gap: 9px; margin-top: 18px; }.project-hub-item { display: grid; grid-template-columns: 40px 1fr 18px; align-items: center; gap: 12px; min-height: 70px; padding: 12px; border: 1px solid #dce5ef; border-radius: 6px; background: #fff; color: #64748b; text-align: left; }.project-hub-item:hover { border-color: #b7cdf1; background: #fbfdff; }.project-hub-item > span:nth-child(2) { display: grid; gap: 5px; }.project-hub-item strong { color: #263548; font-size: 14px; }.project-hub-item small { color: #7c8ca0; font-size: 11px; }.project-hub-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 5px; background: #eff6ff; color: #2563eb; }
.project-create-backdrop { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 20px; background: rgba(15,23,42,.3); }.project-create-modal { width: min(650px, 100%); border: 1px solid #d8e1ec; border-radius: 8px; background: #fff; box-shadow: 0 26px 60px rgba(15,23,42,.22); }.project-create-modal > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 20px 22px 16px; border-bottom: 1px solid #e2e8f0; }.project-create-modal header span { color: #2563eb; font-size: 11px; font-weight: 700; }.project-create-modal header h2 { margin: 5px 0 0; color: #1e293b; font-size: 20px; }.project-create-modal header button { display: grid; place-items: center; width: 30px; height: 30px; border: 0; border-radius: 4px; background: transparent; color: #64748b; }.project-create-modal header button:hover { background: #f1f5f9; }.project-name-field { display: grid; gap: 7px; padding: 18px 22px 0; color: #475569; font-size: 12px; font-weight: 700; }.project-name-field input { width: 100%; height: 38px; padding: 0 10px; border: 1px solid #cfdbe8; border-radius: 5px; color: #334155; outline: 0; font-size: 13px; }.project-name-field input:focus { border-color: #60a5fa; box-shadow: 0 0 0 3px rgba(59,130,246,.1); }.project-choice-label { margin: 19px 22px 10px; color: #475569; font-size: 12px; font-weight: 700; }.project-mode-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 0 22px 22px; }.project-mode-grid article { display: grid; align-content: start; gap: 8px; min-height: 220px; padding: 16px; border: 1px solid #dce5ef; border-radius: 6px; background: #fff; }.project-mode-grid article:hover, .project-mode-grid article.active { border-color: #93c5fd; background: #fbfdff; }.project-mode-icon { display: grid; place-items: center; width: 42px; height: 42px; margin-bottom: 3px; border-radius: 6px; }.project-mode-icon.manual { background: #eff6ff; color: #2563eb; }.project-mode-icon.ai { background: #f5f3ff; color: #7c3aed; }.project-mode-grid strong { color: #25364a; font-size: 15px; }.project-mode-grid p { min-height: 44px; margin: 0; color: #6b7b8f; font-size: 12px; line-height: 1.65; }.project-mode-grid button { align-self: end; height: 34px; border: 1px solid #cbd9eb; border-radius: 4px; background: #fff; color: #2563eb; font-size: 12px; font-weight: 700; }.project-mode-grid article.active button, .project-mode-grid button:hover { border-color: #2563eb; background: #2563eb; color: #fff; }
.project-create-modal-enter-active, .project-create-modal-leave-active { transition: opacity .18s ease; }.project-create-modal-enter-active .project-create-modal, .project-create-modal-leave-active .project-create-modal { transition: transform .18s ease, opacity .18s ease; }.project-create-modal-enter-from, .project-create-modal-leave-to { opacity: 0; }.project-create-modal-enter-from .project-create-modal, .project-create-modal-leave-to .project-create-modal { opacity: 0; transform: translateY(8px) scale(.98); }
.project-create-actions { display: flex; justify-content: flex-end; gap: 9px; padding: 0 22px 22px; } .project-create-actions .primary-button, .project-create-actions .secondary-button { min-width: 92px; justify-content: center; }
.project-requirement-field { display: grid; gap: 7px; padding: 0 22px 18px; color: #475569; font-size: 12px; font-weight: 700; } .project-requirement-field textarea { width: 100%; min-height: 76px; padding: 9px 10px; border: 1px solid #cfdbe8; border-radius: 5px; color: #334155; outline: 0; font-size: 13px; line-height: 1.55; resize: vertical; } .project-requirement-field textarea:focus { border-color: #60a5fa; box-shadow: 0 0 0 3px rgba(59,130,246,.1); }
@media (max-width: 640px) { .project-hub { padding: 22px 4px; }.project-hub-header { align-items: flex-start; flex-direction: column; }.project-mode-grid { grid-template-columns: 1fr; }.project-mode-grid article { min-height: 170px; }.project-mode-grid p { min-height: auto; }.project-hub-create { width: 100%; justify-content: center; } }

/* Project workspace: wide cards make the project state scannable at a glance. */
.project-hub { width: min(1400px, 100%); padding: 40px 32px 52px; }
.project-hub-header { align-items: center; padding-bottom: 24px; }.project-hub-header h2 { font-size: 28px; letter-spacing: 0; }.project-hub-header p { font-size: 14px; }.project-hub-create { height: 38px; padding: 0 14px; border-radius: 6px; }
.project-card-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-top: 24px; }.project-new-card, .project-card { min-height: 300px; border: 1px solid #dce5ef; border-radius: 10px; background: #fff; }.project-new-card { display: grid; align-content: center; justify-items: center; gap: 11px; padding: 24px; border-style: dashed; color: #64748b; text-align: center; }.project-new-card:hover { border-color: #7aaaf0; background: #fbfdff; box-shadow: 0 8px 24px rgba(15, 23, 42, .06); }.project-new-card > span { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 8px; background: #eff6ff; color: #2563eb; }.project-new-card strong { color: #25364a; font-size: 15px; }.project-new-card small { max-width: 210px; color: #718096; font-size: 12px; line-height: 1.6; }
.project-card { position: relative; display: grid; grid-template-rows: 110px auto auto auto auto; gap: 12px; padding: 14px; cursor: pointer; transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease; }.project-card:hover { border-color: #a9c5ee; box-shadow: 0 12px 26px rgba(15, 23, 42, .09); transform: translateY(-2px); }.project-card-preview { position: relative; display: grid; height: 110px; overflow: hidden; border: 1px solid #e1e8f1; border-radius: 7px; background-color: #f8fafc; background-image: radial-gradient(#d0d9e4 .8px, transparent .8px); background-size: 14px 14px; }.project-card-preview svg { width: 100%; height: 100%; }.project-card-preview line { stroke: #9caec2; stroke-width: 1.5; stroke-linecap: round; }.project-card-preview rect { stroke: #fff; stroke-width: 1.5; filter: drop-shadow(0 1px 1px rgba(15, 23, 42, .16)); }.project-preview-empty { display: inline-flex; align-self: center; justify-self: center; align-items: center; gap: 6px; padding: 7px 10px; border: 1px dashed #b7c6d8; border-radius: 5px; background: rgba(255,255,255,.76); color: #718096; font-size: 11px; }
.project-card > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }.project-card > header > div:first-child { display: grid; gap: 4px; min-width: 0; }.project-card > header strong { overflow: hidden; color: #233247; font-size: 14px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }.project-card > header span { color: #718096; font-size: 11px; }.project-card-menu-wrap { position: relative; }.project-more-button { display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: 4px; background: transparent; color: #718096; }.project-more-button:hover { background: #f1f5f9; color: #2563eb; }.project-more-menu { position: absolute; top: 31px; right: 0; z-index: 4; display: grid; width: 118px; padding: 4px; border: 1px solid #d8e1ec; border-radius: 5px; background: #fff; box-shadow: 0 10px 24px rgba(15,23,42,.14); }.project-more-menu button { height: 29px; padding: 0 7px; border: 0; border-radius: 3px; background: transparent; color: #526174; font-size: 11px; text-align: left; }.project-more-menu button:hover { background: #f1f5f9; color: #2563eb; }.project-more-menu .danger-item { color: #dc2626; }.project-more-menu button:disabled { cursor: default; opacity: .48; }
.project-action-modal { width: min(440px, calc(100vw - 28px)); }.project-action-error { margin: 0 22px 16px; color: #dc2626; font-size: 12px; line-height: 1.5; }.project-delete-copy { margin: 0 22px 7px; color: #263548; font-size: 14px; font-weight: 700; line-height: 1.6; }.project-delete-warning, .project-export-copy { margin: 0 22px 18px; color: #718096; font-size: 12px; line-height: 1.65; }.dialog-danger { min-width: 112px; height: 34px; padding: 0 13px; border: 1px solid #dc2626; border-radius: 5px; background: #dc2626; color: #fff; font-size: 12px; font-weight: 700; }.dialog-danger:hover { background: #b91c1c; }.dialog-danger:disabled { cursor: default; opacity: .55; }.project-export-options { display: grid; gap: 8px; padding: 0 22px 20px; }.project-export-options button { display: flex; align-items: center; gap: 10px; min-height: 48px; padding: 8px 10px; border: 1px solid #dce5ef; border-radius: 5px; background: #fff; color: #526174; text-align: left; }.project-export-options button:hover:not(:disabled) { border-color: #93c5fd; background: #f8fbff; color: #2563eb; }.project-export-options button:disabled { cursor: default; opacity: .48; }.project-export-options button > span { display: grid; gap: 2px; }.project-export-options strong { color: #334155; font-size: 12px; }.project-export-options small { color: #8a99aa; font-size: 10px; }
.project-card-tags { display: flex; gap: 6px; }.project-card-tags span { padding: 4px 6px; border-radius: 4px; background: #eff6ff; color: #2563eb; font-size: 10px; font-weight: 700; }.project-card-tags span.done { background: #ecfdf5; color: #047857; }.project-card dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 0; }.project-card dl div { display: grid; gap: 4px; padding: 8px; border: 1px solid #edf1f6; border-radius: 5px; background: #fbfdff; }.project-card dt { color: #8290a3; font-size: 10px; }.project-card dd { margin: 0; color: #334155; font-size: 12px; font-weight: 700; }.project-card footer { display: flex; align-items: center; justify-content: space-between; padding-top: 2px; }.project-card footer button { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: #2563eb; font-size: 12px; font-weight: 700; }.project-card footer span { color: #8a99aa; font-size: 11px; }
@media (max-width: 1180px) { .project-card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.project-hub { padding-inline: 24px; } }
@media (max-width: 680px) { .project-hub { padding: 24px 14px 36px; }.project-card-grid { grid-template-columns: 1fr; }.project-new-card, .project-card { min-height: 270px; } }
</style>
