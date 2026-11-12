<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { createAdminUser, listAdminLoginRecords, listAdminUsers, listBillingTiers, resetAdminUserPassword, saveBillingQuota, setAdminUserStatus, updateAdminUser } from '../../api/admin'
import {
  Ban,
  ChevronLeft,
  ChevronRight,
  Copy,
  Ellipsis,
  KeyRound,
  Mail,
  RotateCcw,
  ShieldCheck,
  SlidersHorizontal,
  UserPlus,
  UserRound,
  X
} from '@lucide/vue'

const roleOptions = [
  { key: 'admin', label: '系统管理员', description: '拥有后台管理、模型配置与账单查看权限。', icon: ShieldCheck },
  { key: 'user', label: '普通用户', description: '可使用网络拓扑规划与平台 AI 能力。', icon: UserRound }
]

const users = ref([])
const planOptions = ref([])

const query = ref('')
const roleFilter = ref('全部角色')
const planFilter = ref('全部用户')
const statusFilter = ref('全部状态')
const currentPage = ref(1)
const pageSize = ref(10)
const pageSizes = [10, 20, 50]
const quotaModalOpen = ref(false)
const selectedUser = ref(null)
const quotaDraft = ref(0)
const drawerOpen = ref(false)
const editingUserId = ref(null)
const activeActionMenuId = ref(null)
const actionMenuPosition = ref({ top: 0, left: 0 })
const userDraft = ref(createUserDraft())
const toast = ref('')
let toastTimer = 0
const isLoading = ref(true)
const isSaving = ref(false)
const totalUsers = ref(0)
const recentLoginEntries = ref([])
const isLoginRecordsLoading = ref(false)

function canonicalTier(value) {
  const code = String(value || '').trim().toUpperCase()
  if (['FREE', 'NORMAL', 'BASIC'].includes(code)) return 'FREE'
  if (['STANDARD', 'TEAM', 'EDUCATION'].includes(code)) return 'STANDARD'
  if (['PRO', 'PREMIUM', 'ADVANCED', 'ENTERPRISE'].includes(code)) return 'PRO'
  return 'FREE'
}

function planForTier(tier) {
  return planOptions.value.find((plan) => plan.code === canonicalTier(tier)) || null
}

function mapUser(raw) {
  const roleKey = String(raw.role || 'USER').toLowerCase()
  const statusKey = String(raw.status || 'ACTIVE').toUpperCase()
  const tier = canonicalTier(raw.tier)
  const subscription = raw.subscription || {}
  const summary = raw.usageSummary || {}
  const plan = planForTier(tier)
  return {
    ...raw,
    tier,
    subscription,
    userId: raw.userId || raw.id || '-',
    name: raw.displayName || raw.username,
    plan: subscription.levelName || plan?.backendName || '-',
    planUserName: subscription.planName || plan?.userName || '-',
    systemRole: roleKey === 'admin' ? '系统管理员' : '普通用户',
    systemRoleKey: roleKey,
    quota: Number(summary.aiChatTotal ?? subscription.quota?.aiChat?.total ?? 0),
    aiRemaining: Number(summary.aiChatRemaining ?? subscription.remaining?.aiChat?.value ?? 0),
    topologyDraws: Number(summary.topologyUsed ?? subscription.usage?.topologyGeneration?.used ?? 0),
    topologyTotal: Number(summary.topologyTotal ?? subscription.quota?.topologyGeneration?.total ?? 0),
    aiCalls: Number(summary.aiChatUsed ?? subscription.usage?.aiChat?.used ?? 0),
    tokenUsed: Number(summary.tokenUsed ?? subscription.usage?.tokens?.used ?? 0),
    tokenTotal: Number(summary.tokenTotal ?? subscription.quota?.tokens?.total ?? 0),
    knowledgeQaUsed: Number(subscription.usage?.knowledgeQa?.used ?? 0),
    knowledgeQaTotal: Number(subscription.quota?.knowledgeQa?.total ?? 0),
    extraAiCalls: Number(subscription.additionalAiCalls ?? 0),
    exportCount: raw.exportCount ?? 0,
    riskStatus: raw.riskStatus || '正常',
    status: statusKey === 'DISABLED' ? '已禁用' : '正常',
    registeredAt: raw.createdAt ? new Date(raw.createdAt).toLocaleDateString('zh-CN') : '-',
    lastLogin: raw.lastLoginAt ? new Date(raw.lastLoginAt).toLocaleString('zh-CN') : '尚未登录'
  }
}

