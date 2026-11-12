<script setup>
import { BookOpen, CheckCircle2, ChevronLeft, ChevronRight, Download, FileSpreadsheet, FileText, LoaderCircle, Plus, Search, SlidersHorizontal, Trash2, UploadCloud, X, XCircle, ZoomIn, ZoomOut } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import LogFilterDropdown from './LogFilterDropdown.vue'
import { deleteKnowledgeDocument, listKnowledgeDocuments, reparseKnowledgeDocument, uploadKnowledgeDocument } from '../../api/admin'

const documents = ref([])
const searchQuery = ref('')
const categoryFilter = ref('all')
const typeFilter = ref('all')
const statusFilter = ref('all')
const pageSize = ref(20)
const currentPage = ref(1)
const jumpPage = ref('')
const isDrawerOpen = ref(false)
const selectedFiles = ref([])
const uploadCategory = ref('网络基础')
const selectedDocument = ref(null)
const pendingDeleteDocument = ref(null)
const previewDocument = ref(null)
const previewState = ref('idle')
const previewText = ref('')
const previewError = ref('')
const previewObjectUrl = ref('')
const previewZoom = ref(100)
const previewPage = ref(1)
const previewTotalPages = ref(1)
const toast = ref('')
let toastTimer = 0
let previousBodyOverflow = ''

const categories = ['网络基础', '交换技术', '路由技术', '网络安全', '无线网络', '综合布线', '服务器与存储', '设备技术资料', '网络设计案例', '行业规范']
const statusLabels = { pending: '待解析', parsing: '解析中', vectorizing: '向量化中', ready: '向量化就绪', failed: '解析失败' }
const categoryOptions = computed(() => [{ value: 'all', label: '全部分类' }, ...categories.map((category) => ({ value: category, label: category }))])
const typeOptions = [{ value: 'all', label: '全部类型' }, { value: 'pdf', label: 'PDF' }, { value: 'docx', label: 'Word' }, { value: 'txt', label: 'TXT' }, { value: 'xlsx', label: 'XLSX' }]
const statusOptions = [{ value: 'all', label: '全部状态' }, { value: 'pending', label: '待解析', tone: 'pending' }, { value: 'parsing', label: '解析中', tone: 'processing' }, { value: 'vectorizing', label: '向量化中', tone: 'processing' }, { value: 'ready', label: '向量化就绪', tone: 'ready' }, { value: 'failed', label: '解析失败', tone: 'failed' }]
const uploadCategoryOptions = computed(() => categories.map((category) => ({ value: category, label: category })))
const pageSizeOptions = [10, 20, 50, 100].map((value) => ({ value, label: `${value} 条` }))

const filteredDocuments = computed(() => documents.value.filter((document) => {
  const keyword = searchQuery.value.trim().toLowerCase()
  const matchesKeyword = !keyword || [document.name, document.category, document.uploader].some((value) => String(value || '').toLowerCase().includes(keyword))
  return matchesKeyword && (categoryFilter.value === 'all' || document.category === categoryFilter.value) && (typeFilter.value === 'all' || document.format === typeFilter.value) && (statusFilter.value === 'all' || document.status === statusFilter.value)
}))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredDocuments.value.length / pageSize.value)))
const pagedDocuments = computed(() => filteredDocuments.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
const visiblePages = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, currentPage.value - 3), Math.min(totalPages.value, currentPage.value + 2)))
const rangeText = computed(() => {
  if (!filteredDocuments.value.length) return '暂无匹配文档'
  const start = (currentPage.value - 1) * pageSize.value + 1
  return `显示 ${start}-${Math.min(currentPage.value * pageSize.value, filteredDocuments.value.length)} 条，共 ${filteredDocuments.value.length} 条`
})
const selectedFileLabel = computed(() => !selectedFiles.value.length ? '支持 PDF、Word、TXT、XLSX，单次最多 20 个文件' : selectedFiles.value.length === 1 ? selectedFiles.value[0].name : `已选择 ${selectedFiles.value.length} 个文件`)

