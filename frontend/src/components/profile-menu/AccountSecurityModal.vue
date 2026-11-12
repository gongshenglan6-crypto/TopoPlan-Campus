<script setup>
import { computed, ref, watch } from 'vue'
import { ImageUp, ShieldCheck, UserRound } from '@lucide/vue'
import { changePassword, updateProfile } from '../../api/auth'
import ModalShell from './ModalShell.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  profile: {
    type: Object,
    required: true
  },
  activeRole: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['close', 'update-profile', 'password-updated'])

const avatarInput = ref(null)
const usernameDraft = ref('')
const displayNameDraft = ref('')
const emailDraft = ref('')
const currentPasswordDraft = ref('')
const passwordDraft = ref('')
const confirmPasswordDraft = ref('')
const avatarDraft = ref('')
const formError = ref('')
const isSaving = ref(false)

const avatarSource = computed(() => avatarDraft.value || props.profile.avatarUrl)
const avatarInitial = computed(() => displayNameDraft.value.trim().charAt(0) || '游')

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return
    usernameDraft.value = props.profile.username || ''
    displayNameDraft.value = props.profile.displayName
    emailDraft.value = props.profile.email || ''
    currentPasswordDraft.value = ''
    passwordDraft.value = ''
    confirmPasswordDraft.value = ''
    avatarDraft.value = props.profile.avatarUrl || ''
    formError.value = ''
  },
  { immediate: true }
)

function openAvatarPicker() {
  avatarInput.value?.click()
}

function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (!['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'].includes(file.type)) {
    formError.value = '请选择 PNG、JPG、WebP 或 SVG 格式的头像。'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    formError.value = '头像文件不能超过 2 MB。'
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    avatarDraft.value = String(reader.result || '')
    formError.value = ''
  }
  reader.readAsDataURL(file)
}

async function saveAccountSettings() {
  const username = usernameDraft.value.trim()
  const displayName = displayNameDraft.value.trim()
  const email = emailDraft.value.trim()
  if (username.length < 3) {
    formError.value = '用户名至少需要 3 位字符。'
    return
  }
  if (!displayName) {
    formError.value = '昵称不能为空。'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    formError.value = '请输入有效的登录邮箱。'
    return
  }
  if (passwordDraft.value) {
    if (!currentPasswordDraft.value) {
      formError.value = '请输入当前密码。'
      return
    }
    if (passwordDraft.value.length < 5) {
      formError.value = '新密码至少需要 5 位。'
      return
    }
    if (passwordDraft.value !== confirmPasswordDraft.value) {
      formError.value = '两次输入的新密码不一致。'
      return
    }
  }

  isSaving.value = true
  try {
    const updatedProfile = await updateProfile({ username, displayName, email, avatar: avatarDraft.value })
    if (passwordDraft.value) {
      await changePassword({ currentPassword: currentPasswordDraft.value, newPassword: passwordDraft.value })
    }
    emit('update-profile', {
      username: updatedProfile.username,
      displayName: updatedProfile.displayName,
      email: updatedProfile.email,
      avatarUrl: updatedProfile.avatar || ''
    })
    if (passwordDraft.value) emit('password-updated')
    emit('close')
  } catch (error) {
    formError.value = error.message || '账号信息保存失败，请稍后重试。'
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <ModalShell
    :open="open"
    title="账号与安全设置"
    description="可修改登录用户名、登录邮箱和密码，保存后立即生效。"
    card-class="account-security-modal"
    @close="$emit('close')"
  >
    <form class="account-security-form" @submit.prevent="saveAccountSettings">
      <section class="account-security-avatar-section" aria-label="用户头像">
        <span v-if="avatarSource" class="account-security-avatar avatar-image"><img :src="avatarSource" alt="用户头像" /></span>
        <span v-else-if="activeRole === 'user'" class="account-security-avatar">{{ avatarInitial }}</span>
        <span v-else class="account-security-avatar admin"><UserRound :size="22" /></span>
        <div class="account-security-avatar-copy">
          <strong>用户头像</strong>
          <span>{{ emailDraft || profile.email }}</span>
          <button type="button" @click="openAvatarPicker"><ImageUp :size="15" />更换头像</button>
        </div>
        <input ref="avatarInput" class="profile-file-input" type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" @change="handleAvatarChange" />
      </section>

      <label class="account-security-field">
        <span>登录用户名</span>
        <input v-model="usernameDraft" minlength="3" maxlength="64" autocomplete="username" placeholder="请输入登录用户名" />
      </label>

      <label class="account-security-field">
        <span>昵称</span>
        <input v-model="displayNameDraft" maxlength="24" autocomplete="nickname" placeholder="请输入昵称" />
      </label>

      <label class="account-security-field">
        <span>登录邮箱</span>
        <input v-model="emailDraft" type="email" maxlength="180" autocomplete="email" placeholder="name@example.com" />
      </label>

      <label class="account-security-field">
        <span>当前密码</span>
        <input v-model="currentPasswordDraft" type="password" autocomplete="current-password" placeholder="修改密码时必填" />
      </label>

      <label class="account-security-field">
        <span>新密码</span>
        <input v-model="passwordDraft" type="password" minlength="5" autocomplete="new-password" placeholder="至少 5 位，留空则不修改" />
      </label>

      <label class="account-security-field">
        <span>确认新密码</span>
        <input v-model="confirmPasswordDraft" type="password" minlength="5" autocomplete="new-password" placeholder="再次输入新密码" />
      </label>

      <p v-if="formError" class="account-security-form-error" role="alert">{{ formError }}</p>

      <footer class="account-security-actions">
        <button class="account-security-cancel" type="button" :disabled="isSaving" @click="$emit('close')">取消</button>
        <button class="account-security-save" type="submit" :disabled="isSaving"><ShieldCheck :size="16" />{{ isSaving ? '保存中...' : '保存修改' }}</button>
      </footer>
    </form>
  </ModalShell>
</template>
