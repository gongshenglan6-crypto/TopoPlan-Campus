<script setup>
import { computed, ref, watch } from 'vue'
import {
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  Copy,
  Ellipsis,
  LockKeyhole,
  Pencil,
  Plus,
  Save,
  Search,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  UserPlus,
  UsersRound,
  X
} from '@lucide/vue'

const permissionModules = [
  {
    id: 'overview', label: '系统总览', description: '平台级运行与使用情况', features: [
      { id: 'overview-dashboard', label: '系统总览', permissions: [
        ['overview.statistics', '查看平台统计', 'view'], ['overview.trends', '查看运行趋势', 'view'],
        ['overview.ai-status', '查看AI运行状态', 'view'], ['overview.topology-stats', '查看拓扑使用统计', 'view']
      ] }
    ]
  },
  {
    id: 'users', label: '用户与权限', description: '账号、等级、配额与后台角色', features: [
      { id: 'users-management', label: '用户管理', permissions: [
        ['users.view', '查看用户列表', 'view'], ['users.profile', '查看用户基本资料', 'view'], ['users.status', '修改账号状态', 'edit'],
        ['users.tier', '调整用户等级', 'edit'], ['users.quota', '调整用户配额', 'edit', true], ['users.reset-quota', '重置用户额度', 'edit'], ['users.login-record', '查看登录记录', 'view']
      ] },
      { id: 'users-roles', label: '角色权限', permissions: [
        ['roles.view', '查看角色', 'view'], ['roles.create', '新增角色', 'create'], ['roles.edit', '修改角色', 'edit'],
        ['roles.delete', '删除角色', 'delete'], ['roles.assign', '分配角色', 'edit'], ['roles.configure', '配置权限', 'config', true]
      ] }
    ]
  },
  {
    id: 'resources', label: '资源与配额', description: '使用统计、等级套餐与配额策略', features: [
      { id: 'usage', label: '使用统计', permissions: [['usage.resources', '查看资源统计', 'view'], ['usage.ai', '查看AI调用统计', 'view'], ['usage.tokens', '查看Token统计', 'view'], ['usage.topology', '查看拓扑生成统计', 'view']] },
      { id: 'tiers', label: '用户等级', permissions: [['tiers.view', '查看等级', 'view'], ['tiers.create', '新增等级', 'create'], ['tiers.edit', '修改等级', 'edit'], ['tiers.disable', '停用等级', 'edit']] },
      { id: 'packages', label: '套餐配置', permissions: [['packages.view', '查看套餐', 'view'], ['packages.create', '新增套餐', 'create'], ['packages.edit', '修改套餐', 'edit'], ['packages.toggle', '启用/停用套餐', 'edit']] },
      { id: 'quota', label: '用户配额', permissions: [['quota.view', '查看配额', 'view'], ['quota.edit', '修改配额', 'edit', true], ['quota.reset', '重置配额', 'edit']] }
    ]
  },
  {
    id: 'topology', label: '拓扑资源', description: '平台公共素材与样式库', features: [
      { id: 'icons', label: '设备图标库', permissions: [['icons.view', '查看设备图标', 'view'], ['icons.create', '新增设备图标', 'create'], ['icons.edit', '修改设备图标', 'edit'], ['icons.replace', '替换图标', 'edit'], ['icons.delete', '删除设备图标', 'delete'], ['icons.toggle', '启用/停用图标', 'edit']] },
      { id: 'categories', label: '图标分类', permissions: [['categories.view', '查看分类', 'view'], ['categories.create', '新增分类', 'create'], ['categories.edit', '修改分类', 'edit'], ['categories.delete', '删除分类', 'delete']] },
      { id: 'lines', label: '连接线样式', permissions: [['lines.view', '查看连接样式', 'view'], ['lines.create', '新增连接样式', 'create'], ['lines.edit', '修改连接样式', 'edit'], ['lines.delete', '删除连接样式', 'delete']] },
      { id: 'assets', label: '系统素材', permissions: [['assets.view', '查看素材', 'view'], ['assets.upload', '上传素材', 'create'], ['assets.edit', '修改素材', 'edit'], ['assets.delete', '删除素材', 'delete']] }
    ]
  },
  {
    id: 'knowledge', label: '知识库', description: '公共网络工程资料与检索配置', features: [
      { id: 'knowledge-library', label: '网络工程知识库', permissions: [['knowledge.view', '查看知识库', 'view'], ['knowledge.upload', '上传文档', 'create'], ['knowledge.edit', '修改文档', 'edit'], ['knowledge.delete', '删除文档', 'delete', true], ['knowledge.reparse', '重新解析文档', 'edit'], ['knowledge.categories', '管理知识分类', 'config']] },
      { id: 'rag', label: 'RAG配置', permissions: [['rag.view', '查看RAG配置', 'view'], ['rag.edit', '修改RAG配置', 'config']] },
      { id: 'retrieval-test', label: '检索测试', permissions: [['rag.test', '执行检索测试', 'execute']] }
    ]
  },
  {
    id: 'ai', label: 'AI引擎', description: '模型、Prompt 与联网搜索能力', features: [
      { id: 'models', label: '模型与API', permissions: [['models.view', '查看模型', 'view'], ['models.create', '新增模型', 'create'], ['models.edit', '修改模型', 'edit'], ['models.toggle', '启用/停用模型', 'edit'], ['models.api', '修改API配置', 'config', true], ['models.test', '执行模型测试', 'execute']] },
      { id: 'prompts', label: 'Prompt管理', permissions: [['prompts.view', '查看Prompt', 'view'], ['prompts.create', '新增Prompt', 'create'], ['prompts.edit', '修改Prompt', 'edit', true], ['prompts.publish', '发布Prompt', 'config', true], ['prompts.rollback', '回滚版本', 'edit'], ['prompts.test', '执行Prompt测试', 'execute']] }
    ]
  },
  {
    id: 'security', label: '安全与风控', description: '策略、黑名单与脱敏安全事件', features: [
      { id: 'risk-policies', label: '风控策略', permissions: [['risk.view', '查看风控策略', 'view'], ['risk.create', '新增策略', 'create'], ['risk.edit', '修改策略', 'edit'], ['risk.toggle', '启用/停用策略', 'edit']] },
      { id: 'blacklist', label: '黑名单', permissions: [['blacklist.view', '查看黑名单', 'view'], ['blacklist.add', '添加黑名单', 'create', true], ['blacklist.remove', '移除黑名单', 'delete']] },
      { id: 'security-events', label: '安全事件', permissions: [['security-events.view', '查看安全事件', 'view'], ['security-events.handle', '处理安全事件', 'edit']] }
    ]
  },
  {
    id: 'system', label: '系统管理', description: '运行概览、规则、日志与服务健康', features: [
      { id: 'system-management', label: '系统管理控制台', permissions: [['system.view', '查看系统概览', 'view'], ['system.rules.edit', '管理系统规则', 'config', true], ['system.logs.view', '查看完整日志', 'view'], ['system.monitoring.refresh', '执行健康检查', 'execute']] }
    ]
  }
].map((module) => ({
  ...module,
  features: module.features.map((feature) => ({
    ...feature,
    permissions: feature.permissions.map(([id, label, action, risk = false]) => ({ id, label, action, risk }))
  }))
}))

