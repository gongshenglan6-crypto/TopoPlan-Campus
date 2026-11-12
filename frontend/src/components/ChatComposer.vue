<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowUp,
  ChevronDown,
  FileText,
  Mic,
  Plus,
  Square,
  X
} from '@lucide/vue'
import HeightCollapse from './HeightCollapse.vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  isNewDialogue: {
    type: Boolean,
    default: false
  },
  resetKey: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const composerRoot = ref(null)
const attachmentInput = ref(null)
const chatInputElement = ref(null)
const attachmentMenuOpen = ref(false)
const composerModeMenuOpen = ref(false)
const composerMode = ref('Flash')
const attachmentName = ref('')
const isVoiceListening = ref(false)
const isMultiline = ref(false)
const voiceNotice = ref('')
let resizeFrameId = null
let voiceRecognition = null
let voiceNoticeTimer = null

const composerModes = ['Flash', '拓扑规划', '方案问答', '资料解析']
const composerMenuItems = [
  { label: '上传资料', icon: FileText, action: 'file' }
]

const inputValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const hasComposerContent = computed(() => Boolean(inputValue.value.trim() || attachmentName.value))
const isExpanded = computed(() => isMultiline.value || Boolean(attachmentName.value))

watch([inputValue, attachmentName], async () => {
  await nextTick()
  queueComposerResize()
})

watch(
  () => props.resetKey,
  () => {
    clearAttachment()
    closeFloatingMenus()
    inputValue.value = ''
  }
)

function clearAttachment() {
  attachmentName.value = ''
}

function queueComposerResize() {
  if (resizeFrameId) window.cancelAnimationFrame(resizeFrameId)
  resizeFrameId = window.requestAnimationFrame(resizeComposer)
}

function resizeComposer() {
  resizeFrameId = null
  const input = chatInputElement.value
  if (!input) return

  input.style.height = '0px'
  const contentHeight = Math.max(input.scrollHeight, 24)
  input.style.height = `${contentHeight}px`
  input.style.overflowY = 'hidden'

  const nextMultilineState = contentHeight > 30
  if (isMultiline.value !== nextMultilineState) {
    isMultiline.value = nextMultilineState
    queueComposerResize()
  }
}

function submitComposer() {
  const text = inputValue.value.trim()
  if (!text && !attachmentName.value) return

  emit('submit', text || `已添加资料：${attachmentName.value}`)
  inputValue.value = ''
  clearAttachment()
  queueComposerResize()
}

function handleComposerKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  submitComposer()
}

function clearVoiceNotice() {
  if (voiceNoticeTimer) {
    window.clearTimeout(voiceNoticeTimer)
    voiceNoticeTimer = null
  }
  voiceNotice.value = ''
}

function showVoiceNotice(message, persistent = false) {
  clearVoiceNotice()
  voiceNotice.value = message

  if (!persistent) {
    voiceNoticeTimer = window.setTimeout(() => {
      voiceNotice.value = ''
      voiceNoticeTimer = null
    }, 4200)
  }
}

function describeVoiceError(errorCode) {
  switch (errorCode) {
    case 'not-allowed':
    case 'service-not-allowed':
      return '请允许浏览器使用麦克风后重试'
    case 'no-speech':
      return '未检测到语音，请重试'
    case 'audio-capture':
      return '未找到可用的麦克风设备'
    case 'network':
      return '语音识别网络连接异常，请重试'
    default:
      return '语音输入失败，请重试'
  }
}

function stopVoiceInput({ announce = true } = {}) {
  const recognition = voiceRecognition
  voiceRecognition = null
  isVoiceListening.value = false

  try {
    recognition?.stop()
  } catch {
    // The browser may already have completed or rejected the recognition request.
  }

  if (announce) showVoiceNotice('语音输入已结束')
}

function toggleVoiceInput() {
  if (isVoiceListening.value) {
    stopVoiceInput()
    return
  }

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) {
    showVoiceNotice('当前浏览器不支持语音输入')
    return
  }

  const recognition = new SpeechRecognition()
  voiceRecognition = recognition
  recognition.lang = 'zh-CN'
  recognition.interimResults = false
  recognition.continuous = false
  recognition.onresult = (event) => {
    const transcript = Array.from(event.results)
      .slice(event.resultIndex)
      .filter((result) => result.isFinal)
      .map((result) => result[0]?.transcript || '')
      .join('')
      .trim()
    if (!transcript) return

    inputValue.value = `${inputValue.value}${inputValue.value ? '\n' : ''}${transcript}`
    focusInput()
  }
  recognition.onerror = (event) => {
    if (voiceRecognition === recognition) voiceRecognition = null
    isVoiceListening.value = false
    showVoiceNotice(describeVoiceError(event.error))
  }
  recognition.onend = () => {
    const wasListening = isVoiceListening.value
    if (voiceRecognition === recognition) voiceRecognition = null
    isVoiceListening.value = false
    if (wasListening) showVoiceNotice('语音输入已结束')
  }

  try {
    recognition.start()
    isVoiceListening.value = true
    showVoiceNotice('正在聆听...', true)
  } catch {
    isVoiceListening.value = false
    voiceRecognition = null
    showVoiceNotice('无法启动语音输入，请重试')
  }
}

