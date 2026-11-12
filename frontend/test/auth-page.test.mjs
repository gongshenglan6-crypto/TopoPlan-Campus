import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('the auth page exposes a local login and registration flow with backend hooks', async () => {
  const source = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  ).catch(() => '')

  assert.match(source, /const isLogin = ref\(true\)/)
  assert.match(source, /TOPOPLAN/)
  assert.match(source, /v-model="username"/)
  assert.match(source, /v-model="password"/)
  assert.match(source, /const isPasswordReset = ref\(false\)/)
  assert.match(source, /重置您的密码/)
  assert.match(source, /isPasswordReset\.value = true/)
  assert.match(source, /mode: isPasswordReset\.value \? 'reset'/)
  assert.match(source, /<template v-if="isPasswordReset">/)
  assert.match(source, /id="auth-confirm-password"/)
  assert.match(source, /async function handleSubmit\(\)/)
  assert.doesNotMatch(source, /import \{ Github \} from '@lucide\/vue'/)
  assert.doesNotMatch(source, /github-icon/)
  assert.match(source, /\.auth-input:focus/)
  assert.match(source, /box-shadow: 0 0 0 2px #111827/)
})

test('the app gates every fresh visit behind the auth page', async () => {
  const source = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(source, /const isAuthenticated = ref\(false\)/)
  assert.match(source, /<AuthPage/)
  assert.match(source, /@submit="handleAuthSubmit"/)
})

test('the application restores roles from the backend session instead of local demo credentials', async () => {
  const source = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(source, /demoAccounts/)
  assert.doesNotMatch(source, /account\.password/)
  assert.match(source, /await getCurrentUser\(\)/)
  assert.match(source, /await login\(username, password\)/)
  assert.match(source, /await register\(/)
  assert.match(source, /currentUser\.value\?\.role === 'ADMIN'/)
})

test('the account-security dialog persists profile and password changes through the auth API', async () => {
  const source = await readFile(
    new URL('../src/components/profile-menu/AccountSecurityModal.vue', import.meta.url),
    'utf8'
  )

  assert.match(source, /import \{ changePassword, updateProfile \} from '\.\.\/\.\.\/api\/auth'/)
  assert.match(source, /v-model="usernameDraft"/)
  assert.match(source, /v-model="emailDraft"/)
  assert.match(source, /await updateProfile\(\{ username, displayName, email, avatar: avatarDraft\.value \}\)/)
  assert.match(source, /v-model="currentPasswordDraft"/)
  assert.match(source, /v-model="passwordDraft"/)
  assert.match(source, /v-model="confirmPasswordDraft"/)
  assert.match(source, /await changePassword\(\{ currentPassword: currentPasswordDraft\.value, newPassword: passwordDraft\.value \}\)/)
  assert.match(source, /emit\('password-updated'\)/)
})

test('the auth page permits the five-character demo account password', async () => {
  const source = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  )

  assert.match(source, /minlength="5"/)
  assert.doesNotMatch(source, /minlength="6"/)
})

test('the forgot-password entry follows the password input', async () => {
  const source = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  )

  assert.ok(
    source.indexOf('id="auth-password"') < source.indexOf('class="forgot-password"'),
    'forgot-password control should follow the password input in the template'
  )
})

test('the auth page omits the OAuth continuation divider', async () => {
  const source = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  )

  assert.doesNotMatch(source, /auth-divider/)
})

test('the auth page has no third-party login entry points', async () => {
  const authSource = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  )
  const appSource = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(authSource, /oauth-actions|oauth-button|handleOAuth|github-icon|google-icon/)
  assert.doesNotMatch(appSource, /@oauth="handleOAuth"|function handleOAuth\(/)
})

test('the auth page replaces native validation popups with inline field errors', async () => {
  const source = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  )

  assert.match(source, /<form class="auth-form" novalidate @submit\.prevent="handleSubmit">/)
  assert.match(source, /const errors = ref\(\{[\s\S]*username: ''[\s\S]*email: ''[\s\S]*password: ''/)
  assert.match(source, /function validateForm\(\)/)
  assert.match(source, /:class="\{ 'has-error': errors\.username \}"/)
  assert.match(source, /:class="\{ 'has-error': errors\.email \}"/)
  assert.match(source, /class="field-error"/)
  assert.match(source, /\.has-error\s*\{[\s\S]*border-color: #ef4444 !important;/)
  assert.match(source, /\.has-error:focus\s*\{[\s\S]*box-shadow: 0 0 0 1px #ef4444 !important;/)
})

test('the registration form exposes the UserRegisterDTO fields and terms gate', async () => {
  const source = await readFile(
    new URL('../src/components/AuthPage.vue', import.meta.url),
    'utf8'
  )

  assert.match(source, /const registerForm = ref\(\{[\s\S]*avatar: ''[\s\S]*nickname: ''[\s\S]*username: ''[\s\S]*email: ''[\s\S]*password: ''/)
  assert.match(source, /v-model="registerForm\.nickname"/)
  assert.match(source, /v-model="registerForm\.username"/)
  assert.match(source, /v-model="registerForm\.email"/)
  assert.match(source, /v-model="registerForm\.password"/)
  assert.match(source, /type="file"/)
  assert.match(source, /handleAvatarChange/)
  assert.match(source, /v-model="acceptedTerms"/)
  assert.match(source, /password\.length < 8/)
  assert.match(source, /mode: 'register'/)
  assert.doesNotMatch(source, /label for="auth-confirm-password">确认密码/)
})

test('the app and auth API submit the complete registration contract', async () => {
  const appSource = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  const apiSource = await readFile(new URL('../src/api/auth.js', import.meta.url), 'utf8')

  assert.match(appSource, /await register\(payload\)/)
  assert.match(apiSource, /avatar/)
  assert.match(apiSource, /nickname/)
  assert.match(apiSource, /email/)
  assert.match(apiSource, /body: \{ avatar, nickname, username, email, password \}/)
})
