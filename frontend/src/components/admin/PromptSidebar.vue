<script setup>
import { FileText, Plus, Search, Trash2 } from '@lucide/vue'
import { computed, ref } from 'vue'

const props = defineProps({
  templates: { type: Array, required: true },
  activeTemplateId: { type: String, default: '' }
})

const emit = defineEmits(['select', 'create', 'delete'])
const query = ref('')
const category = ref('全部')
const categories = ['全部', '基础', '规划', '项目', '检查', '输出']
const groups = [
  { key: '基础', label: '基础能力' },
  { key: '规划', label: '规划阶段' },
  { key: '项目', label: '项目阶段' },
  { key: '检查', label: '检查阶段' },
  { key: '输出', label: '输出阶段' }
]

const visibleGroups = computed(() => groups.map((group) => ({
  ...group,
  templates: props.templates.filter((template) => {
    const keyword = query.value.trim().toLowerCase()
    const matchesKeyword = !keyword || [template.title, template.name, template.detail, template.taskType]
      .some((value) => String(value || '').toLowerCase().includes(keyword))
    return matchesKeyword && (category.value === '全部' || template.group === category.value) && template.group === group.key
  })
})).filter((group) => group.templates.length))
</script>

<template>
  <aside class="admin-card admin-prompt-sidebar" aria-label="Prompt 模板列表">
    <header class="admin-prompt-sidebar-header">
      <div><span class="admin-prompt-sidebar-kicker">WORKFLOWS</span><h3>Prompt 模板</h3></div>
      <button type="button" class="admin-prompt-create-button" title="新建 Prompt" @click="emit('create')"><Plus :size="15" /><span>新建</span></button>
    </header>

    <div class="prompt-sidebar-tools">
      <label class="prompt-sidebar-search"><Search :size="14" /><input v-model="query" placeholder="搜索 Prompt" aria-label="搜索 Prompt" /></label>
      <div class="prompt-sidebar-filters"><button v-for="item in categories" :key="item" type="button" :class="{ active: category === item }" @click="category = item">{{ item }}</button></div>
    </div>

    <div class="admin-prompt-sidebar-list">
      <template v-for="group in visibleGroups" :key="group.key">
        <p class="prompt-sidebar-group-title">{{ group.label }}</p>
        <div v-for="template in group.templates" :key="template.id" class="admin-prompt-template-row" role="button" tabindex="0"
          :class="{ active: activeTemplateId === template.id, 'is-draft': String(template.version).toLowerCase() === 'draft' }"
          :aria-current="activeTemplateId === template.id ? 'page' : undefined" @click="emit('select', template.id)"
          @keydown.enter.prevent="emit('select', template.id)" @keydown.space.prevent="emit('select', template.id)">
          <span class="admin-prompt-template-icon" aria-hidden="true"><FileText :size="15" /></span>
          <span class="admin-prompt-template-copy"><strong>{{ template.title ?? template.name }}</strong><small>{{ template.detail }}</small><em :class="{ active: template.active }"><i />{{ template.active ? '已启用' : '已停用' }}</em></span>
          <span class="admin-prompt-template-actions"><span class="admin-prompt-template-version">v{{ String(template.version).replace(/^v/i, '') }}</span><button type="button" class="admin-prompt-template-delete" :aria-label="`删除${template.title ?? template.name}`" title="删除模板" @click.stop="emit('delete', template.id)"><Trash2 :size="14" /></button></span>
        </div>
      </template>
      <div v-if="!visibleGroups.length" class="admin-prompt-sidebar-empty"><FileText :size="18" /><span>没有匹配的 Prompt</span></div>
    </div>
  </aside>
</template>

