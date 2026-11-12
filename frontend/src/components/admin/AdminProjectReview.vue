<script setup>
import { computed, nextTick, ref } from 'vue'
import { AlertTriangle, Download, Eye, FileWarning, Plus, ShieldAlert, Trash2, X } from '@lucide/vue'

const projects = ref([
  { id: 1, name: '三层办公楼网络规划', owner: 'student01', type: '企业办公网络', updatedAt: '今天 10:26', status: '已通过', score: 92, request: '为三层办公楼设计有线、无线和服务器区网络，约 156 台终端，财务部需要独立隔离。' },
  { id: 2, name: '校园实训楼无线覆盖', owner: 'network_lab', type: '校园网络', updatedAt: '今天 09:54', status: '待复核', score: 78, request: '覆盖 4 层实训楼，包含访客网络与 480 台移动终端，需要分时段流量策略。' },
  { id: 3, name: '服务器区风险分析', owner: 'ai_planner', type: '风险优化', updatedAt: '昨天 18:43', status: '已通过', score: 95, request: '审查现有服务器区南北向和东西向流量，输出防火墙分区与冗余优化建议。' },
  { id: 4, name: '实验室 VPN 接入方案', owner: 'demo_user', type: '远程接入', updatedAt: '2026-07-23', status: '需处理', score: 56, request: '针对校外实验人员提供远程接入，并隔离核心科研资料。' }
])
const activeProject = ref(null)
const confirmClearOpen = ref(false)
const deleteProjectDialogOpen = ref(false)
const deleteTarget = ref(null)
const deleteConfirmation = ref('')
const deleteConfirmationInput = ref(null)
const toast = ref('')
let toastTimer = 0

const visibleProjects = computed(() => projects.value.filter((project) => project.status !== 'archived_deleted'))
const reviewStats = computed(() => ({
  pending: visibleProjects.value.filter((project) => project.status !== '已通过').length,
  total: visibleProjects.value.length
}))
const deleteProjectCode = computed(() => (
  deleteTarget.value ? `ENG-${String(deleteTarget.value.id).padStart(4, '0')}` : ''
))
const deleteConfirmationMatches = computed(() => {
  const value = deleteConfirmation.value.trim()
  const target = deleteTarget.value
  if (!value || !target) return false

  return [target.name, String(target.id), deleteProjectCode.value].includes(value)
})

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2400)
}

function clearOldData() {
  confirmClearOpen.value = false
  showToast('已提交冗余数据清理任务，预计释放 8.6 GB 存储空间')
}

function startProjectCreation() {
  showToast('新建工程向导已准备就绪，请从项目工作区开始配置需求。')
}

async function openDeleteProjectDialog() {
  if (!activeProject.value) return
  deleteTarget.value = activeProject.value
  deleteConfirmation.value = ''
  deleteProjectDialogOpen.value = true
  await nextTick()
  deleteConfirmationInput.value?.focus()
}

function closeDeleteProjectDialog() {
  deleteProjectDialogOpen.value = false
  deleteConfirmation.value = ''
  deleteTarget.value = null
}

function confirmSoftDeleteProject() {
  const target = deleteTarget.value
  if (!target || !deleteConfirmationMatches.value) return

  // Soft delete: send PATCH /api/admin/projects/:id with { status: 'archived_deleted' }.
  // Never issue an HTTP DELETE here; the backend retains the project for the 30-day recovery window.
  projects.value = projects.value.map((project) => (
    project.id === target.id ? { ...project, status: 'archived_deleted' } : project
  ))

  closeDeleteProjectDialog()
  activeProject.value = null
  showToast(`“${target.name}”已移入回收站，将在 30 天后物理销毁`)
}
</script>

