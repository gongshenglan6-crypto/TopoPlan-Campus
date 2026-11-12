<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronDown, CloudUpload, ImagePlus, LoaderCircle, Network, Plus, ToggleLeft, Trash2, X } from '@lucide/vue'
import {
  createTopologyResource,
  getTopologyResourceForAdmin,
  listTopologyResourceCategories,
  listTopologyResourcesForAdmin,
  updateTopologyResource,
  uploadTopologyResourceIcon
} from '../../api/admin'
import { inferTopologyResourceType, normalizeTopologyCategory } from '../../data/topologyResources'

const defaultCategories = ['网络设备', '安全设备', '无线设备', '服务器与存储', '终端设备', '其他']
const devices = ref([])
const categories = ref([...defaultCategories])
const activeCategory = ref('全部')
const toast = ref('')
const saving = ref(false)
const uploading = ref(false)
const categoryOpen = ref(false)
const iconInput = ref(null)
const fieldErrors = ref({})
const resourceModal = ref({ open: false, mode: 'create', resourceId: '', loading: false })
const draft = ref(emptyDraft())
let toastTimer = 0

const categoryTabs = computed(() => ['全部', ...categories.value])
const filteredDevices = computed(() => activeCategory.value === '全部'
  ? devices.value
  : devices.value.filter((device) => device.category === activeCategory.value))
const activeCount = computed(() => devices.value.filter((device) => device.enabled).length)
const aiUsableDevices = computed(() => devices.value.filter((device) => device.enabled && device.allowAi).length)
const modalTitle = computed(() => resourceModal.value.mode === 'edit' ? '编辑拓扑资源' : '新增拓扑资源')
const modalDescription = computed(() => resourceModal.value.mode === 'edit'
  ? '更新公共设备资源的基础信息、端口规格、图标和使用状态。'
  : '添加可供用户项目和 AI 拓扑生成使用的公共设备资源。')
const draftPortCount = computed(() => Array.isArray(draft.value.ports) ? draft.value.ports.length : 0)
const draftPortType = computed(() => {
  const first = draft.value.ports?.[0] || ''
  if (/^10GE|^25GE|^40GE|^GE|^ETH|^Ethernet|^NIC|^LAN|^WAN/i.test(first)) return first.replace(/[0-9].*$/, '') || 'GE'
  return first ? '接口' : '—'
})
const iconPreview = computed(() => draft.value.iconPath || '')
const iconLabel = computed(() => draft.value.originalFileName || draft.value.iconFileName || (draft.value.iconPath ? fileNameFromPath(draft.value.iconPath) : '未上传图标'))

function emptyDraft() {
  return {
    name: '',
    englishName: '',
    deviceType: '',
    category: '网络设备',
    sortOrder: 100,
    allowUserDrag: true,
    allowAi: true,
    isEnabled: true,
    description: '',
    iconPath: '',
    iconFileName: '',
    originalFileName: '',
    iconMimeType: '',
    iconSize: 0,
    iconHash: '',
    ports: [],
    portProfileId: '',
    portProfileName: ''
  }
}

function normalizeDevice(device) {
  const type = String(device.deviceType || device.type || inferTopologyResourceType(device)).trim()
  const rawIconPath = String(device.iconPath || (String(device.asset || '').startsWith('/uploads/topology-icons/') ? device.asset : '') || '')
  // The admin resource API uses the configuration key (router, firewall, ...)
  // as the stable edit identifier. `id` is only the database record id.
  const resourceKey = String(device.key ?? device.id ?? device.recordId ?? '').trim()
  return {
    ...device,
    id: resourceKey,
    key: resourceKey,
    recordId: device.recordId ?? device.id ?? '',
    type,
    deviceType: type,
    name: device.name || type,
    englishName: device.englishName || '',
    category: normalizeTopologyCategory(device.category || '其他'),
    format: device.format || (rawIconPath.split('.').pop() || 'SVG').toUpperCase(),
    enabled: device.isEnabled !== undefined ? device.isEnabled !== false : device.enabled !== false,
    isEnabled: device.isEnabled !== undefined ? device.isEnabled !== false : device.enabled !== false,
    allowAi: device.allowAi !== false,
    draggable: device.allowUserDrag !== undefined ? device.allowUserDrag !== false : device.draggable !== false,
    allowUserDrag: device.allowUserDrag !== undefined ? device.allowUserDrag !== false : device.draggable !== false,
    order: Number(device.sortOrder ?? device.order ?? 100),
    sortOrder: Number(device.sortOrder ?? device.order ?? 100),
    updatedAt: device.updatedAt || '',
    iconPath: rawIconPath,
    asset: rawIconPath,
    iconFileName: device.iconFileName || fileNameFromPath(rawIconPath),
    iconMimeType: device.iconMimeType || '',
    iconSize: Number(device.iconSize || 0),
    iconHash: device.iconHash || '',
    portProfileId: device.portProfileId || `${type}-default`,
    portProfileName: device.portProfileName || '',
    ports: Array.isArray(device.ports) ? device.ports : [],
    icon: Network
  }
}

function resourcePayload(source) {
  return {
    name: String(source.name || '').trim(),
    englishName: String(source.englishName || '').trim(),
    deviceType: String(source.deviceType || source.type || '').trim(),
    category: source.category,
    sortOrder: Number(source.sortOrder ?? source.order ?? 100),
    allowUserDrag: source.allowUserDrag !== undefined ? Boolean(source.allowUserDrag) : source.draggable !== false,
    allowAi: source.allowAi !== false,
    description: String(source.description || '').trim(),
    iconPath: String(source.iconPath || ''),
    iconHash: String(source.iconHash || '').trim(),
    isEnabled: source.isEnabled !== undefined ? Boolean(source.isEnabled) : source.enabled !== false,
    ports: Array.isArray(source.ports) ? source.ports : [],
    portProfileId: String(source.portProfileId || '').trim(),
    portProfileName: String(source.portProfileName || '').trim()
  }
}

