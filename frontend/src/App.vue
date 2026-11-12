<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  BookOpen,
  Bot,
  Database,
  FileCog,
  FileOutput,
  FileText,
  FolderKanban,
  Gauge,
  HardDrive,
  History,
  LayoutDashboard,
  ListChecks,
  MessageSquare,
  Network,
  ScrollText,
  Server,
  Settings2,
  ShieldCheck,
  TriangleAlert,
  UploadCloud,
  Users
} from '@lucide/vue'
import AppHeader from './components/AppHeader.vue'
import AdminWorkspace from './components/AdminWorkspace.vue'
import AuthPage from './components/AuthPage.vue'
import SidebarNav from './components/SidebarNav.vue'
import UserWorkspace from './components/UserWorkspace.vue'
import { getCurrentUser, login, logout as logoutRequest, refreshSession, register, revokeRefreshToken } from './api/auth'
import { clearSession } from './auth/authStore'
import { deleteConversation, getProject, listConversations, listProjects, updateConversation } from './api/user'
import { useInitialEntrance } from './composables/useInitialEntrance'
import { APP_MODE_STORAGE_KEY, AUTH_REDIRECT_STORAGE_KEY, adminPathFor, resolveAuthenticatedAppRoute, userPathFor } from './utils/appRouting'

const isAuthenticated = ref(false)
const isInitializing = ref(true)
const authStatus = ref('loading')
const routeStatus = ref('ready')
const authError = ref('')
const accessNotice = ref('')
const currentUser = ref(null)
const activeRole = ref('user')
const activeUserPage = ref('new-chat')
const activeAdminPage = ref('dashboard')
const activeProject = ref({ id: null, name: '未选择项目', type: '未分类', terminals: 0, files: 0, topologyVersion: 'V0', status: '草稿' })
const recentProjectItems = ref([])
const newChatSessionKey = ref(0)
const activeChatId = ref(null)
const activeProjectId = ref(null)
const newChatTitle = ref('新对话')
const recentChats = ref([])
let accessNoticeTimer = 0
let workspaceLoadSequence = 0
const pendingChatIds = new Set()

function projectKey(project) { return project?.publicId || project?.id || null }
function sameProject(left, right) {
  const leftKey = projectKey(left), rightKey = projectKey(right)
  return leftKey != null && rightKey != null && String(leftKey) === String(rightKey)
}
const { introPlaying, sidebarCollapsed } = useInitialEntrance()
const themePreference = ref('light')

const userNav = [
  { key: 'chat', label: 'AI 对话', icon: MessageSquare },
  { key: 'projects', label: '我的项目', icon: FolderKanban },
  { key: 'requirements', label: '需求输入', icon: FileText },
  { key: 'files', label: '资料管理', icon: UploadCloud },
  { key: 'topology', label: '拓扑编辑', icon: Network },
  { key: 'report', label: '方案分析', icon: ListChecks },
  { key: 'risk', label: '风险优化', icon: TriangleAlert },
  { key: 'export', label: '成果导出', icon: FileOutput },
  { key: 'versions', label: '历史版本', icon: History }
]

const adminNav = [
  { key: 'dashboard', label: '系统总览', icon: LayoutDashboard },
  {
    key: 'user-access',
    label: '用户与权限',
    icon: Users,
    children: [
      { key: 'users', label: '用户管理' },
      { key: 'roles', label: '角色权限' },
      { key: 'login-records', label: '登录记录' }
    ]
  },
  {
    key: 'billing',
    label: '资源与配额',
    icon: Gauge,
    children: [
      { key: 'quota', label: '使用统计' },
      { key: 'quota-tiers', label: '用户等级' },
      { key: 'quota-users', label: '用户配额' }
    ]
  },
  { key: 'devices', label: '拓扑资源', icon: Network },
  {
    key: 'knowledge',
    label: '知识库',
    icon: BookOpen,
    children: [
      { key: 'assets', label: '网络工程知识库' },
      { key: 'knowledge-docs', label: '文档管理' },
      { key: 'rag-config', label: 'RAG 配置' },
      { key: 'knowledge-test', label: '检索测试' }
    ]
  },
  {
    key: 'ai-engine',
    label: 'AI 引擎',
    icon: Bot,
    children: [
      { key: 'ai-models', label: '模型与 API' },
      { key: 'prompts', label: 'Prompt 管理' }
    ]
  },
  {
    key: 'security',
    label: '安全与风控',
    icon: ShieldCheck
  },
  {
    key: 'system-management',
    label: '系统管理',
    icon: Settings2
  }
]
const adminPageKeys = adminNav.flatMap((item) => (
  item.children?.length ? item.children.map((child) => child.key) : [item.key]
))

