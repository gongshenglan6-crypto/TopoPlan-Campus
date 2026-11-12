<script setup>
import { nextTick, reactive, ref, watch } from 'vue'
import { Check, ChevronDown, KeyRound, Pencil, ShieldCheck, UserRound, X } from '@lucide/vue'

const props = defineProps({
  profile: {
    type: Object,
    required: true
  },
  activeRole: {
    type: String,
    required: true
  },
  roleLabel: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update-profile', 'password-updated', 'request-logout'])

const nameInput = ref(null)
const isEditingName = ref(false)
const isPasswordFormOpen = ref(false)
const nameDraft = ref('')
const passwordError = ref('')
const passwordDraft = reactive({ current: '', next: '', confirmation: '' })

watch(
  () => props.profile.displayName,
  (displayName) => {
    if (!isEditingName.value) nameDraft.value = displayName
  },
  { immediate: true }
)

async function startEditingName() {
  nameDraft.value = props.profile.displayName
  isEditingName.value = true
  await nextTick()
  nameInput.value?.focus()
}

function cancelEditingName() {
  nameDraft.value = props.profile.displayName
  isEditingName.value = false
}

function saveDisplayName() {
  const displayName = nameDraft.value.trim()
  if (!displayName) return
  emit('update-profile', { displayName })
  isEditingName.value = false
}

function resetPasswordForm() {
  passwordDraft.current = ''
  passwordDraft.next = ''
  passwordDraft.confirmation = ''
  passwordError.value = ''
}

function togglePasswordForm() {
  isPasswordFormOpen.value = !isPasswordFormOpen.value
  if (!isPasswordFormOpen.value) resetPasswordForm()
}

function submitPasswordChange() {
  if (!passwordDraft.current || !passwordDraft.next || !passwordDraft.confirmation) {
    passwordError.value = '请完整填写密码信息。'
    return
  }
  if (passwordDraft.next.length < 6) {
    passwordError.value = '新密码至少需要 6 位。'
    return
  }
  if (passwordDraft.next !== passwordDraft.confirmation) {
    passwordError.value = '两次输入的新密码不一致。'
    return
  }

  resetPasswordForm()
  isPasswordFormOpen.value = false
  emit('password-updated')
}
</script>

<template>
  <section class="account-management-panel" @click.stop>
    <header class="account-management-profile">
      <span v-if="profile.avatarUrl" class="account-management-avatar avatar-image"><img :src="profile.avatarUrl" alt="用户头像" /></span>
      <span v-else-if="activeRole === 'user'" class="account-management-avatar">{{ profile.displayName.trim().charAt(0) || '游' }}</span>
      <span v-else class="account-management-avatar admin"><UserRound :size="22" /></span>

      <div class="account-management-identity">
        <div v-if="isEditingName" class="account-management-name-editor">
          <input ref="nameInput" v-model="nameDraft" maxlength="24" aria-label="修改昵称" @keydown.esc="cancelEditingName" @keydown.enter.prevent="saveDisplayName" />
          <button type="button" title="保存昵称" @click="saveDisplayName"><Check :size="15" /></button>
          <button type="button" title="取消修改" @click="cancelEditingName"><X :size="15" /></button>
        </div>
        <div v-else class="account-management-name-row">
          <strong>{{ profile.displayName }}</strong>
          <button type="button" title="修改昵称" @click="startEditingName"><Pencil :size="14" /></button>
        </div>
        <span>{{ roleLabel }}</span>
      </div>
    </header>

    <p class="account-management-meta">{{ profile.email }}<span>{{ profile.accountId }}</span></p>

    <div class="account-management-divider"></div>

    <section class="account-management-security" aria-label="安全设置">
      <button class="account-management-action" type="button" :aria-expanded="isPasswordFormOpen" @click="togglePasswordForm">
        <KeyRound :size="17" />
        <span>修改密码</span>
        <ChevronDown :size="16" :class="{ 'is-open': isPasswordFormOpen }" />
      </button>

      <form v-if="isPasswordFormOpen" class="account-management-password-form" @submit.prevent="submitPasswordChange">
        <label><span>原密码</span><input v-model="passwordDraft.current" type="password" autocomplete="current-password" /></label>
        <label><span>新密码</span><input v-model="passwordDraft.next" type="password" minlength="6" autocomplete="new-password" /></label>
        <label><span>确认新密码</span><input v-model="passwordDraft.confirmation" type="password" minlength="6" autocomplete="new-password" /></label>
        <p v-if="passwordError" class="account-management-form-error" role="alert">{{ passwordError }}</p>
        <button class="account-management-password-submit" type="submit"><ShieldCheck :size="15" />确认修改</button>
      </form>
    </section>

    <div class="account-management-divider"></div>

    <button class="account-management-action is-danger" type="button" @click="$emit('request-logout')">
      <span>退出登录</span>
    </button>
  </section>
</template>