function draftFromResource(resource) {
  const device = normalizeDevice(resource)
  return {
    ...emptyDraft(),
    name: device.name,
    englishName: device.englishName,
    deviceType: device.deviceType,
    category: device.category,
    sortOrder: device.sortOrder,
    allowUserDrag: device.allowUserDrag,
    allowAi: device.allowAi,
    isEnabled: device.isEnabled,
    description: device.description || '',
    iconPath: device.iconPath,
    iconFileName: device.iconFileName,
    originalFileName: device.originalFileName || device.iconFileName,
    iconMimeType: device.iconMimeType,
    iconSize: device.iconSize,
    iconHash: device.iconHash || '',
    ports: Array.isArray(device.ports) ? device.ports : [],
    portProfileId: device.portProfileId || '',
    portProfileName: device.portProfileName || ''
  }
}

function sortDevices(values) {
  return [...values].sort((left, right) => left.order - right.order || left.name.localeCompare(right.name, 'zh-CN'))
}

function replaceDevice(saved) {
  const next = normalizeDevice(saved)
  const index = devices.value.findIndex((device) => device.id === next.id)
  devices.value = sortDevices(index === -1
    ? [...devices.value, next]
    : devices.value.map((device, deviceIndex) => deviceIndex === index ? next : device))
}

async function loadDevices() {
  try {
    devices.value = sortDevices((await listTopologyResourcesForAdmin()).map(normalizeDevice))
  } catch (error) {
    showToast(error.message || '拓扑资源加载失败')
  }
}

async function loadCategories() {
  try {
    const values = await listTopologyResourceCategories()
    if (Array.isArray(values) && values.length) categories.value = values
  } catch {
    categories.value = [...defaultCategories]
  }
}

function openCreateResource() {
  draft.value = emptyDraft()
  fieldErrors.value = {}
  categoryOpen.value = false
  resourceModal.value = { open: true, mode: 'create', resourceId: '', loading: false }
}

async function openEditResource(resourceId) {
  draft.value = emptyDraft()
  fieldErrors.value = {}
  categoryOpen.value = false
  resourceModal.value = { open: true, mode: 'edit', resourceId, loading: true }
  try {
    const resource = await getTopologyResourceForAdmin(resourceId)
    if (resourceModal.value.open && resourceModal.value.resourceId === resourceId) draft.value = draftFromResource(resource)
  } catch (error) {
    closeResourceModal()
    showToast(error.message || '资源详情加载失败')
  } finally {
    if (resourceModal.value.open && resourceModal.value.resourceId === resourceId) resourceModal.value.loading = false
  }
}

function closeResourceModal() {
  resourceModal.value = { open: false, mode: 'create', resourceId: '', loading: false }
  fieldErrors.value = {}
  categoryOpen.value = false
  draft.value = emptyDraft()
}

function validateDraft() {
  const errors = {}
  if (!draft.value.name.trim()) errors.name = '请输入设备名称'
  if (!draft.value.deviceType.trim()) errors.deviceType = '请输入设备类型标识'
  else if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(draft.value.deviceType.trim())) errors.deviceType = '仅支持小写字母、数字和连字符'
  if (!draft.value.category) errors.category = '请选择设备分类'
  if (!draft.value.iconPath) errors.iconPath = '请上传设备图标'
  if (!Number.isInteger(Number(draft.value.sortOrder))) errors.sortOrder = '排序权重必须为整数'
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

