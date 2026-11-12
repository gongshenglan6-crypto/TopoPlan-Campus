<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  value: {
    type: Number,
    required: true
  },
  suffix: {
    type: String,
    default: ''
  },
  decimals: {
    type: Number,
    default: 0
  },
  duration: {
    type: Number,
    default: 760
  }
})

const displayedValue = ref(0)
let animationFrame = 0

const formattedValue = computed(() => {
  const formatter = new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals
  })
  return `${formatter.format(displayedValue.value)}${props.suffix}`
})

function animateTo(target) {
  cancelAnimationFrame(animationFrame)
  const startedAt = performance.now()
  const initial = displayedValue.value

  const tick = (now) => {
    const progress = Math.min(1, (now - startedAt) / props.duration)
    const eased = 1 - Math.pow(1 - progress, 4)
    displayedValue.value = initial + ((target - initial) * eased)
    if (progress < 1) animationFrame = requestAnimationFrame(tick)
  }

  animationFrame = requestAnimationFrame(tick)
}

watch(() => props.value, (value) => animateTo(value))

onMounted(() => animateTo(props.value))
onBeforeUnmount(() => cancelAnimationFrame(animationFrame))
</script>

<template>
  <span class="admin-number-ticker">{{ formattedValue }}</span>
</template>