watch([searchQuery, categoryFilter, typeFilter, statusFilter, pageSize], () => { currentPage.value = 1 })
watch(totalPages, () => { if (currentPage.value > totalPages.value) currentPage.value = totalPages.value })

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

function normalizeDocument(document) {
  const format = String(document.format || document.type || 'txt').replace('.', '').toLowerCase()
  const status = document.status === 'processing' ? 'vectorizing' : document.status || 'pending'
  return { ...document, id: document.key ?? document.id, format, status, updatedAt: document.updatedAt || document.uploadedAt || '刚刚', uploadedAt: document.uploadedAt || '刚刚', uploader: document.uploader || '系统管理员', size: document.size || '未知大小', chunks: document.chunks ?? 0 }
}

async function loadDocuments() {
  try {
    const stored = await listKnowledgeDocuments()
    documents.value = stored.map(normalizeDocument)
  } catch (error) {
    showToast(error.message || '知识文档加载失败')
  }
}

function documentIcon(document) { return document.format === 'xlsx' ? FileSpreadsheet : FileText }
function documentFormatLabel(document) { return document.format === 'docx' ? 'WORD' : document.format.toUpperCase() }
function openDrawer() { selectedFiles.value = []; uploadCategory.value = '网络基础'; isDrawerOpen.value = true }
function selectFiles(fileList) {
  const files = Array.from(fileList || []).filter((file) => /\.(pdf|docx?|xlsx?|txt)$/i.test(file.name))
  if (!files.length) return showToast('请选择 PDF、Word、TXT 或 XLSX 文档')
  selectedFiles.value = files.slice(0, 20)
}

async function createDocuments() {
  if (!selectedFiles.value.length) return showToast('请先选择需要上传的知识文档')
  try {
    const created = await Promise.all(selectedFiles.value.map((file) => uploadKnowledgeDocument(file, uploadCategory.value)))
    documents.value.unshift(...created.map(normalizeDocument))
    isDrawerOpen.value = false
    showToast(`已创建 ${created.length} 个文档处理任务`)
  } catch (error) {
    showToast(error.message || '文档任务创建失败')
  }
}

function reparseDocument(document) {
  reparseKnowledgeDocument(document.id).then((updated) => {
    Object.assign(document, normalizeDocument(updated))
    showToast(`${document.name} 已重新解析并写入数据库`)
  }).catch((error) => showToast(error.message || '文档重新解析失败'))
}
function revokePreviewUrl() {
  if (previewObjectUrl.value) URL.revokeObjectURL(previewObjectUrl.value)
  previewObjectUrl.value = ''
}
function closePreview() {
  revokePreviewUrl()
  previewDocument.value = null
  previewState.value = 'idle'
  previewText.value = ''
  previewError.value = ''
  previewZoom.value = 100
  previewPage.value = 1
  previewTotalPages.value = 1
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
}
async function viewDocument(document) {
  closePreview()
  if (typeof globalThis.document !== 'undefined') {
    previousBodyOverflow = globalThis.document.body.style.overflow
    globalThis.document.body.style.overflow = 'hidden'
  }
  previewDocument.value = document
  previewState.value = 'loading'
  try {
    const response = await fetch(`/api/admin/knowledge/documents/${document.id}/view`, { credentials: 'include', headers: { Accept: 'text/html' } })
    if (!response.ok) throw new Error('文档预览请求失败')
    const html = await response.text()
    const parsed = new DOMParser().parseFromString(html, 'text/html')
    previewText.value = parsed.querySelector('pre')?.textContent || '该文档暂无可查看的解析文本，请重新解析后再试。'
    if (document.format === 'pdf') {
      const fileResponse = await fetch(`/api/admin/knowledge/documents/${document.id}/download`, { credentials: 'include' })
      if (fileResponse.ok) previewObjectUrl.value = URL.createObjectURL(await fileResponse.blob())
    }
    previewState.value = 'ready'
  } catch (error) {
    previewError.value = error.message || '暂不支持在线预览该文件'
    previewState.value = 'error'
  }
}
function downloadDocument(document) { window.open(`/api/admin/knowledge/documents/${document.id}/download`, '_blank', 'noopener') }
function requestDocumentDeletion(document) { pendingDeleteDocument.value = document }
async function confirmDocumentDeletion() {
  const document = pendingDeleteDocument.value
  if (!document) return
  try {
    await deleteKnowledgeDocument(document.id)
    documents.value = documents.value.filter((item) => item.id !== document.id)
    pendingDeleteDocument.value = null
    showToast(`已删除 ${document.name}`)
  } catch (error) { showToast(error.message || '文档删除失败') }
}
function goToPage(page) { currentPage.value = Math.min(Math.max(Number(page) || 1, 1), totalPages.value) }
function submitJump() { goToPage(jumpPage.value); jumpPage.value = '' }

