<script setup>
defineProps({
  items: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <div class="metric-strip">
    <article v-for="item in items" :key="item.label" class="metric-tile">
      <div>
        <span class="muted">{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
      <small>{{ item.trend }}</small>
    </article>
  </div>
</template>