function toggleAttachmentMenu() {
  attachmentMenuOpen.value = !attachmentMenuOpen.value
  composerModeMenuOpen.value = false
}

function closeFloatingMenus() {
  attachmentMenuOpen.value = false
  composerModeMenuOpen.value = false
}

function chooseComposerMenuItem(item) {
  if (item.action === 'file') {
    attachmentMenuOpen.value = false
    attachmentInput.value?.click()
    return
  }
  if (item.action) attachmentName.value = item.label
  attachmentMenuOpen.value = false
}

function handleAttachmentSelection(event) {
  const [file] = event.target.files || []
  if (file) attachmentName.value = file.name
  event.target.value = ''
}

function selectComposerMode(mode) {
  composerMode.value = mode
  composerModeMenuOpen.value = false
}

function handleDocumentPointerDown(event) {
  if (!composerRoot.value?.contains(event.target)) closeFloatingMenus()
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') closeFloatingMenus()
}

function focusInput() {
  nextTick(() => {
    const input = chatInputElement.value
    if (!input) return
    input.focus()
    input.setSelectionRange(input.value.length, input.value.length)
  })
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
  nextTick(queueComposerResize)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
  if (resizeFrameId) window.cancelAnimationFrame(resizeFrameId)
  clearVoiceNotice()
  stopVoiceInput({ announce: false })
})

defineExpose({ focusInput })
</script>

<template>
  <form
    ref="composerRoot"
    class="gemini-composer"
    :class="{
      'new-dialogue-composer': isNewDialogue,
      'has-content': hasComposerContent,
      'menu-open': attachmentMenuOpen,
      'is-expanded': isExpanded
    }"
    @submit.prevent="submitComposer"
  >
    <input ref="attachmentInput" class="composer-file-input" type="file" @change="handleAttachmentSelection" />

    <div class="composer-layout" :class="{ 'is-expanded': isExpanded }">
      <div class="composer-input-area">
        <span v-if="attachmentName" class="composer-attachment-chip">
          <FileText :size="15" />
          <span>{{ attachmentName }}</span>
          <button type="button" aria-label="移除已选资料" @click="clearAttachment"><X :size="14" /></button>
        </span>
        <textarea
          ref="chatInputElement"
          v-model="inputValue"
          rows="1"
          :placeholder="isVoiceListening ? '正在聆听...' : '询问 TopoPlan'"
          aria-label="输入消息"
          @keydown="handleComposerKeydown"
          @input="queueComposerResize"
        />
      </div>

      <div class="composer-action-bar">
        <div class="composer-left-tools">
          <HeightCollapse>
            <div v-if="attachmentMenuOpen" class="composer-attachment-menu">
              <template v-for="(item, index) in composerMenuItems" :key="`${item.label || 'divider'}-${index}`">
                <div v-if="item.divider" class="composer-menu-divider"></div>
                <button v-else class="composer-menu-item" type="button" @click="chooseComposerMenuItem(item)">
                  <component :is="item.icon" :size="18" />
                  <span>{{ item.label }}</span>
                </button>
              </template>
            </div>
          </HeightCollapse>
          <button
            class="composer-plus"
            :class="{ active: attachmentMenuOpen }"
            type="button"
            aria-label="上传资料"
            data-tooltip="上传资料"
            :aria-expanded="attachmentMenuOpen"
            @click="toggleAttachmentMenu"
          ><Plus :size="23" /></button>
        </div>

        <div class="composer-controls">
          <div class="composer-mode-wrap">
            <button
              class="composer-mode"
              type="button"
              data-tooltip="选择对话模式"
              :aria-expanded="composerModeMenuOpen"
              @click="composerModeMenuOpen = !composerModeMenuOpen"
            >
              {{ composerMode }}
              <ChevronDown :size="17" />
            </button>
            <HeightCollapse>
              <div v-if="composerModeMenuOpen" class="composer-mode-menu">
                <button
                  v-for="mode in composerModes"
                  :key="mode"
                  type="button"
                  :class="{ active: composerMode === mode }"
                  @click="selectComposerMode(mode)"
                >{{ mode }}</button>
              </div>
            </HeightCollapse>
          </div>
          <div class="composer-primary-action">
            <Transition name="composer-action" mode="out-in">
              <button v-if="hasComposerContent" key="send" class="composer-send" type="submit" aria-label="发送" data-tooltip="发送消息">
                <ArrowUp :size="21" />
              </button>
              <button
                v-else
                key="voice"
                class="composer-mic"
                :class="{ listening: isVoiceListening }"
                type="button"
                :aria-label="isVoiceListening ? '停止语音输入' : '语音输入'"
                :data-tooltip="isVoiceListening ? '停止语音输入' : '语音输入'"
                :aria-pressed="isVoiceListening"
                @click="toggleVoiceInput"
              ><Square v-if="isVoiceListening" :size="16" fill="currentColor" /><Mic v-else :size="20" /></button>
            </Transition>
          </div>
        </div>
      </div>
    </div>
    <p v-if="voiceNotice" class="composer-voice-notice" :class="{ listening: isVoiceListening }" role="status" aria-live="polite">
      {{ voiceNotice }}
    </p>
  </form>
</template>