const navItems = computed(() => (activeRole.value === 'user' ? userNav : adminNav))
const activeKey = computed(() => (activeRole.value === 'user' ? activeUserPage.value : activeAdminPage.value))
const pageTitle = computed(() => {
  const activeItem = navItems.value.find((item) => item.key === activeKey.value)
    ?? navItems.value.flatMap((item) => item.children ?? []).find((item) => item.key === activeKey.value)

  return activeItem?.label || '工作台'
})
const activeChat = computed(() => recentChats.value.find((chat) => chat.id === activeChatId.value) ?? null)
const isUserAssistantPage = computed(() => (
  activeRole.value === 'user' && ['chat', 'new-chat', 'chat-search'].includes(activeUserPage.value)
))
const isTopologyEditorPage = computed(() => activeRole.value === 'user' && activeUserPage.value === 'topology')
const isAdminPromptPage = computed(() => (
  isAuthenticated.value && activeRole.value === 'admin' && activeAdminPage.value === 'prompts'
))
const isAdminAiEnginePage = computed(() => (
  isAuthenticated.value
  && activeRole.value === 'admin'
  && ['ai-models', 'prompts'].includes(activeAdminPage.value)
))
const isAdminSystemManagementPage = computed(() => (
  isAuthenticated.value && activeRole.value === 'admin' && activeAdminPage.value === 'system-management'
))
const isAdminSecurityPage = computed(() => (
  isAuthenticated.value && activeRole.value === 'admin' && activeAdminPage.value === 'security'
))
const isAdminUserAccessPage = computed(() => (
  isAuthenticated.value && activeRole.value === 'admin' && ['users', 'roles', 'login-records'].includes(activeAdminPage.value)
))

function syncPromptViewportLock(isLocked) {
  document.documentElement.classList.toggle('is-admin-prompt-page', isLocked)
  document.body.classList.toggle('is-admin-prompt-page', isLocked)
}

function syncAiEngineViewportLock(isLocked) {
  document.documentElement.classList.toggle('is-admin-ai-engine-page', isLocked)
  document.body.classList.toggle('is-admin-ai-engine-page', isLocked)
}

function syncSystemManagementViewportLock(isLocked) {
  document.documentElement.classList.toggle('is-admin-system-management-page', isLocked)
  document.body.classList.toggle('is-admin-system-management-page', isLocked)
}

function syncSecurityViewportLock(isLocked) {
  document.documentElement.classList.toggle('is-admin-security-page', isLocked)
  document.body.classList.toggle('is-admin-security-page', isLocked)
}

function syncUserAccessViewportLock(isLocked) {
  document.documentElement.classList.toggle('is-admin-user-access-page', isLocked)
  document.body.classList.toggle('is-admin-user-access-page', isLocked)
}

function selectPage(key) {
  if (adminPageKeys.includes(key)) {
    if (currentUser.value?.role !== 'ADMIN') return
    activeRole.value = 'admin'
    persistAppMode('admin')
    activeAdminPage.value = key
    syncAdminRoute()
    return
  }

  if (activeRole.value === 'user') {
    if (key === 'new-chat') {
      activeChatId.value = null
      newChatTitle.value = '新对话'
      newChatSessionKey.value += 1
    }
    activeUserPage.value = key === 'chat' && !activeChatId.value ? 'new-chat' : key
    syncChatRoute()
  } else {
    activeAdminPage.value = key
    syncAdminRoute()
  }
}

function openChat(chatId) {
  activeChatId.value = String(chatId || '')
  activeUserPage.value = 'chat'
  syncChatRoute()
}

