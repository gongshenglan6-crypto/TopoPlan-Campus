<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  BadgeHelp,
  ChevronRight,
  CircleGauge,
  LogOut,
  Palette,
  Repeat2,
  Settings,
  SlidersHorizontal,
  Sparkles,
  UserRound
} from '@lucide/vue'
import AccountLogoutConfirmModal from './profile-menu/AccountLogoutConfirmModal.vue'
import AccountSecurityModal from './profile-menu/AccountSecurityModal.vue'
import AiPreferencesModal from './profile-menu/AiPreferencesModal.vue'
import HelpGuideModal from './profile-menu/HelpGuideModal.vue'
import SubscriptionModal from './profile-menu/SubscriptionModal.vue'
import ThemeCascadingSubmenu from './profile-menu/ThemeCascadingSubmenu.vue'
import UsageLimitsModal from './profile-menu/UsageLimitsModal.vue'
import { getCurrentUser } from '../api/auth'

const props = defineProps({
  activeRole: {
    type: String,
    required: true
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'show-tooltip',
  'hide-tooltip',
  'logout',
  'switch-account'
])

const profileMenuOpen = ref(false)
const accountPanelOpen = ref(false)
const logoutConfirmOpen = ref(false)
const activeModal = ref(null)
const activeSubMenu = ref(null)
const submenuAnchorTop = ref(0)
const themePreference = ref('system')
const toastMessage = ref('')
const profileRoot = ref(null)
const profileTrigger = ref(null)
const profilePopover = ref(null)
const popoverAnchor = ref({ left: 16, bottom: 76 })
const profile = reactive({
  username: '',
  displayName: '用户',
  avatarUrl: '',
  email: '',
  accountId: ''
})
const aiPreferences = reactive({
  vendor: '华为',
  designStyle: '性能优先'
})
const settingsMenuSections = [
  {
    label: '用量与订阅',
    items: [
      { id: 'usage', label: '用量与限额', icon: CircleGauge, action: 'usage' },
      { id: 'subscription', label: '管理订阅 / 升级专业版', icon: Sparkles, action: 'subscription' }
    ]
  },
  {
    label: 'AI 与系统设置',
    items: [
      { id: 'preferences', label: 'AI 偏好设置', icon: SlidersHorizontal, action: 'preferences' },
      { id: 'theme', label: '外观主题', icon: Palette, submenu: 'theme' }
    ]
  },
  {
    label: '支持与反馈',
    items: [
      { id: 'help', label: '帮助与使用指南', icon: BadgeHelp, action: 'help' }
    ]
  }
]
const accountMenuItems = [
  { id: 'switch-account', label: '切换账号', icon: Repeat2, action: 'switch-account' },
  { id: 'logout', label: '退出登录', icon: LogOut, action: 'logout', danger: true }
]
let toastTimer = 0
let colorSchemeQuery = null
let submenuCloseTimer = 0
const submenuCloseDelay = 220

const roleLabel = computed(() => (props.activeRole === 'user' ? '普通用户' : '系统管理员'))
const profileInitial = computed(() => profile.displayName.trim().charAt(0) || '游')
const profilePopoverStyle = computed(() => ({
  left: `${popoverAnchor.value.left}px`,
  bottom: `${popoverAnchor.value.bottom}px`
}))
const themeLabel = computed(() => ({ light: '浅色', dark: '深色', system: '跟随系统' }[themePreference.value]))

async function toggleProfileMenu() {
  profileMenuOpen.value = !profileMenuOpen.value
  accountPanelOpen.value = false
  emit('hide-tooltip')
  if (!profileMenuOpen.value) {
    closeActiveSubmenu()
    return
  }
  await nextTick()
  updatePopoverAnchor()
}

function toggleAccountPanel() {
  accountPanelOpen.value = !accountPanelOpen.value
  profileMenuOpen.value = false
  closeActiveSubmenu()
  emit('hide-tooltip')
}

function updatePopoverAnchor() {
  const trigger = profileTrigger.value
  if (!trigger) return

  const rect = trigger.getBoundingClientRect()
  const left = props.collapsed
    ? Math.min(window.innerWidth - 322, rect.right + 12)
    : Math.max(12, rect.left)

  popoverAnchor.value = {
    left: Math.max(12, left),
    bottom: Math.max(16, window.innerHeight - rect.top + 10)
  }
}

function closeProfileMenu() {
  profileMenuOpen.value = false
  accountPanelOpen.value = false
  closeActiveSubmenu()
}

function cancelSubmenuClose() {
  window.clearTimeout(submenuCloseTimer)
  submenuCloseTimer = 0
}

function openSubmenuOnHover(submenu, event) {
  cancelSubmenuClose()
  const menuRect = profilePopover.value?.getBoundingClientRect()
  const itemRect = event.currentTarget?.getBoundingClientRect?.()
  if (menuRect && itemRect) submenuAnchorTop.value = Math.max(0, itemRect.top - menuRect.top)
  activeSubMenu.value = submenu
}

function scheduleSubmenuClose() {
  cancelSubmenuClose()
  submenuCloseTimer = window.setTimeout(() => {
    activeSubMenu.value = null
    submenuCloseTimer = 0
  }, submenuCloseDelay)
}

function closeActiveSubmenu() {
  cancelSubmenuClose()
  activeSubMenu.value = null
  submenuAnchorTop.value = 0
}

function runDirectMenuAction(action) {
  if (action === 'switch-account') {
    switchAccount()
    return
  }
  if (action === 'logout') logout()
  else openMenuAction(action)
}

function openMenuAction(action) {
  closeProfileMenu()
  activeModal.value = action
}

function closeActiveModal() {
  activeModal.value = null
}

function saveAccountProfile(nextProfile) {
  profile.username = nextProfile.username
  profile.displayName = nextProfile.displayName
  profile.email = nextProfile.email
  profile.avatarUrl = nextProfile.avatarUrl ?? profile.avatarUrl
  showToast('账号信息已更新')
}

async function loadProfile() {
  try {
    const user = await getCurrentUser()
    profile.username = user.username
    profile.displayName = user.displayName
    profile.email = user.email || ''
    profile.avatarUrl = user.avatar || ''
    profile.accountId = user.id ? `#${user.id}` : ''
  } catch {
    // The surrounding application will return to the login screen if the session is no longer valid.
  }
}

function handlePasswordUpdated() {
  showToast('密码已修改')
}

function requestLogout() {
  logoutConfirmOpen.value = true
}

function cancelLogout() {
  logoutConfirmOpen.value = false
}

function confirmLogout() {
  logoutConfirmOpen.value = false
  logout()
}

function saveAiPreferences(nextPreferences) {
  aiPreferences.vendor = nextPreferences.vendor
  aiPreferences.designStyle = nextPreferences.designStyle
  try {
    window.localStorage.setItem('topoplan.ai-preferences', JSON.stringify(nextPreferences))
  } catch {
    // The visible preference state still works when local storage is unavailable.
  }
  showToast('AI 偏好已保存')
}

function resolveTheme(preference) {
  if (preference !== 'system') return preference
  return colorSchemeQuery?.matches ? 'dark' : 'light'
}

function applyTheme(preference, persist = true) {
  themePreference.value = preference
  document.documentElement.dataset.theme = resolveTheme(preference)
  window.dispatchEvent(new CustomEvent('topoplan-theme-change', { detail: preference }))
  if (persist) {
    try {
      window.localStorage.setItem('topoplan.theme-preference', preference)
    } catch {
      // Theme switching remains available for the current page session.
    }
  }
}

function selectTheme(preference) {
  applyTheme(preference)
  showToast(`已切换为${themeLabel.value}主题`)
}

function handleSystemThemeChange() {
  if (themePreference.value === 'system') applyTheme('system', false)
}

function showToast(message) {
  toastMessage.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastMessage.value = '' }, 2200)
}

