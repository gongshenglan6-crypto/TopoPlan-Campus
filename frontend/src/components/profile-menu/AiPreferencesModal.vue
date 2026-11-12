<script setup>
import { reactive, watch } from 'vue'
import { Save } from '@lucide/vue'
import ModalShell from './ModalShell.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  preferences: { type: Object, required: true }
})
const emit = defineEmits(['close', 'save'])
const draft = reactive({ vendor: '', designStyle: '' })

watch(() => props.open, (open) => {
  if (!open) return
  draft.vendor = props.preferences.vendor
  draft.designStyle = props.preferences.designStyle
})

function savePreferences() {
  emit('save', { vendor: draft.vendor, designStyle: draft.designStyle })
  emit('close')
}
</script>

<template>
  <ModalShell :open="open" title="AI 偏好设置" description="偏好会用于后续生成的网络方案和设备建议。" @close="$emit('close')">
    <form class="profile-preference-form" @submit.prevent="savePreferences">
      <fieldset>
        <legend>偏好的设备厂商</legend>
        <label v-for="vendor in ['华为', '思科', 'H3C']" :key="vendor" class="profile-choice-card" :class="{ selected: draft.vendor === vendor }">
          <input v-model="draft.vendor" type="radio" name="vendor" :value="vendor" />
          <span>{{ vendor }}</span>
        </label>
      </fieldset>
      <fieldset>
        <legend>网络设计风格</legend>
        <label v-for="style in ['成本优先', '性能优先']" :key="style" class="profile-choice-card" :class="{ selected: draft.designStyle === style }">
          <input v-model="draft.designStyle" type="radio" name="design-style" :value="style" />
          <span>{{ style }}</span>
        </label>
      </fieldset>
      <div class="profile-dialog-actions"><button type="button" @click="$emit('close')">取消</button><button type="submit"><Save :size="16" /> 保存偏好</button></div>
    </form>
  </ModalShell>
</template>
