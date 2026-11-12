<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { X } from '@lucide/vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  wide: {
    type: Boolean,
    default: false
  },
  cardClass: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['close'])

function handleKeydown(event) {
  if (props.open && event.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="profile-modal">
      <div v-if="open" class="profile-modal-backdrop" @pointerdown.self="$emit('close')">
        <section class="profile-modal-card" :class="[{ 'is-wide': wide }, cardClass]" role="dialog" aria-modal="true" :aria-label="title">
          <header class="profile-modal-header">
            <div>
              <h2>{{ title }}</h2>
              <p v-if="description">{{ description }}</p>
            </div>
            <button class="profile-dialog-close" type="button" aria-label="关闭弹窗" @click="$emit('close')">
              <X :size="18" />
            </button>
          </header>
          <slot />
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
