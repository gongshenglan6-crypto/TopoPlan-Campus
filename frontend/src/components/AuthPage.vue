<template>
  <main class="auth-page">
    <section class="auth-card" aria-labelledby="auth-title">
      <header class="auth-header">
        <h1 id="auth-title">TOPOPLAN</h1>
        <p class="auth-subtitle">
          {{ isPasswordReset ? '重置您的密码' : (isLogin ? '欢迎回来' : '创建您的账户') }}
        </p>

        <p class="auth-switch-copy">
          <span>{{ isPasswordReset ? '想起密码？' : (isLogin ? '还没有账户？' : '已有账户？') }}</span>
          <button class="auth-switch" type="button" @click="isPasswordReset ? exitPasswordReset() : toggleMode()">
            {{ isPasswordReset ? '返回登录' : (isLogin ? '立即注册' : '返回登录') }}
          </button>
        </p>
      </header>

      <form class="auth-form" novalidate @submit.prevent="handleSubmit">
        <template v-if="isPasswordReset">
          <div class="auth-field">
            <label for="auth-username">邮箱或用户名</label>
            <input
              id="auth-username"
              v-model="username"
              class="auth-input"
              :class="{ 'has-error': errors.username }"
              type="text"
              name="username"
              autocomplete="username"
              placeholder="name@company.com"
              @input="clearFieldError('username')"
              required
            >
            <span v-if="errors.username" class="field-error" role="alert">{{ errors.username }}</span>
          </div>

          <div class="auth-field">
            <label for="auth-password">新密码</label>
            <input
              id="auth-password"
              v-model="password"
              class="auth-input"
              :class="{ 'has-error': errors.password }"
              type="password"
              name="password"
              autocomplete="new-password"
              placeholder="请输入新密码"
              @input="clearFieldError('password')"
              minlength="8"
              required
            >
            <span v-if="errors.password" class="field-error" role="alert">{{ errors.password }}</span>
          </div>

          <div class="auth-field">
            <label for="auth-confirm-password">确认新密码</label>
            <input
              id="auth-confirm-password"
              v-model="confirmPassword"
              class="auth-input"
              :class="{ 'has-error': errors.confirmPassword }"
              type="password"
              name="confirmPassword"
              autocomplete="new-password"
              placeholder="请再次输入密码"
              @input="clearFieldError('confirmPassword')"
              minlength="8"
              required
            >
            <span v-if="errors.confirmPassword" class="field-error" role="alert">{{ errors.confirmPassword }}</span>
          </div>
        </template>

        <template v-else-if="isLogin">
          <div class="auth-field">
            <label for="auth-username">邮箱或用户名</label>
            <input
              id="auth-username"
              v-model="username"
              class="auth-input"
              :class="{ 'has-error': errors.username }"
              type="text"
              name="username"
              autocomplete="username"
              placeholder="name@company.com"
              @input="clearFieldError('username')"
              required
            >
            <span v-if="errors.username" class="field-error" role="alert">{{ errors.username }}</span>
          </div>

          <div class="auth-field">
            <label for="auth-password">密码</label>
            <input
              id="auth-password"
              v-model="password"
              class="auth-input"
              :class="{ 'has-error': errors.password }"
              type="password"
              name="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              @input="clearFieldError('password')"
              minlength="5"
              required
            >
            <span v-if="errors.password" class="field-error" role="alert">{{ errors.password }}</span>
            <button class="forgot-password" type="button" @click="handleForgotPassword">
              忘记密码？
            </button>
          </div>
        </template>

        <template v-else>
          <div class="avatar-section">
            <button
              class="avatar-picker"
              type="button"
              aria-label="上传头像（选填）"
              @click="avatarInput?.click()"
            >
              <img v-if="registerForm.avatar" :src="registerForm.avatar" alt="头像预览">
              <Camera v-else :size="22" :stroke-width="1.6" aria-hidden="true" />
            </button>
            <div class="avatar-copy">
              <span class="avatar-title">头像</span>
              <span class="avatar-hint">上传头像（选填）</span>
            </div>
            <input
              ref="avatarInput"
              class="visually-hidden"
              type="file"
              name="avatar"
              accept="image/png,image/jpeg,image/webp"
              @change="handleAvatarChange"
            >
          </div>

          <div class="register-grid">
            <div class="auth-field">
              <label for="register-nickname">昵称</label>
              <input
                id="register-nickname"
                v-model="registerForm.nickname"
                class="auth-input"
                :class="{ 'has-error': errors.nickname }"
                type="text"
                name="nickname"
                autocomplete="nickname"
                placeholder="如：张三"
                @input="clearFieldError('nickname')"
                maxlength="120"
                required
              >
              <span v-if="errors.nickname" class="field-error" role="alert">{{ errors.nickname }}</span>
            </div>

            <div class="auth-field">
              <label for="register-username">用户名</label>
              <input
                id="register-username"
                v-model="registerForm.username"
                class="auth-input"
                :class="{ 'has-error': errors.username }"
                type="text"
                name="registerUsername"
                autocomplete="username"
                placeholder="用于登录的唯一账号"
                @input="clearFieldError('username')"
                minlength="3"
                maxlength="120"
                required
              >
              <span v-if="errors.username" class="field-error" role="alert">{{ errors.username }}</span>
            </div>
          </div>

          <div class="auth-field">
            <label for="register-email">电子邮箱</label>
            <input
              id="register-email"
              v-model="registerForm.email"
              class="auth-input"
              :class="{ 'has-error': errors.email }"
              type="email"
              name="email"
              autocomplete="email"
              placeholder="name@company.com"
              @input="clearFieldError('email')"
              maxlength="180"
              required
            >
            <span v-if="errors.email" class="field-error" role="alert">{{ errors.email }}</span>
          </div>

          <div class="auth-field">
            <label for="register-password">密码</label>
            <input
              id="register-password"
              v-model="registerForm.password"
              class="auth-input"
              :class="{ 'has-error': errors.password }"
              type="password"
              name="registerPassword"
              autocomplete="new-password"
              placeholder="至少 8 位字符"
              @input="clearFieldError('password')"
              minlength="8"
              required
            >
            <span v-if="errors.password" class="field-error" role="alert">{{ errors.password }}</span>
          </div>

          <label class="terms-field" for="register-terms">
            <input
              id="register-terms"
              v-model="acceptedTerms"
              :class="{ 'has-error': errors.terms }"
              type="checkbox"
              name="terms"
              @change="clearFieldError('terms')"
            >
            <span>我同意服务条款与隐私政策</span>
          </label>
          <span v-if="errors.terms" class="field-error" role="alert">{{ errors.terms }}</span>
        </template>

        <p v-if="submitError || props.errorMessage" class="auth-error" role="alert">
          {{ submitError || props.errorMessage }}
        </p>

        <button class="auth-submit" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '处理中...' : (isPasswordReset ? '修改密码' : (isLogin ? '登录' : '创建账户')) }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { Camera } from '@lucide/vue'

