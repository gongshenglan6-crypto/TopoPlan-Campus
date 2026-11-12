<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ImagePlus, Send, X } from '@lucide/vue'
import ModalShell from './ModalShell.vue'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'submitted'])
const feedback = ref('')
const screenshotInput = ref(null)
const screenshotUrl = ref('')
const submitted = ref(false)
let temporaryUrl = ''

function chooseScreenshot() {
  screenshotInput.value?.click()
}

function selectScreenshot(event) {
  const file = event.target.files?.[0]
  if (!file || !file.type.startsWith('image/')) return
  if (temporaryUrl) URL.revokeObjectURL(temporaryUrl)
  temporaryUrl = URL.createObjectURL(file)
  screenshotUrl.value = temporaryUrl
}

function removeScreenshot() {
  if (temporaryUrl) URL.revokeObjectURL(temporaryUrl)
  temporaryUrl = ''
  screenshotUrl.value = ''
  if (screenshotInput.value) screenshotInput.value.value = ''
}

function submitFeedback() {
  if (!feedback.value.trim()) return
  submitted.value = true
  emit('submitted')
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    submitted.value = false
    feedback.value = ''
    removeScreenshot()
  }
)

onBeforeUnmount(removeScreenshot)
</script>

<template>
  <ModalShell :open="open" title="问题反馈" description="提交的问题会作为前端演示反馈记录，不会发送到真实服务端。" @close="$emit('close')">
    <form class="profile-feedback-form" @submit.prevent="submitFeedback">
      <label>
        <span>问题描述</span>
        <textarea v-model="feedback" maxlength="500" placeholder="请描述遇到的页面、操作步骤和预期结果。" />
      </label>
      <div class="profile-screenshot-upload">
        <button type="button" @click="chooseScreenshot"><ImagePlus :size="16" /> 上传截图</button>
        <input ref="screenshotInput" class="profile-file-input" type="file" accept="image/*" @change="selectScreenshot" />
        <div v-if="screenshotUrl" class="profile-screenshot-preview"><img :src="screenshotUrl" alt="反馈截图预览" /><button type="button" aria-label="移除截图" @click="removeScreenshot"><X :size="14" /></button></div>
      </div>
      <p v-if="submitted" class="profile-submit-success">反馈已提交，感谢你的说明。</p>
      <div class="profile-dialog-actions"><button type="button" @click="$emit('close')">取消</button><button type="submit"><Send :size="16" /> 提交反馈</button></div>
    </form>
  </ModalShell>
</template>