<style scoped>
.prompt-sidebar-tools { display:grid; gap:8px; padding:10px 12px 8px; border-bottom:1px solid #f1f5f9; }
.prompt-sidebar-search { display:flex; align-items:center; gap:6px; height:30px; padding:0 8px; border:1px solid #e2e8f0; border-radius:6px; background:#fff; color:#94a3b8; }
.prompt-sidebar-search:focus-within { border-color:#93c5fd; box-shadow:0 0 0 3px rgba(37,99,235,.08); }.prompt-sidebar-search input { width:100%; min-width:0; border:0; outline:0; background:transparent; color:#334155; font-size:11px; }
.prompt-sidebar-filters { display:flex; gap:4px; overflow-x:auto; scrollbar-width:none; }.prompt-sidebar-filters button { flex:0 0 auto; min-height:23px; padding:0 6px; border:0; border-radius:4px; background:transparent; color:#94a3b8; font-size:10px; }.prompt-sidebar-filters button:hover,.prompt-sidebar-filters button.active { background:#eff6ff; color:#2563eb; }
.prompt-sidebar-group-title { margin:10px 7px 4px; color:#94a3b8; font-size:10px; font-weight:750; letter-spacing:.05em; }.prompt-sidebar-group-title:first-child { margin-top:2px; }
.admin-prompt-template-row { align-items:start; min-height:68px; }.admin-prompt-template-copy em { display:flex; align-items:center; gap:4px; margin-top:4px; color:#94a3b8; font-size:9px; font-style:normal; }.admin-prompt-template-copy em i { width:5px; height:5px; border:1px solid #94a3b8; border-radius:50%; }.admin-prompt-template-copy em.active { color:#15803d; }.admin-prompt-template-copy em.active i { border-color:#22c55e; background:#22c55e; }.admin-prompt-template-actions { display:inline-flex; align-items:center; align-self:start; gap:3px; }.admin-prompt-template-delete { display:grid; place-items:center; width:22px; height:22px; padding:0; border:0; border-radius:5px; background:transparent; color:#94a3b8; cursor:pointer; opacity:.68; }.admin-prompt-template-row:hover .admin-prompt-template-delete,.admin-prompt-template-row:focus-within .admin-prompt-template-delete,.admin-prompt-template-row.active .admin-prompt-template-delete { opacity:1; }.admin-prompt-template-delete:hover { background:#fee2e2; color:#dc2626; }
.admin-prompt-template-row.active { background-color:#f3f4f6; color:#111827; }.admin-prompt-template-row.active .admin-prompt-template-copy strong { color:#111827; font-weight:600; }.admin-prompt-template-row.active .admin-prompt-template-copy small { color:#4b5563; }.admin-prompt-template-row.is-draft .admin-prompt-template-version { padding:2px 6px; border-radius:4px; background:#e0f2fe; color:#0369a1; font-size:10px; font-weight:500; }
.prompt-sidebar-tools { gap:10px; padding:14px 14px 12px; }
.prompt-sidebar-search { height:40px; padding:0 12px; border-color:#d9e1ea; border-radius:10px; background:#f8fafc; color:#64748b; transition:border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease; }
.prompt-sidebar-search:hover { border-color:#aebdce; background:#fff; }
.prompt-sidebar-search:focus-within { border-color:#60a5fa; background:#fff; box-shadow:0 0 0 3px rgba(37,99,235,.1); }
.prompt-sidebar-search input { font-size:14px; font-style:normal; }
.prompt-sidebar-search input::placeholder { color:#64748b; opacity:1; }
.prompt-sidebar-filters { gap:6px; padding:1px 0 2px; }
.prompt-sidebar-filters button { min-height:29px; padding:0 11px; border:1px solid transparent; border-radius:999px; background:#f1f5f9; color:#475569; font-size:12px; font-style:normal; font-weight:550; transition:background-color 140ms ease, border-color 140ms ease, color 140ms ease; }
.prompt-sidebar-filters button:hover { border-color:#dbeafe; background:#eef6ff; color:#2563eb; }
.prompt-sidebar-filters button.active { border-color:#bfdbfe; background:#eaf3ff; color:#1d4ed8; font-weight:650; }
.prompt-sidebar-filters::-webkit-scrollbar { height:0; }
:global(html[data-theme='dark']) .prompt-sidebar-tools { border-color:rgba(148,163,184,.12); }:global(html[data-theme='dark']) .prompt-sidebar-search { border-color:#334155; background:#0f172a; }:global(html[data-theme='dark']) .prompt-sidebar-search input { color:#e2e8f0; }:global(html[data-theme='dark']) .admin-prompt-template-row.active { background:rgba(148,163,184,.14); }:global(html[data-theme='dark']) .admin-prompt-template-row.active .admin-prompt-template-copy strong { color:#f8fafc; }:global(html[data-theme='dark']) .admin-prompt-template-row.active .admin-prompt-template-copy small { color:#cbd5e1; }:global(html[data-theme='dark']) .admin-prompt-template-delete:hover { background:rgba(220,38,38,.18); color:#fca5a5; }
:global(html[data-theme='dark']) .prompt-sidebar-search:hover,:global(html[data-theme='dark']) .prompt-sidebar-search:focus-within { background:#111827; border-color:#64748b; }
:global(html[data-theme='dark']) .prompt-sidebar-search input::placeholder { color:#94a3b8; }
:global(html[data-theme='dark']) .prompt-sidebar-filters button { background:#1e293b; color:#cbd5e1; }
:global(html[data-theme='dark']) .prompt-sidebar-filters button:hover { border-color:#334155; background:rgba(59,130,246,.16); color:#bfdbfe; }
:global(html[data-theme='dark']) .prompt-sidebar-filters button.active { border-color:#3b82f6; background:rgba(59,130,246,.2); color:#93c5fd; }
</style>
