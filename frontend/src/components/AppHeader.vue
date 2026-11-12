<script setup>
import { Bell, ChevronRight, LogOut, MoonStar, Search, ShieldCheck, SunMedium, UserCircle } from '@lucide/vue'

defineProps({
  title: {
    type: String,
    required: true
  },
  activeRole: {
    type: String,
    required: true
  },
  activePage: {
    type: String,
    default: 'dashboard'
  },
  theme: {
    type: String,
    default: 'light'
  },
  saveState: {
    type: String,
    default: '已保存'
  }
})

defineEmits(['changeRole', 'logout', 'toggle-theme'])
</script>

<template>
  <header class="app-header" :class="{ 'admin-header': activeRole === 'admin' }">
    <div class="header-title-block">
      <div v-if="activeRole === 'admin'" class="admin-breadcrumb" aria-label="面包屑">
        <span>管理中心</span>
        <ChevronRight :size="14" />
        <strong>{{ title }}</strong>
      </div>
      <span v-else class="field-label">用户端</span>
      <h1>{{ activeRole === 'admin' ? '网络拓扑工程规划辅助系统' : title }}</h1>
    </div>

    <div class="header-actions">
      <label class="search-box">
        <Search :size="16" />
        <input type="search" :placeholder="activeRole === 'admin' ? '搜索用户、项目、日志' : '搜索项目、设备、资料'" />
      </label>

      <div v-if="activeRole === 'user'" class="segmented" role="tablist" aria-label="端切换">
        <button
          type="button"
          :class="{ active: activeRole === 'user' }"
          @click="$emit('changeRole', 'user')"
        >
          <UserCircle :size="16" />
          用户端
        </button>
        <button
          type="button"
          :class="{ active: activeRole === 'admin' }"
          @click="$emit('changeRole', 'admin')"
        >
          <ShieldCheck :size="16" />
          管理端
        </button>
      </div>

      <span v-if="activeRole === 'user'" class="save-state">{{ saveState }}</span>

      <button
        v-if="activeRole === 'admin'"
        class="admin-theme-toggle"
        type="button"
        :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
        @click="$emit('toggle-theme')"
      >
        <SunMedium v-if="theme === 'dark'" :size="17" />
        <MoonStar v-else :size="17" />
      </button>

      <button class="icon-button" type="button" aria-label="通知">
        <Bell :size="18" />
      </button>

      <span v-if="activeRole === 'admin'" class="admin-profile-chip" aria-label="当前登录身份：系统管理员">
        <span>管</span>
        <strong>系统管理员</strong>
      </span>

      <button class="icon-button" type="button" aria-label="退出登录" @click="$emit('logout')">
        <LogOut :size="18" />
      </button>
    </div>
  </header>
</template>