function mergeConversation(session) {
  const publicId = session?.publicId
  if (!publicId || session?.projectId != null || String(session?.conversationType || '').toUpperCase() === 'PROJECT_CHAT') return
  const existing = recentChats.value.find((chat) => chat.id === publicId)
  recentChats.value = [
    {
      ...existing,
      id: publicId,
      publicId,
      title: session.title ?? existing?.title ?? '新对话',
      titleStatus: session.titleStatus ?? existing?.titleStatus,
      pinned: session.pinned ?? existing?.pinned ?? false,
      projectId: session.projectId ?? existing?.projectId ?? null,
      conversationType: session.conversationType ?? existing?.conversationType ?? 'GENERAL_CHAT',
      updatedAt: session.updatedAt ?? existing?.updatedAt
    },
    ...recentChats.value.filter((chat) => chat.id !== publicId)
  ].sort((left, right) => Number(right.pinned) - Number(left.pinned))
}

async function renameChat({ chatId, title }) {
  const nextTitle = title?.trim()
  if (!nextTitle) return
  try {
    mergeConversation(await updateConversation(chatId, { title: nextTitle }))
  } catch {
    // The server remains authoritative; retain the existing sidebar item when an update fails.
  }
}

function renameNewChat(title) {
  const nextTitle = title?.trim()
  if (nextTitle) newChatTitle.value = nextTitle
}

function handleProjectCreated(project) {
  if (!projectKey(project)) return
  recentProjectItems.value = [
    project,
    ...recentProjectItems.value.filter((item) => !sameProject(item, project))
  ]
  activeProject.value = project
  activeProjectId.value = projectKey(project)
  activeUserPage.value = 'topology'
  syncChatRoute({ replace: true })
}

function handleProjectUpdated(project) {
  if (!projectKey(project)) return
  recentProjectItems.value = recentProjectItems.value.map((item) => (
    sameProject(item, project) ? { ...item, ...project } : item
  ))
  if (String(activeProjectId.value) === String(projectKey(project))) activeProject.value = { ...activeProject.value, ...project }
}

function handleProjectDuplicated(project) {
  if (!projectKey(project)) return
  recentProjectItems.value = [project, ...recentProjectItems.value.filter((item) => !sameProject(item, project))]
}

function handleProjectDeleted(projectId) {
  recentProjectItems.value = recentProjectItems.value.filter((item) => String(projectKey(item)) !== String(projectId) && String(item.id) !== String(projectId))
  if (String(activeProjectId.value) === String(projectId)) {
    activeProjectId.value = null
    activeProject.value = recentProjectItems.value[0] || { id: null, name: '未选择项目', type: '未分类', terminals: 0, files: 0, topologyVersion: 'V0', status: '草稿' }
    activeUserPage.value = 'projects'
    syncChatRoute({ replace: true })
  }
}

function openProject(project) {
  activeProject.value = project
  activeProjectId.value = projectKey(project)
  activeUserPage.value = 'topology'
  syncChatRoute()
}

function handleChatCreated(session) {
  const publicId = session?.publicId
  if (!publicId) return
  pendingChatIds.add(publicId)
  mergeConversation(session)
  activeChatId.value = String(publicId)
  activeProjectId.value = session.projectId ?? activeProjectId.value
  const sessionProject = recentProjectItems.value.find((item) => String(item.id) === String(session.projectId) || String(item.publicId) === String(session.projectId))
  if (sessionProject) activeProjectId.value = projectKey(sessionProject)
  activeUserPage.value = 'chat'
  syncChatRoute()
}

async function handleResourceUnavailable(resource) {
  if (resource?.type === 'conversation') {
    pendingChatIds.delete(resource.id)
    recentChats.value = recentChats.value.filter((chat) => chat.id !== resource.id)
    if (activeChatId.value === resource.id) {
      activeChatId.value = null
      activeUserPage.value = 'new-chat'
      syncBrowserPath('/new', { replace: true })
    }
    showAccessNotice('该会话不存在或无法访问')
    try { await loadUserWorkspace() } catch { /* The server remains authoritative on the next request. */ }
  }
}

async function toggleChatPin(chatId) {
  const chat = recentChats.value.find((item) => item.id === chatId)
  if (!chat) return
  try {
    mergeConversation(await updateConversation(chatId, { pinned: !chat.pinned }))
  } catch {
    // The existing server value remains visible after a failed request.
  }
}

async function removeChat(chatId) {
  try {
    await deleteConversation(chatId)
    pendingChatIds.delete(chatId)
    recentChats.value = recentChats.value.filter((chat) => chat.id !== chatId)
    if (activeChatId.value === chatId) selectPage('new-chat')
  } catch {
    // A failed deletion leaves the current conversation intact.
  }
}