async function loadUsers() {
  isLoading.value = true
  try {
    const [data, tiers] = await Promise.all([listAdminUsers({ page: 0, size: 100 }), listBillingTiers()])
    planOptions.value = (tiers || []).map((tier) => ({
      code: canonicalTier(tier.code),
      backendName: tier.backendName || tier.name || '-',
      userName: tier.userName || tier.name || '-',
      description: tier.description || '',
      quota: tier.aiCallQuota
    }))
    users.value = (data?.items || []).map(mapUser)
    totalUsers.value = data?.total ?? users.value.length
  } catch (error) {
    showToast(error.message || '用户加载失败')
  } finally {
    isLoading.value = false
  }
}

const filteredUsers = computed(() => users.value.filter((user) => {
  const normalizedQuery = query.value.trim().toLowerCase()
  const matchesQuery = !normalizedQuery || [user.name, user.email, user.username, user.userId].some((value) => String(value || '').toLowerCase().includes(normalizedQuery))
  const matchesRole = roleFilter.value === '全部角色' || user.systemRole === roleFilter.value
  const matchesPlan = planFilter.value === '全部用户' || user.tier === planFilter.value
  const matchesStatus = statusFilter.value === '全部状态' || user.status === statusFilter.value
  return matchesQuery && matchesRole && matchesPlan && matchesStatus
}))

const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize.value)))
const paginatedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})
const userPageNumbers = computed(() => {
  const start = Math.max(1, Math.min(currentPage.value - 1, totalPages.value - 2))
  return Array.from({ length: Math.min(3, totalPages.value) }, (_, index) => start + index)
})

const editingUser = computed(() => users.value.find((user) => user.id === editingUserId.value) ?? null)
const actionMenuUser = computed(() => users.value.find((user) => user.id === activeActionMenuId.value) ?? null)
watch([query, roleFilter, planFilter, statusFilter, pageSize], () => { currentPage.value = 1 })
watch(totalPages, (pages) => { if (currentPage.value > pages) currentPage.value = pages })

function createUserDraft(user = null) {
  return {
    username: user?.username ?? '',
    name: user?.name ?? '',
    email: user?.email ?? '',
    password: '',
    systemRoleKey: user?.systemRoleKey ?? 'user',
    tier: user?.tier ?? 'FREE'
  }
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 3000)
}

function openQuotaModal(user) {
  selectedUser.value = user
  quotaDraft.value = Number(user.extraAiCalls ?? 0)
  quotaModalOpen.value = true
  activeActionMenuId.value = null
}

function closeQuotaModal() {
  quotaModalOpen.value = false
  selectedUser.value = null
}

async function saveQuota() {
  if (!selectedUser.value) return
  const nextQuota = Math.max(0, Number(quotaDraft.value) || 0)
  const user = selectedUser.value
  isSaving.value = true
  try {
    await saveBillingQuota(user.id, { tier: user.tier, customDailyCalls: nextQuota })
    await loadUsers()
    closeQuotaModal()
    showToast(`已将 ${user.name} 的额外 AI 调用额度更新为 ${nextQuota} 次`)
  } catch (error) {
    showToast(error.message || '额度保存失败')
  } finally {
    isSaving.value = false
  }
}

function openUserDrawer(user = null) {
  editingUserId.value = user?.id ?? null
  userDraft.value = createUserDraft(user)
  recentLoginEntries.value = []
  drawerOpen.value = true
  activeActionMenuId.value = null
  if (user) loadUserLoginRecords(user)
}