const allPermissionIds = permissionModules.flatMap((module) => module.features.flatMap((feature) => feature.permissions.map((permission) => permission.id)))
const permissionIndex = new Map(permissionModules.flatMap((module) => module.features.flatMap((feature) => feature.permissions.map((permission) => [permission.id, { ...permission, feature }]))))
const templates = [
  { id: 'readonly', label: '只读', description: '只授予后台数据查看权限' },
  { id: 'operations', label: '运营管理', description: '账号、配额与公共资源运营' },
  { id: 'ai', label: 'AI管理', description: '模型、Prompt、知识库配置' },
  { id: 'security', label: '安全审计', description: '风控、审计与系统运行查看' },
  { id: 'all', label: '全部权限', description: '全部后台治理能力' }
]

function templatePermissionIds(templateId) {
  if (templateId === 'all') return [...allPermissionIds]
  if (templateId === 'readonly') return permissionModules.flatMap((module) => module.features.flatMap((feature) => feature.permissions.filter((permission) => permission.action === 'view').map((permission) => permission.id)))
  const modules = { operations: ['overview', 'users', 'resources', 'topology'], ai: ['overview', 'knowledge', 'ai'], security: ['overview', 'security', 'system'] }[templateId] || []
  return permissionModules.filter((module) => modules.includes(module.id)).flatMap((module) => module.features.flatMap((feature) => feature.permissions.filter((permission) => templateId !== 'security' || module.id === 'security' || permission.action === 'view').map((permission) => permission.id)))
}

const roles = ref([
  { id: 'super-admin', name: '超级管理员', englishName: 'Super Admin', code: 'super_admin', description: '拥有平台后台最高管理权限。', members: 2, builtIn: true, status: 'enabled', permissions: [...allPermissionIds] },
  { id: 'operations-admin', name: '运营管理员', englishName: 'Operations Admin', code: 'operations_admin', description: '负责用户、配额和平台公共资源运营。', members: 3, builtIn: true, status: 'enabled', permissions: templatePermissionIds('operations') },
  { id: 'ai-admin', name: 'AI管理员', englishName: 'AI Admin', code: 'ai_admin', description: '负责模型、Prompt、知识库及AI能力配置。', members: 2, builtIn: true, status: 'enabled', permissions: templatePermissionIds('ai') },
  { id: 'security-admin', name: '安全管理员', englishName: 'Security Admin', code: 'security_admin', description: '负责平台安全、风控和日志审计。', members: 1, builtIn: true, status: 'enabled', permissions: templatePermissionIds('security') },
  { id: 'readonly-auditor', name: '只读审计员', englishName: 'Read-only Auditor', code: 'readonly_auditor', description: '仅允许查看平台运行和审计数据。', members: 1, builtIn: true, status: 'enabled', permissions: templatePermissionIds('readonly') }
])
const roleMembers = ref({
  'operations-admin': [{ id: 'zhou', name: '周晨', account: 'zhou.chen@topoplan.cn', joinedAt: '2026-05-12', status: '正常' }, { id: 'li', name: '李珊', account: 'li.shan@topoplan.cn', joinedAt: '2026-06-03', status: '正常' }, { id: 'wang', name: '王桐', account: 'wang.tong@topoplan.cn', joinedAt: '2026-07-18', status: '正常' }],
  'ai-admin': [{ id: 'he', name: '何一鸣', account: 'he.yiming@topoplan.cn', joinedAt: '2026-06-18', status: '正常' }],
  'security-admin': [{ id: 'qian', name: '钱瑾', account: 'qian.jin@topoplan.cn', joinedAt: '2026-07-01', status: '正常' }]
})

const selectedRoleId = ref('operations-admin')
const roleSearch = ref('')
const selectedPermissions = ref(new Set())
const expandedModules = ref(new Set())
const isDirty = ref(false)
const menuOpen = ref(false)
const permissionDrawerOpen = ref(false)
const roleDrawerMode = ref('')
const memberDrawerOpen = ref(false)
const saveConfirmationOpen = ref(false)
const deleteConfirmationOpen = ref(false)
const pendingRiskPermission = ref(null)
const hasSeenRiskWarning = ref(false)
const toast = ref('')
const memberAccount = ref('')
let toastTimer = 0

function createRoleDraft(role = null) {
  return { name: role?.name || '', code: role?.code || '', description: role?.description || '', template: 'blank', status: role?.status || 'enabled' }
}
const roleDraft = ref(createRoleDraft())
const activeRole = computed(() => roles.value.find((role) => role.id === selectedRoleId.value) || roles.value[0])
const isSuperAdmin = computed(() => activeRole.value?.id === 'super-admin')
const filteredRoles = computed(() => {
  const query = roleSearch.value.trim().toLowerCase()
  return !query ? roles.value : roles.value.filter((role) => `${role.name} ${role.englishName} ${role.code}`.toLowerCase().includes(query))
})
const activeMembers = computed(() => roleMembers.value[activeRole.value?.id] || [])
const hasRiskPermissions = computed(() => [...selectedPermissions.value].some((id) => permissionIndex.get(id)?.risk))
const permissionOverview = computed(() => permissionModules.map((module) => {
  const ids = modulePermissionIds(module)
  const granted = ids.filter(isSelected).length
  return { ...module, granted, total: ids.length, enabled: granted > 0 }
}))

watch(activeRole, (role) => {
  selectedPermissions.value = new Set(role?.permissions || [])
  isDirty.value = false
  menuOpen.value = false
}, { immediate: true })

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 3000)
}
function permissionIds(feature) { return feature.permissions.map((permission) => permission.id) }
function modulePermissionIds(module) { return module.features.flatMap(permissionIds) }
function isSelected(id) { return isSuperAdmin.value || selectedPermissions.value.has(id) }
function selectedAll(ids) { return ids.length > 0 && ids.every(isSelected) }
function selectedPartial(ids) { const count = ids.filter(isSelected).length; return count > 0 && count < ids.length }
function setIndeterminate(element, partial) { if (element) element.indeterminate = partial }