const props = defineProps({
  errorMessage: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['submit', 'forgot-password', 'clear-error'])

const isLogin = ref(true)
const isPasswordReset = ref(false)
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const registerForm = ref({
  avatar: '',
  nickname: '',
  username: '',
  email: '',
  password: ''
})
const acceptedTerms = ref(false)
const avatarInput = ref(null)
const isSubmitting = ref(false)
const submitError = ref('')
const errors = ref({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
  terms: ''
})

function clearErrors() {
  submitError.value = ''
  clearFieldErrors()
  emit('clear-error')
}

function clearFieldErrors() {
  Object.keys(errors.value).forEach((field) => {
    errors.value[field] = ''
  })
}

function clearFieldError(field) {
  errors.value[field] = ''
  submitError.value = ''
  emit('clear-error')
}

function validateForm() {
  clearFieldErrors()

  if (isPasswordReset.value) {
    if (!username.value.trim()) errors.value.username = '请输入邮箱或用户名。'
    if (!password.value) {
      errors.value.password = '请输入新密码。'
    } else if (password.value.length < 8) {
      errors.value.password = '新密码至少需要 8 位字符。'
    }
    if (!confirmPassword.value) {
      errors.value.confirmPassword = '请确认新密码。'
    } else if (password.value !== confirmPassword.value) {
      errors.value.confirmPassword = '两次输入的密码不一致。'
    }
  } else if (isLogin.value) {
    if (!username.value.trim()) errors.value.username = '请输入邮箱或用户名。'
    if (!password.value) errors.value.password = '请输入密码。'
  } else {
    if (!registerForm.value.nickname.trim()) errors.value.nickname = '请输入昵称。'
    if (!registerForm.value.username.trim()) {
      errors.value.username = '请输入用户名。'
    } else if (registerForm.value.username.trim().length < 3) {
      errors.value.username = '用户名至少需要 3 位字符。'
    }
    if (!registerForm.value.email.trim()) {
      errors.value.email = '请输入电子邮箱地址。'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.value.email.trim())) {
      errors.value.email = '请输入有效的电子邮箱地址。'
    }
    if (!registerForm.value.password) {
      errors.value.password = '请输入密码。'
    } else if (registerForm.value.password.length < 8) {
      errors.value.password = '密码至少需要 8 位字符。'
    }
    if (!acceptedTerms.value) errors.value.terms = '请先同意服务条款。'
  }

  return Object.values(errors.value).every((message) => !message)
}

function resetRegistrationForm() {
  registerForm.value = {
    avatar: '',
    nickname: '',
    username: '',
    email: '',
    password: ''
  }
  acceptedTerms.value = false
}

function toggleMode() {
  isLogin.value = !isLogin.value
  isPasswordReset.value = false
  username.value = ''
  password.value = ''
  confirmPassword.value = ''
  resetRegistrationForm()
  clearErrors()
}

function exitPasswordReset() {
  isLogin.value = true
  isPasswordReset.value = false
  password.value = ''
  confirmPassword.value = ''
  clearErrors()
}

