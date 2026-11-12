<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Bell,
  ChevronDown,
  Copy,
  EllipsisVertical,
  FolderKanban,
  MessageSquarePlus,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  Settings,
  Trash2,
} from '@lucide/vue'
import HeightCollapse from './HeightCollapse.vue'
import SidebarProfile from './SidebarProfile.vue'
import { createFrameThrottle } from '../utils/frameThrottle'

const props = defineProps({
  activeRole: {
    type: String,
    required: true
  },
  activeKey: {
    type: String,
    required: true
  },
  activeChatId: {
    type: String,
    default: null
  },
  recentChats: {
    type: Array,
    required: true
  },
  collapsed: {
    type: Boolean,
    default: false
  },
  items: {
    type: Array,
    required: true
  },
  project: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['select', 'open-chat', 'toggle-sidebar', 'logout', 'switch-account', 'rename-chat', 'toggle-chat-pin', 'delete-chat'])

const activeChatMenu = ref(null)
const sidebarRoot = ref(null)
const renameInput = ref(null)
const renameDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const renamingChatId = ref(null)
const deletingChatId = ref(null)
const renameDraft = ref('')
const sidebarTooltip = ref(null)
const chatMenuPosition = ref({ top: 0, left: 0 })
const chatMenuNotice = ref('')
const scheduleTooltipHide = createFrameThrottle(hideSidebarTooltip)
let chatMenuNoticeTimer = null

const activeRecentChat = ref(props.activeChatId)
const isRecentExpanded = ref(true)
const expandedAdminGroups = ref({})

watch(
  () => props.activeKey,
  (key) => {
    if (key === 'new-chat' || key === 'chat-search' || key === 'projects') {
      activeRecentChat.value = null
      activeChatMenu.value = null
    }
  }
)

watch(
  () => props.activeChatId,
  (chatId) => {
    activeRecentChat.value = chatId
  }
)

watch(
  () => [props.activeRole, props.activeKey, props.items],
  ([role, activeKey]) => {
    if (role !== 'admin') return
    const parent = props.items.find((item) => item.children?.some((child) => child.key === activeKey))
    if (!parent) return
    expandedAdminGroups.value = { [parent.key]: true }
  },
  { immediate: true, deep: true }
)

function hasSubMenu(item) {
  return Array.isArray(item.children) && item.children.length > 0
}

function isSubMenuActive(item) {
  return hasSubMenu(item) && item.children.some((child) => child.key === props.activeKey)
}

function isSubMenuExpanded(item) {
  return Boolean(expandedAdminGroups.value[item.key])
}

function toggleAdminGroup(item) {
  expandedAdminGroups.value = isSubMenuExpanded(item) ? {} : { [item.key]: true }
}

function toggleChatMenu(event, chatId) {
  event.stopPropagation()

  if (activeChatMenu.value === chatId) {
    activeChatMenu.value = null
    return
  }

  const triggerRect = event.currentTarget.getBoundingClientRect()
  const menuWidth = 204
  const menuGap = 8
  chatMenuPosition.value = {
    top: Math.max(12, Math.min(triggerRect.top, window.innerHeight - 198)),
    left: Math.max(12, Math.min(triggerRect.right + menuGap, window.innerWidth - menuWidth - 12))
  }
  activeChatMenu.value = chatId
}

function toggleRecentChats() {
  isRecentExpanded.value = !isRecentExpanded.value
  if (!isRecentExpanded.value) activeChatMenu.value = null
}

function openChat(chatId) {
  if (!chatId) return
  activeRecentChat.value = chatId
  activeChatMenu.value = null
}

function startNewChat() {
  activeRecentChat.value = null
  activeChatMenu.value = null
  emit('select', 'new-chat')
}

function openProjects() {
  activeRecentChat.value = null
  activeChatMenu.value = null
  emit('select', 'projects')
}

function openChatSearch() {
  activeRecentChat.value = null
  activeChatMenu.value = null
  emit('select', 'chat-search')
}

function selectRecentChat(chatId) {
  openChat(chatId)
  emit('open-chat', chatId)
}

function deleteChat(chatId) {
  const remainingChats = props.recentChats.filter((chat) => chat.id !== chatId)
  emit('delete-chat', chatId)
  if (activeRecentChat.value === chatId) {
    activeRecentChat.value = remainingChats[0]?.id ?? null
    if (activeRecentChat.value) emit('open-chat', activeRecentChat.value)
    else emit('select', 'new-chat')
  }
  activeChatMenu.value = null
}

function pinChat(chatId) {
  emit('toggle-chat-pin', chatId)
  activeChatMenu.value = null
}

async function openRenameChat(chat) {
  renamingChatId.value = chat.id
  renameDraft.value = chat.title
  renameDialogOpen.value = true
  activeChatMenu.value = null
  await nextTick()
  renameInput.value?.focus()
}

function confirmRenameChat() {
  const nextTitle = renameDraft.value.trim()
  if (!nextTitle || !renamingChatId.value) return
  emit('rename-chat', { chatId: renamingChatId.value, title: nextTitle })
  renameDialogOpen.value = false
  renamingChatId.value = null
}

function requestDeleteChat(chatId) {
  deletingChatId.value = chatId
  deleteDialogOpen.value = true
  activeChatMenu.value = null
}

function showChatMenuNotice(message) {
  if (chatMenuNoticeTimer) window.clearTimeout(chatMenuNoticeTimer)
  chatMenuNotice.value = message
  chatMenuNoticeTimer = window.setTimeout(() => {
    chatMenuNotice.value = ''
    chatMenuNoticeTimer = null
  }, 3200)
}

async function shareChat(chat) {
  if (!chat.publicId) { showChatMenuNotice('该会话缺少公开地址，请刷新列表后重试'); activeChatMenu.value = null; return }
  const shareLink = `${window.location.origin}/c/${encodeURIComponent(chat.publicId)}`
  activeChatMenu.value = null

  try {
    await navigator.clipboard.writeText(shareLink)
    showChatMenuNotice('分享链接已复制')
  } catch {
    showChatMenuNotice('已生成分享链接，请从地址栏复制')
  }
}

function confirmDeleteChat() {
  if (deletingChatId.value) deleteChat(deletingChatId.value)
  deleteDialogOpen.value = false
  deletingChatId.value = null
}

function closeRenameDialog() {
  renameDialogOpen.value = false
  renamingChatId.value = null
}

function closeDeleteDialog() {
  deleteDialogOpen.value = false
  deletingChatId.value = null
}

function handleDocumentPointerDown(event) {
  const target = event.target
  if (!sidebarRoot.value?.contains(target) && !target.closest('.chat-row-menu')) {
    activeChatMenu.value = null
  }
}

function handleDocumentKeydown(event) {
  if (event.key !== 'Escape') return
  activeChatMenu.value = null
  closeRenameDialog()
  closeDeleteDialog()
}

function showSidebarTooltip(event, label, collapsedOnly = false) {
  if (collapsedOnly && !props.collapsed) return
  const rect = event.currentTarget.getBoundingClientRect()
  sidebarTooltip.value = {
    label,
    top: rect.top + (rect.height / 2),
    left: rect.right + 10
  }
}

function hideSidebarTooltip() {
  sidebarTooltip.value = null
}

function toggleSidebar() {
  activeChatMenu.value = null
  hideSidebarTooltip()
  emit('toggle-sidebar')
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
  window.addEventListener('resize', scheduleTooltipHide)
  window.addEventListener('scroll', scheduleTooltipHide, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
  window.removeEventListener('resize', scheduleTooltipHide)
  window.removeEventListener('scroll', scheduleTooltipHide, true)
  scheduleTooltipHide.cancel()
  if (chatMenuNoticeTimer) window.clearTimeout(chatMenuNoticeTimer)
})
</script>

<template>
  <aside ref="sidebarRoot" class="sidebar">
    <div class="brand sidebar-brand sidebar-brand-minimal">
      <strong>TOPOPLAN</strong>
      <button
        class="sidebar-collapse-button"
        type="button"
        :aria-label="collapsed ? '展开边栏' : '收起边栏'"
        @mouseenter="showSidebarTooltip($event, collapsed ? '展开边栏' : '收起边栏')"
        @mouseleave="hideSidebarTooltip"
        @focus="showSidebarTooltip($event, collapsed ? '展开边栏' : '收起边栏')"
        @blur="hideSidebarTooltip"
        @click="toggleSidebar"
      >
        <PanelLeftOpen v-if="collapsed" :size="18" />
        <PanelLeftClose v-else :size="18" />
      </button>
    </div>

    <div v-if="activeRole === 'user'" class="dialogue-shortcuts">
      <button
        class="side-create"
        type="button"
        :class="{ active: activeKey === 'new-chat' }"
        @mouseenter="showSidebarTooltip($event, '发起新对话', true)"
        @mouseleave="hideSidebarTooltip"
        @focus="showSidebarTooltip($event, '发起新对话', true)"
        @blur="hideSidebarTooltip"
        @click="startNewChat"
      >
        <MessageSquarePlus :size="17" />
        <span class="sidebar-label">发起新对话</span>
      </button>
      <button
        class="side-link"
        type="button"
        :class="{ active: activeKey === 'chat-search' }"
        @mouseenter="showSidebarTooltip($event, '搜索对话内容', true)"
        @mouseleave="hideSidebarTooltip"
        @focus="showSidebarTooltip($event, '搜索对话内容', true)"
        @blur="hideSidebarTooltip"
        @click="openChatSearch"
      >
        <Search :size="16" />
        <span class="sidebar-label">搜索对话内容</span>
      </button>
      <button
        class="side-link"
        type="button"
        :class="{ active: activeKey === 'projects' }"
        @mouseenter="showSidebarTooltip($event, '项目', true)"
        @mouseleave="hideSidebarTooltip"
        @focus="showSidebarTooltip($event, '项目', true)"
        @blur="hideSidebarTooltip"
        @click="openProjects"
      >
        <FolderKanban :size="16" />
        <span class="sidebar-label">项目</span>
      </button>
    </div>

    <div v-if="activeRole === 'user'" class="recent-chat-list" :class="{ 'is-collapsed': !isRecentExpanded }">
      <button
        class="recent-chat-header"
        type="button"
        :aria-expanded="isRecentExpanded"
        aria-controls="recent-chat-items"
        @click="toggleRecentChats"
      >
        <span class="field-label">最近</span>
        <ChevronDown class="recent-chat-chevron" :class="{ 'is-collapsed': !isRecentExpanded }" :size="15" />
      </button>
      <HeightCollapse>
        <div v-if="isRecentExpanded" id="recent-chat-items" class="recent-chat-items">
          <div
            v-for="chat in props.recentChats"
            :key="chat.id"
            class="recent-chat-row"
            :class="{ active: activeRecentChat === chat.id, 'menu-open': activeChatMenu === chat.id }"
          >
            <button
              class="recent-chat-title"
              type="button"
              @mouseenter="showSidebarTooltip($event, chat.title, true)"
              @mouseleave="hideSidebarTooltip"
              @focus="showSidebarTooltip($event, chat.title, true)"
              @blur="hideSidebarTooltip"
              @click="selectRecentChat(chat.id)"
            >
              <span class="recent-chat-name">{{ chat.title }}</span>
            </button>
            <button
              class="chat-more-button"
              type="button"
              :aria-label="`${chat.title} 更多操作`"
              :data-chat-id="chat.id"
              :aria-expanded="activeChatMenu === chat.id"
              :aria-controls="`chat-row-menu-${chat.id}`"
              @click="toggleChatMenu($event, chat.id)"
            >
              <EllipsisVertical :size="17" />
            </button>
          </div>
        </div>
      </HeightCollapse>
      <p v-if="chatMenuNotice" class="recent-chat-notice" role="status" aria-live="polite">{{ chatMenuNotice }}</p>
    </div>

    <nav v-if="activeRole !== 'user'" class="nav-list" aria-label="主导航">
      <template v-for="item in items" :key="item.key">
        <div v-if="hasSubMenu(item)" class="admin-nav-group" :class="{ active: isSubMenuActive(item), expanded: isSubMenuExpanded(item) }">
          <button
            class="nav-item admin-nav-group-trigger"
            :class="{ active: isSubMenuActive(item) }"
            type="button"
            :aria-expanded="isSubMenuExpanded(item)"
            :aria-controls="'admin-subnav-' + item.key"
            @mouseenter="showSidebarTooltip($event, item.label, true)"
            @mouseleave="hideSidebarTooltip"
            @focus="showSidebarTooltip($event, item.label, true)"
            @blur="hideSidebarTooltip"
            @click="toggleAdminGroup(item)"
          >
            <span class="menu-item-left">
              <span class="sidebar-nav-icon"><component :is="item.icon" :size="18" /></span>
              <span class="sidebar-label">{{ item.label }}</span>
            </span>
            <ChevronDown class="admin-nav-chevron" :class="{ 'is-expanded': isSubMenuExpanded(item) }" :size="15" />
          </button>
          <HeightCollapse>
            <div v-if="isSubMenuExpanded(item)" :id="'admin-subnav-' + item.key" class="admin-subnav-list">
              <button
                v-for="child in item.children"
                :key="child.key"
                class="admin-subnav-item"
                :class="{ active: activeKey === child.key }"
                type="button"
                @click="$emit('select', child.key)"
              >{{ child.label }}</button>
            </div>
          </HeightCollapse>
        </div>
        <button
          v-else
          class="nav-item"
          :class="{ active: activeKey === item.key }"
          type="button"
          @mouseenter="showSidebarTooltip($event, item.label, true)"
          @mouseleave="hideSidebarTooltip"
          @focus="showSidebarTooltip($event, item.label, true)"
          @blur="hideSidebarTooltip"
          @click="$emit('select', item.key)"
        >
          <span class="menu-item-left">
            <span class="sidebar-nav-icon"><component :is="item.icon" :size="18" /></span>
            <span class="sidebar-label">{{ item.label }}</span>
          </span>
        </button>
      </template>
    </nav>

    <SidebarProfile
      v-if="activeRole === 'user'"
      :active-role="activeRole"
      :collapsed="collapsed"
      @show-tooltip="showSidebarTooltip"
      @hide-tooltip="hideSidebarTooltip"
      @logout="$emit('logout')"
      @switch-account="$emit('switch-account')"
    />

    <div v-if="renameDialogOpen" class="chat-dialog-backdrop" @pointerdown.self="closeRenameDialog">
      <form class="chat-dialog" @submit.prevent="confirmRenameChat">
        <h2>重命名对话</h2>
        <p>修改后会同步保存到最近对话列表。</p>
        <input ref="renameInput" v-model="renameDraft" maxlength="36" aria-label="对话名称" />
        <div class="chat-dialog-actions">
          <button type="button" class="dialog-secondary" @click="closeRenameDialog">取消</button>
          <button type="submit" class="dialog-primary">保存</button>
        </div>
      </form>
    </div>

    <div v-if="deleteDialogOpen" class="chat-dialog-backdrop" @pointerdown.self="closeDeleteDialog">
      <section class="chat-dialog" role="alertdialog" aria-label="删除对话确认">
        <h2>删除此对话？</h2>
        <p>删除后，该对话将从最近列表中移除。</p>
        <div class="chat-dialog-actions">
          <button type="button" class="dialog-secondary" @click="closeDeleteDialog">取消</button>
          <button type="button" class="dialog-danger" @click="confirmDeleteChat">删除</button>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <Transition name="chat-row-menu">
        <div
          v-if="activeChatMenu"
          :id="`chat-row-menu-${activeChatMenu}`"
          class="chat-row-menu"
          :style="{ top: `${chatMenuPosition.top}px`, left: `${chatMenuPosition.left}px` }"
          role="menu"
          @pointerdown.stop
          @click.stop
        >
          <button type="button" role="menuitem" @click="shareChat(props.recentChats.find((chat) => chat.id === activeChatMenu))">
            <Copy :size="16" />
            分享
          </button>
          <button type="button" role="menuitem" @click="pinChat(activeChatMenu)">
            <Bell :size="16" />
            {{ props.recentChats.find((chat) => chat.id === activeChatMenu)?.pinned ? '取消固定' : '固定' }}
          </button>
          <button type="button" role="menuitem" @click="openRenameChat(props.recentChats.find((chat) => chat.id === activeChatMenu))">
            <Settings :size="16" />
            重命名
          </button>
          <button class="danger" type="button" role="menuitem" @click="requestDeleteChat(activeChatMenu)">
            <Trash2 :size="16" />
            删除
          </button>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="sidebar-tooltip">
        <span
          v-if="sidebarTooltip"
          class="sidebar-floating-tooltip"
          :style="{ top: `${sidebarTooltip.top}px`, left: `${sidebarTooltip.left}px` }"
          role="tooltip"
        >{{ sidebarTooltip.label }}</span>
      </Transition>
    </Teleport>
  </aside>
</template>