function openUserLoginRecords(user) {
  openUserDrawer(user)
}

function closeUserDrawer() {
  drawerOpen.value = false
  editingUserId.value = null
  userDraft.value = createUserDraft()
  recentLoginEntries.value = []
}

function formatLoginTimestamp(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

async function loadUserLoginRecords(user) {
  isLoginRecordsLoading.value = true
  try {
    const data = await listAdminLoginRecords({ page: 0, size: 5, userId: user.id })
    if (editingUserId.value !== user.id) return
    recentLoginEntries.value = (data?.items || []).map((record) => ({
      id: record.id,
      time: formatLoginTimestamp(record.createdAt),
      method: record.loginMethod || '账号密码',
      device: `${record.device || '未知设备'} · ${record.ipAddress || '未知IP'}`,
      result: record.result || '成功'
    }))
  } catch (error) {
    if (editingUserId.value === user.id) showToast(error.message || '登录记录加载失败')
  } finally {
    if (editingUserId.value === user.id) isLoginRecordsLoading.value = false
  }
}

function changePage(page) {
  currentPage.value = Math.max(1, Math.min(totalPages.value, page))
}

function getRoleOption(roleKey) {
  return roleOptions.find((option) => option.key === roleKey) ?? roleOptions[1]
}

function buildUpdatePayload(user, overrides = {}) {
  return {
    username: overrides.username ?? user.username,
    displayName: overrides.displayName ?? user.name,
    email: overrides.email ?? user.email,
    role: overrides.role ?? (user.systemRoleKey === 'admin' ? 'ADMIN' : 'USER'),
    status: overrides.status ?? (user.status === '已禁用' ? 'DISABLED' : 'ACTIVE'),
    tier: overrides.tier ?? user.tier,
    dailyQuota: 0,
    password: overrides.password || undefined,
    updatedAt: user.updatedAt
  }
}

async function saveUser() {
  const draft = userDraft.value
  const role = getRoleOption(draft.systemRoleKey)
  const tier = canonicalTier(draft.tier)
  isSaving.value = true
  try {
    const payload = { username: draft.username.trim(), displayName: draft.name.trim(), email: draft.email.trim(), role: role.key.toUpperCase(), tier, dailyQuota: 0 }
    const saved = editingUser.value
      ? await updateAdminUser(editingUser.value.id, { ...buildUpdatePayload(editingUser.value, payload), password: draft.password || undefined })
      : await createAdminUser({ username: draft.username.trim(), password: draft.password, displayName: draft.name.trim(), email: draft.email.trim(), role: role.key.toUpperCase(), tier, dailyQuota: 0 })
    if (editingUser.value) Object.assign(editingUser.value, mapUser(saved))
    else users.value.unshift(mapUser(saved))
    totalUsers.value = Math.max(totalUsers.value, users.value.length)
    showToast(editingUser.value ? '用户信息已更新' : '用户已创建')
    closeUserDrawer()
  } catch (error) {
    showToast(error.message || '用户保存失败')
  } finally {
    isSaving.value = false
  }
}

function createRandomPassword() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%'
  const values = new Uint32Array(14)
  window.crypto?.getRandomValues?.(values)

  return Array.from(values, (value, index) => alphabet[(value || ((index + 17) * 37)) % alphabet.length]).join('')
}

async function copyToClipboard(value) {
  try {
    await navigator.clipboard?.writeText(value)
    return true
  } catch {
    return false
  }
}

async function generateAndCopyPassword() {
  userDraft.value.password = createRandomPassword()
  const copied = await copyToClipboard(userDraft.value.password)
  showToast(copied ? '已生成随机密码并复制到剪贴板' : '已生成随机密码，请从输入框手动复制')
}

