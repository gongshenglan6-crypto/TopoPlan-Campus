<script setup>
import { Check, ChevronDown } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])
const root = ref(null)
const open = ref(false)
const activeIndex = ref(-1)

const normalizedOptions = computed(() => props.options.map((option) => {
  if (option && typeof option === 'object') {
    return { value: String(option.value ?? option.key ?? ''), label: String(option.label ?? option.name ?? option.value ?? '') }
  }
  return { value: String(option ?? ''), label: String(option ?? '') }
}))

const selectedOption = computed(() => normalizedOptions.value.find((option) => option.value === String(props.modelValue ?? '')))

function close() {
  open.value = false
  activeIndex.value = -1
}

function focusOption(index) {
  const options = root.value?.querySelectorAll('[role="option"]')
  if (!options?.length) return
  const nextIndex = (index + options.length) % options.length
  activeIndex.value = nextIndex
  nextTick(() => options[nextIndex]?.focus())
}

function toggle() {
  if (props.disabled) return
  if (open.value) return close()
  open.value = true
  activeIndex.value = Math.max(0, normalizedOptions.value.findIndex((option) => option.value === String(props.modelValue ?? '')))
}

function choose(option) {
  emit('update:modelValue', option.value)
  close()
}

function handleTriggerKeydown(event) {
  if (props.disabled) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!open.value) open.value = true
    focusOption(activeIndex.value < 0 ? 0 : activeIndex.value + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) open.value = true
    focusOption(activeIndex.value < 0 ? normalizedOptions.value.length - 1 : activeIndex.value - 1)
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    toggle()
    if (open.value) focusOption(activeIndex.value < 0 ? 0 : activeIndex.value)
  } else if (event.key === 'Escape') {
    close()
  }
}

function handleOptionKeydown(event, index) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    focusOption(index + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    focusOption(index - 1)
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    choose(normalizedOptions.value[index])
  } else if (event.key === 'Escape') {
    event.preventDefault()
    close()
    root.value?.querySelector('button[aria-haspopup="listbox"]')?.focus()
  }
}

function handleDocumentPointerdown(event) {
  if (root.value && !root.value.contains(event.target)) close()
}