function ensureReadDependencies(nextPermissions) {
  nextPermissions.forEach((permissionId) => {
    const permission = permissionIndex.get(permissionId)
    if (!permission || permission.action === 'view') return
    const viewPermission = permission.feature.permissions.find((item) => item.action === 'view')
    if (viewPermission) nextPermissions.add(viewPermission.id)
  })
}
function updatePermissions(ids, enabled) {
  if (isSuperAdmin.value) return
  const next = new Set(selectedPermissions.value)
  ids.forEach((id) => enabled ? next.add(id) : next.delete(id))
  ensureReadDependencies(next)
  selectedPermissions.value = next
  isDirty.value = true
}
function togglePermission(permission) {
  if (isSuperAdmin.value) return
  const enabled = !selectedPermissions.value.has(permission.id)
  if (enabled && permission.risk && !hasSeenRiskWarning.value) { pendingRiskPermission.value = permission; return }
  updatePermissions([permission.id], enabled)
}
function confirmRiskPermission() {
  if (pendingRiskPermission.value) updatePermissions([pendingRiskPermission.value.id], true)
  hasSeenRiskWarning.value = true
  pendingRiskPermission.value = null
}
function toggleModule(module) { const ids = modulePermissionIds(module); updatePermissions(ids, !selectedAll(ids)) }
function toggleFeature(feature) { const ids = permissionIds(feature); updatePermissions(ids, !selectedAll(ids)) }
function toggleExpanded(moduleId) { const next = new Set(expandedModules.value); next.has(moduleId) ? next.delete(moduleId) : next.add(moduleId); expandedModules.value = next }
function setAllExpanded(expanded) { expandedModules.value = expanded ? new Set(permissionModules.map((module) => module.id)) : new Set() }
function openPermissionDrawer() { expandedModules.value = new Set(); permissionDrawerOpen.value = true; menuOpen.value = false }
function applyTemplate(templateId) {
  if (isSuperAdmin.value) return
  const next = new Set(templatePermissionIds(templateId))
  ensureReadDependencies(next)
  selectedPermissions.value = next
  isDirty.value = true
  showToast(`已应用“${templates.find((template) => template.id === templateId)?.label}”权限模板，请保存后生效`)
}
function requestSave() { if (!isSuperAdmin.value && isDirty.value) saveConfirmationOpen.value = true }
function confirmSave() { activeRole.value.permissions = [...selectedPermissions.value]; isDirty.value = false; saveConfirmationOpen.value = false; showToast('角色权限已更新') }
function roleStatus(status) { return status === 'enabled' ? '启用' : '停用' }

function openRoleDrawer(mode) { roleDrawerMode.value = mode; roleDraft.value = createRoleDraft(mode === 'edit' ? activeRole.value : null) }
function closeRoleDrawer() { roleDrawerMode.value = ''; roleDraft.value = createRoleDraft() }
function saveRole() {
  const draft = roleDraft.value
  if (!draft.name.trim()) return
  if (roleDrawerMode.value === 'create') {
    const code = draft.code.trim().toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || `custom_${Date.now()}`
    const id = `${code}_${Date.now()}`
    roles.value.push({ id, name: draft.name.trim(), englishName: 'Custom Role', code, description: draft.description.trim() || '自定义后台管理角色。', members: 0, builtIn: false, status: draft.status, permissions: templatePermissionIds(draft.template) })
    selectedRoleId.value = id
    closeRoleDrawer()
    showToast('角色已创建，请继续配置权限')
    return
  }
  if (!activeRole.value.builtIn) activeRole.value.name = draft.name.trim() || activeRole.value.name
  activeRole.value.description = draft.description.trim() || activeRole.value.description
  if (!isSuperAdmin.value) activeRole.value.status = draft.status
  closeRoleDrawer()
  showToast('角色信息已更新')
}
function duplicateRole() {
  const source = activeRole.value
  const id = `${source.code}_copy_${Date.now()}`
  roles.value.push({ ...source, id, name: `${source.name}副本`, englishName: 'Custom Role', code: id, builtIn: false, members: 0, permissions: [...source.permissions] })
  selectedRoleId.value = id
  menuOpen.value = false
  showToast('已复制为自定义角色')
}
function toggleRoleStatus() { if (isSuperAdmin.value) return; activeRole.value.status = activeRole.value.status === 'enabled' ? 'disabled' : 'enabled'; menuOpen.value = false; showToast(activeRole.value.status === 'enabled' ? '角色已启用' : '角色已停用') }
function deleteRole() { roles.value = roles.value.filter((role) => role.id !== activeRole.value.id); selectedRoleId.value = roles.value[0]?.id || ''; deleteConfirmationOpen.value = false; menuOpen.value = false; showToast('自定义角色已删除') }
function openMembers() { memberDrawerOpen.value = true; memberAccount.value = '' }
function addMember() {
  const account = memberAccount.value.trim()
  if (!account) return
  const members = [...activeMembers.value, { id: `admin-${Date.now()}`, name: account.split('@')[0], account, joinedAt: new Date().toISOString().slice(0, 10), status: '正常' }]
  roleMembers.value = { ...roleMembers.value, [activeRole.value.id]: members }
  activeRole.value.members = members.length
  memberAccount.value = ''
  showToast('后台管理员已加入角色')
}
function removeMember(id) { const members = activeMembers.value.filter((member) => member.id !== id); roleMembers.value = { ...roleMembers.value, [activeRole.value.id]: members }; activeRole.value.members = members.length; showToast('成员已移出角色') }
</script>