function openRequestedProfileModal(event) {
  const modal = event?.detail?.modal
  if (!['usage', 'subscription'].includes(modal)) return
  closeProfileMenu()
  activeModal.value = modal
}

function switchAccount() {
  closeProfileMenu()
  emit('switch-account')
}

function logout() {
  logoutConfirmOpen.value = false
  closeProfileMenu()
  emit('logout')
}

function showTooltip(event, label, collapsedOnly = false) {
  if (collapsedOnly && !props.collapsed) return
  emit('show-tooltip', event, label, collapsedOnly)
}

function closeSettingsWhenOutside(event) {
  if (logoutConfirmOpen.value) return
  if (accountPanelOpen.value) return
  const target = event.target
  if (!profileRoot.value?.contains(target) && !profilePopover.value?.contains(target)) closeProfileMenu()
}

function closeSettingsOnEscape(event) {
  if (event.key !== 'Escape') return
  if (accountPanelOpen.value) {
    accountPanelOpen.value = false
    return
  }
  if (logoutConfirmOpen.value) {
    cancelLogout()
    return
  }
  closeProfileMenu()
}

function restorePreferences() {
  try {
    const savedPreferences = JSON.parse(window.localStorage.getItem('topoplan.ai-preferences') || 'null')
    if (savedPreferences?.vendor && savedPreferences?.designStyle) {
      aiPreferences.vendor = savedPreferences.vendor
      aiPreferences.designStyle = savedPreferences.designStyle
    }
    const savedTheme = window.localStorage.getItem('topoplan.theme-preference')
    if (['light', 'dark', 'system'].includes(savedTheme)) themePreference.value = savedTheme
  } catch {
    // Defaults are used when cached prototype preferences cannot be read.
  }
  applyTheme(themePreference.value, false)
}