onMounted(() => document.addEventListener('pointerdown', handleDocumentPointerdown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', handleDocumentPointerdown))
</script>

<template>
  <div ref="root" class="prompt-select" :class="{ 'is-open': open, 'is-disabled': disabled }">
    <button
      type="button"
      class="prompt-select-trigger"
      :disabled="disabled"
      :aria-label="ariaLabel || placeholder"
      aria-haspopup="listbox"
      :aria-expanded="open"
      @click="toggle"
      @keydown="handleTriggerKeydown"
    >
      <span :class="{ 'is-placeholder': !selectedOption }">{{ selectedOption?.label || placeholder }}</span>
      <ChevronDown :size="16" aria-hidden="true" />
    </button>

    <Transition name="prompt-select-menu">
      <div v-if="open" class="prompt-select-menu" role="listbox" :aria-label="ariaLabel || placeholder">
        <button
          v-for="(option, index) in normalizedOptions"
          :key="option.value"
          type="button"
          class="prompt-select-option"
          role="option"
          :aria-selected="option.value === String(modelValue ?? '')"
          :class="{ selected: option.value === String(modelValue ?? '') }"
          @click="choose(option)"
          @keydown="handleOptionKeydown($event, index)"
        >
          <span>{{ option.label }}</span>
          <Check v-if="option.value === String(modelValue ?? '')" :size="15" aria-hidden="true" />
        </button>
        <span v-if="!normalizedOptions.length" class="prompt-select-empty">暂无可选项</span>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.prompt-select { position: relative; width: 100%; min-width: 0; }
.prompt-select-trigger { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 40px; padding: 0 12px; border: 1px solid #d9e1ea; border-radius: 10px; outline: 0; background: #fff; color: #1f2937; font: inherit; font-size: 14px; font-style: normal; font-weight: 500; line-height: 1.2; text-align: left; cursor: pointer; transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease; }
.prompt-select-trigger:hover { border-color: #aebdce; background: #fff; }
.prompt-select-trigger:focus-visible,.prompt-select.is-open .prompt-select-trigger { border-color: #60a5fa; box-shadow: 0 0 0 3px rgba(37, 99, 235, .1); }
.prompt-select-trigger > svg { flex: 0 0 auto; margin-left: 10px; color: #64748b; transition: transform 160ms ease; }
.prompt-select.is-open .prompt-select-trigger > svg { transform: rotate(180deg); color: #2563eb; }
.prompt-select-trigger .is-placeholder { color: #64748b; }
.prompt-select-trigger:disabled { cursor: not-allowed; background: #f8fafc; color: #94a3b8; }
.prompt-select-trigger:disabled > svg { color: #94a3b8; }
.prompt-select-menu { position: absolute; z-index: 1400; top: calc(100% + 5px); right: 0; left: 0; max-height: 248px; padding: 6px; overflow-y: auto; border: 1px solid #dfe6ee; border-radius: 12px; background: #fff; box-shadow: 0 16px 34px rgba(15, 23, 42, .14), 0 3px 8px rgba(15, 23, 42, .06); }
.prompt-select-menu::-webkit-scrollbar { width: 6px; }
.prompt-select-menu::-webkit-scrollbar-track { background: #f1f5f9; border-radius: 99px; }
.prompt-select-menu::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 99px; }
.prompt-select-option { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 38px; gap: 10px; padding: 0 10px; border: 0; border-radius: 8px; background: transparent; color: #334155; font: inherit; font-size: 14px; font-style: normal; font-weight: 450; line-height: 1.25; text-align: left; cursor: pointer; transition: background-color 140ms ease, color 140ms ease; }
.prompt-select-option:hover,.prompt-select-option:focus-visible { outline: 0; background: #f1f7ff; color: #1d4ed8; }
.prompt-select-option.selected { background: #eaf3ff; color: #1d4ed8; font-weight: 650; }
.prompt-select-option > svg { flex: 0 0 auto; color: #2563eb; }
.prompt-select-empty { display: block; padding: 12px 10px; color: #64748b; font-size: 13px; text-align: center; }
.prompt-select-menu-enter-active,.prompt-select-menu-leave-active { transform-origin: top; transition: opacity 140ms ease, transform 140ms ease; }
.prompt-select-menu-enter-from,.prompt-select-menu-leave-to { opacity: 0; transform: translateY(-4px) scale(.985); }
:global(html[data-theme='dark']) .prompt-select-trigger { border-color: #3f4b5e; background: #111827; color: #f8fafc; }
:global(html[data-theme='dark']) .prompt-select-trigger:hover { border-color: #64748b; background: #111827; }
:global(html[data-theme='dark']) .prompt-select-trigger .is-placeholder { color: #94a3b8; }
:global(html[data-theme='dark']) .prompt-select-trigger:disabled { background: #172033; color: #64748b; }
:global(html[data-theme='dark']) .prompt-select-menu { border-color: #334155; background: #111827; box-shadow: 0 16px 34px rgba(0, 0, 0, .34); }
:global(html[data-theme='dark']) .prompt-select-menu::-webkit-scrollbar-track { background: #1e293b; }
:global(html[data-theme='dark']) .prompt-select-menu::-webkit-scrollbar-thumb { background: #475569; }
:global(html[data-theme='dark']) .prompt-select-option { color: #dbeafe; }
:global(html[data-theme='dark']) .prompt-select-option:hover,:global(html[data-theme='dark']) .prompt-select-option:focus-visible { background: rgba(59, 130, 246, .16); color: #bfdbfe; }
:global(html[data-theme='dark']) .prompt-select-option.selected { background: rgba(59, 130, 246, .2); color: #93c5fd; }
:global(html[data-theme='dark']) .prompt-select-empty { color: #94a3b8; }
</style>