<template>
  <section class="admin-section-page">
    <header class="admin-page-intro compact"><div><p>资产审计与风险治理</p><h2>项目资产与审核</h2><span>检查平台生成的网络项目、处理异常内容，并维护长期存储健康度。</span></div><div class="admin-section-icon"><ShieldAlert :size="22" /></div></header>
    <section class="admin-review-summary"><article><span>待人工复核</span><strong>{{ reviewStats.pending }}</strong><small>需要查看需求文本或风险提示</small></article><article><span>今日审核项目</span><strong>{{ reviewStats.total }}</strong><small>自动审查覆盖率 100%</small></article><button type="button" class="admin-danger-outline" @click="confirmClearOpen = true"><Trash2 :size="16" /> 清理 30 天前冗余数据</button></section>
    <section class="admin-card admin-table-card"><div class="admin-table-scroll"><table class="admin-data-table"><thead><tr><th>项目名称</th><th>归属账号</th><th>类型</th><th>风险评分</th><th>审核状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="project in visibleProjects" :key="project.id"><td><strong>{{ project.name }}</strong></td><td>{{ project.owner }}</td><td>{{ project.type }}</td><td><span class="admin-score" :class="{ 'is-risk': project.score < 70 }">{{ project.score }}</span></td><td><span class="admin-status-pill" :class="{ 'is-review': project.status === '待复核', 'is-danger': project.status === '需处理' }">{{ project.status }}</span></td><td>{{ project.updatedAt }}</td><td><button type="button" class="admin-table-action" @click="activeProject = project"><Eye :size="15" /> 查看</button></td></tr><tr v-if="!visibleProjects.length"><td class="admin-table-empty-state" colspan="7"><div><span><FileWarning :size="20" /></span><strong>当前没有活动中的工程</strong><p>新建工程后，可在此处查看审核状态、项目资产与操作记录。</p><button type="button" class="admin-primary-button" @click="startProjectCreation"><Plus :size="15" /> 新建工程</button></div></td></tr></tbody></table></div></section>
    <Transition name="admin-drawer"><div v-if="activeProject" class="admin-drawer-backdrop" @pointerdown.self="activeProject = null"><aside class="admin-review-drawer"><header><div><span>项目审查</span><h3>{{ activeProject.name }}</h3></div><button type="button" class="admin-close-button" aria-label="关闭" @click="activeProject = null"><X :size="18" /></button></header><section class="admin-drawer-score"><span>自动风险评分</span><strong>{{ activeProject.score }}</strong><small>/ 100</small></section><section class="admin-review-block"><h4>脱敏需求文本</h4><p>{{ activeProject.request }}</p></section><section class="admin-review-block"><h4>微缩网络拓扑</h4><div class="admin-mini-topology"><span class="edge edge-a"></span><span class="edge edge-b"></span><span class="edge edge-c"></span><b class="node node-edge">出口</b><b class="node node-core">核心</b><b class="node node-access">接入</b><b class="node node-server">服务器</b></div></section><section class="admin-project-danger-zone" aria-labelledby="danger-zone-title"><header><div><span>危险操作</span><h4 id="danger-zone-title">危险操作 (Danger Zone)</h4></div><AlertTriangle :size="18" aria-hidden="true" /></header><div class="admin-project-danger-item"><div class="admin-project-danger-copy"><strong>删除该项目与工程</strong><p>该操作将把项目移入回收站，保留 30 天后进行物理销毁。操作将被记录到系统审计日志中。</p></div><button type="button" class="admin-project-danger-button" @click="openDeleteProjectDialog"><Trash2 :size="15" /> 删除项目</button></div></section><footer><button type="button" class="admin-ghost-button"><Download :size="16" /> 下载审查记录</button><button type="button" class="admin-primary-button" @click="showToast('该项目已标记为已审查'); activeProject = null">通过审核</button></footer></aside></div></Transition>
    <Transition name="admin-modal"><div v-if="confirmClearOpen" class="admin-modal-backdrop" @pointerdown.self="confirmClearOpen = false"><section class="admin-modal-card danger-confirm"><header><div class="danger-icon"><AlertTriangle :size="20" /></div><button type="button" class="admin-close-button" aria-label="关闭" @click="confirmClearOpen = false"><X :size="18" /></button></header><h3>清理 30 天前冗余数据？</h3><p>这项操作会删除失效缓存、孤立导出文件和未关联附件，释放预计 8.6 GB 存储空间，且无法撤销。</p><footer><button type="button" class="admin-ghost-button" @click="confirmClearOpen = false">取消</button><button type="button" class="admin-danger-button" @click="clearOldData">确认清理</button></footer></section></div></Transition>
    <Transition name="admin-modal"><div v-if="deleteProjectDialogOpen" class="admin-modal-backdrop admin-project-delete-backdrop" @pointerdown.self="closeDeleteProjectDialog"><section class="admin-modal-card admin-project-delete-modal" role="alertdialog" aria-modal="true" aria-labelledby="project-delete-title" aria-describedby="project-delete-description"><header><div class="danger-icon"><AlertTriangle :size="20" /></div><button type="button" class="admin-close-button" aria-label="关闭删除确认" @click="closeDeleteProjectDialog"><X :size="18" /></button></header><h3 id="project-delete-title">删除“{{ deleteTarget?.name }}”？</h3><p id="project-delete-description">此操作会将项目移入回收站。请输入项目名称或工程 ID 以确认删除。</p><label class="admin-project-delete-input"><span>确认项目名称或工程 ID</span><input ref="deleteConfirmationInput" v-model="deleteConfirmation" type="text" :placeholder="`输入 ${deleteTarget?.name ?? ''} 或 ${deleteProjectCode}`" :aria-label="`输入 ${deleteTarget?.name ?? '项目名称'} 或工程 ID 确认删除`" @keydown.esc="closeDeleteProjectDialog" /><small>项目：{{ deleteTarget?.name }} · 工程 ID：{{ deleteProjectCode }}</small></label><footer><button type="button" class="admin-ghost-button" @click="closeDeleteProjectDialog">取消</button><button type="button" class="admin-danger-button" :disabled="!deleteConfirmationMatches" @click="confirmSoftDeleteProject">确认删除</button></footer></section></div></Transition>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>
