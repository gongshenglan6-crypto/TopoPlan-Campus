<script setup>
const props = defineProps({
  anchorTop: {
    type: Number,
    default: 0
  },
  themePreference: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['pointer-enter', 'pointer-leave', 'select-theme'])

const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' }
]

function selectTheme(preference) {
  emit('select-theme', preference)
}
</script>

<template>
  <aside
    class="profile-theme-submenu"
    :style="{ '--profile-submenu-top': `${anchorTop}px` }"
    aria-label="外观主题"
    @mouseenter="emit('pointer-enter')"
    @mouseleave="emit('pointer-leave')"
    @click.stop
  >
    <p>外观主题</p>
    <div class="profile-theme-submenu-options" role="group" aria-label="选择外观主题">
      <button
        v-for="option in themeOptions"
        :key="option.value"
        type="button"
        :class="{ active: themePreference === option.value }"
        :aria-pressed="themePreference === option.value"
        @click.stop="selectTheme(option.value)"
      >{{ option.label }}</button>
    </div>
  </aside>
</template>