function handleKeydown(event) {
  if (event.key === 'Escape' && previewDocument.value) closePreview()
}
onMounted(() => {
  loadDocuments()
  window.addEventListener('keydown', handleKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  closePreview()
  window.clearTimeout(toastTimer)
})
</script>

<template>
  <section class="admin-section-page kb-documents-page">
    <header class="admin-page-intro compact kb-documents-intro"><div><span>KNOWLEDGE DOCUMENT OPERATIONS</span><h2>文档管理</h2><p>管理用于 AI 网络规划和专业问答的知识库文档。</p></div><button type="button" class="admin-primary-button kb-documents-create" @click="openDrawer"><Plus :size="16" /> 新增文档</button></header>

    <section class="admin-card kb-documents-card">
      <header class="kb-documents-toolbar"><label class="kb-documents-search"><Search :size="17" /><input v-model="searchQuery" type="search" placeholder="搜索文档名称、分类或上传人" /></label><div class="kb-documents-filters"><LogFilterDropdown class="admin-knowledge-filter" v-model="categoryFilter" label="知识分类" :options="categoryOptions" /><LogFilterDropdown class="admin-knowledge-filter" v-model="typeFilter" label="文件类型" :options="typeOptions" /><LogFilterDropdown class="admin-knowledge-filter" v-model="statusFilter" label="解析状态" :options="statusOptions" :leading-icon="SlidersHorizontal" /></div></header>
      <div class="kb-documents-table-wrap"><table class="admin-knowledge-table kb-documents-table"><thead><tr><th>文档名称</th><th>分类</th><th>文件类型</th><th>文件大小</th><th>Chunks</th><th>解析状态</th><th>上传时间</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="document in pagedDocuments" :key="document.id"><td><div class="admin-document-cell"><span class="admin-document-icon" :class="`is-${document.format}`"><b>{{ documentFormatLabel(document) }}</b><component :is="documentIcon(document)" :size="15" /></span><div><strong>{{ document.name }}</strong><small>{{ document.uploader }}</small></div></div></td><td><span class="admin-document-category">{{ document.category }}</span></td><td>{{ documentFormatLabel(document) }}</td><td>{{ document.size }}</td><td>{{ document.chunks || '待生成' }}</td><td><span class="kb-document-status" :class="`is-${document.status}`"><CheckCircle2 v-if="document.status === 'ready'" :size="13" /><span v-else-if="['parsing', 'vectorizing'].includes(document.status)" class="kb-status-spinner"></span><XCircle v-else-if="document.status === 'failed'" :size="13" /><span v-else class="kb-status-dot"></span>{{ statusLabels[document.status] }}</span></td><td>{{ document.uploadedAt }}</td><td>{{ document.updatedAt }}</td><td><div class="kb-document-actions"><button type="button" class="admin-ghost-button" @click="viewDocument(document)"><BookOpen :size="14" /> 网页查看</button><button type="button" class="admin-ghost-button" @click="reparseDocument(document)">重新解析</button><button type="button" class="admin-ghost-button admin-knowledge-download-button" @click="downloadDocument(document)"><Download :size="14" /> 下载</button><button type="button" class="admin-icon-button admin-danger-icon admin-knowledge-delete-button" title="删除文档" :aria-label="`删除 ${document.name}`" @click="requestDocumentDeletion(document)"><Trash2 :size="14" /></button></div></td></tr><tr v-if="!pagedDocuments.length"><td colspan="9" class="kb-documents-empty"><BookOpen :size="20" /><strong>未找到匹配的知识文档</strong><span>调整筛选条件，或上传新的网络工程资料。</span></td></tr></tbody></table></div>
      <footer class="kb-documents-pagination"><span>{{ rangeText }}</span><div><label>每页<LogFilterDropdown class="admin-knowledge-filter kb-page-size-select" v-model="pageSize" label="每页条数" :options="pageSizeOptions" /></label><button type="button" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">上一页</button><button v-for="page in visiblePages" :key="page" type="button" :class="{ active: page === currentPage }" @click="goToPage(page)">{{ page }}</button><button type="button" :disabled="currentPage === totalPages" @click="goToPage(currentPage + 1)">下一页</button><form @submit.prevent="submitJump"><span>跳至</span><input v-model="jumpPage" type="number" min="1" :max="totalPages" /><span>页</span></form></div></footer>
    </section>

    <Transition name="admin-drawer"><div v-if="isDrawerOpen" class="admin-drawer-backdrop" @pointerdown.self="isDrawerOpen = false"><aside class="admin-review-drawer kb-upload-drawer" role="dialog" aria-modal="true" aria-label="新增知识文档"><header><div><span>DOCUMENT INGESTION</span><h3>新增文档</h3></div><button type="button" class="admin-close-button" @click="isDrawerOpen = false"><X :size="18" /></button></header><p>上传后将按当前 RAG 配置执行解析、切块与向量化。</p><label class="kb-upload-zone"><input type="file" multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.txt" @change="selectFiles($event.target.files)" /><UploadCloud :size="24" /><strong>拖入文件，或点击选择文件</strong><small>{{ selectedFileLabel }}</small></label><div class="kb-upload-category"><span>知识分类</span><LogFilterDropdown class="admin-knowledge-filter kb-upload-category-select" v-model="uploadCategory" label="知识分类" :options="uploadCategoryOptions" /></div><footer><button type="button" class="admin-ghost-button" @click="isDrawerOpen = false">取消</button><button type="button" class="admin-primary-button" @click="createDocuments">上传并开始处理</button></footer></aside></div></Transition>
    <Transition name="admin-modal"><div v-if="previewDocument" class="admin-modal-backdrop kb-preview-backdrop" @pointerdown.self="closePreview"><section class="admin-modal-card kb-preview-modal" role="dialog" aria-modal="true" :aria-label="'预览 ' + previewDocument.name"><header class="kb-preview-toolbar"><div class="kb-preview-file"><span class="admin-document-icon" :class="'is-' + previewDocument.format"><component :is="documentIcon(previewDocument)" :size="16" /></span><div><strong>{{ previewDocument.name }}</strong><small>{{ documentFormatLabel(previewDocument) }} · {{ previewDocument.size }}</small></div></div><div class="kb-preview-meta"><span>{{ statusLabels[previewDocument.status] }}</span><span>{{ previewDocument.chunks || 0 }} Chunks</span></div><div class="kb-preview-actions"><button type="button" class="admin-ghost-button" @click="downloadDocument(previewDocument)"><Download :size="14" /> 下载</button><button type="button" class="admin-close-button" aria-label="关闭预览" @click="closePreview"><X :size="18" /></button></div></header><div class="kb-preview-body"><div v-if="previewState === 'loading'" class="kb-preview-loading"><LoaderCircle :size="22" class="kb-preview-spinner" /><strong>正在加载文档...</strong></div><div v-else-if="previewState === 'error'" class="kb-preview-fallback"><FileText :size="30" /><strong>暂不支持在线预览该文件</strong><p>{{ previewError }}</p><button type="button" class="admin-ghost-button" @click="downloadDocument(previewDocument)"><Download :size="14" /> 下载文件</button></div><template v-else><div v-if="previewDocument.format === 'pdf' && previewObjectUrl" class="kb-preview-pdf"><div class="kb-preview-pdf-tools"><button type="button" class="admin-ghost-button" :disabled="previewPage === 1" @click="previewPage = Math.max(1, previewPage - 1)"><ChevronLeft :size="14" /> 上一页</button><span>{{ previewPage }} / {{ previewTotalPages }}</span><button type="button" class="admin-ghost-button" :disabled="previewPage === previewTotalPages" @click="previewPage = Math.min(previewTotalPages, previewPage + 1)">下一页 <ChevronRight :size="14" /></button><button type="button" class="admin-ghost-button" @click="previewZoom = Math.max(70, previewZoom - 10)"><ZoomOut :size="14" /></button><span>{{ previewZoom }}%</span><button type="button" class="admin-ghost-button" @click="previewZoom = Math.min(150, previewZoom + 10)"><ZoomIn :size="14" /></button></div><iframe :src="previewObjectUrl" title="PDF 文档预览" class="kb-preview-pdf-frame" sandbox="allow-same-origin"></iframe></div><div v-else class="kb-preview-text-wrap"><pre class="kb-preview-text">{{ previewText }}</pre></div></template></div></section></div></Transition>
    <Transition name="admin-modal"><div v-if="selectedDocument" class="admin-modal-backdrop" @pointerdown.self="selectedDocument = null"><section class="admin-modal-card kb-document-info" role="dialog" aria-modal="true"><header><div><span>DOCUMENT DETAILS</span><h3>{{ selectedDocument.name }}</h3></div><button type="button" class="admin-close-button" @click="selectedDocument = null"><X :size="18" /></button></header><dl><div><dt>知识分类</dt><dd>{{ selectedDocument.category }}</dd></div><div><dt>上传人</dt><dd>{{ selectedDocument.uploader }}</dd></div><div><dt>文件大小</dt><dd>{{ selectedDocument.size }}</dd></div><div><dt>知识块数量</dt><dd>{{ selectedDocument.chunks || '尚未生成' }}</dd></div><div><dt>当前状态</dt><dd>{{ statusLabels[selectedDocument.status] }}</dd></div><div><dt>最近更新时间</dt><dd>{{ selectedDocument.updatedAt }}</dd></div></dl></section></div></Transition>
    <Transition name="admin-modal"><div v-if="pendingDeleteDocument" class="admin-modal-backdrop" @pointerdown.self="pendingDeleteDocument = null"><section class="admin-modal-card admin-knowledge-delete-modal" role="alertdialog" aria-modal="true"><header><div><span class="admin-knowledge-delete-icon"><Trash2 :size="18" /></span><h3>删除知识文档</h3></div></header><p>确定删除“{{ pendingDeleteDocument.name }}”吗？<br />删除后，该文档对应的知识块和向量索引数据也会被移除，此操作不可撤销。</p><footer><button type="button" class="admin-ghost-button" @click="pendingDeleteDocument = null">取消</button><button type="button" class="admin-danger-button" @click="confirmDocumentDeletion">确认删除</button></footer></section></div></Transition>
    <Transition name="admin-toast"><p v-if="toast" class="admin-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.kb-documents-page { display:grid; gap:18px; max-width:1500px; margin:0 auto; padding-bottom:28px; }.kb-documents-intro { align-items:center; }.kb-documents-intro p { margin:7px 0 0; }.kb-documents-create { min-height:40px; }.kb-documents-card { overflow:hidden; }.kb-documents-toolbar { display:flex; align-items:center; justify-content:space-between; gap:14px; padding:14px 16px; border-bottom:1px solid #e5e7eb; }.kb-documents-search { display:flex; align-items:center; gap:8px; width:min(330px,100%); height:38px; padding:0 11px; border:1px solid #dfe3e8; border-radius:8px; background:#f8fafc; color:#64748b; }.kb-documents-search input { width:100%; border:0; outline:0; background:transparent; color:#1e293b; font-size:12px; }.kb-documents-filters { display:flex; align-items:center; gap:8px; }.kb-documents-table { min-width:1300px; }.kb-documents-table th,.kb-documents-table td { white-space:nowrap; }.kb-documents-table th { font-size:11px; }.kb-documents-table td { font-size:12px; }.kb-documents-table-wrap { overflow-x:auto; }.kb-document-status { display:inline-flex; align-items:center; gap:5px; min-height:25px; padding:0 8px; border-radius:999px; font-size:11px; font-weight:700; }.kb-document-status.is-ready { background:#ecfdf5; color:#047857; }.kb-document-status.is-pending { background:#f1f5f9; color:#475569; }.kb-document-status.is-parsing,.kb-document-status.is-vectorizing { background:#fffbeb; color:#b45309; }.kb-document-status.is-failed { background:#fef2f2; color:#b91c1c; }.kb-status-spinner { width:11px; height:11px; border:2px solid currentColor; border-right-color:transparent; border-radius:50%; animation:kb-spin 900ms linear infinite; }.kb-status-dot { width:6px; height:6px; border-radius:50%; background:currentColor; }.kb-document-actions { display:flex; align-items:center; gap:6px; }.kb-document-actions .admin-ghost-button { min-height:30px; padding:0 8px; font-size:11px; white-space:nowrap; }.kb-documents-empty { height:160px; color:#94a3b8 !important; text-align:center; }.kb-documents-empty>* { display:block; margin:0 auto 7px; }.kb-documents-empty strong { color:#475569; font-size:13px; }.kb-documents-empty span { font-size:12px; }.kb-documents-pagination { display:flex; align-items:center; justify-content:space-between; gap:14px; padding:12px 16px; border-top:1px solid #e5e7eb; color:#64748b; font-size:12px; }.kb-documents-pagination>div,.kb-documents-pagination label,.kb-documents-pagination form { display:flex; align-items:center; gap:6px; }.kb-documents-pagination select,.kb-documents-pagination form input { height:29px; border:1px solid #dfe3e8; border-radius:6px; background:#fff; color:#475569; font-size:11px; outline:0; }.kb-documents-pagination select { padding:0 4px; }.kb-documents-pagination form input { width:36px; padding:0 5px; }.kb-documents-pagination button { min-width:29px; height:29px; padding:0 7px; border:1px solid #e5e7eb; border-radius:6px; background:#fff; color:#475569; font-size:11px; font-weight:650; }.kb-documents-pagination button.active { border-color:#2563eb; background:#2563eb; color:#fff; }.kb-documents-pagination button:disabled { cursor:not-allowed; opacity:.45; }.kb-upload-drawer { display:flex; flex-direction:column; width:min(460px,100vw); min-height:100%; padding:24px; }.kb-upload-drawer p { margin:16px 0; color:#64748b; font-size:13px; line-height:1.6; }.kb-upload-zone { display:grid; place-items:center; gap:8px; min-height:185px; padding:20px; border:1px dashed #94a3b8; border-radius:10px; background:#f8fafc; color:#64748b; text-align:center; cursor:pointer; }.kb-upload-zone input { display:none; }.kb-upload-zone svg { color:#2563eb; }.kb-upload-zone strong { color:#334155; font-size:13px; }.kb-upload-zone small { font-size:11px; }.kb-upload-category { display:grid; gap:7px; margin-top:18px; color:#475569; font-size:12px; font-weight:700; }.kb-upload-category select { height:38px; padding:0 10px; border:1px solid #dfe3e8; border-radius:8px; background:#fff; color:#334155; outline:0; }.kb-upload-drawer footer { display:flex; justify-content:flex-end; gap:8px; margin-top:auto; padding-top:22px; }.kb-document-info { width:min(480px,100%); }.kb-document-info dl { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin:18px 0 0; }.kb-document-info dl div { padding:11px; border:1px solid #e5e7eb; border-radius:8px; background:#f8fafc; }.kb-document-info dt { color:#94a3b8; font-size:11px; }.kb-document-info dd { margin:4px 0 0; color:#334155; font-size:12px; font-weight:700; }.admin-knowledge-download-button { display:inline-flex; align-items:center; gap:5px; }@keyframes kb-spin { to { transform:rotate(360deg); } }:global(html[data-theme='dark']) .kb-documents-card,:global(html[data-theme='dark']) .kb-upload-drawer,:global(html[data-theme='dark']) .kb-document-info { border-color:#27272a; background:#18181b; }:global(html[data-theme='dark']) .kb-documents-toolbar,:global(html[data-theme='dark']) .kb-documents-pagination { border-color:#27272a; }:global(html[data-theme='dark']) .kb-documents-search,:global(html[data-theme='dark']) .kb-upload-zone,:global(html[data-theme='dark']) .kb-document-info dl div { border-color:#3f3f46; background:#27272a; }:global(html[data-theme='dark']) .kb-documents-search input,:global(html[data-theme='dark']) .kb-upload-category select { background:transparent; color:#f4f4f5; }:global(html[data-theme='dark']) .kb-upload-zone strong,:global(html[data-theme='dark']) .kb-document-info dd,:global(html[data-theme='dark']) .kb-documents-empty strong { color:#f4f4f5; }:global(html[data-theme='dark']) .kb-documents-pagination button,:global(html[data-theme='dark']) .kb-documents-pagination select,:global(html[data-theme='dark']) .kb-documents-pagination form input,:global(html[data-theme='dark']) .kb-upload-category select { border-color:#3f3f46; background:#18181b; color:#d4d4d8; }@media (max-width:1080px) { .kb-documents-toolbar { align-items:stretch; flex-direction:column; }.kb-documents-search { width:100%; }.kb-documents-filters { flex-wrap:wrap; }.kb-documents-pagination { align-items:flex-start; flex-direction:column; }.kb-documents-pagination>div { flex-wrap:wrap; } }@media (max-width:640px) { .kb-documents-intro { align-items:flex-start; flex-direction:column; }.kb-documents-create { width:100%; }.kb-documents-filters { display:grid; grid-template-columns:1fr; }.kb-documents-filters :deep(.admin-log-filter-trigger) { width:100%; justify-content:space-between; } }
</style>

<style scoped>
.kb-documents-actions .admin-knowledge-delete-button,
.kb-document-actions .admin-knowledge-delete-button { box-sizing:border-box; display:inline-flex; align-items:center; justify-content:center; width:auto; min-width:58px; min-height:30px; height:30px; padding:0 8px; gap:5px; border:1px solid #dfe3e8; border-radius:7px; background:#fff; color:#475569; font-size:11px; white-space:nowrap; }
.kb-document-actions .admin-knowledge-delete-button::after { content:'删除'; }
.kb-document-actions .admin-knowledge-delete-button:hover { border-color:#fecaca; background:#fff7f7; color:#b91c1c; }
.kb-preview-backdrop { z-index:120; background:rgba(15,23,42,.48); backdrop-filter:blur(4px); }
.kb-preview-modal { display:flex; flex-direction:column; width:min(84vw,1450px); height:min(86vh,900px); max-width:1450px; max-height:900px; overflow:hidden; border-radius:12px; background:#fff; box-shadow:0 24px 70px rgba(15,23,42,.24); }
.kb-preview-toolbar { display:flex; align-items:center; justify-content:space-between; gap:18px; flex:0 0 auto; min-height:70px; padding:12px 18px; border-bottom:1px solid #e5e7eb; background:#fff; }
.kb-preview-file,.kb-preview-meta,.kb-preview-actions { display:flex; align-items:center; gap:9px; }
.kb-preview-file { min-width:0; flex:1 1 auto; }
.kb-preview-file>div { display:grid; gap:3px; min-width:0; }
.kb-preview-file strong { overflow:hidden; color:#1e293b; font-size:14px; text-overflow:ellipsis; white-space:nowrap; }
.kb-preview-file small { color:#64748b; font-size:11px; }
.kb-preview-meta { flex:0 0 auto; color:#64748b; font-size:11px; }
.kb-preview-meta span:first-child { color:#047857; font-weight:700; }
.kb-preview-actions { flex:0 0 auto; }
.kb-preview-actions .admin-ghost-button { min-height:30px; }
.kb-preview-body { min-height:0; flex:1 1 auto; overflow:hidden; background:#f8fafc; }
.kb-preview-loading,.kb-preview-fallback { display:grid; place-items:center; align-content:center; gap:10px; height:100%; color:#64748b; text-align:center; }
.kb-preview-loading strong,.kb-preview-fallback strong { color:#334155; font-size:14px; }
.kb-preview-fallback p { max-width:440px; margin:0; color:#64748b; font-size:12px; }
.kb-preview-fallback svg { color:#2563eb; }
.kb-preview-spinner { color:#2563eb; animation:kb-preview-spin 900ms linear infinite; }
.kb-preview-text-wrap { box-sizing:border-box; height:100%; overflow:auto; padding:32px 48px 44px; background:#fff; }
.kb-preview-text { max-width:980px; margin:0 auto; color:#334155; font:14px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif; white-space:pre-wrap; overflow-wrap:anywhere; }
.kb-preview-pdf { display:flex; flex-direction:column; height:100%; }
.kb-preview-pdf-tools { display:flex; align-items:center; justify-content:center; gap:8px; flex:0 0 auto; min-height:45px; border-bottom:1px solid #e5e7eb; background:#fff; color:#64748b; font-size:11px; }
.kb-preview-pdf-tools .admin-ghost-button { min-height:29px; padding:0 8px; }
.kb-preview-pdf-frame { width:100%; min-height:0; flex:1 1 auto; border:0; background:#fff; transform:scale(1); transform-origin:top center; }
:global(html[data-theme='dark']) .kb-document-actions .admin-knowledge-delete-button { border-color:#3f3f46; background:#27272a; color:#d4d4d8; }
:global(html[data-theme='dark']) .kb-document-actions .admin-knowledge-delete-button:hover { border-color:rgba(248,113,113,.4); background:rgba(127,29,29,.22); color:#fca5a5; }
:global(html[data-theme='dark']) .kb-preview-modal,:global(html[data-theme='dark']) .kb-preview-toolbar,:global(html[data-theme='dark']) .kb-preview-pdf-tools { border-color:#27272a; background:#18181b; }
:global(html[data-theme='dark']) .kb-preview-body { background:#111113; }
:global(html[data-theme='dark']) .kb-preview-text { color:#e4e4e7; }
:global(html[data-theme='dark']) .kb-preview-file strong,:global(html[data-theme='dark']) .kb-preview-loading strong,:global(html[data-theme='dark']) .kb-preview-fallback strong { color:#f4f4f5; }
@keyframes kb-preview-spin { to { transform:rotate(360deg); } }
@media (max-width:800px) { .kb-preview-modal { width:94vw; height:88vh; }.kb-preview-toolbar { align-items:flex-start; flex-wrap:wrap; gap:10px; }.kb-preview-meta { order:3; width:100%; }.kb-preview-actions { margin-left:auto; }.kb-preview-text-wrap { padding:24px 20px 30px; }.kb-preview-pdf-tools { flex-wrap:wrap; padding:7px; } }
</style>

<style scoped>
.kb-upload-category-select.admin-knowledge-filter { width:100%; }
.kb-upload-category-select :deep(.admin-log-filter-trigger) { width:100%; min-height:38px; }
.kb-page-size-select.admin-knowledge-filter { min-width:66px; }
.kb-page-size-select :deep(.admin-log-filter-trigger) { min-height:29px; padding:0 7px; border-radius:6px; font-size:11px; }
.kb-page-size-select :deep(.admin-log-filter-menu) { min-width:100%; }
</style>
