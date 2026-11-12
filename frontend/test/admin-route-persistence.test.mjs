import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { APP_MODE_STORAGE_KEY, parseAppRoute, resolveAuthenticatedAppRoute, resolveInitialAppRoute } from '../src/utils/appRouting.js'

const adminPages = ['dashboard', 'users', 'quota', 'quota-tiers', 'quota-users', 'devices']
const completeAdminPages = [
  'dashboard', 'users', 'roles', 'login-records', 'quota', 'quota-tiers', 'quota-users', 'devices',
  'assets', 'knowledge-docs', 'rag-config', 'knowledge-test', 'ai-models', 'prompts', 'security', 'system-management'
]

test('an explicit admin URL takes precedence over a persisted user mode', () => {
  assert.deepEqual(resolveInitialAppRoute({
    pathname: '/admin/users',
    savedMode: 'user',
    adminPages
  }), {
    mode: 'admin',
    adminPage: 'users',
    chatId: null,
    userPage: 'new-chat'
  })
})

test('billing sub-routes resolve directly to their management views', () => {
  assert.equal(resolveInitialAppRoute({
    pathname: '/admin/quota-tiers',
    savedMode: 'user',
    adminPages
  }).adminPage, 'quota-tiers')

  assert.equal(resolveInitialAppRoute({
    pathname: '/admin/quota-users',
    savedMode: 'user',
    adminPages
  }).adminPage, 'quota-users')
})

test('legacy topology-resource URLs resolve to the direct topology resources page', () => {
  for (const legacyPage of ['icon-categories', 'line-styles', 'system-assets']) {
    assert.equal(resolveInitialAppRoute({
      pathname: `/admin/${legacyPage}`,
      savedMode: 'user',
      adminPages
    }).adminPage, 'devices')
  }
})

test('the root path restores the persisted admin mode', () => {
  assert.equal(APP_MODE_STORAGE_KEY, 'app_mode')
  assert.equal(resolveInitialAppRoute({
    pathname: '/',
    savedMode: 'admin',
    adminPages
  }).mode, 'admin')
})

test('an administrator defaults to the management dashboard after authentication', () => {
  for (const pathname of ['/', '/login']) {
    assert.deepEqual(resolveAuthenticatedAppRoute({
      pathname,
      savedMode: 'user',
      adminPages,
      isAdmin: true
    }), {
      mode: 'admin',
      adminPage: 'dashboard',
      chatId: null,
      userPage: 'new-chat'
    })
  }
})

test('a standard user still enters the user workspace after authentication', () => {
  assert.equal(resolveAuthenticatedAppRoute({
    pathname: '/',
    savedMode: 'user',
    adminPages,
    isAdmin: false
  }).mode, 'user')
})

test('an explicit chat URL remains user-facing despite an admin cache', () => {
  assert.deepEqual(resolveInitialAppRoute({
    pathname: '/c/11111111-1111-4111-8111-111111111111',
    savedMode: 'admin',
    adminPages
  }), {
    mode: 'user',
    adminPage: 'dashboard',
    chatId: '11111111-1111-4111-8111-111111111111',
    userPage: 'chat'
  })
})

test('direct management URLs resolve to their exact admin views', () => {
  assert.equal(parseAppRoute('/admin/system', completeAdminPages).adminPage, 'system-management')
  assert.equal(parseAppRoute('/admin/rag', completeAdminPages).adminPage, 'rag-config')
  assert.equal(parseAppRoute('/admin/ai-models', completeAdminPages).adminPage, 'ai-models')
  assert.equal(parseAppRoute('/admin/prompts', completeAdminPages).adminPage, 'prompts')
})

test('direct user resource URLs retain their identifiers', () => {
  assert.deepEqual(parseAppRoute('/project/10', completeAdminPages), {
    mode: 'user',
    adminPage: 'dashboard',
    chatId: null,
    userPage: 'topology',
    projectId: '10'
  })
  assert.deepEqual(parseAppRoute('/c/22222222-2222-4222-8222-222222222222', completeAdminPages), {
    mode: 'user',
    adminPage: 'dashboard',
    chatId: '22222222-2222-4222-8222-222222222222',
    userPage: 'chat'
  })
})

test('unknown paths are not redirected to the dashboard', () => {
  assert.equal(parseAppRoute('/admin/does-not-exist', completeAdminPages).mode, 'not-found')
  assert.equal(parseAppRoute('/does-not-exist', completeAdminPages).mode, 'not-found')
})

test('non-administrators receive a forbidden route state for admin URLs', () => {
  assert.equal(resolveAuthenticatedAppRoute({
    pathname: '/admin/system',
    savedMode: 'user',
    adminPages: completeAdminPages,
    isAdmin: false
  }).mode, 'forbidden')
})

test('App gates workspace rendering until initialization completes', async () => {
  const source = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(source, /isInitializing/)
  assert.match(source, /resolveAuthenticatedAppRoute/)
  assert.match(source, /isAdmin: currentUser\.value\?\.role === 'ADMIN'/)
})

test('App canonicalizes a persisted admin mode back to an /admin URL', async () => {
  const source = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(source, /const canonicalAdminPath = adminPathFor\(route\.adminPage\)/)
  assert.match(source, /syncBrowserPath\(canonicalAdminPath, \{ replace: true \}\)/)
})

test('billing views are exposed through the global sidebar hierarchy', async () => {
  const [app, workspace] = await Promise.all([
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/AdminWorkspace.vue', import.meta.url), 'utf8')
  ])

  assert.match(app, /key: 'billing'/)
  assert.match(app, /key: 'quota-tiers'/)
  assert.match(app, /key: 'quota-users'/)
  assert.match(workspace, /const billingPageTabs/)
  assert.match(workspace, /:initial-tab="billingPageTabs\[activePage\]"/)
})

test('topology resources is a direct sidebar page without child navigation', async () => {
  const [app, workspace] = await Promise.all([
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/AdminWorkspace.vue', import.meta.url), 'utf8')
  ])

  assert.match(app, /\{ key: 'devices', label: '拓扑资源', icon: Network \}/)
  assert.doesNotMatch(app, /key: 'topology-assets'/)
  assert.doesNotMatch(app, /key: 'icon-categories'/)
  assert.doesNotMatch(app, /key: 'line-styles'/)
  assert.doesNotMatch(app, /key: 'system-assets'/)
  assert.match(workspace, /<AdminDeviceLibrary v-else-if="activePage === 'devices'" \/>/)
  assert.doesNotMatch(workspace, /topologyResourcePages/)
})

test('a previous admin cache cannot select admin mode for a regular account', () => {
  assert.equal(resolveAuthenticatedAppRoute({ pathname: '/', savedMode: 'admin', adminPages, isAdmin: false }).mode, 'user')
})