<template>
  <section class="admin-section-page role-permissions-page">
    <header class="role-page-heading">
      <div><nav class="role-breadcrumb" aria-label="面包屑">用户与权限 <span>/</span> <strong>角色权限</strong></nav><h2>角色权限</h2><p>配置管理后台角色及其功能权限，实现不同管理人员之间的权限隔离。</p></div>
      <button class="admin-primary-button role-create-button" type="button" @click="openRoleDrawer('create')"><Plus :size="16" /> 新建角色</button>
    </header>

    <section class="role-workspace">
      <aside class="role-list-panel" aria-label="后台角色">
        <header><div><h3>后台角色</h3><span>{{ roles.length }} 个角色</span></div></header>
        <label class="role-search"><Search :size="16" /><input v-model="roleSearch" type="search" placeholder="搜索角色名称" /></label>
        <div class="role-list"><button v-for="role in filteredRoles" :key="role.id" class="role-list-item" :class="{ active: role.id === activeRole.id }" type="button" @click="selectedRoleId = role.id"><i></i><span class="role-list-copy"><strong>{{ role.name }}</strong><small>{{ role.description }}</small></span><span class="role-list-meta"><em :class="{ custom: !role.builtIn }">{{ role.builtIn ? '系统内置' : '自定义' }}</em><small>{{ role.members }} 人</small></span></button><p v-if="!filteredRoles.length" class="role-list-empty">没有匹配的角色</p></div>
      </aside>

      <main class="role-detail-pane">
        <section class="role-profile">
          <div class="role-profile-main"><span class="role-profile-kicker">{{ activeRole.builtIn ? '系统内置角色' : '自定义角色' }}</span><h3>{{ activeRole.name }}</h3><p>{{ activeRole.description }}</p></div>
          <div class="role-profile-actions"><button class="admin-ghost-button" type="button" @click="openRoleDrawer('edit')"><Pencil :size="15" /> 编辑角色</button><button class="admin-primary-button" type="button" @click="openPermissionDrawer"><ShieldCheck :size="15" /> 配置权限</button><div class="role-more-wrap"><button class="role-icon-button" type="button" aria-label="更多角色操作" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen"><Ellipsis :size="18" /></button><div v-if="menuOpen" class="role-more-menu"><button type="button" @click="duplicateRole"><Copy :size="15" />复制角色</button><button :disabled="isSuperAdmin" type="button" @click="toggleRoleStatus">{{ activeRole.status === 'enabled' ? '停用角色' : '启用角色' }}</button><button v-if="!activeRole.builtIn" class="danger" type="button" @click="deleteConfirmationOpen = true">删除角色</button><span v-else>系统内置角色不可删除</span></div></div></div>
          <dl class="role-profile-stats"><div><dt>角色名称</dt><dd>{{ activeRole.name }}</dd></div><div><dt>角色编码</dt><dd><code>{{ activeRole.code }}</code></dd></div><div><dt>成员数量</dt><dd><button type="button" @click="openMembers"><UsersRound :size="15" />{{ activeRole.members }} 人</button></dd></div><div><dt>角色状态</dt><dd><i :class="{ disabled: activeRole.status !== 'enabled' }"></i>{{ roleStatus(activeRole.status) }}</dd></div></dl>
        </section>

        <section class="role-data-boundary" aria-labelledby="role-data-boundary-title">
          <header><div><LockKeyhole :size="17" /><div><h3 id="role-data-boundary-title">数据访问范围</h3><p>系统级隐私策略，不随角色权限变化。</p></div></div><span>不可修改</span></header>
          <div class="role-boundary-grid role-boundary-lock-list"><div v-for="item in ['用户项目内容', '用户网络拓扑', '用户AI问答', '用户工程文档']" :key="item" class="role-data-boundary-locked"><LockKeyhole :size="13" /><strong>{{ item }}</strong></div></div>
          <p class="role-boundary-note">后台角色无法访问用户具体项目内容，该限制属于系统级隐私策略。</p>
        </section>

        <section class="role-permissions role-permission-overview">
          <header class="role-permissions-heading"><div><h3>权限概览</h3><p>主页面仅显示模块授权情况，完整细粒度权限在抽屉中配置。</p></div></header>
          <div class="role-permission-overview-grid"><article v-for="module in permissionOverview" :key="module.id"><strong>{{ module.label }}</strong><span>已授权 {{ module.granted }} / {{ module.total }}</span><small :class="{ disabled: !module.enabled }"><i></i>{{ module.enabled ? '已启用' : '未启用' }}</small><button type="button" @click="openPermissionDrawer">查看权限</button></article></div>
        </section>
      </main>
    </section>

    <Teleport to="body">
      <Transition name="role-overlay"><div v-if="permissionDrawerOpen" class="role-overlay" @pointerdown.self="permissionDrawerOpen = false"><aside class="role-drawer role-permissions-drawer" role="dialog" aria-modal="true" aria-label="配置权限"><header><div><span>角色权限</span><h3>配置 {{ activeRole.name }} 的权限</h3></div><button type="button" aria-label="关闭" @click="permissionDrawerOpen = false"><X :size="18" /></button></header><div class="role-permissions-drawer-body"><section class="role-permissions"><header class="role-permissions-heading"><div><h3>权限配置</h3><p>配置该角色可以访问的后台模块及允许执行的操作。</p></div><div><button class="role-text-button" type="button" @click="setAllExpanded(true)">展开全部</button><button class="role-text-button" type="button" @click="setAllExpanded(false)">收起全部</button><button class="admin-primary-button" :disabled="isSuperAdmin || !isDirty" type="button" @click="requestSave"><Save :size="15" />{{ isDirty ? '保存权限' : '已保存' }}</button></div></header><div v-if="isSuperAdmin" class="role-super-admin-note"><ShieldCheck :size="17" />超级管理员为系统最高权限角色，核心后台权限默认生效；用户项目内容访问仍受系统级隐私策略限制。</div><div class="role-template-bar"><span>权限模板</span><button v-for="template in templates" :key="template.id" :disabled="isSuperAdmin" type="button" :title="template.description" @click="applyTemplate(template.id)">{{ template.label }}</button></div><div class="permission-module-list"><section v-for="module in permissionModules" :key="module.id" class="permission-module"><header><label class="permission-module-toggle"><input :ref="(element) => setIndeterminate(element, selectedPartial(modulePermissionIds(module)))" :checked="selectedAll(modulePermissionIds(module))" :disabled="isSuperAdmin" type="checkbox" @change="toggleModule(module)" /><span><strong>{{ module.label }}</strong><small>{{ module.description }}</small></span></label><div><span>{{ modulePermissionIds(module).filter(isSelected).length }}/{{ modulePermissionIds(module).length }}</span><button type="button" :aria-label="`${expandedModules.has(module.id) ? '收起' : '展开'}${module.label}`" @click="toggleExpanded(module.id)"><ChevronDown v-if="expandedModules.has(module.id)" :size="17" /><ChevronRight v-else :size="17" /></button></div></header><div v-show="expandedModules.has(module.id)" class="permission-feature-list"><article v-for="feature in module.features" :key="feature.id"><label class="permission-feature-toggle"><input :ref="(element) => setIndeterminate(element, selectedPartial(permissionIds(feature)))" :checked="selectedAll(permissionIds(feature))" :disabled="isSuperAdmin" type="checkbox" @change="toggleFeature(feature)" /><strong>{{ feature.label }}</strong></label><div class="permission-actions"><label v-for="permission in feature.permissions" :key="permission.id" class="permission-action" :class="{ selected: isSelected(permission.id), risk: permission.risk }"><input :checked="isSelected(permission.id)" :disabled="isSuperAdmin" type="checkbox" @change="togglePermission(permission)" /><span>{{ permission.label }}</span><em v-if="permission.risk">高风险</em></label></div></article></div></section></div></section></div></aside></div></Transition>
      <Transition name="role-overlay"><div v-if="roleDrawerMode" class="role-overlay" @pointerdown.self="closeRoleDrawer"><aside class="role-drawer" role="dialog" aria-modal="true" :aria-label="roleDrawerMode === 'create' ? '新建角色' : '编辑角色'"><header><div><span>{{ roleDrawerMode === 'create' ? '后台角色' : '角色基本信息' }}</span><h3>{{ roleDrawerMode === 'create' ? '新建角色' : `编辑${activeRole.name}` }}</h3></div><button type="button" aria-label="关闭" @click="closeRoleDrawer"><X :size="18" /></button></header><form @submit.prevent="saveRole"><label><span>角色名称 *</span><input v-model="roleDraft.name" :disabled="roleDrawerMode === 'edit' && activeRole.builtIn" required maxlength="32" placeholder="例如：内容运营管理员" /></label><label><span>角色编码 *</span><input v-model="roleDraft.code" :disabled="roleDrawerMode === 'edit'" :required="roleDrawerMode === 'create'" pattern="[a-z0-9_]+" placeholder="例如：content_operator" /></label><label><span>角色说明</span><textarea v-model="roleDraft.description" rows="4" placeholder="说明该后台角色负责的管理范围"></textarea></label><label v-if="roleDrawerMode === 'create'"><span>权限模板</span><select v-model="roleDraft.template"><option value="blank">空白角色</option><option v-for="template in templates.filter((item) => item.id !== 'all')" :key="template.id" :value="template.id">{{ template.label }}</option></select></label><label class="role-drawer-status"><span>状态</span><div><button :class="{ active: roleDraft.status === 'enabled' }" :disabled="roleDrawerMode === 'edit' && isSuperAdmin" type="button" @click="roleDraft.status = 'enabled'">启用</button><button :class="{ active: roleDraft.status === 'disabled' }" :disabled="roleDrawerMode === 'edit' && isSuperAdmin" type="button" @click="roleDraft.status = 'disabled'">停用</button></div></label><p v-if="roleDrawerMode === 'edit' && activeRole.builtIn" class="role-drawer-hint">系统内置角色的编码和名称受系统保护，部分属性不可修改。</p><footer><button class="admin-ghost-button" type="button" @click="closeRoleDrawer">取消</button><button class="admin-primary-button" type="submit">{{ roleDrawerMode === 'create' ? '创建并配置权限' : '保存角色' }}</button></footer></form></aside></div></Transition>
      <Transition name="role-overlay"><div v-if="memberDrawerOpen" class="role-overlay" @pointerdown.self="memberDrawerOpen = false"><aside class="role-drawer role-member-drawer" role="dialog" aria-modal="true" aria-label="角色成员"><header><div><span>后台管理员成员</span><h3>{{ activeRole.name }} · {{ activeRole.members }} 人</h3></div><button type="button" aria-label="关闭" @click="memberDrawerOpen = false"><X :size="18" /></button></header><form class="role-member-add" @submit.prevent="addMember"><input v-model="memberAccount" type="email" placeholder="输入后台管理员账号" /><button class="admin-primary-button" type="submit"><UserPlus :size="15" />添加成员</button></form><p class="role-member-scope"><ShieldCheck :size="15" />仅可添加已具备后台管理员身份的账号，普通平台注册用户不可加入。</p><div class="role-member-list"><article v-for="member in activeMembers" :key="member.id"><span>{{ member.name.slice(0, 1) }}</span><div><strong>{{ member.name }}</strong><small>{{ member.account }} · {{ member.joinedAt }} 加入</small></div><em>{{ member.status }}</em><button type="button" @click="removeMember(member.id)">移出</button></article><p v-if="!activeMembers.length">当前角色尚无成员</p></div></aside></div></Transition>
      <Transition name="role-overlay"><div v-if="pendingRiskPermission" class="role-overlay role-confirm-overlay"><section class="role-confirm-dialog" role="dialog" aria-modal="true"><ShieldAlert :size="22" /><h3>确认授权高风险权限</h3><p>“{{ pendingRiskPermission.label }}”可影响平台核心配置，请确认是否授权。</p><footer><button class="admin-ghost-button" type="button" @click="pendingRiskPermission = null">取消</button><button class="admin-primary-button" type="button" @click="confirmRiskPermission">确认授权</button></footer></section></div></Transition>
      <Transition name="role-overlay"><div v-if="saveConfirmationOpen" class="role-overlay role-confirm-overlay"><section class="role-confirm-dialog" role="dialog" aria-modal="true"><CircleAlert :size="22" /><h3>确认保存权限</h3><p>确定保存“{{ activeRole.name }}”的权限配置吗？<br /><strong v-if="hasRiskPermissions">本次权限调整包含高风险操作权限，请确认授权。</strong></p><footer><button class="admin-ghost-button" type="button" @click="saveConfirmationOpen = false">取消</button><button class="admin-primary-button" type="button" @click="confirmSave">确认保存</button></footer></section></div></Transition>
      <Transition name="role-overlay"><div v-if="deleteConfirmationOpen" class="role-overlay role-confirm-overlay"><section class="role-confirm-dialog" role="dialog" aria-modal="true"><Trash2 :size="22" /><h3>删除自定义角色</h3><p>删除“{{ activeRole.name }}”后无法恢复，确认继续吗？</p><footer><button class="admin-ghost-button" type="button" @click="deleteConfirmationOpen = false">取消</button><button class="role-danger-button" type="button" @click="deleteRole">确认删除</button></footer></section></div></Transition>
    </Teleport>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.role-permissions-page { width: min(100%, 1440px); margin: 0 auto; }.role-page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 22px; }.role-breadcrumb { margin-bottom: 8px; color: #8290a2; font-size: 12px; font-weight: 600; }.role-breadcrumb span { padding: 0 7px; color: #b2bdca; }.role-breadcrumb strong { color: #53657c; }.role-page-heading h2 { margin: 0; color: #17253a; font-size: 28px; line-height: 1.15; }.role-page-heading p { margin: 8px 0 0; color: #6d7c91; font-size: 14px; }.role-create-button { flex: 0 0 auto; margin-top: 8px; }
.role-workspace { display: grid; grid-template-columns: 304px minmax(0, 1fr); min-height: 680px; overflow: hidden; border: 1px solid #dde6f0; border-radius: 12px; background: #fff; box-shadow: 0 10px 28px rgba(28, 57, 90, .06); }.role-list-panel { display: flex; min-width: 0; flex-direction: column; border-right: 1px solid #e4ebf3; background: #fbfcfe; }.role-list-panel > header { padding: 20px 18px 14px; }.role-list-panel h3,.role-detail-pane h3 { margin: 0; color: #1f2d3f; font-size: 16px; font-weight: 750; }.role-list-panel header span { color: #8a99ab; font-size: 12px; }.role-search { display: flex; align-items: center; gap: 8px; margin: 0 14px 12px; padding: 0 10px; border: 1px solid #dce5ef; border-radius: 7px; background: #fff; color: #8a99ab; }.role-search:focus-within { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59, 130, 246, .12); }.role-search input { width: 100%; height: 36px; border: 0; outline: 0; background: transparent; color: #26364a; font-size: 13px; }.role-list { flex: 1; overflow-y: auto; padding: 0 8px 12px; }.role-list-item { position: relative; display: flex; align-items: flex-start; width: 100%; gap: 9px; padding: 13px 10px 13px 13px; border: 0; border-radius: 7px; background: transparent; color: inherit; text-align: left; }.role-list-item:hover { background: #f1f5f9; }.role-list-item.active { background: #eaf4ff; }.role-list-item > i { position: absolute; top: 11px; bottom: 11px; left: 0; width: 3px; border-radius: 0 3px 3px 0; background: transparent; }.role-list-item.active > i { background: #2775d8; }.role-list-copy { display: grid; min-width: 0; flex: 1; gap: 4px; }.role-list-copy strong { color: #34445a; font-size: 13px; font-weight: 750; }.role-list-item.active .role-list-copy strong { color: #1459ad; }.role-list-copy small { overflow: hidden; color: #76869a; font-size: 11px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }.role-list-meta { display: grid; justify-items: end; gap: 6px; }.role-list-meta em { padding: 2px 5px; border-radius: 4px; background: #e9eef5; color: #65758a; font-size: 10px; font-style: normal; white-space: nowrap; }.role-list-meta em.custom { background: #edf5ff; color: #3571b7; }.role-list-meta small { color: #8493a6; font-size: 11px; }.role-list-empty { color: #8b99aa; font-size: 13px; text-align: center; }
.role-detail-pane { min-width: 0; padding: 24px; }.role-profile { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 16px 24px; padding-bottom: 20px; border-bottom: 1px solid #e7edf4; }.role-profile-kicker { color: #5486b8; font-size: 12px; font-weight: 700; }.role-profile-main h3 { margin-top: 5px; font-size: 21px; }.role-profile-main code { display: inline-block; margin-top: 7px; color: #708096; font-size: 12px; }.role-profile-main p { max-width: 620px; margin: 8px 0 0; color: #65758a; font-size: 13px; line-height: 1.55; }.role-profile-actions { display: flex; gap: 8px; }.role-icon-button { display: grid; width: 34px; height: 34px; place-items: center; border: 1px solid #d9e3ee; border-radius: 7px; background: #fff; color: #5e6f84; }.role-more-wrap { position: relative; }.role-more-menu { position: absolute; z-index: 15; top: 40px; right: 0; display: grid; width: 152px; padding: 5px; border: 1px solid #dce5ef; border-radius: 8px; background: #fff; box-shadow: 0 12px 25px rgba(22, 49, 83, .14); }.role-more-menu button,.role-more-menu span { display: flex; align-items: center; gap: 6px; padding: 8px; border: 0; border-radius: 5px; background: transparent; color: #4f6075; font-size: 12px; text-align: left; }.role-more-menu button:hover:not(:disabled) { background: #f2f6fa; }.role-more-menu button:disabled { cursor: not-allowed; opacity: .45; }.role-more-menu .danger { color: #c2413b; }.role-more-menu span { color: #97a4b5; }.role-profile-stats { display: flex; grid-column: 1 / -1; flex-wrap: wrap; gap: 28px; margin: 2px 0 0; }.role-profile-stats div { display: grid; gap: 5px; }.role-profile-stats dt { color: #94a1b2; font-size: 11px; }.role-profile-stats dd { display: flex; align-items: center; gap: 6px; margin: 0; color: #34445a; font-size: 13px; font-weight: 700; }.role-profile-stats dd > i { width: 7px; height: 7px; border-radius: 50%; background: #23a26d; }.role-profile-stats dd > i.disabled { background: #98a5b4; }.role-profile-stats dd button { display: inline-flex; align-items: center; gap: 5px; padding: 0; border: 0; background: transparent; color: #236ac0; font: inherit; font-size: 13px; }
.role-data-boundary { margin-top: 18px; padding: 15px 16px; border: 1px solid #d9e7f7; border-radius: 9px; background: #f8fbff; }.role-data-boundary > header,.role-permissions-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.role-data-boundary > header > div { display: flex; gap: 9px; color: #3170af; }.role-data-boundary h3 { font-size: 14px; }.role-data-boundary header p { margin: 3px 0 0; color: #728297; font-size: 12px; }.role-data-boundary header > span { padding: 3px 7px; border: 1px solid #d8e1eb; border-radius: 4px; color: #8190a2; font-size: 11px; }.role-boundary-grid { display: grid; grid-template-columns: 1fr 1.25fr; gap: 16px; margin-top: 14px; }.role-boundary-grid > div { padding: 10px 12px; border-radius: 6px; background: #fff; }.role-boundary-grid strong { color: #4f6177; font-size: 12px; }.role-boundary-grid ul { display: flex; flex-wrap: wrap; gap: 7px 14px; margin: 9px 0 0; padding: 0; list-style: none; }.role-boundary-grid li { display: inline-flex; align-items: center; gap: 4px; color: #4e677e; font-size: 12px; }.role-boundary-grid li svg { color: #2e8f67; }.role-data-boundary-locked { opacity: .75; }.role-data-boundary-locked strong,.role-data-boundary-locked li { color: #8592a1; }.role-data-boundary-locked li svg { color: #8d99a7; }.role-boundary-note { margin: 12px 0 0; color: #75869a; font-size: 12px; line-height: 1.5; }
.role-permissions { margin-top: 24px; }.role-permissions-heading h3 { font-size: 17px; }.role-permissions-heading p { margin: 5px 0 0; color: #75859a; font-size: 12px; }.role-permissions-heading > div:last-child { display: flex; align-items: center; gap: 8px; }.role-text-button { padding: 5px 3px; border: 0; background: transparent; color: #57728e; font-size: 12px; }.role-text-button:hover { color: #236ac0; }.role-permissions-heading .admin-primary-button { min-height: 32px; padding: 0 10px; }.role-permissions-heading .admin-primary-button:disabled { cursor: not-allowed; opacity: .5; }.role-super-admin-note { display: flex; gap: 8px; margin-top: 15px; padding: 10px 12px; border: 1px solid #d9e7f7; border-radius: 7px; background: #f3f8ff; color: #4675a6; font-size: 12px; line-height: 1.5; }.role-template-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin: 17px 0 14px; }.role-template-bar > span { margin-right: 3px; color: #718096; font-size: 12px; font-weight: 700; }.role-template-bar button { padding: 5px 9px; border: 1px solid #dbe5ef; border-radius: 5px; background: #fff; color: #52657b; font-size: 12px; }.role-template-bar button:hover:not(:disabled) { border-color: #9dc5f0; background: #eff7ff; color: #1f67b5; }.role-template-bar button:disabled { cursor: not-allowed; opacity: .5; }
.permission-module-list { display: grid; gap: 12px; }.permission-module { overflow: hidden; border: 1px solid #dfe7ef; border-radius: 8px; background: #fff; }.permission-module > header { display: flex; align-items: center; justify-content: space-between; min-height: 58px; padding: 10px 13px; background: #fbfcfe; }.permission-module-toggle,.permission-feature-toggle { display: flex; align-items: center; gap: 9px; min-width: 0; }.permission-module input,.permission-feature-toggle input,.permission-action input { width: 15px; height: 15px; margin: 0; accent-color: #2f79d2; }.permission-module-toggle span { display: grid; gap: 3px; }.permission-module-toggle strong { color: #35475e; font-size: 14px; }.permission-module-toggle small { color: #8593a5; font-size: 11px; }.permission-module > header > div { display: flex; align-items: center; gap: 10px; color: #8493a4; font-size: 11px; }.permission-module > header button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: #6e7e91; }.permission-module > header button:hover { background: #edf3f8; }.permission-feature-list { display: grid; padding: 2px 13px 5px; }.permission-feature-list article { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 14px; padding: 12px 0; border-top: 1px solid #edf1f5; }.permission-feature-list article:first-child { border-top: 0; }.permission-feature-toggle { align-self: flex-start; min-height: 26px; }.permission-feature-toggle strong { color: #516278; font-size: 12px; }.permission-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 8px; }.permission-action { display: inline-flex; align-items: center; gap: 5px; min-height: 27px; padding: 0 7px; border: 1px solid #e1e8ef; border-radius: 5px; background: #fff; color: #738295; font-size: 12px; }.permission-action:hover { border-color: #b6d3ee; }.permission-action.selected { border-color: #a8ccef; background: #f0f7ff; color: #2767ad; }.permission-action input { width: 13px; height: 13px; }.permission-action em { padding: 2px 4px; border-radius: 3px; background: #fff2df; color: #bd7416; font-size: 10px; font-style: normal; }
.role-overlay { position: fixed; z-index: 120; inset: 0; display: flex; justify-content: flex-end; background: rgba(20, 35, 55, .34); }.role-drawer { display: flex; width: min(430px, 100vw); flex-direction: column; padding: 22px; overflow-y: auto; background: #fff; box-shadow: -18px 0 38px rgba(17, 37, 63, .16); }.role-drawer > header { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 19px; border-bottom: 1px solid #e6edf4; }.role-drawer > header span { color: #7d8da0; font-size: 12px; }.role-drawer > header h3 { margin-top: 4px; font-size: 18px; }.role-drawer > header > button { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: #68798e; }.role-drawer form:not(.role-member-add) { display: grid; gap: 16px; padding-top: 20px; }.role-drawer label { display: grid; gap: 7px; }.role-drawer label > span { color: #52647b; font-size: 12px; font-weight: 700; }.role-drawer input,.role-drawer textarea,.role-drawer select { width: 100%; box-sizing: border-box; border: 1px solid #d7e1eb; border-radius: 6px; outline: 0; background: #fff; color: #27394f; font-size: 13px; }.role-drawer input,.role-drawer select { height: 38px; padding: 0 10px; }.role-drawer textarea { min-height: 92px; padding: 9px 10px; resize: vertical; }.role-drawer input:focus,.role-drawer textarea:focus,.role-drawer select:focus { border-color: #3e85d8; box-shadow: 0 0 0 3px rgba(62, 133, 216, .12); }.role-drawer input:disabled { cursor: not-allowed; background: #f2f5f8; color: #8c99a9; }.role-drawer-status > div { display: inline-flex; width: max-content; padding: 3px; border-radius: 6px; background: #eef3f7; }.role-drawer-status button { padding: 6px 13px; border: 0; border-radius: 4px; background: transparent; color: #718195; font-size: 12px; }.role-drawer-status button.active { background: #fff; color: #246bc1; box-shadow: 0 1px 3px rgba(27, 55, 88, .12); }.role-drawer-status button:disabled { cursor: not-allowed; opacity: .55; }.role-drawer-hint { margin: -4px 0 0; color: #8592a2; font-size: 12px; line-height: 1.5; }.role-drawer footer,.role-confirm-dialog footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: auto; padding-top: 20px; }.role-member-add { display: flex; gap: 8px; padding-top: 18px; }.role-member-add input { flex: 1; min-width: 0; }.role-member-add .admin-primary-button { min-height: 38px; padding: 0 10px; white-space: nowrap; }.role-member-scope { display: flex; gap: 6px; margin: 14px 0; padding: 9px; border-radius: 6px; background: #f2f7fc; color: #5e7994; font-size: 12px; line-height: 1.4; }.role-member-list { display: grid; gap: 2px; }.role-member-list article { display: flex; align-items: center; gap: 9px; padding: 11px 0; border-bottom: 1px solid #e9eef3; }.role-member-list article > span { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 50%; background: #dcecff; color: #3475bb; font-size: 12px; font-weight: 800; }.role-member-list article > div { display: grid; min-width: 0; flex: 1; gap: 3px; }.role-member-list strong { color: #35465b; font-size: 13px; }.role-member-list small { overflow: hidden; color: #8090a2; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.role-member-list em { color: #259466; font-size: 11px; font-style: normal; }.role-member-list button { padding: 4px; border: 0; background: transparent; color: #c24d49; font-size: 12px; }.role-member-list > p { color: #8291a3; font-size: 13px; text-align: center; }.role-confirm-overlay { align-items: center; justify-content: center; padding: 18px; }.role-confirm-dialog { width: min(390px, 100%); padding: 22px; border: 1px solid #dce6f0; border-radius: 10px; background: #fff; box-shadow: 0 20px 44px rgba(15, 32, 52, .2); }.role-confirm-dialog > svg { color: #d68a24; }.role-confirm-dialog h3 { margin: 12px 0 0; font-size: 17px; }.role-confirm-dialog p { margin: 8px 0 0; color: #66778c; font-size: 13px; line-height: 1.55; }.role-confirm-dialog p strong { color: #b56d1f; }.role-danger-button { min-height: 34px; padding: 0 11px; border: 1px solid #d44b45; border-radius: 6px; background: #d44b45; color: #fff; font-size: 12px; font-weight: 700; }.role-overlay-enter-active,.role-overlay-leave-active { transition: opacity .18s ease; }.role-overlay-enter-active .role-drawer,.role-overlay-leave-active .role-drawer { transition: transform .22s ease; }.role-overlay-enter-from,.role-overlay-leave-to { opacity: 0; }.role-overlay-enter-from .role-drawer,.role-overlay-leave-to .role-drawer { transform: translateX(100%); }
:global(html[data-theme='dark']) .role-page-heading h2,:global(html[data-theme='dark']) .role-list-panel h3,:global(html[data-theme='dark']) .role-detail-pane h3 { color: #edf3fb; }:global(html[data-theme='dark']) .role-workspace,:global(html[data-theme='dark']) .role-detail-pane,:global(html[data-theme='dark']) .role-drawer,:global(html[data-theme='dark']) .role-confirm-dialog,:global(html[data-theme='dark']) .permission-module,:global(html[data-theme='dark']) .role-boundary-grid > div { border-color: #304158; background: #172333; }:global(html[data-theme='dark']) .role-list-panel,:global(html[data-theme='dark']) .permission-module > header { border-color: #2c3b4f; background: #152232; }:global(html[data-theme='dark']) .role-list-item:hover { background: #1d2d40; }:global(html[data-theme='dark']) .role-list-item.active { background: #1b365b; }:global(html[data-theme='dark']) .role-list-copy strong,:global(html[data-theme='dark']) .role-profile-stats dd,:global(html[data-theme='dark']) .permission-module-toggle strong { color: #dce7f3; }:global(html[data-theme='dark']) .role-list-copy small,:global(html[data-theme='dark']) .role-profile-main p,:global(html[data-theme='dark']) .role-permissions-heading p { color: #9caec2; }:global(html[data-theme='dark']) .role-search,:global(html[data-theme='dark']) .role-icon-button,:global(html[data-theme='dark']) .role-data-boundary,:global(html[data-theme='dark']) .permission-action,:global(html[data-theme='dark']) .role-template-bar button { border-color: #35475d; background: #1b2939; }:global(html[data-theme='dark']) .role-search input,:global(html[data-theme='dark']) .role-drawer input,:global(html[data-theme='dark']) .role-drawer textarea,:global(html[data-theme='dark']) .role-drawer select { background: #1b2939; color: #e5edf7; }:global(html[data-theme='dark']) .role-data-boundary { background: #152c45; }:global(html[data-theme='dark']) .permission-feature-list article,:global(html[data-theme='dark']) .role-profile,:global(html[data-theme='dark']) .role-drawer > header,:global(html[data-theme='dark']) .role-member-list article { border-color: #2c3b4f; }:global(html[data-theme='dark']) .permission-action.selected { border-color: #3867a8; background: #1b365b; color: #a7ccff; }
@media (max-width: 980px) { .role-workspace { grid-template-columns: 260px minmax(0, 1fr); }.role-detail-pane { padding: 18px; }.permission-feature-list article { grid-template-columns: 130px minmax(0, 1fr); }.role-boundary-grid { grid-template-columns: 1fr; } } @media (max-width: 760px) { .role-page-heading { flex-direction: column; }.role-create-button { margin-top: 0; }.role-workspace { grid-template-columns: 1fr; overflow: visible; }.role-list-panel { min-height: 300px; max-height: 390px; border-right: 0; border-bottom: 1px solid #e4ebf3; }.role-profile { grid-template-columns: 1fr; }.role-profile-actions { position: absolute; top: 0; right: 0; }.role-profile-actions .admin-ghost-button { padding: 0 8px; font-size: 0; }.role-profile-actions .admin-ghost-button svg { margin: 0; }.role-permissions-heading { flex-direction: column; }.permission-feature-list article { grid-template-columns: 1fr; gap: 8px; }.permission-actions { padding-left: 24px; }.role-profile-stats { gap: 15px; }.role-drawer { width: 100%; }.role-member-add { flex-direction: column; }.role-member-add .admin-primary-button { justify-content: center; }.role-page-heading h2 { font-size: 24px; } }
</style>

<style scoped>
@media (min-width: 981px) {
  .role-permissions-page { display: flex; flex-direction: column; width: min(100%, 1480px); height: 100%; min-height: 0; overflow: hidden; box-sizing: border-box; }
  .role-page-heading { flex: 0 0 auto; min-height: 58px; margin-bottom: 10px; }.role-page-heading h2 { font-size: 23px; }.role-page-heading p { margin-top: 4px; font-size: 12px; }.role-breadcrumb { margin-bottom: 4px; }.role-create-button { min-height: 34px; margin-top: 0; }
  .role-workspace { flex: 1 1 auto; grid-template-columns: 270px minmax(0, 1fr); min-height: 0; border-radius: 8px; }.role-list-panel > header { padding: 14px 14px 8px; }.role-list-panel h3 { font-size: 14px; }.role-search { margin: 0 10px 7px; }.role-search input { height: 31px; font-size: 12px; }.role-list { padding: 0 5px 8px; }.role-list-item { min-height: 56px; padding: 8px 8px 8px 11px; }.role-list-copy { gap: 2px; }.role-list-copy strong { font-size: 12px; }.role-list-copy small { font-size: 10px; }.role-list-meta { gap: 3px; }.role-list-meta em,.role-list-meta small { font-size: 9px; }
  .role-detail-pane { display: flex; min-height: 0; flex-direction: column; padding: 16px 18px; overflow: hidden; }.role-profile { flex: 0 0 auto; grid-template-columns: minmax(0, 1fr) auto; gap: 7px 16px; padding-bottom: 12px; }.role-profile-main h3 { margin-top: 3px; font-size: 19px; }.role-profile-main p { margin-top: 4px; font-size: 12px; line-height: 1.4; }.role-profile-actions { gap: 7px; }.role-profile-actions .admin-primary-button,.role-profile-actions .admin-ghost-button { min-height: 32px; padding: 0 9px; font-size: 11px; }.role-icon-button { width: 32px; height: 32px; }.role-profile-stats { gap: 18px; margin-top: 4px; }.role-profile-stats div { gap: 2px; }.role-profile-stats dt { font-size: 10px; }.role-profile-stats dd { font-size: 11px; }.role-profile-stats dd code { font-size: 10px; }.role-profile-stats dd button { font-size: 11px; }
  .role-data-boundary { flex: 0 0 auto; margin-top: 10px; padding: 10px 12px; border-radius: 7px; }.role-data-boundary h3 { font-size: 12px; }.role-data-boundary header p { margin-top: 1px; font-size: 10px; }.role-boundary-grid.role-boundary-lock-list { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 7px; margin-top: 8px; }.role-boundary-lock-list > div { display: flex; align-items: center; gap: 5px; min-width: 0; padding: 7px; }.role-boundary-lock-list strong { overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.role-boundary-note { margin-top: 7px; font-size: 10px; }
  .role-permission-overview { flex: 1 1 auto; min-height: 0; margin-top: 11px; overflow: hidden; }.role-permissions-heading { margin-bottom: 8px; }.role-permissions-heading h3 { font-size: 15px; }.role-permissions-heading p { margin-top: 2px; font-size: 11px; }.role-permission-overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }.role-permission-overview-grid article { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; min-height: 68px; gap: 5px 8px; padding: 9px 10px; border: 1px solid #e1e8ef; border-radius: 7px; background: #fff; }.role-permission-overview-grid article > strong { color: #34445a; font-size: 12px; }.role-permission-overview-grid article > span { color: #718096; font-size: 10px; text-align: right; }.role-permission-overview-grid article > small { display: inline-flex; align-items: center; gap: 4px; color: #15803d; font-size: 10px; }.role-permission-overview-grid article > small.disabled { color: #94a3b8; }.role-permission-overview-grid article > small i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.role-permission-overview-grid article button { justify-self: end; padding: 0; border: 0; background: transparent; color: #2563eb; font-size: 10px; font-weight: 700; cursor: pointer; }.role-permission-overview-grid article button:hover { color: #1d4ed8; text-decoration: underline; }
  .role-permissions-drawer { width: min(860px, 94vw); padding: 0; overflow: hidden; }.role-permissions-drawer > header { flex: 0 0 auto; padding: 16px 18px 12px; }.role-permissions-drawer-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; padding: 12px 18px 18px; }.role-permissions-drawer .role-permissions { margin-top: 0; }.role-permissions-drawer .permission-module-list { gap: 6px; }.role-permissions-drawer .permission-module { border-radius: 6px; }.role-permissions-drawer .permission-module > header { min-height: 42px; padding: 5px 8px; }.role-permissions-drawer .permission-module-toggle { gap: 7px; }.role-permissions-drawer .permission-module-toggle input { width: 13px; height: 13px; }.role-permissions-drawer .permission-module-toggle span { gap: 0; }.role-permissions-drawer .permission-module-toggle strong { font-size: 12px; }.role-permissions-drawer .permission-module-toggle small { display: none; }.role-permissions-drawer .permission-module > header > div { gap: 5px; font-size: 10px; }.role-permissions-drawer .permission-module > header button { width: 24px; height: 24px; }.role-permissions-drawer .permission-feature-list { padding: 1px 9px 4px; }.role-permissions-drawer .permission-feature-list article { grid-template-columns: 124px minmax(0, 1fr); gap: 8px; padding: 6px 0; }.role-permissions-drawer .permission-feature-toggle { min-height: 22px; gap: 6px; }.role-permissions-drawer .permission-feature-toggle strong { font-size: 11px; }.role-permissions-drawer .permission-feature-toggle input { width: 13px; height: 13px; }.role-permissions-drawer .permission-actions { gap: 4px; }.role-permissions-drawer .permission-action { min-height: 22px; gap: 4px; padding: 0 6px; font-size: 10px; }.role-permissions-drawer .permission-action input { width: 11px; height: 11px; }.role-permissions-drawer .permission-action em { padding: 1px 3px; font-size: 9px; }
}

:global(html[data-theme='dark']) .role-permission-overview-grid article { border-color: #304158; background: #172333; }:global(html[data-theme='dark']) .role-permission-overview-grid article > strong { color: #dce7f3; }:global(html[data-theme='dark']) .role-permission-overview-grid article > span { color: #9caec2; }
</style>