function syncChatRoute({ replace = false } = {}) {
  if (activeRole.value !== 'user') return
  const nextPath = userPathFor(activeUserPage.value, activeProjectId.value || activeProject.value?.id, activeChatId.value)

  try {
    if (window.location.pathname === nextPath) return
    window.history[replace ? 'replaceState' : 'pushState']({}, '', nextPath)
  } catch {
    // The in-memory page state remains usable when browser history is unavailable.
  }
}

function syncAdminRoute({ replace = false } = {}) {
  if (activeRole.value !== 'admin') return
  syncBrowserPath(adminPathFor(activeAdminPage.value), { replace })
}

function syncBrowserPath(nextPath, { replace = false } = {}) {
  try {
    const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
    if (currentPath === nextPath) return
    window.history[replace ? 'replaceState' : 'pushState']({}, '', nextPath)
  } catch {
    // The in-memory page state remains usable when browser history is unavailable.
  }
}

function readSavedAppMode() {
  try {
    const savedMode = window.localStorage.getItem(APP_MODE_STORAGE_KEY)
    return savedMode === 'admin' || savedMode === 'user' ? savedMode : null
  } catch {
    return null
  }
}

function currentBrowserPath() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`
}

function saveAuthRedirect(pathname = currentBrowserPath()) {
  if (!pathname || pathname === '/' || pathname === '/login') return
  try { window.sessionStorage.setItem(AUTH_REDIRECT_STORAGE_KEY, pathname) } catch { /* Preserve login flow when storage is unavailable. */ }
}

function consumeAuthRedirect() {
  try {
    const redirect = window.sessionStorage.getItem(AUTH_REDIRECT_STORAGE_KEY)
    window.sessionStorage.removeItem(AUTH_REDIRECT_STORAGE_KEY)
    return redirect && redirect.startsWith('/') && !redirect.startsWith('/api/') ? redirect : ''
  } catch {
    return ''
  }
}

function persistAppMode(mode) {
  try {
    window.localStorage.setItem(APP_MODE_STORAGE_KEY, mode)
  } catch {
    // The active view remains available when browser storage is unavailable.
  }
}

function applyRoute(route) {
  routeStatus.value = route.mode === 'not-found' ? 'not-found' : route.mode === 'forbidden' ? 'forbidden' : 'ready'
  const canUseAdmin = currentUser.value?.role === 'ADMIN'
  activeRole.value = route.mode === 'admin' && canUseAdmin ? 'admin' : 'user'

  if (activeRole.value === 'admin') {
    activeAdminPage.value = route.adminPage
    return
  }

  activeChatId.value = route.chatId
  activeProjectId.value = route.projectId || null
  if (route.projectId) {
    const project = recentProjectItems.value.find((item) => String(projectKey(item)) === String(route.projectId) || String(item.id) === String(route.projectId))
    if (project) activeProject.value = project
  }
  activeUserPage.value = route.userPage
  if (!route.chatId) newChatTitle.value = '新对话'
}

function showAccessNotice(message = '该会话不存在或无法访问') {
  accessNotice.value = message
  window.clearTimeout(accessNoticeTimer)
  accessNoticeTimer = window.setTimeout(() => { accessNotice.value = '' }, 2600)
}

function restoreRouteFromLocation() {
  const pathname = window.location.pathname
  const route = resolveAuthenticatedAppRoute({
    pathname,
    savedMode: readSavedAppMode(),
    adminPages: adminPageKeys,
    isAdmin: currentUser.value?.role === 'ADMIN'
  })
  applyRoute(route)
  persistAppMode(activeRole.value)

  if (route.mode === 'not-found' || route.mode === 'forbidden') {
    activeChatId.value = null
    activeProjectId.value = null
    activeUserPage.value = 'new-chat'
    routeStatus.value = 'ready'
    showAccessNotice('该页面不存在或无法访问')
    syncBrowserPath('/new', { replace: true })
    return
  }
  const canonicalAdminPath = adminPathFor(route.adminPage)
  if (activeRole.value === 'admin' && pathname !== canonicalAdminPath) {
    syncBrowserPath(canonicalAdminPath, { replace: true })
  }
}

function changeRole(role) {
  if (role !== 'admin' && role !== 'user') return
  if (role === 'admin' && currentUser.value?.role !== 'ADMIN') return

  activeRole.value = role
  persistAppMode(role)

  if (role === 'admin') {
    if (!adminPageKeys.includes(activeAdminPage.value)) activeAdminPage.value = 'dashboard'
    syncAdminRoute()
    return
  }

  activeUserPage.value = activeChatId.value ? 'chat' : 'new-chat'
  activeProjectId.value = null
  if (!activeChatId.value) newChatSessionKey.value += 1
  syncChatRoute()
}

function resolveTheme(preference) {
  if (preference !== 'system') return preference
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(preference, { persist = true } = {}) {
  if (!['light', 'dark', 'system'].includes(preference)) return
  themePreference.value = preference
  document.documentElement.dataset.theme = resolveTheme(preference)

  if (persist) {
    try {
      window.localStorage.setItem('topoplan.theme-preference', preference)
    } catch {
      // Theme switching remains available for the current page session.
    }
  }
}

function toggleTheme() {
  applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark')
}

function restoreAdminShellState() {
  try {
    const savedTheme = window.localStorage.getItem('topoplan.theme-preference')
    if (['light', 'dark', 'system'].includes(savedTheme)) themePreference.value = savedTheme
    const savedCollapseState = window.localStorage.getItem('topoplan.admin.sidebar-collapsed')
    if (!introPlaying.value && savedCollapseState !== null) sidebarCollapsed.value = savedCollapseState === 'true'
  } catch {
    // The in-memory defaults remain available in privacy-restricted sessions.
  }
  applyTheme(themePreference.value, { persist: false })
}

function handleThemeChange(event) {
  applyTheme(event.detail, { persist: false })
}

watch(sidebarCollapsed, (collapsed) => {
  try {
    window.localStorage.setItem('topoplan.admin.sidebar-collapsed', String(collapsed))
  } catch {
    // Sidebar state remains available while the page is open.
  }
})

watch(isAdminPromptPage, syncPromptViewportLock, { immediate: true })
watch(isAdminAiEnginePage, syncAiEngineViewportLock, { immediate: true })
watch(isAdminSystemManagementPage, syncSystemManagementViewportLock, { immediate: true })
watch(isAdminSecurityPage, syncSecurityViewportLock, { immediate: true })
watch(isAdminUserAccessPage, syncUserAccessViewportLock, { immediate: true })

function signIn(user) {
  currentUser.value = user
  isAuthenticated.value = true
  const redirect = consumeAuthRedirect()
  if (redirect) syncBrowserPath(redirect, { replace: true })
  restoreRouteFromLocation()
}

async function loadUserWorkspace() {
  const loadSequence = ++workspaceLoadSequence
  const routedProjectRequest = activeProjectId.value
    ? getProject(activeProjectId.value)
        .then((project) => ({ project, error: null }))
        .catch((error) => ({ project: null, error }))
    : Promise.resolve({ project: null, error: null })
  const [projectData, sessionData, routedProjectResult] = await Promise.all([
    listProjects(),
    listConversations(),
    routedProjectRequest
  ])
  if (loadSequence !== workspaceLoadSequence) return
  const projectItems = projectData.items ?? []
  recentProjectItems.value = projectItems
  const routedProject = activeProjectId.value
    ? (routedProjectResult.project ?? projectItems.find((item) => String(projectKey(item)) === String(activeProjectId.value) || String(item.id) === String(activeProjectId.value)))
    : null
  activeProject.value = activeProjectId.value
    ? (routedProject ?? activeProject.value)
    : (projectItems[0] ?? activeProject.value)
  if (activeProjectId.value && routedProject) {
    const canonicalProjectKey = projectKey(routedProject)
    if (canonicalProjectKey && String(activeProjectId.value) !== String(canonicalProjectKey)) {
      activeProjectId.value = canonicalProjectKey
      syncChatRoute({ replace: true })
    }
  }
  if (activeProjectId.value && !routedProject) {
    activeProjectId.value = null
    activeProject.value = projectItems[0] ?? activeProject.value
    activeUserPage.value = 'projects'
    routeStatus.value = 'ready'
    showAccessNotice('该项目不存在或无法访问')
    syncBrowserPath('/projects', { replace: true })
  }
  const loadedChats = (sessionData.items ?? []).filter((session) => session?.projectId == null && String(session?.conversationType || 'GENERAL_CHAT').toUpperCase() !== 'PROJECT_CHAT' && typeof session.publicId === 'string' && session.publicId.length === 36).map((session) => ({
    id: session.publicId,
    publicId: session.publicId,
    title: session.title,
    titleStatus: session.titleStatus,
    pinned: session.pinned,
    projectId: session.projectId,
    conversationType: session.conversationType,
    updatedAt: session.updatedAt
  }))
  const loadedChatIds = new Set(loadedChats.map((chat) => chat.id))
  const locallyCreatedChats = recentChats.value.filter((chat) => pendingChatIds.has(chat.id) && !loadedChatIds.has(chat.id))
  recentChats.value = [...loadedChats, ...locallyCreatedChats]
    .sort((left, right) => Number(right.pinned) - Number(left.pinned))
  loadedChats.forEach((chat) => pendingChatIds.delete(chat.id))
  if (activeChatId.value && !recentChats.value.some((chat) => chat.id === activeChatId.value)
      && !pendingChatIds.has(activeChatId.value)) {
    activeChatId.value = null
    activeUserPage.value = 'new-chat'
    routeStatus.value = 'ready'
    showAccessNotice('该会话不存在或无法访问')
    syncBrowserPath('/new', { replace: true })
  }
}

async function handleAuthSubmit(payload) {
  authError.value = ''

  const { mode, username, password } = payload

  if (mode === 'reset') {
    authError.value = '密码重置接口尚未开放，请联系管理员处理。'
    return
  }

  try {
    const user = mode === 'register'
      ? await register(payload)
      : await login(username, password)
    await loadUserWorkspace()
    signIn(user)
  } catch (error) {
    authError.value = error.message || '登录失败，请稍后重试。'
  }
}

async function logout() {
  try {
    await revokeRefreshToken()
    await logoutRequest()
    window.history.replaceState({}, '', '/login')
  } catch {
    // Return to the login view even when the network request could not complete.
  }
  activeChatId.value = null
  activeProjectId.value = null
  recentProjectItems.value = []
  activeProject.value = { id: null, name: '未选择项目', type: '未分类', terminals: 0, files: 0, topologyVersion: 'V0', status: '草稿' }
  newChatTitle.value = '新对话'
  activeUserPage.value = 'new-chat'
  activeRole.value = 'user'
  currentUser.value = null
  recentChats.value = []
  pendingChatIds.clear()
  newChatSessionKey.value += 1
  isAuthenticated.value = false
  authStatus.value = 'unauthenticated'
  clearSession()
}

function handleAuthInvalid(event) {
  const accountDisabled = event.detail?.code === 'ACCOUNT_DISABLED'
  authError.value = accountDisabled ? '账号已被停用，请联系管理员。' : ''
  activeChatId.value = null
  activeProjectId.value = null
  recentProjectItems.value = []
  activeProject.value = { id: null, name: '未选择项目', type: '未分类', terminals: 0, files: 0, topologyVersion: 'V0', status: '草稿' }
  activeUserPage.value = 'new-chat'
  activeRole.value = 'user'
  currentUser.value = null
  recentChats.value = []
  pendingChatIds.clear()
  isAuthenticated.value = false
  authStatus.value = 'unauthenticated'
  clearSession()
  saveAuthRedirect()
  syncBrowserPath('/login', { replace: true })
}

async function initializeApp() {
  restoreAdminShellState()
  let user
  try {
    const session = await refreshSession()
    user = session?.user || session
    if (!user?.id) user = await getCurrentUser()
  } catch (error) {
    try {
      user = await getCurrentUser()
    } catch (fallbackError) {
      const sessionInvalid = fallbackError?.status === 401 || error?.status === 401
        || fallbackError?.status === 403 || error?.status === 403
        || ['UNAUTHORIZED', 'ACCOUNT_DISABLED', 'REFRESH_INVALID', 'REFRESH_REUSED'].includes(fallbackError?.code || error?.code)
      authStatus.value = sessionInvalid ? 'unauthenticated' : 'error'
      isAuthenticated.value = false
      if (sessionInvalid) {
        saveAuthRedirect()
        if (window.location.pathname !== '/login') syncBrowserPath('/login', { replace: true })
      } else {
        authError.value = fallbackError?.message || error?.message || '暂时无法恢复登录状态，请检查服务连接。'
      }
      isInitializing.value = false
      return
    }
  }
  currentUser.value = user
  authStatus.value = 'authenticated'
  isAuthenticated.value = true
  restoreRouteFromLocation()
  try {
    await loadUserWorkspace()
  } catch {
    // A workspace data failure does not invalidate a healthy server Session.
  }
  isInitializing.value = false
}

onMounted(() => {
  initializeApp()
  window.addEventListener('popstate', restoreRouteFromLocation)
  window.addEventListener('topoplan-theme-change', handleThemeChange)
  window.addEventListener('topoplan-auth-invalid', handleAuthInvalid)
})

onBeforeUnmount(() => {
  window.removeEventListener('popstate', restoreRouteFromLocation)
  window.removeEventListener('topoplan-theme-change', handleThemeChange)
  window.removeEventListener('topoplan-auth-invalid', handleAuthInvalid)
  syncPromptViewportLock(false)
  syncAiEngineViewportLock(false)
  syncSystemManagementViewportLock(false)
  syncSecurityViewportLock(false)
  syncUserAccessViewportLock(false)
})
</script>

<template>
  <main v-if="isInitializing" class="app-initializing" aria-busy="true" aria-live="polite">
    <span class="app-initializing-skeleton" aria-hidden="true"><i></i><i></i><i></i></span>
    <span>正在恢复工作区...</span>
  </main>

  <AuthPage
    v-else-if="!isAuthenticated"
    :error-message="authError"
    @submit="handleAuthSubmit"
    @clear-error="authError = ''"
  />

  <main
    v-else
    class="app-shell"
    :class="{
      'chat-mode': isUserAssistantPage,
      'admin-shell': activeRole === 'admin',
      'prompt-page-active': isAdminPromptPage,
      'ai-engine-page-active': isAdminAiEnginePage,
      'system-management-page-active': isAdminSystemManagementPage,
      'security-page-active': isAdminSecurityPage,
      'user-access-page-active': isAdminUserAccessPage,
      'topology-editor-active': isTopologyEditorPage,
      'sidebar-collapsed': sidebarCollapsed,
      'intro-sequence': introPlaying
    }"
  >
    <p v-if="accessNotice" class="app-access-notice" role="status" aria-live="polite">{{ accessNotice }}</p>
    <SidebarNav
      :class="{ 'is-collapsed': sidebarCollapsed }"
      :collapsed="sidebarCollapsed"
      :active-role="activeRole"
      :active-key="activeKey"
      :active-chat-id="activeChatId"
      :recent-chats="recentChats"
      :items="navItems"
      :project="activeProject"
      @select="selectPage"
      @open-chat="openChat"
      @rename-chat="renameChat"
      @toggle-chat-pin="toggleChatPin"
      @delete-chat="removeChat"
      @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
      @logout="logout"
      @switch-account="logout"
    />

    <section class="main-region">
      <AppHeader
        v-if="!isUserAssistantPage && !isTopologyEditorPage && activeUserPage !== 'projects'"
        :title="pageTitle"
        :active-role="activeRole"
        :active-page="activeAdminPage"
        :theme="themePreference"
        save-state="本地原型"
        @change-role="changeRole"
        @toggle-theme="toggleTheme"
        @logout="logout"
      />

      <Transition name="workspace-view" mode="out-in">
        <UserWorkspace
          v-if="activeRole === 'user'"
          :active-page="activeUserPage"
          :chat-id="activeChatId"
          :chat-title="activeChat?.title ?? newChatTitle"
          :chat-pinned="activeChat?.pinned ?? false"
          :project="activeProject"
          :new-chat-session-key="newChatSessionKey"
          :recent-chats="recentChats"
          :project-items="recentProjectItems"
          @select="selectPage"
          @open-chat="openChat"
          @rename-chat="renameChat"
          @rename-new-chat="renameNewChat"
          @toggle-chat-pin="toggleChatPin"
          @project-created="handleProjectCreated"
          @project-updated="handleProjectUpdated"
          @project-duplicated="handleProjectDuplicated"
          @project-deleted="handleProjectDeleted"
          @open-project="openProject"
          @chat-created="handleChatCreated"
          @chat-updated="mergeConversation"
          @resource-unavailable="handleResourceUnavailable"
        />
        <AdminWorkspace
          v-else
          :key="activeAdminPage"
          :active-page="activeAdminPage"
          :theme="themePreference"
        />
      </Transition>
    </section>
  </main>
</template>