function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    submitError.value = '请选择 PNG、JPG 或 WebP 图片。'
    event.target.value = ''
    return
  }

  if (file.size > 2 * 1024 * 1024) {
    submitError.value = '头像大小不能超过 2 MB。'
    event.target.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    registerForm.value.avatar = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.onerror = () => {
    submitError.value = '头像读取失败，请重试。'
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

async function handleSubmit() {
  clearErrors()

  if (!validateForm()) return

  isSubmitting.value = true

  try {
    if (isPasswordReset.value || isLogin.value) {
      emit('submit', {
        mode: isPasswordReset.value ? 'reset' : 'login',
        username: username.value.trim(),
        password: password.value
      })
      return
    }

    emit('submit', {
      mode: 'register',
      avatar: registerForm.value.avatar,
      nickname: registerForm.value.nickname.trim(),
      username: registerForm.value.username.trim(),
      email: registerForm.value.email.trim(),
      password: registerForm.value.password
    })
  } finally {
    isSubmitting.value = false
  }
}

function handleForgotPassword() {
  isPasswordReset.value = true
  password.value = ''
  confirmPassword.value = ''
  clearErrors()
  emit('forgot-password', username.value.trim())
}
</script>

<style scoped>
.auth-page {
  box-sizing: border-box;
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  padding: 24px;
  background: #f9fafb;
}

.auth-card {
  box-sizing: border-box;
  width: min(100%, 400px);
  padding: 40px;
  border: 1px solid #f3f4f6;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 8px 30px rgb(0 0 0 / 4%);
}

.auth-header {
  text-align: center;
}

.auth-header h1 {
  margin: 0;
  color: #111111;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}

.auth-subtitle {
  margin: 10px 0 0;
  color: #4b5563;
  font-size: 15px;
  line-height: 1.5;
}

.auth-switch-copy {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 5px;
  margin: 20px 0 0;
  color: #9ca3af;
  font-size: 13px;
  line-height: 1.5;
}

.auth-switch,
.forgot-password {
  padding: 0;
  border: 0;
  background: transparent;
  color: #111827;
  font: inherit;
  font-weight: 500;
  cursor: pointer;
}

.auth-switch:hover,
.forgot-password:hover {
  text-decoration: underline;
}

.auth-switch:focus-visible,
.forgot-password:focus-visible,
.auth-submit:focus-visible,
.avatar-picker:focus-visible {
  outline: 2px solid #111827;
  outline-offset: 2px;
}

.auth-form {
  display: grid;
  gap: 16px;
  margin-top: 30px;
}

.auth-field {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.auth-field label,
.avatar-title {
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
}

.forgot-password {
  justify-self: end;
  margin-top: 0;
  font-size: 12px;
}

.auth-input {
  box-sizing: border-box;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  outline: none;
  background: #ffffff;
  color: #111827;
  font: inherit;
  font-size: 14px;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

.auth-input::placeholder {
  color: #9ca3af;
}

.auth-input:hover {
  border-color: #d1d5db;
}

.auth-input:focus {
  border-color: #111827;
  outline: none;
  box-shadow: 0 0 0 2px #111827;
}

.has-error {
  border-color: #ef4444 !important;
}

.has-error:focus {
  box-shadow: 0 0 0 1px #ef4444 !important;
}

.field-error {
  display: block;
  margin-top: 6px;
  color: #ef4444;
  font-size: 12px;
  line-height: 1.4;
  animation: field-error-in 150ms ease-out;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 2px;
}

.avatar-picker {
  display: grid;
  width: 80px;
  height: 80px;
  flex: 0 0 80px;
  place-items: center;
  overflow: hidden;
  padding: 0;
  border: 1px dashed #d1d5db;
  border-radius: 50%;
  background: #f9fafb;
  color: #9ca3af;
  cursor: pointer;
}

.avatar-picker:hover {
  border-color: #9ca3af;
  color: #4b5563;
}

.avatar-picker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-copy {
  display: grid;
  gap: 4px;
}

.avatar-hint {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.4;
}

.register-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.terms-field {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
}

.terms-field input {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
  margin: 1px 0 0;
  accent-color: #111827;
}

.auth-error {
  margin: -2px 0 0;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.5;
}

@keyframes field-error-in {
  from {
    opacity: 0;
    transform: translateY(-2px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.auth-submit {
  box-sizing: border-box;
  width: 100%;
  min-height: 40px;
  border: 1px solid #111827;
  border-radius: 8px;
  background: #111827;
  color: #ffffff;
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms ease;
}

.auth-submit:hover:not(:disabled) {
  background: #1f2937;
}

.auth-submit:disabled {
  cursor: wait;
  opacity: 0.7;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

@media (max-width: 460px) {
  .auth-page {
    padding: 16px;
  }

  .auth-card {
    padding: 32px 24px;
  }

  .register-grid .auth-input {
    font-size: 12px;
  }
}

@media (max-width: 360px) {
  .register-grid {
    grid-template-columns: 1fr;
  }
}
</style>