function toggleActionMenu(event, user) {
  event.stopPropagation()
  if (activeActionMenuId.value === user.id) {
    activeActionMenuId.value = null
    return
  }

  const rect = event.currentTarget.getBoundingClientRect()
  actionMenuPosition.value = {
    top: rect.bottom + 6,
    left: Math.max(12, rect.right - 190)
  }
  activeActionMenuId.value = user.id
}

async function resetPassword(user) {
  activeActionMenuId.value = null
  try {
    const result = await resetAdminUserPassword(user.id)
    const copied = await copyToClipboard(result.temporaryPassword)
    showToast(copied ? `已重置 ${user.name} 的密码并复制临时密码` : `已重置 ${user.name} 的密码：${result.temporaryPassword}`)
  } catch (error) {
    showToast(error.message || '密码重置失败')
  }
}

async function resetQuota(user) {
  activeActionMenuId.value = null
  try {
    await saveBillingQuota(user.id, { tier: user.tier, customDailyCalls: 0 })
    await loadUsers()
    showToast(`已将 ${user.name} 的额外 AI 调用额度恢复为 0`)
  } catch (error) {
    showToast(error.message || '额度重置失败')
  }
}

async function toggleAccountStatus(user) {
  const nextStatus = user.status === '已禁用' ? 'ACTIVE' : 'DISABLED'
  activeActionMenuId.value = null
  try {
    const updated = await setAdminUserStatus(user.id, nextStatus)
    Object.assign(user, mapUser(updated))
    showToast(nextStatus === 'DISABLED' ? `已禁用 ${user.name} 的账户` : `已恢复 ${user.name} 的账户`)
  } catch (error) {
    showToast(error.message || '状态更新失败')
  }
}

function handleDocumentPointerDown(event) {
  if (!event.target.closest('.admin-user-actions') && !event.target.closest('.admin-user-action-menu')) {
    activeActionMenuId.value = null
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  loadUsers()
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  window.clearTimeout(toastTimer)
})
</script>

