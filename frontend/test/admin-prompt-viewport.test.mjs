import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('Prompt 页面将视口锁定限制在当前后台路由，并把长内容留在局部滚动区', async () => {
  const [app, studio, styles] = await Promise.all([
    source('src/App.vue'),
    source('src/components/admin/AdminPromptStudio.vue'),
    source('src/styles.css')
  ])

  assert.match(app, /const isAdminPromptPage = computed/)
  assert.match(app, /classList\.toggle\('is-admin-prompt-page', isLocked\)/)
  assert.match(app, /'prompt-page-active': isAdminPromptPage/)
  assert.match(studio, /class="admin-prompt-editor-scroll"/)
  assert.match(styles, /html\.is-admin-prompt-page,[\s\S]*?overflow:\s*hidden;/)
  assert.match(styles, /height:\s*calc\(100vh - 68px\);/)
  assert.match(styles, /\.app-shell\.admin-shell\.prompt-page-active \.admin-prompt-editor\s*\{[\s\S]*?overflow-y:\s*auto;/)
  assert.match(styles, /\.admin-prompt-sidebar-list\s*\{\s*flex:\s*1 1 auto;/)
})