onMounted(() => {
  colorSchemeQuery = window.matchMedia?.('(prefers-color-scheme: dark)') ?? null
  colorSchemeQuery?.addEventListener('change', handleSystemThemeChange)
  restorePreferences()
  loadProfile()
  document.addEventListener('pointerdown', closeSettingsWhenOutside)
  document.addEventListener('keydown', closeSettingsOnEscape)
  window.addEventListener('resize', updatePopoverAnchor)
  window.addEventListener('scroll', updatePopoverAnchor, true)
  window.addEventListener('topoplan-open-profile-modal', openRequestedProfileModal)
})

onBeforeUnmount(() => {
  colorSchemeQuery?.removeEventListener('change', handleSystemThemeChange)
  document.removeEventListener('pointerdown', closeSettingsWhenOutside)
  document.removeEventListener('keydown', closeSettingsOnEscape)
  window.removeEventListener('resize', updatePopoverAnchor)
  window.removeEventListener('scroll', updatePopoverAnchor, true)
  window.removeEventListener('topoplan-open-profile-modal', openRequestedProfileModal)
  window.clearTimeout(toastTimer)
  cancelSubmenuClose()
})
</script>

<template>
  <div ref="profileRoot" class="sidebar-profile">
    <button
      ref="profileTrigger"
      class="sidebar-profile-summary"
      type="button"
      aria-label="打开账号管理中心"
      :aria-expanded="accountPanelOpen"
      @mouseenter="showTooltip($event, '账号管理中心', true)"
      @mouseleave="$emit('hide-tooltip')"
      @focus="showTooltip($event, '账号管理中心', true)"
      @blur="$emit('hide-tooltip')"
      @click.stop="toggleAccountPanel"
    >
      <span v-if="profile.avatarUrl" class="avatar-badge avatar-image"><img :src="profile.avatarUrl" alt="用户头像" /></span>
      <span v-else-if="activeRole === 'user'" class="avatar-badge">{{ profileInitial }}</span>
      <span v-else class="avatar-badge admin"><UserRound :size="17" /></span>
      <span class="sidebar-profile-copy">
        <strong>{{ profile.displayName }}</strong>
        <span>{{ roleLabel }}</span>
      </span>
    </button>

    <div class="sidebar-profile-controls" aria-label="个人控制">
      <button
        class="profile-control"
        type="button"
        aria-label="打开设置菜单"
        :aria-expanded="profileMenuOpen"
        @mouseenter="showTooltip($event, '打开设置菜单')"
        @mouseleave="$emit('hide-tooltip')"
        @focus="showTooltip($event, '打开设置菜单')"
        @blur="$emit('hide-tooltip')"
        @click.stop="toggleProfileMenu"
      ><Settings :size="16" /></button>
    </div>

    <AccountSecurityModal
      :open="accountPanelOpen"
      :profile="profile"
      :active-role="activeRole"
      @close="accountPanelOpen = false"
      @update-profile="saveAccountProfile"
      @password-updated="handlePasswordUpdated"
    />

    <Teleport to="body">
      <Transition name="profile-popover">
        <section v-if="profileMenuOpen" ref="profilePopover" class="profile-popover profile-actions-menu" :style="profilePopoverStyle" aria-label="用户偏好菜单">
          <template v-for="(section, sectionIndex) in settingsMenuSections" :key="section.label">
            <section class="profile-menu-group" :aria-label="section.label">
              <template v-for="item in section.items" :key="item.id">
                <button
                  v-if="item.submenu"
                  class="profile-menu-item"
                  :class="{ 'is-submenu-active': activeSubMenu === item.submenu }"
                  type="button"
                  :aria-expanded="activeSubMenu === item.submenu"
                  @mouseenter="openSubmenuOnHover(item.submenu, $event)"
                  @mouseleave="scheduleSubmenuClose"
                  @focus="openSubmenuOnHover(item.submenu, $event)"
                >
                  <component :is="item.icon" :size="18" />
                  <span>{{ item.label }}</span>
                  <ChevronRight :size="16" />
                </button>
                <button v-else class="profile-menu-item" type="button" @click="runDirectMenuAction(item.action)">
                  <component :is="item.icon" :size="18" />
                  <span>{{ item.label }}</span>
                </button>
              </template>
            </section>
            <div v-if="sectionIndex < settingsMenuSections.length - 1" class="profile-popover-divider"></div>
          </template>

          <div class="profile-popover-divider"></div>
          <section class="profile-menu-group profile-account-actions" aria-label="账号操作">
            <button v-for="item in accountMenuItems" :key="item.id" class="profile-menu-item" :class="{ 'is-danger': item.danger }" type="button" @click="runDirectMenuAction(item.action)">
              <component :is="item.icon" :size="18" />
              <span>{{ item.label }}</span>
            </button>
          </section>

          <Transition name="profile-submenu">
            <ThemeCascadingSubmenu
              v-if="activeSubMenu === 'theme'"
              :anchor-top="submenuAnchorTop"
              :theme-preference="themePreference"
              @pointer-enter="cancelSubmenuClose"
              @pointer-leave="scheduleSubmenuClose"
              @select-theme="selectTheme"
            />
          </Transition>
        </section>
      </Transition>
    </Teleport>

    <AccountLogoutConfirmModal :open="logoutConfirmOpen" @close="cancelLogout" @confirm="confirmLogout" />
    <UsageLimitsModal :open="activeModal === 'usage'" @close="closeActiveModal" @upgrade="activeModal = 'subscription'" />
    <SubscriptionModal :open="activeModal === 'subscription'" @close="closeActiveModal" />
    <AiPreferencesModal :open="activeModal === 'preferences'" :preferences="aiPreferences" @close="closeActiveModal" @save="saveAiPreferences" />
    <HelpGuideModal :open="activeModal === 'help'" @close="closeActiveModal" />

    <Teleport to="body">
      <Transition name="profile-toast"><p v-if="toastMessage" class="profile-menu-toast" role="status">{{ toastMessage }}</p></Transition>
    </Teleport>
  </div>
</template>
