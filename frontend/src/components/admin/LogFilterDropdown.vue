<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, ChevronDown } from '@lucide/vue'

const props = defineProps({
  label: { type: String, required: true },
  modelValue: { type: [String, Number], required: true },
  options: { type: Array, required: true },
  leadingIcon: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])
const root = ref(null)
const isOpen = ref(false)
const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue) || props.options[0])

function close() {
  isOpen.value = false
}

function choose(value) {
  emit('update:modelValue', value)
  close()
}

function handleDocumentPointerDown(event) {
  if (!root.value?.contains(event.target)) close()
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <div ref="root" class="admin-log-filter-dropdown" :class="{ 'is-open': isOpen }">
    <button type="button" class="admin-log-filter-trigger" :aria-label="label" aria-haspopup="listbox" :aria-expanded="isOpen" @click="isOpen = !isOpen" @keydown.arrow-down.prevent="isOpen = true">
      <component v-if="leadingIcon" :is="leadingIcon" :size="15" aria-hidden="true" />
      <span>{{ label }}</span><strong>{{ selectedOption?.label }}</strong><ChevronDown :size="16" :class="{ 'is-open': isOpen }" />
    </button>
    <Transition name="admin-log-filter-menu">
      <div v-if="isOpen" class="admin-log-filter-menu" role="listbox" :aria-label="label">
        <button v-for="option in options" :key="option.value" type="button" role="option" :aria-selected="option.value === modelValue" :class="{ selected: option.value === modelValue }" @click="choose(option.value)">
          <span class="admin-log-filter-option-label"><i v-if="option.tone" class="admin-log-filter-dot" :class="`is-${option.tone}`" aria-hidden="true" />{{ option.label }}</span><Check v-if="option.value === modelValue" :size="16" />
        </button>
      </div>
    </Transition>
  </div>
</template>