<template>
  <section class="admin-section-page admin-users-page">
    <header class="admin-page-intro compact">
      <div>
        <p>用户与权限 / 用户管理</p>
        <h2>用户管理</h2>
        <span>集中管理平台用户账号、角色、等级及资源使用情况。</span>
      </div>
    </header>

    <section class="admin-card admin-table-card">
      <div class="admin-table-toolbar">
        <label class="admin-filter-search"><input v-model="query" type="search" placeholder="搜索账号 / 邮箱 / 用户 ID" /></label>
        <select v-model="roleFilter" aria-label="用户角色筛选"><option>全部角色</option><option>系统管理员</option><option>普通用户</option></select>
        <select v-model="planFilter" aria-label="用户等级筛选"><option value="全部用户">全部用户</option><option v-for="plan in planOptions" :key="plan.code" :value="plan.code">{{ plan.backendName }}</option></select>
        <select v-model="statusFilter" aria-label="用户状态筛选"><option>全部状态</option><option>正常</option><option>已禁用</option></select>
        <button class="admin-user-add" type="button" @click="openUserDrawer()"><UserPlus :size="16" /> 添加用户</button>
      </div>

      <div class="admin-table-scroll">
        <table class="admin-data-table admin-users-table">
          <thead><tr><th>用户</th><th>用户 ID</th><th>角色</th><th>用户等级</th><th>AI 剩余额度</th><th>拓扑绘制次数</th><th>账号状态</th><th>注册时间</th><th>最近登录</th><th aria-label="操作"></th></tr></thead>
          <tbody>
            <tr v-for="user in paginatedUsers" :key="user.id">
              <td><div class="admin-user-cell"><span>{{ user.name.charAt(0).toUpperCase() }}</span><div><strong>{{ user.name }}</strong><small>{{ user.email }}</small><small class="admin-user-login-account">登录账号：{{ user.username || '-' }}</small></div></div></td>
              <td><code class="admin-user-id">{{ user.userId }}</code></td>
              <td><span class="admin-system-role-pill" :class="`is-${user.systemRoleKey}`">{{ user.systemRole }}</span></td>
              <td><span class="admin-plan-pill">{{ user.plan }}</span></td>
              <td><strong>{{ user.aiRemaining.toLocaleString() }}</strong><small class="admin-unit">次</small></td>
              <td><strong>{{ user.topologyDraws.toLocaleString() }}</strong><small class="admin-unit">次</small></td>
              <td><span class="admin-status-pill" :class="{ 'is-disabled': user.status === '已禁用' }">{{ user.status }}</span></td>
              <td>{{ user.registeredAt }}</td>
              <td>{{ user.lastLogin }}</td>
              <td>
                <div class="admin-user-actions">
                  <button class="admin-user-more" type="button" :aria-label="`${user.name} 的更多操作`" :aria-expanded="activeActionMenuId === user.id" @click="toggleActionMenu($event, user)"><Ellipsis :size="18" /></button>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredUsers.length"><td class="admin-users-empty" colspan="10">未找到匹配的用户</td></tr>
          </tbody>
        </table>
      </div>
      <footer class="admin-users-pagination" aria-label="用户分页">
        <p>共 {{ totalUsers || filteredUsers.length }} 个用户</p>
        <div>
          <label>每页<select v-model.number="pageSize" aria-label="每页用户数"><option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option></select></label>
          <button type="button" :disabled="currentPage === 1" aria-label="上一页" @click="changePage(currentPage - 1)"><ChevronLeft :size="15" /></button>
          <button v-for="page in userPageNumbers" :key="page" type="button" :class="{ active: currentPage === page }" @click="changePage(page)">{{ page }}</button>
          <span v-if="totalPages > 4">...</span>
          <button type="button" :disabled="currentPage === totalPages" aria-label="下一页" @click="changePage(currentPage + 1)"><ChevronRight :size="15" /></button>
        </div>
      </footer>
    </section>

    <Teleport to="body">
      <Transition name="admin-user-menu">
        <div v-if="actionMenuUser" class="admin-user-action-menu" :style="{ top: `${actionMenuPosition.top}px`, left: `${actionMenuPosition.left}px` }" role="menu" @pointerdown.stop>
          <button type="button" role="menuitem" @click="openUserDrawer(actionMenuUser)"><UserRound :size="15" /> 查看用户资料（编辑信息/权限）</button>
          <button type="button" role="menuitem" @click="openUserDrawer(actionMenuUser)"><SlidersHorizontal :size="15" /> 调整等级</button>
          <button type="button" role="menuitem" @click="openQuotaModal(actionMenuUser)"><SlidersHorizontal :size="15" /> 调整配额</button>
          <button type="button" role="menuitem" @click="resetQuota(actionMenuUser)"><RotateCcw :size="15" /> 重置额度</button>
          <button type="button" role="menuitem" @click="openUserLoginRecords(actionMenuUser)"><KeyRound :size="15" /> 查看登录记录</button>
          <button class="is-danger" type="button" role="menuitem" @click="toggleAccountStatus(actionMenuUser)"><Ban :size="15" /> {{ actionMenuUser.status === '已禁用' ? '恢复账户' : '禁用账户' }}</button>
        </div>
      </Transition>
    </Teleport>

    <Transition name="admin-user-drawer">
      <div v-if="drawerOpen" class="admin-user-drawer-backdrop" @pointerdown.self="closeUserDrawer">
        <aside class="admin-user-drawer" role="dialog" aria-modal="true" :aria-label="editingUser ? '编辑用户与权限' : '添加用户'">
          <header class="admin-user-drawer-header">
            <div><span>{{ editingUser ? '账户与权限' : '创建新账户' }}</span><h3>{{ editingUser ? `编辑 ${editingUser.name}` : '添加用户' }}</h3></div>
            <button type="button" class="admin-close-button" aria-label="关闭抽屉" @click="closeUserDrawer"><X :size="18" /></button>
          </header>

          <form class="admin-user-drawer-form" @submit.prevent="saveUser">
            <section v-if="editingUser" class="admin-user-drawer-section admin-user-summary-section">
              <header><UserRound :size="16" /><div><h4>用户概览</h4><p>后台仅可查看账号级统计，无法访问用户项目内容。</p></div></header>
              <dl class="admin-user-summary-grid">
                <div><dt>用户 ID</dt><dd>{{ editingUser.userId }}</dd></div><div><dt>账号状态</dt><dd>{{ editingUser.status }}</dd></div>
                <div><dt>用户等级</dt><dd>{{ editingUser.plan }}</dd></div><div><dt>用户端套餐</dt><dd>{{ editingUser.planUserName }}</dd></div>
                <div><dt>订阅状态</dt><dd>{{ editingUser.subscription.status === 'ACTIVE' ? '生效中' : '已暂停' }}</dd></div><div><dt>订阅开始</dt><dd>{{ editingUser.subscription.startAt ? new Date(editingUser.subscription.startAt).toLocaleDateString('zh-CN') : '-' }}</dd></div>
                <div><dt>订阅到期</dt><dd>{{ editingUser.subscription.expireAt ? new Date(editingUser.subscription.expireAt).toLocaleDateString('zh-CN') : '长期有效' }}</dd></div><div><dt>额度重置</dt><dd>{{ editingUser.subscription.nextResetAt ? new Date(editingUser.subscription.nextResetAt).toLocaleString('zh-CN', { hour12: false }) : '-' }}</dd></div>
                <div><dt>AI调用额度</dt><dd>{{ editingUser.aiCalls }} / {{ editingUser.quota }} 次</dd></div><div><dt>剩余AI调用</dt><dd>{{ editingUser.aiRemaining }} 次</dd></div>
                <div><dt>Token使用</dt><dd>{{ editingUser.tokenUsed.toLocaleString() }} / {{ editingUser.tokenTotal.toLocaleString() }}</dd></div><div><dt>拓扑生成</dt><dd>{{ editingUser.topologyDraws }} / {{ editingUser.topologyTotal }} 次</dd></div>
                <div><dt>知识库AI问答</dt><dd>{{ editingUser.knowledgeQaUsed }} / {{ editingUser.knowledgeQaTotal }} 次</dd></div><div><dt>风险状态</dt><dd>{{ editingUser.riskStatus }}</dd></div>
                <div><dt>最近登录</dt><dd>{{ editingUser.lastLogin }}</dd></div>
              </dl>
            </section>
            <section v-if="editingUser" id="user-login-records" class="admin-user-drawer-section admin-user-login-section">
              <header><KeyRound :size="16" /><div><h4>登录记录</h4><p>仅展示账号登录行为，不包含用户项目内容。</p></div></header>
              <div v-if="isLoginRecordsLoading" class="admin-user-login-empty">正在读取该用户的登录记录...</div>
              <div v-else-if="recentLoginEntries.length" class="admin-user-login-list">
                <article v-for="entry in recentLoginEntries" :key="entry.time">
                  <div><strong>{{ entry.time }}</strong><small>{{ entry.method }} · {{ entry.device }}</small></div><span>{{ entry.result }}</span>
                </article>
              </div>
              <p v-else class="admin-user-login-empty">该用户暂未留下登录记录。</p>
            </section>
            <section v-if="editingUser" class="admin-user-drawer-section admin-user-status-section">
              <header><Ban :size="16" /><div><h4>账户状态</h4><p>禁用后，该用户将无法继续登录平台。</p></div></header>
              <div class="admin-user-status-action"><span class="admin-status-pill" :class="{ 'is-disabled': editingUser.status === '已禁用' }">{{ editingUser.status }}</span><button type="button" :class="editingUser.status === '已禁用' ? 'admin-ghost-button' : 'admin-danger-button'" @click="toggleAccountStatus(editingUser)">{{ editingUser.status === '已禁用' ? '恢复账户' : '禁用账户' }}</button></div>
            </section>
            <section class="admin-user-drawer-section">
              <header><KeyRound :size="16" /><div><h4>基础账户信息</h4><p>可修改登录用户名、邮箱和密码；用户可使用用户名或邮箱登录。</p></div></header>
              <label><span>登录用户名</span><input v-model="userDraft.username" required minlength="3" maxlength="64" placeholder="例如：topo" /></label>
              <label><span>显示名称</span><input v-model="userDraft.name" required maxlength="32" placeholder="例如：网络拓扑用户" /></label>
              <label><span>登录邮箱</span><div class="admin-user-input-icon"><Mail :size="15" /><input v-model="userDraft.email" required type="email" placeholder="name@topoplan.cn" /></div></label>
              <label><span>{{ editingUser ? '新密码（可选）' : '初始密码' }}</span><div class="admin-user-password-field"><input v-model="userDraft.password" type="text" :required="!editingUser" placeholder="至少 12 位随机密码" /><button type="button" title="生成随机密码并复制" @click="generateAndCopyPassword"><Copy :size="14" /> 生成随机密码并复制</button></div></label>
            </section>

            <section class="admin-user-drawer-section">
              <header><ShieldCheck :size="16" /><div><h4>系统角色分配</h4><p>角色决定后台模块和 API 调用范围。</p></div></header>
              <div class="admin-role-card-grid">
                <label v-for="role in roleOptions" :key="role.key" class="admin-role-card" :class="{ active: userDraft.systemRoleKey === role.key }">
                  <input v-model="userDraft.systemRoleKey" type="radio" name="system-role" :value="role.key" />
                  <component :is="role.icon" :size="17" />
                  <span><strong>{{ role.label }}</strong><small>{{ role.description }}</small></span>
                </label>
              </div>
            </section>

            <section class="admin-user-drawer-section">
              <header><SlidersHorizontal :size="16" /><div><h4>业务套餐绑定</h4><p>套餐作为默认 AI 调用额度的基础配置。</p></div></header>
              <div class="admin-plan-card-grid">
                <label v-for="plan in planOptions" :key="plan.code" class="admin-plan-card" :class="{ active: userDraft.tier === plan.code }">
                  <input v-model="userDraft.tier" type="radio" name="plan" :value="plan.code" />
                  <span><strong>{{ plan.backendName }}</strong><small>{{ plan.code }} · {{ plan.description }}</small></span>
                </label>
              </div>
            </section>

            <footer class="admin-user-drawer-footer"><button type="button" class="admin-ghost-button" @click="closeUserDrawer">取消</button><button type="submit" class="admin-primary-button">{{ editingUser ? '保存用户' : '创建用户' }}</button></footer>
          </form>
        </aside>
      </div>
    </Transition>

    <Transition name="admin-modal">
      <div v-if="quotaModalOpen" class="admin-modal-backdrop" @pointerdown.self="closeQuotaModal">
        <form class="admin-modal-card quota-modal" @submit.prevent="saveQuota">
          <header><div><span>额度控制</span><h3>调整额外 AI 调用额度</h3></div><button type="button" class="admin-close-button" aria-label="关闭" @click="closeQuotaModal"><X :size="18" /></button></header>
          <p>为 <strong>{{ selectedUser?.name }}</strong> 配置套餐之外的额外 AI 调用额度；套餐基础额度仍由用户等级配置统一决定。</p>
          <div class="admin-quota-value"><strong>{{ quotaDraft }}</strong><span>次 / 月</span></div>
          <input v-model.number="quotaDraft" class="admin-range" type="range" min="0" max="10000" step="1" aria-label="额外 AI 调用额度" />
          <label class="admin-number-field">额外额度<input v-model.number="quotaDraft" type="number" min="0" max="10000" /></label>
          <footer><button type="button" class="admin-ghost-button" @click="closeQuotaModal">取消</button><button type="submit" class="admin-primary-button">保存额度</button></footer>
        </form>
      </div>
    </Transition>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>