async function saveResource() {
  if (!validateDraft() || saving.value || uploading.value) return
  saving.value = true
  try {
    const wasEdit = resourceModal.value.mode === 'edit'
    const payload = resourcePayload(draft.value)
    const saved = wasEdit
      ? await updateTopologyResource(resourceModal.value.resourceId, payload)
      : await createTopologyResource(payload)
    replaceDevice(saved)
    closeResourceModal()
    showToast(wasEdit ? '资源已更新' : '资源已创建')
  } catch (error) {
    if (error.code === 'TOPOLOGY_DEVICE_TYPE_EXISTS') fieldErrors.value = { ...fieldErrors.value, deviceType: error.message }
    else showToast(error.message || '资源保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleResource(device, field) {
  const changed = { ...device }
  if (field === 'enabled') {
    changed.enabled = !device.enabled
    changed.isEnabled = changed.enabled
  } else {
    changed.allowAi = !device.allowAi
  }
  try {
    replaceDevice(await updateTopologyResource(device.id, resourcePayload(changed)))
  } catch (error) {
    showToast(error.message || '资源状态保存失败')
  }
}

function chooseIcon() {
  if (!uploading.value) iconInput.value?.click()
}

async function uploadIcon(event) {
  const file = event.target?.files?.[0]
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['svg', 'png', 'webp'].includes(extension)) {
    showToast('仅支持SVG、PNG、WEBP格式')
  } else if (!file.size) {
    showToast('图标文件不能为空')
  } else if (file.size > 2 * 1024 * 1024) {
    showToast('图标文件不能超过2MB')
  } else {
    uploading.value = true
    try {
      const uploaded = await uploadTopologyResourceIcon(file)
      draft.value.iconPath = uploaded.iconPath
      draft.value.iconFileName = uploaded.fileName
      draft.value.originalFileName = uploaded.originalFileName || file.name
      draft.value.iconMimeType = uploaded.iconMimeType || file.type
      draft.value.iconSize = Number(uploaded.iconSize || file.size)
      draft.value.iconHash = uploaded.iconHash || ''
      fieldErrors.value = { ...fieldErrors.value, iconPath: '' }
      showToast('图标上传成功')
    } catch (error) {
      showToast(error.message || '图标上传失败，请重试')
    } finally {
      uploading.value = false
    }
  }
  event.target.value = ''
}

function removeIcon() {
  draft.value.iconPath = ''
  draft.value.iconFileName = ''
  draft.value.originalFileName = ''
  draft.value.iconMimeType = ''
  draft.value.iconSize = 0
  draft.value.iconHash = ''
}

function selectCategory(category) {
  draft.value.category = category
  categoryOpen.value = false
  fieldErrors.value = { ...fieldErrors.value, category: '' }
}

function fileNameFromPath(path) {
  return path ? path.split('/').filter(Boolean).at(-1) || '' : ''
}

function formatFileSize(value) {
  const size = Number(value || 0)
  if (!size) return ''
  return size >= 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.ceil(size / 1024))} KB`
}

function formatUpdatedAt(value) {
  const text = String(value ?? '').trim()
  const match = text.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : text || '刚刚'
}

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

onMounted(() => { loadCategories(); loadDevices() })
onBeforeUnmount(() => window.clearTimeout(toastTimer))
</script>

<template>
  <section class="admin-section-page topology-assets-page">
    <header class="admin-page-intro compact topology-assets-intro">
      <div><p>公共设备资源</p><h2>拓扑资源</h2><span>统一管理用户项目和 AI 拓扑生成可用的设备图标与设备类型。</span></div>
      <div class="topology-assets-actions"><button type="button" class="topology-primary-button" @click="openCreateResource"><Plus :size="16" /> 新增资源</button></div>
    </header>

    <section class="topology-assets-stats" aria-label="拓扑资源统计">
      <article><span>设备资源</span><strong>{{ devices.length }}</strong></article>
      <article><span>设备分类</span><strong>{{ new Set(devices.map((device) => device.category)).size }}</strong></article>
      <article class="is-enabled"><span>启用资源</span><strong>{{ activeCount }}</strong></article>
      <article class="is-disabled"><span>停用资源</span><strong>{{ devices.length - activeCount }}</strong></article>
    </section>

    <section class="topology-assets-toolbar">
      <div class="topology-assets-tabs" role="tablist"><button v-for="category in categoryTabs" :key="category" type="button" role="tab" :aria-selected="activeCategory === category" :class="{ active: activeCategory === category }" @click="activeCategory = category">{{ category }}</button></div>
      <span>AI 可用资源 {{ aiUsableDevices }} 个</span>
    </section>

    <section class="admin-card topology-assets-table-card">
      <div class="topology-assets-table-wrap"><table class="topology-assets-table"><thead><tr><th>图标</th><th>名称</th><th>英文名</th><th>设备类型</th><th>分类</th><th>端口模板</th><th>格式</th><th>状态</th><th>排序</th><th>允许 AI 使用</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="device in filteredDevices" :key="device.id">
          <td><span class="topology-asset-icon"><img v-if="device.asset" :src="device.asset" :alt="`${device.name} 图标`" /><component v-else :is="device.icon" :size="21" /></span></td>
          <td><strong>{{ device.name }}</strong><small>{{ device.iconPath ? '已上传图标' : '系统默认图标' }}</small></td><td>{{ device.englishName || '—' }}</td><td><code>{{ device.deviceType }}</code></td><td><span class="topology-asset-category">{{ device.category }}</span></td><td><span>{{ device.portProfileName || '默认端口模板' }}</span><small>{{ device.ports?.length || 0 }} 个端口</small></td><td><code>{{ device.format }}</code></td>
          <td><button type="button" class="topology-asset-status" :class="{ enabled: device.enabled }" @click="toggleResource(device, 'enabled')"><i></i>{{ device.enabled ? '启用' : '停用' }}</button></td><td>{{ device.order }}</td>
          <td><button type="button" class="topology-ai-toggle" :class="{ enabled: device.allowAi }" :aria-pressed="device.allowAi" @click="toggleResource(device, 'allowAi')"><ToggleLeft :size="17" />{{ device.allowAi ? '允许' : '禁止' }}</button></td>
          <td><time class="topology-assets-updated-at">{{ formatUpdatedAt(device.updatedAt) }}</time></td><td><button type="button" class="admin-table-action" @click="openEditResource(device.id)">编辑</button></td>
        </tr>
        <tr v-if="!filteredDevices.length"><td colspan="12" class="topology-assets-empty">该分类暂无公共设备资源</td></tr>
      </tbody></table></div>
    </section>

    <Transition name="admin-modal"><div v-if="resourceModal.open" class="admin-modal-backdrop topology-resource-backdrop" @pointerdown.self="closeResourceModal">
      <form class="admin-modal-card topology-resource-modal" role="dialog" aria-modal="true" :aria-label="modalTitle" @submit.prevent="saveResource">
        <header class="topology-resource-header"><div><h3>{{ modalTitle }}</h3><p>{{ modalDescription }}</p></div><button type="button" class="topology-resource-close" :aria-label="`关闭${modalTitle}`" @click="closeResourceModal"><X :size="18" /></button></header>
        <div v-if="resourceModal.loading" class="topology-resource-loading"><LoaderCircle :size="20" />正在读取资源信息…</div>
        <div v-else class="topology-resource-body">
          <section class="resource-section resource-basic-section"><div class="resource-section-heading"><strong>基础信息</strong><span>配置设备名称、分类及系统内部标识</span></div><div class="topology-resource-form">
            <label>设备名称 <b>*</b><input v-model="draft.name" maxlength="80" placeholder="例如：核心交换机" @input="fieldErrors.name = ''" /><small v-if="fieldErrors.name" class="field-error">{{ fieldErrors.name }}</small></label>
            <label>英文名称<input v-model="draft.englishName" maxlength="120" placeholder="例如：Core Switch" /></label>
            <label>设备类型 / 唯一标识 <b>*</b><input v-model="draft.deviceType" maxlength="80" autocomplete="off" placeholder="例如：core-switch" @input="fieldErrors.deviceType = ''" /><small v-if="fieldErrors.deviceType" class="field-error">{{ fieldErrors.deviceType }}</small></label>
            <div class="topology-select-field"><span>设备分类 <b>*</b></span><button type="button" class="topology-category-trigger" :aria-expanded="categoryOpen" @click="categoryOpen = !categoryOpen"><span>{{ draft.category || '请选择设备分类' }}</span><ChevronDown :size="16" /></button><div v-if="categoryOpen" class="topology-category-menu"><button v-for="category in categories" :key="category" type="button" :class="{ selected: category === draft.category }" @click="selectCategory(category)">{{ category }}</button></div><small v-if="fieldErrors.category" class="field-error">{{ fieldErrors.category }}</small></div>
            <label>排序权重<input v-model.number="draft.sortOrder" type="number" step="1" placeholder="100" @input="fieldErrors.sortOrder = ''" /><small v-if="fieldErrors.sortOrder" class="field-error">{{ fieldErrors.sortOrder }}</small></label>
            <label>端口模板标识<input v-model="draft.portProfileId" placeholder="例如：access-switch-24" /></label>
          </div></section>

          <section class="resource-section resource-port-section"><div class="resource-section-heading"><strong>端口规格</strong><span>定义该设备默认使用的端口模板</span></div><div class="port-profile-name"><label>端口模板名称<input v-model="draft.portProfileName" placeholder="例如：24口接入交换机" /></label><div class="port-profile-stats"><span>端口数量 <b>{{ draftPortCount }}</b></span><span>端口类型 <b>{{ draftPortType }}</b></span><span>模板标识 <b>{{ draft.portProfileId || '—' }}</b></span></div></div><div class="port-preview"><header><strong>端口列表</strong><span>共 {{ draftPortCount }} 个端口</span></header><div class="port-chips"><span v-for="port in draft.ports" :key="port">{{ port }}</span><em v-if="!draft.ports.length">暂无端口</em></div><label class="port-editor-label">编辑端口列表<textarea :value="draft.ports.join(', ')" rows="2" placeholder="例如：GE0/0/1, GE0/0/2" @input="draft.ports = $event.target.value.split(',').map((port) => port.trim()).filter(Boolean)" /></label></div></section>

          <section class="resource-section resource-status-section"><div class="resource-section-heading"><strong>使用状态</strong><span>控制该资源是否允许在系统中使用</span></div><div class="resource-settings"><label class="resource-setting-row"><span><strong>启用资源</strong><small>启用后可被系统设备资源接口正常调用</small></span><i class="resource-switch" :class="{ on: draft.isEnabled }"><input v-model="draft.isEnabled" type="checkbox" /><b></b></i></label><label class="resource-setting-row"><span><strong>允许用户拖拽</strong><small>允许用户将设备添加到拓扑画布</small></span><i class="resource-switch" :class="{ on: draft.allowUserDrag }"><input v-model="draft.allowUserDrag" type="checkbox" /><b></b></i></label><label class="resource-setting-row"><span><strong>允许 AI 使用</strong><small>允许 AI 在生成拓扑时使用该资源</small></span><i class="resource-switch" :class="{ on: draft.allowAi }"><input v-model="draft.allowAi" type="checkbox" /><b></b></i></label></div></section>

          <section class="resource-section resource-icon-section"><div class="resource-section-heading"><strong>设备图标 <b>*</b></strong><span>管理用户端和画布统一使用的设备图标</span></div><div class="topology-icon-upload"><div class="topology-icon-preview"><img v-if="iconPreview" :src="iconPreview" :alt="`${draft.name || '设备'} 图标预览`" /><Network v-else :size="34" /></div><div class="topology-icon-details"><strong>{{ iconLabel }}</strong><span>{{ draft.iconPath ? `${draft.iconMimeType || draft.iconPath.split('.').pop()?.toUpperCase()} · 本地图标库` : 'SVG / PNG / WEBP · 最大 2MB' }}</span><div class="topology-icon-actions"><button type="button" class="topology-secondary-button" :disabled="uploading" @click="chooseIcon"><ImagePlus :size="15" />{{ draft.iconPath ? '更换图标' : '选择图标' }}</button><button v-if="draft.iconPath" type="button" class="topology-icon-delete" :disabled="uploading" @click="removeIcon"><Trash2 :size="15" />删除图标</button></div></div><input ref="iconInput" type="file" accept=".svg,.png,.webp,image/svg+xml,image/png,image/webp" @change="uploadIcon" /></div><label class="topology-icon-path">图标路径<input :value="draft.iconPath" readonly placeholder="上传后自动生成" /></label><small v-if="fieldErrors.iconPath" class="field-error">{{ fieldErrors.iconPath }}</small><p v-if="uploading" class="topology-uploading"><LoaderCircle :size="14" />正在上传图标…</p></section>
          <label class="topology-resource-description">设备说明<textarea v-model="draft.description" rows="3" maxlength="1000" placeholder="说明资源适用的网络工程场景" /></label>
        </div>
        <footer class="topology-resource-footer"><button type="button" class="topology-cancel-button" :disabled="saving" @click="closeResourceModal">取消</button><button type="submit" class="topology-primary-button" :disabled="saving || uploading"><LoaderCircle v-if="saving" class="is-spinning" :size="16" />{{ saving ? '正在保存' : resourceModal.mode === 'edit' ? '保存修改' : '保存资源' }}</button></footer>
      </form>
    </div></Transition>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.topology-assets-page { width: min(100%, 1500px); margin: 0 auto; padding: 30px 32px 44px; }
.topology-assets-intro { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.topology-assets-intro p { margin: 0 0 7px; color: #2563eb; font-size: 13px; font-weight: 700; }
.topology-assets-intro h2 { margin: 0; color: #111827; font-size: 30px; line-height: 1.2; }
.topology-assets-intro span { display: block; max-width: 720px; margin-top: 8px; color: #64748b; font-size: 14px; line-height: 1.65; }
.topology-assets-actions { display: flex; gap: 8px; }
.topology-primary-button, .topology-secondary-button, .topology-cancel-button, .topology-icon-delete { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 38px; border-radius: 8px; font: inherit; font-size: 13px; font-weight: 650; cursor: pointer; transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease; }
.topology-primary-button { border: 1px solid #2563eb; background: #2563eb; color: #fff; padding: 0 14px; }.topology-primary-button:hover:not(:disabled) { border-color: #1d4ed8; background: #1d4ed8; }.topology-primary-button:disabled, .topology-secondary-button:disabled, .topology-cancel-button:disabled, .topology-icon-delete:disabled { cursor: not-allowed; opacity: .65; }
.topology-assets-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; }.topology-assets-stats article { display: grid; gap: 10px; padding: 18px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; box-shadow: 0 6px 18px rgba(15, 23, 42, .04); }.topology-assets-stats span { color: #64748b; font-size: 13px; font-weight: 600; }.topology-assets-stats strong { color: #1f2937; font-size: 28px; line-height: 1; }.topology-assets-stats .is-enabled strong { color: #059669; }.topology-assets-stats .is-disabled strong { color: #dc2626; }
.topology-assets-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 14px; }.topology-assets-tabs { display: flex; max-width: 100%; gap: 5px; overflow-x: auto; }.topology-assets-tabs button { min-height: 36px; padding: 0 12px; border: 0; border-radius: 6px; background: transparent; color: #475569; font: inherit; font-size: 13px; font-weight: 600; white-space: nowrap; cursor: pointer; }.topology-assets-tabs button:hover { background: #f8fafc; color: #1e293b; }.topology-assets-tabs button.active { background: #eff6ff; color: #2563eb; }.topology-assets-toolbar > span { color: #64748b; font-size: 13px; font-weight: 600; white-space: nowrap; }
.topology-assets-table-card { overflow: hidden; padding: 0; border-color: #e2e8f0; background: #fff; }.topology-assets-table-wrap { overflow-x: auto; }.topology-assets-table { width: 100%; min-width: 1250px; border-collapse: collapse; font-size: 13px; }.topology-assets-table th { padding: 13px 15px; border-bottom: 1px solid #e2e8f0; background: #f8fafc; color: #475569; font-size: 12px; text-align: left; white-space: nowrap; }.topology-assets-table td { padding: 14px 15px; border-bottom: 1px solid #edf2f7; color: #475569; vertical-align: middle; white-space: nowrap; }.topology-assets-table tbody tr:hover { background: #f8fafc; }.topology-assets-table td:nth-child(2) { display: grid; gap: 3px; }.topology-assets-table td:nth-child(2) strong { color: #1f2937; }.topology-assets-table td:nth-child(2) small, .topology-assets-table code, .topology-assets-updated-at { color: #64748b; font-size: 12px; }.topology-assets-table code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }.topology-asset-category { color: #334155; font-weight: 600; }.topology-asset-icon { display: grid; width: 34px; height: 34px; place-items: center; overflow: hidden; border-radius: 8px; background: #eff6ff; color: #334155; }.topology-asset-icon img { width: 23px; height: 23px; object-fit: contain; }.topology-asset-status, .topology-ai-toggle { display: inline-flex; align-items: center; gap: 6px; min-height: 28px; padding: 4px 8px; border: 1px solid #cbd5e1; border-radius: 999px; background: #fff; color: #64748b; font: inherit; font-size: 12px; font-weight: 600; cursor: pointer; }.topology-asset-status i { width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; }.topology-asset-status.enabled { border-color: #86efac; background: #ecfdf5; color: #047857; }.topology-asset-status.enabled i { background: #10b981; }.topology-ai-toggle.enabled { border-color: #bfdbfe; background: #eff6ff; color: #2563eb; }.topology-assets-table .admin-table-action { color: #2563eb; font-size: 13px; font-weight: 650; }.topology-assets-table .admin-table-action:hover { background: #eff6ff; color: #1d4ed8; }.topology-assets-empty { height: 120px; color: #94a3b8 !important; text-align: center; }
.topology-resource-backdrop { background: rgba(15, 23, 42, .35); backdrop-filter: blur(3px); }.topology-resource-modal { width: min(620px, calc(100vw - 28px)); max-height: min(780px, calc(100vh - 28px)); overflow: auto; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff !important; color: #1f2937; box-shadow: 0 24px 60px rgba(15, 23, 42, .2); }.topology-resource-modal header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding-bottom: 16px; border-bottom: 1px solid #e5e7eb; }.topology-resource-modal header h3 { margin: 0; color: #111827; font-size: 20px; }.topology-resource-modal header p { margin: 7px 0 0; color: #64748b; font-size: 13px; line-height: 1.55; }.topology-resource-loading { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 260px; color: #64748b; font-size: 14px; }.topology-resource-form { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 20px 0 8px; }.topology-resource-form > label, .topology-select-field, .topology-enabled-field { position: relative; display: grid; gap: 6px; color: #334155; font-size: 13px; font-weight: 650; }.topology-resource-form input, .topology-resource-form textarea { box-sizing: border-box; width: 100%; min-height: 38px; padding: 8px 10px; border: 1px solid #e2e8f0; border-radius: 8px; outline: 0; background: #fff !important; color: #1f2937 !important; font: inherit; font-weight: 400; }.topology-resource-form textarea { min-height: 96px; resize: vertical; }.topology-resource-form input::placeholder, .topology-resource-form textarea::placeholder { color: #94a3b8; }.topology-resource-form input:focus, .topology-resource-form textarea:focus { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37, 99, 235, .08); }.field-error { color: #dc2626; font-size: 12px; font-weight: 500; }.topology-form-section-title { grid-column: 1 / -1; margin: 2px 0 -5px; color: #64748b; font-size: 12px; font-weight: 750; }.topology-category-trigger { display: flex; align-items: center; justify-content: space-between; min-height: 38px; padding: 0 10px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; color: #1f2937; font: inherit; font-size: 13px; font-weight: 400; cursor: pointer; }.topology-category-trigger:hover, .topology-category-trigger:focus { border-color: #2563eb; outline: 0; box-shadow: 0 0 0 3px rgba(37, 99, 235, .08); }.topology-category-menu { position: absolute; z-index: 3; top: calc(100% + 5px); right: 0; left: 0; overflow: hidden; padding: 4px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; box-shadow: 0 12px 28px rgba(15, 23, 42, .14); }.topology-category-menu button { display: block; width: 100%; min-height: 34px; padding: 0 9px; border: 0; border-radius: 5px; background: transparent; color: #334155; font: inherit; font-size: 13px; text-align: left; cursor: pointer; }.topology-category-menu button:hover { background: #f8fafc; }.topology-category-menu button.selected { background: #eff6ff; color: #2563eb; font-weight: 650; }.topology-enabled-field { align-content: start; }.topology-enabled-field > span:first-child { margin-bottom: 2px; }
.topology-icon-section, .topology-permission-section, .topology-resource-description { grid-column: 1 / -1; }.topology-icon-section { display: grid; gap: 9px; padding-top: 3px; }.topology-icon-section .topology-form-section-title, .topology-permission-section .topology-form-section-title { margin: 0; }.topology-icon-upload { display: flex; align-items: center; gap: 14px; min-height: 112px; padding: 14px; border: 1px dashed #cbd5e1; border-radius: 8px; background: #f8fbff; }.topology-icon-upload:hover { border-color: #2563eb; background: #f8faff; }.topology-icon-preview { display: grid; flex: 0 0 78px; width: 78px; height: 78px; place-items: center; overflow: hidden; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; color: #64748b; }.topology-icon-preview img { width: 52px; height: 52px; object-fit: contain; }.topology-icon-details { display: grid; min-width: 0; gap: 5px; }.topology-icon-details strong { overflow: hidden; color: #1f2937; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.topology-icon-details > span { color: #64748b; font-size: 12px; }.topology-icon-details b { font-weight: 500; }.topology-icon-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 5px; }.topology-secondary-button { min-height: 32px; padding: 0 10px; border: 1px solid #cbd5e1; background: #fff; color: #334155; }.topology-secondary-button:hover:not(:disabled) { border-color: #93c5fd; background: #eff6ff; color: #1d4ed8; }.topology-icon-delete { min-height: 32px; padding: 0; border: 0; background: transparent; color: #dc2626; }.topology-icon-delete:hover:not(:disabled) { color: #b91c1c; }.topology-icon-upload input { display: none; }.topology-icon-path { display: grid; gap: 6px; color: #334155; font-size: 13px; font-weight: 650; }.topology-icon-path input { color: #64748b !important; background: #f8fafc !important; cursor: default; }.topology-uploading { display: flex; align-items: center; gap: 6px; margin: 0; color: #2563eb; font-size: 12px; }.topology-permission-section { display: flex; flex-wrap: wrap; align-items: center; gap: 14px 22px; padding: 2px 0; }.topology-permission-section .topology-form-section-title { width: 100%; }.topology-checkbox { display: inline-flex !important; align-items: center; gap: 8px !important; color: #334155; font-size: 13px; font-weight: 500 !important; cursor: pointer; }.topology-checkbox input { width: 16px; min-height: 16px; height: 16px; margin: 0; accent-color: #2563eb; }.topology-resource-description { display: grid !important; gap: 6px; }.topology-resource-modal footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 16px; border-top: 1px solid #e5e7eb; }.topology-cancel-button { min-width: 76px; border: 1px solid #e2e8f0; background: #fff; color: #334155; }.topology-cancel-button:hover:not(:disabled) { border-color: #cbd5e1; background: #f8fafc; }.is-spinning { animation: topology-spin 900ms linear infinite; }
@keyframes topology-spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) { .topology-assets-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .topology-assets-page { padding: 20px 14px 30px; }.topology-assets-intro, .topology-assets-toolbar { align-items: flex-start; flex-direction: column; }.topology-assets-actions { width: 100%; }.topology-assets-actions button { width: 100%; }.topology-resource-form { grid-template-columns: 1fr; }.topology-resource-form > *, .topology-form-section-title, .topology-icon-section, .topology-permission-section, .topology-resource-description { grid-column: auto; }.topology-icon-upload { align-items: flex-start; }.topology-resource-modal footer { position: sticky; bottom: 0; padding-bottom: 2px; background: #fff; }.topology-resource-modal footer > * { flex: 1; } }

/* Resource editor redesign: fixed chrome with a calm, scrollable configuration body. */
.topology-resource-modal { display: flex; flex-direction: column; width: min(780px, calc(100vw - 48px)); max-width: calc(100vw - 48px); height: min(84vh, 760px); max-height: 84vh; overflow: hidden; border-radius: 14px; background: #fff !important; font-style: normal !important; }
.topology-resource-header { flex: 0 0 auto; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; min-height: 86px; padding: 22px 24px 18px; border-bottom: 1px solid #e5e7eb; }
.topology-resource-header h3 { margin: 0; color: #0f172a; font-size: 20px; font-weight: 700; line-height: 1.3; }
.topology-resource-header p { max-width: 580px; margin: 7px 0 0; color: #64748b; font-size: 13px; line-height: 1.5; }
.topology-resource-close { display: grid; flex: 0 0 auto; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 8px; background: transparent; color: #64748b; cursor: pointer; }
.topology-resource-close:hover { background: #f1f5f9; color: #334155; }
.topology-resource-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; padding: 20px 24px 24px; scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent; }
.topology-resource-body::-webkit-scrollbar { width: 6px; }.topology-resource-body::-webkit-scrollbar-track { background: transparent; }.topology-resource-body::-webkit-scrollbar-thumb { border-radius: 999px; background: #cbd5e1; }.topology-resource-body::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
.resource-section { margin-bottom: 22px; padding-top: 20px; border-top: 1px solid #f1f5f9; }.resource-section:first-child { padding-top: 0; border-top: 0; }
.resource-section-heading { display: grid; gap: 4px; margin-bottom: 12px; }.resource-section-heading strong { color: #1e293b; font-size: 14px; font-weight: 650; line-height: 1.35; }.resource-section-heading span { color: #94a3b8; font-size: 12px; line-height: 1.5; }.resource-section-heading b, .topology-resource-form label > b, .topology-select-field span b { color: #ef4444; font-weight: 650; }
.topology-resource-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; row-gap: 16px; padding: 0; }
.topology-resource-form > label, .topology-select-field, .topology-resource-description { display: grid; gap: 6px; color: #475569; font-size: 13px; font-weight: 500; }
.topology-resource-form input, .topology-resource-form textarea, .topology-category-trigger, .topology-icon-path input { min-height: 40px; box-sizing: border-box; border: 1px solid #dce3ec; border-radius: 9px; background: #fff !important; color: #1e293b !important; font: inherit; font-size: 14px; font-style: normal; outline: 0; transition: border-color 150ms ease, box-shadow 150ms ease; }
.topology-resource-form input, .topology-icon-path input { padding: 0 12px; }.topology-resource-form textarea { padding: 10px 12px; line-height: 1.7; resize: vertical; }.topology-resource-form input::placeholder, .topology-resource-form textarea::placeholder { color: #94a3b8; }.topology-resource-form input:hover, .topology-resource-form textarea:hover, .topology-category-trigger:hover { border-color: #cbd5e1; }.topology-resource-form input:focus, .topology-resource-form textarea:focus, .topology-category-trigger:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,.08); }
.topology-select-field { position: relative; }.topology-select-field > span { color: #475569; font-size: 13px; font-weight: 500; }.topology-category-trigger { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 0 12px; cursor: pointer; }.topology-category-menu { top: calc(100% + 7px); padding: 5px; border-radius: 10px; box-shadow: 0 10px 28px rgba(15,23,42,.12); }.topology-category-menu button { min-height: 38px; padding: 0 10px; border-radius: 7px; font-size: 13px; }.topology-category-menu button:hover { background: #f8fafc; }.topology-category-menu button.selected { background: #eff6ff; color: #2563eb; }
.port-profile-name { display: grid; grid-template-columns: minmax(0, 1fr) 1.5fr; gap: 18px; align-items: end; }.port-profile-name label { display: grid; gap: 6px; color: #475569; font-size: 13px; font-weight: 500; }.port-profile-name input { min-height: 40px; padding: 0 12px; border: 1px solid #dce3ec; border-radius: 9px; color: #1e293b; font-size: 14px; outline: 0; }.port-profile-name input:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,.08); }.port-profile-stats { display: flex; flex-wrap: wrap; gap: 8px 18px; padding-bottom: 10px; color: #64748b; font-size: 12px; }.port-profile-stats span { display: inline-flex; gap: 5px; }.port-profile-stats b { color: #334155; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-weight: 600; }
.port-preview { margin-top: 16px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; }.port-preview > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 9px; }.port-preview > header strong { color: #334155; font-size: 13px; font-weight: 650; }.port-preview > header span { color: #64748b; font-size: 12px; }.port-chips { display: flex; flex-wrap: wrap; gap: 6px; max-height: 130px; overflow-y: auto; padding: 2px; scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent; }.port-chips::-webkit-scrollbar { width: 5px; }.port-chips::-webkit-scrollbar-thumb { border-radius: 999px; background: #cbd5e1; }.port-chips span { display: inline-flex; align-items: center; height: 26px; padding: 0 8px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; color: #475569; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }.port-chips em { color: #94a3b8; font-size: 12px; font-style: normal; }.port-editor-label { display: grid; gap: 5px; margin-top: 10px; color: #94a3b8; font-size: 11px; }.port-editor-label textarea { width: 100%; min-height: 56px; padding: 8px 10px; border: 1px solid #e2e8f0; border-radius: 7px; background: #fff; color: #475569; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; line-height: 1.7; outline: 0; resize: vertical; }
.resource-settings { display: grid; gap: 8px; }.resource-setting-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; min-height: 56px; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 9px; background: #fff; }.resource-setting-row > span { display: grid; gap: 3px; }.resource-setting-row strong { color: #334155; font-size: 13px; font-weight: 600; }.resource-setting-row small { color: #94a3b8; font-size: 12px; line-height: 1.4; }.resource-switch { position: relative; display: inline-flex; flex: 0 0 auto; width: 34px; height: 18px; }.resource-switch input { position: absolute; width: 1px; height: 1px; opacity: 0; }.resource-switch b { position: absolute; inset: 0; border-radius: 999px; background: #cbd5e1; cursor: pointer; transition: background 150ms ease; }.resource-switch b::after { position: absolute; top: 2px; left: 2px; width: 14px; height: 14px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(15,23,42,.18); content: ''; transition: transform 150ms ease; }.resource-switch.on b { background: #2563eb; }.resource-switch.on b::after { transform: translateX(16px); }
.resource-icon-section .topology-icon-upload { min-height: 116px; padding: 16px; border-style: solid; border-radius: 10px; background: #fff; }.resource-icon-section .topology-icon-preview { flex: 0 0 72px; width: 72px; height: 72px; border-radius: 12px; background: #f8fafc; }.resource-icon-section .topology-icon-preview img { width: 48px; height: 48px; }.resource-icon-section .topology-icon-details { gap: 6px; }.resource-icon-section .topology-icon-details strong { color: #1e293b; font-size: 14px; font-weight: 600; }.resource-icon-section .topology-icon-details > span { color: #94a3b8; font-size: 12px; font-style: normal; }.resource-icon-section .topology-secondary-button { min-height: 32px; padding: 0 12px; border-radius: 7px; }.resource-icon-section .topology-icon-delete { min-height: 32px; padding: 0 8px; border-radius: 6px; font-size: 12px; }.resource-icon-section .topology-icon-delete:hover { background: #fef2f2; }.topology-icon-path { margin-top: 10px; }.topology-icon-path input { min-height: 36px; background: #f8fafc !important; color: #64748b !important; font-size: 12px; }.topology-resource-description { margin-top: 0; }.topology-resource-description textarea { min-height: 82px; }
.topology-resource-footer { flex: 0 0 64px; display: flex; align-items: center; justify-content: flex-end; gap: 10px; padding: 12px 24px; border-top: 1px solid #e5e7eb; background: rgba(255,255,255,.97); }.topology-resource-footer .topology-primary-button, .topology-resource-footer .topology-cancel-button { min-height: 38px; border-radius: 8px; font-size: 13px; }.topology-resource-footer .topology-primary-button { padding: 0 18px; border-color: #2563eb; background: #2563eb; }.topology-resource-footer .topology-primary-button:hover:not(:disabled) { border-color: #1d4ed8; background: #1d4ed8; }.topology-resource-footer .topology-cancel-button { padding: 0 16px; border-color: #dce3ec; background: #fff; color: #475569; }.topology-resource-footer .topology-cancel-button:hover:not(:disabled) { background: #f8fafc; }
@media (max-width: 720px) { .topology-resource-modal { width: calc(100vw - 24px); max-width: calc(100vw - 24px); height: 90vh; max-height: 90vh; }.topology-resource-header, .topology-resource-body { padding-left: 18px; padding-right: 18px; }.topology-resource-footer { padding-left: 18px; padding-right: 18px; }.topology-resource-form, .port-profile-name { grid-template-columns: 1fr; }.port-profile-stats { padding-bottom: 0; }.resource-setting-row { gap: 12px; }.resource-setting-row small { max-width: 250px; } }

/* Theme-specific surfaces must not override the light palette. */
.topology-assets-intro h2, .topology-assets-stats strong { font-weight: 700; }
:global(html[data-theme='dark']) .topology-assets-page { color: #e2e8f0; }
:global(html[data-theme='dark']) .topology-assets-stats article,
:global(html[data-theme='dark']) .topology-assets-table-card,
:global(html[data-theme='dark']) .topology-resource-modal { background: #111827; border-color: #334155; }
:global(html[data-theme='dark']) .topology-assets-intro h2,
:global(html[data-theme='dark']) .topology-assets-stats article strong,
:global(html[data-theme='dark']) .topology-assets-table td:nth-child(2) strong { color: #f1f5f9; }
:global(html[data-theme='dark']) .topology-assets-table th { background: #1e293b; color: #e2e8f0; border-color: #334155; }
:global(html[data-theme='dark']) .topology-assets-table td { color: #cbd5e1; border-color: #334155; }
:global(html[data-theme='dark']) .topology-assets-table tbody tr:hover,
:global(html[data-theme='dark']) .topology-assets-tabs button:hover { background: #1e293b; }
:global(html[data-theme='dark']) .topology-assets-tabs button { color: #cbd5e1; }
:global(html[data-theme='dark']) .topology-assets-tabs button.active { color: #93c5fd; background: #1e3a5f; }
:global(html[data-theme='dark']) .topology-assets-table .admin-table-action { color: #93c5fd; }
:global(html[data-theme='dark']) .topology-assets-stats .is-enabled strong { color: #34d399; }
:global(html[data-theme='dark']) .topology-assets-stats .is-disabled strong { color: #f87171; }

</style>
